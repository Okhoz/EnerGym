package com.energym.app.domain;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.util.*;

/**
 * MODELO de programas clásicos de fuerza (5/3/1, Empuje/Tirón/Pierna, Cuerpo completo y GZCLP): cada día se
 * convierte en una rutina del catálogo que la persona revisa en el editor antes de guardarla. Las cargas salen
 * de su mejor 1RM estimado (training max = 90 %) y, sin datos, quedan a 0 para ajustarlas en la primera sesión.
 */
public final class ClassicPrograms {
	public enum Program {FIVE_THREE_ONE, PUSH_PULL_LEGS, FULL_BODY, GZCLP}

	/** Hueco de un día: ejercicio, series × repeticiones, % del training max (0 = sin carga calculada) y descanso. */
	public static final class Slot {
		public final String exerciseId;
		public final int sets, reps, restSeconds;
		public final double percent;
		public final String note;

		Slot(String exerciseId, int sets, int reps, double percent, int restSeconds, String note) {
			this.exerciseId = exerciseId;
			this.sets = sets;
			this.reps = reps;
			this.percent = percent;
			this.restSeconds = restSeconds;
			this.note = note;
		}
	}

	public static final class Day {
		public final String name;
		public final List<Slot> slots;

		Day(String name, Slot... slots) {
			this.name = name;
			this.slots = Collections.unmodifiableList(Arrays.asList(slots));
		}
	}

	public static final double TRAINING_MAX = 0.9;
	private static String w531() {
		return Txt.t(R.string.cp_531_week1);
	}

	private ClassicPrograms() {
	}

	public static String title(Program p) {
		switch (p) {
			case FIVE_THREE_ONE:
				return "5/3/1";
			case PUSH_PULL_LEGS:
				return Txt.t(R.string.cp_ppl);
			case FULL_BODY:
				return Txt.t(R.string.df_cuerpo_completo);
			default:
				return "GZCLP";
		}
	}

	public static List<Day> days(Program p) {
		switch (p) {
			case FIVE_THREE_ONE:
				return Arrays.asList(
					new Day(Txt.t(R.string.cp_overhead_press), main("barbell-overhead-press"), bbb("barbell-overhead-press"),
						acc("lat-pulldown", 5, 10)),
					new Day(Txt.t(R.string.cp_deadlift), main("barbell-deadlift"), bbb("barbell-deadlift"), acc("crunch", 5, 15)),
					new Day(Txt.t(R.string.cp_bench_press), main("barbell-bench-press"), bbb("barbell-bench-press"),
						acc("barbell-row", 5, 10)),
					new Day(Txt.t(R.string.cp_squat), main("barbell-back-squat"), bbb("barbell-back-squat"), acc("leg-curl", 5, 10)));
			case PUSH_PULL_LEGS:
				return Arrays.asList(
					new Day(Txt.t(R.string.cp_push), lift("barbell-bench-press", 4, 6, 0.75, 150), lift("barbell-overhead-press", 3, 8, 0.7, 120),
						acc("dumbbell-incline-press", 3, 10), acc("dumbbell-lateral-raise", 3, 15),
						acc("cable-triceps-pushdown", 3, 12)),
					new Day(Txt.t(R.string.cp_pull), lift("barbell-deadlift", 3, 5, 0.75, 180), acc("lat-pulldown", 4, 10),
						acc("seated-cable-row", 3, 10), acc("cable-face-pull", 3, 15), acc("dumbbell-biceps-curl", 3, 12)),
					new Day(Txt.t(R.string.cp_legs), lift("barbell-back-squat", 4, 6, 0.75, 180), lift("barbell-romanian-deadlift", 3, 10, 0.6, 120),
						acc("leg-press", 3, 12), acc("leg-curl", 3, 12), acc("machine-calf-raise", 4, 15)));
			case FULL_BODY:
				return Arrays.asList(
					new Day(Txt.t(R.string.df_cuerpo_completo) + " A", lift("barbell-back-squat", 3, 5, 0.8, 180),
						lift("barbell-bench-press", 3, 5, 0.8, 180), lift("barbell-row", 3, 8, 0.7, 120), acc("forearm-plank", 3, 0)),
					new Day(Txt.t(R.string.df_cuerpo_completo) + " B", lift("barbell-deadlift", 3, 5, 0.8, 180),
						lift("barbell-overhead-press", 3, 5, 0.8, 180), acc("lat-pulldown", 3, 10),
						acc("dumbbell-walking-lunge", 3, 10)));
			default:
				return Arrays.asList(
					new Day("GZCLP A1", t1("barbell-back-squat"), t2("barbell-bench-press"), t3("lat-pulldown")),
					new Day("GZCLP B1", t1("barbell-overhead-press"), t2("barbell-deadlift"), t3("dumbbell-row")),
					new Day("GZCLP A2", t1("barbell-bench-press"), t2("barbell-back-squat"), t3("lat-pulldown")),
					new Day("GZCLP B2", t1("barbell-deadlift"), t2("barbell-overhead-press"), t3("dumbbell-row")));
		}
	}

