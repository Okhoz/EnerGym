package com.energym.app.ui;

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
	static final Locale SPANISH = new Locale("es", "ES");
	private static final String[] MEALS = {"Desayuno", "Comida", "Merienda", "Cena", "Otros"};

	public NutritionFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		NutritionController n = state.nutrition;
		heading("NUTRICIÓN", "Nutrición", "");
		if (app.owner() == null || n == null) {
			LinearLayout signIn = card("Entra para empezar", "Tu diario se guarda por cuenta en este dispositivo.");
			action(signIn, "Elegir cuenta", () -> app.navigate(Screen.ACCOUNT));
			return;
		}
		LocalDate day = n.selected;
		LinearLayout dates = columns(rows, 0);
		MaterialButton previous = compact(dates, "‹", true, () -> n.select(day.minusDays(1)));
		previous.setContentDescription("Día anterior");
		previous.setTextSize(22);
		// El botón central muestra el día; si no es hoy, pulsarlo vuelve a hoy.
		MaterialButton today = compact(dates, label(day, n.today()), !day.equals(n.today()), () -> n.select(n.today()));
		today.setContentDescription("Día mostrado: " + label(day, n.today()) + (day.equals(n.today()) ? "" : ". Ir a hoy"));
		today.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		today.setSingleLine(true);
		today.setEllipsize(android.text.TextUtils.TruncateAt.END);
		MaterialButton next = compact(dates, "›", day.isBefore(n.today()), () -> n.select(day.plusDays(1)));
		next.setContentDescription("Día siguiente");
		next.setTextSize(22);
		((LinearLayout.LayoutParams) today.getLayoutParams()).weight = 3;
		add(rows, new android.widget.Space(requireContext()), 8);

		double[] totals = n.totals(day);
		BodyProfile.Targets targets = n.targets();
		// Arriba, de un vistazo: lo que te queda hoy (o configurar el objetivo si aún no hay).
		if (targets == null)
			dailyGoal(n, null);
		else
			remaining(n, day, totals, targets);
		water(n, day);

		section("Tus comidas", 32);
		// Solo las comidas con algo anotado; si el día está vacío, una tarjeta y copiar lo de ayer a un toque.
		List<FoodEntry> entries = n.entriesOn(day);
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
			LinearLayout section = card(MEALS[meal.ordinal()] + "   " + Math.round(mealKcal) + " kcal", "");
			for (FoodEntry e : items)
				foodRow(section, e, n);
			action(section, "★ Guardar como comida", !n.busy, () -> saveMeal(items, MEALS[meal.ordinal()]))
				.setTag("save_meal_" + meal.name());
		}
		if (entries.isEmpty()) {
			List<FoodEntry> yesterday = n.entriesOn(day.minusDays(1));
			LinearLayout empty = card("Nada anotado " + (day.equals(n.today()) ? "hoy" : "este día"),
				"Pulsa «+ Añadir» o escanea un código de barras.");
			if (!yesterday.isEmpty())
				action(empty, "↺ Copiar lo del día anterior (" + yesterday.size() +
					(yesterday.size() == 1 ? " alimento)" : " alimentos)"), !n.busy,
					() -> n.logAll(yesterday, null, "Copiado del día anterior", () -> {
					})).setTag("copy_yesterday");
		}
		savedMeals(n);
		if (!n.status.isEmpty())
			text(rows, n.status, R.style.EnerGym_FieldCaption, 0);

		// Lo que no se usa a diario, plegado: objetivo, consulta con la IA y la dieta del mes.
		section("Más", 32);
		MaterialButton more = action(rows, moreOpen ? "Ocultar ▴" : "Objetivo, dieta del mes e IA ▾", true, () -> {
			moreOpen = !moreOpen;
			render();
		});
		more.setTag("nutrition_more");
		if (moreOpen) {
			if (targets != null)
				dailyGoal(n, targets);
			diet(n, targets);
			text(rows, "Datos de Open Food Facts (ODbL): revisa la etiqueta. Objetivos estimados; no sustituyen a un " +
				"dietista o médico.", R.style.EnerGym_FieldCaption, 4);
		}

		// Añadir, siempre al final: alimento a mano o escaneando.
		LinearLayout add = columns(rows, 32);
		MaterialButton addFood = compact(add, "+ Añadir", !n.busy, () -> {
			n.startManual(n.defaultMeal());
			app.navigate(Screen.FOOD);
		});
		primary(addFood);
		addFood.setTag("nutrition_add_food");
		addFood.setContentDescription("Añadir alimento");
		compact(add, "Escanear", !n.busy, () -> {
			n.startManual(n.defaultMeal());
			scan(this);
		}).setTag("nutrition_scan");
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
			.setTitle("Guardar comida").setMessage("Ponle un nombre, p. ej. «Mi desayuno» o «Pasta con atún».")
			.setView(box).setNegativeButton("Cancelar", null)
			.setPositiveButton("Guardar", (d, w) -> {
				String error = state.savedMeals.save(name.getText().toString(), foods);
				android.widget.Toast.makeText(requireContext(), error.isEmpty() ? "Comida guardada" : error,
					android.widget.Toast.LENGTH_LONG).show();
				render();
			}).show();
	}

	/** Comidas guardadas: se anotan enteras en la comida que toca según la hora. */
	private void savedMeals(NutritionController n) {
		com.energym.app.nutrition.SavedMeals saved = state.savedMeals;
		if (saved == null || saved.list().isEmpty())
			return;
		LinearLayout card = card("★ Comidas guardadas", "Toca una para anotarla entera en " +
			MEALS[n.defaultMeal().ordinal()].toLowerCase(SPANISH) + ". Mantén pulsado para borrarla.");
		for (com.energym.app.nutrition.SavedMeals.Meal m : saved.list()) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, card, false);
			((TextView) row.findViewById(R.id.food_name)).setText(m.name);
			List<String> names = new ArrayList<>();
			for (FoodEntry f : m.foods)
				names.add(f.name);
			((TextView) row.findViewById(R.id.food_detail)).setText(String.join(", ", names));
			((TextView) row.findViewById(R.id.food_kcal)).setText(Math.round(m.kcal()) + " kcal");
			row.setContentDescription(m.name + ", " + Math.round(m.kcal()) + " kcal. Toca para anotarla");
			row.setOnClickListener(v -> {
				if (!n.busy)
					n.logAll(m.foods, n.defaultMeal(), m.name, () -> {
					});
			});
			row.setOnLongClickListener(v -> {
				new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle("¿Borrar «" + m.name + "»?").setNegativeButton("Cancelar", null)
					.setPositiveButton("Borrar", (d, w) -> {
						saved.remove(m.name);
						render();
					}).show();
				return true;
			});
			card.addView(row);
		}
	}

	/** Sección «Más» desplegada. */
	private static boolean moreOpen;

	private static String euros(double value) {
		return String.format(SPANISH, "%.2f €", value);
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
		String monthName = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, SPANISH);
		section("Dieta del mes", 32);
		double cost = planner.monthCost();
		LinearLayout card = card("✦ Tu dieta de " + monthName, planner.busy ? "Preparando el menú con la IA del móvil…" :
			planner.days().size() + " días con menú · " + current.mealsPerDay + " comidas al día" +
				(cost >= 0 ? " · ≈ " + Math.round(cost) + " € previstos" +
					(current.monthlyBudget > 0 ? " de " + Math.round(current.monthlyBudget) + " €" : "") : "") +
				"\nSe ajusta sola con lo que comes, tu peso y tus preferencias.");
		card.setTag("nutrition_diet");
		if (planner.busy || plan == null)
			return;
		MaterialButton toggle = action(card, planner.expanded ? "Ocultar menú ▴" : "Ver menú del día ▾", true, () -> {
			planner.expanded = !planner.expanded;
			render();
		});
		toggle.setTag("diet_toggle");
		toggle.setContentDescription(toggle.getText() + (planner.expanded ? ", desplegado" : ", plegado"));
		if (!planner.expanded)
			return;
		// Día del mes: anterior / día / siguiente.
		LinearLayout nav = columns(card, 16);
		LocalDate first = planner.days().isEmpty() ? day : planner.days().firstKey();
		MaterialButton back = compact(nav, "‹", day.isAfter(first), () -> planner.select(day.minusDays(1)));
		back.setContentDescription("Día anterior");
		back.setTextSize(22);
		MaterialButton label = compact(nav, label(day, n.today()), !day.equals(n.today()), () -> planner.select(n.today()));
		label.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.white));
		((LinearLayout.LayoutParams) label.getLayoutParams()).weight = 3;
		MaterialButton forward = compact(nav, "›", day.isBefore(month.atEndOfMonth()), () -> planner.select(day.plusDays(1)));
		forward.setContentDescription("Día siguiente");
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
			text(card, "P " + Math.round(meal.protein) + " g · H " + Math.round(meal.carbs) + " g · G " +
				Math.round(meal.fat) + " g", R.style.EnerGym_FieldCaption, 4);
			if (today)
				action(card, "Anotar en mi diario", !n.busy, () -> n.logPlanned(meal, () -> { })).setTag("diet_log_" + i);
		}
		if (!plan.advice.isEmpty())
			text(card, "💡 " + plan.advice, R.style.EnerGym_Body, 20);
		action(card, plan.source.startsWith("Creado con IA") ? "🔄 Otra propuesta con IA" : "✦ Mejorar este día con IA",
			!n.busy, () -> planner.create(current)).setTag("diet_again");
		text(card, "Menú orientativo; no sustituye a un dietista. Cambia tus preferencias en Datos físicos.",
			R.style.EnerGym_FieldCaption, 8);
	}

	/** Agua del día: vasos de 250 ml frente a un objetivo según el peso. */
	private void water(NutritionController n, LocalDate day) {
		com.energym.app.nutrition.WaterLog w = state.water;
		if (w == null)
			return;
		int ml = w.ml(day), goal = com.energym.app.nutrition.WaterLog.goal(n.effectiveBody());
		int glasses = ml / com.energym.app.nutrition.WaterLog.GLASS, target = goal / com.energym.app.nutrition.WaterLog.GLASS;
		LinearLayout card = card("💧 Agua · " + com.energym.app.nutrition.WaterLog.liters(ml) + " de " +
			com.energym.app.nutrition.WaterLog.liters(goal), ml >= goal ? "¡Objetivo cumplido! 🎉" :
			glasses + " de " + target + (target == 1 ? " vaso" : " vasos"));
		card.setTag("nutrition_water");
		add(card, new com.energym.app.ui.charts.ProgressBarView(requireContext()), 12)
			.set(goal == 0 ? 0 : Math.min(1, ml / (double) goal), "Agua: " + ml + " de " + goal + " ml");
		LinearLayout buttons = columns(card, 16);
		MaterialButton less = compact(buttons, "− Vaso", ml > 0, () -> w.add(day, -com.energym.app.nutrition.WaterLog.GLASS));
		less.setContentDescription("Quitar un vaso de agua");
		MaterialButton more = compact(buttons, "+ Vaso", true, () -> w.add(day, com.energym.app.nutrition.WaterLog.GLASS));
		more.setContentDescription("Añadir un vaso de agua de 250 mililitros");
		primary(more);
	}

	/** Objetivo confirmado y visible; el formulario mantiene su propio borrador hasta pulsar Guardar. */
	private void dailyGoal(NutritionController n, BodyProfile.Targets targets) {
		BodyProfile current = n.effectiveBody();
		LocalDate weightDay = n.targetWeightDay();
		LinearLayout goal = card("Tu objetivo diario", n.body == null ?
			"Completa tus datos para calcular tus calorías y macronutrientes." :
			targets == null ? current.estimationIssue() : current.goal.label + "\nCalculado con " +
				NutritionController.format(current.weightKg) + " kg · " + (weightDay == null ? "fecha no registrada" :
					weightDay.format(DateTimeFormatter.ofPattern("d/M/yyyy", SPANISH))));
		if (targets != null) {
			TextView kcal = text(goal, Math.round(targets.kcal) + " kcal/día", R.style.EnerGym_StatValue, 4);
			kcal.setTag("nutrition_daily_target_kcal");
			LinearLayout macros = columns(goal, 16);
			stat(macros, Math.round(targets.protein) + " g", "Proteínas");
			stat(macros, Math.round(targets.carbs) + " g", "Hidratos");
			stat(macros, Math.round(targets.fat) + " g", "Grasas");
		}
		MaterialButton edit = action(goal, n.body == null ? "Configurar mi objetivo" : "Cambiar objetivo", !n.busy, () -> open(Screen.BODY));
		edit.setTag("nutrition_change_goal");
		if (targets == null) primary(edit);
		action(goal, "Consultar mi alimentación con IA", state.assistant != null && !state.assistant.busy, () -> {
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
		LinearLayout card = card("Hoy te quedan", "");
		// Se redondea cada término antes de operar para que la cuenta visible cuadre (2451 − 195 + 0 = 2256).
		long goal = Math.round(targets.kcal), food = Math.round(totals[0]), exercise = Math.round(exerciseKcal(n, day));
		long left = goal - food + exercise;
		LinearLayout equation = columns(card, 4);
		term(equation, goal, "Objetivo", false);
		operator(equation, "−");
		term(equation, food, "Comida", false);
		operator(equation, "+");
		term(equation, exercise, "Ejercicio", false);
		operator(equation, "=");
		term(equation, left, "Restantes", true);
		equation.setContentDescription("Objetivo " + goal + " menos comida " + food + " más ejercicio " + exercise +
			" igual a " + left + " calorías restantes");
		macro(card, "Hidratos", totals[2], targets.carbs, 0);
		macro(card, "Proteínas", totals[1], targets.protein, 1);
		macro(card, "Grasas", totals[3], targets.fat, 2);
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
		((TextView) row.findViewById(R.id.food_name)).setText(e.name);
		((TextView) row.findViewById(R.id.food_detail)).setText(NutritionController.format(e.amount) + " " +
			e.unit.symbol + " · P " + one(e.protein()) + " · H " + one(e.carbs()) + " · G " + one(e.fat()));
		((TextView) row.findViewById(R.id.food_kcal)).setText(Long.toString(Math.round(e.kcal())));
		row.setContentDescription(e.name + ", " + NutritionController.format(e.amount) + " " + e.unit.symbol + ", " +
			Math.round(e.kcal()) + " kilocalorías. Toca para ver opciones.");
		long scope = n.scopeRevision();
		row.setTag("food_options_" + e.id);
		row.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(e.name)
			.setItems(new String[]{"Editar alimento", "Añadir otra vez", "Eliminar del diario"}, (dialog, which) -> {
				if (n.busy || scope != n.scopeRevision()) return;
				if (which == 0) {
					n.edit(e);
					app.navigate(Screen.FOOD);
				} else if (which == 1) {
					n.startFrom(e);
					app.navigate(Screen.FOOD);
				} else new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
					.setTitle("¿Eliminar este alimento?")
					.setMessage(e.name + " se quitará del diario y se recalcularán tus calorías y macronutrientes.")
					.setNegativeButton("Cancelar", null)
					.setPositiveButton("Eliminar", (confirm, button) -> {
						if (scope == n.scopeRevision()) n.remove(e);
					}).show();
			}).setNegativeButton("Cancelar", null).show());
		section.addView(row);
		View divider = new View(requireContext());
		divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.divider));
		section.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
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
			f.app.error("No se pudo abrir el escáner (necesita Google Play services actualizado). " +
				"Puedes introducir el alimento manualmente.");
		});
	}

	static String label(LocalDate day, LocalDate today) {
		if (day.equals(today))
			return "Hoy";
		if (day.equals(today.minusDays(1)))
			return "Ayer";
		String text = day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH));
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static long[] round(double[] values) {
		long[] result = new long[values.length];
		for (int i = 0; i < values.length; i++)
			result[i] = Math.round(values[i]);
		return result;
	}
}
