package com.energym.app.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.domain.*;

import java.util.*;

/**
 * VISTA del plan semanal antes de guardarlo, como las rutinas de FitNotes: cada día con sus ejercicios y su
 * objetivo (series × repeticiones · carga). Guardar crea una rutina por día; nada se escribe antes.
 */
public final class PlanFragment extends ScreenFragment {
	public PlanFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		List<Routine> plan = app.planPreview;
		if (plan.isEmpty()) {
			heading("", "Tu plan semanal", "");
			card("No hay plan pendiente", "Pídeselo al entrenador o créalo desde Entreno.");
			return;
		}
		int minutes = 0;
		for (Routine r : plan)
			minutes += r.getEstimatedMinutes();
		heading("PLAN SEMANAL", "Tu plan de " + plan.size() + (plan.size() == 1 ? " día" : " días"),
			"Una rutina por día de entreno. Al terminar un día, Entreno te deja preparado el siguiente. " +
				"Las cargas suben solas cuando cumples el objetivo.");
		for (Routine r : plan) {
			LinearLayout day = card(r.getTitle(), r.getItems().size() + " ejercicios · " + r.getEstimatedMinutes() + " min");
			for (RoutineItem item : r.getItems()) {
				android.view.View row = getLayoutInflater().inflate(R.layout.row_food, day, false);
				((TextView) row.findViewById(R.id.food_name)).setText(item.getExercise().getName());
				((TextView) row.findViewById(R.id.food_detail)).setText(target(item));
				((TextView) row.findViewById(R.id.food_kcal)).setText("›");
				row.setContentDescription("Ver técnica de " + item.getExercise().getName());
				row.setOnClickListener(v -> app.openExercise(item.getExercise()));
				day.addView(row);
			}
		}
		text(rows, "Total semanal: unos " + minutes + " min. Ejemplos sin revisión profesional; adapta la carga a cómo te encuentres.",
			R.style.EnerGym_FieldCaption, 8).setPadding(dp(4), 0, dp(4), 0);
		primary(action(rows, "Guardar plan", !app.trainingMutation, app::acceptPlan));
		action(rows, "🔀 Otra combinación de ejercicios", !app.trainingMutation, app::shufflePlan);
		action(rows, "Cancelar", !app.trainingMutation, app::cancelPlan);
	}

	/** «3 × 10 · 20 kg», «2 × 30 s», «20 min»… según el tipo de ejercicio. */
	static String target(RoutineItem item) {
		Map<String, String> p = item.getPrescription();
		switch (item.getExercise().getMetricType()) {
			case STRENGTH: {
				double kg;
				try {
					kg = Double.parseDouble(p.getOrDefault("loadKg", "0"));
				} catch (NumberFormatException e) {
					kg = 0;
				}
				return p.getOrDefault("sets", "?") + " × " + p.getOrDefault("repetitions", "?") + " · " +
					(kg > 0 ? SetLog.format(kg) + " kg" : "peso corporal");
			}
			case ISOMETRIC:
				return p.getOrDefault("sets", "?") + " × " + p.getOrDefault("durationSeconds", "?") + " s";
			case MOBILITY:
				return p.getOrDefault("repetitions", "?") + " reps · " + p.getOrDefault("durationSeconds", "?") + " s";
			case ENDURANCE:
				return p.getOrDefault("timeMinutes", "?") + " min";
			default:
				return p.getOrDefault("durationMinutes", "?") + " min";
		}
	}
}
