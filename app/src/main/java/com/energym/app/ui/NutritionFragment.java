package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.nutrition.*;
import com.energym.app.ui.charts.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.*;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VISTA de nutrición: anillos del día, diario por comidas, barras semanales y peso. El escáner de Google
 * Play services devuelve el código sin permiso de cámara en la app; el controlador busca la ficha.
 */
public final class NutritionFragment extends ScreenFragment {
	private static final int[] MEALS = {R.string.meal_breakfast, R.string.meal_lunch, R.string.meal_snack,
		R.string.meal_dinner, R.string.meal_other};

	static String meal(int ordinal) {
		return Txt.t(MEALS[ordinal]);
	}

	public NutritionFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		NutritionController n = state.nutrition;
		heading(Txt.t(R.string.nut_eyebrow), Txt.t(R.string.nutrition), "");
		if (app.owner() == null || n == null) {
			LinearLayout signIn = card(Txt.t(R.string.nut_sign_in), Txt.t(R.string.nut_sign_in_body));
			action(signIn, Txt.t(R.string.tr_choose_account), () -> app.navigate(Screen.ACCOUNT));
			return;
		}
		LocalDate day = n.selected;
		LinearLayout dates = columns(rows, 0);
		MaterialButton previous = compact(dates, "‹", true, () -> n.select(day.minusDays(1)));
		previous.setContentDescription(Txt.t(R.string.nut_previous_day));
		// El botón central muestra el día; si no es hoy, pulsarlo vuelve a hoy.
		MaterialButton today = compact(dates, label(day, n.today()), !day.equals(n.today()), () -> n.select(n.today()));
		today.setContentDescription(Txt.t(R.string.nut_shown_day, label(day, n.today())) +
			(day.equals(n.today()) ? "" : ". " + Txt.t(R.string.nut_go_today)));
		today.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		today.setSingleLine(true);
		today.setEllipsize(android.text.TextUtils.TruncateAt.END);
		MaterialButton next = compact(dates, "›", day.isBefore(n.today()), () -> n.select(day.plusDays(1)));
		next.setContentDescription(Txt.t(R.string.nut_next_day));
		((LinearLayout.LayoutParams) today.getLayoutParams()).weight = 3;
		add(rows, new android.widget.Space(requireContext()), 24);

		double[] totals = n.totals(day);
		BodyProfile.Targets targets = n.targets();
		// Sin objetivo, lo primero es fijarlo: esa tarjeta no se pliega.
		if (targets == null)
			dailyGoal(n, null);
		// Cada bloque se pliega con su cabecera y, plegado, deja ver su cifra clave.
		long left = targets == null ? 0 :
			Math.round(targets.kcal) - Math.round(totals[0]) + Math.round(exerciseKcal(n, day));
		if (fold("summary", Txt.t(R.string.nut_fold_summary),
			targets == null ? "" : Txt.t(R.string.nut_fold_left, left), true)) {
			if (targets != null)
				remaining(n, day, totals, targets);
			water(n, day);
		}

		List<FoodEntry> entries = n.entriesOn(day);
		if (fold("meals", Txt.t(R.string.nut_fold_meals), entries.isEmpty() ? "" :
			Txt.n(R.plurals.nut_fold_meals_detail, entries.size(), entries.size(), Math.round(totals[0])), true))
			meals(n, day, entries);
		if (!n.status.isEmpty())
			text(rows, n.status, R.style.EnerGym_FieldCaption, 0);

		NutritionController.Progress p = n.progress(progressDays);
		if (fold("progress", Txt.t(R.string.nut_fold_progress), p.logged == 0 ? "" :
			Txt.t(R.string.nut_fold_progress_detail, Math.round(p.kcal)), true))
			progress(n, targets, p);

		// Lo que no se usa a diario empieza plegado: objetivo, consulta con la IA y la dieta del mes.
		if (fold("more", Txt.t(R.string.nut_fold_more), "", false)) {
			if (targets != null)
				dailyGoal(n, targets);
			diet(n, targets);
			text(rows, Txt.t(R.string.nut_disclaimer), R.style.EnerGym_FieldCaption, 4);
		}

