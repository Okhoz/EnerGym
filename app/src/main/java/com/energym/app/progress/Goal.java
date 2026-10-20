package com.energym.app.progress;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.time.LocalDate;
import java.util.*;

/**
 * MODEL inmutable: objetivo que la persona se marca y cuyo avance se calcula con sus sesiones y su peso, o con
 * lo que va apuntando a mano en una meta personal. Cumplirla la archiva sin borrarla.
 */
public final class Goal {
	public enum Type {
		SESSIONS_PER_WEEK(R.string.goal_sessions_week, R.string.goal_unit_sessions, 1, 14, true),
		MINUTES_PER_WEEK(R.string.hist_bars_minutes, R.string.unit_min, 10, 2000, true),
		VOLUME_PER_WEEK(R.string.hist_bars_kg, R.string.unit_kg, 50, 200000, true),
		DISTANCE_PER_WEEK(R.string.hist_bars_km, R.string.unit_km, 1, 500, true),
		TARGET_WEIGHT(R.string.goal_target_weight, R.string.unit_kg, 30, 300, false),
		EXERCISE_LOAD(R.string.goal_exercise_load, R.string.unit_kg, 1, 500, false),
		/** Meta con nombre y unidad propios; su avance lo apunta la persona. */
		CUSTOM(R.string.goal_custom, 0, 0, 1000000, false);

		private final int label, unit;
		public final double min, max;
		public final boolean weekly;

		Type(int label, int unit, double min, double max, boolean weekly) {
			this.label = label;
			this.unit = unit;
			this.min = min;
			this.max = max;
			this.weekly = weekly;
		}

		public String display() {
			return Txt.t(label);
		}

		public String unit() {
			return unit == 0 ? "" : Txt.t(unit);
		}
	}

	/** Lo que se puede apuntar en una meta personal: un registro por día, que guarda el último valor. */
	public static final int MAX_ENTRIES = 1000;
	public static final int MAX_NAME = 60, MAX_UNIT = 15;

	public final String id, exerciseId, exerciseName, name, customUnit;
	public final Type type;
	public final double target, start;
	public final long createdAt;
	/** Momento en que se marcó como cumplida; 0 mientras sigue activa. */
	public final long completedAt;
	/** Avance apuntado a mano (meta personal), ordenado por día. */
	public final SortedMap<LocalDate, Double> entries;

	/**
	 * @param start valor de partida (peso al crear el objetivo de peso); 0 en el resto.
	 */
	public Goal(String id, Type type, double target, double start, String exerciseId, String exerciseName,
		long createdAt) {
		this(id, type, target, start, exerciseId, exerciseName, "", "", createdAt, 0, Collections.emptySortedMap());
	}

