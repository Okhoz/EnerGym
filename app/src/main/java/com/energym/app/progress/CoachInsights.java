package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;

import java.time.*;
import java.util.*;

/**
 * MODELO: lo que un entrenador vería en el historial antes de responder. Cuánto lleva sin entrenar, si el esfuerzo
 * (RPE) va alto o bajo, qué ejercicios llevan estancados, qué grupos musculares ha descuidado y las notas con
 * molestias. Son hechos calculados con sus datos reales, para que la IA no tenga que deducirlos.
 */
public final class CoachInsights {
	/** Marca de la línea de grupos descuidados (la usa el entrenador para orientar la rutina si no pide nada). */
	public static final String NEGLECTED = "Sin trabajar en 2 semanas: ";
	private static final List<Area> MAIN = Arrays.asList(Area.LEGS, Area.GLUTES, Area.CHEST, Area.BACK,
		Area.SHOULDERS, Area.ARMS, Area.CORE);
	private static final java.util.regex.Pattern PAIN = java.util.regex.Pattern.compile(
		"(?i)(dolor|duele|molest|lesi|tir[oó]n|pinchazo|sobrecarga|inflama)");

	private CoachInsights() {
	}

	public static List<String> analyze(List<Session> history, ZoneId zone, LocalDate today) {
		List<Session> done = new ArrayList<>();
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED)
				done.add(s);
		done.sort(Comparator.comparingLong(Session::getCompletedAt));
		List<String> out = new ArrayList<>();
		if (done.isEmpty())
			return out;
		LocalDate last = day(done.get(done.size() - 1), zone);
		long rest = Duration.between(last.atStartOfDay(), today.atStartOfDay()).toDays();
		if (rest >= 7)
			out.add("Lleva " + rest + " días sin entrenar: vuelta suave, sin subir cargas el primer día.");
		else if (rest == 0)
			out.add("Ya ha entrenado hoy.");
		effort(done, out);
		stalls(done, out);
		balance(history, zone, today, out);
		notes(done, zone, out);
		return out;
	}

	/** Esfuerzo de las 2 últimas sesiones (RPE de las series efectivas, o el esfuerzo global anotado). */
	private static void effort(List<Session> done, List<String> out) {
		double sum = 0;
		int rated = 0;
		for (Session s : done.subList(Math.max(0, done.size() - 2), done.size()))
			for (Map<String, String> values : s.getResults().values()) {
				boolean perSet = false;
				for (SetLog.Entry e : SetLog.parse(values.get(SetLog.KEY)))
					if (e.counts() && e.rpe > 0) {
						sum += e.rpe;
						rated++;
						perSet = true;
					}
				if (!perSet)
					try {
						double effort = Double.parseDouble(values.getOrDefault("effort", ""));
						if (effort > 0) {
							sum += effort;
							rated++;
						}
					} catch (NumberFormatException ignored) {
						// Sin esfuerzo anotado.
					}
			}
		if (rated < 3)
			return;
		double avg = Math.round(sum / rated * 10) / 10.0;
		if (avg >= 9)
			out.add("Esfuerzo muy alto en las últimas sesiones (RPE medio " + fmt(avg) + "): conviene mantener o bajar " +
				"cargas y descansar bien.");
		else if (avg <= 6.5)
			out.add("Va sobrado (RPE medio " + fmt(avg) + "): puede subir carga o repeticiones.");
		else
			out.add("Esfuerzo adecuado (RPE medio " + fmt(avg) + ").");
	}

	/** Ejercicios de fuerza hechos 3 veces o más cuya mejor serie no mejora en las 3 últimas. */
	private static void stalls(List<Session> done, List<String> out) {
		Map<String, List<Double>> best = new LinkedHashMap<>();
		Map<String, String> names = new HashMap<>();
		for (Session s : done)
			for (RoutineItem item : s.getRoutine().getItems()) {
				Exercise e = item.getExercise();
				Map<String, String> values = s.getResults().get(e.getId());
				if (values == null || e.getMetricType() != MetricType.STRENGTH)
					continue;
				List<SetLog.Entry> sets = SetLog.of(values);
				double top = SetLog.best(sets);
				if (top == 0)
					for (SetLog.Entry x : sets)
						if (x.counts())
							top = Math.max(top, x.reps);
				if (top > 0) {
					best.computeIfAbsent(e.getId(), k -> new ArrayList<>()).add(top);
					names.put(e.getId(), e.getName());
				}
			}
		List<String> stalled = new ArrayList<>();
		for (Map.Entry<String, List<Double>> e : best.entrySet()) {
			List<Double> v = e.getValue();
			if (v.size() >= 3 && v.get(v.size() - 1) <= v.get(v.size() - 3) + 1e-9)
				stalled.add(names.get(e.getKey()));
		}
		if (!stalled.isEmpty())
			out.add("Estancado (3 sesiones sin mejorar): " + String.join(", ", stalled.subList(0, Math.min(3,
				stalled.size()))) + ". Propón cambiar repeticiones, una variante o una semana de descarga.");
	}

	/** Grupos principales sin ninguna serie en 14 días, si ha entrenado otros en ese tiempo. */
	private static void balance(List<Session> history, ZoneId zone, LocalDate today, List<String> out) {
		List<MuscleBreakdown.Share> shares = MuscleBreakdown.of(history, zone, today.minusDays(13), today);
		if (shares.isEmpty())
			return;
		Set<Area> worked = EnumSet.noneOf(Area.class);
		for (MuscleBreakdown.Share s : shares)
			if (s.sets > 0)
				worked.add(s.area);
		List<String> missing = new ArrayList<>();
		for (Area a : MAIN)
			if (!worked.contains(a))
				missing.add(a.label);
		if (!missing.isEmpty() && missing.size() < MAIN.size())
			out.add(NEGLECTED + String.join(", ", missing) + ".");
	}

	/** Notas de las 3 últimas sesiones; las de molestias van primero y con aviso. */
	private static void notes(List<Session> done, ZoneId zone, List<String> out) {
		List<String> pain = new ArrayList<>(), other = new ArrayList<>();
		for (Session s : done.subList(Math.max(0, done.size() - 3), done.size()))
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> values = s.getResults().get(item.getExercise().getId());
				String note = values == null ? "" : values.getOrDefault(SetLog.NOTE, "").trim();
				if (note.isEmpty())
					continue;
				String line = item.getExercise().getName() + " (" + day(s, zone) + "): «" +
					(note.length() > 120 ? note.substring(0, 119) + "…" : note) + "»";
				(PAIN.matcher(note).find() ? pain : other).add(line);
			}
		if (!pain.isEmpty())
			out.add("⚠ Anotó molestias: " + String.join("; ", pain) + ". No cargues esa zona y, si persiste, " +
				"recomienda consultar a un profesional.");
		if (!other.isEmpty())
			out.add("Sus notas: " + String.join("; ", other.subList(0, Math.min(2, other.size()))) + ".");
	}

	private static LocalDate day(Session s, ZoneId zone) {
		return Instant.ofEpochMilli(s.getCompletedAt()).atZone(zone).toLocalDate();
	}

	private static String fmt(double v) {
		return SetLog.format(v).replace('.', ',');
	}
}
