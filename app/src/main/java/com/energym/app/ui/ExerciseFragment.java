package com.energym.app.ui;

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
		heading("CONOCE EL MOVIMIENTO", e == null ? "Ejercicio" : e.getName(),
			"");
		if (e == null)
			return;
		card("Técnica y ejecución", e.getDescription());
		card("Contexto del ejercicio", AppController.label(e.getMetricType().name()) + "\nRecursos necesarios: " +
			(e.getRequiredResources().isEmpty() ? "ninguno" : AppController.labels(e.getRequiredResources())) + "\n" +
			(e.isReviewed() ? "Contenido revisado." : "Ejemplo sin revisión profesional."));
		LinearLayout help =
			card("¿Dudas?", "Pregunta al asistente por este ejercicio.");
		action(help, "Explicación guiada", app::requestExplanation);
		String explanation = app.visualizationExplanation();
		LinearLayout movement = card("Vista 3D", explanation != null ? explanation :
			"Este ejercicio aún no tiene animación; sigue la técnica escrita.");
		action(movement, "Vista previa 3D", app.visualizationAvailable(), app::requestVisualization);
	}
}
