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
		heading("TU PLAN", "Entrenamiento",
			"Revisa la propuesta antes de aceptarla. Puedes sustituir ejercicios compatibles.");
		LinearLayout movement = card("Referencias de movimiento",
			"Vista previa técnica sin revisión profesional. Consulta primero la ejecución escrita; la animación disponible corresponde a su variante exacta.");
		for (com.energym.app.domain.Exercise exercise : app.availableAnimations())
			action(movement, exercise.getName(), () -> app.openExercise(exercise));
		LinearLayout library = card("Biblioteca de ejercicios", app.catalog.getExercises().size() +
			" ejercicios con su técnica escrita. Busca por nombre, músculo o material.");
		LinearLayout results = new LinearLayout(requireContext());
		results.setOrientation(LinearLayout.VERTICAL);
		textField(library, "exercise_search", "Buscar ejercicio", query, false, false, q -> {
			query = q;
			search(results, q);
		});
		add(library, results, 0);
		search(results, query);
		LinearLayout profile = card("Tus filtros", app.profileSummary());
		action(profile, app.profile == null ? "Configurar mi perfil" : "Editar perfil",
			() -> app.navigate(Screen.PROFILE));
		action(profile, "Crear propuesta", !app.loading && !app.profileSaving() && !app.routines.busy,
			app::createRoutine);
		if (app.routines.preview != null) {
			LinearLayout p = card("Propuesta sin aceptar",
				app.routines.preview.getItems().size() + " ejercicios · " + app.routines.preview.getEstimatedMinutes() +
					" min estimados");
			action(p, "Revisar propuesta", () -> app.navigate(Screen.ROUTINE));
		}
		if (app.routines.accepted != null) {
			LinearLayout accepted = card("Rutina aceptada",
				app.routines.accepted.getTitle() + "\n" + app.routines.accepted.getItems().size() +
					" ejercicios · versión " + app.routines.accepted.getVersionId());
			action(accepted, "Empezar sesión", !app.sessions.busy, app::startSession);
		}
		for (Session draft : app.pending) {
			LinearLayout row = card("Borrador recuperable",
				draft.getRoutine().getTitle() + "\n" + draft.getResults().size() + " ejercicios registrados");
			action(row, "Continuar borrador", !app.sessions.busy, () -> app.resume(draft));
		}
		if (app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT) {
			LinearLayout active = card("Sesión abierta",
				"Los valores escritos se conservan al cambiar de pantalla o rotar. Usa Guardar ejercicio para persistirlos.");
			action(active, "Volver a mi sesión", () -> app.navigate(Screen.SESSION));
		}
		card("Alcance de la propuesta",
			"Ejemplos locales sin revisión profesional. El plan usa disciplina, objetivo, entorno, recursos y tiempo; no interpreta aún tus textos ni crea un calendario semanal.");
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
