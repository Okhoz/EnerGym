package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.nutrition.*;
import com.energym.app.progress.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Todo lo que la persona ha hecho en la app, resumido por secciones para el entrenador IA del móvil (nunca sale
 * del teléfono): entrenamiento, progreso, objetivos, cuerpo, alimentación de hoy y de la semana, y su dieta.
 * Cada sección tiene un tope para que el conjunto quepa en la entrada del modelo.
 */
public final class AssistantContext {
	public static final int MAX = 3800;

	private AssistantContext() {
	}

	public static String build(AppController app, NutritionController nutrition, GoalsController goals,
		DietPlanner diet, LocalDate today) {
		return build(app, nutrition, goals, diet, today, null);
	}

	public static String build(AppController app, NutritionController nutrition, GoalsController goals,
		DietPlanner diet, LocalDate today, WaterLog water) {
		return build(app, nutrition, goals, diet, today, water, null);
	}

	public static String build(AppController app, NutritionController nutrition, GoalsController goals,
		DietPlanner diet, LocalDate today, WaterLog water, BodyMeasures measures) {
		if (app == null || app.owner() == null)
			return "";
		List<String> parts = new ArrayList<>();
		add(parts, "ENTRENAMIENTO", app.trainingSummary(), 1300);
		// Lo que vería un entrenador en su historial: esfuerzo, estancamientos, grupos olvidados y molestias.
		List<String> insights = CoachInsights.analyze(app.history, ZoneId.systemDefault(), today);
		if (!insights.isEmpty())
			add(parts, "ANÁLISIS DEL ENTRENADOR", "• " + String.join("\n• ", insights), 650);
		// Cifras exactas para «¿subo peso?»: las calcula la sobrecarga progresiva de la app, no el modelo.
		add(parts, "PRÓXIMA CARGA CALCULADA", nextLoads(app.history), 600);
		add(parts, "TUS CIFRAS (calculadas por la app)", personal(app.history, nutrition), 400);
		ProgressStats stats = new ProgressStats(app.history, ZoneId.systemDefault(), today);
		add(parts, "PROGRESO", progress(stats), 450);
		add(parts, "OBJETIVOS", goals(goals, stats, nutrition), 350);
		String drink = water == null || nutrition == null ? "" : "\nAgua hoy: " + WaterLog.liters(water.ml(today)) +
			" de " + WaterLog.liters(WaterLog.goal(nutrition.effectiveBody())) + ".";
		add(parts, "CUERPO Y NUTRICIÓN", body(nutrition, today) + drink, 760);
		add(parts, "SU DIETA CON IA", diet(diet), 350);
		add(parts, "MEDIDAS", measures(measures), 200);
		String text = String.join("\n", parts);
		return text.length() > MAX ? text.substring(0, MAX - 1) + "…" : text;
	}

	/**
	 * Para los últimos ejercicios de fuerza entrenados: qué toca la próxima vez según la sobrecarga progresiva
	 * (más carga, una repetición más, repetir o descargar). Una línea por ejercicio, el más reciente primero.
	 */
	static String nextLoads(List<Session> history) {
		List<Session> done = new ArrayList<>();
		for (Session s : history)
			if (s.getStatus() == Session.Status.FINISHED)
				done.add(s);
		done.sort(Comparator.comparingLong(Session::getCompletedAt));
		Map<String, List<List<SetLog.Entry>>> sets = new LinkedHashMap<>();
		Map<String, RoutineItem> items = new HashMap<>();
		for (Session s : done)
			for (RoutineItem item : s.getRoutine().getItems()) {
				Exercise e = item.getExercise();
				Map<String, String> values = s.getResults().get(e.getId());
				if (e.getMetricType() != MetricType.STRENGTH || values == null)
					continue;
				List<SetLog.Entry> logged = SetLog.of(values);
				if (logged.isEmpty())
					continue;
				// Se vuelve a insertar para que el último ejercicio entrenado quede al final.
				List<List<SetLog.Entry>> list = sets.remove(e.getId());
				if (list == null)
					list = new ArrayList<>();
				list.add(logged);
				sets.put(e.getId(), list);
				items.put(e.getId(), item);
			}
		List<String> ids = new ArrayList<>(sets.keySet());
		Collections.reverse(ids);
		List<String> lines = new ArrayList<>();
		for (String id : ids) {
			if (lines.size() >= 5)
				break;
			RoutineItem item = items.get(id);
			int target;
			try {
				target = Integer.parseInt(item.getPrescription().getOrDefault("repetitions", "10").trim());
			} catch (NumberFormatException e) {
				target = 10;
			}
			int[] range = OverloadCoach.range(target);
			OverloadCoach.Suggestion s = OverloadCoach.suggest(sets.get(id), range[0], range[1],
				OverloadCoach.increment(item.getExercise().getRequiredResources()));
			if (s == null)
				continue;
			String last = (s.lastKg > 0 ? SetLog.format(s.lastKg) + " kg" : "peso corporal") + " × " + s.lastReps;
			String next;
			switch (s.kind) {
				case INCREASE_LOAD:
					next = "sube a " + SetLog.format(s.kg) + " kg y busca " + s.reps + " repeticiones";
					break;
				case ADD_REPS:
					next = (s.kg > 0 ? "mismo peso (" + SetLog.format(s.kg) + " kg), " : "") + "busca " + s.reps +
						" repeticiones";
					break;
				case DELOAD:
					next = "lleva " + s.stalled + " sesiones sin mejorar: descarga a " + SetLog.format(s.kg) + " kg × " +
						s.reps;
					break;
				default:
					next = "repite " + (s.kg > 0 ? SetLog.format(s.kg) + " kg " : "") + "hasta hacer " + s.reps +
						" repeticiones en todas las series";
			}
			lines.add(item.getExercise().getSourceName() + ": última vez " + last + " (rango " + range[0] + "–" + range[1] +
				") → " + next + ".");
		}
		return String.join("\n", lines);
	}

