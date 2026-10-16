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
		heading("REGISTRA LO REAL", "Tu sesión",
			"Anota lo que has hecho en cada ejercicio.");
		if (app.sessions.session == null) {
			card(app.sessions.busy ? "Abriendo borrador…" : "Sin sesión",
				app.sessions.busy ? "Consultando sus valores guardados." : "Acepta una rutina desde Entreno.");
			return;
		}
		if (app.sessions.session.getStatus() == Session.Status.FINISHED) {
			card("Sesión finalizada", "Consulta sus valores en Progreso.");
			return;
		}
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		int i = 0;
		for (RoutineItem item : app.sessions.session.getRoutine().getItems()) {
			ids.add(Integer.toString(i++));
			names.add(item.getExercise().getName());
		}
		section("Ejercicio actual", 8);
		choice(rows, "selectedExercise", "Ejercicio de la sesión", ids, names, Integer.toString(app.sessions.selected),
			value -> app.sessions.select(Integer.parseInt(value)));
		RoutineItem item = app.sessions.item();
		Exercise e = item.getExercise();
		LinearLayout context = card(e.getName(),
			"Propuesta orientativa:\n" + AppController.prescription(e.getMetricType(), item.getPrescription()) +
				"\n\n" + (app.sessions.session.getResults().containsKey(e.getId()) ?
				"Valores guardados. Puedes actualizarlos antes de finalizar." : "Todavía sin valores guardados."));
		action(context, "Ver técnica: " + e.getName(), () -> app.openExercise(e));
		LinearLayout fields = card("Valores realizados", AppController.label(e.getMetricType().name()) +
			" · carga 0 = peso corporal");
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
		section("Cierre", 32);
		LinearLayout finish = card("Cerrar el entrenamiento",
			app.sessions.session.getResults().size() + " de " + ids.size() +
				" ejercicios registrados. Guarda todos antes de finalizar.");
		action(finish, "Finalizar sesión", !app.sessions.busy, app.sessions::finish);
		action(finish, "Eliminar borrador", !app.sessions.busy && !app.trainingMutation, () -> {
			Session current = app.sessions.session;
			long scope = app.accountRevision();
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("¿Eliminar este borrador?")
				.setMessage("Se eliminarán sus registros de tu cuenta. Esta acción no se puede deshacer.")
				.setNegativeButton("Cancelar", null)
				.setPositiveButton("Eliminar", (dialog, button) -> {
					if (scope == app.accountRevision()) app.deleteSession(current);
				}).show();
		});
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
