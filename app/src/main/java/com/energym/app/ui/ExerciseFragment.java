package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.domain.Exercise;

import android.widget.LinearLayout;

/**
 * VISTA de técnica; las rutas de explicación y visualización se conectarán al módulo correspondiente.
 */
public final class ExerciseFragment extends ScreenFragment {
	public ExerciseFragment() {
		super(R.layout.fragment_exercise);
	}

	protected void content() {
		Exercise e = app.exercise;
		heading(Txt.t(R.string.ex_eyebrow), e == null ? Txt.t(R.string.common_exercise) : e.getName(),
			"");
		if (e == null)
			return;
		card(Txt.t(R.string.ex_technique), e.getDescription());
		card(Txt.t(R.string.ex_context), AppController.label(e.getMetricType().name()) + "\n" + Txt.t(R.string.ex_resources,
			e.getRequiredResources().isEmpty() ? Txt.t(R.string.app_none) : AppController.labels(e.getRequiredResources())) + "\n" +
			Txt.t(e.isReviewed() ? R.string.ex_reviewed : R.string.ex_not_reviewed));
		LinearLayout help =
			card(Txt.t(R.string.ex_questions), Txt.t(R.string.ex_questions_body));
		action(help, Txt.t(R.string.ex_guided), app::requestExplanation);
		String explanation = app.visualizationExplanation();
		LinearLayout movement = card(Txt.t(R.string.ex_3d), explanation != null ? explanation :
			Txt.t(R.string.ex_no_animation));
		action(movement, Txt.t(R.string.ex_3d_preview), app.visualizationAvailable(), app::requestVisualization);
	}
}
