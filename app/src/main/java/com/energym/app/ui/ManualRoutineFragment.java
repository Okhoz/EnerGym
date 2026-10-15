package com.energym.app.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.controller.ManualRoutineController;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.util.*;
import java.util.function.Consumer;

/** Vista del editor: los datos y la validación pertenecen al controlador, sin escribir el perfil global. */
public final class ManualRoutineFragment extends ScreenFragment {
	private static final int PAGE_SIZE = 12;
	private int visible = PAGE_SIZE;
	private boolean contextExpanded;
	public ManualRoutineFragment() { super(R.layout.fragment_manual_routine); }

	protected void content() {
		heading("", "Rutina manual", "Elige ejercicios del catálogo y ajusta su prescripción. Sólo se guarda al confirmarlo.");
		if (app.owner() == null) {
			action(card("Tu cuenta", "Selecciona una cuenta antes de guardar una rutina."), "Ir a Cuenta",
				() -> app.navigate(AppController.Screen.ACCOUNT));
			return;
		}
		ManualRoutineController c = app.manual;
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
		primary(action(save, c.busy ? "Guardando…" : "Guardar mi rutina", !c.busy && !app.loading, c::save));
		action(save, "Volver a entrenamientos", !c.busy, () -> app.navigate(AppController.Screen.TRAINING));
		LinearLayout library = card("Añadir del catálogo", "La biblioteca es explorable. Cada ejercicio indica si admite el contexto de esta rutina.");
		LinearLayout results = new LinearLayout(requireContext());
		results.setOrientation(LinearLayout.VERTICAL);
		field(library, "manual_search", "Buscar ejercicio", c.query, false, false, query -> {
			c.query = query;
			visible = PAGE_SIZE;
			catalog(results);
		});
		add(library, results, 0);
		catalog(results);

	}

	private void contextFields() {
		ManualRoutineController c = app.manual;
		LinearLayout box = card("Contexto de esta rutina", "Cambiarlo conserva los ejercicios; deberás resolver los incompatibles antes de guardar.");
		List<String> ids = new ArrayList<>(), names = new ArrayList<>();
		for (Sport sport : app.catalog.getSports()) { ids.add(sport.getId()); names.add(sport.getName()); }
		choose(box, "sportId", "Disciplina", ids, names);
		enumChoice(box, "goal", "Objetivo", Goal.values());
		enumChoice(box, "environment", "Entorno", Environment.values());
		enumChoice(box, "focus", "Enfoque", Focus.values());
		field(box, "manual_level", "Nivel", c.contextValue("level"), false, false, v -> c.contextInput("level", v));
		field(box, "manual_duration", "Tiempo disponible (min)", c.contextValue("durationMinutes"), true, true,
			v -> c.contextInput("durationMinutes", v));
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

	private void catalog(LinearLayout results) {
		results.removeAllViews();
		ManualRoutineController c = app.manual;
		List<Exercise> found = c.search(c.query);
		int shown = Math.min(visible, found.size());
		text(results, found.size() + " resultados · " + shown + " visibles", R.style.EnerGym_FieldCaption, 12);
		for (int i = 0; i < shown; i++) {
			Exercise e = found.get(i);
			String reason = c.compatibilityMessage(e);
			action(results, "Añadir " + e.getName(), !c.busy && reason.isEmpty(), () -> c.add(e.getId()));
			if (!reason.isEmpty()) text(results, e.getName() + ": " + reason, R.style.EnerGym_FieldCaption, 4);
		}
		if (shown < found.size()) action(results, "Mostrar más ejercicios", !c.busy, () -> { visible += PAGE_SIZE; catalog(results); });
		if (found.isEmpty()) text(results, "No hay ejercicios con esta búsqueda. Prueba otro nombre.", R.style.EnerGym_Body, 12);
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
		for (Enum<?> option : options) { values.add(option.name()); labels.add(AppController.label(option.name())); }
		choose(box, key, label, values, labels);
	}
	private void choose(LinearLayout box, String key, String label, List<String> values, List<String> labels) {
		choice(box, "manual_" + key, label, values, labels, app.manual.contextValue(key), v -> app.manual.contextInput(key, v));
		box.findViewWithTag("manual_" + key).setEnabled(!app.manual.busy);
	}
}
