package com.energym.app.progress;

import com.energym.app.domain.*;

import java.util.*;

/**
 * MODEL del resumen que se comparte como tarjeta: ejercicios hechos, volumen, duración y récords de una sesión
 * terminada. Sin Android ni textos: la vista decide cómo se dibuja y se traduce.
 */
public final class ShareSummary {
	/** Lo hecho en un ejercicio: series válidas y la mejor (más kg; a igualdad, más repeticiones). */
	public static final class Line {
		public final String name;
		public final MetricType type;
		public final int sets, topReps;
		public final double topKg, distanceKm;
		public final boolean record;

		Line(String name, MetricType type, int sets, int topReps, double topKg, double distanceKm, boolean record) {
			this.name = name;
			this.type = type;
			this.sets = sets;
			this.topReps = topReps;
			this.topKg = topKg;
			this.distanceKm = distanceKm;
			this.record = record;
		}
	}

	public final String title;
	public final long completedAt;
	public final long minutes;
	public final double volumeKg;
	public final List<Line> lines;

	private ShareSummary(String title, long completedAt, long minutes, double volumeKg, List<Line> lines) {
		this.title = title;
		this.completedAt = completedAt;
		this.minutes = minutes;
		this.volumeKg = volumeKg;
		this.lines = Collections.unmodifiableList(lines);
	}

	/** Ejercicios de la sesión en los que se batió un récord personal. */
	public List<String> records() {
		List<String> names = new ArrayList<>();
		for (Line l : lines)
			if (l.record)
				names.add(l.name);
		return names;
	}

	public boolean hasRecord() {
		return !records().isEmpty();
	}

	/**
	 * Resumen de una sesión terminada. El récord se compara con las sesiones terminadas antes que ella (misma
	 * regla que {@link Achievements#records}: 1RM estimado, o repeticiones a peso corporal).
	 */
	public static ShareSummary of(Session session, List<Session> history) {
		Objects.requireNonNull(session, "session");
		if (session.getStatus() != Session.Status.FINISHED)
			throw new IllegalArgumentException("Solo se comparten sesiones terminadas");
		List<Line> lines = new ArrayList<>();
		for (RoutineItem item : session.getRoutine().getItems()) {
			Exercise e = item.getExercise();
			Map<String, String> values = session.getResults().get(e.getId());
			if (values == null)
				continue;
			int sets = 0, topReps = 0;
			double topKg = 0;
			for (SetLog.Entry s : SetLog.of(values))
				if (s.counts()) {
					sets++;
					if (s.kg > topKg || (s.kg == topKg && s.reps > topReps)) {
						topKg = s.kg;
						topReps = s.reps;
					}
				}
			boolean record = false;
			if (e.getMetricType() == MetricType.STRENGTH) {
				Double before = previousBest(e.getId(), session, history);
				record = before != null && score(values) > before + 1e-9;
			}
			lines.add(new Line(e.getName(), e.getMetricType(), sets, topReps, topKg,
				e.getMetricType() == MetricType.ENDURANCE ? ProgressStats.number(values.get("distanceKm")) : 0, record));
		}
		long minutes = Math.max(0, session.getCompletedAt() - session.getStartedAt()) / 60000;
		return new ShareSummary(session.getRoutine().getTitle(), session.getCompletedAt(), minutes,
			ProgressStats.volume(session), lines);
	}

	/** Mejor marca previa del ejercicio, o null si nunca se hizo (la primera vez no es récord). */
	private static Double previousBest(String exerciseId, Session session, List<Session> history) {
		Double best = null;
		for (Session s : history) {
			if (s.getStatus() != Session.Status.FINISHED || s.getSyncId().equals(session.getSyncId()) ||
				s.getCompletedAt() >= session.getCompletedAt())
				continue;
			Map<String, String> values = s.getResults().get(exerciseId);
			if (values == null || SetLog.of(values).isEmpty())
				continue;
			double score = score(values);
			best = best == null ? score : Math.max(best, score);
		}
		return best;
	}

	private static double score(Map<String, String> values) {
		List<SetLog.Entry> sets = SetLog.of(values);
		double top = SetLog.best(sets);
		if (top == 0)
			for (SetLog.Entry e : sets)
				if (e.counts())
					top = Math.max(top, e.reps / 1000.0);
		return top;
	}
}
