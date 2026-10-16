package com.energym.app.controller;

import android.content.Context;
import android.content.SharedPreferences;

import com.energym.app.data.ExtrasRepository;

import java.util.*;

/**
 * Copia en la cuenta (y trae de vuelta) lo que cada función guarda en el móvil: medidas, comidas guardadas,
 * ejercicios propios y superseries. Gana la versión guardada más tarde. Las fotos de progreso no se suben.
 */
public final class ExtrasSync {
	/** Tipo → preferencias donde vive su copia local (por cuenta, salvo superseries, que van por entreno). */
	private static final Map<String, String> PREFS = new LinkedHashMap<>();

	static {
		PREFS.put("measures", "body_measures");
		PREFS.put("meals", "saved_meals");
		PREFS.put("exercises", "custom_exercises");
		PREFS.put("supersets", "supersets");
	}

	private final Context context;
	private final SyncController sync;
	private final Runnable reloaded;
	private String owner;

	ExtrasSync(Context context, SyncController sync, Runnable reloaded) {
		this.context = context.getApplicationContext();
		this.sync = sync;
		this.reloaded = reloaded;
	}

	void activate(String next) {
		owner = next;
		pull();
	}

	/** Llamar tras guardar en local: lo deja en la cola de la cuenta. Medidas, comidas y ejercicios usan la clave «all». */
	public void push(String type, String key, String data) {
		String current = owner;
		if (current == null || sync == null)
			return;
		stamp(type, current, key, System.currentTimeMillis());
		sync.withDatabase(db -> {
			if (!db.getOwnerUid().equals(current))
				throw new IllegalStateException("La cuenta activa cambió");
			new ExtrasRepository(db).put(type, key, data == null ? "" : data);
			return null;
		}, new SyncController.DataCallback<Object>() {
			public void onSuccess(Object value) {
				sync.requestSync();
			}

			public void onFailure(Exception error) {
				// La copia local sigue intacta; se reintentará al guardar de nuevo o al traer la cuenta.
			}
		});
	}

	/**
	 * Trae lo de la cuenta (tras restaurar o sincronizar) y lo aplica en local si es más reciente; lo local que aún
	 * no estaba en la cuenta (datos de antes de esta función) se sube.
	 */
	void pull() {
		String current = owner;
		if (current == null || sync == null)
			return;
		sync.withDatabase(db -> {
			if (!db.getOwnerUid().equals(current))
				throw new IllegalStateException("La cuenta activa cambió");
			return new ExtrasRepository(db).all();
		}, new SyncController.DataCallback<List<ExtrasRepository.Extra>>() {
			public void onSuccess(List<ExtrasRepository.Extra> remote) {
				if (!current.equals(owner))
					return;
				boolean changed = false;
				Set<String> known = new HashSet<>();
				for (ExtrasRepository.Extra e : remote) {
					known.add(e.type + "/" + e.key);
					String local = read(e.type, current, e.key);
					if (e.updatedAt > stamp(e.type, current, e.key) && !e.data.equals(local)) {
						write(e.type, current, e.key, e.data);
						stamp(e.type, current, e.key, e.updatedAt);
						changed = true;
					}
				}
				for (String type : new String[]{"measures", "meals", "exercises"}) {
					String local = read(type, current, "all");
					if (!known.contains(type + "/all") && local != null && !local.isEmpty() && !"[]".equals(local))
						push(type, "all", local);
				}
				if (changed)
					reloaded.run();
			}

			public void onFailure(Exception error) {
				// Sin base abierta (sin cuenta o cambiando de cuenta) no hay nada que traer.
			}
		});
	}

	private SharedPreferences prefs(String type) {
		return context.getSharedPreferences(PREFS.get(type), Context.MODE_PRIVATE);
	}

	/** Superseries por entreno (clave = id de la sesión); el resto por cuenta. */
	private String localKey(String type, String owner, String key) {
		return "supersets".equals(type) ? key : owner;
	}

	private String read(String type, String owner, String key) {
		return prefs(type).getString(localKey(type, owner, key), null);
	}

	private void write(String type, String owner, String key, String data) {
		prefs(type).edit().putString(localKey(type, owner, key), data).apply();
	}

	private long stamp(String type, String owner, String key) {
		return context.getSharedPreferences("extras_time", Context.MODE_PRIVATE)
			.getLong(type + "/" + owner + "/" + key, 0);
	}

	private void stamp(String type, String owner, String key, long time) {
		context.getSharedPreferences("extras_time", Context.MODE_PRIVATE).edit()
			.putLong(type + "/" + owner + "/" + key, time).apply();
	}
}
