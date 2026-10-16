package com.energym.app.ui;

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
		heading("", app.manual.editing() ? "Editar rutina" : "Rutina manual", app.manual.editing() ?
			"Los cambios se guardarán como una nueva versión. Tus sesiones anteriores se conservan." :
			"Elige ejercicios del catálogo y ajusta su prescripción. Sólo se guarda al confirmarlo.");
		if (app.owner() == null) {
			action(card("Tu cuenta", "Selecciona una cuenta antes de guardar una rutina."), "Ir a Cuenta",
				() -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		ManualRoutineController c = app.manual;
		ExerciseBrowser.restore(restored, "manual.library", state.manualExerciseBrowser, app.owner(), c.draftRevision());
		restored = null;
		LinearLayout basics = card("Mi rutina", "Estos ajustes pertenecen a esta rutina. Tu perfil general se conserva.");
		field(basics, "manual_title", "Nombre de la rutina", c.title(), false, false, c::title);
		check(basics, "manual_progression", "Progresión automática", c.progression(), c::progression);
		text(basics, c.progression() ? "Activada: podrás revisar y aceptar propuestas de progresión." :
			"Desactivada: se conservan tus series, repeticiones, tiempos y cargas.", R.style.EnerGym_FieldCaption, 8);
		action(basics, contextExpanded ? "Ocultar contexto" : "Ajustar contexto de esta rutina", !c.busy, () -> {
			contextExpanded = !contextExpanded;
			render();
		});
		if (contextExpanded) contextFields();
		List<ManualRoutineController.Entry> entries = c.entries();
		LinearLayout chosen = card("Ejercicios elegidos", entries.size() + " ejercicios · selecciona uno para editarlo.");
		for (int i = 0; i < entries.size(); i++) {
			final int index = i;
			action(chosen, (i + 1) + ". " + entries.get(i).exercise.getName() + (i == c.selected ? " · editando" : ""),
				!c.busy, () -> c.select(index));
		}
		if (entries.isEmpty()) text(chosen, "Añade tu primer ejercicio desde el catálogo.", R.style.EnerGym_Body, 8);
		else prescription(entries.get(c.selected), c.selected, entries.size());
		LinearLayout save = card("Guardar", "Se guardará en tu cuenta con el contexto propio de esta rutina.");
		if (!c.error.isEmpty()) {
			TextView error = text(save, c.error, R.style.EnerGym_Body, 8);
			error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
		}
		primary(action(save, c.busy ? "Guardando…" : c.editing() ? "Guardar cambios" : "Guardar mi rutina", !c.busy && !app.loading, c::save));
		action(save, "Volver a entrenamientos", !c.busy, () -> app.navigate(AppController.Screen.TRAINING));
		LinearLayout library = card("Añadir del catálogo", "Empieza por los ejercicios compatibles. Desmarca la opción para explorar todos y consultar su técnica.");
		browser = new ExerciseBrowser(this, library, state.manualExerciseBrowser, "manual", true, new ExerciseBrowser.Results() {
			public List<Exercise> find(String query, ExerciseSearch.Filters filters, boolean onlyCompatible) {
				return c.search(query, filters, onlyCompatible);
			}
			public void row(LinearLayout target, Exercise exercise) {
				String reason = c.compatibilityMessage(exercise);
				text(target, exercise.getName(), R.style.EnerGym_Body, 16);
				text(target, ExerciseBrowser.metadata(exercise), R.style.EnerGym_FieldCaption, 4);
				LinearLayout actions = columns(target, 4);
				MaterialButton technique = compact(actions, "Ver técnica", !c.busy, () -> {
					browser.capture();
					app.openExercise(exercise);
				});
				technique.setContentDescription("Ver técnica de " + exercise.getName());
				MaterialButton add = compact(actions, "Añadir", !c.busy && reason.isEmpty(), () -> {
					browser.capture();
					c.add(exercise.getId());
				});
				add.setContentDescription("Añadir " + exercise.getName());
				if (!reason.isEmpty()) text(target, reason, R.style.EnerGym_FieldCaption, 4);
			}
			public void empty(LinearLayout target) {
				if (state.manualExerciseBrowser.onlyCompatible)
					text(target, "Puedes ajustar el contexto de esta rutina o desmarcar «Sólo compatibles» para explorar todos.", R.style.EnerGym_FieldCaption, 8);
			}
		});

	}

	private void contextFields() {
		ManualRoutineController c = app.manual;
		LinearLayout box = card("Contexto de esta rutina", "Cambiarlo conserva los ejercicios; deberás resolver los incompatibles antes de guardar.");
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		for (Sport sport : app.catalog.getSports()) {
			ids.add(sport.getId());
			names.add(sport.getName());
		}
		choose(box, "sportId", "Disciplina", ids, names);
		enumChoice(box, "goal", "Objetivo", Goal.values());
		enumChoice(box, "environment", "Entorno", Environment.values());
		enumChoice(box, "focus", "Enfoque", Focus.values());
		listChoice(box, "level", "Nivel", AppController.LEVELS, AppController.LEVELS, "");
		listChoice(box, "durationMinutes", "Tiempo disponible", AppController.MINUTES, AppController.MINUTE_LABELS, " min");
		for (Resource resource : Resource.values()) {
			String key = "resource." + resource.name();
			check(box, "manual_" + key, AppController.label(resource.name()), Boolean.parseBoolean(c.contextValue(key)),
				v -> c.contextInput(key, String.valueOf(v)));
		}
	}

	private void prescription(ManualRoutineController.Entry entry, int index, int size) {
		ManualRoutineController c = app.manual;
		LinearLayout box = card("Editar: " + entry.exercise.getName(), "Prescripción prevista, no resultados de una sesión.");
		action(box, "Ver técnica y animación", !c.busy, () -> app.openExercise(entry.exercise));
		String incompatible = c.contextCompatibility(entry.exercise);
		if (!incompatible.isEmpty()) text(box, incompatible, R.style.EnerGym_Body, 8);
		for (MetricValidator.Field f : c.fields(entry.exercise)) {
			String label = f.label.replace("realizadas", "previstas").replace("realizados", "previstos")
				.replace("realizado", "previsto").replace("trabajado", "previsto");
			String value = entry.values.getOrDefault(f.key, "");
			if (f.key.equals("loadKg") && !c.externalLoad(entry.exercise)) {
				text(box, "Carga: 0 kg · peso corporal", R.style.EnerGym_FieldCaption, 12);
			} else if (!f.choices.isEmpty()) {
				choice(box, "manual_" + f.key, label, f.choices, f.choices, value, v -> c.input(index, f.key, v));
				box.findViewWithTag("manual_" + f.key).setEnabled(!c.busy);
			} else field(box, "manual_" + f.key, label, value, true, f.integer, v -> c.input(index, f.key, v));
		}
		field(box, "manual_estimatedMinutes", "Tiempo reservado para este ejercicio (min)", entry.minutes, true, true,
			v -> c.input(index, "estimatedMinutes", v));
		LinearLayout order = columns(box, 8);
		compact(order, "Subir", !c.busy && index > 0, () -> c.move(index, -1));
		compact(order, "Bajar", !c.busy && index < size - 1, () -> c.move(index, 1));
		action(box, "Quitar ejercicio", !c.busy, () -> c.remove(index));
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
			t.add(0, current.isEmpty() ? "Sin indicar" : current + unit);
		}
		choose(box, key, label, v, t);
	}
	private void choose(LinearLayout box, String key, String label, List<String> values, List<String> labels) {
		choice(box, "manual_" + key, label, values, labels, app.manual.contextValue(key), v -> app.manual.contextInput(key, v));
		box.findViewWithTag("manual_" + key).setEnabled(!app.manual.busy);
	}
}