		// Añadir, siempre al final: alimento a mano o escaneando.
		LinearLayout add = columns(rows, 32);
		MaterialButton addFood = compact(add, Txt.t(R.string.nut_add), !n.busy, () -> {
			n.startManual(n.defaultMeal());
			app.navigate(Screen.FOOD);
		});
		primary(addFood);
		addFood.setTag("nutrition_add_food");
		addFood.setContentDescription(Txt.t(R.string.nut_add_food));
		compact(add, Txt.t(R.string.nut_scan), !n.busy, () -> {
			n.startManual(n.defaultMeal());
			scan(this);
		}).setTag("nutrition_scan");
	}

	/** Preferencias de los apartados plegados; se recuerdan aunque se cierre la app. */
	static final String FOLDS = "nutrition_folds";

	private boolean fold(String key, String label, String detail, boolean openByDefault) {
		return fold(FOLDS, "nutrition_fold_", key, label, detail, openByDefault);
	}

	/**
	 * Comidas del día: solo las que tienen algo anotado; si el día está vacío, una tarjeta y copiar lo de ayer a un
	 * toque. Debajo, las comidas guardadas.
	 */
	private void meals(NutritionController n, LocalDate day, List<FoodEntry> entries) {
		for (FoodEntry.Meal meal : FoodEntry.Meal.values()) {
			List<FoodEntry> items = new ArrayList<>();
			double mealKcal = 0;
			for (FoodEntry e : entries)
				if (e.meal == meal) {
					items.add(e);
					mealKcal += e.kcal();
				}
			if (items.isEmpty())
				continue;
			LinearLayout section = card(meal(meal.ordinal()) + "   " + Math.round(mealKcal) + " kcal", "");
			for (FoodEntry e : items)
				foodRow(section, e, n);
			action(section, Txt.t(R.string.nut_save_as_meal), !n.busy, () -> saveMeal(items, meal(meal.ordinal())))
				.setTag("save_meal_" + meal.name());
		}
		if (entries.isEmpty()) {
			List<FoodEntry> yesterday = n.entriesOn(day.minusDays(1));
			LinearLayout empty = card(Txt.t(day.equals(n.today()) ? R.string.nut_nothing_today : R.string.nut_nothing_day),
				Txt.t(R.string.nut_nothing_body));
			if (!yesterday.isEmpty())
				action(empty, Txt.n(R.plurals.nut_copy_yesterday, yesterday.size()), !n.busy,
					() -> n.logAll(yesterday, null, Txt.t(R.string.nut_copied_yesterday), () -> {
					})).setTag("copy_yesterday");
		}
		savedMeals(n);
	}

	/** Pide un nombre y guarda esos alimentos como comida para anotarla otro día de un toque. */
	private void saveMeal(List<FoodEntry> foods, String suggested) {
		android.widget.EditText name = new android.widget.EditText(requireContext());
		name.setText(suggested);
		name.setSelectAllOnFocus(true);
		name.setSingleLine(true);
		android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
		box.setPadding(dp(24), dp(8), dp(24), 0);
		box.addView(name);
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(Txt.t(R.string.nut_save_meal)).setMessage(Txt.t(R.string.nut_save_meal_hint))
			.setView(box).setNegativeButton(Txt.t(R.string.common_cancel), null)
			.setPositiveButton(Txt.t(R.string.common_save), (d, w) -> {
				String error = state.savedMeals.save(name.getText().toString(), foods);
				android.widget.Toast.makeText(requireContext(), error.isEmpty() ? Txt.t(R.string.nut_meal_saved) : error,
					android.widget.Toast.LENGTH_LONG).show();
				render();
			}).show();
	}

	/** Comidas guardadas: se anotan enteras en la comida que toca según la hora. */
	private void savedMeals(NutritionController n) {
		com.energym.app.nutrition.SavedMeals saved = state.savedMeals;
		if (saved == null || saved.list().isEmpty())
			return;
		LinearLayout card = card(Txt.t(R.string.nut_saved_meals),
			Txt.t(R.string.nut_saved_meals_body, meal(n.defaultMeal().ordinal()).toLowerCase(Txt.locale())));
		for (com.energym.app.nutrition.SavedMeals.Meal m : saved.list()) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			((TextView) row.findViewById(R.id.food_name)).setText(m.name);
			List<String> names = new ArrayList<>();
			for (FoodEntry f : m.foods)
				names.add(f.label());
			((TextView) row.findViewById(R.id.food_detail)).setText(String.join(", ", names));
			((TextView) row.findViewById(R.id.food_kcal)).setText(Math.round(m.kcal()) + " kcal");
			row.setContentDescription(m.name + ", " + Math.round(m.kcal()) + " kcal. " + Txt.t(R.string.nut_tap_to_log));
			row.setOnClickListener(v -> {
				if (!n.busy)
					n.logAll(m.foods, n.defaultMeal(), m.name, () -> {
					});
			});
			Runnable delete = () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle(Txt.t(R.string.nut_delete_saved_meal_title, m.name)).setNegativeButton(Txt.t(R.string.common_cancel), null)
				.setPositiveButton(Txt.t(R.string.common_delete), (d, w) -> {
					saved.remove(m.name);
					render();
				}).show();
			row.setOnLongClickListener(v -> {
				delete.run();
				return true;
			});
			rowIcon((LinearLayout) row, R.drawable.ic_delete, Txt.t(R.string.nut_delete_named, m.name),
				"saved_meal_delete_" + m.name, delete);
			card.addView(row);
		}
	}

	/** Sección «Más» desplegada. */
	/** Días de «Tu progreso»: 7 (cada día) o 28 (media de cada semana). */
	private static int progressDays = 7;

	private static String euros(double value) {
		return String.format(Txt.locale(), "%.2f €", value);
	}

	/**
	 * Dieta del mes con la IA del móvil: cada día tiene su menú; se elige el día con las flechas y los días que
	 * vienen se ajustan solos con lo que se anota (comidas, peso y preferencias).
	 */
	private void diet(NutritionController n, BodyProfile.Targets targets) {
		com.energym.app.nutrition.DietPlanner planner = state.diet;
		if (planner == null || n.body == null || targets == null)
			return;
		BodyProfile current = n.effectiveBody();
		planner.sync(current);
		com.energym.app.nutrition.DietPlan plan = planner.plan;
		LocalDate day = planner.selected == null ? n.today() : planner.selected;
		java.time.YearMonth month = planner.month();
		String monthName = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Txt.locale());
		section(Txt.t(R.string.nut_month_diet), 32);
		double cost = planner.monthCost();
		LinearLayout card = card(Txt.t(R.string.nut_your_diet, monthName), planner.busy ? Txt.t(R.string.nut_diet_busy) :
			Txt.t(R.string.nut_diet_summary, planner.days().size(), current.mealsPerDay) +
				(cost >= 0 ? " · " + Txt.t(R.string.nut_diet_cost, Math.round(cost)) +
					(current.monthlyBudget > 0 ? " " + Txt.t(R.string.nut_diet_budget, Math.round(current.monthlyBudget)) : "") : "") +
				"\n" + Txt.t(R.string.nut_diet_adjusts));
		card.setTag("nutrition_diet");
		if (planner.busy || plan == null)
			return;
		MaterialButton toggle = action(card, Txt.t(planner.expanded ? R.string.nut_hide_menu : R.string.nut_show_menu), true, () -> {
			planner.expanded = !planner.expanded;
			render();
		});
		toggle.setTag("diet_toggle");
		toggle.setContentDescription(toggle.getText() + ", " + Txt.t(planner.expanded ? R.string.nut_expanded : R.string.nut_collapsed));
		if (!planner.expanded)
			return;
		// Día del mes: anterior / día / siguiente.
		LinearLayout nav = columns(card, 16);
		LocalDate first = planner.days().isEmpty() ? day : planner.days().firstKey();
		MaterialButton back = compact(nav, "‹", day.isAfter(first), () -> planner.select(day.minusDays(1)));
		back.setContentDescription(Txt.t(R.string.nut_previous_day));
		back.setTextSize(22);
		MaterialButton label = compact(nav, label(day, n.today()), !day.equals(n.today()), () -> planner.select(n.today()));
		label.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		((LinearLayout.LayoutParams) label.getLayoutParams()).weight = 3;
		MaterialButton forward = compact(nav, "›", day.isBefore(month.atEndOfMonth()), () -> planner.select(day.plusDays(1)));
		forward.setContentDescription(Txt.t(R.string.nut_next_day));
		forward.setTextSize(22);
		text(card, plan.source + " · " + Math.round(plan.totals()[0]) + " kcal" +
			(plan.cost() >= 0 ? " · ≈ " + euros(plan.cost()) : ""), R.style.EnerGym_FieldCaption, 12);
		if (!planner.adjustment.isEmpty())
			text(card, "⚖ " + planner.adjustment, R.style.EnerGym_FieldCaption, 4);
		if (!planner.status.isEmpty())
			text(card, planner.status, R.style.EnerGym_FieldCaption, 4);
		boolean today = day.equals(n.today());
		for (int i = 0; i < plan.meals.size(); i++) {
			com.energym.app.nutrition.DietPlan.Meal meal = plan.meals.get(i);
			TextView title = text(card, meal.icon() + "  " + meal.name + " · " + Math.round(meal.kcal) + " kcal" +
				(meal.cost >= 0 ? " · ≈ " + euros(meal.cost) : ""), R.style.EnerGym_CardTitle, i == 0 ? 16 : 24);
			title.setTextSize(17);
			text(card, meal.dish, R.style.EnerGym_Body, 4);
			if (!meal.ingredients.isEmpty())
				text(card, meal.ingredients, R.style.EnerGym_FieldCaption, 4);
			text(card, Txt.t(R.string.nut_macros_line, Math.round(meal.protein), Math.round(meal.carbs), Math.round(meal.fat)),
				R.style.EnerGym_FieldCaption, 4);
			if (today)
				action(card, Txt.t(R.string.nut_log_in_diary), !n.busy, () -> n.logPlanned(meal, () -> { })).setTag("diet_log_" + i);
		}
		if (!plan.advice.isEmpty())
			text(card, "💡 " + plan.advice, R.style.EnerGym_Body, 20);
		action(card, Txt.t(plan.source.startsWith("Creado con IA") ? R.string.nut_diet_again : R.string.nut_diet_improve),
			!n.busy, () -> planner.create(current)).setTag("diet_again");
		text(card, Txt.t(R.string.nut_diet_disclaimer),
			R.style.EnerGym_FieldCaption, 8);
	}

	/** Agua del día: vasos de 250 ml frente a un objetivo según el peso. */
	private void water(NutritionController n, LocalDate day) {
		com.energym.app.nutrition.WaterLog w = state.water;
		if (w == null)
			return;
		int ml = w.ml(day), goal = com.energym.app.nutrition.WaterLog.goal(n.effectiveBody());
		int glasses = ml / com.energym.app.nutrition.WaterLog.GLASS, target = goal / com.energym.app.nutrition.WaterLog.GLASS;
		LinearLayout card = card(Txt.t(R.string.nut_water_title, com.energym.app.nutrition.WaterLog.liters(ml),
			com.energym.app.nutrition.WaterLog.liters(goal)), ml >= goal ? Txt.t(R.string.nut_water_done) :
			Txt.n(R.plurals.nut_glasses, target, glasses, target));
		card.setTag("nutrition_water");
		add(card, new com.energym.app.ui.charts.ProgressBarView(requireContext()), 12)
			.set(goal == 0 ? 0 : Math.min(1, ml / (double) goal), Txt.t(R.string.nut_water_progress, ml, goal));
		LinearLayout buttons = columns(card, 16);
		MaterialButton less = compact(buttons, Txt.t(R.string.nut_glass_less), ml > 0, () -> w.add(day, -com.energym.app.nutrition.WaterLog.GLASS));
		less.setContentDescription(Txt.t(R.string.nut_glass_less_description));
		MaterialButton more = compact(buttons, Txt.t(R.string.nut_glass_more), true, () -> w.add(day, com.energym.app.nutrition.WaterLog.GLASS));
		more.setContentDescription(Txt.t(R.string.nut_glass_more_description));
		primary(more);
	}

	/** Objetivo confirmado y visible; el formulario mantiene su propio borrador hasta pulsar Guardar. */
	private void dailyGoal(NutritionController n, BodyProfile.Targets targets) {
		BodyProfile current = n.effectiveBody();
		LocalDate weightDay = n.targetWeightDay();
		LinearLayout goal = card(Txt.t(R.string.nut_daily_goal), n.body == null ?
			Txt.t(R.string.nut_daily_goal_empty) :
			targets == null ? current.estimationIssue() : current.goal.display() + "\n" + Txt.t(R.string.nut_calculated_with,
				NutritionController.format(current.weightKg), weightDay == null ? Txt.t(R.string.nut_no_date) :
					weightDay.format(DateTimeFormatter.ofPattern(Txt.t(R.string.pattern_short_date), Txt.locale()))));
		if (targets != null) {
			TextView kcal = text(goal, Txt.t(R.string.nut_kcal_day, Math.round(targets.kcal)), R.style.EnerGym_StatValue, 4);
			kcal.setTag("nutrition_daily_target_kcal");
			LinearLayout macros = columns(goal, 16);
			stat(macros, Math.round(targets.protein) + " g", Txt.t(R.string.nut_protein));
			stat(macros, Math.round(targets.carbs) + " g", Txt.t(R.string.nut_carbs));
			stat(macros, Math.round(targets.fat) + " g", Txt.t(R.string.nut_fat));
		}
		MaterialButton edit = action(goal, Txt.t(n.body == null ? R.string.nut_set_goal : R.string.nut_change_goal), !n.busy, () -> open(Screen.BODY));
		edit.setTag("nutrition_change_goal");
		if (targets == null) primary(edit);
		if (targets != null)
			action(goal, Txt.t(R.string.nut_consult_ai), state.assistant != null && !state.assistant.busy, () -> {
			if (state.assistant == null || state.assistant.busy) return;
			state.assistant.selectLocalGuidance(false);
			state.assistant.messageInput(n.coachBrief());
			app.navigate(Screen.ASSISTANT);
		}).setTag("nutrition_consult_ai");
	}

	/**
	 * Cabecera tipo registro de calorías: Objetivo − Comida + Ejercicio = Restantes, y barras de macros.
	 */
	private void remaining(NutritionController n, LocalDate day, double[] totals, BodyProfile.Targets targets) {
		if (targets == null) return;
		LinearLayout card = card(Txt.t(R.string.nut_left_today), "");
		// Se redondea cada término antes de operar para que la cuenta visible cuadre (2451 − 195 + 0 = 2256).
		long goal = Math.round(targets.kcal), food = Math.round(totals[0]), exercise = Math.round(exerciseKcal(n, day));
		long left = goal - food + exercise;
		LinearLayout equation = columns(card, 4);
		term(equation, goal, Txt.t(R.string.nut_term_goal), false);
		operator(equation, "−");
		term(equation, food, Txt.t(R.string.nut_term_food), false);
		operator(equation, "+");
		term(equation, exercise, Txt.t(R.string.common_exercise), false);
		operator(equation, "=");
		term(equation, left, Txt.t(R.string.nut_term_left), true);
		equation.setContentDescription(Txt.t(R.string.nut_equation_description, goal, food, exercise, left));
		macro(card, Txt.t(R.string.nut_carbs), totals[2], targets.carbs, 0);
		macro(card, Txt.t(R.string.nut_protein), totals[1], targets.protein, 1);
		macro(card, Txt.t(R.string.nut_fat), totals[3], targets.fat, 2);
	}

	/**
	 * Tu progreso: calorías de cada día (o media de cada semana) frente al objetivo, días en objetivo y la media de
	 * cada macro. Siempre hasta hoy, sea cual sea el día elegido arriba.
	 */
	private void progress(NutritionController n, BodyProfile.Targets targets, NutritionController.Progress p) {
		segmented("nutrition_progress", new String[]{Txt.t(R.string.nut_progress_week), Txt.t(R.string.nut_progress_month)},
			new String[]{"nutrition_progress_7", "nutrition_progress_28"}, progressDays == 7 ? 0 : 1, index -> {
				progressDays = index == 0 ? 7 : NutritionController.HISTORY_DAYS;
				render();
			});
		boolean week = progressDays == 7;
		LinearLayout card = card(Txt.t(R.string.nut_progress_kcal),
			p.logged == 0 ? Txt.t(R.string.nut_progress_empty) :
				Txt.t(week ? R.string.nut_progress_body_week : R.string.nut_progress_body_month));
		card.setTag("nutrition_progress");
		if (p.logged == 0)
			return;
		LinearLayout figures = columns(card, 12);
		stat(figures, Long.toString(Math.round(p.kcal)), Txt.t(R.string.nut_progress_avg));
		stat(figures, p.logged + "/" + p.days, Txt.n(R.plurals.nut_progress_logged, p.logged));
		if (targets != null)
			stat(figures, p.onTarget + "/" + p.logged, Txt.t(R.string.nut_progress_on_target));
		StringBuilder values = new StringBuilder();
		for (int i = 0; i < p.bars.length; i++)
			values.append(i == 0 ? "" : ", ").append(p.labels[i]).append(' ').append(Math.round(p.bars[i]));
		add(card, new BarChartView(requireContext()), 12).set(p.bars, p.labels, targets == null ? 0 : targets.kcal,
			Txt.t(week ? R.string.nut_progress_chart_week : R.string.nut_progress_chart_month, values.toString()))
			.highlightLast(week);
		text(card, Txt.t(R.string.nut_progress_macros), R.style.EnerGym_CardTitle, 16);
		if (targets == null) {
			text(card, Txt.t(R.string.nut_progress_macros_plain, Math.round(p.protein), Math.round(p.carbs),
				Math.round(p.fat)), R.style.EnerGym_Body, 4);
			return;
		}
		macro(card, Txt.t(R.string.nut_carbs), p.carbs, targets.carbs, 0);
		macro(card, Txt.t(R.string.nut_protein), p.protein, targets.protein, 1);
		macro(card, Txt.t(R.string.nut_fat), p.fat, targets.fat, 2);
	}

	private void term(LinearLayout row, long value, String label, boolean highlight) {
		LinearLayout box = new LinearLayout(requireContext());
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
		TextView number = new TextView(requireContext());
		number.setText(Long.toString(value));
		number.setTextSize(highlight ? 20 : 17);
		number.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		number.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
			highlight ? (value < 0 ? R.color.chart_over : R.color.gold) : R.color.white));
		number.setMaxLines(1);
		TextView caption = new TextView(requireContext());
		caption.setText(label);
		caption.setTextSize(11);
		caption.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary));
		caption.setMaxLines(1);
		box.addView(number);
		box.addView(caption);
		box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
		row.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, highlight ? 1.3f : 1));
	}

	private void operator(LinearLayout row, String symbol) {
		TextView op = new TextView(requireContext());
		op.setText(symbol);
		op.setTextSize(17);
		op.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary));
		op.setGravity(android.view.Gravity.CENTER);
		op.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		row.addView(op, new LinearLayout.LayoutParams(dp(14), ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private void macro(LinearLayout card, String name, double value, double target, int kind) {
		LinearLayout line = columns(card, 14);
		TextView label = new TextView(requireContext());
		label.setTextAppearance(R.style.EnerGym_FieldCaption);
		label.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		label.setText(name);
		line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		TextView amount = new TextView(requireContext());
		amount.setTextAppearance(R.style.EnerGym_FieldCaption);
		amount.setText(Math.round(value) + " / " + Math.round(target) + " g");
		line.addView(amount);
		ProgressBarView bar = new ProgressBarView(requireContext());
		bar.macro(kind == 0 ? bar.carbs() : kind == 1 ? bar.protein() : bar.fat());
		add(card, bar, 2).set(target <= 0 ? 0 : value / target, name);
	}

	/**
	 * Gasto estimado de los entrenos finalizados ese día: MET 5 × peso × horas registradas.
	 */
	private double exerciseKcal(NutritionController n, LocalDate day) {
		BodyProfile current = n.effectiveBody();
		if (current == null)
			return 0;
		com.energym.app.progress.ProgressStats stats = new com.energym.app.progress.ProgressStats(app.history,
			java.time.ZoneId.systemDefault(), n.today());
		double minutes = 0;
		for (com.energym.app.domain.Session s : stats.sessionsOn(day))
			minutes += Math.max(0, s.getCompletedAt() - s.getStartedAt()) / 60000.0;
		return 5 * current.weightKg * Math.min(minutes, 240) / 60.0;
	}

	private void foodRow(LinearLayout section, FoodEntry e, NutritionController n) {
		View row = getLayoutInflater().inflate(R.layout.row_food, section, false);
		((TextView) row.findViewById(R.id.food_name)).setText(e.label());
		((TextView) row.findViewById(R.id.food_detail)).setText(NutritionController.format(e.amount) + " " +
			e.unit.symbol + " · " + Txt.t(R.string.nut_macros_short, one(e.protein()), one(e.carbs()), one(e.fat())));
		((TextView) row.findViewById(R.id.food_kcal)).setText(Long.toString(Math.round(e.kcal())));
		// Con los botones de editar y borrar queda menos ancho: los macros pasan a una segunda línea en vez de cortarse.
		((TextView) row.findViewById(R.id.food_detail)).setMaxLines(2);
		row.setContentDescription(e.label() + ", " + NutritionController.format(e.amount) + " " + e.unit.symbol + ", " +
			Txt.t(R.string.nut_food_row_description, Math.round(e.kcal())));
		long scope = n.scopeRevision();
		row.setTag("food_options_" + e.id);
		row.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(e.label())
			.setItems(new String[]{Txt.t(R.string.nut_edit_food), Txt.t(R.string.nut_add_again), Txt.t(R.string.nut_remove_from_diary)}, (dialog, which) -> {
				if (n.busy || scope != n.scopeRevision()) return;
				if (which == 0)
					editFood(n, e, scope);
				else if (which == 1) {
					n.startFrom(e);
					app.navigate(Screen.FOOD);
				} else
					removeFood(n, e, scope);
			}).setNegativeButton(Txt.t(R.string.common_cancel), null).show());
		// Editar y borrar a la vista, sin tener que descubrir el menú de la fila.
		rowIcon((LinearLayout) row, R.drawable.ic_edit, Txt.t(R.string.nut_edit_named, e.label()), "food_edit_" + e.id,
			() -> editFood(n, e, scope));
		rowIcon((LinearLayout) row, R.drawable.ic_delete, Txt.t(R.string.nut_delete_named, e.label()),
			"food_delete_" + e.id, () -> removeFood(n, e, scope));
		section.addView(row);
		View divider = new View(requireContext());
		divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.divider));
		section.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
	}

	/** Abre el alimento anotado para cambiar cantidad, comida, nombre o valores; se guarda en la misma entrada. */
	private void editFood(NutritionController n, FoodEntry e, long scope) {
		if (n.busy || scope != n.scopeRevision())
			return;
		n.edit(e);
		app.navigate(Screen.FOOD);
	}

	/** Borrar del diario siempre pide confirmación. */
	private void removeFood(NutritionController n, FoodEntry e, long scope) {
		if (n.busy || scope != n.scopeRevision())
			return;
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(Txt.t(R.string.nut_remove_food_title))
			.setMessage(Txt.t(R.string.nut_remove_food_message, e.label()))
			.setNegativeButton(Txt.t(R.string.common_cancel), null)
			.setPositiveButton(Txt.t(R.string.common_remove), (confirm, button) -> {
				if (scope == n.scopeRevision()) n.remove(e);
			}).show();
	}

	/** Botón de icono de 48 dp al final de una fila (editar, borrar). */
	private void rowIcon(LinearLayout row, int icon, String description, String tag, Runnable click) {
		android.widget.ImageButton button = new android.widget.ImageButton(requireContext());
		button.setImageResource(icon);
		android.util.TypedValue ripple = new android.util.TypedValue();
		requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
		button.setBackgroundResource(ripple.resourceId);
		button.setContentDescription(description);
		button.setTag(tag);
		button.setOnClickListener(v -> click.run());
		row.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
	}

	private static String one(double value) {
		return NutritionController.format(Math.round(value * 10) / 10.0);
	}

	private void open(Screen screen) {
		state.nutrition.fillBody();
		app.navigate(screen);
	}

	/**
	 * Escáner compartido por el diario y la pantalla de añadir alimento.
	 */
	static void scan(ScreenFragment f) {
		NutritionController nutrition = f.state.nutrition;
		if (nutrition == null || nutrition.owner() == null)
			return;
		long token = nutrition.scanToken();
		GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
			.setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
				Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX).enableAutoZoom().build();
		GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(f.requireActivity(), options);
		scanner.startScan().addOnSuccessListener(barcode -> {
			if (!f.isAdded() || f.state.nutrition != nutrition)
				return;
			if (nutrition.scanned(barcode.getRawValue(), token))
				f.app.navigate(Screen.FOOD);
		}).addOnFailureListener(e -> {
			if (!f.isAdded() || f.state.nutrition != nutrition || token != nutrition.scanToken())
				return;
			f.app.error(Txt.t(R.string.nut_scanner_failed));
		});
	}

	static String label(LocalDate day, LocalDate today) {
		if (day.equals(today))
			return Txt.t(R.string.common_today);
		if (day.equals(today.minusDays(1)))
			return Txt.t(R.string.common_yesterday);
		String text = day.format(DateTimeFormatter.ofPattern(Txt.t(R.string.pattern_weekday_day_month), Txt.locale()));
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static long[] round(double[] values) {
		long[] result = new long[values.length];
		for (int i = 0; i < values.length; i++)
			result[i] = Math.round(values[i]);
		return result;
	}
}
