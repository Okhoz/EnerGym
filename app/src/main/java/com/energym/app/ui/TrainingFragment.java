package com.energym.app.ui;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.controller.AppController;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.Session;
import com.energym.app.domain.Sport;
import com.energym.app.domain.TrainingProfile.*;
import com.google.android.material.button.MaterialButton;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.*;

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
		int total = app.catalog.getExercises().size(), animated = app.availableAnimations().size();
		LinearLayout library = card("Biblioteca", total + " ejercicios con su técnica" +
			(animated == 0 ? "." : animated == total ? " y vista 3D." : "; " + animated + " con vista 3D."));
		ExerciseSearch search = new ExerciseSearch(app.catalog.getExercises());
		LinearLayout results = vertical();
		textField(library, "exercise_search", "Buscar por nombre, técnica o material", query, false, false, q -> {
			query = q;
			visible = PAGE_SIZE;
			search(results, search);
		});
		LinearLayout filters = vertical();
		MaterialButton toggle = action(library, expanded ? "Ocultar filtros" : "Filtrar ejercicios", true, () -> {
			expanded = !expanded;
			filters.setVisibility(expanded ? View.VISIBLE : View.GONE);
			// Se actualiza sólo el control pulsado, sin reconstruir el campo de búsqueda ni su foco.
			for (int i = 0; i < library.getChildCount(); i++) {
				View child = library.getChildAt(i);
				if ("exercise_filters_toggle".equals(child.getTag()))
					((MaterialButton) child).setText(expanded ? "Ocultar filtros" : "Filtrar ejercicios");
			}
		});
		toggle.setTag("exercise_filters_toggle");
		List<String> sportValues = new ArrayList<>(Collections.singletonList(""));
		List<String> sportLabels = new ArrayList<>(Collections.singletonList("Todas las disciplinas"));
		for (Sport sport : app.catalog.getSports()) {
			sportValues.add(sport.getId());
			sportLabels.add(sport.getName());
		}
		choice(filters, "exercise_filter_sport", "Disciplina", sportValues, sportLabels, discipline, value -> {
			discipline = value;
			visible = PAGE_SIZE;
			search(results, search);
		});
		filter(filters, "environment", "Entorno", Environment.values(), environment, value -> {
			environment = value;
			visible = PAGE_SIZE;
			search(results, search);
		});
		List<String> materialValues = new ArrayList<>(Arrays.asList("", "NONE"));
		List<String> materialLabels = new ArrayList<>(Arrays.asList("Cualquier material", "Sin material"));
		for (Resource resource : Resource.values()) {
			materialValues.add(resource.name());
			materialLabels.add(AppController.label(resource.name()));
		}
		choice(filters, "exercise_filter_material", "Material que requiere", materialValues, materialLabels, material, value -> {
			material = value;
			visible = PAGE_SIZE;
			search(results, search);
		});
		filter(filters, "goal", "Objetivo", Goal.values(), goal, value -> {
			goal = value;
			visible = PAGE_SIZE;
			search(results, search);
		});
		text(filters, "Los filtros exploran la biblioteca. Tu perfil sigue determinando qué ejercicios admite tu rutina.",
			R.style.EnerGym_FieldCaption, 8);
		add(library, filters, 0);
		filters.setVisibility(expanded ? View.VISIBLE : View.GONE);
		action(library, "Limpiar búsqueda y filtros", true, () -> {
			query = discipline = environment = material = goal = "";
			visible = PAGE_SIZE;
			render();
		});
		add(library, results, 0);
		search(results, search);
		// Con la biblioteca casi completa en 3D, la lista dejaría de ser un atajo útil.
		if (animated > 0 && animated <= 12) {
			LinearLayout movement = card("Con vista 3D", "");
			for (com.energym.app.domain.Exercise exercise : app.availableAnimations())
				action(movement, exercise.getName(), () -> app.openExercise(exercise));
		}
		text(rows, "Rutinas de ejemplo sin revisión profesional: adapta la intensidad a tu situación.",
			R.style.EnerGym_FieldCaption, 4);
	}

	private String query = "";
	private String discipline = "", environment = "", material = "", goal = "";
	private boolean expanded;
	private static final int PAGE_SIZE = 12;
	private int visible = PAGE_SIZE;

	@Override
	public void onCreate(Bundle saved) {
		super.onCreate(saved);
		if (saved != null) {
			query = saved.getString("library.query", "");
			discipline = saved.getString("library.discipline", "");
			environment = saved.getString("library.environment", "");
			material = saved.getString("library.material", "");
			goal = saved.getString("library.goal", "");
			expanded = saved.getBoolean("library.expanded", false);
			visible = saved.getInt("library.visible", PAGE_SIZE);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle saved) {
		super.onSaveInstanceState(saved);
		saved.putString("library.query", query);
		saved.putString("library.discipline", discipline);
		saved.putString("library.environment", environment);
		saved.putString("library.material", material);
		saved.putString("library.goal", goal);
		saved.putBoolean("library.expanded", expanded);
		saved.putInt("library.visible", visible);
	}

	private LinearLayout vertical() {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		return layout;
	}

	private void filter(LinearLayout target, String key, String label, Enum<?>[] options, String selected,
		java.util.function.Consumer<String> event) {
		List<String> values = new ArrayList<>(Collections.singletonList(""));
		List<String> labels = new ArrayList<>(Collections.singletonList("Cualquier " + label.toLowerCase(Locale.ROOT)));
		for (Enum<?> option : options) {
			values.add(option.name());
			labels.add(AppController.label(option.name()));
		}
		choice(target, "exercise_filter_" + key, label, values, labels, selected, event);
	}

	/** La vista remite consulta y filtros al modelo; sólo pagina y presenta sus resultados. */
	private void search(LinearLayout results, ExerciseSearch search) {
		results.removeAllViews();
		List<Exercise> found = search.find(query, new ExerciseSearch.Filters(discipline,
			environment.isEmpty() ? null : Environment.valueOf(environment), material,
			goal.isEmpty() ? null : Goal.valueOf(goal)));
		int shown = Math.min(visible, found.size());
		TextView count = text(results, found.size() + (found.size() == 1 ? " resultado" : " resultados") +
			" · " + shown + " visibles", R.style.EnerGym_FieldCaption, 12);
		count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		for (int i = 0; i < shown; i++) {
			Exercise e = found.get(i);
			MaterialButton b = action(results, e.getName(), true, () -> app.openExercise(e));
			((LinearLayout.LayoutParams) b.getLayoutParams()).topMargin = dp(8);
			b.setContentDescription("Abrir técnica de " + e.getName());
		}
		if (shown < found.size())
			action(results, "Mostrar más ejercicios", true, () -> {
				visible += PAGE_SIZE;
				search(results, search);
			});
		if (found.isEmpty()) {
			text(results, "No hay ejercicios con esta búsqueda y estos filtros. Prueba otro nombre o limpia los filtros.",
				R.style.EnerGym_Body, 12);
			if (!query.trim().isEmpty())
				action(results, "Preguntar al asistente por este ejercicio", app.owner() != null, () -> {
					state.assistant.messageInput("Explícame la técnica del ejercicio «" + query.trim() +
						"»: posición inicial, ejecución, errores comunes y qué ejercicio del catálogo se parece más.");
					app.navigate(Screen.ASSISTANT);
				});
		}
	}
}
