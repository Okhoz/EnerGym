package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.domain.*;
import com.energym.app.nutrition.NutritionController;
import com.energym.app.progress.*;
import com.energym.app.ui.charts.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * VISTA de progreso con dos pestañas: Resumen (todo lo realizado, contabilizado y en gráficas de mejora) y
 * Objetivos (metas propias con barra de avance y gráfica). Solo representa datos guardados.
 */
public final class HistoryFragment extends ScreenFragment {
	private boolean goalsTab;
	private YearMonth month;
	private LocalDate selected;

	public HistoryFragment() {
		super(R.layout.fragment_history);
	}

	protected void content() {
		heading("TU EVOLUCIÓN", "Progreso", "");
		LinearLayout tabs = columns(rows, 0);
		MaterialButton summary = compact(tabs, "Resumen", true, () -> {
			goalsTab = false;
			render();
		});
		MaterialButton goals = compact(tabs, "Objetivos", true, () -> {
			goalsTab = true;
			render();
		});
		primary(goalsTab ? goals : summary);
		summary.setSelected(!goalsTab);
		goals.setSelected(goalsTab);
		add(rows, new android.widget.Space(requireContext()), 8);
		ProgressStats stats = new ProgressStats(app.history, ZoneId.systemDefault(), LocalDate.now());
		if (goalsTab)
			goals(stats);
		else
			summary(stats);
	}

	// ---------------- Resumen ----------------

	private void summary(ProgressStats stats) {
		NutritionController n = state.nutrition;
		LinearLayout totals = card("Todo lo que has hecho", stats.total() == 0 ?
			"Finaliza tu primera sesión y aquí se irá sumando todo." : "");
		LinearLayout row1 = columns(totals, 4);
		stat(row1, Integer.toString(stats.total()), "sesiones");
		stat(row1, BarChartView.compact(stats.totalMinutes() / 60.0), "horas");
		stat(row1, Integer.toString(stats.exercisesDone()), "ejercicios");
		LinearLayout row2 = columns(totals, 16);
		stat(row2, BarChartView.compact(stats.totalVolume()), "kg levantados");
		stat(row2, BarChartView.compact(stats.totalDistance()), "km");
		stat(row2, Integer.toString(n == null ? 0 : n.loggedDays().size()), "días con dieta");

		if (stats.total() > 0) {
			LinearLayout trend = card("Tu mejora", "Últimas 4 semanas frente a las 4 anteriores");
			text(trend, "Sesiones: " + trend(stats.sessionsTrend(), ""), R.style.EnerGym_Body, 8);
			text(trend, "Tiempo: " + trend(stats.minutesTrend(), " min"), R.style.EnerGym_Body, 4);
			LinearLayout figures = columns(trend, 12);
			stat(figures, Integer.toString(stats.weekStreak()), stats.weekStreak() == 1 ? "semana seguida" : "semanas seguidas");
			stat(figures, Integer.toString(stats.daysThisMonth()), "días este mes");

			bars("Sesiones por semana", stats.sessionsPerWeek(), stats);
			bars("Minutos por semana", stats.minutesPerWeek(), stats);
			if (stats.totalVolume() > 0)
				bars("Kilos levantados por semana", stats.volumePerWeek(), stats);
			if (stats.totalDistance() > 0)
				bars("Kilómetros por semana", stats.distancePerWeek(), stats);

			List<ProgressStats.Improvement> improvements = stats.improvements();
			if (!improvements.isEmpty()) {
				ProgressStats.Improvement top = improvements.get(0);
				LinearLayout records = card("Récords por ejercicio", "Primera carga → mejor carga");
				for (int i = 0; i < Math.min(5, improvements.size()); i++) {
					ProgressStats.Improvement m = improvements.get(i);
					text(records, m.name + "\n" + NutritionController.format(m.first) + " → " +
							NutritionController.format(m.best) + " kg" + (m.gain() > 0 ? "  ▲ +" +
							NutritionController.format(Math.round(m.gain() * 10) / 10.0) + " kg" : ""),
						R.style.EnerGym_Body, 10);
				}
				double[] loads = stats.loadHistory(top.exerciseId);
				if (loads.length > 1)
					add(records, new LineChartView(requireContext()), 12).set(loads,
						new String[]{"1.ª vez", "última"}, "kg", "Evolución de carga en " + top.name);
			}
		}
		if (n != null && n.weights.size() > 1) {
			List<LocalDate> days = new ArrayList<>(n.weights.keySet());
			double[] kg = new double[days.size()];
			for (int i = 0; i < kg.length; i++)
				kg[i] = n.weights.get(days.get(i));
			double change = kg[kg.length - 1] - kg[0];
			LinearLayout weight = card("Peso corporal", (Math.abs(change) < 0.05 ? "Estable" :
				(change > 0 ? "▲ Subida de " : "▼ Bajada de ") +
					NutritionController.format(Math.round(Math.abs(change) * 10) / 10.0) + " kg") + " desde " +
				short_(days.get(0)));
			add(weight, new LineChartView(requireContext()), 8).set(kg,
				new String[]{short_(days.get(0)), short_(days.get(days.size() - 1))}, "kg", "Evolución del peso");
		}
		calendar(stats, n);
	}

