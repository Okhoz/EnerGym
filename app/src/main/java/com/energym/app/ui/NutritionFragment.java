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
		dailyGoal(n, targets);
		diet(n, targets);
		remaining(n, day, totals, targets);

		// Diario por comidas, como en un registro de calorías: cada comida con su total y su botón de añadir.
		List<FoodEntry> entries = n.entriesOn(day);
		for (FoodEntry.Meal meal : FoodEntry.Meal.values()) {
			List<FoodEntry> items = new ArrayList<>();
			double mealKcal = 0;
			for (FoodEntry e : entries)
				if (e.meal == meal) {
					items.add(e);
					mealKcal += e.kcal();
				}
			if (items.isEmpty() && meal == FoodEntry.Meal.OTHER)
				continue;
			LinearLayout section = card(MEALS[meal.ordinal()] + (items.isEmpty() ? "" : "   " + Math.round(mealKcal) +
				" kcal"), "");
			for (FoodEntry e : items)
				foodRow(section, e, n);
			LinearLayout buttons = columns(section, items.isEmpty() ? 0 : 8);
			MaterialButton addFood = compact(buttons, "+ Añadir alimento", !n.busy, () -> {
				n.startManual(meal);
				app.navigate(Screen.FOOD);
			});
			addFood.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold));
			addFood.setContentDescription("Añadir alimento a " + MEALS[meal.ordinal()]);
			((LinearLayout.LayoutParams) addFood.getLayoutParams()).weight = 1.4f;
			MaterialButton scanFood = compact(buttons, "Escanear", !n.busy, () -> {
				n.startManual(meal);
				scan(this);
			});
			scanFood.setContentDescription("Escanear código de barras para " + MEALS[meal.ordinal()]);
		}
		if (!n.status.isEmpty())
			text(rows, n.status, R.style.EnerGym_FieldCaption, 0);

		LinearLayout week = card("Últimos 7 días", "Calorías por día" + (targets == null ? "." :
			"; la línea discontinua marca tu objetivo de " + Math.round(targets.kcal) + " kcal."));
		double[] series = n.caloriesSeries(7);
		add(week, new BarChartView(requireContext()), 8).set(series, n.dayLabels(7), targets == null ? 0 : targets.kcal,
			"Calorías de los últimos 7 días: " + Arrays.toString(round(series)));

		BodyProfile current = n.effectiveBody();
		LinearLayout body = card("Peso y datos físicos", current == null ? "Sin datos físicos." :
			"Peso actual " + NutritionController.format(current.weightKg) + " kg" + (targets == null ? "" :
				" · mantenimiento estimado " + Math.round(current.maintenance()) + " kcal/día") +
				(n.body.allergies.isEmpty() ? "" : "\nAlergias declaradas: " + n.body.allergies));
		String[] typed = {""};
		textField(body, "weight", "Peso de hoy (kg)", "", true, false, v -> typed[0] = v);
		action(body, "Anotar peso", !n.busy, () -> n.logWeight(typed[0]));
		action(body, n.body == null ? "Completar mis datos" : "Editar mis datos", true, () -> open(Screen.BODY));

		text(rows, "Datos de Open Food Facts (ODbL): revisa la etiqueta. Objetivos estimados; no sustituyen a un " +
			"dietista o médico.", R.style.EnerGym_FieldCaption, 4);
	}

	private static String euros(double value) {
		return String.format(SPANISH, "%.2f €", value);
	}

	/** Dieta personalizada con la IA del móvil, a partir de los datos físicos y preferencias guardados. */
	private void diet(NutritionController n, BodyProfile.Targets targets) {
		com.energym.app.nutrition.DietPlanner planner = state.diet;
		if (planner == null || n.body == null || targets == null)
			return;
		com.energym.app.nutrition.DietPlan plan = planner.plan;
		BodyProfile current = n.effectiveBody();
		LinearLayout card = card("✦ Tu dieta con IA", planner.busy ? "Preparando tu menú en el móvil…" :
			plan == null ? "Un menú de un día con tus " + Math.round(targets.kcal) + " kcal, " +
				current.mealsPerDay + " comidas y tu alimentación " + current.diet.label.toLowerCase(SPANISH) +
				(current.budget > 0 ? ", con un presupuesto de " + euros(current.budget) + " al día" : "") +
				". Se tienen en cuenta tus alergias y lo que no tomas." :
				plan.source + " · " + Math.round(plan.totals()[0]) + " de " + Math.round(targets.kcal) + " kcal" +
					(plan.cost() >= 0 ? " · ≈ " + euros(plan.cost()) + (current.budget > 0 ? " de " + euros(current.budget) : "") +
						" al día" : ""));
		card.setTag("nutrition_diet");
		if (planner.busy)
			return;
		if (plan == null) {
			primary(action(card, "Crear mi dieta con IA", !n.busy, () -> planner.create(current)));
			return;
		}
		// Desplegable: plegado solo ocupa el resumen y el botón para ver el menú.
		MaterialButton toggle = action(card, planner.expanded ? "Ocultar menú ▴" : "Ver menú de " + plan.meals.size() +
			" comidas ▾", true, () -> {
			planner.expanded = !planner.expanded;
			render();
		});
		toggle.setTag("diet_toggle");
		toggle.setContentDescription(toggle.getText() + (planner.expanded ? ", desplegado" : ", plegado"));
		if (!planner.expanded)
			return;
		if (!planner.status.isEmpty())
			text(card, planner.status, R.style.EnerGym_FieldCaption, 12);
		for (int i = 0; i < plan.meals.size(); i++) {
			com.energym.app.nutrition.DietPlan.Meal meal = plan.meals.get(i);
			TextView title = text(card, meal.icon() + "  " + meal.name + " · " +
				Math.round(meal.kcal) + " kcal" + (meal.cost >= 0 ? " · ≈ " + euros(meal.cost) : ""),
				R.style.EnerGym_CardTitle, i == 0 ? 16 : 24);
			title.setTextSize(17);
			text(card, meal.dish, R.style.EnerGym_Body, 4);
			if (!meal.ingredients.isEmpty())
				text(card, meal.ingredients, R.style.EnerGym_FieldCaption, 4);
			text(card, "P " + Math.round(meal.protein) + " g · H " + Math.round(meal.carbs) + " g · G " +
				Math.round(meal.fat) + " g", R.style.EnerGym_FieldCaption, 4);
			MaterialButton log = action(card, "Anotar en mi diario", !n.busy, () ->
				n.logPlanned(meal, () -> { }));
			log.setTag("diet_log_" + i);
		}
		if (!plan.advice.isEmpty())
			text(card, "💡 " + plan.advice, R.style.EnerGym_Body, 20);
		action(card, "🔄 Otra propuesta", !n.busy, () -> planner.create(current)).setTag("diet_again");
		text(card, "Menú orientativo; no sustituye a un dietista. Cambia tus preferencias en Datos físicos.",
			R.style.EnerGym_FieldCaption, 8);
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
		LinearLayout card = card("Calorías restantes", "");
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
