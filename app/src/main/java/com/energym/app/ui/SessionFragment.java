package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.*;

import android.os.*;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.*;

/**
 * VISTA de registro: el esquema viene de MetricValidator; no asume kg/repeticiones para todas las actividades.
 */
public final class SessionFragment extends ScreenFragment {
	/** Fin del descanso en curso (reloj del sistema); sobrevive a los repintados y al giro de pantalla. */
	private static long restEndsAt;
	private final Handler ticker = new Handler(Looper.getMainLooper());
	private TextView restLabel;
	private final Runnable tick = this::tick;
	/** Ejercicio y registros mostrados en el último pintado: detectan el avance tras guardar. */
	private int shownExercise = -1, shownResults = -1;
	/** Registro de fuerza: ejercicio mostrado, pestaña, serie en edición y valores de − / +. */
	private String trackKey = "", kgInput = "", repsInput = "";
	private int trackTab, selectedSet = -1;

	public SessionFragment() {
		super(R.layout.fragment_session);
	}

	@Override
	public void onDestroyView() {
		ticker.removeCallbacks(tick);
		restLabel = null;
		super.onDestroyView();
	}

	protected void content() {
		heading("", "Tu entreno", app.sessions.session == null ? "" : app.sessions.session.getRoutine().getTitle());
		if (app.sessions.session == null) {
			card(app.sessions.busy ? "Abriendo tu entreno…" : "No hay entreno en marcha",
				app.sessions.busy ? "Recuperando lo que ya apuntaste." : "Ve a Entreno y pulsa «Empezar entreno».");
			return;
		}
		if (app.sessions.session.getStatus() == Session.Status.FINISHED) {
			card("Entreno terminado", "Lo tienes en Progreso.");
			return;
		}
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		int i = 0;
		for (RoutineItem item : app.sessions.session.getRoutine().getItems()) {
			ids.add(Integer.toString(i++));
			// ✓ marca lo ya apuntado para ver de un vistazo qué falta.
			names.add((app.sessions.session.getResults().containsKey(item.getExercise().getId()) ? "✓ " : "") +
				item.getExercise().getName());
		}
		section("Ejercicio " + (app.sessions.selected + 1) + " de " + ids.size(), 8);
		choice(rows, "selectedExercise", "Cambiar de ejercicio", ids, names, Integer.toString(app.sessions.selected),
			value -> app.sessions.select(Integer.parseInt(value)));
		int saved = app.sessions.session.getResults().size();
		if (shownResults >= 0 && saved > shownResults &&
			app.sessions.item().getExercise().getMetricType() != MetricType.STRENGTH)
			android.widget.Toast.makeText(requireContext(), "Ejercicio guardado", android.widget.Toast.LENGTH_SHORT).show();
		// Al cambiar de ejercicio (a mano o tras guardar) se vuelve arriba para verlo desde el principio.
		if (shownExercise >= 0 && shownExercise != app.sessions.selected && getView() instanceof android.widget.ScrollView)
			getView().post(() -> {
				if (getView() != null)
					((android.widget.ScrollView) getView()).smoothScrollTo(0, 0);
			});
		shownExercise = app.sessions.selected;
		shownResults = saved;
		RoutineItem item = app.sessions.item();
		Exercise e = item.getExercise();
		if (e.getMetricType() == MetricType.STRENGTH)
			tracker(item, e);
		else
			form(item, e);
		if (FreeWorkout.is(app.sessions.session.getRoutine())) {
			com.google.android.material.button.MaterialButton more = action(rows, "+ Añadir ejercicio",
				!app.sessions.busy && !app.trainingMutation,
				() -> ExercisePicker.show(requireContext(), "Añadir ejercicio", app.freeCandidates(), app::addFreeExercise));
			more.setTag("session_add_exercise");
		}
		rest();
		if (app.sessions.selected + 1 < ids.size())
			action(rows, "Siguiente ejercicio", !app.sessions.busy,
				() -> app.sessions.select(app.sessions.selected + 1));
		section("Terminar", 32);
		boolean done = saved >= ids.size();
		LinearLayout finish = card(done ? "🎉 ¡Todo apuntado!" : "Terminar el entreno", done ?
			"Termina para guardarlo en tu historial y que tu rutina pueda subir de nivel." :
			saved + " de " + ids.size() + " ejercicios apuntados. Apunta todos para poder terminar.");
		com.google.android.material.button.MaterialButton end =
			action(finish, "Terminar entreno", !app.sessions.busy, app.sessions::finish);
		if (done)
			primary(end);
		action(finish, "Descartar entreno", !app.sessions.busy && !app.trainingMutation, () -> {
			Session current = app.sessions.session;
			long scope = app.accountRevision();
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("¿Descartar este entreno?")
				.setMessage("Se eliminarán sus registros de tu cuenta. Esta acción no se puede deshacer.")
				.setNegativeButton("Cancelar", null)
				.setPositiveButton("Descartar", (dialog, button) -> {
					if (scope == app.accountRevision()) app.deleteSession(current);
				}).show();
		});
	}

