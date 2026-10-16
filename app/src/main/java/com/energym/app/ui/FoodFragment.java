package com.energym.app.ui;

import android.widget.LinearLayout;
import android.widget.TextView;

import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;
import com.energym.app.nutrition.*;

import java.util.*;

/**
 * VISTA para anotar un alimento: ficha escaneada o manual, cantidad consumida y vista previa en vivo.
 * Con ficha escaneada la unidad sigue la etiqueta (g o ml); no se convierte volumen en peso.
 */
public final class FoodFragment extends ScreenFragment {
	/** Texto de la búsqueda de alimentos habituales; sobrevive a los repintados de esta pantalla. */
	private String commonQuery = "";

	public FoodFragment() {
		super(R.layout.fragment_screen);
	}

	protected void content() {
		NutritionController n = state.nutrition;
		if (n == null || app.owner() == null) {
			heading("NUTRICIÓN", "Añadir alimento", "");
			card("Entra para empezar", "");
			return;
		}
		heading("NUTRICIÓN · " + NutritionFragment.label(n.selected, n.today()).toUpperCase(NutritionFragment.SPANISH),
			n.editingFood() ? "Editar alimento" : n.product == null ? "Añadir alimento" : n.product.name, n.status);
		if (n.looking) {
			card("Buscando la ficha…", "Consultando Open Food Facts con el código escaneado.");
			return;
		}
		Map<String, String> d = n.foodDraft;
		FoodProduct p = n.product;
		if (!n.editingFood() && p == null && d.getOrDefault("barcode", "").isEmpty() && d.getOrDefault("name", "").isEmpty())
			recents(n);
		if (!n.editingFood())
			alreadyInMeal(n, d);
		FoodEntry.Unit unit = FoodEntry.Unit.valueOf(d.getOrDefault("unit", "G"));
		if (p != null)
			card(p.displayName(), (p.quantity.isEmpty() ? "" : "Envase: " + p.quantity + "\n") + "Código " + p.barcode +
				"\nValores por 100 " + unit.symbol + " según la ficha. Comprueba que coinciden con tu etiqueta.");

		LinearLayout amount = card("¿Cuánto has tomado?", "");
		TextView[] preview = new TextView[1];
		Runnable refresh = () -> {
			if (preview[0] == null)
				return;
			FoodEntry e = n.preview();
			// Se recalcula con cada cifra que escribes: lo que vas a tomar, no los valores por 100.
			preview[0].setText(e == null ? "Escribe la cantidad para ver sus calorías y macros." :
				"Con " + NutritionController.format(e.amount) + " " + e.unit.symbol + ": " + Math.round(e.kcal()) +
					" kcal\nProteínas " + NutritionController.format(round(e.protein())).replace('.', ',') +
					" g\nHidratos " + NutritionController.format(round(e.carbs())).replace('.', ',') +
					" g\nGrasas " + NutritionController.format(round(e.fat())).replace('.', ',') + " g");
		};
		textField(amount, "amount", "Cantidad (" + unit.symbol + ")", d.getOrDefault("amount", ""), true, false, v -> {
			d.put("amount", v);
			refresh.run();
		});
		String pack = p == null ? "" : packAmount(p.quantity, unit);
		if (!pack.isEmpty())
			compact(columns(amount, 4), "Envase entero (" + pack + " " + unit.symbol + ")", !n.busy, () -> {
				d.put("amount", pack);
				render();
			}).setTag("food_whole_pack");
		choice(amount, "meal", "Comida", Arrays.asList("BREAKFAST", "LUNCH", "SNACK", "DINNER", "OTHER"),
			Arrays.asList("Desayuno", "Comida", "Merienda", "Cena", "Otros"), d.getOrDefault("meal", "OTHER"),
			v -> d.put("meal", v));
		preview[0] = text(amount, "", R.style.EnerGym_CardTitle, 4);
		preview[0].setTextSize(16);
		preview[0].setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);

