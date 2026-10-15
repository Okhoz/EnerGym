package com.energym.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.energym.app.R;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.ProgressionEngine;
import com.energym.app.domain.Routine;
import com.energym.app.domain.RoutineMetadata;
import com.energym.app.domain.Session;
import com.google.android.material.button.MaterialButton;
import java.util.*;

/** Vista MVC de Entreno: una sección visible y una acción principal según el estado actual. */
public final class TrainingFragment extends ScreenFragment {

	private ExerciseBrowser browser;
	private Bundle restored;
	private String featuredDraftId = "";

	public TrainingFragment() {
		super(R.layout.fragment_training);
	}

	@Override
	protected void content() {
		heading("", "Entreno", "");
		restoreSelection();
		// La referencia anterior se captura antes de retirar las filas y después se libera.
		browser = null;
		featuredDraftId = "";
		sections();
		if (state.training.pane == TrainingViewState.Pane.EXERCISES) {
			library();
			return;
		}
		boolean hasNextStep = nextStep();
		routines(state.training.pane == TrainingViewState.Pane.AI, hasNextStep);
		otherDrafts();
		moreOptions();
	}

	private void sections() {
		LinearLayout tabs = columns(rows, 0);
		tab(tabs, "Mis rutinas", "mine", TrainingViewState.Pane.MINE);
		tab(tabs, "Con IA", "ai", TrainingViewState.Pane.AI);
		tab(tabs, "Ejercicios", "exercises", TrainingViewState.Pane.EXERCISES);
		((LinearLayout.LayoutParams) tabs.getLayoutParams()).bottomMargin = dp(16);
	}

