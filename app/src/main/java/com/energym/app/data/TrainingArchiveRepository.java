package com.energym.app.data;

import org.json.*;
import java.util.*;

/**
 * Lápidas por cuenta para quitar rutinas o sesiones de las listas sin reescribir sus snapshots.
 * Son registros inmutables y sincronizables: reinstalar no vuelve a mostrar un entrenamiento eliminado.
 */
public final class TrainingArchiveRepository {
	private final EnerGymDatabase database;

	public TrainingArchiveRepository(EnerGymDatabase database) {
		this.database = Objects.requireNonNull(database);
	}

	public boolean contains(SyncRecord.Kind kind, String targetId) {
		return database.find(SyncRecord.Kind.TRAINING_ARCHIVE, id(kind, targetId)) != null;
	}

	/** Una lectura por listado, evitando una consulta SQLite adicional por cada rutina o sesión. */
	public Set<String> targets(SyncRecord.Kind kind) {
		if (kind != SyncRecord.Kind.ROUTINE && kind != SyncRecord.Kind.SESSION)
			throw new IllegalArgumentException("Tipo de entrenamiento inválido");
		Set<String> result = new HashSet<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.TRAINING_ARCHIVE, null)) {
			try {
				JSONObject value = new JSONObject(row.record.getPayload());
				if (kind.name().equals(value.getString("kind"))) result.add(value.getString("targetId"));
			} catch (JSONException e) { throw new IllegalArgumentException("Lápida inválida", e); }
		}
		return result;
	}

	public void archive(SyncRecord.Kind kind, String targetId) {
		database.transaction(() -> {
			String archiveId = id(kind, targetId);
			if (database.find(SyncRecord.Kind.TRAINING_ARCHIVE, archiveId) != null) return null;
			if (database.find(kind, targetId) == null)
				throw new IllegalArgumentException("El entrenamiento no pertenece a esta cuenta");
			return database.writeLocal(SyncRecord.Kind.TRAINING_ARCHIVE, archiveId, payload(kind, targetId));
		});
	}

	private static String id(SyncRecord.Kind kind, String targetId) {
		if ((kind != SyncRecord.Kind.ROUTINE && kind != SyncRecord.Kind.SESSION) ||
			targetId == null || !targetId.matches("[A-Za-z0-9_-]{1,128}"))
			throw new IllegalArgumentException("Identidad de entrenamiento inválida");
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(targetId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder key = new StringBuilder(kind.name()).append('_');
			for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value & 255));
			return key.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String payload(SyncRecord.Kind kind, String targetId) {
		try {
			return new JSONObject().put("kind", kind.name()).put("targetId", targetId).put("deleted", true).toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Lápida de entrenamiento inválida", e);
		}
	}

	/** Valida la identidad tanto al importar SQLite como al cruzar la frontera Firestore. */
	public static String canonical(String syncId, String json) {
		try {
			JSONObject o = new JSONObject(json);
			SyncRecord.Kind kind = SyncRecord.Kind.valueOf(o.getString("kind"));
			String targetId = o.getString("targetId");
			if (o.length() != 3 || !o.getBoolean("deleted") || !id(kind, targetId).equals(syncId))
				throw new IllegalArgumentException("Lápida de entrenamiento inválida");
			return payload(kind, targetId);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Lápida de entrenamiento inválida", e);
		}
	}
}
