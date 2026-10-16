package com.energym.app.controller;

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
