package com.energym.app.domain;

import com.energym.app.domain.RoutineRequest.Area;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * MODELO del plan semanal al estilo FitNotes: una rutina por día de entreno, repartida por grupos musculares
 * (Empuje / Tirón / Pierna, Torso / Pierna…) según los días por semana del perfil o lo que se pida.
 * Cada día se titula «Día 2/3 · Tirón» para saber cuál toca después.
 */
public final class WeeklyPlan {
	public static final class Day {
		public final String name;
		public final Set<Area> areas;

		Day(String name, Area... areas) {
			this.name = name;
			this.areas = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(areas)));
		}
	}

	private static final Day FULL = new Day("Cuerpo completo", Area.LEGS, Area.CHEST, Area.BACK, Area.CORE);
	private static final Day UPPER = new Day("Torso", Area.CHEST, Area.BACK, Area.SHOULDERS, Area.ARMS);
	private static final Day LOWER = new Day("Pierna", Area.LEGS, Area.GLUTES, Area.CORE);
	private static final Day PUSH = new Day("Empuje", Area.CHEST, Area.SHOULDERS);
	private static final Day PULL = new Day("Tirón", Area.BACK, Area.ARMS);
	private static final Pattern TITLE = Pattern.compile("^Día (\\d)/(\\d) · ");
	/** Cada plan lleva su marca en la explicación para no mezclar días de planes distintos. */
	private static final Pattern PLAN = Pattern.compile("Plan semanal \\(([^)]+)\\)");

	private WeeklyPlan() {
	}

	/** Reparto de días; con «empuje/tirón/pierna» o «torso/pierna» pedidos explícitamente se respeta. */
	public static List<Day> split(int days, String asked) {
		String t = normalize(asked);
		boolean ppl = t.matches(".*\\b(ppl|push pull|empuje|tiron)\\b.*");
		boolean upperLower = t.matches(".*\\b(torso|upper lower|torso pierna)\\b.*") && !ppl;
		boolean full = t.matches(".*\\b(cuerpo completo|full ?body)\\b.*");
		int n = Math.max(1, Math.min(6, days));
		if (full)
			return Collections.nCopies(n, FULL);
		if (ppl)
			return cycle(Arrays.asList(PUSH, PULL, LOWER), Math.max(3, n));
		if (upperLower)
			return cycle(Arrays.asList(UPPER, LOWER), Math.max(2, n));
		switch (n) {
			case 1:
				return Collections.singletonList(FULL);
			case 2:
				return Arrays.asList(UPPER, LOWER);
			case 3:
				return Arrays.asList(PUSH, PULL, LOWER);
			case 4:
				return Arrays.asList(UPPER, LOWER, UPPER, LOWER);
			case 5:
				return Arrays.asList(PUSH, PULL, LOWER, UPPER, LOWER);
			default:
				return cycle(Arrays.asList(PUSH, PULL, LOWER), 6);
		}
	}

	private static List<Day> cycle(List<Day> pattern, int n) {
		List<Day> result = new ArrayList<>();
		for (int i = 0; i < n; i++)
			result.add(pattern.get(i % pattern.size()));
		return result;
	}

	/**
	 * Una rutina por día. Si un nombre se repite (Torso A/B), la semilla cambia y los ejercicios varían.
	 * Los días sin ejercicios compatibles se omiten.
	 */
	public static List<Routine> create(TrainingProfile profile, List<Exercise> catalog, String asked, long seed) {
		RoutineRequest base = RoutineRequest.parse(asked);
		List<Day> days = split(profile.getDaysPerWeek(), asked);
		List<Routine> result = new ArrayList<>();
		Map<String, Integer> repeats = new HashMap<>();
		RoutinePlanner planner = new RoutinePlanner();
		String label = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("d/M HH:mm:ss"));
		for (int i = 0; i < days.size(); i++) {
			Day day = days.get(i);
			int repeat = repeats.merge(day.name, 1, Integer::sum);
			Routine r = planner.create(profile, catalog, RoutineRequest.of(day.areas, base.intensity, base.count),
				seed + i * 7919L);
			if (r.isEmpty())
				continue;
			String name = day.name + (Collections.frequency(days, day) > 1 ? " " + (char) ('A' + repeat - 1) : "");
			result.add(new Routine(r.getProposalId(), 0, "Día " + (i + 1) + "/" + days.size() + " · " + name, profile,
				r.getItems(), "Plan semanal (" + label + ") · día " + (i + 1) + ": " + name.toLowerCase(Locale.ROOT) +
				". " + r.getExplanation(), r.getMetadata()));
		}
		return result;
	}

	/** {día, total} de una rutina de plan («Día 2/3 · Tirón» → {2, 3}); null si no lo es. */
	public static int[] dayOf(Routine routine) {
		Matcher m = TITLE.matcher(routine == null ? "" : routine.getTitle());
		return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
	}

	/** Rutina del día siguiente del mismo plan (vuelve al 1 tras el último); null si no hay. */
	public static Routine next(Routine done, List<Routine> routines) {
		int[] d = dayOf(done);
		if (d == null)
			return null;
		int wanted = d[0] % d[1] + 1;
		String plan = planOf(done);
		for (Routine r : routines) {
			int[] other = dayOf(r);
			if (other != null && other[1] == d[1] && other[0] == wanted && plan.equals(planOf(r)))
				return r;
		}
		return null;
	}

	public static boolean samePlan(Routine a, Routine b) {
		return !planOf(a).isEmpty() && planOf(a).equals(planOf(b));
	}

	private static String planOf(Routine r) {
		Matcher m = PLAN.matcher(r.getExplanation() == null ? "" : r.getExplanation());
		return m.find() ? m.group(1) : "";
	}

	/** ¿Pide un plan de varios días y no una sola rutina? */
	public static boolean asksForPlan(String message) {
		String t = normalize(message);
		return t.matches(".*\\b(plan (semanal|de la semana|de entrenamiento semanal|de \\d dias)|rutina semanal|semana de " +
			"entreno|para (toda )?la semana|split|ppl|push pull|empuje tiron|torso pierna|weider|por dias|cada dia de la semana)\\b.*");
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
