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
		heading("", "Tu entreno", app.sessions.session == null ? "" : app.sessions.session.getRoutine().getTitle() +
			" · apunta lo que haces en cada ejercicio.");
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
		if (shownResults >= 0 && saved > shownResults)
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
		LinearLayout context = card(e.getName(),
			"Lo previsto:\n" + AppController.prescription(e.getMetricType(), item.getPrescription()) +
				"\n\n" + (app.sessions.session.getResults().containsKey(e.getId()) ?
				"✓ Ya apuntado. Puedes corregirlo antes de terminar." :
				"Hazlo y apunta abajo lo que has hecho de verdad."));
		Session last = lastTime(e.getId());
		if (last != null)
			text(context, "🕑 La última vez (" + java.time.Instant.ofEpochMilli(last.getCompletedAt())
				.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
				.format(java.time.format.DateTimeFormatter.ofPattern("d/M")) + "): " +
				AppController.metrics(e.getMetricType(), last.getResults().get(e.getId())), R.style.EnerGym_Body, 12);
		action(context, "Ver técnica: " + e.getName(), () -> app.openExercise(e));
		LinearLayout fields = card("Lo que has hecho", "Viene relleno con lo previsto: cambia solo lo que fue distinto." +
			" Carga 0 = peso corporal.");
		Map<String, String> inputs = app.sessions.inputs();
		for (MetricValidator.Field f : new MetricValidator().fields(e.getMetricType()))
			if (f.choices.isEmpty())
				textField(fields, f.key, f.label, inputs.getOrDefault(f.key, ""), true, f.integer,
					v -> app.sessions.input(f.key, v));
			else
				choice(fields, f.key, f.label, f.choices, f.choices, inputs.getOrDefault(f.key, ""),
					v -> app.sessions.input(f.key, v));
		primary(action(rows, "Guardar ejercicio", !app.sessions.busy, app.sessions::record));
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
