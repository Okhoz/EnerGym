package com.energym.app.progress;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
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
		ladder(all, "🏋️", sessions, new int[]{1, 10, 25, 50, 100, 250, 500}, n -> Txt.n(R.plurals.badge_workouts, n),
			Txt.t(R.string.badge_workouts_what));
		ladder(all, "🔥", weekStreak, new int[]{2, 4, 8, 12, 26, 52}, n -> Txt.n(R.plurals.badge_streak, n),
			Txt.t(R.string.badge_streak_what));
		ladder(all, "🏗️", volumeKg / 1000, new int[]{1, 10, 50, 100, 500}, n -> Txt.t(R.string.badge_tonnes, n),
			Txt.t(R.string.badge_tonnes_what));
		ladder(all, "⏱", minutes / 60, new int[]{10, 50, 100, 250}, n -> Txt.t(R.string.badge_hours, n),
			Txt.t(R.string.badge_hours_what));
		ladder(all, "🏆", records, new int[]{1, 10, 25, 50, 100}, n -> Txt.n(R.plurals.badge_records, n),
			Txt.t(R.string.badge_records_what));
		ladder(all, "🥗", foodDays, new int[]{1, 7, 30, 100}, n -> Txt.n(R.plurals.badge_food_days, n),
			Txt.t(R.string.badge_food_days_what));
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
				out.add(new Badge(icon, name.of(steps[i]), earned ? Txt.t(R.string.badge_earned) :
					Txt.t(R.string.badge_progress, format(value), steps[i], what), value, steps[i]));
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