	/** Formulario de siempre para lo que no se mide en series de peso × repeticiones (cardio, movilidad…). */
	private void form(RoutineItem item, Exercise e) {
		LinearLayout context = card(e.getName(),
			"Lo previsto:\n" + AppController.prescription(e.getMetricType(), item.getPrescription()) +
				"\n\n" + (app.sessions.session.getResults().containsKey(e.getId()) ?
				"✓ Ya apuntado. Puedes corregirlo antes de terminar." :
				"Hazlo y apunta abajo lo que has hecho de verdad."));
		Session last = lastTime(e.getId());
		if (last != null)
			text(context, "🕑 La última vez (" + day(last) + "): " +
				AppController.metrics(e.getMetricType(), last.getResults().get(e.getId())), R.style.EnerGym_Body, 12);
		action(context, "Ver técnica: " + e.getName(), () -> app.openExercise(e));
		LinearLayout fields = card("Lo que has hecho", "Viene relleno con lo previsto: cambia solo lo que fue distinto.");
		Map<String, String> inputs = app.sessions.inputs();
		for (MetricValidator.Field f : new MetricValidator().fields(e.getMetricType()))
			if (f.choices.isEmpty())
				textField(fields, f.key, f.label, inputs.getOrDefault(f.key, ""), true, f.integer,
					v -> app.sessions.input(f.key, v));
			else
				choice(fields, f.key, f.label, f.choices, f.choices, inputs.getOrDefault(f.key, ""),
					v -> app.sessions.input(f.key, v));
		primary(action(rows, "Guardar ejercicio", !app.sessions.busy, app.sessions::record));
	}

