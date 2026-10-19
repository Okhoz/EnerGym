package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
			heading("", Txt.t(R.string.plan_title), "");
			card(Txt.t(R.string.plan_none), Txt.t(R.string.plan_none_body));
			return;
		}
		int minutes = 0;
		for (Routine r : plan)
			minutes += r.getEstimatedMinutes();
		heading(Txt.t(R.string.plan_eyebrow), Txt.n(R.plurals.plan_days, plan.size()), Txt.t(R.string.plan_body));
		for (Routine r : plan) {
			LinearLayout day = card(r.getTitle(), Txt.n(R.plurals.tr_summary, r.getItems().size(), r.getItems().size(), r.getEstimatedMinutes()));
			for (RoutineItem item : r.getItems()) {
				android.view.View row = getLayoutInflater().inflate(R.layout.row_food, day, false);
				((TextView) row.findViewById(R.id.food_name)).setText(item.getExercise().getName());
				((TextView) row.findViewById(R.id.food_detail)).setText(target(item));
				((TextView) row.findViewById(R.id.food_kcal)).setText("›");
				row.setContentDescription(Txt.t(R.string.tr_see_technique_of, item.getExercise().getName()));
				row.setOnClickListener(v -> app.openExercise(item.getExercise()));
				day.addView(row);
			}
		}
		text(rows, Txt.t(R.string.plan_total, minutes),
			R.style.EnerGym_FieldCaption, 8);
		action(rows, Txt.t(R.string.plan_shuffle), !app.trainingMutation, app::shufflePlan);
		primary(action(rows, Txt.t(R.string.plan_save), !app.trainingMutation, app::acceptPlan));
		action(rows, Txt.t(R.string.common_cancel), !app.trainingMutation, app::cancelPlan);
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
					(kg > 0 ? SetLog.format(kg) + " kg" : Txt.t(R.string.ses_bodyweight_lower));
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