	public Goal(String id, Type type, double target, double start, String exerciseId, String exerciseName,
		String name, String customUnit, long createdAt, long completedAt, SortedMap<LocalDate, Double> entries) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		if (!id.matches("[A-Za-z0-9_-]{1,64}"))
			throw new IllegalArgumentException(Txt.t(R.string.goal_invalid_id));
		if (type == Type.CUSTOM) {
			if (Double.isNaN(target) || target < 0 || target > type.max)
				throw new IllegalArgumentException(Txt.t(R.string.goal_custom_range, fmt(type.max)));
		} else if (Double.isNaN(target) || target < type.min || target > type.max)
			throw new IllegalArgumentException(Txt.t(R.string.goal_range, type.display(), fmt(type.min), fmt(type.max),
				type.unit()));
		if (type == Type.EXERCISE_LOAD && (exerciseId == null || exerciseId.trim().isEmpty()))
			throw new IllegalArgumentException(Txt.t(R.string.goal_pick_exercise));
		String cleanName = name == null ? "" : name.trim();
		String cleanUnit = customUnit == null ? "" : customUnit.trim();
		if (type == Type.CUSTOM && (cleanName.isEmpty() || cleanName.length() > MAX_NAME))
			throw new IllegalArgumentException(Txt.t(R.string.goal_custom_name, MAX_NAME));
		if (cleanUnit.length() > MAX_UNIT)
			throw new IllegalArgumentException(Txt.t(R.string.goal_custom_unit, MAX_UNIT));
		if (type == Type.CUSTOM && target == start)
			throw new IllegalArgumentException(Txt.t(R.string.goal_custom_same));
		if (Double.isNaN(start) || start < 0 || start > 1000000 || createdAt <= 0 || completedAt < 0)
			throw new IllegalArgumentException(Txt.t(R.string.goal_invalid));
		Objects.requireNonNull(entries, "entries");
		if (entries.size() > MAX_ENTRIES)
			throw new IllegalArgumentException(Txt.t(R.string.goal_invalid));
		for (Map.Entry<LocalDate, Double> e : entries.entrySet())
			if (e.getKey() == null || e.getValue() == null || e.getValue().isNaN() || e.getValue() < 0 ||
				e.getValue() > 1000000)
				throw new IllegalArgumentException(Txt.t(R.string.goal_invalid));
		this.target = target;
		this.start = start;
		this.exerciseId = type == Type.EXERCISE_LOAD ? exerciseId.trim() : "";
		this.exerciseName = type == Type.EXERCISE_LOAD ? (exerciseName == null ? "" : exerciseName.trim()) : "";
		this.name = type == Type.CUSTOM ? cleanName : "";
		this.customUnit = type == Type.CUSTOM ? cleanUnit : "";
		this.createdAt = createdAt;
		this.completedAt = completedAt;
		this.entries = Collections.unmodifiableSortedMap(type == Type.CUSTOM ? new TreeMap<>(entries) : new TreeMap<>());
	}

	public static Goal create(Type type, double target, double start, String exerciseId, String exerciseName) {
		return new Goal(UUID.randomUUID().toString(), type, target, start, exerciseId, exerciseName,
			System.currentTimeMillis());
	}

	public static Goal custom(String name, String unit, double target, double start) {
		return new Goal(UUID.randomUUID().toString(), Type.CUSTOM, target, start, null, null, name, unit,
			System.currentTimeMillis(), 0, Collections.emptySortedMap());
	}

	public boolean completed() {
		return completedAt > 0;
	}

	public Goal complete(long at) {
		return copy(Math.max(1, at), entries);
	}

	public Goal reopen() {
		return copy(0, entries);
	}

	/** Apuntar otra vez el mismo día sustituye el valor de ese día. */
	public Goal withEntry(LocalDate day, double value) {
		if (type != Type.CUSTOM)
			throw new IllegalStateException("Solo las metas personales se apuntan a mano");
		SortedMap<LocalDate, Double> next = new TreeMap<>(entries);
		next.put(Objects.requireNonNull(day, "day"), value);
		return copy(completedAt, next);
	}

	public Goal withoutEntry(LocalDate day) {
		SortedMap<LocalDate, Double> next = new TreeMap<>(entries);
		next.remove(day);
		return copy(completedAt, next);
	}

	private Goal copy(long completed, SortedMap<LocalDate, Double> nextEntries) {
		return new Goal(id, type, target, start, exerciseId, exerciseName, name, customUnit, createdAt, completed,
			nextEntries);
	}

	/** Unidad visible: la propia en una meta personal, la del tipo en el resto. */
	public String unit() {
		return type == Type.CUSTOM ? customUnit : type.unit();
	}

	public String title() {
		if (type == Type.CUSTOM)
			return name + ": " + fmt(target) + (customUnit.isEmpty() ? "" : " " + customUnit);
		return type == Type.EXERCISE_LOAD ? exerciseName + ": " + fmt(target) + " kg" :
			type.display() + ": " + fmt(target) + " " + type.unit();
	}

	public static String fmt(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Txt.decimal(String.format(Locale.ROOT, "%.1f", v));
	}
}
