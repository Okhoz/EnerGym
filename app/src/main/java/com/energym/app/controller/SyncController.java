package com.energym.app.controller;

import android.content.Context;

import com.energym.app.auth.*;
import com.energym.app.data.EnerGymDatabase;
import com.energym.app.sync.*;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * CONTROLADOR MVC: una instancia SQLite por cuenta; la vista recibe resultados por delivery.
 * Usar un ejecutor de trabajo de un solo hilo compartido con acciones de datos y sincronización.
 */
public final class SyncController implements AccountController.SessionTarget, AutoCloseable {
	public interface DataAction<T> {
		T run(EnerGymDatabase database) throws Exception;
	}

	public interface DataCallback<T> {
		void onSuccess(T value);

		void onFailure(Exception error);
	}

	private final Context context;
	private final AccountGateway accounts;
	private final Executor worker, delivery;
	private SyncCoordinator coordinator;
	private EnerGymDatabase database;
	private volatile long generation;

	public SyncController(Context context, AccountGateway accounts, CloudSyncGateway cloud, Executor worker, Executor delivery, SyncCoordinator.Listener listener) {
		this.context = context.getApplicationContext();
		this.accounts = Objects.requireNonNull(accounts);
		this.worker = Objects.requireNonNull(worker);
		this.delivery = Objects.requireNonNull(delivery);
		coordinator = new SyncCoordinator(cloud, () -> {
			AccountUser u = accounts.getCurrentUser();
			return u == null ? null : u.getUid();
		}, worker, (status, error) -> {
			long token = generation;
			String owner = coordinator == null ? null : coordinator.activeOwnerUid();
			delivery.execute(() -> {
				synchronized (SyncController.this) {
					String currentOwner = database == null ? null : database.getOwnerUid();
					if (token == generation && Objects.equals(owner, currentOwner))
						listener.onStatus(status, error);
				}
			});
		});
	}

	public synchronized void authenticated(AccountUser user) {
		AccountUser current = accounts.getCurrentUser();
		if (current == null || !current.getUid().equals(user.getUid()))
			throw new IllegalArgumentException("Identidad no autenticada");
		open(current.getUid());
		coordinator.sync();
	}

	public synchronized void demo() {
		open(EnerGymDatabase.DEMO_OWNER_UID);
	}

	public synchronized void signedOut() {
		generation++;
		database = null;
		coordinator.close();
	}

	private void open(String uid) {
		if (database != null && uid.equals(database.getOwnerUid()))
			return;
		generation++;
		database = new EnerGymDatabase(context, uid);
		coordinator.activate(new SqliteSyncStore(database));
	}

	/**
	 * Cargar/guardar mediante repositorios dentro de esta acción; callbacks antiguos se descartan.
	 * Tras una mutación confirmada solicitar requestSync. No devolver ni retener la instancia database.
	 */
	public synchronized <T> void withDatabase(DataAction<T> action, DataCallback<T> callback) {
		long token = generation;
		EnerGymDatabase target = database;
		if (target == null) {
			delivery.execute(() -> {
				synchronized (SyncController.this) {
					if (token == generation)
						callback.onFailure(new IllegalStateException("Selecciona cuenta o demo"));
				}
			});
			return;
		}
		worker.execute(() -> {
			synchronized (SyncController.this) {
				if (!current(token, target))
					return;
				try {
					T result = action.run(target);
					deliver(token, target, () -> callback.onSuccess(result));
				} catch (Exception e) {
					deliver(token, target, () -> callback.onFailure(e));
				}
			}
		});
	}

	private boolean current(long token, EnerGymDatabase target) {
		if (token != generation || target != database)
			return false;
		if (EnerGymDatabase.DEMO_OWNER_UID.equals(target.getOwnerUid()))
			return true;
		AccountUser user = accounts.getCurrentUser();
		return user != null && target.getOwnerUid().equals(user.getUid());
	}

	private void deliver(long token, EnerGymDatabase target, Runnable result) {
		delivery.execute(() -> {
			synchronized (SyncController.this) {
				if (current(token, target))
					result.run();
			}
		});
	}

	/**
	 * Llamar tras mutaciones y al reconectar; no escucha ni descarga mientras demo/cerrado.
	 */
	public void requestSync() {
		coordinator.sync();
	}

	public synchronized void close() {
		signedOut();
	}
}