	private static Slot main(String id) {
		return new Slot(id, 3, 5, 0.85, 180, w531());
	}

	private static Slot bbb(String id) {
		return new Slot(id, 5, 10, 0.5, 90, Txt.t(R.string.cp_bbb));
	}

	private static Slot t1(String id) {
		return new Slot(id, 5, 3, 0.85, 180, Txt.t(R.string.cp_t1));
	}

	private static Slot t2(String id) {
		return new Slot(id, 3, 10, 0.65, 120, Txt.t(R.string.cp_t2));
	}

	private static Slot t3(String id) {
		return new Slot(id, 3, 15, 0, 60, Txt.t(R.string.cp_t3));
	}

	private static Slot lift(String id, int sets, int reps, double percent, int rest) {
		return new Slot(id, sets, reps, percent, rest, "");
	}

	private static Slot acc(String id, int sets, int reps) {
		return new Slot(id, sets, reps, 0, 90, "");
	}

	/** Carga de trabajo: % del training max (90 % del 1RM) redondeada al salto de la barra; 0 sin 1RM. */
	public static double load(double oneRepMax, double percent, double step) {
		if (oneRepMax <= 0 || percent <= 0 || step <= 0)
			return 0;
		return Math.max(step, Math.round(oneRepMax * TRAINING_MAX * percent / step) * step);
	}

	/**
	 * Rutina de un día. Omite los ejercicios que no estén en el catálogo; {@code best} da el mejor 1RM estimado por
	 * id de ejercicio. Null si el día no existe o no queda ningún ejercicio.
	 */
	public static Routine routine(Program p, int day, List<Exercise> catalog, Map<String, Double> best,
		TrainingProfile profile) {
		List<Day> days = days(p);
		if (day < 0 || day >= days.size() || catalog == null)
			return null;
		Day d = days.get(day);
		Map<String, Exercise> byId = new HashMap<>();
		for (Exercise e : catalog)
			byId.put(e.getId(), e);
		List<RoutineItem> items = new ArrayList<>();
		for (Slot s : d.slots) {
			Exercise e = byId.get(s.exerciseId);
			if (e == null)
				continue;
			Map<String, String> prescription = new LinkedHashMap<>();
			prescription.put("sets", Integer.toString(s.sets));
			if (e.getMetricType() == MetricType.ISOMETRIC)
				prescription.put("durationSeconds", "30");
			else {
				prescription.put("repetitions", Integer.toString(s.reps));
				double oneRm = best == null ? 0 : best.getOrDefault(s.exerciseId, 0.0);
				double step = e.getRequiredResources().contains(TrainingProfile.Resource.BARBELL) ? 2.5 : 1;
				prescription.put("loadKg", SetLog.format(load(oneRm, s.percent, step)));
			}
			prescription.put("restSeconds", Integer.toString(s.restSeconds));
			items.add(new RoutineItem(e, prescription, minutes(s), (s.note.isEmpty() ? "" : s.note + ". ") +
				Txt.t(R.string.cp_program, title(p))));
		}
		if (items.isEmpty())
			return null;
		return new Routine(UUID.randomUUID().toString(), 0, title(p) + " · " + d.name, profile, items,
			Txt.t(R.string.cp_explanation, title(p)), new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
	}

	/** Minutos estimados: descansos entre series más unos 40 s por serie. */
	static int minutes(Slot s) {
		return Math.max(1, (int) Math.ceil((s.sets * 40 + (s.sets - 1) * s.restSeconds) / 60.0));
	}
}
