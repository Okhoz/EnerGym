package com.energym.app.data;

import com.energym.app.nutrition.*;

import org.json.*;

import java.time.LocalDate;
import java.util.*;

/**
 * MODEL: diario de nutrición, perfil físico y peso como registros sincronizables de la cuenta.
 * Quitar un alimento escribe una lápida definitiva para que el borrado también llegue a la nube.
 */
public final class NutritionRepository {
	public static final String BODY_ID = "body";
	private final EnerGymDatabase database;

	public NutritionRepository(EnerGymDatabase database) {
		this.database = Objects.requireNonNull(database, "database");
	}

	public void saveBody(BodyProfile body) {
		database.transaction(() -> database.writeLocal(SyncRecord.Kind.BODY, BODY_ID, encodeBody(body)));
	}

	public BodyProfile body() {
		EnerGymDatabase.Row row = database.find(SyncRecord.Kind.BODY, BODY_ID);
		return row == null ? null : decodeBody(row.record.getPayload());
	}

	public void add(FoodEntry entry) {
		database.transaction(() -> {
			if (database.find(SyncRecord.Kind.FOOD, entry.id) != null)
				throw new IllegalArgumentException("Alimento ya registrado");
			return database.writeLocal(SyncRecord.Kind.FOOD, entry.id, encodeFood(entry));
		});
	}

	/** Actualiza la misma entrada; una lápida nunca vuelve a convertirse en un alimento activo. */
	public void update(FoodEntry entry) {
		database.transaction(() -> {
			EnerGymDatabase.Row row = database.find(SyncRecord.Kind.FOOD, entry.id);
			if (row == null || deleted(row.record.getPayload()))
				throw new IllegalArgumentException("El alimento ya no está disponible en esta cuenta");
			FoodEntry previous = decodeFood(row.record.getPayload());
			if (previous.createdAt != entry.createdAt || !previous.day.equals(entry.day))
				throw new IllegalArgumentException("La edición no puede cambiar la identidad ni el día del registro");
			return database.writeLocal(SyncRecord.Kind.FOOD, entry.id, encodeFood(entry));
		});
	}

	/**
	 * Sustituye el alimento por su lápida; un ID desconocido o ya borrado no escribe nada.
	 */
	public void remove(String id) {
		database.transaction(() -> {
			EnerGymDatabase.Row row = database.find(SyncRecord.Kind.FOOD, id);
			if (row == null || deleted(row.record.getPayload()))
				return null;
			FoodEntry entry = decodeFood(row.record.getPayload());
			return database.writeLocal(SyncRecord.Kind.FOOD, id, tombstone(entry));
		});
	}

