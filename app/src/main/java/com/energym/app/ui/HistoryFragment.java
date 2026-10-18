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
	/** 0 Resumen · 1 Calendario · 2 Cuerpo · 3 Metas. */
	private int tab;
	/** Logros: todos (también los conseguidos) o solo los 3 siguientes. */
	private boolean allBadges;
	private YearMonth month;
	private LocalDate selected;
	/** Análisis por grupo muscular: false = este mes, true = últimos 90 días. */
	private boolean recentPeriod;
	/** Fotos de progreso: de la galería (selector del sistema, sin permisos) o de la cámara. */
	private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickPhoto =
		registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
			if (uri != null)
				storePhoto(uri);
		});
	private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePhoto =
		registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(), ok -> {
			if (Boolean.TRUE.equals(ok))
				storePhoto(cameraUri());
		});

	public HistoryFragment() {
		super(R.layout.fragment_history);
	}

	protected void content() {
		heading("", "Progreso", "");
		// Cuatro pestañas cortas en vez de una página interminable.
		segmented("history", new String[]{"Resumen", "Calendario", "Cuerpo", "Metas"},
			new String[]{"history_tab_summary", "history_tab_calendar", "history_tab_body", "history_tab_goals"}, tab,
			index -> {
				tab = index;
				render();
			});
		ProgressStats stats = new ProgressStats(app.history, ZoneId.systemDefault(), LocalDate.now());
		NutritionController n = state.nutrition;
		if (tab == 1) {
			calendar(stats, n);
			breakdown();
		} else if (tab == 2) {
			weight(n);
			body();
		} else if (tab == 3)
			goals(stats);
		else
			summary(stats);
	}

	/** Separación entre tarjetas sin cabecera (las tarjetas ya llevan su título). */
	private void gap(int dp) {
		if (dp > 0)
			add(rows, new android.view.View(requireContext()), dp - 8);
	}

	// ---------------- Resumen ----------------

	private void summary(ProgressStats stats) {
		NutritionController n = state.nutrition;
		gap(0);
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

		inProgress();

		if (stats.total() > 0) {
			gap(32);
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
		nutrition(n);
		achievements(stats, n);
	}

	/** Peso: anotar el de hoy y su evolución. */
	private void weight(NutritionController n) {
		if (n == null || app.owner() == null)
			return;
		gap(0);
		com.energym.app.nutrition.BodyProfile current = n.effectiveBody();
		LinearLayout log = card("⚖️ Peso", current == null ? "Anota tu peso para seguir su evolución." :
			"Actual: " + NutritionController.format(current.weightKg) + " kg");
		String[] typed = {""};
		textField(log, "weight", "Peso de hoy (kg)", "", true, false, v -> typed[0] = v);
		action(log, "Anotar peso", !n.busy, () -> n.logWeight(typed[0])).setTag("progress_log_weight");
		action(log, n.body == null ? "Completar mis datos" : "Editar mis datos físicos", true, () -> {
			n.fillBody();
			app.navigate(com.energym.app.controller.AppController.Screen.BODY);
		});
		if (n.weights.size() > 1) {
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
	}

	/** Logros que se ganan solos: los conseguidos y el siguiente de cada tipo con lo que falta. */
	private void achievements(ProgressStats stats, NutritionController n) {
		List<Achievements.Badge> badges = Achievements.of(stats.total(), stats.weekStreak(), stats.totalVolume(),
			stats.totalMinutes(), Achievements.records(app.history), n == null ? 0 : n.loggedDays().size());
		int earned = 0;
		for (Achievements.Badge b : badges)
			if (b.earned()) earned++;
		gap(32);
		LinearLayout card = card("🏅 " + earned + (earned == 1 ? " logro conseguido" : " logros conseguidos"),
			allBadges ? "Todos los logros." : "Los siguientes que puedes conseguir.");
		List<Achievements.Badge> shown = new ArrayList<>();
		for (Achievements.Badge b : badges)
			if (allBadges || !b.earned())
				shown.add(b);
		if (!allBadges && shown.size() > 3)
			shown = shown.subList(0, 3);
		for (Achievements.Badge b : shown) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			((TextView) row.findViewById(R.id.food_name)).setText(b.icon + "  " + b.title);
			((TextView) row.findViewById(R.id.food_detail)).setText(b.detail);
			((TextView) row.findViewById(R.id.food_kcal)).setText(b.earned() ? "✓" :
				Math.round(Math.min(99, b.value * 100 / b.goal)) + " %");
			row.setAlpha(b.earned() ? 1f : 0.6f);
			row.setContentDescription(b.title + ". " + b.detail);
			card.addView(row);
		}
		action(card, allBadges ? "Ver menos" : "Ver todos los logros", true, () -> {
			allBadges = !allBadges;
			render();
		}).setTag("progress_all_badges");
	}

	// ---------------- Cuerpo: medidas y fotos ----------------

	private void body() {
		if (app.owner() == null || state.measures == null)
			return;
		section("Cuerpo", 32);
		com.energym.app.nutrition.BodyMeasures m = state.measures;
		SortedMap<LocalDate, Map<com.energym.app.nutrition.BodyMeasures.Part, Double>> days = m.days();
		StringBuilder s = new StringBuilder();
		if (days.isEmpty())
			s.append("Mide cintura, pecho, brazo… cada 2–4 semanas: a veces el cuerpo cambia aunque la báscula no se mueva.");
		else {
			LocalDate last = days.lastKey();
			s.append("Última medición: ").append(short_(last));
			for (Map.Entry<com.energym.app.nutrition.BodyMeasures.Part, Double> e : days.get(last).entrySet()) {
				s.append('\n').append(e.getKey().label).append(": ").append(NutritionController.format(e.getValue()))
					.append(' ').append(e.getKey().unit);
				double[] change = m.change(e.getKey());
				if (change != null && Math.abs(change[1] - change[0]) >= 0.05)
					s.append("   (").append(change[1] > change[0] ? "▲ +" : "▼ ")
						.append(NutritionController.format(Math.abs(Math.round((change[1] - change[0]) * 10) / 10.0)))
						.append(" desde ").append(short_(days.firstKey())).append(')');
			}
		}
		LinearLayout card = card("📏 Medidas", s.toString());
		action(card, days.isEmpty() ? "Anotar medidas" : "Anotar medidas de hoy", true, this::measureDialog)
			.setTag("progress_measures");

		List<java.io.File> photos = ProgressPhotos.list(requireContext(), app.owner());
		LinearLayout album = card("📸 Fotos de progreso", photos.isEmpty() ?
			"Haz una foto cada pocas semanas con la misma luz y postura para comparar. Se guardan solo en este móvil." :
			photos.size() + (photos.size() == 1 ? " foto" : " fotos") + " · solo en este móvil. Toca para verla, " +
				"mantén pulsado para borrarla.");
		if (!photos.isEmpty()) {
			android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(requireContext());
			LinearLayout strip = new LinearLayout(requireContext());
			strip.setOrientation(LinearLayout.HORIZONTAL);
			scroll.addView(strip);
			List<java.io.File> recent = new ArrayList<>(photos);
			Collections.reverse(recent);
			for (java.io.File f : recent.subList(0, Math.min(20, recent.size())))
				strip.addView(photo(f), new LinearLayout.LayoutParams(dp(96), dp(128)));
			add(album, scroll, 12);
		}
		LinearLayout buttons = columns(album, 12);
		compact(buttons, "Hacer foto", true, () -> {
			try {
				takePhoto.launch(cameraUri());
			} catch (RuntimeException e) {
				android.widget.Toast.makeText(requireContext(), "No hay cámara disponible", android.widget.Toast.LENGTH_SHORT).show();
			}
		}).setTag("progress_camera");
		compact(buttons, "De la galería", true, () -> pickPhoto.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
			.setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
			.build())).setTag("progress_gallery");
	}

	private android.view.View photo(java.io.File f) {
		LinearLayout box = new LinearLayout(requireContext());
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(0, 0, dp(8), 0);
		android.widget.ImageView image = new android.widget.ImageView(requireContext());
		image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
		image.setImageBitmap(ProgressPhotos.thumbnail(f, dp(96)));
		image.setContentDescription("Foto de progreso del " + ProgressPhotos.date(f));
		box.addView(image, new LinearLayout.LayoutParams(dp(88), dp(104)));
		TextView label = text(box, ProgressPhotos.date(f), R.style.EnerGym_FieldCaption, 2);
		label.setGravity(Gravity.CENTER);
		box.setOnClickListener(v -> {
			android.widget.ImageView full = new android.widget.ImageView(requireContext());
			full.setAdjustViewBounds(true);
			full.setImageBitmap(ProgressPhotos.thumbnail(f, 1200));
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(ProgressPhotos.date(f)).setView(full).setPositiveButton("Cerrar", null).show();
		});
		box.setOnLongClickListener(v -> {
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("¿Borrar esta foto?").setMessage("Se borrará de este móvil.")
				.setNegativeButton("Cancelar", null).setPositiveButton("Borrar", (d, w) -> {
					//noinspection ResultOfMethodCallIgnored
					f.delete();
					render();
				}).show();
			return true;
		});
		return box;
	}

	private android.net.Uri cameraUri() {
		return androidx.core.content.FileProvider.getUriForFile(requireContext(),
			requireContext().getPackageName() + ".photos", ProgressPhotos.cameraFile(requireContext()));
	}

	private void storePhoto(android.net.Uri uri) {
		if (app == null || app.owner() == null)
			return;
		boolean ok = ProgressPhotos.add(requireContext(), app.owner(), uri);
		android.widget.Toast.makeText(requireContext(), ok ? "Foto guardada" : "No se pudo guardar la foto",
			android.widget.Toast.LENGTH_SHORT).show();
		//noinspection ResultOfMethodCallIgnored
		ProgressPhotos.cameraFile(requireContext()).delete();
		render();
	}

	private void measureDialog() {
		LinearLayout form = new LinearLayout(requireContext());
		form.setOrientation(LinearLayout.VERTICAL);
		form.setPadding(dp(24), dp(8), dp(24), 0);
		Map<com.energym.app.nutrition.BodyMeasures.Part, android.widget.EditText> fields =
			new EnumMap<>(com.energym.app.nutrition.BodyMeasures.Part.class);
		Map<com.energym.app.nutrition.BodyMeasures.Part, Double> today = state.measures.days().get(LocalDate.now());
		for (com.energym.app.nutrition.BodyMeasures.Part p : com.energym.app.nutrition.BodyMeasures.Part.values()) {
			com.google.android.material.textfield.TextInputLayout layout =
				new com.google.android.material.textfield.TextInputLayout(requireContext());
			layout.setHint(p.label + " (" + p.unit + ")");
			com.google.android.material.textfield.TextInputEditText edit =
				new com.google.android.material.textfield.TextInputEditText(layout.getContext());
			edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
			edit.setTag("measure_" + p.key);
			if (today != null && today.containsKey(p))
				edit.setText(NutritionController.format(today.get(p)));
			layout.addView(edit);
			form.addView(layout);
			fields.put(p, edit);
		}
		android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
		scroll.addView(form);
		androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Medidas de hoy").setMessage("Rellena solo las que midas. Mide siempre en el mismo sitio y a la misma hora.")
			.setView(scroll).setNegativeButton("Cancelar", null).setPositiveButton("Guardar medidas", null).show();
		dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
			Map<com.energym.app.nutrition.BodyMeasures.Part, String> typed =
				new EnumMap<>(com.energym.app.nutrition.BodyMeasures.Part.class);
			for (Map.Entry<com.energym.app.nutrition.BodyMeasures.Part, android.widget.EditText> e : fields.entrySet())
				typed.put(e.getKey(), e.getValue().getText().toString());
			String error = state.measures.save(LocalDate.now(), typed);
			if (!error.isEmpty()) {
				android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_LONG).show();
				return;
			}
			dialog.dismiss();
			render();
		});
	}

	/** Lo guardado en una sesión sin finalizar también cuenta como avance visible. */
	private void inProgress() {
		for (Session s : app.pending) {
			if (s.getStatus() != Session.Status.DRAFT)
				continue;
			int total = s.getRoutine().getItems().size(), saved = s.getResults().size();
			StringBuilder done = new StringBuilder(saved + " de " + total + " ejercicios guardados");
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> values = s.getResults().get(item.getExercise().getId());
				if (values != null)
					done.append("\n✓ ").append(item.getExercise().getName()).append(" · ")
						.append(com.energym.app.controller.AppController.metrics(item.getExercise().getMetricType(), values));
			}
			gap(32);
			LinearLayout card = card("Entreno en curso: " + s.getRoutine().getTitle(), done.toString());
			add(card, new ProgressBarView(requireContext()), 12).set(total == 0 ? 0 : saved / (double) total,
				saved + " de " + total + " ejercicios");
			primary(action(card, "▶ Continuar entreno", !app.trainingMutation, () -> app.resume(s)));
			return;
		}
	}

	/** Alimentación en Progreso: calorías y proteínas de las dos últimas semanas frente al objetivo. */
	private void nutrition(NutritionController n) {
		if (n == null || app.owner() == null)
			return;
		final int days = 14;
		double[] kcal = n.recentSeries(days, 0), protein = n.recentSeries(days, 1);
		int logged = 0;
		for (double v : kcal)
			if (v > 0) logged++;
		com.energym.app.nutrition.BodyProfile.Targets targets = n.targets();
		gap(32);
		LinearLayout card = card("Alimentación", logged == 0 ?
			"Anota comidas en Alimentación y aquí verás tu evolución." : "Últimos " + days + " días");
		if (logged == 0)
			return;
		LinearLayout figures = columns(card, 4);
		stat(figures, Integer.toString(logged), logged == 1 ? "día anotado" : "días anotados");
		stat(figures, Long.toString(Math.round(NutritionController.loggedAverage(kcal, kcal))), "kcal de media");
		stat(figures, Long.toString(Math.round(NutritionController.loggedAverage(protein, kcal))), "g proteína/día");
		if (targets != null) {
			int onTarget = 0;
			for (double v : kcal)
				if (v > 0 && Math.abs(v - targets.kcal) <= targets.kcal * 0.1) onTarget++;
			text(card, "🎯 " + onTarget + " de " + logged + " días dentro de tu objetivo (±10 % de " +
				Math.round(targets.kcal) + " kcal)", R.style.EnerGym_Body, 12);
		}
		add(card, new BarChartView(requireContext()), 12).set(kcal, n.recentLabels(days),
			targets == null ? 0 : targets.kcal, "Calorías de los últimos " + days + " días: " + Arrays.toString(kcal));
		add(card, new LineChartView(requireContext()), 12).set(protein,
			new String[]{n.recentLabels(days)[0], "hoy"}, "g", "Proteínas por día");
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
		gap(0);
		LinearLayout calendar = card("Calendario", "Dorado: entreno · puntos de color: grupos musculares · punto blanco: comida anotada");
		Map<LocalDate, Set<RoutineRequest.Area>> worked = MuscleBreakdown.byDay(app.history, ZoneId.systemDefault());
		Map<LocalDate, int[]> dots = new HashMap<>();
		Set<RoutineRequest.Area> seen = EnumSet.noneOf(RoutineRequest.Area.class);
		for (Map.Entry<LocalDate, Set<RoutineRequest.Area>> e : worked.entrySet()) {
			int[] colors = new int[e.getValue().size()];
			int i = 0;
			for (RoutineRequest.Area a : e.getValue())
				colors[i++] = areaColor(a);
			dots.put(e.getKey(), colors);
			if (YearMonth.from(e.getKey()).equals(month))
				seen.addAll(e.getValue());
		}
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
			}).groups(dots);
		if (!seen.isEmpty())
			text(calendar, "", R.style.EnerGym_FieldCaption, 8).setText(legend(seen));
		StringBuilder detail = new StringBuilder(NutritionFragment.label(selected, today));
		List<Session> that = stats.sessionsOn(selected);
		detail.append(that.isEmpty() ? " · sin entreno" : " · " + that.size() + (that.size() == 1 ? " sesión" : " sesiones"));
		// Resultados exactos del día elegido: lo realizado, nunca la prescripción.
		for (Session s : that) {
			detail.append("\n\n").append(s.getRoutine().getTitle());
			for (RoutineItem item : s.getRoutine().getItems()) {
				Map<String, String> done = s.getResults().get(item.getExercise().getId());
				if (done != null)
					detail.append("\n\n").append(item.getExercise().getName()).append("\n").append(done(item, done));
			}
		}
		if (n != null && n.historyCalories(selected) > 0)
			detail.append("\nComida: ").append(Math.round(n.historyCalories(selected))).append(" kcal");
		text(calendar, detail.toString(), R.style.EnerGym_Body, 12);
		for (Session session : that)
			action(calendar, getString(R.string.share_history_button, session.getRoutine().getTitle()), true, () ->
				ShareCard.share(requireContext(), com.energym.app.progress.ShareSummary.of(session, app.history)));
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

	/** Reparto por grupo muscular del mes o de los últimos 90 días, con anillo y lista (como FitNotes). */
	private void breakdown() {
		LocalDate today = LocalDate.now();
		LocalDate from = recentPeriod ? today.minusDays(89) : today.withDayOfMonth(1);
		List<MuscleBreakdown.Share> shares = MuscleBreakdown.of(app.history, ZoneId.systemDefault(), from, today);
		weeklyVolume(today);
		section("Por grupo muscular", 32);
		segmented("breakdown", new String[]{"Este mes", "Últimos 90 días"},
			new String[]{"breakdown_month", "breakdown_90"}, recentPeriod ? 1 : 0, index -> {
				recentPeriod = index == 1;
				render();
			});
		if (shares.isEmpty()) {
			card("Sin datos en este periodo", "Termina entrenos y aquí verás qué grupos musculares trabajas más.");
			return;
		}
		double total = 0;
		for (MuscleBreakdown.Share sh : shares)
			total += sh.sets;
		double[] values = new double[shares.size()];
		int[] colors = new int[shares.size()];
		for (int i = 0; i < shares.size(); i++) {
			values[i] = shares.get(i).sets;
			colors[i] = areaColor(shares.get(i).area);
		}
		MuscleBreakdown.Share top = shares.get(0);
		LinearLayout card = card("Series por grupo", "Un ejercicio que trabaja varias zonas reparte sus series entre ellas.");
		StringBuilder spoken = new StringBuilder("Reparto de series: ");
		for (MuscleBreakdown.Share sh : shares)
			spoken.append(sh.area.label).append(" ").append(Math.round(sh.sets / total * 100)).append(" %, ");
		add(card, new com.energym.app.ui.charts.DonutView(requireContext()), 8).set(values, colors,
			capital(top.area.label), Math.round(top.sets / total * 100) + " %", spoken.toString());
		for (MuscleBreakdown.Share sh : shares) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			TextView name = row.findViewById(R.id.food_name);
			name.setText("●  " + capital(sh.area.label));
			android.text.SpannableString dot = new android.text.SpannableString(name.getText());
			dot.setSpan(new android.text.style.ForegroundColorSpan(areaColor(sh.area)), 0, 1, 0);
			name.setText(dot);
			((TextView) row.findViewById(R.id.food_detail)).setText(
				NutritionController.format(Math.round(sh.sets * 10) / 10.0) + " series" +
					(sh.kg > 0 ? " · " + Math.round(sh.kg) + " kg movidos" : ""));
			((TextView) row.findViewById(R.id.food_kcal)).setText(Math.round(sh.sets / total * 100) + " %");
			card.addView(row);
		}
	}

	/** Series de esta semana por grupo frente a las 10–20 recomendadas, con aviso de los que se quedan cortos. */
	private void weeklyVolume(LocalDate today) {
		List<WeeklyVolume.Line> lines = WeeklyVolume.of(app.history, ZoneId.systemDefault(), today);
		List<WeeklyVolume.Line> low = WeeklyVolume.low(lines);
		List<String> names = new ArrayList<>();
		for (WeeklyVolume.Line l : low)
			names.add(l.area.label);
		LinearLayout card = card(getString(R.string.volume_title), low.isEmpty() ?
			getString(R.string.volume_all_ok, WeeklyVolume.MIN_SETS, WeeklyVolume.MAX_SETS) :
			getString(R.string.volume_low, String.join(", ", names), WeeklyVolume.MIN_SETS));
		card.setTag("weekly_volume");
		for (WeeklyVolume.Line l : lines) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			TextView name = row.findViewById(R.id.food_name);
			name.setText("●  " + capital(l.area.label));
			android.text.SpannableString dot = new android.text.SpannableString(name.getText());
			dot.setSpan(new android.text.style.ForegroundColorSpan(areaColor(l.area)), 0, 1, 0);
			name.setText(dot);
			((TextView) row.findViewById(R.id.food_detail)).setText(getString(R.string.volume_sets,
				NutritionController.format(Math.round(l.sets * 10) / 10.0), WeeklyVolume.MIN_SETS, WeeklyVolume.MAX_SETS));
			((TextView) row.findViewById(R.id.food_kcal)).setText(l.status == WeeklyVolume.Status.LOW ? "⚠️" :
				l.status == WeeklyVolume.Status.HIGH ? "⬆️" : "✅");
			row.setContentDescription(capital(l.area.label) + ": " + ((TextView) row.findViewById(R.id.food_detail)).getText());
			card.addView(row);
		}
	}

	/** Leyenda con el punto de color de cada grupo, igual que en el calendario. */
	private static CharSequence legend(Set<RoutineRequest.Area> areas) {
		android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Este mes:");
		for (RoutineRequest.Area a : areas) {
			b.append("   ");
			int start = b.length();
			b.append("●");
			b.setSpan(new android.text.style.ForegroundColorSpan(areaColor(a)), start, start + 1, 0);
			b.append(" ").append(capital(a.label));
		}
		return b;
	}

	/** Lo hecho en un ejercicio: series una a una en fuerza (como FitNotes); el resumen en lo demás. */
	private static String done(RoutineItem item, Map<String, String> values) {
		if (item.getExercise().getMetricType() != MetricType.STRENGTH)
			return com.energym.app.controller.AppController.metrics(item.getExercise().getMetricType(), values);
		List<SetLog.Entry> sets = SetLog.of(values);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < sets.size(); i++)
			b.append(i == 0 ? "" : "\n").append(i + 1).append(".  ")
				.append(sets.get(i).kg > 0 ? SetLog.format(sets.get(i).kg) + " kg" : "Peso corporal")
				.append(" × ").append(sets.get(i).reps);
		return b.toString();
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Un color fijo por grupo muscular, igual en el calendario y en el anillo. */
	static int areaColor(RoutineRequest.Area area) {
		switch (area) {
			case LEGS:
				return 0xFF42A5F5;
			case GLUTES:
				return 0xFFBA68C8;
			case CHEST:
				return 0xFFEF5350;
			case BACK:
				return 0xFF66BB6A;
			case SHOULDERS:
				return 0xFFFFA726;
			case ARMS:
				return 0xFF26A69A;
			case CORE:
				return 0xFFEC407A;
			case CARDIO:
				return 0xFFFFEE58;
			default:
				return 0xFF90A4AE;
		}
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