	private void tab(LinearLayout tabs, String label, String key, TrainingViewState.Pane pane) {
		boolean selected = state.training.pane == pane;
		MaterialButton button = new MaterialButton(requireContext(), null,
			com.google.android.material.R.attr.materialButtonOutlinedStyle);
		button.setText(label);
		button.setTag("training_tab_" + key);
		button.setTextSize(14);
		button.setMinWidth(0);
		button.setMinHeight(dp(48));
		button.setMaxLines(2);
		button.setPadding(dp(6), dp(4), dp(6), dp(4));
		button.setInsetTop(0);
		button.setInsetBottom(0);
		button.setCornerRadius(dp(16));
		button.setCheckable(true);
		button.setChecked(selected);
		button.setBackgroundTintList(ColorStateList.valueOf(color(selected ? R.color.surface_selected : R.color.surface)));
		button.setStrokeColor(ColorStateList.valueOf(color(selected ? R.color.gold : R.color.divider)));
		button.setStrokeWidth(dp(1));
		button.setTextColor(color(selected ? R.color.gold : R.color.text_secondary));
		button.setOnClickListener(view -> {
			if (browser != null) browser.capture();
			state.training.select(pane);
			InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
			if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
			render();
			getView().post(() -> {
				if (getView() instanceof android.widget.ScrollView)
					((android.widget.ScrollView) getView()).smoothScrollTo(0, 0);
			});
		});
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), 1);
		if (tabs.getChildCount() > 0) params.setMarginStart(dp(6));
		tabs.addView(button, params);
	}

	/** Continuar tiene prioridad; cada propuesta sigue siendo revisable antes de aceptarse. */
	private boolean nextStep() {
		if (app.owner() == null) {
			LinearLayout account = card("Tu espacio de entrenamiento", "Conecta tu cuenta para crear y guardar tus rutinas.");
			primary(action(account, "Elegir cuenta", true, () -> app.navigate(Screen.ACCOUNT)));
			return true;
		}
		Session active = app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT ?
			app.sessions.session : null;
		List<Session> pending = distinctDrafts();
		Session draft = active != null ? active : pending.isEmpty() ? null : pending.get(0);
		if (draft != null) {
			featuredDraftId = draft.getSyncId();
			LinearLayout resume = card("Continúa tu entreno", draft.getRoutine().getTitle() + "\n" +
				draft.getResults().size() + " de " + draft.getRoutine().getItems().size() + " ejercicios registrados");
			primary(action(resume, active != null ? "Volver a mi sesión" : "Continuar borrador", !app.sessions.busy,
				() -> {
					if (active != null) app.navigate(Screen.SESSION);
					else app.resume(draft);
				}));
			if (app.routines.preview != null)
				action(resume, "Revisar propuesta", !app.routines.busy, () -> app.navigate(Screen.ROUTINE));
			return true;
		}
		if (app.routines.preview != null) {
			Routine preview = app.routines.preview;
			LinearLayout proposal = card("Propuesta pendiente", preview.getTitle() + "\n" + summary(preview));
			primary(action(proposal, "Revisar propuesta", !app.routines.busy, () -> app.navigate(Screen.ROUTINE)));
			return true;
		}
		ProgressionEngine.Plan progression = app.progression();
		if (progression != null) {
			LinearLayout next = card("Tu próxima progresión", "Hay ajustes de sobrecarga progresiva para tu rutina seleccionada.");
			primary(action(next, "Revisar progresión", !app.routines.busy, app::reviewProgression));
			action(next, "Repetir rutina actual", !app.sessions.busy, app::startSession);
			return true;
		}
		if (app.routines.accepted == null) return false;
		Routine selected = app.routines.accepted;
		LinearLayout ready = card("Tu rutina seleccionada", selected.getTitle() + "\n" + summary(selected));
		primary(action(ready, "Empezar sesión", !app.sessions.busy, app::startSession));
		action(ready, "Ver ejercicios", !app.routines.busy, app::viewSelectedRoutine);
		return true;
	}

	private void routines(boolean ai, boolean hasNextStep) {
		List<Routine> routines = new ArrayList<>();
		for (Routine routine : app.versions) {
			boolean generated = routine.getMetadata() != null && routine.getMetadata().getOrigin() == RoutineMetadata.Origin.AI;
			if (generated == ai) routines.add(routine);
		}
		LinearLayout list = card(ai ? "Rutinas con IA" : "Tus rutinas", routines.isEmpty() ?
			(ai ? "Habla con tu entrenador para preparar un plan." : "Elige ejercicios y ajusta tus series, repeticiones y cargas.") :
			"Selecciona la rutina que quieras entrenar.");
		MaterialButton create = action(list, ai ? "Crear rutina con IA" : "Crear rutina manual",
			app.owner() != null && !app.loading && !app.manual.busy && (!ai || !state.assistant.busy), () -> {
				if (ai) {
					if (state.assistant.busy)
						return;
					state.assistant.selectLocalGuidance(false);
					state.assistant.messageInput("Prepara una rutina para mí.");
					app.navigate(Screen.ASSISTANT);
				} else app.createManualRoutine();
			});
		if (!hasNextStep) primary(create);
		for (Routine routine : routines) {
			boolean selected = app.routines.accepted != null &&
				app.routines.accepted.getProposalId().equals(routine.getProposalId());
			LinearLayout row = columns(list, 12);
			MaterialButton choose = compact(row, routine.getTitle() + (selected ? " · seleccionada" : ""),
				!app.routines.busy && !app.trainingMutation, () -> app.selectRoutine(routine.getProposalId()));
			choose.setMaxLines(3);
			MaterialButton options = compact(row, "⋮", !app.trainingMutation && !app.manual.busy,
				() -> routineOptions(routine));
			options.setContentDescription("Opciones de la rutina " + routine.getTitle());
			options.setTag("routine_options_" + routine.getProposalId());
			LinearLayout.LayoutParams size = (LinearLayout.LayoutParams) options.getLayoutParams();
			size.width = dp(56);
			size.weight = 0;
			choose.setTextColor(color(selected ? R.color.gold : R.color.white));
			text(list, origin(routine) + " · " + summary(routine) + "\nProgresión " +
				(routine.isProgressionEnabled() ? "activada" : "desactivada"), R.style.EnerGym_FieldCaption, 4);
		}
	}

	private void routineOptions(Routine routine) {
		long scope = app.accountRevision();
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(routine.getTitle())
			.setItems(new String[]{"Editar rutina", "Eliminar rutina"}, (dialog, which) -> {
				if (scope != app.accountRevision()) return;
				if (which == 0) app.editRoutine(routine);
				else new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle("¿Eliminar esta rutina?")
					.setMessage("Se quitará de tus rutinas y de tu cuenta sincronizada. Las sesiones que ya has registrado se conservan.")
					.setNegativeButton("Cancelar", null)
					.setPositiveButton("Eliminar", (confirm, button) -> {
						if (scope == app.accountRevision()) app.deleteRoutine(routine);
					}).show();
			}).setNegativeButton("Cancelar", null).show();
	}

	private List<Session> distinctDrafts() {
		Map<String, Session> unique = new LinkedHashMap<>();
		for (Session draft : app.pending)
			if (draft.getStatus() == Session.Status.DRAFT) unique.putIfAbsent(draft.getSyncId(), draft);
		return new ArrayList<>(unique.values());
	}

	private void otherDrafts() {
		List<Session> others = distinctDrafts();
		others.removeIf(draft -> featuredDraftId.equals(draft.getSyncId()));
		if (others.isEmpty()) return;
		MaterialButton toggle = action(rows, "Otros borradores (" + others.size() + ")", true, () -> {
			state.training.draftsExpanded = !state.training.draftsExpanded;
			render();
		});
		toggle.setTag("training_drafts_toggle");
		toggle.setContentDescription(toggle.getText() + (state.training.draftsExpanded ? ", desplegados" : ", plegados"));
		if (!state.training.draftsExpanded) return;
		LinearLayout drafts = card("Sesiones pendientes", "Cada borrador conserva sus propios registros.");
		for (Session draft : others) {
			text(drafts, draft.getRoutine().getTitle() + " · " + draft.getResults().size() + " ejercicios registrados",
				R.style.EnerGym_FieldCaption, 12);
			action(drafts, "Continuar borrador", !app.sessions.busy, () -> app.resume(draft));
			action(drafts, "Eliminar borrador", !app.trainingMutation && !app.sessions.busy, () -> confirmDraftDelete(draft));
		}
	}

	private void confirmDraftDelete(Session draft) {
		long scope = app.accountRevision();
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("¿Eliminar este borrador?")
			.setMessage("Se eliminarán sus registros de tu cuenta. Esta acción no se puede deshacer.")
			.setNegativeButton("Cancelar", null)
			.setPositiveButton("Eliminar", (dialog, button) -> {
				if (scope == app.accountRevision()) app.deleteSession(draft);
			}).show();
	}

	private void moreOptions() {
		MaterialButton toggle = action(rows, state.training.optionsExpanded ? "Menos opciones" : "Más opciones", true, () -> {
			state.training.optionsExpanded = !state.training.optionsExpanded;
			render();
		});
		toggle.setTag("training_more_options");
		if (!state.training.optionsExpanded) return;
		// El perfil se edita desde el menú ☰ de arriba a la derecha.
		LinearLayout options = card("Propuesta local", "Genera una propuesta por reglas locales, sin IA.");
		action(options, "Crear propuesta", !app.loading && !app.profileSaving() && !app.routines.busy, app::createRoutine);
	}

	private void library() {
		LinearLayout library = card("Biblioteca de ejercicios", app.catalog.getExercises().size() + " ejercicios del catálogo. Toca uno para ver su técnica.");
		ExerciseSearch search = new ExerciseSearch(app.catalog.getExercises());
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
				if (query.isEmpty()) return;
				action(target, "Preguntar al asistente por este ejercicio", app.owner() != null, () -> {
					state.assistant.messageInput("Explícame la técnica del ejercicio «" + query +
						"»: posición inicial, ejecución, errores comunes y qué ejercicio del catálogo se parece más.");
					app.navigate(Screen.ASSISTANT);
				});
			}
		});
	}

	private int color(int resource) {
		return ContextCompat.getColor(requireContext(), resource);
	}

	private String summary(Routine routine) {
		return routine.getItems().size() + " ejercicios · " + routine.getEstimatedMinutes() + " min";
	}

	private String origin(Routine routine) {
		if (routine.getMetadata() == null) return "Rutina anterior";
		switch (routine.getMetadata().getOrigin()) {
			case AI: return "Creada con IA";
			case MANUAL: return "Creada por ti";
			default: return "Reglas locales";
		}
	}

	private void restoreSelection() {
		if (restored != null && Objects.equals(app.owner(), restored.getString("training.owner")) &&
			restored.getLong("training.binding", -1) == state.training.bindingRevision()) {
			ExerciseBrowser.restore(restored, "library", state.exerciseBrowser, app.owner(), 0);
			try {
				state.training.select(TrainingViewState.Pane.valueOf(restored.getString("training.pane", "MINE")));
			} catch (IllegalArgumentException ignored) {
				state.training.select(TrainingViewState.Pane.MINE);
			}
			state.training.draftsExpanded = restored.getBoolean("training.drafts", false);
			state.training.optionsExpanded = restored.getBoolean("training.options", false);
		}
		restored = null;
	}

	@Override
	protected void beforeContentReset() {
		if (browser != null) browser.capture();
	}

	@Override
	public void onCreate(Bundle saved) {
		super.onCreate(saved);
		restored = saved;
	}

	@Override
	public void onSaveInstanceState(Bundle saved) {
		super.onSaveInstanceState(saved);
		if (browser != null) browser.capture();
		if (state == null || app == null) return;
		ExerciseBrowser.save(saved, "library", state.exerciseBrowser, app.owner(), 0);
		saved.putString("training.owner", app.owner());
		saved.putLong("training.binding", state.training.bindingRevision());
		saved.putString("training.pane", state.training.pane.name());
		saved.putBoolean("training.drafts", state.training.draftsExpanded);
		saved.putBoolean("training.options", state.training.optionsExpanded);
	}

	@Override
	public void onDestroyView() {
		browser = null;
		super.onDestroyView();
	}
}
