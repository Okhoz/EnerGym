package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.controller.ManualRoutineController;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.button.MaterialButton;
import java.util.*;
import java.util.function.Consumer;

/** Vista del editor: los datos y la validación pertenecen al controlador, sin escribir el perfil global. */
public final class ManualRoutineFragment extends ScreenFragment {
	private boolean contextExpanded;
	private ExerciseBrowser browser;
	private Bundle restored;
	public ManualRoutineFragment() {
		super(R.layout.fragment_manual_routine);
	}

	protected void content() {
		heading("", Txt.t(app.manual.editing() ? R.string.man_edit : R.string.man_title), Txt.t(app.manual.editing() ?
			R.string.man_edit_body : R.string.man_title_body));
		if (app.owner() == null) {
			action(card(Txt.t(R.string.man_account), Txt.t(R.string.man_account_body)), Txt.t(R.string.man_go_account),
				() -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		ManualRoutineController c = app.manual;
		ExerciseBrowser.restore(restored, "manual.library", state.manualExerciseBrowser, app.owner(), c.draftRevision());
		restored = null;
		LinearLayout basics = card(Txt.t(R.string.man_my_routine), Txt.t(R.string.man_my_routine_body));
		field(basics, "manual_title", Txt.t(R.string.man_name), c.title(), false, false, c::title);
		check(basics, "manual_progression", Txt.t(R.string.man_progression), c.progression(), c::progression);
		text(basics, Txt.t(c.progression() ? R.string.man_progression_on : R.string.man_progression_off),
			R.style.EnerGym_FieldCaption, 8);
		action(basics, Txt.t(contextExpanded ? R.string.man_hide_context : R.string.man_adjust_context), !c.busy, () -> {
			contextExpanded = !contextExpanded;
			render();
		});
		if (contextExpanded) contextFields();
		List<ManualRoutineController.Entry> entries = c.entries();
		LinearLayout chosen = card(Txt.t(R.string.man_chosen), Txt.t(R.string.man_chosen_body, entries.size()));
		for (int i = 0; i < entries.size(); i++) {
			final int index = i;
			action(chosen, (i + 1) + ". " + entries.get(i).exercise.getName() + (i == c.selected ? " · " + Txt.t(R.string.man_editing) : ""),
				!c.busy, () -> c.select(index));
		}
		if (entries.isEmpty()) text(chosen, Txt.t(R.string.man_add_first), R.style.EnerGym_Body, 8);
		else prescription(entries.get(c.selected), c.selected, entries.size());
		LinearLayout library = card(Txt.t(R.string.man_add_from_catalog), Txt.t(R.string.man_add_from_catalog_body));
		browser = new ExerciseBrowser(this, library, state.manualExerciseBrowser, "manual", true, new ExerciseBrowser.Results() {
			public List<Exercise> find(String query, ExerciseSearch.Filters filters, boolean onlyCompatible) {
				return c.search(query, filters, onlyCompatible);
			}
			public void row(LinearLayout target, Exercise exercise) {
				String reason = c.compatibilityMessage(exercise);
				text(target, exercise.getName(), R.style.EnerGym_Body, 16);
				text(target, ExerciseBrowser.metadata(exercise), R.style.EnerGym_FieldCaption, 4);
				LinearLayout actions = columns(target, 4);
				MaterialButton technique = compact(actions, Txt.t(R.string.ses_see_technique), !c.busy, () -> {
					browser.capture();
					app.openExercise(exercise);
				});
				technique.setContentDescription(Txt.t(R.string.tr_see_technique_of, exercise.getName()));
				MaterialButton add = compact(actions, Txt.t(R.string.man_add), !c.busy && reason.isEmpty(), () -> {
					browser.capture();
					c.add(exercise.getId());
				});
				add.setContentDescription(Txt.t(R.string.man_add_named, exercise.getName()));
				if (!reason.isEmpty()) text(target, reason, R.style.EnerGym_FieldCaption, 4);
			}
			public void empty(LinearLayout target) {
				if (state.manualExerciseBrowser.onlyCompatible)
					text(target, Txt.t(R.string.man_empty_results), R.style.EnerGym_FieldCaption, 8);
			}
		});
		// Guardar y volver, siempre al final del formulario.
		LinearLayout save = card(Txt.t(R.string.common_save), Txt.t(R.string.man_save_body));
		if (!c.error.isEmpty()) {
			TextView error = text(save, c.error, R.style.EnerGym_Body, 8);
			error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		}
		primary(action(save, Txt.t(c.busy ? R.string.man_saving : c.editing() ? R.string.food_save_changes : R.string.man_save_mine),
			!c.busy && !app.loading, c::save));
		action(save, Txt.t(R.string.man_back_to_training), !c.busy, () -> app.navigate(AppController.Screen.TRAINING));
	}

	private void contextFields() {
		ManualRoutineController c = app.manual;
		LinearLayout box = card(Txt.t(R.string.man_context), Txt.t(R.string.man_context_body));
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		for (Sport sport : app.catalog.getSports()) {
			ids.add(sport.getId());
			names.add(sport.getName());
		}
		choose(box, "sportId", Txt.t(R.string.man_discipline), ids, names);
		enumChoice(box, "goal", Txt.t(R.string.man_goal), Goal.values());
		enumChoice(box, "environment", Txt.t(R.string.man_environment), Environment.values());
		enumChoice(box, "focus", Txt.t(R.string.man_focus), Focus.values());
		listChoice(box, "level", Txt.t(R.string.man_level), AppController.LEVELS, AppController.levelLabels(), "");
		field(box, "manual_duration", Txt.t(R.string.man_time_available), AppController.hours(c.contextValue("durationMinutes")),
			false, false, v -> c.contextInput("durationMinutes", AppController.minutes(v)));
		timeInput(box, "manual_duration");
		for (Resource resource : Resource.values()) {
			String key = "resource." + resource.name();
			check(box, "manual_" + key, AppController.label(resource.name()), Boolean.parseBoolean(c.contextValue(key)),
				v -> c.contextInput(key, String.valueOf(v)));
		}
	}

	private void prescription(ManualRoutineController.Entry entry, int index, int size) {
		ManualRoutineController c = app.manual;
		LinearLayout box = card(Txt.t(R.string.man_edit_named, entry.exercise.getName()), Txt.t(R.string.man_edit_named_body));
		action(box, Txt.t(R.string.man_technique_animation), !c.busy, () -> app.openExercise(entry.exercise));
		String incompatible = c.contextCompatibility(entry.exercise);
		if (!incompatible.isEmpty()) text(box, incompatible, R.style.EnerGym_Body, 8);
		for (MetricValidator.Field f : c.fields(entry.exercise)) {
			String label = AppController.plannedLabel(f);
			String value = entry.values.getOrDefault(f.key, "");
			if (f.key.equals("loadKg") && !c.externalLoad(entry.exercise)) {
				text(box, Txt.t(R.string.man_bodyweight_load), R.style.EnerGym_FieldCaption, 12);
			} else if (!f.choices.isEmpty()) {
				choice(box, "manual_" + f.key, label, f.choices, choiceLabels(f.choices), value, v -> c.input(index, f.key, v));
				box.findViewWithTag("manual_" + f.key).setEnabled(!c.busy);
			} else field(box, "manual_" + f.key, label, value, true, f.integer, v -> c.input(index, f.key, v));
		}
		field(box, "manual_estimatedMinutes", Txt.t(R.string.man_reserved_minutes), entry.minutes, true, true,
			v -> c.input(index, "estimatedMinutes", v));
		LinearLayout order = columns(box, 8);
		compact(order, Txt.t(R.string.man_up), !c.busy && index > 0, () -> c.move(index, -1));
		compact(order, Txt.t(R.string.man_down), !c.busy && index < size - 1, () -> c.move(index, 1));
		action(box, Txt.t(R.string.man_remove_exercise), !c.busy, () -> c.remove(index));
	}

	@Override
	protected void beforeContentReset() {
		if (browser != null) browser.capture();
	}

	@Override public void onCreate(Bundle saved) {
		super.onCreate(saved);
		restored = saved;
		if (saved != null) contextExpanded = saved.getBoolean("manual.contextExpanded", false);
	}
	@Override public void onSaveInstanceState(Bundle saved) {
		super.onSaveInstanceState(saved);
		if (browser != null) browser.capture();
		saved.putBoolean("manual.contextExpanded", contextExpanded);
		if (state != null && app != null) ExerciseBrowser.save(saved, "manual.library", state.manualExerciseBrowser, app.owner(), app.manual.draftRevision());
	}
	@Override public void onDestroyView() {
		browser = null;
		super.onDestroyView();
	}

	private void field(LinearLayout box, String key, String label, String value, boolean number, boolean integer, Consumer<String> event) {
		textField(box, key, label, value, number, integer, event);
		box.findViewWithTag(key).setEnabled(!app.manual.busy);
	}
	private void check(LinearLayout box, String key, String label, boolean selected, Consumer<Boolean> event) {
		MaterialCheckBox check = new MaterialCheckBox(requireContext());
		check.setText(label);
		check.setTag(key);
		check.setChecked(selected);
		check.setEnabled(!app.manual.busy);
		check.setMinHeight(dp(48));
		check.setOnCheckedChangeListener((button, enabled) -> event.accept(enabled));
		add(box, check, 8);
	}
	private void enumChoice(LinearLayout box, String key, String label, Enum<?>[] options) {
		List<String> values = new ArrayList<>(), labels = new ArrayList<>();
		for (Enum<?> option : options) {
			values.add(option.name());
			labels.add(AppController.label(option.name()));
		}
		choose(box, key, label, values, labels);
	}
	/** Lista cerrada que conserva un valor antiguo fuera de ella para no perderlo. */
	private void listChoice(LinearLayout box, String key, String label, List<String> values, List<String> labels,
		String unit) {
		String current = app.manual.contextValue(key);
		List<String> v = new ArrayList<>(values), t = new ArrayList<>(labels);
		if (!values.contains(current)) {
			v.add(0, current);
			t.add(0, current.isEmpty() ? Txt.t(R.string.ses_rpe_none) : current + unit);
		}
		choose(box, key, label, v, t);
	}
	private void choose(LinearLayout box, String key, String label, List<String> values, List<String> labels) {
		choice(box, "manual_" + key, label, values, labels, app.manual.contextValue(key), v -> app.manual.contextInput(key, v));
		box.findViewWithTag("manual_" + key).setEnabled(!app.manual.busy);
	}


	private static List<String> choiceLabels(List<String> values) {
		List<String> labels = new ArrayList<>();
		for (String v : values)
			labels.add(MetricValidator.choiceLabel(v));
		return labels;
	}
}
