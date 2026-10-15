package com.energym.app.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import com.energym.app.R;
import com.energym.app.catalog.ExerciseSearch;
import com.energym.app.controller.AppController;
import com.energym.app.domain.Exercise;
import com.energym.app.domain.Sport;
import com.energym.app.domain.TrainingProfile.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import java.util.*;
import java.util.function.Consumer;

/** Controles de exploración compartidos: mantiene el campo estable al filtrar y paginar. */
final class ExerciseBrowser {
	interface Results {
		List<Exercise> find(String query, ExerciseSearch.Filters filters, boolean onlyCompatible);
		void row(LinearLayout target, Exercise exercise);
		default void empty(LinearLayout target) {}
	}
	private final ScreenFragment host;
	private final ExerciseBrowserState state;
	private final long bindingRevision;
	private final String prefix;
	private final Results source;
	private final LinearLayout results, panel;
	private final ChipGroup active;
	private final MaterialButton toggle, clear;
	private final Map<String, Spinner> controls = new LinkedHashMap<>();
	private final Map<String, List<String>> values = new LinkedHashMap<>();
	private final Map<String, List<String>> labels = new LinkedHashMap<>();
	private EditText input;
	private boolean changing;

	ExerciseBrowser(ScreenFragment host, LinearLayout target, ExerciseBrowserState state, String prefix,
		boolean manual, Results source) {
		this.host = host;
		this.state = state;
		bindingRevision = state.bindingRevision();
		this.prefix = prefix;
		this.source = source;
		results = vertical();
		panel = vertical();
		host.textField(target, prefix + "_search", "Buscar ejercicio", state.query, false, false, text -> {
			state.query = text;
			state.visible = 12;
			if (!changing) refresh();
		});
		input = target.findViewWithTag(prefix + "_search");
		input.setHint("Ej.: sentadilla, flexión, mancuernas");
		input.setSingleLine(true);
		input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		TextInputLayout field = (TextInputLayout) input.getParent().getParent();
		field.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
		field.setEndIconContentDescription("Borrar búsqueda");
		active = new ChipGroup(host.requireContext());
		active.setSingleLine(false);
		active.setTag(prefix + "_active_filters");
		host.add(target, active, 0);
		toggle = host.action(target, "Filtrar ejercicios", true, () -> {
			state.expanded = !state.expanded;
			updateToggle();
		});
		toggle.setTag(prefix + "_filters_toggle");
		List<String> sports = new ArrayList<>(Collections.singletonList(""));
		List<String> sportNames = new ArrayList<>(Collections.singletonList("Todas las disciplinas"));
		for (Sport sport : host.app.catalog.getSports()) {
			sports.add(sport.getId());
			sportNames.add(sport.getName());
		}
		choice("sport", "Disciplina", sports, sportNames, state.discipline, value -> state.discipline = value);
		enums("environment", "Entorno", "Cualquier entorno", Environment.values(), state.environment, value -> state.environment = value);
		List<String> materials = new ArrayList<>(Arrays.asList("", "NONE"));
		List<String> materialNames = new ArrayList<>(Arrays.asList("Cualquier material", "Sin material"));
		for (Resource resource : Resource.values()) {
			materials.add(resource.name());
			materialNames.add(AppController.label(resource.name()));
		}
		choice("material", "Material que requiere", materials, materialNames, state.material, value -> state.material = value);
		enums("goal", "Objetivo", "Cualquier objetivo", Goal.values(), state.goal, value -> state.goal = value);
		host.text(panel, manual ? "Estos filtros sólo cambian lo que ves. El contexto de tu rutina se conserva." :
			"Filtra la biblioteca sin cambiar tu perfil ni tus rutinas.", R.style.EnerGym_FieldCaption, 8);
		host.action(panel, "Quitar filtros", true, () -> {
			state.clearFilters();
			syncControls();
			refresh();
		});
		host.add(target, panel, 0);
		if (manual) {
			MaterialCheckBox compatible = new MaterialCheckBox(host.requireContext());
			compatible.setTag("manual_only_compatible");
			compatible.setText("Sólo compatibles con esta rutina");
			compatible.setMinHeight(host.dp(48));
			compatible.setChecked(state.onlyCompatible);
			compatible.setOnCheckedChangeListener((button, checked) -> {
				state.onlyCompatible = checked;
				state.visible = 12;
				refresh();
			});
			host.add(target, compatible, 8);
		}
		clear = host.action(target, "Limpiar búsqueda y filtros", true, () -> {
			state.clearFilters();
			state.query = "";
			changing = true;
			input.setText("");
			changing = false;
			syncControls();
			refresh();
		});
		clear.setTag(prefix + "_reset");
		host.add(target, results, 0);
		updateToggle();
		refresh();
		boolean restoreFocus = state.focused;
		state.focused = false;
		if (restoreFocus) {
			int selection = Math.min(state.selection, input.length());
			input.requestFocus();
			input.setSelection(selection);
			restoreSelectionAfterResume(selection, input.getText().toString());
		}
	}

