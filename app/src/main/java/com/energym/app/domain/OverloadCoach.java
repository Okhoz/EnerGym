package com.energym.app.domain;

import java.util.*;

/**
 * MODELO: sugerencia de peso y repeticiones al empezar un ejercicio, a partir de las últimas sesiones de ese
 * ejercicio (en cualquier rutina). Doble progresión: con todas las series en el tope del rango sube la carga y
 * vuelve al mínimo de repeticiones; si no, suma una repetición; si falló, mantiene; y tras varias sesiones sin
 * mejorar con la misma carga propone una descarga del ~10 %. El calentamiento no cuenta. Sin textos: la interfaz
 * los pone desde strings.xml.
 */
public final class OverloadCoach {
	/** Sesiones seguidas sin mejorar con la misma carga a partir de las que se propone descargar. */
	public static final int STALL_SESSIONS = 3;
	public static final double DELOAD = 0.9;

	public enum Kind {
		/** Todas las series en el tope: más carga, repeticiones al mínimo del rango. */
		INCREASE_LOAD,
		/** Dentro del rango: misma carga, una repetición más. */
		ADD_REPS,
		/** Por debajo del rango: repetir la misma carga. */
		HOLD,
		/** Estancado varias sesiones: bajar ~10 % y reconstruir. */
		DELOAD
	}

	public static final class Suggestion {
		public final Kind kind;
		public final double kg;
		public final int reps;
		/** Kilos y repeticiones de referencia (la última sesión). */
		public final double lastKg;
		public final int lastReps;
		/** Sesiones seguidas sin mejora (para explicar la descarga). */
		public final int stalled;

		Suggestion(Kind kind, double kg, int reps, double lastKg, int lastReps, int stalled) {
			this.kind = kind;
			this.kg = kg;
			this.reps = reps;
			this.lastKg = lastKg;
			this.lastReps = lastReps;
			this.stalled = stalled;
		}
	}

	/** Rango de repeticiones [mínimo, tope] al que pertenece un objetivo de repeticiones. */
	public static int[] range(int reps) {
		if (reps <= 5)
			return new int[]{3, 5};
		if (reps <= 8)
			return new int[]{6, 8};
		if (reps <= 12)
			return new int[]{8, 12};
		return new int[]{12, 15};
	}

	/**
	 * Salto mínimo realista según el material: 2,5 kg con barra o máquina, 2 kg con mancuernas y 1 kg en lo demás.
	 */
	public static double increment(Set<TrainingProfile.Resource> resources) {
		if (resources == null)
			return 1;
		if (resources.contains(TrainingProfile.Resource.BARBELL) || resources.contains(TrainingProfile.Resource.MACHINE))
			return 2.5;
		return resources.contains(TrainingProfile.Resource.DUMBBELLS) ? 2 : 1;
	}

	private OverloadCoach() {
	}

	/**
	 * @param history series de cada sesión anterior de este ejercicio, de la más antigua a la más reciente.
	 * @param low     mínimo del rango de repeticiones.
	 * @param high    tope del rango de repeticiones.
	 * @param step    salto mínimo de carga en kg.
	 * @return null si no hay ninguna sesión anterior con series efectivas.
	 */
	public static Suggestion suggest(List<List<SetLog.Entry>> history, int low, int high, double step) {
		if (history == null || low < 1 || high < low || step <= 0)
			return null;
		List<List<SetLog.Entry>> sessions = new ArrayList<>();
		for (List<SetLog.Entry> s : history) {
			List<SetLog.Entry> w = working(s);
			if (!w.isEmpty())
				sessions.add(w);
		}
		if (sessions.isEmpty())
			return null;
		List<SetLog.Entry> last = sessions.get(sessions.size() - 1);
		double kg = top(last);
		int minReps = minRepsAt(last, kg);
		if (kg <= 0) {
			// Peso corporal: solo progresan las repeticiones; sin tope ni descarga.
			boolean done = minReps >= low;
			return new Suggestion(done ? Kind.ADD_REPS : Kind.HOLD, 0, done ? minReps + 1 : Math.max(minReps, 1), 0,
				minReps, 0);
		}
		if (minReps >= high)
			return new Suggestion(Kind.INCREASE_LOAD, round(kg + Math.max(step, kg * 0.025), step), low, kg, minReps, 0);
		int stalled = stalled(sessions);
		if (stalled >= STALL_SESSIONS)
			return new Suggestion(Kind.DELOAD, round(kg * DELOAD, step), low, kg, minReps, stalled);
		if (minReps >= low)
			return new Suggestion(Kind.ADD_REPS, kg, Math.min(high, minReps + 1), kg, minReps, stalled);
		return new Suggestion(Kind.HOLD, kg, low, kg, minReps, stalled);
	}

	/**
	 * Sesiones seguidas al final del historial con la misma carga que la última y sin superar el mejor 1RM
	 * estimado de la sesión anterior. Cambiar la carga (también una descarga) reinicia la cuenta.
	 */
	static int stalled(List<List<SetLog.Entry>> sessions) {
		int last = sessions.size() - 1;
		double kg = top(sessions.get(last));
		int count = 0;
		for (int i = last; i > 0; i--) {
			List<SetLog.Entry> s = sessions.get(i), before = sessions.get(i - 1);
			if (Math.abs(top(s) - kg) > 0.001 || Math.abs(top(before) - kg) > 0.001 ||
				SetLog.best(s) > SetLog.best(before) + 0.001)
				break;
			count++;
		}
		// La primera sesión con esa carga también cuenta como sesión sin mejora si las siguientes no la superan.
		return count == 0 ? 0 : count + 1;
	}

	private static List<SetLog.Entry> working(List<SetLog.Entry> sets) {
		List<SetLog.Entry> result = new ArrayList<>();
		if (sets != null)
			for (SetLog.Entry e : sets)
				if (e.counts() && e.kind != SetLog.Kind.DROP && e.reps > 0)
					result.add(e);
		return result;
	}

	private static double top(List<SetLog.Entry> sets) {
		double kg = 0;
		for (SetLog.Entry e : sets)
			kg = Math.max(kg, e.kg);
		return kg;
	}

	private static int minRepsAt(List<SetLog.Entry> sets, double kg) {
		int reps = Integer.MAX_VALUE;
		for (SetLog.Entry e : sets)
			if (Math.abs(e.kg - kg) < 0.001)
				reps = Math.min(reps, e.reps);
		return reps == Integer.MAX_VALUE ? 0 : reps;
	}

	private static double round(double kg, double step) {
		return Math.max(step, Math.round(kg / step) * step);
	}
}
