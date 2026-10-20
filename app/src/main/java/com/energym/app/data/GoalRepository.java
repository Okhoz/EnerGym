package com.energym.app.data;

import com.energym.app.progress.Goal;

import org.json.*;

import java.util.*;

/**
 * MODEL: objetivos de la cuenta como registros sincronizables. Borrar escribe una lápida definitiva.
 */
public final class GoalRepository {
	private final EnerGymDatabase database;

	public GoalRepository(EnerGymDatabase database) {
		this.database = Objects.requireNonNull(database, "database");
	}

	public void save(Goal goal) {
		database.transaction(() -> {
			EnerGymDatabase.Row row = database.find(SyncRecord.Kind.GOAL, goal.id);
			if (row != null && deleted(row.record.getPayload()))
				throw new IllegalArgumentException("Objetivo eliminado");
			return database.writeLocal(SyncRecord.Kind.GOAL, goal.id, encode(goal));
		});
	}

	public void remove(String id) {
		database.transaction(() -> {
			EnerGymDatabase.Row row = database.find(SyncRecord.Kind.GOAL, id);
			if (row == null || deleted(row.record.getPayload()))
				return null;
			Goal goal = decode(row.record.getPayload());
			return database.writeLocal(SyncRecord.Kind.GOAL, id, tombstone(goal.id, goal.createdAt));
		});
	}

	public List<Goal> goals() {
		List<Goal> result = new ArrayList<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.GOAL, null))
			if (!deleted(row.record.getPayload()))
				result.add(decode(row.record.getPayload()));
		result.sort(Comparator.comparingLong(g -> g.createdAt));
		return result;
	}

	public static String encode(Goal g) {
		try {
			JSONObject o = new JSONObject().put("id", g.id).put("type", g.type.name()).put("target", g.target)
				.put("start", g.start).put("exerciseId", g.exerciseId).put("exerciseName", g.exerciseName)
				.put("createdAt", g.createdAt).put("deleted", false);
			// Campos nuevos solo cuando hacen falta: un objetivo de antes se guarda igual que siempre.
			if (g.type == Goal.Type.CUSTOM)
				o.put("name", g.name).put("unit", g.customUnit);
			if (g.completedAt > 0)
				o.put("completedAt", g.completedAt);
			if (!g.entries.isEmpty()) {
				JSONArray entries = new JSONArray();
				for (Map.Entry<java.time.LocalDate, Double> e : g.entries.entrySet())
					entries.put(new JSONObject().put("day", e.getKey().toString()).put("value", e.getValue()));
				o.put("entries", entries);
			}
			return o.toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}

	public static Goal decode(String json) {
		try {
			JSONObject o = new JSONObject(json);
			if (o.optBoolean("deleted", false))
				throw new IllegalArgumentException("Objetivo eliminado");
			SortedMap<java.time.LocalDate, Double> entries = new TreeMap<>();
			JSONArray rows = o.optJSONArray("entries");
			for (int i = 0; rows != null && i < rows.length(); i++) {
				JSONObject row = rows.getJSONObject(i);
				entries.put(java.time.LocalDate.parse(row.getString("day")), row.getDouble("value"));
			}
			return new Goal(o.getString("id"), Goal.Type.valueOf(o.getString("type")), o.getDouble("target"),
				o.getDouble("start"), o.getString("exerciseId"), o.getString("exerciseName"), o.optString("name", ""),
				o.optString("unit", ""), o.getLong("createdAt"), o.optLong("completedAt", 0), entries);
		} catch (JSONException | java.time.format.DateTimeParseException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}

	static String tombstone(String id, long createdAt) {
		try {
			return new JSONObject().put("id", id).put("createdAt", createdAt).put("deleted", true).toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}

	public static boolean deleted(String json) {
		try {
			return new JSONObject(json).optBoolean("deleted", false);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}

	/**
	 * Forma canónica de un registro GOAL (activo o lápida) validando que el ID coincida.
	 */
	public static String canonical(String syncId, String json) {
		try {
			JSONObject o = new JSONObject(json);
			if (!syncId.equals(o.optString("id")))
				throw new IllegalArgumentException("Identidad de objetivo incorrecta");
			if (!o.optBoolean("deleted", false))
				return encode(decode(json));
			long createdAt = o.getLong("createdAt");
			if (createdAt <= 0 || o.length() != 3)
				throw new IllegalArgumentException("Lápida de objetivo inválida");
			return tombstone(syncId, createdAt);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}

	public static long createdAt(String json) {
		try {
			return new JSONObject(json).optLong("createdAt", 0);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Objetivo inválido", e);
		}
	}
}
