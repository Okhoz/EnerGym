package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.controller.AppController;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.Session;
import com.google.android.material.button.MaterialButton;

import android.widget.LinearLayout;

/**
 * VISTA de preparación: presenta filtros y rutinas aceptadas sin generarlas por sí misma.
 */
public final class TrainingFragment extends ScreenFragment {
	public TrainingFragment() {
		super(R.layout.fragment_training);
	}

	protected void content() {
		heading("TU PLAN", "Entreno", "");
		// 1. Lo que puedes hacer ahora: continuar, empezar o revisar.
		if (app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT) {
			LinearLayout active = card("Sesión abierta", "Tus valores se conservan aunque cambies de pantalla.");
			primary(action(active, "Volver a mi sesión", true, () -> app.navigate(Screen.SESSION)));
		}
		for (Session draft : app.pending) {
			LinearLayout row = card("Borrador recuperable",
				draft.getRoutine().getTitle() + " · " + draft.getResults().size() + " ejercicios registrados");
			action(row, "Continuar borrador", !app.sessions.busy, () -> app.resume(draft));
		}
		if (app.routines.preview != null) {
			LinearLayout p = card("Propuesta sin aceptar",
				app.routines.preview.getItems().size() + " ejercicios · " + app.routines.preview.getEstimatedMinutes() +
					" min");
			primary(action(p, "Revisar propuesta", true, () -> app.navigate(Screen.ROUTINE)));
		}
		if (app.routines.accepted != null) {
			LinearLayout accepted = card("Tu rutina", app.routines.accepted.getTitle() + " · " +
				app.routines.accepted.getItems().size() + " ejercicios · " + app.routines.accepted.getEstimatedMinutes() +
				" min");
			MaterialButton start = action(accepted, "Empezar sesión", !app.sessions.busy, app::startSession);
			if (app.routines.preview == null)
				primary(start);
			action(accepted, "Ver ejercicios", true, () -> app.navigate(Screen.ROUTINE));
		}
		// 2. Plan: perfil resumido y nueva propuesta.
		LinearLayout profile = card("Tu plan", app.profile == null ?
			"Dinos tu objetivo, tiempo y material para crear tu rutina." : app.profileSummary());
		MaterialButton create = action(profile, "Crear propuesta",
			!app.loading && !app.profileSaving() && !app.routines.busy, app::createRoutine);
		if (app.routines.accepted == null && app.routines.preview == null && app.profile != null)
			primary(create);
		action(profile, app.profile == null ? "Configurar mi perfil" : "Editar perfil",
			() -> app.navigate(Screen.PROFILE));
		// 3. Explorar: biblioteca con buscador y animaciones 3D.
		LinearLayout library = card("Biblioteca", app.catalog.getExercises().size() + " ejercicios con su técnica.");
		LinearLayout results = new LinearLayout(requireContext());
		results.setOrientation(LinearLayout.VERTICAL);
		textField(library, "exercise_search", "Buscar ejercicio", query, false, false, q -> {
			query = q;
			search(results, q);
		});
		add(library, results, 0);
		search(results, query);
		if (!app.availableAnimations().isEmpty()) {
			LinearLayout movement = card("Con vista 3D", "");
			for (com.energym.app.domain.Exercise exercise : app.availableAnimations())
				action(movement, exercise.getName(), () -> app.openExercise(exercise));
		}
		text(rows, "Rutinas de ejemplo sin revisión profesional: adapta la intensidad a tu situación.",
			R.style.EnerGym_FieldCaption, 4);
	}

	private String query = "";

	/**
	 * Filtra el catálogo sin reconstruir la pantalla (conserva el foco del teclado). Sin coincidencias, ofrece
	 * preguntar al asistente; la respuesta de la IA no se añade al catálogo ni a rutinas sin revisión.
	 */
	private void search(LinearLayout results, String raw) {
		results.removeAllViews();
		String q = normalize(raw);
		if (q.length() < 2)
			return;
		int shown = 0, total = 0;
		for (Exercise e : app.catalog.getExercises()) {
			String haystack = normalize(e.getName() + " " + e.getDescription() + " " + e.getId().replace('-', ' ') +
				" " + AppController.labels(e.getRequiredResources()));
			boolean all = true;
			for (String word : q.split("\\s+"))
				all &= haystack.contains(word);
			if (!all)
				continue;
			total++;
			if (shown++ < 8) {
				MaterialButton b = action(results, e.getName(), true, () -> app.openExercise(e));
				((LinearLayout.LayoutParams) b.getLayoutParams()).topMargin = dp(8);
				b.setContentDescription("Abrir técnica de " + e.getName());
			}
		}
		if (total > 8)
			text(results, "Y " + (total - 8) + " más. Afina la búsqueda.", R.style.EnerGym_Body, 8);
		if (total == 0) {
			text(results, "«" + raw.trim() + "» no está en el catálogo.", R.style.EnerGym_Body, 12);
			MaterialButton ask = action(results, "Preguntar al asistente por este ejercicio", app.owner() != null, () -> {
				state.assistant.messageInput("Explícame la técnica del ejercicio «" + raw.trim() +
					"»: posición inicial, ejecución, errores comunes y qué ejercicio del catálogo se parece más.");
				app.navigate(Screen.ASSISTANT);
			});
			((LinearLayout.LayoutParams) ask.getLayoutParams()).topMargin = dp(12);
		}
	}

	private static String normalize(String text) {
		return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).trim();
	}
}
