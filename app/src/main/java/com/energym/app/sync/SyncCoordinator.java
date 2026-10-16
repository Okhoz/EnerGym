package com.energym.app.sync;

import com.energym.app.data.SyncRecord;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * MODEL de sincronización. Todas las operaciones SQLite se despachan al ejecutor de trabajo.
 * La generación y el UID autenticado invalidan restauraciones/confirmaciones de cuentas anteriores.
 */
public final class SyncCoordinator {
	public enum Status {SIGNED_OUT, DEMO_LOCAL, IDLE, SYNCING, SYNCED, CONFLICT, ERROR}

	public interface Listener {
		void onStatus(Status status, Exception error);
	}

	private final CloudSyncGateway cloud;
	private final Supplier<String> authenticatedUid;
	private final Executor worker;
	private final Listener listener;
	private LocalSyncStore store;
	private long generation;
	private boolean busy, repeat;

	public SyncCoordinator(CloudSyncGateway cloud, Supplier<String> authenticatedUid, Executor worker,
		Listener listener) {
		this.cloud = Objects.requireNonNull(cloud);
		this.authenticatedUid = Objects.requireNonNull(authenticatedUid);
		this.worker = Objects.requireNonNull(worker);
		this.listener = Objects.requireNonNull(listener);
	}

	/**
	 * Cambio de cuenta: cierra el almacén anterior; demo nunca se migra ni se sube.
	 */
	public synchronized void activate(LocalSyncStore next) {
		generation++;
		busy = false;
		repeat = false;
		LocalSyncStore previous = store;
		store = next;
		if (previous != null && previous != next)
			worker.execute(previous::close);
		status(availableStatus(), null);
	}

	private Status availableStatus() {
		if (store != null && "demo:local".equals(store.ownerUid()))
			return Status.DEMO_LOCAL;
		if (store == null || !store.ownerUid().equals(authenticatedUid.get()))
			return Status.SIGNED_OUT;
		return Status.IDLE;
	}

	/**
	 * Solicitar al iniciar sesión, recuperar conexión o mutar datos; agrupa peticiones simultáneas.
	 */
	public synchronized void sync() {
		Status available = availableStatus();
		if (available != Status.IDLE) {
			// Una reautenticación puede ocultar temporalmente el UID sin cerrar el scope SQLite.
			// El ciclo deja de ser utilizable; no conservar busy hasta un callback que quizá nunca llegue.
			if (busy) {
				generation++;
				busy = false;
				repeat = false;
			}
			status(available, null);
			return;
		}
		if (busy) {
			repeat = true;
			return;
		}
		busy = true;
		deferred.clear();
		long token = generation;
		LocalSyncStore target = store;
		status(Status.SYNCING, null);
		worker.execute(() -> {
			synchronized (this) {
				if (!current(token, target))
					return;
				try {
					cloud.restore(target.ownerUid(),
						new CloudSyncGateway.Callback<List<CloudSyncGateway.RemoteRecord>>() {
							public void onSuccess(List<CloudSyncGateway.RemoteRecord> remote) {
								schedule(token, target, () -> restored(token, target, remote));
							}

							public void onFailure(Exception e) {
								schedule(token, target, () -> failed(token, target, e));
							}
						});
				} catch (Exception e) {
					failed(token, target, e);
				}
			}
		});
	}

	private synchronized void restored(long token, LocalSyncStore target, List<CloudSyncGateway.RemoteRecord> remote) {
		if (!current(token, target))
			return;
		try {
			List<SyncRecord> records = new ArrayList<>();
			Map<String, Long> versions = new HashMap<>();
			for (CloudSyncGateway.RemoteRecord r : remote) {
				if (!target.ownerUid().equals(r.record.getOwnerUid()))
					throw new IllegalArgumentException("Cuenta remota incorrecta");
				if (versions.put(key(r.record), r.cloudVersion) != null)
					throw new IllegalArgumentException("Identidad remota duplicada");
				records.add(r.record);
			}
			target.importRecords(records); // SQLite protege pendientes y valida referencias antes de escribir.
			uploadNext(token, target, new ArrayList<>(target.pending()), versions, 0, false);
		} catch (Exception e) {
			failed(token, target, e);
		}
	}