		if (p != null) {
			scannedValues(n, d, unit, refresh);
			finish(n, d);
			return;
		}
		LinearLayout values = card("Datos de la etiqueta", "Copia los valores «por 100 g» o «por 100 ml» de la etiqueta.");
		com.google.android.material.button.MaterialButton[] reuse = new com.google.android.material.button.MaterialButton[1];
		textField(values, "name", "Nombre del alimento", d.getOrDefault("name", ""), false, false, v -> {
			d.put("name", v);
			refresh.run();
			if (reuse[0] != null)
				suggest(n, reuse[0], v);
		});
		// Autocompletar: si ya anotaste este alimento, sus valores de etiqueta se reutilizan con un toque.
		if (p == null && !n.editingFood()) {
			reuse[0] = action(values, "", !n.busy, () -> {
				FoodEntry known = n.known(d.getOrDefault("name", ""));
				if (known != null) {
					n.useLabelOf(known);
					app.changed();
				}
			});
			suggest(n, reuse[0], d.getOrDefault("name", ""));
		}
		choice(values, "unit", "La etiqueta indica valores por", Arrays.asList("G", "ML"),
			Arrays.asList("100 g (sólidos)", "100 ml (líquidos)"), unit.name(), v -> {
				d.put("unit", v);
				app.changed();
			});
		if (p != null)
			text(values, "Comprueba la base en la etiqueta. Cambiar g/ml no convierte la cantidad ni los valores.",
				R.style.EnerGym_FieldCaption, 0);
		String base = " por 100 " + unit.symbol;
		field(values, d, "kcal100", "Calorías (kcal)" + base, refresh);
		field(values, d, "protein100", "Proteínas (g)" + base, refresh);
		field(values, d, "carbs100", "Hidratos de carbono (g)" + base, refresh);
		field(values, d, "fat100", "Grasas (g)" + base, refresh);
		refresh.run();
		if (!n.editingFood()) {
			LinearLayout more = new LinearLayout(requireContext());
			more.setOrientation(LinearLayout.HORIZONTAL);
			more.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
			TextView hint = new TextView(requireContext());
			hint.setTextAppearance(R.style.EnerGym_FieldCaption);
			hint.setText("Guardar y añadir otro");
			hint.setGravity(android.view.Gravity.END);
			more.addView(hint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
			com.google.android.material.button.MaterialButton plus = new com.google.android.material.button.MaterialButton(
				requireContext(), null, com.google.android.material.R.attr.materialIconButtonFilledStyle);
			plus.setText("+");
			plus.setTextSize(26);
			plus.setPadding(0, 0, 0, 0);
			plus.setInsetTop(0);
			plus.setInsetBottom(0);
			plus.setCornerRadius(dp(28));
			plus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
				androidx.core.content.ContextCompat.getColor(requireContext(), R.color.gold)));
			plus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surface));
			plus.setEnabled(!n.busy);
			plus.setTag("food_add_another");
			plus.setContentDescription("Guardar este alimento y añadir otro a la misma comida");
			plus.setOnClickListener(v -> {
				FoodEntry.Meal meal = FoodEntry.Meal.valueOf(d.getOrDefault("meal", "OTHER"));
				n.addFood(() -> {
					n.startManual(meal);
					app.changed();
					if (getView() instanceof android.widget.ScrollView)
						getView().post(() -> ((android.widget.ScrollView) getView()).smoothScrollTo(0, 0));
				});
			});
			LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(56), dp(56));
			size.setMarginStart(dp(12));
			more.addView(plus, size);
			values.addView(more, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT) {{ topMargin = dp(16); }});
		}
		finish(n, d);
	}

	private void finish(NutritionController n, Map<String, String> d) {
		primary(action(rows, n.editingFood() ? "Guardar cambios" : "Añadir al diario", !n.busy,
			() -> n.addFood(() -> app.navigate(Screen.NUTRITION))));
		action(rows, "Cancelar", !n.busy, () -> {
			n.cancelFood();
			app.navigate(Screen.NUTRITION);
		});
	}

	/**
	 * Producto escaneado: sus valores por 100 vienen de la ficha y no se tocan; solo se piden los que la ficha no
	 * trae. Así la persona únicamente indica cuánto ha tomado.
	 */
	private void scannedValues(NutritionController n, Map<String, String> d, FoodEntry.Unit unit, Runnable refresh) {
		String base = " por 100 " + unit.symbol;
		String[][] fields = {{"kcal100", "Calorías", "kcal"}, {"protein100", "Proteínas", "g"},
			{"carbs100", "Hidratos", "g"}, {"fat100", "Grasas", "g"}};
		StringBuilder fixed = new StringBuilder();
		boolean missing = false;
		for (String[] f : fields) {
			String v = n.scannedValue(f[0]);
			if (v != null)
				fixed.append(fixed.length() == 0 ? "" : "\n").append(f[1]).append(": ").append(v.replace('.', ','))
					.append(" ").append(f[2]);
			else
				missing = true;
		}
		LinearLayout values = card("Valores" + base, (fixed.length() == 0 ? "" : fixed + "\n\n") +
			"Datos de Open Food Facts para este código. Solo tienes que indicar la cantidad.");
		values.setTag("food_scanned_values");
		if (!missing)
			return;
		text(values, "A la ficha le faltan estos datos: cópialos de la etiqueta.", R.style.EnerGym_FieldCaption, 8);
		for (String[] f : fields)
			if (n.scannedValue(f[0]) == null)
				field(values, d, f[0], f[1] + " (" + f[2] + ")" + base, refresh);
	}

	/** Cantidad del envase en la unidad de la ficha («500 g», «1 l» → 1000 ml); vacío si no se entiende. */
	static String packAmount(String quantity, FoodEntry.Unit unit) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|g|ml|cl|l)\\b")
			.matcher(quantity == null ? "" : quantity.toLowerCase(java.util.Locale.ROOT));
		if (!m.find())
			return "";
		double v = Double.parseDouble(m.group(1).replace(',', '.'));
		switch (m.group(2)) {
			case "kg": case "l": v *= 1000; break;
			case "cl": v *= 10; break;
			default: break;
		}
		boolean volume = m.group(2).endsWith("l");
		if (volume != (unit == FoodEntry.Unit.ML) || v <= 0 || v > 5000)
			return "";
		return NutritionController.format(v);
	}

	/**
	 * Atajos: escanear o repetir un alimento reciente con su ficha ya rellena.
	 */
	private void recents(NutritionController n) {
		primary(action(rows, "Escanear código de barras", !n.busy, () -> NutritionFragment.scan(this)));
		common(n);
		List<FoodEntry> recent = n.recentFoods(8);
		if (recent.isEmpty())
			return;
		LinearLayout list = card("Recientes", "");
		for (FoodEntry e : recent) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, list, false);
			((TextView) row.findViewById(R.id.food_name)).setText(e.name);
			((TextView) row.findViewById(R.id.food_detail)).setText(NutritionController.format(e.amount) + " " +
				e.unit.symbol + " · " + NutritionController.format(e.kcal100) + " kcal/100 " + e.unit.symbol);
			((TextView) row.findViewById(R.id.food_kcal)).setText("+");
			row.setContentDescription("Añadir de nuevo " + e.name);
			row.setOnClickListener(v -> {
				n.startFrom(e);
				app.changed();
			});
			list.addView(row);
		}
	}

	/** Búsqueda en los alimentos genéricos de la app: un toque rellena valores y ración. */
	private void common(NutritionController n) {
		LinearLayout card = card("Alimentos habituales",
			"Busca un alimento básico y se rellenan sus valores orientativos. Si tienes la etiqueta, mejor usa sus datos.");
		LinearLayout results = new LinearLayout(requireContext());
		results.setOrientation(LinearLayout.VERTICAL);
		textField(card, "common_food", "Buscar alimento", commonQuery, false, false, "Ej.: arroz, pollo, plátano", "", v -> {
			commonQuery = v;
			showCommon(n, results);
		});
		card.addView(results);
		showCommon(n, results);
	}

	private void showCommon(NutritionController n, LinearLayout results) {
		results.removeAllViews();
		List<CommonFoods.Food> found = CommonFoods.search(commonQuery, 8);
		String q = commonQuery.trim();
		if (found.isEmpty() && q.length() >= 2)
			text(results, "No está en la lista de habituales.", R.style.EnerGym_FieldCaption, 4);
		for (CommonFoods.Food f : found) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, results, false);
			((TextView) row.findViewById(R.id.food_name)).setText(f.name);
			((TextView) row.findViewById(R.id.food_detail)).setText("Ración " + NutritionController.format(f.portion) + " " +
				f.unit.symbol + " · " + NutritionController.format(f.kcal100) + " kcal/100 " + f.unit.symbol);
			((TextView) row.findViewById(R.id.food_kcal)).setText("+");
			row.setContentDescription("Usar " + f.name);
			row.setOnClickListener(v -> {
				commonQuery = "";
				n.startCommon(f);
				app.changed();
			});
			results.addView(row);
		}
		online(n, results, q);
	}

	/**
	 * Productos de marca de Open Food Facts por nombre (bajo petición: solo se envía el texto buscado). Al elegir
	 * uno, sus valores quedan fijos como al escanear y solo se indica la cantidad.
	 */
	private void online(NutritionController n, LinearLayout results, String q) {
		if (q.length() < 3)
			return;
		boolean mine = q.equals(n.onlineQuery);
		if (mine && !n.onlineResults.isEmpty()) {
			text(results, "En Open Food Facts (productos de marca):", R.style.EnerGym_FieldCaption, 12);
			for (FoodProduct p : n.onlineResults) {
				android.view.View row = getLayoutInflater().inflate(R.layout.row_food, results, false);
				((TextView) row.findViewById(R.id.food_name)).setText(p.displayName());
				((TextView) row.findViewById(R.id.food_detail)).setText((p.quantity.isEmpty() ? "" : p.quantity + " · ") +
					NutritionController.format(p.kcal100) + " kcal/100 " + p.unit.symbol);
				((TextView) row.findViewById(R.id.food_kcal)).setText("+");
				row.setContentDescription("Usar " + p.displayName());
				row.setOnClickListener(v -> {
					commonQuery = "";
					n.useProduct(p);
					app.changed();
				});
				results.addView(row);
			}
			return;
		}
		com.google.android.material.button.MaterialButton search = action(results,
			n.searchingOnline && mine ? "Buscando en Open Food Facts…" : "🔎 Buscar «" + q + "» en Open Food Facts",
			!n.searchingOnline && !n.busy, () -> n.searchOnline(q));
		search.setTag("food_search_online");
	}

	private static void suggest(NutritionController n, android.widget.Button button, String typed) {
		FoodEntry known = n.known(typed);
		boolean same = known != null && known.name.equalsIgnoreCase(typed.trim()) &&
			NutritionController.format(known.kcal100).equals(n.foodDraft.getOrDefault("kcal100", ""));
		button.setVisibility(known == null || same ? android.view.View.GONE : android.view.View.VISIBLE);
		if (known != null)
			button.setText("↺ Usar «" + known.name + "» (" + NutritionController.format(known.kcal100) + " kcal/100 " +
				known.unit.symbol + ")");
	}

	/** Alimentos ya anotados en esta comida del día: se ve lo que se lleva y se añaden más con «+». */
	private void alreadyInMeal(NutritionController n, Map<String, String> d) {
		FoodEntry.Meal meal = FoodEntry.Meal.valueOf(d.getOrDefault("meal", "OTHER"));
		List<FoodEntry> added = new ArrayList<>();
		for (FoodEntry e : n.entriesOn(n.selected))
			if (e.meal == meal)
				added.add(e);
		if (added.isEmpty())
			return;
		double kcal = 0;
		StringBuilder list = new StringBuilder();
		for (FoodEntry e : added) {
			kcal += e.kcal();
			list.append(list.length() == 0 ? "" : "\n").append("✓ ").append(e.name).append(" · ")
				.append(NutritionController.format(e.amount)).append(" ").append(e.unit.symbol).append(" · ")
				.append(Math.round(e.kcal())).append(" kcal");
		}
		LinearLayout card = card("Ya en " + MEALS_LABEL[meal.ordinal()] + " · " + Math.round(kcal) + " kcal", list.toString());
		card.setTag("food_meal_so_far");
	}

	private static final String[] MEALS_LABEL = {"desayuno", "comida", "merienda", "cena", "otros"};

	private void field(LinearLayout target, Map<String, String> d, String key, String label, Runnable refresh) {
		textField(target, key, label, d.getOrDefault(key, ""), true, false, v -> {
			d.put(key, v);
			refresh.run();
		});
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}
}
