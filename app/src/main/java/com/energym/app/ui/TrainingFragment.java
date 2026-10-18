package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
	/** Sugerencia del entrenador pedida desde «+ Nuevo». */
	private boolean suggesting;

	public TrainingFragment() {
		super(R.layout.fragment_training);
	}

	@Override
	protected void content() {
		heading("", Txt.t(R.string.training_navigation), "");
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
		routines(hasNextStep);
		otherDrafts();
		createSection();
	}

	/** Dos pestañas: todas las rutinas juntas (las haya creado quien sea) y la biblioteca de ejercicios. */
	private void sections() {
		int current = state.training.pane == TrainingViewState.Pane.EXERCISES ? 1 : 0;
		segmented("training", new String[]{Txt.t(R.string.tr_tab_routines), Txt.t(R.string.tr_tab_exercises)},
			new String[]{"training_tab_mine", "training_tab_exercises"}, current, index -> {
				if (browser != null) browser.capture();
				state.training.select(index == 1 ? TrainingViewState.Pane.EXERCISES : TrainingViewState.Pane.MINE);
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

	/**
	 * Tarjeta principal con una sola acción clara según el momento: seguir lo empezado, revisar algo nuevo, subir de
	 * nivel o empezar el entreno de hoy.
	 */
	private boolean nextStep() {
		if (app.owner() == null) {
			LinearLayout account = card(Txt.t(R.string.tr_space), Txt.t(R.string.tr_space_body));
			primary(action(account, Txt.t(R.string.tr_choose_account), true, () -> app.navigate(Screen.ACCOUNT)));
			return true;
		}
		Session active = app.sessions.session != null && app.sessions.session.getStatus() == Session.Status.DRAFT ?
			app.sessions.session : null;
		List<Session> pending = distinctDrafts();
		Session draft = active != null ? active : pending.isEmpty() ? null : pending.get(0);
		if (draft != null) {
			featuredDraftId = draft.getSyncId();
				int saved = draft.getResults().size(), total = draft.getRoutine().getItems().size();
			LinearLayout resume = card(Txt.t(R.string.tr_half_done), draft.getRoutine().getTitle());
			add(resume, new com.energym.app.ui.charts.ProgressBarView(requireContext()), 4)
				.set(total == 0 ? 0 : saved / (double) total, Txt.t(R.string.tr_done_of_total, saved, total));
			primary(action(resume, Txt.t(R.string.tr_keep_training), !app.sessions.busy,
				() -> {
					if (active != null) app.navigate(Screen.SESSION);
					else app.resume(draft);
				}));
			if (app.routines.preview != null)
				action(resume, Txt.t(R.string.tr_review_new), !app.routines.busy, () -> app.navigate(Screen.ROUTINE));
			return true;
		}
		if (app.routines.preview != null) {
			Routine preview = app.routines.preview;
				LinearLayout proposal = card(Txt.t(R.string.tr_new_routine, preview.getTitle()), summary(preview));
			primary(action(proposal, Txt.t(R.string.tr_review_new), !app.routines.busy, () -> app.navigate(Screen.ROUTINE)));
			return true;
		}
		ProgressionEngine.Plan progression = app.progression();
		if (progression != null) {
				LinearLayout next = card(Txt.t(R.string.tr_level_up), Txt.t(R.string.tr_level_up_body,
				app.routines.accepted == null ? Txt.t(R.string.tr_your_routine) : app.routines.accepted.getTitle()));
			primary(action(next, Txt.t(R.string.tr_see_what_goes_up), !app.routines.busy, app::reviewProgression));
			action(next, Txt.t(R.string.tr_repeat_unchanged), !app.sessions.busy, app::startSession);
			return true;
		}
		if (app.routines.accepted == null) return false;
		Routine selected = app.routines.accepted;
		LinearLayout ready = card(Txt.t(R.string.tr_today, selected.getTitle()), summary(selected));
		primary(action(ready, Txt.t(R.string.tr_start_workout), !app.sessions.busy, app::startSession));
		action(ready, Txt.t(R.string.tr_see_exercises), !app.routines.busy, app::viewSelectedRoutine);
		return true;
	}

	/**
	 * Sugerencia del entrenador: una rutina para lo que menos has trabajado. Se puede hacer tal cual, modificar
	 * antes de guardarla o pedir otra.
	 */
	private void suggestion() {
		if (app.owner() == null)
			return;
		Routine s = app.suggest();
		if (s == null) {
			card(Txt.t(R.string.tr_no_suggestion), Txt.t(R.string.tr_no_suggestion_body));
			return;
		}
		LinearLayout card = card("💡 " + s.getTitle(), app.suggestionReason + "\n" + summary(s));
		card.setTag("training_suggestion");
		for (com.energym.app.domain.RoutineItem item : s.getItems()) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			((android.widget.TextView) row.findViewById(R.id.food_name)).setText(item.getExercise().getName());
			((android.widget.TextView) row.findViewById(R.id.food_detail)).setText(PlanFragment.target(item));
			((android.widget.TextView) row.findViewById(R.id.food_kcal)).setText("›");
			row.setContentDescription(Txt.t(R.string.tr_see_technique_of, item.getExercise().getName()));
			row.setOnClickListener(v -> app.openExercise(item.getExercise()));
			card.addView(row);
		}
		action(card, Txt.t(R.string.tr_other_suggestion), !app.trainingMutation, app::nextSuggestion);
		action(card, Txt.t(R.string.tr_modify), !app.trainingMutation, () -> app.editProposal(s)).setTag("suggestion_edit");
		primary(action(card, Txt.t(R.string.tr_do_it_now), !app.trainingMutation && !app.sessions.busy,
			() -> app.acceptAndStart(s, true)));
	}

	private void routines(boolean hasNextStep) {
		List<Routine> routines = new ArrayList<>(app.versions);
		// El entreno libre no es una rutina guardada: se ve en «Ahora» mientras está abierto.
		routines.removeIf(com.energym.app.domain.FreeWorkout::is);
		// Los días de un plan semanal van juntos y en orden (Día 1, Día 2…), como las rutinas de FitNotes.
		routines.sort(Comparator.comparingInt((Routine r) -> com.energym.app.domain.WeeklyPlan.dayOf(r) == null ? 1 : 0)
			.thenComparingInt(r -> com.energym.app.domain.WeeklyPlan.dayOf(r) == null ? 0 :
				com.energym.app.domain.WeeklyPlan.dayOf(r)[0]));
		// Si la única rutina es la de hoy, ya se ve arriba.
		if (routines.isEmpty() || routines.size() == 1 && app.routines.accepted != null &&
			routines.get(0).getProposalId().equals(app.routines.accepted.getProposalId()))
			return;
		section(Txt.t(R.string.tr_your_routines), hasNextStep ? 32 : 0);
		for (Routine routine : routines)
			routineRow(routine);
	}

	/**
	 * Todo lo que crea algo nuevo detrás de un solo botón: entreno libre, sugerencia, IA, plan semanal, a mano o
	 * automática. Sin rutinas se muestra abierto.
	 */
	private void createSection() {
		boolean ready = app.owner() != null && !app.loading;
		if (app.owner() == null)
			return;
		// Sin perfil no se puede crear nada útil: primero eso, con un solo botón.
		if (app.profile == null) {
			LinearLayout first = card(Txt.t(R.string.tr_profile_first), Txt.t(R.string.tr_profile_first_body));
			MaterialButton setup = action(first, Txt.t(R.string.home_configure_profile), true, () -> app.navigate(Screen.PROFILE));
			setup.setTag("training_profile");
			primary(setup);
			return;
		}
		boolean first = app.versions.isEmpty() && app.routines.accepted == null;
		boolean open = state.training.optionsExpanded || first;
		section(Txt.t(R.string.tr_new), 32);
		if (!first) {
			MaterialButton toggle = action(rows, Txt.t(open ? R.string.tr_close_menu : R.string.tr_new_menu), true, () -> {
				state.training.optionsExpanded = !state.training.optionsExpanded;
				suggesting = false;
				render();
			});
			toggle.setTag("training_new");
			if (!open)
				primary(toggle);
		}
		if (!open)
			return;
		LinearLayout create = card(Txt.t(first ? R.string.tr_create_first : R.string.tr_what_to_do), "");
		View freeStart = option(create, Txt.t(R.string.tr_free), Txt.t(R.string.tr_free_help),
			ready && !app.trainingMutation && app.profile != null,
			() -> ExercisePicker.show(requireContext(), Txt.t(R.string.tr_first_exercise), app.freeCandidates(), app::addFreeExercise));
		freeStart.setTag("training_free");
		option(create, Txt.t(R.string.tr_suggest), Txt.t(R.string.tr_suggest_help), ready && !app.trainingMutation, () -> {
			suggesting = true;
			render();
		}).setTag("training_suggest");
		View ai = option(create, Txt.t(R.string.tr_ask_trainer), Txt.t(R.string.tr_ask_trainer_help),
			ready && !state.assistant.busy, () -> {
				if (state.assistant.busy)
					return;
				state.assistant.selectLocalGuidance(false);
				state.assistant.messageInput("Prepara una rutina para mí.");
				app.navigate(Screen.ASSISTANT);
			});
		ai.setTag("training_create_ai");
		ai.setContentDescription(Txt.t(R.string.tr_ai_description));
		option(create, Txt.t(R.string.tr_weekly), Txt.t(R.string.tr_weekly_help),
			ready && !app.trainingMutation, () -> app.createPlan("")).setTag("training_create_plan");
		View manual = option(create, Txt.t(R.string.tr_manual), Txt.t(R.string.tr_manual_help),
			ready && !app.manual.busy, app::createManualRoutine);
		manual.setTag("training_create_manual");
		manual.setContentDescription(Txt.t(R.string.tr_manual_description));
		option(create, Txt.t(R.string.tr_auto), Txt.t(R.string.tr_auto_help),
			ready && !app.profileSaving() && !app.routines.busy, app::createRoutine).setTag("training_more_options");
		if (suggesting)
			suggestion();
	}

	/** Opción del menú «Nuevo»: fila compacta con su explicación de una línea. */
	private View option(LinearLayout target, String label, String help, boolean enabled, Runnable click) {
		return listRow(target, label, help, "›", enabled, click);
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
		row.setContentDescription(routine.getTitle() + ". " + summary(routine) + ". " +
			Txt.t(selected ? R.string.tr_is_today : R.string.tr_tap_to_train));
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
		meta.setText(summary(routine) + "\n" + origin(routine) +
			(routine.isProgressionEnabled() ? " · " + Txt.t(R.string.tr_levels_up) : "") +
			(selected ? "\n" + Txt.t(R.string.tr_today_mark) : ""));
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
		options.setContentDescription(Txt.t(R.string.tr_routine_options, routine.getTitle()));
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
			.setItems(new String[]{Txt.t(R.string.tr_view_edit), Txt.t(R.string.tr_delete_routine)}, (dialog, which) -> {
				if (scope != app.accountRevision()) return;
				if (which == 0) app.editRoutine(routine);
				else new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle(Txt.t(R.string.tr_delete_routine_title))
					.setMessage(Txt.t(R.string.tr_delete_routine_message))
					.setNegativeButton(Txt.t(R.string.common_cancel), null)
					.setPositiveButton(Txt.t(R.string.common_remove), (confirm, button) -> {
						if (scope == app.accountRevision()) app.deleteRoutine(routine);
					}).show();
			}).setNegativeButton(Txt.t(R.string.common_cancel), null).show();
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
		MaterialButton toggle = action(rows, Txt.t(R.string.tr_other_drafts, others.size()), true, () -> {
			state.training.draftsExpanded = !state.training.draftsExpanded;
			render();
		});
		toggle.setTag("training_drafts_toggle");
		toggle.setContentDescription(toggle.getText() + ", " + Txt.t(state.training.draftsExpanded ? R.string.tr_expanded : R.string.tr_collapsed));
		if (!state.training.draftsExpanded) return;
		LinearLayout drafts = card(Txt.t(R.string.tr_drafts), Txt.t(R.string.tr_drafts_body));
		for (Session draft : others) {
			text(drafts, draft.getRoutine().getTitle() + " · " + Txt.t(R.string.tr_exercises_logged, draft.getResults().size()),
				R.style.EnerGym_FieldCaption, 12);
			action(drafts, Txt.t(R.string.tr_continue_this), !app.sessions.busy, () -> app.resume(draft));
			action(drafts, Txt.t(R.string.ses_discard_confirm), !app.trainingMutation && !app.sessions.busy, () -> confirmDraftDelete(draft));
		}
	}

	private void confirmDraftDelete(Session draft) {
		long scope = app.accountRevision();
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(Txt.t(R.string.tr_delete_draft_title))
			.setMessage(Txt.t(R.string.ses_discard_message))
			.setNegativeButton(Txt.t(R.string.common_cancel), null)
			.setPositiveButton(Txt.t(R.string.common_remove), (dialog, button) -> {
				if (scope == app.accountRevision()) app.deleteSession(draft);
			}).show();
	}

	private void library() {
		myExercises();
		LinearLayout library = card(Txt.t(R.string.tr_library), Txt.t(R.string.tr_library_body, app.catalog.getExercises().size()));
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
				button.setContentDescription(Txt.t(R.string.tr_see_technique_of, exercise.getName()));
				text(target, ExerciseBrowser.metadata(exercise), R.style.EnerGym_FieldCaption, 4);
			}
			public void empty(LinearLayout target) {
				String query = state.exerciseBrowser.query.trim();
				if (query.isEmpty()) return;
				action(target, Txt.t(R.string.tr_ask_about_exercise), app.owner() != null, () -> {
					state.assistant.messageInput("Explícame la técnica del ejercicio «" + query +
						"»: posición inicial, ejecución, errores comunes y qué ejercicio del catálogo se parece más.");
					app.navigate(Screen.ASSISTANT);
				});
			}
		});
	}

	/** Ejercicios creados por la persona: se ven, se borran y se crean (botón al final). */
	private void myExercises() {
		com.energym.app.catalog.CustomExercises custom = state.customExercises;
		if (custom == null || app.owner() == null)
			return;
		List<Exercise> mine = custom.mine();
		LinearLayout card = card(Txt.t(R.string.tr_my_exercises), mine.isEmpty() ?
			Txt.t(R.string.tr_my_exercises_empty) : Txt.n(R.plurals.tr_my_exercises_count, mine.size()));
		card.setTag("training_my_exercises");
		for (Exercise e : mine) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			((android.widget.TextView) row.findViewById(R.id.food_name)).setText(e.getName());
			com.energym.app.domain.RoutineRequest.Area area = com.energym.app.catalog.CustomExercises.area(e);
			((android.widget.TextView) row.findViewById(R.id.food_detail)).setText(
				(area == null ? "" : Character.toUpperCase(area.display().charAt(0)) + area.display().substring(1) + " · ") +
					com.energym.app.controller.AppController.label(e.getMetricType().name()));
			((android.widget.TextView) row.findViewById(R.id.food_kcal)).setText("⋮");
			row.setContentDescription(Txt.t(R.string.tr_options_of, e.getName()));
			row.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(e.getName())
				.setItems(new String[]{Txt.t(R.string.tr_view_card), Txt.t(R.string.tr_delete_exercise)}, (dialog, which) -> {
					if (which == 0)
						app.openExercise(e);
					else
						new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
							.setTitle(Txt.t(R.string.tr_delete_exercise_title, e.getName()))
							.setMessage(Txt.t(R.string.tr_delete_exercise_message))
							.setNegativeButton(Txt.t(R.string.common_cancel), null)
							.setPositiveButton(Txt.t(R.string.common_delete), (d2, w2) -> custom.remove(e.getId())).show();
				}).setNegativeButton(Txt.t(R.string.common_cancel), null).show());
			card.addView(row);
		}
		action(card, Txt.t(R.string.tr_create_exercise), true, () -> CustomExerciseDialog.show(requireContext(), custom, app::error))
			.setTag("training_create_exercise");
	}

	private int color(int resource) {
		return ContextCompat.getColor(requireContext(), resource);
	}

	private String summary(Routine routine) {
		return Txt.t(R.string.tr_summary, routine.getItems().size(), routine.getEstimatedMinutes());
	}

	private String origin(Routine routine) {
		if (routine.getMetadata() == null) return Txt.t(R.string.tr_origin_saved);
		switch (routine.getMetadata().getOrigin()) {
			case AI: return Txt.t(R.string.tr_origin_ai);
			case MANUAL: return Txt.t(R.string.tr_origin_manual);
			default: return Txt.t(R.string.tr_origin_auto);
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
