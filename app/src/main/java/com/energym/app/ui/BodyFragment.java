package com.energym.app.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.nutrition.*;
import com.google.android.material.button.MaterialButton;

import java.util.*;

/**
 * VISTA del perfil físico: datos para estimar mantenimiento y objetivos diarios. Validación en el controlador.
 */
public final class BodyFragment extends ScreenFragment {
	public BodyFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		NutritionController n = state.nutrition;
		heading("NUTRICIÓN", "Tus datos físicos",
			"Para calcular tus calorías y macros diarios.");
		if (n == null || app.owner() == null) {
			card("Entra para empezar", "");
			return;
		}
		if (n.bodyDraft.isEmpty())
			n.fillBody();
		Map<String, String> d = n.bodyDraft;
		Runnable[] refresh = {() -> {}};
		LinearLayout basics = card("Sobre ti", "");
		choice(basics, "sex", "Sexo biológico (para la fórmula)", Arrays.asList("FEMALE", "MALE"),
			Arrays.asList("Mujer", "Hombre"), d.get("sex"), v -> { d.put("sex", v); refresh[0].run(); });
		textField(basics, "age", "Edad (años)", d.get("age"), true, true,
			v -> { d.put("age", v); refresh[0].run(); });
		textField(basics, "heightCm", "Altura (cm)", d.get("heightCm"), true, false,
			v -> { d.put("heightCm", v); refresh[0].run(); });
		textField(basics, "weightKg", "Peso actual (kg)", d.get("weightKg"), true, false,
			v -> { d.put("weightKg", v); refresh[0].run(); });

		LinearLayout plan = card("Tu actividad", "Incluye tu movimiento habitual y tus entrenamientos.");
		choice(plan, "activity", "Actividad habitual",
			Arrays.asList("SEDENTARY", "LIGHT", "MODERATE", "HIGH", "VERY_HIGH"),
			Arrays.asList("Sedentaria (poco movimiento)", "Ligera (1–3 entrenos/semana)",
				"Moderada (3–5 entrenos/semana)", "Alta (6–7 entrenos/semana)", "Muy alta (trabajo físico + entreno)"),
			d.get("activity"), v -> { d.put("activity", v); refresh[0].run(); });
		LinearLayout goals = card("¿Qué quieres conseguir?", "Elige una opción. Los cambios se aplican al guardar.");
		for (BodyProfile.Goal goal : BodyProfile.Goal.values()) {
			boolean selected = goal.name().equals(d.get("goal"));
			String description = goal == BodyProfile.Goal.LOSE ? "Perder grasa" :
				goal == BodyProfile.Goal.GAIN ? "Ganar masa muscular" : "Mantener tu peso";
			MaterialButton button = action(goals, goal.label + "\n" + description, !n.busy,
				() -> n.selectNutritionGoal(goal));
			button.setTag("nutrition_goal_" + goal.name().toLowerCase(Locale.ROOT));
			button.setMaxLines(3);
			button.setCheckable(true);
			button.setChecked(selected);
			button.setContentDescription(goal.label + (selected ? ", seleccionado" : ""));
			if (selected) primary(button);
		}
		textField(plan, "allergies", "Alergias o intolerancias (opcional)", d.get("allergies"), false, false,
			v -> d.put("allergies", v));

		LinearLayout preview = card("Tu estimación diaria", "Revisa las calorías y macronutrientes antes de guardar.");
		TextView kcal = text(preview, "", R.style.EnerGym_CardTitle, 4);
		kcal.setTag("nutrition_goal_preview_kcal");
		TextView macros = text(preview, "", R.style.EnerGym_Body, 8);
		macros.setTag("nutrition_goal_preview_macros");
		kcal.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
		refresh[0] = () -> {
			BodyProfile.Targets targets = n.previewTargets();
			kcal.setText(targets == null ? "Estimación pendiente" : Math.round(targets.kcal) + " kcal/día");
			macros.setText(targets == null ? n.bodyPreviewIssue() :
				"Proteínas: " + Math.round(targets.protein) + " g\nHidratos: " + Math.round(targets.carbs) +
					" g\nGrasas: " + Math.round(targets.fat) + " g");
		};
		refresh[0].run();
		card("Cómo se calcula",
			"Metabolismo basal con Mifflin-St Jeor × factor de actividad, ajustado a tu objetivo. Proteína 1,6–2 g/kg, " +
				"grasa mínima 0,8 g/kg y el resto en hidratos. Déficit orientativo del 15 % o superávit del 10 %. " +
				"Estimación para adultos; no se aplica durante embarazo o lactancia y no sustituye una valoración profesional.");
		primary(action(rows, "Guardar objetivo y datos", !n.busy,
			() -> n.saveBody(() -> app.navigate(Screen.NUTRITION))));
		action(rows, "Cancelar", !n.busy, () -> {
			n.cancelBody();
			app.navigate(Screen.NUTRITION);
		});
	}
}