	/**
	 * Números personales que un entrenador tendría a mano: proteína y agua según su peso y el 1RM estimado de sus
	 * ejercicios de fuerza (Epley con su mejor serie). Así el modelo no tiene que calcular nada.
	 */
	static String personal(List<Session> history, NutritionController nutrition) {
		List<String> lines = new ArrayList<>();
		BodyProfile b = nutrition == null ? null : nutrition.effectiveBody();
		if (b != null && b.weightKg > 0)
			lines.add("Proteína recomendada: " + Math.round(b.weightKg * 1.6) + "–" + Math.round(b.weightKg * 2.2) +
				" g al día (1,6–2,2 g/kg con " + NutritionController.format(b.weightKg) + " kg). Agua: unos " +
				NutritionController.format(Math.round(b.weightKg * 0.035 * 10) / 10.0) + " L al día.");
		Map<String, Double> best = new LinkedHashMap<>();
		for (Session s : history) {
			if (s.getStatus() != Session.Status.FINISHED)
				continue;
			for (RoutineItem item : s.getRoutine().getItems()) {
				Exercise e = item.getExercise();
				Map<String, String> values = s.getResults().get(e.getId());
				if (e.getMetricType() != MetricType.STRENGTH || values == null)
					continue;
				double oneRm = SetLog.best(SetLog.of(values));
				if (oneRm > 0)
					best.merge(e.getSourceName(), oneRm, Math::max);
			}
		}
		List<Map.Entry<String, Double>> top = new ArrayList<>(best.entrySet());
		top.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
		List<String> rms = new ArrayList<>();
		for (Map.Entry<String, Double> e : top.subList(0, Math.min(4, top.size())))
			rms.add(e.getKey() + " " + NutritionController.format(Math.round(e.getValue() * 2) / 2.0) + " kg");
		if (!rms.isEmpty())
			lines.add("1RM estimado (mejor serie): " + String.join(", ", rms) + ".");
		return String.join("\n", lines);
	}

	private static void add(List<String> parts, String title, String body, int max) {
		if (body == null || body.trim().isEmpty())
			return;
		String b = body.trim();
		parts.add("[" + title + "]\n" + (b.length() > max ? b.substring(0, max - 1) + "…" : b));
	}

	static String progress(ProgressStats stats) {
		if (stats.total() == 0)
			return "Aún no ha terminado ninguna sesión.";
		StringBuilder s = new StringBuilder();
		s.append("Total: ").append(stats.total()).append(" sesiones, ").append(Math.round(stats.totalMinutes()))
			.append(" min, ").append(stats.exercisesDone()).append(" ejercicios");
		if (stats.totalVolume() > 0)
			s.append(", ").append(Math.round(stats.totalVolume())).append(" kg levantados");
		if (stats.totalDistance() > 0)
			s.append(", ").append(NutritionController.format(Math.round(stats.totalDistance() * 10) / 10.0)).append(" km");
		s.append(".\nRacha: ").append(stats.weekStreak()).append(" semanas seguidas; ").append(stats.daysThisMonth())
			.append(" días entrenados este mes.");
		ProgressStats.Trend t = stats.sessionsTrend();
		if (t.comparable())
			s.append(" Últimas 4 semanas frente a las 4 anteriores: ").append(t.percent() >= 0 ? "+" : "")
				.append(Math.round(t.percent())).append(" % de sesiones.");
		List<ProgressStats.Improvement> best = stats.improvements();
		if (!best.isEmpty()) {
			s.append("\nRécords: ");
			List<String> records = new ArrayList<>();
			for (int i = 0; i < Math.min(3, best.size()); i++) {
				ProgressStats.Improvement m = best.get(i);
				records.add(m.name + " " + NutritionController.format(m.first) + "→" + NutritionController.format(m.best) + " kg");
			}
			s.append(String.join("; ", records)).append('.');
		}
		return s.toString();
	}