	public List<FoodEntry> food(LocalDate from) {
		List<FoodEntry> result = new ArrayList<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.FOOD, null)) {
			if (deleted(row.record.getPayload()))
				continue;
			FoodEntry entry = decodeFood(row.record.getPayload());
			if (!entry.day.isBefore(from))
				result.add(entry);
		}
		result.sort(Comparator.comparing((FoodEntry e) -> e.day).thenComparingLong(e -> e.createdAt));
		return result;
	}

	public void weight(LocalDate day, double kg) {
		database.transaction(() -> database.writeLocal(SyncRecord.Kind.WEIGHT, day.toString(), encodeWeight(day, kg)));
	}

	public SortedMap<LocalDate, Double> weights() {
		SortedMap<LocalDate, Double> result = new TreeMap<>();
		for (EnerGymDatabase.Row row : database.rows(SyncRecord.Kind.WEIGHT, null)) {
			JSONObject o = object(row.record.getPayload());
			result.put(LocalDate.parse(o.optString("day")), weightKg(o));
		}
		return result;
	}

	// ---- Codec canónico (orden fijo de claves) compartido con la frontera Firestore ----

	public static String encodeBody(BodyProfile b) {
		try {
			return new JSONObject().put("sex", b.sex.name()).put("age", b.age).put("heightCm", b.heightCm)
				.put("weightKg", b.weightKg).put("activity", b.activity.name()).put("goal", b.goal.name())
				.put("allergies", b.allergies).put("diet", b.diet.name()).put("cooking", b.cooking.name())
				.put("mealsPerDay", b.mealsPerDay).put("dislikes", b.dislikes).put("budget", b.budget).toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Perfil físico inválido", e);
		}
	}

	/** Un valor desconocido (versión más nueva u omitido) vuelve al valor por defecto en vez de fallar. */
	private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
		try {
			return name == null || name.isEmpty() ? fallback : Enum.valueOf(type, name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	public static BodyProfile decodeBody(String json) {
		try {
			JSONObject o = new JSONObject(json);
			return new BodyProfile(BodyProfile.Sex.valueOf(o.getString("sex")), o.getInt("age"),
				o.getDouble("heightCm"), o.getDouble("weightKg"), BodyProfile.Activity.valueOf(o.getString("activity")),
				BodyProfile.Goal.valueOf(o.getString("goal")), o.getString("allergies"),
				enumOr(BodyProfile.Diet.class, o.optString("diet"), BodyProfile.Diet.OMNIVORE),
				enumOr(BodyProfile.Cooking.class, o.optString("cooking"), BodyProfile.Cooking.NORMAL),
				o.optInt("mealsPerDay", 4), o.optString("dislikes", ""), o.optDouble("budget", 0));
		} catch (JSONException e) {
			throw new IllegalArgumentException("Perfil físico inválido", e);
		}
	}

	public static String encodeFood(FoodEntry e) {
		try {
			return new JSONObject().put("id", e.id).put("day", e.day.toString()).put("meal", e.meal.name())
				.put("name", e.name).put("barcode", e.barcode).put("unit", e.unit.name()).put("amount", e.amount)
				.put("kcal100", e.kcal100).put("protein100", e.protein100).put("carbs100", e.carbs100)
				.put("fat100", e.fat100).put("createdAt", e.createdAt).put("deleted", false).toString();
		} catch (JSONException ex) {
			throw new IllegalArgumentException("Alimento inválido", ex);
		}
	}

	static String tombstone(FoodEntry e) {
		try {
			return new JSONObject().put("id", e.id).put("day", e.day.toString()).put("createdAt", e.createdAt)
				.put("deleted", true).toString();
		} catch (JSONException ex) {
			throw new IllegalArgumentException("Alimento inválido", ex);
		}
	}

	public static boolean deleted(String json) {
		return object(json).optBoolean("deleted", false);
	}

	public static FoodEntry decodeFood(String json) {
		try {
			JSONObject o = new JSONObject(json);
			if (o.optBoolean("deleted", false))
				throw new IllegalArgumentException("Alimento eliminado");
			FoodEntry e = new FoodEntry(o.getString("id"), LocalDate.parse(o.getString("day")),
				FoodEntry.Meal.valueOf(o.getString("meal")), o.getString("name"), o.getString("barcode"),
				FoodEntry.Unit.valueOf(o.getString("unit")), o.getDouble("amount"), o.getDouble("kcal100"),
				o.getDouble("protein100"), o.getDouble("carbs100"), o.getDouble("fat100"), o.getLong("createdAt"));
			if (e.createdAt <= 0)
				throw new IllegalArgumentException("Fecha de alimento inválida");
			return e;
		} catch (JSONException | java.time.DateTimeException ex) {
			throw new IllegalArgumentException("Alimento inválido", ex);
		}
	}

	/**
	 * Valida y devuelve la forma canónica de un registro FOOD (activo o lápida) con su ID y fecha de alta.
	 */
	public static String canonicalFood(String syncId, String json) {
		JSONObject o = object(json);
		if (!syncId.equals(o.optString("id")))
			throw new IllegalArgumentException("Identidad de alimento incorrecta");
		if (!deleted(json))
			return encodeFood(decodeFood(json));
		try {
			LocalDate day = LocalDate.parse(o.getString("day"));
			long createdAt = o.getLong("createdAt");
			if (createdAt <= 0 || o.length() != 4)
				throw new IllegalArgumentException("Lápida de alimento inválida");
			return new JSONObject().put("id", syncId).put("day", day.toString()).put("createdAt", createdAt)
				.put("deleted", true).toString();
		} catch (JSONException | java.time.DateTimeException ex) {
			throw new IllegalArgumentException("Lápida de alimento inválida", ex);
		}
	}

	public static long foodCreatedAt(String json) {
		return object(json).optLong("createdAt", 0);
	}

	public static String encodeWeight(LocalDate day, double kg) {
		if (!(kg >= 30 && kg <= 300))
			throw new IllegalArgumentException("Peso entre 30 y 300 kg.");
		try {
			return new JSONObject().put("day", day.toString()).put("kg", kg).toString();
		} catch (JSONException e) {
			throw new IllegalArgumentException("Peso inválido", e);
		}
	}

	/**
	 * Valida un registro WEIGHT: el ID es la fecha ISO del día.
	 */
	public static String canonicalWeight(String syncId, String json) {
		JSONObject o = object(json);
		try {
			LocalDate day = LocalDate.parse(o.getString("day"));
			if (!day.toString().equals(syncId) || o.length() != 2)
				throw new IllegalArgumentException("Identidad de peso incorrecta");
			return encodeWeight(day, weightKg(o));
		} catch (JSONException | java.time.DateTimeException e) {
			throw new IllegalArgumentException("Peso inválido", e);
		}
	}

	private static double weightKg(JSONObject o) {
		double kg = o.optDouble("kg", Double.NaN);
		if (!(kg >= 30 && kg <= 300))
			throw new IllegalArgumentException("Peso inválido");
		return kg;
	}

	private static JSONObject object(String json) {
		try {
			return new JSONObject(json);
		} catch (JSONException e) {
			throw new IllegalArgumentException("Registro de nutrición inválido", e);
		}
	}
}
