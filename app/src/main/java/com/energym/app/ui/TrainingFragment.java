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
		heading("", "Entreno", "Tus rutinas suben de nivel solas cuando completas las sesiones.");
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

	private static final TrainingViewState.Pane[] PANES = {TrainingViewState.Pane.MINE, TrainingViewState.Pane.AI,
		TrainingViewState.Pane.EXERCISES};

	private void sections() {
		int current = Arrays.asList(PANES).indexOf(state.training.pane);
		segmented("training", new String[]{"Mis rutinas", "Con IA", "Ejercicios"},
			new String[]{"training_tab_mine", "training_tab_ai", "training_tab_exercises"}, current, index -> {
				if (browser != null) browser.capture();
				state.training.select(PANES[index]);
				View view = getView();
				InputMethodManager keyboard = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
				if (keyboard != null && view != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
				render();
				if (getView() != null)
					getView().post(() -> {
						if (getView() instanceof android.widget.ScrollView)
							((android.widget.ScrollView) getView()).smoothScrollTo(0, 0);
					});
			});
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
			section("En curso", 0);
			int saved = draft.getResults().size(), total = draft.getRoutine().getItems().size();
			LinearLayout resume = card("Continúa tu entreno", draft.getRoutine().getTitle());
			add(resume, new com.energym.app.ui.charts.ProgressBarView(requireContext()), 4)
				.set(total == 0 ? 0 : saved / (double) total, saved + " de " + total + " ejercicios registrados");
			text(resume, saved + " de " + total + " ejercicios registrados", R.style.EnerGym_FieldCaption, 8);
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
			section("Pendiente", 0);
			LinearLayout proposal = card("Propuesta pendiente", preview.getTitle() + "\n" + summary(preview));
			primary(action(proposal, "Revisar propuesta", !app.routines.busy, () -> app.navigate(Screen.ROUTINE)));
			return true;
		}
		ProgressionEngine.Plan progression = app.progression();
		if (progression != null) {
			section("Siguiente nivel", 0);
			LinearLayout next = card("Tu próxima progresión", "Hay ajustes de sobrecarga progresiva para tu rutina seleccionada.");
			primary(action(next, "Revisar progresión", !app.routines.busy, app::reviewProgression));
			action(next, "Repetir rutina actual", !app.sessions.busy, app::startSession);
			return true;
		}
		if (app.routines.accepted == null) return false;
		Routine selected = app.routines.accepted;
		section("Hoy", 0);
		LinearLayout ready = card(selected.getTitle(), summary(selected) + (selected.isProgressionEnabled() ?
			"\n📈 Progresión automática activada" : ""));
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
		if (!app.lastProgression.isEmpty()) {
			section("Última subida de nivel", hasNextStep ? 32 : 0);
			card("📈 Tu rutina ha mejorado", "• " + String.join("\n• ", app.lastProgression));
			hasNextStep = true;
		}
		section(ai ? "Rutinas con IA" : "Tus rutinas", hasNextStep ? 32 : 0);
		if (routines.isEmpty()) {
			LinearLayout empty = card(ai ? "✦ Aún no tienes rutinas con IA" : "Aún no tienes rutinas propias",
				ai ? "Habla con tu entrenador para preparar un plan a tu medida." :
					"Elige ejercicios y ajusta tus series, repeticiones y cargas.");
			MaterialButton create = createButton(empty, ai);
			if (!hasNextStep) primary(create);
			return;
		}
		for (Routine routine : routines)
			routineRow(routine);
		MaterialButton create = createButton(rows, ai);
		((LinearLayout.LayoutParams) create.getLayoutParams()).topMargin = dp(4);
		if (!hasNextStep) primary(create);
	}

	private MaterialButton createButton(LinearLayout target, boolean ai) {
		return action(target, ai ? "Crear rutina con IA" : "Crear rutina manual",
			app.owner() != null && !app.loading && !app.manual.busy && (!ai || !state.assistant.busy), () -> {
				if (ai) {
					if (state.assistant.busy)
						return;
					state.assistant.selectLocalGuidance(false);
					state.assistant.messageInput("Prepara una rutina para mí.");
					app.navigate(Screen.ASSISTANT);
				} else app.createManualRoutine();
			});
	}

	/** Fila de rutina: título, datos clave y menú; la elegida lleva borde dorado. */
	private void routineRow(Routine routine) {
		boolean selected = app.routines.accepted != null &&
			app.routines.accepted.getProposalId().equals(routine.getProposalId());
		boolean enabled = !app.routines.busy && !app.trainingMutation;
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setPadding(dp(20), dp(18), dp(8), dp(18));
		row.setBackground(rounded(selected ? R.color.surface_selected : R.color.surface_card, 22,
			R.color.gold, selected ? 1 : 0));
		row.setMinimumHeight(dp(72));
		row.setClickable(enabled);
		row.setFocusable(true);
		row.setTag("routine_row_" + routine.getProposalId());
		row.setContentDescription(routine.getTitle() + ". " + summary(routine) + (selected ? ". Seleccionada" : ". Toca para seleccionarla"));
		row.setOnClickListener(v -> app.selectRoutine(routine.getProposalId()));
		android.util.TypedValue ripple = new android.util.TypedValue();
		requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
		row.setForeground(ContextCompat.getDrawable(requireContext(), ripple.resourceId));

		LinearLayout texts = new LinearLayout(requireContext());
		texts.setOrientation(LinearLayout.VERTICAL);
		android.widget.TextView title = new android.widget.TextView(requireContext());
		title.setTextAppearance(R.style.EnerGym_CardTitle);
		title.setText(routine.getTitle());
		title.setTextColor(color(selected ? R.color.gold : R.color.white));
		title.setMaxLines(2);
		title.setEllipsize(android.text.TextUtils.TruncateAt.END);
		texts.addView(title);
		android.widget.TextView meta = new android.widget.TextView(requireContext());
		meta.setTextAppearance(R.style.EnerGym_FieldCaption);
		meta.setText(summary(routine) + " · " + origin(routine) + (routine.isProgressionEnabled() ? " · 📈" : "") +
			(selected ? "\n✓ Seleccionada" : ""));
		LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		metaParams.topMargin = dp(6);
		texts.addView(meta, metaParams);
		row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

		MaterialButton options = new MaterialButton(requireContext(), null,
			com.google.android.material.R.attr.materialIconButtonStyle);
		options.setText("⋮");
		options.setTextSize(20);
		options.setTextColor(color(R.color.text_secondary));
		options.setEnabled(!app.trainingMutation && !app.manual.busy);
		options.setContentDescription("Opciones de la rutina " + routine.getTitle());
		options.setTag("routine_options_" + routine.getProposalId());
		options.setOnClickListener(v -> routineOptions(routine));
		row.addView(options, new LinearLayout.LayoutParams(dp(48), dp(48)));

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.bottomMargin = dp(12);
		rows.addView(row, params);
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
		toggle.setBackgroundTintList(ColorStateList.valueOf(color(android.R.color.transparent)));
		toggle.setTextColor(color(R.color.text_secondary));
		((LinearLayout.LayoutParams) toggle.getLayoutParams()).topMargin = dp(24);
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