	private void bars(String title, double[] values, ProgressStats stats) {
		LinearLayout card = card(title, "Últimas 8 semanas");
		add(card, new BarChartView(requireContext()), 8).set(values, stats.weekLabels(), 0,
			title + ": " + Arrays.toString(values));
	}

	private void calendar(ProgressStats stats, NutritionController n) {
		LocalDate today = stats.today();
		if (month == null)
			month = YearMonth.from(today);
		if (selected == null)
			selected = today;
		LinearLayout calendar = card("Calendario", "Dorado: entreno · punto: comida anotada");
		LinearLayout nav = columns(calendar, 4);
		compact(nav, "‹", true, () -> {
			month = month.minusMonths(1);
			render();
		}).setContentDescription("Mes anterior");
		((TextView) nav.getChildAt(0)).setTextSize(22);
		String title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", NutritionFragment.SPANISH));
		TextView monthTitle = new TextView(requireContext());
		monthTitle.setTextAppearance(R.style.EnerGym_CardTitle);
		monthTitle.setText(Character.toUpperCase(title.charAt(0)) + title.substring(1));
		monthTitle.setGravity(Gravity.CENTER);
		monthTitle.setMinHeight(dp(48));
		LinearLayout.LayoutParams middle =
			new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 2);
		middle.gravity = Gravity.CENTER_VERTICAL;
		nav.addView(monthTitle, middle);
		compact(nav, "›", month.isBefore(YearMonth.from(today)), () -> {
			month = month.plusMonths(1);
			render();
		}).setContentDescription("Mes siguiente");
		((TextView) nav.getChildAt(2)).setTextSize(22);
		add(calendar, new MonthCalendarView(requireContext()), 12).set(month, today, selected,
			stats.sessionsByDay().keySet(), n == null ? Collections.emptySet() : n.loggedDays(), day -> {
				selected = day;
				render();
			});
		StringBuilder detail = new StringBuilder(NutritionFragment.label(selected, today));
		List<Session> that = stats.sessionsOn(selected);
		detail.append(that.isEmpty() ? " · sin entreno" : " · " + that.size() + (that.size() == 1 ? " sesión" : " sesiones"));
		// Resultados exactos del día elegido: lo realizado, nunca la prescripción.
		for (Session s : that) {
			detail.append("\n\n").append(s.getRoutine().getTitle());
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> done = s.getResults().get(item.getExercise().getId());
				if (done != null)
					detail.append("\n\n").append(item.getExercise().getName()).append("\n")
						.append(com.energym.app.controller.AppController.metrics(item.getExercise().getMetricType(), done));
			}
		}
		if (n != null && n.historyCalories(selected) > 0)
			detail.append("\nComida: ").append(Math.round(n.historyCalories(selected))).append(" kcal");
		text(calendar, detail.toString(), R.style.EnerGym_Body, 12);
		long scope = app.accountRevision();
		for (Session session : that)
			action(calendar, "Eliminar sesión: " + session.getRoutine().getTitle(), !app.trainingMutation, () ->
				new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle("¿Eliminar esta sesión?")
					.setMessage("Se quitará de tu historial y se recalculará el progreso. La rutina se conserva. No se puede deshacer.")
					.setNegativeButton("Cancelar", null)
					.setPositiveButton("Eliminar", (dialog, button) -> {
						if (scope == app.accountRevision()) app.deleteSession(session);
					}).show());
	}

	// ---------------- Objetivos ----------------

	private void goals(ProgressStats stats) {
		GoalsController g = state.goals;
		if (g == null || app.owner() == null) {
			card("Objetivos", "Entra con tu cuenta para marcar objetivos.");
			return;
		}
		SortedMap<LocalDate, Double> weights = state.nutrition == null ? new TreeMap<>() : state.nutrition.weights;
		if (g.editing)
			form(g, weights);
		else
		{
			MaterialButton add = action(rows, "Nuevo objetivo", !g.busy, g::startNew);
			primary(add);
			((LinearLayout.LayoutParams) add.getLayoutParams()).bottomMargin = dp(16);
		}
		if (g.goals.isEmpty() && !g.editing)
			card("Aún no tienes objetivos", "Por ejemplo: 3 sesiones por semana, 150 minutos por semana, " +
				"llegar a 75 kg o levantar 60 kg en sentadilla.");
		for (Goal goal : g.goals) {
			GoalProgress p = GoalProgress.of(goal, stats, weights);
			LinearLayout card = card(goal.title(), p.status() + (p.reached ? "\n✓ ¡Objetivo cumplido!" : ""));
			add(card, new ProgressBarView(requireContext()), 12).set(p.ratio, goal.title());
			if (p.series.length > 0) {
				if (goal.type.weekly)
					add(card, new BarChartView(requireContext()), 12).set(p.series, p.labels, goal.target,
						goal.title() + " por semana");
				else if (p.series.length > 1)
					add(card, new LineChartView(requireContext()), 12).set(p.series,
						new String[]{p.labels[0], p.labels[p.labels.length - 1]}, goal.type.unit, goal.title());
			}
			MaterialButton remove = action(card, "Quitar objetivo", !g.busy, () -> g.remove(goal));
			remove.setContentDescription("Quitar objetivo " + goal.title());
		}
	}

	private void form(GoalsController g, SortedMap<LocalDate, Double> weights) {
		Map<String, String> d = g.draft;
		LinearLayout form = card("Nuevo objetivo", "");
		List<String> types = new ArrayList<>(), labels = new ArrayList<>();
		for (Goal.Type t : Goal.Type.values()) {
			types.add(t.name());
			labels.add(t.label);
		}
		choice(form, "goal_type", "Tipo de objetivo", types, labels, d.get("type"), v -> {
			d.put("type", v);
			app.changed();
		});
		Goal.Type type = Goal.Type.valueOf(d.getOrDefault("type", Goal.Type.SESSIONS_PER_WEEK.name()));
		Map<String, String> names = new LinkedHashMap<>();
		if (type == Goal.Type.EXERCISE_LOAD) {
			List<String> ids = new ArrayList<>(), exerciseNames = new ArrayList<>();
			for (Exercise e : app.catalog.getExercises())
				if (e.getMetricType() == MetricType.STRENGTH && !e.getRequiredResources().isEmpty()) {
					ids.add(e.getId());
					exerciseNames.add(e.getName());
					names.put(e.getId(), e.getName());
				}
			d.putIfAbsent("exerciseId", ids.get(0));
			choice(form, "goal_exercise", "Ejercicio", ids, exerciseNames, d.get("exerciseId"),
				v -> d.put("exerciseId", v));
		}
		textField(form, "goal_target", "Meta (" + type.unit + (type.weekly ? " por semana)" : ")"),
			d.getOrDefault("target", ""), true, false, v -> d.put("target", v));
		double latest = weights.isEmpty() ? 0 : weights.get(weights.lastKey());
		if (type == Goal.Type.TARGET_WEIGHT)
			text(form, latest > 0 ? "Partes de " + NutritionController.format(latest) + " kg (último peso anotado)." :
				"Anota tu peso en Nutrición para medir el avance.", R.style.EnerGym_FieldCaption, 0);
		LinearLayout buttons = columns(form, 8);
		compact(buttons, "Cancelar", true, g::cancel);
		primary(compact(buttons, "Guardar", !g.busy, () -> g.save(latest, names.get(d.get("exerciseId")))));
	}

	private static String short_(LocalDate d) {
		return d.getDayOfMonth() + "/" + d.getMonthValue();
	}

	private static String trend(ProgressStats.Trend t, String unit) {
		String now = Math.round(t.recent) + unit;
		if (!t.comparable())
			return now + (t.recent > 0 ? " · primer periodo" : "");
		long percent = Math.round(t.percent());
		return now + "  " + (percent > 0 ? "▲ +" + percent + " %" : percent < 0 ? "▼ " + percent + " %" : "= igual");
	}
}
