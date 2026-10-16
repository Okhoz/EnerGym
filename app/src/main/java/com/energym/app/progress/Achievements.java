package com.energym.app.progress;

import com.energym.app.domain.*;

import java.util.*;

/**
 * MODELO de logros: medallas que se ganan solas con lo ya guardado (sesiones, constancia, kilos, récords y
 * comidas anotadas). No guarda nada propio: se recalculan siempre a partir del historial.
 */
public final class Achievements {
	public static final class Badge {
		public final String icon, title, detail;
		public final double value, goal;

		Badge(String icon, String title, String detail, double value, double goal) {
			this.icon = icon;
			this.title = title;
			this.detail = detail;
			this.value = value;
			this.goal = goal;
		}

		public boolean earned() {
			return value >= goal;
		}
	}

	private Achievements() {
	}

	/** Los logros de cada familia: el último conseguido y el siguiente por conseguir, o todos si ya están. */
	public static List<Badge> of(int sessions, int weekStreak, double volumeKg, double minutes, int records, int foodDays) {
		List<Badge> all = new ArrayList<>();
		ladder(all, "🏋️", sessions, new int[]{1, 10, 25, 50, 100, 250, 500}, n -> n == 1 ? "Primer entreno" :
			n + " entrenos", "entrenos terminados");
		ladder(all, "🔥", weekStreak, new int[]{2, 4, 8, 12, 26, 52}, n -> n + " semanas seguidas",
			"semanas seguidas entrenando");
		ladder(all, "🏗️", volumeKg / 1000, new int[]{1, 10, 50, 100, 500}, n -> n + " t levantadas",
			"toneladas levantadas en total");
		ladder(all, "⏱", minutes / 60, new int[]{10, 50, 100, 250}, n -> n + " horas entrenando", "horas de entreno");
		ladder(all, "🏆", records, new int[]{1, 10, 25, 50, 100}, n -> n == 1 ? "Primer récord" : n + " récords",
			"récords personales batidos");
		ladder(all, "🥗", foodDays, new int[]{1, 7, 30, 100}, n -> n == 1 ? "Primer día anotado" : n + " días de dieta",
			"días con comidas anotadas");
		return all;
	}

	private interface Name {
		String of(int n);
	}

	private static void ladder(List<Badge> out, String icon, double value, int[] steps, Name name, String what) {
		for (int i = 0; i < steps.length; i++) {
			boolean earned = value >= steps[i];
			boolean nextUp = !earned && (i == 0 || value >= steps[i - 1]);
			if (earned || nextUp)
				out.add(new Badge(icon, name.of(steps[i]), earned ? "Conseguido" :
					format(value) + " de " + steps[i] + " " + what, value, steps[i]));
			if (nextUp)
				break;
		}
	}

	/** Veces que se batió el 1RM estimado (o las repeticiones a peso corporal) de un ejercicio ya hecho antes. */
	public static int records(List<Session> history) {
		List<Session> done = new ArrayList<>();
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED)
				done.add(s);
		done.sort(Comparator.comparingLong(Session::getCompletedAt));
		Map<String, Double> best = new HashMap<>();
		int records = 0;
		for (Session s : done)
			for (Map.Entry<String, Map<String, String>> r : s.getResults().entrySet()) {
				List<SetLog.Entry> sets = SetLog.of(r.getValue());
				if (sets.isEmpty())
					continue;
				double top = SetLog.best(sets);
				if (top == 0)
					for (SetLog.Entry e : sets)
						if (e.counts())
							top = Math.max(top, e.reps / 1000.0);
				Double before = best.get(r.getKey());
				if (before != null && top > before + 1e-9)
					records++;
				if (before == null || top > before)
					best.put(r.getKey(), top);
			}
		return records;
	}

	private static String format(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
	}
}
