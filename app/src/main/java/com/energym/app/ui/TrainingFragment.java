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
		heading("", "Entreno", "Elige una rutina, entrena y apunta lo que haces. Tus rutinas suben de nivel solas.");
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
		createSection(hasNextStep);
	}

	/** Dos pestañas: todas las rutinas juntas (las haya creado quien sea) y la biblioteca de ejercicios. */
	private void sections() {
		int current = state.training.pane == TrainingViewState.Pane.EXERCISES ? 1 : 0;
		segmented("training", new String[]{"Rutinas", "Ejercicios"},
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
			section("Ahora", 0);
			int saved = draft.getResults().size(), total = draft.getRoutine().getItems().size();
			LinearLayout resume = card("⏸ Entreno a medias", draft.getRoutine().getTitle());
			add(resume, new com.energym.app.ui.charts.ProgressBarView(requireContext()), 4)
				.set(total == 0 ? 0 : saved / (double) total, saved + " de " + total + " ejercicios hechos");
			primary(action(resume, "Seguir entrenando", !app.sessions.busy,
				() -> {
					if (active != null) app.navigate(Screen.SESSION);
					else app.resume(draft);
				}));
			if (app.routines.preview != null)
				action(resume, "Revisar rutina nueva", !app.routines.busy, () -> app.navigate(Screen.ROUTINE));
			return true;
		}
		if (app.routines.preview != null) {
			Routine preview = app.routines.preview;
			section("Ahora", 0);
			LinearLayout proposal = card("✨ Tienes una rutina nueva", preview.getTitle() + "\n" + summary(preview) +
				"\nRevísala y acéptala para poder entrenar con ella.");
			primary(action(proposal, "Revisar rutina nueva", !app.routines.busy, () -> app.navigate(Screen.ROUTINE)));
			return true;
		}
		ProgressionEngine.Plan progression = app.progression();
		if (progression != null) {
			section("Ahora", 0);
			LinearLayout next = card("📈 ¡Toca subir de nivel!", "Lo has hecho bien en «" +
				(app.routines.accepted == null ? "tu rutina" : app.routines.accepted.getTitle()) +
				"». Mira qué subimos (repeticiones o carga) y entrena con la versión nueva.");
			primary(action(next, "Ver qué sube", !app.routines.busy, app::reviewProgression));
			action(next, "Repetir sin cambios", !app.sessions.busy, app::startSession);
			return true;
		}
		if (app.routines.accepted == null) return false;
		Routine selected = app.routines.accepted;
		section("Hoy", 0);
		LinearLayout ready = card("Tu entreno de hoy", selected.getTitle() + "\n" + summary(selected) +
			(selected.isProgressionEnabled() ? "\n📈 Sube de nivel sola cuando la completas" : ""));
		primary(action(ready, "Empezar entreno", !app.sessions.busy, app::startSession));
		action(ready, "Ver ejercicios", !app.routines.busy, app::viewSelectedRoutine);
		return true;
	}

	private void routines(boolean hasNextStep) {
		List<Routine> routines = new ArrayList<>(app.versions);
		// Los días de un plan semanal van juntos y en orden (Día 1, Día 2…), como las rutinas de FitNotes.
		routines.sort(Comparator.comparingInt((Routine r) -> com.energym.app.domain.WeeklyPlan.dayOf(r) == null ? 1 : 0)
			.thenComparingInt(r -> com.energym.app.domain.WeeklyPlan.dayOf(r) == null ? 0 :
				com.energym.app.domain.WeeklyPlan.dayOf(r)[0]));
		if (!app.lastProgression.isEmpty()) {
			section("Última subida de nivel", hasNextStep ? 32 : 0);
			card("📈 Tu rutina ha mejorado", "• " + String.join("\n• ", app.lastProgression));
			hasNextStep = true;
		}
		section("Tus rutinas", hasNextStep ? 32 : 0);
		if (routines.isEmpty()) {
			card("Empieza aquí", "Aún no tienes rutinas. Pídele una al entrenador con lo que quieras trabajar " +
				"(por ejemplo «piernas, 30 minutos») o créala tú eligiendo los ejercicios.");
			return;
		}
		if (routines.size() > 1)
			text(rows, "Toca una rutina para entrenar con ella hoy. Con ⋮ la editas o la borras.",
				R.style.EnerGym_FieldCaption, 0).setPadding(dp(4), 0, dp(4), dp(12));
		for (Routine routine : routines)
			routineRow(routine);
	}

	/** Crear siempre a la vista: con el entrenador, a mano o una automática al momento. */
	private void createSection(boolean hasNextStep) {
		section("Crear una rutina", 32);
		boolean ready = app.owner() != null && !app.loading;
		LinearLayout create = card("¿Cómo la quieres crear?", "");
		MaterialButton ai = action(create, "✦ Pedírsela al entrenador", ready && !state.assistant.busy, () -> {
			if (state.assistant.busy)
				return;
			state.assistant.selectLocalGuidance(false);
			state.assistant.messageInput("Prepara una rutina para mí.");
			app.navigate(Screen.ASSISTANT);
		});
		ai.setTag("training_create_ai");
		ai.setContentDescription("Crear rutina con IA");
		text(create, "Dile qué quieres trabajar, cuánto tiempo tienes o cómo te encuentras.", R.style.EnerGym_FieldCaption, 0)
			.setPadding(dp(4), 0, dp(4), dp(8));
		MaterialButton plan = action(create, "📅 Plan semanal", ready && !app.trainingMutation, () -> app.createPlan(""));
		plan.setTag("training_create_plan");
		text(create, "Una rutina por día según los días que entrenas (Empuje / Tirón / Pierna, Torso / Pierna…).",
			R.style.EnerGym_FieldCaption, 0).setPadding(dp(4), 0, dp(4), dp(8));
		MaterialButton manual = action(create, "✍ Hacerla yo", ready && !app.manual.busy, app::createManualRoutine);
		manual.setTag("training_create_manual");
		manual.setContentDescription("Crear rutina manual");
		text(create, "Eliges los ejercicios, series, repeticiones y carga.", R.style.EnerGym_FieldCaption, 0)
			.setPadding(dp(4), 0, dp(4), dp(8));
		MaterialButton quick = action(create, "⚡ Una automática ya",
			ready && !app.profileSaving() && !app.routines.busy, app::createRoutine);
		quick.setTag("training_more_options");
		text(create, "Te propongo una al momento con tu perfil, sin IA. La revisas antes de guardarla.",
			R.style.EnerGym_FieldCaption, 0).setPadding(dp(4), 0, dp(4), 0);
		if (!hasNextStep && app.versions.isEmpty())
			primary(ai);
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
		row.setContentDescription(routine.getTitle() + ". " + summary(routine) + (selected ? ". Es la de hoy" : ". Toca para entrenar con ella hoy"));
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
			(routine.isProgressionEnabled() ? " · 📈 Sube de nivel sola" : "") + (selected ? "\n✓ La de hoy" : ""));
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
			.setItems(new String[]{"Ver o editar ejercicios", "Eliminar rutina"}, (dialog, which) -> {
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
		MaterialButton toggle = action(rows, "Otros entrenos a medias (" + others.size() + ")", true, () -> {
			state.training.draftsExpanded = !state.training.draftsExpanded;
			render();
		});
		toggle.setTag("training_drafts_toggle");
		toggle.setContentDescription(toggle.getText() + (state.training.draftsExpanded ? ", desplegados" : ", plegados"));
		if (!state.training.draftsExpanded) return;
		LinearLayout drafts = card("Entrenos a medias", "Cada uno conserva lo que ya apuntaste.");
		for (Session draft : others) {
			text(drafts, draft.getRoutine().getTitle() + " · " + draft.getResults().size() + " ejercicios registrados",
				R.style.EnerGym_FieldCaption, 12);
			action(drafts, "Seguir este", !app.sessions.busy, () -> app.resume(draft));
			action(drafts, "Descartar", !app.trainingMutation && !app.sessions.busy, () -> confirmDraftDelete(draft));
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
		if (routine.getMetadata() == null) return "Rutina guardada";
		switch (routine.getMetadata().getOrigin()) {
			case AI: return "✦ Hecha con IA";
			case MANUAL: return "✍ Hecha por ti";
			default: return "⚡ Automática";
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