	static String goals(GoalsController goals, ProgressStats stats, NutritionController nutrition) {
		if (goals == null || goals.goals.isEmpty())
			return "";
		SortedMap<LocalDate, Double> weights = nutrition == null ? new TreeMap<>() : nutrition.weights;
		List<String> lines = new ArrayList<>();
		for (Goal goal : goals.goals) {
			try {
				GoalProgress p = GoalProgress.of(goal, stats, weights);
				lines.add("- " + goal.title() + ": " + p.status() + (p.reached ? " (cumplido)" : ""));
			} catch (RuntimeException ignored) {
				lines.add("- " + goal.title());
			}
		}
		return String.join("\n", lines);
	}

	static String body(NutritionController n, LocalDate today) {
		if (n == null)
			return "";
		StringBuilder s = new StringBuilder();
		BodyProfile b = n.effectiveBody();
		if (b != null) {
			s.append(b.age).append(" años, ").append(NutritionController.format(b.heightCm)).append(" cm, ")
				.append(NutritionController.format(b.weightKg)).append(" kg, ")
				.append(b.sex == BodyProfile.Sex.FEMALE ? "mujer" : "hombre").append(". Objetivo de dieta: ")
				.append(b.goal.label).append(". Alimentación ").append(b.diet.label.toLowerCase(Locale.ROOT)).append(", ")
				.append(b.mealsPerDay).append(" comidas al día");
			if (b.monthlyBudget > 0)
				s.append(", presupuesto ").append(NutritionController.format(b.monthlyBudget)).append(" €/mes para comida");
			s.append('.');
			if (!b.allergies.isEmpty())
				s.append(" Alergias: ").append(b.allergies).append('.');
			if (!b.dislikes.isEmpty())
				s.append(" No toma: ").append(b.dislikes).append('.');
			BodyProfile.Targets t = b.targets();
			if (t != null)
				s.append("\nObjetivo diario: ").append(Math.round(t.kcal)).append(" kcal, P ").append(Math.round(t.protein))
					.append(" g, H ").append(Math.round(t.carbs)).append(" g, G ").append(Math.round(t.fat)).append(" g.");
		}
		SortedMap<LocalDate, Double> weights = n.weightHistory();
		if (weights.size() > 1) {
			LocalDate last = weights.firstKey(), first = weights.lastKey();
			s.append("\nPeso: ").append(NutritionController.format(weights.get(first))).append(" kg (").append(first)
				.append(") → ").append(NutritionController.format(weights.get(last))).append(" kg (").append(last).append(").");
		}
		List<FoodEntry> eaten = n.entriesOn(today);
		if (!eaten.isEmpty()) {
			double[] t = n.totals(today);
			List<String> foods = new ArrayList<>();
			for (FoodEntry e : eaten)
				foods.add(e.name + " " + Math.round(e.kcal()) + " kcal");
			s.append("\nHoy ha comido ").append(Math.round(t[0])).append(" kcal (P ").append(Math.round(t[1]))
				.append(" g, H ").append(Math.round(t[2])).append(" g, G ").append(Math.round(t[3])).append(" g): ")
				.append(String.join(", ", foods)).append('.');
		} else if (b != null)
			s.append("\nHoy aún no ha anotado comidas.");
		String week = n.summary();
		if (!week.isEmpty())
			s.append('\n').append(week);
		return s.toString();
	}

	/** Última medición y cambio desde la primera (cm o %). */
	static String measures(BodyMeasures measures) {
		if (measures == null || measures.days().isEmpty())
			return "";
		LocalDate last = measures.days().lastKey();
		List<String> parts = new ArrayList<>();
		for (Map.Entry<BodyMeasures.Part, Double> e : measures.days().get(last).entrySet()) {
			double[] change = measures.change(e.getKey());
			parts.add(e.getKey().label + " " + NutritionController.format(e.getValue()) + " " + e.getKey().unit +
				(change == null ? "" : " (" + (change[1] >= change[0] ? "+" : "") +
					NutritionController.format(Math.round((change[1] - change[0]) * 10) / 10.0) + " desde " +
					measures.days().firstKey() + ")"));
		}
		return "Última medición " + last + ": " + String.join(", ", parts) + ".";
	}

	static String diet(DietPlanner diet) {
		DietPlan plan = diet == null ? null : diet.days().get(LocalDate.now());
		if (plan == null)
			return "";
		List<String> meals = new ArrayList<>();
		for (DietPlan.Meal m : plan.meals)
			meals.add(m.name + ": " + m.dish + " (" + Math.round(m.kcal) + " kcal)");
		return "Plan del mes (" + diet.days().size() + " días con menú). Hoy: " + plan.source + ", " +
			Math.round(plan.totals()[0]) + " kcal" +
			(plan.cost() >= 0 ? String.format(Locale.ROOT, ", ≈ %.2f €", plan.cost()) : "") + ". " + String.join("; ", meals) + ".";
	}
}
