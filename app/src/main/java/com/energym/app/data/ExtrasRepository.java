package com.energym.app.data;

import org.json.*;

import java.util.*;

/**
 * MODEL: datos extra de la cuenta que se sincronizan como un documento cada uno (último en guardarse gana):
 * medidas corporales, comidas guardadas, ejercicios propios y superseries de cada entreno. El contenido es el
 * texto que ya guarda cada función; aquí solo se valida su forma y tamaño.
 */
public final class ExtrasRepository {
	/** Tipos admitidos; el syncId es «tipo_clave». */
	public static final Set<String> TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"measures", "meals", "exercises", "supersets")));
	/** Límite holgado por debajo de los 600 KB por documento de Firestore. */
	public static final int MAX_DATA_CHARS = 300_000;

	public static final class Extra {
		public final String type, key, data;
		public final long updatedAt;

		Extra(String type, String key, String data, long updatedAt) {
			this.type = type;
			this.key = key;
			this.data = data;
			this.updatedAt = updatedAt;
		}
	}

	private final EnerGymDatabase database;

	public ExtrasRepository(EnerGymDatabase database) {
		this.database = Objects.requireNonNull(database, "database");
	}

	/** Guarda (o sustituye) el contenido de un tipo y clave; con data vacío queda vacío pero sincronizado. */
	public void put(String type, String key, String data) {
		String syncId = syncId(type, key);
		String payload = encode(type, key, data);
		database.transaction(() -> {
			EnerGymDatabase.Row row = database.find(SyncRecord.Kind.EXTRA, syncId);
			if (row != null && row.record.getPayload().equals(payload))
				return null;
			return database.writeLocal(SyncRecord.Kind.EXTRA, syncId, payload);
		});
	}

	public List<Extra> all() {
		List<Extra> result = new ArrayList<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.EXTRA, null))
			try {
				JSONObject o = new JSONObject(row.record.getPayload());
				result.add(new Extra(o.getString("type"), o.getString("key"), o.getString("data"),
					row.record.getUpdatedAt()));
			} catch (JSONException e) {
				throw new IllegalArgumentException("Extra inválido", e);
			}
		return result;
	}

	public static String syncId(String type, String key) {
		if (!TYPES.contains(type) || key == null || !key.matches("[A-Za-z0-9_-]{1,100}"))
			throw new IllegalArgumentException("Extra sin tipo o clave válidos");
		return type + "_" + key;
	}

	static String encode(String type, String key, String data) {
		if (data == null || data.length() > MAX_DATA_CHARS)
			throw new IllegalArgumentException("Extra demasiado grande");
		syncId(type, key);
		try {
			return new JSONObject().put("type", type).put("key", key).put("data", data).toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Extra inválido", e);
		}
	}

	/** Forma canónica de un registro EXTRA, validando que el syncId corresponda a su tipo y clave. */
	public static String canonical(String syncId, String json) {
		try {
			JSONObject o = new JSONObject(json);
			if (o.length() != 3)
				throw new IllegalArgumentException("Extra con campos inesperados");
			String type = o.getString("type"), key = o.getString("key");
			if (!syncId(type, key).equals(syncId))
				throw new IllegalArgumentException("Identidad de extra incorrecta");
			return encode(type, key, o.getString("data"));
		} catch (JSONException e) {
			throw new IllegalArgumentException("Extra inválido", e);
		}
	}
}
