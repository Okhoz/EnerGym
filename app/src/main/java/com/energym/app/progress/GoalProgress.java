package com.energym.app.progress;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.time.LocalDate;
import java.util.*;

/**
 * MODEL: avance de un objetivo con los datos reales (sesiones finalizadas y registro de peso). Sin Android.
 */
public final class GoalProgress {
	public final Goal goal;
	public final double current, ratio;
	public final double[] series;
	public final String[] labels;
	public final boolean reached;

	private GoalProgress(Goal goal, double current, double ratio, double[] series, String[] labels) {
		this.goal = goal;
		this.current = current;
		this.ratio = Math.max(0, Math.min(1, ratio));
		this.series = series;
		this.labels = labels;
		reached = ratio >= 1;
	}

	public static GoalProgress of(Goal goal, ProgressStats stats, SortedMap<LocalDate, Double> weights) {
		switch (goal.type) {
			case SESSIONS_PER_WEEK:
				return weekly(goal, stats, stats.sessionsPerWeek());
			case MINUTES_PER_WEEK:
				return weekly(goal, stats, stats.minutesPerWeek());
			case VOLUME_PER_WEEK:
				return weekly(goal, stats, stats.volumePerWeek());
			case DISTANCE_PER_WEEK:
				return weekly(goal, stats, stats.distancePerWeek());
			case TARGET_WEIGHT:
				return weight(goal, weights);
			case CUSTOM:
				return custom(goal);
			default:
				double[] loads = stats.loadHistory(goal.exerciseId);
				double best = 0;
				for (double v : loads)
					best = Math.max(best, v);
				String[] labels = new String[loads.length];
				for (int i = 0; i < labels.length; i++)
					labels[i] = Integer.toString(i + 1);
				return new GoalProgress(goal, best, best / goal.target, loads, labels);
		}
	}

	private static GoalProgress weekly(Goal goal, ProgressStats stats, double[] weeks) {
		double current = weeks[weeks.length - 1];
		return new GoalProgress(goal, current, current / goal.target, weeks, stats.weekLabels());
	}

	/**
	 * Avance hacia el peso objetivo en cualquier dirección (bajar o subir) desde el peso de partida.
	 */
	private static GoalProgress weight(Goal goal, SortedMap<LocalDate, Double> weights) {
		List<LocalDate> days = new ArrayList<>(weights.keySet());
		double[] series = new double[days.size()];
		String[] labels = new String[days.size()];
		for (int i = 0; i < series.length; i++) {
			series[i] = weights.get(days.get(i));
			labels[i] = days.get(i).getDayOfMonth() + "/" + days.get(i).getMonthValue();
		}
		if (series.length == 0)
			return new GoalProgress(goal, 0, 0, series, labels);
		double current = series[series.length - 1];
		double start = goal.start > 0 ? goal.start : series[0];
		double total = start - goal.target;
		double ratio = Math.abs(total) < 0.05 ? (Math.abs(current - goal.target) < 0.05 ? 1 : 0) :
			(start - current) / total;
		return new GoalProgress(goal, current, ratio, series, labels);
	}

	/**
	 * Meta personal: cuenta el último valor apuntado, desde el de partida hacia la meta (sirve para subir, como
	 * «leer 12 libros», y para bajar, como «correr 5 km en 25 min»).
	 */
	private static GoalProgress custom(Goal goal) {
		List<LocalDate> days = new ArrayList<>(goal.entries.keySet());
		double[] series = new double[days.size()];
		String[] labels = new String[days.size()];
		for (int i = 0; i < series.length; i++) {
			series[i] = goal.entries.get(days.get(i));
			labels[i] = days.get(i).getDayOfMonth() + "/" + days.get(i).getMonthValue();
		}
		if (series.length == 0)
			return new GoalProgress(goal, goal.start, 0, series, labels);
		double current = series[series.length - 1];
		return new GoalProgress(goal, current, (current - goal.start) / (goal.target - goal.start), series, labels);
	}

	public String status() {
		if (goal.type == Goal.Type.CUSTOM) {
			String unit = goal.unit().isEmpty() ? "" : " " + goal.unit();
			return series.length == 0 ? Txt.t(R.string.goal_status_custom_empty) :
				Txt.t(R.string.goal_status_custom, Goal.fmt(round(current)) + unit, Goal.fmt(goal.target) + unit);
		}
		String unit = goal.type.unit();
		if (goal.type == Goal.Type.TARGET_WEIGHT)
			return series.length == 0 ? Txt.t(R.string.hist_goal_log_weight) :
				Txt.t(R.string.goal_status_weight, Goal.fmt(round(current)), Goal.fmt(goal.target));
		if (goal.type == Goal.Type.EXERCISE_LOAD)
			return series.length == 0 ? Txt.t(R.string.goal_status_load_empty) :
				Txt.t(R.string.goal_status_load, Goal.fmt(round(current)), Goal.fmt(goal.target));
		return Txt.t(R.string.goal_status_week, Goal.fmt(round(current)), Goal.fmt(goal.target), unit);
	}

	private static double round(double v) {
		return Math.round(v * 10) / 10.0;
	}
}
