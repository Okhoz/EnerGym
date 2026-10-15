package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;
import com.energym.app.nutrition.NutritionController;
import com.energym.app.progress.ProgressStats;
import com.energym.app.ui.charts.*;

import java.text.DateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import android.widget.LinearLayout;

/**
 * VISTA de progreso: resumen global con tendencias, gráficas semanales, calendario y sesiones guardadas.
 * Representa snapshots realizados; jamás sustituye resultados por la prescripción.
 */
public final class HistoryFragment extends ScreenFragment {
	private YearMonth month;
	private LocalDate selected;

	public HistoryFragment() {
		super(R.layout.fragment_history);
	}

	protected void content() {
		heading("TU EVOLUCIÓN", "Progreso",
			"Resumen global, calendario y resultados guardados de cada entrenamiento.");
		ZoneId zone = ZoneId.systemDefault();
		LocalDate today = LocalDate.now(zone);
		if (month == null)
			month = YearMonth.from(today);
		if (selected == null)
			selected = today;
		ProgressStats stats = new ProgressStats(app.history, zone, today);
		NutritionController n = state.nutrition;

		LinearLayout overview = card("Resumen global", stats.total() == 0 ?
			"Finaliza tu primera sesión para ver tendencias. Los borradores no cuentan como completados." : "");
		LinearLayout figures = columns(overview, 4);
		stat(figures, Integer.toString(stats.total()), "sesiones totales");
		stat(figures, Integer.toString(stats.weekStreak()), stats.weekStreak() == 1 ? "semana seguida" : "semanas seguidas");
		stat(figures, Integer.toString(stats.daysThisMonth()), "días este mes");
		ProgressStats.Trend sessions = stats.sessionsTrend(), minutes = stats.minutesTrend();
		text(overview, "Sesiones (4 semanas): " + trend(sessions, ""), R.style.EnerGym_Body, 16);
		text(overview, "Tiempo entrenado (4 semanas): " + trend(minutes, " min"), R.style.EnerGym_Body, 4);

		LinearLayout perWeek = card("Sesiones por semana", "Últimas 8 semanas; la barra dorada es la semana actual.");
		double[] weekly = stats.sessionsPerWeek();
		add(perWeek, new BarChartView(requireContext()), 8).set(weekly, stats.weekLabels(), 0,
			"Sesiones por semana: " + Arrays.toString(weekly));

		LinearLayout time = card("Minutos entrenados", "Tiempo entre empezar y finalizar cada sesión.");
		double[] mins = stats.minutesPerWeek();
		add(time, new BarChartView(requireContext()), 8).set(mins, stats.weekLabels(), 0,
			"Minutos por semana: " + Arrays.toString(mins));

		double[] volumes = stats.recentVolumes(12);
		boolean anyVolume = false;
		for (double v : volumes)
			anyVolume |= v > 0;
		if (anyVolume) {
			LinearLayout load = card("Volumen de fuerza",
				"Series × repeticiones × kg de tus últimas " + volumes.length + " sesiones. Subir indica más trabajo total.");
			add(load, new LineChartView(requireContext()), 8).set(volumes, new String[]{"antigua", "última"}, "kg",
				"Volumen de las últimas sesiones");
		}

		if (n != null && n.weights.size() >= 1) {
			List<LocalDate> days = new ArrayList<>(n.weights.keySet());
			double[] kg = new double[days.size()];
			for (int i = 0; i < kg.length; i++)
				kg[i] = n.weights.get(days.get(i));
			double change = kg[kg.length - 1] - kg[0];
			LinearLayout weight = card("Peso corporal", kg.length < 2 ? "Anota tu peso en Nutrición para ver la evolución." :
				(Math.abs(change) < 0.05 ? "Estable" : (change > 0 ? "Subida de " : "Bajada de ") +
					NutritionController.format(Math.round(Math.abs(change) * 10) / 10.0) + " kg") + " desde " +
					short_(days.get(0)) + ".");
			add(weight, new LineChartView(requireContext()), 8).set(kg,
				new String[]{short_(days.get(0)), short_(days.get(days.size() - 1))}, "kg", "Evolución del peso");
		}

		LinearLayout calendar = card("Calendario", "Dorado: día entrenado · punto: alimentación anotada.");
		LinearLayout nav = columns(calendar, 4);
		compact(nav, "‹", true, () -> {
			month = month.minusMonths(1);
			render();
		}).setContentDescription("Mes anterior");
		String title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", NutritionFragment.SPANISH));
		android.widget.TextView monthTitle = new android.widget.TextView(requireContext());
		monthTitle.setTextAppearance(R.style.EnerGym_CardTitle);
		monthTitle.setText(Character.toUpperCase(title.charAt(0)) + title.substring(1));
		monthTitle.setGravity(android.view.Gravity.CENTER);
		monthTitle.setMinHeight(dp(48));
		column(nav, monthTitle, 8).setLayoutParams(new LinearLayout.LayoutParams(0,
			android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 2));
		((LinearLayout.LayoutParams) monthTitle.getLayoutParams()).gravity = android.view.Gravity.CENTER_VERTICAL;
		compact(nav, "›", month.isBefore(YearMonth.from(today)), () -> {
			month = month.plusMonths(1);
			render();
		}).setContentDescription("Mes siguiente");
		add(calendar, new MonthCalendarView(requireContext()), 12).set(month, today, selected,
			stats.sessionsByDay().keySet(), n == null ? Collections.emptySet() : n.loggedDays(), day -> {
				selected = day;
				render();
			});
		StringBuilder detail = new StringBuilder(NutritionFragment.label(selected, today));
		List<Session> that = stats.sessionsOn(selected);
		detail.append(that.isEmpty() ? "\nSin entrenamiento." : "\n" + that.size() + (that.size() == 1 ? " sesión: " : " sesiones: "));
		for (int i = 0; i < that.size(); i++)
			detail.append(i == 0 ? "" : ", ").append(that.get(i).getRoutine().getTitle());
		if (n != null) {
			double kcal = n.totals(selected)[0];
			if (kcal > 0)
				detail.append("\nAlimentación: ").append(Math.round(kcal)).append(" kcal anotadas.");
		}
		text(calendar, detail.toString(), R.style.EnerGym_Body, 12);

		for (Session draft : app.pending) {
			LinearLayout row = card("Borrador pendiente",
				draft.getRoutine().getTitle() + " · " + draft.getResults().size() + " ejercicios guardados");
			action(row, "Continuar borrador", !app.sessions.busy, () -> app.resume(draft));
		}
		for (Session s : app.history) {
			StringBuilder body = new StringBuilder(
				DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, NutritionFragment.SPANISH)
					.format(new Date(s.getCompletedAt())));
			body.append("\n").append(s.getRoutine().getProfile().getDurationMinutes()).append(" min disponibles · ")
				.append(AppController.label(s.getRoutine().getProfile().getGoal().name()));
			double volume = ProgressStats.volume(s);
			if (volume > 0)
				body.append(" · volumen ").append(Math.round(volume)).append(" kg");
			for (RoutineItem item : s.getRoutine().getItems()) {
				body.append("\n\n").append(item.getExercise().getName()).append("\n").append(
					AppController.metrics(item.getExercise().getMetricType(),
						s.getResults().getOrDefault(item.getExercise().getId(), Collections.emptyMap())));
			}
			card("Sesión finalizada", body.toString());
		}
	}

	private static String short_(LocalDate d) {
		return d.getDayOfMonth() + "/" + d.getMonthValue();
	}

	private static String trend(ProgressStats.Trend t, String unit) {
		String now = Math.round(t.recent) + unit;
		if (!t.comparable())
			return now + (t.recent > 0 ? " · primer periodo registrado" : "");
		long percent = Math.round(t.percent());
		return now + " · " + (percent > 0 ? "▲ +" + percent + " %" : percent < 0 ? "▼ " + percent + " %" : "= sin cambios") +
			" frente a las 4 anteriores";
	}
}
