package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.Session;
import com.energym.app.domain.Routine;
import com.energym.app.domain.RoutineMetadata;
import com.google.android.material.button.MaterialButton;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.*;

/**
 * VISTA de preparación: presenta filtros y rutinas aceptadas sin generarlas por sí misma.
 */
public final class TrainingFragment extends ScreenFragment {
	public TrainingFragment() {
		super(R.layout.fragment_training);
	}

	protected void content() {
		heading("TU PLAN", "Entrenamientos", "");
		ExerciseBrowser.restore(restored, "library", state.exerciseBrowser, app.owner(), 0);
		restored = null;
		LinearLayout discover = card("Ejercicios", "Busca por nombre o material y afina con filtros.");
		MaterialButton shortcut = action(discover, "Buscar ejercicios", true, () -> {
			View search = getView().findViewWithTag("exercise_search");
			if (search != null) {
				search.requestFocus();
				search.requestRectangleOnScreen(new android.graphics.Rect(0, 0, search.getWidth(), search.getHeight()), true);
				search.post(() -> {
					android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)
						requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
					if (keyboard != null) keyboard.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
				});
			}
		});
		shortcut.setTag("exercise_search_shortcut");
		primary(shortcut);
		// 2. Dos formas explícitas de crear y elegir rutinas. Abrir el chat no envía datos.
		LinearLayout ai = card("Con IA", "Habla con tu entrenador personal y revisa cualquier propuesta antes de aceptarla.");
		primary(action(ai, "Crear rutina con IA", app.owner() != null && !app.loading, () -> {
			state.assistant.messageInput("Prepara una rutina para mí.");
			app.navigate(Screen.ASSISTANT);
		}));
		routineList(ai, true);
		LinearLayout mine = card("Mis rutinas", "Crea tu propia rutina con ejercicios del catálogo y su contexto independiente.");
		primary(action(mine, "Crear rutina manual", !app.loading && !app.manual.busy, app::createManualRoutine));
		routineList(mine, false);

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
		com.energym.app.domain.ProgressionEngine.Plan level = app.progression();
		if (level != null && app.routines.preview == null) {
			LinearLayout up = card("Sobrecarga progresiva", HomeFragment.summary(level));
			primary(action(up, "Revisar progresión", !app.routines.busy, app::reviewProgression));
		}
		if (app.routines.accepted != null) {
			LinearLayout accepted = card("Tu rutina", app.routines.accepted.getTitle() + " · " +
				app.routines.accepted.getItems().size() + " ejercicios · " + app.routines.accepted.getEstimatedMinutes() +
				" min");
			MaterialButton start = action(accepted, "Empezar sesión", !app.sessions.busy, app::startSession);
			if (app.routines.preview == null && level == null)
				primary(start);
			text(accepted, origin(app.routines.accepted) + " · Progresión " +
				(app.routines.accepted.isProgressionEnabled() ? "activada" : "desactivada"), R.style.EnerGym_FieldCaption, 8);
			action(accepted, "Ver ejercicios", !app.routines.busy && app.routines.preview == null, app::viewSelectedRoutine);
		}
		LinearLayout profile = card("Propuesta local", app.profile == null ?
			"Dinos tu objetivo, tiempo y material para crear tu rutina." : app.profileSummary());
		MaterialButton create = action(profile, "Crear propuesta",
			!app.loading && !app.profileSaving() && !app.routines.busy, app::createRoutine);
		if (app.routines.accepted == null && app.routines.preview == null && app.profile != null)
			primary(create);
		action(profile, app.profile == null ? "Configurar mi perfil" : "Editar perfil",
			() -> app.navigate(Screen.PROFILE));
		// 3. Explorar: biblioteca con buscador y animaciones 3D.
		int total = app.catalog.getExercises().size(), animated = app.availableAnimations().size();
		LinearLayout library = card("Biblioteca", total + " ejercicios con su técnica" +
			(animated == 0 ? "." : animated == total ? " y vista 3D." : "; " + animated + " con vista 3D."));
		ExerciseSearch search = new ExerciseSearch(app.catalog.getExercises());
		text(library, "Toca un ejercicio para ver su técnica.", R.style.EnerGym_FieldCaption, 8);
		browser = new ExerciseBrowser(this, library, state.exerciseBrowser, "exercise", false, new ExerciseBrowser.Results() {
			public List<Exercise> find(String query, ExerciseSearch.Filters filters, boolean onlyCompatible) {
				return search.find(query, filters);
			}
			public void row(LinearLayout target, Exercise exercise) {
				MaterialButton button = action(target, exercise.getName(), true, () -> {
					browser.capture();
					app.openExercise(exercise);
				});
				button.setContentDescription("Ver técnica de " + exercise.getName());
				text(target, ExerciseBrowser.metadata(exercise), R.style.EnerGym_FieldCaption, 4);
			}
			public void empty(LinearLayout target) {
				String query = state.exerciseBrowser.query.trim();
				if (!query.isEmpty()) action(target, "Preguntar al asistente por este ejercicio", app.owner() != null, () -> {
					state.assistant.messageInput("Explícame la técnica del ejercicio «" + query +
						"»: posición inicial, ejecución, errores comunes y qué ejercicio del catálogo se parece más.");
					app.navigate(Screen.ASSISTANT);
				});
			}
		});
		// Con la biblioteca casi completa en 3D, la lista dejaría de ser un atajo útil.
		if (animated > 0 && animated <= 12) {
			LinearLayout movement = card("Con vista 3D", "");
			for (com.energym.app.domain.Exercise exercise : app.availableAnimations())
				action(movement, exercise.getName(), () -> app.openExercise(exercise));
		}
		text(rows, "Rutinas de ejemplo sin revisión profesional: adapta la intensidad a tu situación.",
			R.style.EnerGym_FieldCaption, 4);
	}

	private void routineList(LinearLayout box, boolean ai) {
		int count = 0;
		for (Routine routine : app.versions) {
			boolean generated = routine.getMetadata() != null && routine.getMetadata().getOrigin() == RoutineMetadata.Origin.AI;
			if (generated != ai) continue;
			count++;
			boolean selected = app.routines.accepted != null &&
				app.routines.accepted.getProposalId().equals(routine.getProposalId());
			action(box, routine.getTitle() + (selected ? " · seleccionada" : ""), !app.routines.busy,
				() -> app.selectRoutine(routine.getProposalId()));
			text(box, origin(routine) + " · " + routine.getEstimatedMinutes() + " min · Progresión " +
				(routine.isProgressionEnabled() ? "activada" : "desactivada"), R.style.EnerGym_FieldCaption, 4);
		}
		if (count == 0) text(box, ai ? "Todavía no has aceptado rutinas con IA." : "Aquí aparecerán tus rutinas manuales, locales y anteriores.",
			R.style.EnerGym_FieldCaption, 8);
	}

	private String origin(Routine routine) {
		if (routine.getMetadata() == null) return "Rutina anterior · origen no registrado";
		switch (routine.getMetadata().getOrigin()) {
			case AI: return "Creada con IA";
			case MANUAL: return "Creada por ti";
			default: return "Reglas locales";
		}
	}

	private ExerciseBrowser browser;
	private Bundle restored;

	@Override
	protected void beforeContentReset() {
		if (browser != null) browser.capture();
	}

	@Override public void onCreate(Bundle saved) {
		super.onCreate(saved);
		restored = saved;
	}

	@Override public void onSaveInstanceState(Bundle saved) {
		super.onSaveInstanceState(saved);
		if (browser != null) browser.capture();
		if (state != null && app != null) ExerciseBrowser.save(saved, "library", state.exerciseBrowser, app.owner(), 0);
	}

	@Override public void onDestroyView() {
		browser = null;
		super.onDestroyView();
	}
}
