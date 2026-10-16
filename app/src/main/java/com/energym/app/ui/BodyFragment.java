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
			"Completa tus datos para estimar calorías y macros. Al guardar confirmas tu objetivo; después puedes pedir a la IA ideas de comidas.");
		if (n == null || app.owner() == null) {
			card("Entra para empezar", "");
			return;
		}
		WeightHistorySection.render(this, n);
		if (n.bodyDraft.isEmpty()) {
			n.fillBody();
			if (app.profile != null)
				n.suggestActivity(app.profile.getDaysPerWeek());
		}
		Map<String, String> d = n.bodyDraft;
		Runnable[] refresh = {() -> {}};
		LinearLayout basics = card("Sobre ti", "Escribe solo las cifras; las unidades aparecen en cada etiqueta.");
		choice(basics, "sex", "Sexo biológico (para la fórmula)", Arrays.asList("FEMALE", "MALE"),
			Arrays.asList("Mujer", "Hombre"), d.get("sex"), "La fórmula usa este dato para estimar tu gasto en reposo.",
			v -> { d.put("sex", v); refresh[0].run(); });
		textField(basics, "age", "Edad (años)", d.get("age"), true, true,
			"Tu edad actual ayuda a estimar el gasto de energía en reposo.",
			v -> { d.put("age", v); refresh[0].run(); });
		textField(basics, "heightCm", "Altura (cm)", d.get("heightCm"), true, false,
			"Se usa junto a tu edad y peso para estimar el gasto diario.",
			v -> { d.put("heightCm", v); refresh[0].run(); });
		textField(basics, "weightKg", "Peso actual (kg)", d.get("weightKg"), true, false,
			"Es la referencia para estimar calorías, proteínas y grasas.",
			v -> { d.put("weightKg", v); refresh[0].run(); });

		LinearLayout plan = card("Tu actividad", "Incluye tu movimiento habitual y tus entrenamientos.");
		choice(plan, "activity", "Actividad habitual",
			Arrays.asList("SEDENTARY", "LIGHT", "MODERATE", "HIGH", "VERY_HIGH"),
			Arrays.asList("Sedentaria (poco movimiento)", "Ligera (1–3 entrenos/semana)",
				"Moderada (3–5 entrenos/semana)", "Alta (6–7 entrenos/semana)", "Muy alta (trabajo físico + entreno)"),
			d.get("activity"), n.body == null && app.profile != null ?
				"Propuesta según los " + app.profile.getDaysPerWeek() + " días de entreno de tu perfil; cámbiala si no encaja." :
				"Elige la opción que mejor refleje una semana habitual, incluido el entrenamiento.",
			v -> { d.put("activity", v); refresh[0].run(); });
		textField(plan, "allergies", "Alergias o intolerancias (opcional)", d.get("allergies"), false, false,
			"Anota los alimentos que necesitas evitar. Puedes dejarlo vacío.", v -> d.put("allergies", v));
		LinearLayout diet = card("Para tu dieta con IA", "Con esto el entrenador prepara menús a tu medida.");
		List<String> diets = new ArrayList<>(), dietLabels = new ArrayList<>();
		for (BodyProfile.Diet value : BodyProfile.Diet.values()) {
			diets.add(value.name());
			dietLabels.add(value.label);
		}
		choice(diet, "diet", "Tipo de alimentación", diets, dietLabels, d.getOrDefault("diet", "OMNIVORE"),
			"Los menús solo usarán alimentos compatibles.", v -> d.put("diet", v));
		choice(diet, "mealsPerDay", "Comidas al día", Arrays.asList("2", "3", "4", "5", "6"),
			Arrays.asList("2 comidas", "3 comidas", "4 comidas", "5 comidas", "6 comidas"),
			d.getOrDefault("mealsPerDay", "4"), "Reparte tus calorías en este número de tomas.", v -> d.put("mealsPerDay", v));
		List<String> cooking = new ArrayList<>(), cookingLabels = new ArrayList<>();
		for (BodyProfile.Cooking value : BodyProfile.Cooking.values()) {
			cooking.add(value.name());
			cookingLabels.add(value.label);
		}
		choice(diet, "cooking", "Tiempo para cocinar", cooking, cookingLabels, d.getOrDefault("cooking", "NORMAL"),
			"", v -> d.put("cooking", v));
		textField(diet, "budget", "Presupuesto mensual para comida en € (opcional)", d.getOrDefault("budget", ""), true,
			false, "Lo que gastas al mes en comida; la IA lo reparte por días y elige los alimentos con mejor relación calidad-precio. Vacío = sin límite.",
			v -> d.put("budget", v));
		textField(diet, "dislikes", "Alimentos que no tomas (opcional)", d.getOrDefault("dislikes", ""), false, false,
			"Por gusto o costumbre; las alergias van en su propio campo.", v -> d.put("dislikes", v));
		LinearLayout goals = card("¿Qué quieres conseguir?", "Elige el ajuste de calorías. Se confirma al guardar tus datos.");
		for (BodyProfile.Goal goal : BodyProfile.Goal.values()) {
			boolean selected = goal.name().equals(d.get("goal"));
			String description = goal == BodyProfile.Goal.LOSE ? "Reducir calorías para perder grasa." :
				goal == BodyProfile.Goal.GAIN ? "Aumentar calorías para apoyar la ganancia muscular." :
					"Cubrir tu gasto para mantener el peso.";
			MaterialButton button = action(goals, goal.label + "\n" + description, !n.busy,
				() -> n.selectNutritionGoal(goal));
			button.setTag("nutrition_goal_" + goal.name().toLowerCase(Locale.ROOT));
			// El nombre y la explicación deben leerse completos también con fuentes grandes.
			button.setSingleLine(false);
			button.setMaxLines(Integer.MAX_VALUE);
			button.setEllipsize(null);
			button.setCheckable(true);
			button.setChecked(selected);
			button.setContentDescription(goal.label + ". " + description + (selected ? " Seleccionado." : ""));
			if (selected) primary(button);
		}
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