	private synchronized void uploadNext(long token, LocalSyncStore target, List<SyncRecord> pending,
		Map<String, Long> versions, int index, boolean conflict) {
		if (!current(token, target))
			return;
		if (index == pending.size()) {
			boolean newerPending = false;
			for (SyncRecord r : target.pending())
				newerPending |= !deferred.contains(key(r));
			if (newerPending && !conflict)
				repeat = true;
			finish(conflict ? Status.CONFLICT : newerPending ? Status.IDLE : Status.SYNCED, null);
			return;
		}
		SyncRecord record = pending.get(index);
		if (!target.ownerUid().equals(record.getOwnerUid())) {
			failed(token, target, new IllegalArgumentException("Cuenta local incorrecta"));
			return;
		}
		try {
			cloud.upload(record, versions.getOrDefault(key(record), 0L),
				new CloudSyncGateway.Callback<CloudSyncGateway.UploadResult>() {
					public void onSuccess(CloudSyncGateway.UploadResult result) {
						schedule(token, target, () -> {
							synchronized (SyncCoordinator.this) {
								if (!current(token, target))
									return;
								try {
									if (result.accepted)
										target.acknowledge(record); // Confirma exclusivamente la revisión enviada.
									uploadNext(token, target, pending, versions, index + 1,
										conflict || !result.accepted);
								} catch (Exception e) {
									failed(token, target, e);
								}
							}
						});
					}

					public void onFailure(Exception e) {
						if (record.getKind() == SyncRecord.Kind.EXTRA) {
							// Datos extra: no bloquean la copia de entrenos y comidas; se reintentan en el siguiente ciclo.
							schedule(token, target, () -> {
								synchronized (SyncCoordinator.this) {
									deferred.add(key(record));
									uploadNext(token, target, pending, versions, index + 1, conflict);
								}
							});
							return;
						}
						schedule(token, target, () -> failed(token, target, e));
					}
				});
		} catch (Exception e) {
			failed(token, target, e);
		}
	}

	/** Extras que no se pudieron subir en este ciclo: quedan pendientes sin forzar repeticiones inmediatas. */
	private final Set<String> deferred = new HashSet<>();

	private boolean current(long token, LocalSyncStore target) {
		if (token != generation || target != store)
			return false;
		if (target.ownerUid().equals(authenticatedUid.get()))
			return true;
		// Invalidar solo el ciclo que aún posee este token. Un callback de otro ciclo/cuenta
		// no puede liberar busy del trabajo nuevo ni confirmar una subida antigua al recuperar UID.
		generation++;
		busy = false;
		repeat = false;
		status(availableStatus(), null);
		return false;
	}

	/**
	 * Descarta callbacks invalidados antes de tocar un worker que puede haber finalizado.
	 */
	private synchronized void schedule(long token, LocalSyncStore target, Runnable callback) {
		if (current(token, target))
			worker.execute(callback);
	}

	private synchronized void failed(long token, LocalSyncStore target, Exception e) {
		if (current(token, target))
			finish(Status.ERROR, e);
	}

	private void finish(Status state, Exception e) {
		busy = false;
		boolean again = repeat;
		repeat = false;
		status(state, e);
		if (again)
			sync();
	}

	private void status(Status s, Exception e) {
		listener.onStatus(s, e);
	}

	private static String key(SyncRecord r) {
		return r.getKind().name() + ":" + r.getSyncId();
	}

	/**
	 * Identifica la emisión de estado; permite al controlador descartar un estado encolado de otra cuenta.
	 */
	public synchronized String activeOwnerUid() {
		return store == null ? null : store.ownerUid();
	}

	/**
	 * Invalida callbacks antes de cerrar SQLite; seguro tras cerrar sesión.
	 */
	public synchronized void close() {
		activate(null);
	}
}