	/**
	 * Registro estilo FitNotes para fuerza: pestañas Registrar / Historial / Gráfica, peso y repeticiones con − y +,
	 * cada serie se guarda al momento y las que baten tu récord llevan 🏆.
	 */
	private void tracker(RoutineItem item, Exercise e) {
		String key = app.sessions.session.getSyncId() + "/" + e.getId();
		if (!key.equals(trackKey)) {
			trackKey = key;
			selectedSet = -1;
			// Lo escrito en − / + vive en el controlador: sobrevive a girar la pantalla y a salir y volver.
			Map<String, String> typed = app.sessions.inputs();
			seedInputs(item, e);
			kgInput = typed.getOrDefault("trackKg", kgInput);
			repsInput = typed.getOrDefault("trackReps", repsInput);
		}
		segmented("track", new String[]{"Registrar", "Historial", "Gráfica"},
			new String[]{"track_tab_log", "track_tab_history", "track_tab_graph"}, trackTab, index -> {
				trackTab = index;
				render();
			});
		if (trackTab == 1) {
			history(e);
			return;
		}
		if (trackTab == 2) {
			graph(e);
			return;
		}
		List<SetLog.Entry> sets = app.sessions.sets();
		boolean editing = selectedSet >= 0 && selectedSet < sets.size();
		Session last = lastTime(e.getId());
		List<SetLog.Entry> before = last == null ? Collections.<SetLog.Entry>emptyList() :
			SetLog.of(last.getResults().get(e.getId()));
		LinearLayout log = card(e.getName() + " · " + (editing ? "editando serie " + (selectedSet + 1) :
			"serie " + (sets.size() + 1)), "🎯 Objetivo de hoy: " + planned(item) +
			(before.isEmpty() ? "" : "\n🕑 La última vez (" + day(last) + "): " + compactSets(before)));
		com.google.android.material.button.MaterialButton technique =
			action(log, "Ver técnica", true, () -> app.openExercise(e));
		technique.setContentDescription("Ver técnica: " + e.getName());
		stepper(log, "Peso (kg)", "track_kg", kgInput, 2.5, true, v -> {
			kgInput = v;
			app.sessions.input("trackKg", v);
		});
		stepper(log, "Repeticiones", "track_reps", repsInput, 1, false, v -> {
			repsInput = v;
			app.sessions.input("trackReps", v);
		});
		if (sets.isEmpty())
			text(log, "Ajusta con − y + (o escribe el número) y pulsa «Guardar serie» al acabar cada serie. " +
				"Peso 0 = peso corporal. Tu objetivo sube solo cuando lo cumples.", R.style.EnerGym_FieldCaption, 12);
		LinearLayout buttons = columns(log, 12);
		com.google.android.material.button.MaterialButton save =
			compact(buttons, editing ? "Actualizar" : "Guardar serie", !app.sessions.busy, () -> {
				double kg = number(kgInput, -1);
				int reps = (int) number(repsInput, 0);
				if (editing)
					app.sessions.updateSet(selectedSet, kg, reps);
				else {
					app.sessions.addSet(kg, reps);
					startRest(item);
				}
				selectedSet = -1;
			});
		primary(save);
		compact(buttons, editing ? "Eliminar" : "Borrar", !app.sessions.busy, () -> {
			if (editing) {
				app.sessions.removeSet(selectedSet);
				selectedSet = -1;
			} else {
				kgInput = "";
				repsInput = "";
				render();
			}
		});
		if (editing)
			action(log, "Cancelar edición", true, () -> {
				selectedSet = -1;
				render();
			});
		if (sets.isEmpty())
			return;
		double record = previousBest(e.getId());
		int repsRecord = previousBestReps(e.getId());
		LinearLayout list = card("Hoy · " + sets.size() + (sets.size() == 1 ? " serie" : " series"),
			"Toca una serie para corregirla o borrarla.");
		for (int i = 0; i < sets.size(); i++) {
			SetLog.Entry s = sets.get(i);
			// Récord: más 1RM estimado con carga, o más repeticiones que nunca a peso corporal.
			boolean pr = s.kg > 0 ? s.oneRepMax() > record + 0.001 : s.reps > repsRecord;
			if (pr && s.kg > 0)
				record = s.oneRepMax();
			else if (pr)
				repsRecord = s.reps;
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, list, false);
			((TextView) row.findViewById(R.id.food_name)).setText((i + 1) + ".   " + weight(s.kg) + " × " + s.reps +
				(s.reps == 1 ? " rep" : " reps"));
			((TextView) row.findViewById(R.id.food_detail)).setText(pr ? "Nuevo récord personal" :
				s.kg > 0 ? "1RM estimado " + SetLog.format(Math.round(s.oneRepMax() * 2) / 2.0) + " kg" : "");
			((TextView) row.findViewById(R.id.food_kcal)).setText(pr ? "🏆" : "");
			int index = i;
			row.setTag("track_set_" + i);
			row.setContentDescription("Serie " + (i + 1) + ": " + weight(s.kg) + " por " + s.reps +
				(pr ? ", récord personal" : "") + ". Toca para editarla");
			if (index == selectedSet)
				row.setBackground(rounded(R.color.surface_selected, 12, R.color.gold, 1));
			row.setOnClickListener(v -> {
				selectedSet = index;
				kgInput = SetLog.format(s.kg);
				repsInput = Integer.toString(s.reps);
				render();
			});
			list.addView(row);
		}
	}

	/** Valores de partida: la última serie de hoy, si no la última vez que lo hiciste, si no lo previsto. */
	private void seedInputs(RoutineItem item, Exercise e) {
		List<SetLog.Entry> today = app.sessions.sets();
		Session last = lastTime(e.getId());
		List<SetLog.Entry> before = last == null ? Collections.emptyList() : SetLog.of(last.getResults().get(e.getId()));
		// Hoy se parte del objetivo que fija la progresión automática; tras una serie, de la última apuntada.
		SetLog.Entry seed = !today.isEmpty() ? today.get(today.size() - 1) : null;
		String target = item.getPrescription().getOrDefault("loadKg", "0");
		if (seed == null && number(target, 0) == 0 && !before.isEmpty() && before.get(0).kg > 0)
			target = SetLog.format(before.get(0).kg);
		kgInput = seed != null ? SetLog.format(seed.kg) : target;
		repsInput = seed != null ? Integer.toString(seed.reps) : item.getPrescription().getOrDefault("repetitions", "8");
	}

	/** Fila «− valor +» para ajustar con toques o escribir el número. */
	private void stepper(LinearLayout target, String label, String tag, String value, double step, boolean decimal,
		java.util.function.Consumer<String> change) {
		text(target, label, R.style.EnerGym_FieldCaption, 12);
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		android.widget.EditText field = new android.widget.EditText(requireContext());
		field.setTag(tag);
		field.setText(value);
		field.setGravity(android.view.Gravity.CENTER);
		field.setTextSize(28);
		field.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		field.setBackground(rounded(R.color.surface, 16, R.color.text_secondary, 0));
		field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
			(decimal ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
		field.setContentDescription(label);
		field.setSelectAllOnFocus(true);
		field.addTextChangedListener(new android.text.TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void onTextChanged(CharSequence s, int a, int b, int c) {
			}

			public void afterTextChanged(android.text.Editable s) {
				change.accept(s.toString());
			}
		});
		row.addView(stepButton("−", "Menos " + label.toLowerCase(Locale.ROOT), () -> {
			double next = Math.max(0, number(field.getText().toString(), 0) - step);
			field.setText(decimal ? SetLog.format(next) : Integer.toString((int) Math.max(1, next)));
		}), new LinearLayout.LayoutParams(dp(64), dp(64)));
		LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, dp(64), 1);
		middle.setMargins(dp(10), 0, dp(10), 0);
		row.addView(field, middle);
		row.addView(stepButton("+", "Más " + label.toLowerCase(Locale.ROOT), () -> {
			double next = number(field.getText().toString(), 0) + step;
			field.setText(decimal ? SetLog.format(next) : Integer.toString((int) next));
		}), new LinearLayout.LayoutParams(dp(64), dp(64)));
		add(target, row, 6);
	}

	private com.google.android.material.button.MaterialButton stepButton(String symbol, String description, Runnable click) {
		com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(
			requireContext(), null, com.google.android.material.R.attr.materialIconButtonFilledStyle);
		b.setText(symbol);
		b.setTextSize(26);
		b.setPadding(0, 0, 0, 0);
		b.setInsetTop(0);
		b.setInsetBottom(0);
		b.setCornerRadius(dp(16));
		b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
			androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface_selected)));
		b.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold));
		b.setContentDescription(description);
		b.setOnClickListener(v -> click.run());
		return b;
	}

	/** Historial del ejercicio: cada día que lo hiciste, con sus series y récords. */
	private void history(Exercise e) {
		List<Session> done = finishedWith(e.getId());
		if (done.isEmpty()) {
			card("Sin historial todavía", "Cuando termines un entreno con este ejercicio, aquí verás cada día y sus series.");
			return;
		}
		Collections.reverse(done);
		double best = 0;
		for (Session s : finishedWith(e.getId()))
			best = Math.max(best, SetLog.best(SetLog.of(s.getResults().get(e.getId()))));
		for (Session s : done.subList(0, Math.min(15, done.size()))) {
			List<SetLog.Entry> sets = SetLog.of(s.getResults().get(e.getId()));
			StringBuilder lines = new StringBuilder();
			for (int i = 0; i < sets.size(); i++) {
				SetLog.Entry x = sets.get(i);
				lines.append(i == 0 ? "" : "\n").append(i + 1).append(".  ").append(weight(x.kg)).append(" × ")
					.append(x.reps).append(x.kg > 0 && Math.abs(x.oneRepMax() - best) < 0.001 ? "  🏆" : "");
			}
			card(dayLong(s), lines.length() == 0 ? "Sin series registradas" : lines.toString());
		}
	}

	/** Gráfica del 1RM estimado de cada día (o del peso máximo si es a peso corporal, de las repeticiones). */
	private void graph(Exercise e) {
		List<Session> done = finishedWith(e.getId());
		if (done.size() < 2) {
			card("Aún no hay gráfica", "Con dos entrenos o más de este ejercicio verás aquí cómo progresas.");
			return;
		}
		List<Session> recent = done.subList(Math.max(0, done.size() - 12), done.size());
		boolean bodyweight = true;
		for (Session s : recent)
			for (SetLog.Entry x : SetLog.of(s.getResults().get(e.getId())))
				if (x.kg > 0)
					bodyweight = false;
		double[] values = new double[recent.size()];
		String[] labels = new String[recent.size()];
		double top = 0;
		for (int i = 0; i < recent.size(); i++) {
			List<SetLog.Entry> sets = SetLog.of(recent.get(i).getResults().get(e.getId()));
			double v = 0;
			for (SetLog.Entry x : sets)
				v = Math.max(v, bodyweight ? x.reps : x.oneRepMax());
			values[i] = Math.round(v * 10) / 10.0;
			labels[i] = day(recent.get(i));
			top = Math.max(top, values[i]);
		}
		LinearLayout card = card(bodyweight ? "Repeticiones máximas" : "1RM estimado",
			bodyweight ? "Mejor serie de cada día." : "Peso que podrías levantar una vez, calculado con tu mejor serie de cada día.");
		add(card, new com.energym.app.ui.charts.LineChartView(requireContext()), 12).set(values, labels,
			bodyweight ? "reps" : "kg", (bodyweight ? "Repeticiones máximas" : "1RM estimado") + " de " + e.getName());
		text(card, "🏆 Mejor marca: " + SetLog.format(top) + (bodyweight ? " repeticiones" : " kg"),
			R.style.EnerGym_Body, 12);
	}

	/** Al guardar una serie arranca solo el descanso previsto, como el temporizador de FitNotes. */
	private void startRest(RoutineItem item) {
		double seconds = number(item.getPrescription().getOrDefault("restSeconds", "60"), 60);
		if (seconds > 0)
			restEndsAt = SystemClock.elapsedRealtime() + (long) (seconds * 1000);
	}

	private List<Session> finishedWith(String exerciseId) {
		List<Session> result = new ArrayList<>();
		for (Session s : app.history)
			if (s.getStatus() == Session.Status.FINISHED && s.getResults().containsKey(exerciseId))
				result.add(s);
		result.sort(Comparator.comparingLong(Session::getCompletedAt));
		return result;
	}

	private double previousBest(String exerciseId) {
		double best = 0;
		for (Session s : finishedWith(exerciseId))
			best = Math.max(best, SetLog.best(SetLog.of(s.getResults().get(exerciseId))));
		return best;
	}

	/** Más repeticiones hechas en una serie sin carga (0 si nunca). */
	private int previousBestReps(String exerciseId) {
		int best = 0;
		for (Session s : finishedWith(exerciseId))
			for (SetLog.Entry x : SetLog.of(s.getResults().get(exerciseId)))
				if (x.kg == 0)
					best = Math.max(best, x.reps);
		return best;
	}

	private static String planned(RoutineItem item) {
		Map<String, String> p = item.getPrescription();
		String load = p.getOrDefault("loadKg", "0");
		return p.getOrDefault("sets", "?") + " × " + p.getOrDefault("repetitions", "?") + " · " +
			(number(load, 0) > 0 ? SetLog.format(number(load, 0)) + " kg" : "peso corporal") +
			" · descanso " + p.getOrDefault("restSeconds", "60") + " s";
	}

	/** «2 × 8 · 20 kg» si todas las series son iguales; si no, «20×8, 22,5×6…». */
	private static String compactSets(List<SetLog.Entry> sets) {
		boolean same = true;
		for (SetLog.Entry x : sets)
			same &= x.equals(sets.get(0));
		if (same)
			return sets.size() + " × " + sets.get(0).reps + " · " + weight(sets.get(0).kg).toLowerCase(Locale.ROOT);
		List<String> parts = new ArrayList<>();
		for (SetLog.Entry x : sets)
			parts.add((x.kg > 0 ? SetLog.format(x.kg) : "PC") + "×" + x.reps);
		return String.join(", ", parts);
	}

	private static String weight(double kg) {
		return kg > 0 ? SetLog.format(kg) + " kg" : "Peso corporal";
	}

	private static double number(String value, double fallback) {
		try {
			return Double.parseDouble(value.trim().replace(',', '.'));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String day(Session s) {
		return java.time.Instant.ofEpochMilli(s.getCompletedAt()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
			.format(java.time.format.DateTimeFormatter.ofPattern("d/M"));
	}

	private static String dayLong(Session s) {
		String d = java.time.Instant.ofEpochMilli(s.getCompletedAt()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
			.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "ES")));
		return Character.toUpperCase(d.charAt(0)) + d.substring(1);
	}

	/** Sesión terminada más reciente en la que se registró este ejercicio (en cualquier rutina). */
	private Session lastTime(String exerciseId) {
		Session best = null;
		for (Session s : app.history)
			if (s.getStatus() == Session.Status.FINISHED && s.getResults().containsKey(exerciseId) &&
				(best == null || s.getCompletedAt() > best.getCompletedAt()))
				best = s;
		return best;
	}

	/** Temporizador de descanso entre series: cuenta atrás visible y vibración al terminar. */
	private void rest() {
		section("Descanso", 32);
		LinearLayout card = card("⏱ Descanso", "");
		restLabel = text(card, "", R.style.EnerGym_CardTitle, 0);
		LinearLayout presets = columns(card, 8);
		for (int seconds : new int[]{60, 90, 120})
			compact(presets, seconds / 60 + ":" + (seconds % 60 == 0 ? "00" : "30"), true, () -> {
				restEndsAt = SystemClock.elapsedRealtime() + seconds * 1000L;
				tick();
			});
		compact(presets, "✕", true, () -> {
			restEndsAt = 0;
			tick();
		}).setContentDescription("Parar el descanso");
		tick();
	}

	private void tick() {
		ticker.removeCallbacks(tick);
		if (restLabel == null)
			return;
		long left = restEndsAt - SystemClock.elapsedRealtime();
		if (restEndsAt == 0) {
			restLabel.setText("Elige un descanso tras cada serie.");
			return;
		}
		if (left <= 0) {
			restEndsAt = 0;
			restLabel.setText("💪 ¡A por la siguiente serie!");
			vibrate();
			return;
		}
		long secs = (left + 999) / 1000;
		restLabel.setText(String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60));
		ticker.postDelayed(tick, Math.min(1000, left));
	}

	private void vibrate() {
		try {
			Vibrator v = Build.VERSION.SDK_INT >= 31 ?
				((VibratorManager) requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE))
					.getDefaultVibrator() :
				(Vibrator) requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
			if (v != null && v.hasVibrator())
				v.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 250}, -1));
		} catch (RuntimeException ignored) {
			// Sin vibración el aviso en pantalla basta.
		}
	}
}
