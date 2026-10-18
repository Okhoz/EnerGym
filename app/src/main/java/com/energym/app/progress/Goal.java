package com.energym.app.progress;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.util.*;

/**
 * MODEL inmutable: objetivo que la persona se marca y cuyo avance se calcula con sus sesiones y su peso.
 */
public final class Goal {
	public enum Type {
		SESSIONS_PER_WEEK(R.string.goal_sessions_week, R.string.goal_unit_sessions, 1, 14, true),
		MINUTES_PER_WEEK(R.string.hist_bars_minutes, R.string.unit_min, 10, 2000, true),
		VOLUME_PER_WEEK(R.string.hist_bars_kg, R.string.unit_kg, 50, 200000, true),
		DISTANCE_PER_WEEK(R.string.hist_bars_km, R.string.unit_km, 1, 500, true),
		TARGET_WEIGHT(R.string.goal_target_weight, R.string.unit_kg, 30, 300, false),
		EXERCISE_LOAD(R.string.goal_exercise_load, R.string.unit_kg, 1, 500, false);

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
			return Txt.t(unit);
		}
	}

	public final String id, exerciseId, exerciseName;
	public final Type type;
	public final double target, start;
	public final long createdAt;

	/**
	 * @param start valor de partida (peso al crear el objetivo de peso); 0 en el resto.
	 */
	public Goal(String id, Type type, double target, double start, String exerciseId, String exerciseName,
		long createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		if (!id.matches("[A-Za-z0-9_-]{1,64}"))
			throw new IllegalArgumentException(Txt.t(R.string.goal_invalid_id));
		if (Double.isNaN(target) || target < type.min || target > type.max)
			throw new IllegalArgumentException(Txt.t(R.string.goal_range, type.display(), fmt(type.min), fmt(type.max),
				type.unit()));
		if (type == Type.EXERCISE_LOAD && (exerciseId == null || exerciseId.trim().isEmpty()))
			throw new IllegalArgumentException(Txt.t(R.string.goal_pick_exercise));
		if (Double.isNaN(start) || start < 0 || createdAt <= 0)
			throw new IllegalArgumentException(Txt.t(R.string.goal_invalid));
		this.target = target;
		this.start = start;
		this.exerciseId = type == Type.EXERCISE_LOAD ? exerciseId.trim() : "";
		this.exerciseName = type == Type.EXERCISE_LOAD ? (exerciseName == null ? "" : exerciseName.trim()) : "";
		this.createdAt = createdAt;
	}

	public static Goal create(Type type, double target, double start, String exerciseId, String exerciseName) {
		return new Goal(UUID.randomUUID().toString(), type, target, start, exerciseId, exerciseName,
			System.currentTimeMillis());
	}

	public String title() {
		return type == Type.EXERCISE_LOAD ? exerciseName + ": " + fmt(target) + " kg" :
			type.display() + ": " + fmt(target) + " " + type.unit();
	}

	static String fmt(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
	}
}