	private void restoreSelectionAfterResume(int selection, String text) {
		Lifecycle lifecycle = host.getViewLifecycleOwner().getLifecycle();
		lifecycle.addObserver(new LifecycleEventObserver() {
			@Override
			public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
				if (event == Lifecycle.Event.ON_DESTROY) {
					lifecycle.removeObserver(this);
					return;
				}
				if (event != Lifecycle.Event.ON_RESUME) return;
				lifecycle.removeObserver(this);
				// FragmentManager restaura el foco al reanudar y puede mover el cursor al final.
				input.post(() -> {
					View view = host.getView();
					if (bindingRevision != state.bindingRevision() || view == null ||
						view.findViewWithTag(prefix + "_search") != input || !input.isAttachedToWindow() ||
						!input.hasFocus() || state.selection != selection || !text.contentEquals(input.getText())) return;
					input.setSelection(selection);
				});
			}
		});
	}

	void capture() {
		state.capture(bindingRevision, input.hasFocus(), input.getSelectionStart());
	}

	private LinearLayout vertical() {
		LinearLayout result = new LinearLayout(host.requireContext());
		result.setOrientation(LinearLayout.VERTICAL);
		return result;
	}

	private void enums(String key, String title, String all, Enum<?>[] options, String selected, Consumer<String> changed) {
		List<String> ids = new ArrayList<>(Collections.singletonList("")), names = new ArrayList<>(Collections.singletonList(all));
		for (Enum<?> option : options) {
			ids.add(option.name());
			names.add(AppController.label(option.name()));
		}
		choice(key, title, ids, names, selected, changed);
	}

	private void choice(String key, String title, List<String> ids, List<String> names, String selected, Consumer<String> changed) {
		values.put(key, ids);
		labels.put(key, names);
		host.choice(panel, prefix + "_filter_" + key, title, ids, names, selected, value -> {
			if (Objects.equals(value, selected(key))) return;
			changed.accept(value);
			state.visible = 12;
			if (!changing) refresh();
		});
		controls.put(key, panel.findViewWithTag(prefix + "_filter_" + key));
	}

	private String selected(String key) {
		switch (key) {
			case "sport": return state.discipline;
			case "environment": return state.environment;
			case "material": return state.material;
			default: return state.goal;
		}
	}

	private void remove(String key) {
		switch (key) {
			case "sport":
				state.discipline = "";
				break;
			case "environment":
				state.environment = "";
				break;
			case "material":
				state.material = "";
				break;
			default:
				state.goal = "";
		}
		state.visible = 12;
		syncControls();
		refresh();
	}

	private void syncControls() {
		changing = true;
		for (String key : controls.keySet()) controls.get(key).setSelection(Math.max(0, values.get(key).indexOf(selected(key))));
		changing = false;
	}

	private void updateToggle() {
		panel.setVisibility(state.expanded ? View.VISIBLE : View.GONE);
		toggle.setText(state.expanded ? "Ocultar filtros" : "Filtrar ejercicios");
		toggle.setContentDescription((state.expanded ? "Ocultar filtros" : "Filtrar ejercicios") +
			(state.activeCount() == 0 ? "" : ", " + state.activeCount() + " activos"));
	}

	private void refresh() {
		clear.setVisibility(state.query.isEmpty() && state.activeCount() == 0 ? View.GONE : View.VISIBLE);
		active.removeAllViews();
		for (String key : controls.keySet()) {
			int index = values.get(key).indexOf(selected(key));
			if (index <= 0) continue;
			Chip chip = new Chip(host.requireContext());
			chip.setText(labels.get(key).get(index));
			chip.setCloseIconVisible(true);
			chip.setCheckable(false);
			chip.setEnsureMinTouchTargetSize(true);
			chip.setCloseIconContentDescription("Quitar filtro " + labels.get(key).get(index));
			chip.setOnCloseIconClickListener(view -> remove(key));
			active.addView(chip);
		}
		active.setVisibility(state.activeCount() == 0 ? View.GONE : View.VISIBLE);
		updateToggle();
		results.removeAllViews();
		List<Exercise> found = source.find(state.query, state.filters(), state.onlyCompatible);
		int shown = Math.min(state.visible, found.size());
		TextView count = host.text(results, found.size() + (found.size() == 1 ? " resultado" : " resultados") +
			" · " + shown + " visibles" + (state.activeCount() == 0 ? "" : " · " + state.activeCount() + " filtros activos"),
			R.style.EnerGym_FieldCaption, 12);
		count.setTag(prefix + "_result_count");
		count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		for (int index = 0; index < shown; index++) source.row(results, found.get(index));
		if (shown < found.size()) host.action(results, "Mostrar más ejercicios", true, () -> {
			state.visible += 12;
			refresh();
		});
		if (found.isEmpty()) {
			host.text(results, "No hay ejercicios con esta búsqueda y estos filtros. Prueba otro nombre o quita algún filtro.", R.style.EnerGym_Body, 12);
			if (state.activeCount() > 0) host.action(results, "Quitar filtros", true, () -> {
				state.clearFilters();
				syncControls();
				refresh();
			});
			source.empty(results);
		}
	}

	static String metadata(Exercise exercise) {
		List<String> parts = new ArrayList<>();
		for (Environment environment : exercise.getEnvironments()) parts.add(AppController.label(environment.name()));
		String environment = String.join(" / ", parts);
		parts.clear();
		for (Resource resource : exercise.getRequiredResources()) parts.add(AppController.label(resource.name()));
		return environment + " · " + (parts.isEmpty() ? "Sin material" : String.join(", ", parts));
	}

	static void save(Bundle bundle, String key, ExerciseBrowserState state, String owner, long revision) {
		bundle.putString(key + ".owner", owner);
		bundle.putLong(key + ".revision", revision);
		bundle.putString(key + ".query", state.query);
		bundle.putString(key + ".discipline", state.discipline);
		bundle.putString(key + ".environment", state.environment);
		bundle.putString(key + ".material", state.material);
		bundle.putString(key + ".goal", state.goal);
		bundle.putBoolean(key + ".expanded", state.expanded);
		bundle.putBoolean(key + ".onlyCompatible", state.onlyCompatible);
		bundle.putInt(key + ".visible", state.visible);
		bundle.putBoolean(key + ".focused", state.focused);
		bundle.putInt(key + ".selection", state.selection);
	}

	static void restore(Bundle bundle, String key, ExerciseBrowserState state, String owner, long revision) {
		if (bundle == null || !Objects.equals(owner, bundle.getString(key + ".owner")) ||
			bundle.getLong(key + ".revision", -1) != revision) return;
		state.query = bundle.getString(key + ".query", "");
		state.discipline = bundle.getString(key + ".discipline", "");
		state.environment = bundle.getString(key + ".environment", "");
		state.material = bundle.getString(key + ".material", "");
		state.goal = bundle.getString(key + ".goal", "");
		state.expanded = bundle.getBoolean(key + ".expanded", false);
		state.onlyCompatible = bundle.getBoolean(key + ".onlyCompatible", true);
		state.visible = Math.max(12, bundle.getInt(key + ".visible", 12));
		state.focused = bundle.getBoolean(key + ".focused", false);
		state.selection = bundle.getInt(key + ".selection", 0);
	}
}
