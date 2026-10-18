package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
		if (app.guidedStep == Screen.BODY)
			heading(Txt.t(R.string.guide_body_eyebrow), Txt.t(R.string.body_title), Txt.t(R.string.guide_body_subtitle));
		else
			heading(Txt.t(R.string.nut_eyebrow), Txt.t(R.string.body_title), Txt.t(R.string.body_subtitle));
		if (n == null || app.owner() == null) {
			card(Txt.t(R.string.nut_sign_in), "");
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
		LinearLayout basics = card(Txt.t(R.string.body_about_you), Txt.t(R.string.body_about_you_body));
		choice(basics, "sex", Txt.t(R.string.body_sex), Arrays.asList("FEMALE", "MALE"),
			Arrays.asList(Txt.t(R.string.body_female), Txt.t(R.string.body_male)), d.get("sex"), Txt.t(R.string.body_sex_help),
			v -> { d.put("sex", v); refresh[0].run(); });
		textField(basics, "age", Txt.t(R.string.body_age), d.get("age"), true, true,
			Txt.t(R.string.body_age_help),
			v -> { d.put("age", v); refresh[0].run(); });
		textField(basics, "heightCm", Txt.t(R.string.body_height), d.get("heightCm"), true, false,
			Txt.t(R.string.body_height_help),
			v -> { d.put("heightCm", v); refresh[0].run(); });
		textField(basics, "weightKg", Txt.t(R.string.body_weight), d.get("weightKg"), true, false,
			Txt.t(R.string.body_weight_help),
			v -> { d.put("weightKg", v); refresh[0].run(); });

		LinearLayout plan = card(Txt.t(R.string.body_activity_title), Txt.t(R.string.body_activity_body));
		choice(plan, "activity", Txt.t(R.string.body_activity),
			Arrays.asList("SEDENTARY", "LIGHT", "MODERATE", "HIGH", "VERY_HIGH"),
			Arrays.asList(Txt.t(R.string.body_activity_sedentary), Txt.t(R.string.body_activity_light),
				Txt.t(R.string.body_activity_moderate), Txt.t(R.string.body_activity_high), Txt.t(R.string.body_activity_very_high)),
			d.get("activity"), n.body == null && app.profile != null ?
				Txt.t(R.string.body_activity_proposed, app.profile.getDaysPerWeek()) :
				Txt.t(R.string.body_activity_help),
			v -> { d.put("activity", v); refresh[0].run(); });
		textField(plan, "allergies", Txt.t(R.string.body_allergies), d.get("allergies"), false, false,
			Txt.t(R.string.body_allergies_help), v -> d.put("allergies", v));
		LinearLayout diet = card(Txt.t(R.string.body_ai_diet), Txt.t(R.string.body_ai_diet_body));
		List<String> diets = new ArrayList<>(), dietLabels = new ArrayList<>();
		for (BodyProfile.Diet value : BodyProfile.Diet.values()) {
			diets.add(value.name());
			dietLabels.add(value.display());
		}
		choice(diet, "diet", Txt.t(R.string.body_diet_type), diets, dietLabels, d.getOrDefault("diet", "OMNIVORE"),
			Txt.t(R.string.body_diet_type_help), v -> d.put("diet", v));
		List<String> mealCounts = new ArrayList<>();
		for (int count = 2; count <= 6; count++)
			mealCounts.add(Txt.n(R.plurals.body_meals_count, count));
		choice(diet, "mealsPerDay", Txt.t(R.string.body_meals_per_day), Arrays.asList("2", "3", "4", "5", "6"),
			mealCounts,
			d.getOrDefault("mealsPerDay", "4"), Txt.t(R.string.body_meals_help), v -> d.put("mealsPerDay", v));
		List<String> cooking = new ArrayList<>(), cookingLabels = new ArrayList<>();
		for (BodyProfile.Cooking value : BodyProfile.Cooking.values()) {
			cooking.add(value.name());
			cookingLabels.add(value.display());
		}
		choice(diet, "cooking", Txt.t(R.string.body_cooking_time), cooking, cookingLabels, d.getOrDefault("cooking", "NORMAL"),
			"", v -> d.put("cooking", v));
		textField(diet, "budget", Txt.t(R.string.body_budget), d.getOrDefault("budget", ""), true,
			false, Txt.t(R.string.body_budget_help),
			v -> d.put("budget", v));
		textField(diet, "dislikes", Txt.t(R.string.body_dislikes), d.getOrDefault("dislikes", ""), false, false,
			Txt.t(R.string.body_dislikes_help), v -> d.put("dislikes", v));
		LinearLayout goals = card(Txt.t(R.string.body_goal_title), Txt.t(R.string.body_goal_body));
		for (BodyProfile.Goal goal : BodyProfile.Goal.values()) {
			boolean selected = goal.name().equals(d.get("goal"));
			String description = Txt.t(goal == BodyProfile.Goal.LOSE ? R.string.body_goal_lose_help :
				goal == BodyProfile.Goal.GAIN ? R.string.body_goal_gain_help : R.string.body_goal_maintain_help);
			MaterialButton button = action(goals, goal.display() + "\n" + description, !n.busy,
				() -> n.selectNutritionGoal(goal));
			button.setTag("nutrition_goal_" + goal.name().toLowerCase(Locale.ROOT));
			// El nombre y la explicación deben leerse completos también con fuentes grandes.
			button.setSingleLine(false);
			button.setMaxLines(Integer.MAX_VALUE);
			button.setEllipsize(null);
			button.setCheckable(true);
			button.setChecked(selected);
			button.setContentDescription(goal.display() + ". " + description + (selected ? " " + Txt.t(R.string.body_selected) : ""));
			if (selected) primary(button);
		}
		LinearLayout preview = card(Txt.t(R.string.body_estimate), Txt.t(R.string.body_estimate_body));
		TextView kcal = text(preview, "", R.style.EnerGym_CardTitle, 4);
		kcal.setTag("nutrition_goal_preview_kcal");
		TextView macros = text(preview, "", R.style.EnerGym_Body, 8);
		macros.setTag("nutrition_goal_preview_macros");
		kcal.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
		refresh[0] = () -> {
			BodyProfile.Targets targets = n.previewTargets();
			kcal.setText(targets == null ? Txt.t(R.string.body_estimate_pending) : Txt.t(R.string.nut_kcal_day, Math.round(targets.kcal)));
			macros.setText(targets == null ? n.bodyPreviewIssue() :
				Txt.t(R.string.body_macros, Math.round(targets.protein), Math.round(targets.carbs), Math.round(targets.fat)));
		};
		refresh[0].run();
		card(Txt.t(R.string.body_how), Txt.t(R.string.body_how_body));
		primary(action(rows, Txt.t(R.string.body_save), !n.busy,
			() -> n.saveBody(() -> app.navigate(Screen.NUTRITION))));
		action(rows, Txt.t(R.string.common_cancel), !n.busy, () -> {
			n.cancelBody();
			app.navigate(Screen.NUTRITION);
		});
	}
}
