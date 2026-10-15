package com.energym.app.ui;

import android.widget.LinearLayout;

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
		LinearLayout summary = card("Resumen del día", targets == null ?
			"Completa tus datos físicos para calcular tus objetivos de calorías y macronutrientes." : balance(n, totals));
		RingView kcal = new RingView(requireContext());
		add(summary, kcal, 8).set("Calorías", totals[0], targets == null ? 0 : targets.kcal, "kcal", kcal.gold(), 12);
		LinearLayout macros = columns(summary, 16);
		RingView protein = column(macros, new RingView(requireContext()), 4);
		protein.set("Proteínas", totals[1], targets == null ? 0 : targets.protein, "g", protein.protein(), 7);
		RingView carbs = column(macros, new RingView(requireContext()), 4);
		carbs.set("Hidratos", totals[2], targets == null ? 0 : targets.carbs, "g", carbs.carbs(), 7);
		RingView fat = column(macros, new RingView(requireContext()), 4);
		fat.set("Grasas", totals[3], targets == null ? 0 : targets.fat, "g", fat.fat(), 7);
		if (targets == null)
			primary(action(summary, "Completar mis datos", true, () -> open(Screen.BODY)));

		LinearLayout add = card("Añadir alimento", n.status);
		MaterialButton scan = action(add, "Escanear código de barras o QR", !n.busy, this::scan);
		primary(scan);
		action(add, "Introducir manualmente", !n.busy, () -> {
			n.startManual();
			app.navigate(Screen.FOOD);
		});

		List<FoodEntry> entries = n.entriesOn(day);
		if (entries.isEmpty())
			card("Diario", "Aún no hay alimentos este día.");
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
			LinearLayout list = card(MEALS[meal.ordinal()] + " · " + Math.round(mealKcal) + " kcal", "");
			for (FoodEntry e : items) {
				text(list, e.name, R.style.EnerGym_CardTitle, 12).setTextSize(16);
				text(list, NutritionController.format(e.amount) + " " + e.unit.symbol + " · " + Math.round(e.kcal()) +
					" kcal · P " + NutritionController.format(Math.round(e.protein() * 10) / 10.0) + " g · H " +
					NutritionController.format(Math.round(e.carbs() * 10) / 10.0) + " g · G " +
					NutritionController.format(Math.round(e.fat() * 10) / 10.0) + " g", R.style.EnerGym_Body, 4);
				MaterialButton remove = action(list, "Quitar", !n.busy, () -> n.remove(e));
				remove.setMinHeight(dp(44));
				remove.setContentDescription("Quitar " + e.name);
				((LinearLayout.LayoutParams) remove.getLayoutParams()).topMargin = dp(8);
			}
		}

		LinearLayout week = card("Últimos 7 días", "Calorías por día" + (targets == null ? "." :
			"; la línea discontinua marca tu objetivo de " + Math.round(targets.kcal) + " kcal."));
		double[] series = n.caloriesSeries(7);
		add(week, new BarChartView(requireContext()), 8).set(series, n.dayLabels(7), targets == null ? 0 : targets.kcal,
			"Calorías de los últimos 7 días: " + Arrays.toString(round(series)));

		LinearLayout body = card("Peso y datos físicos", n.body == null ? "Sin datos físicos." :
			"Peso actual " + NutritionController.format(n.body.weightKg) + " kg · mantenimiento estimado " +
				Math.round(n.body.maintenance()) + " kcal/día" +
				(n.body.allergies.isEmpty() ? "" : "\nAlergias declaradas: " + n.body.allergies));
		String[] typed = {""};
		textField(body, "weight", "Peso de hoy (kg)", "", true, false, v -> typed[0] = v);
		action(body, "Anotar peso", !n.busy, () -> n.logWeight(typed[0]));
		action(body, n.body == null ? "Completar mis datos" : "Editar mis datos", true, () -> open(Screen.BODY));

		text(rows, "Datos de Open Food Facts (ODbL): revisa la etiqueta. Objetivos estimados; no sustituyen a un " +
			"dietista o médico.", R.style.EnerGym_FieldCaption, 4);
	}

	private void open(Screen screen) {
		state.nutrition.fillBody();
		app.navigate(screen);
	}

	private void scan() {
		GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
			.setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
				Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX).enableAutoZoom().build();
		GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(requireActivity(), options);
		scanner.startScan().addOnSuccessListener(barcode -> {
			if (state.nutrition.scanned(barcode.getRawValue()))
				app.navigate(Screen.FOOD);
		}).addOnFailureListener(e -> {
			app.error("No se pudo abrir el escáner (necesita Google Play services actualizado). " +
				"Puedes introducir el alimento manualmente.");
		});
	}

	private static String balance(NutritionController n, double[] totals) {
		double maintenance = n.body.maintenance();
		double diff = totals[0] - maintenance;
		String today = totals[0] == 0 ? "Sin registros todavía." : Math.abs(diff) < 50 ?
			"En equilibrio con tu mantenimiento estimado." :
			(diff < 0 ? "Déficit de " : "Superávit de ") + Math.round(Math.abs(diff)) + " kcal frente a tu mantenimiento.";
		Double week = n.weeklyBalance();
		return today + (week == null ? "" : "\nMedia 7 días: " + (week < 0 ? "déficit " : "superávit ") +
			Math.round(Math.abs(week)) + " kcal/día.");
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
