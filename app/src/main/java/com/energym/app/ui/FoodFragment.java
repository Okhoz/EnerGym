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
			n.product == null ? "Añadir alimento" : n.product.name, n.status);
		if (n.looking) {
			card("Buscando la ficha…", "Consultando Open Food Facts con el código escaneado.");
			return;
		}
		Map<String, String> d = n.foodDraft;
		FoodProduct p = n.product;
		if (p == null && d.getOrDefault("barcode", "").isEmpty() && d.getOrDefault("name", "").isEmpty())
			recents(n);
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
			preview[0].setText(e == null ? "Completa cantidad y valores para ver el cálculo." :
				Math.round(e.kcal()) + " kcal · Proteínas " + NutritionController.format(round(e.protein())) +
					" g · Hidratos " + NutritionController.format(round(e.carbs())) + " g · Grasas " +
					NutritionController.format(round(e.fat())) + " g");
		};
		textField(amount, "amount", "Cantidad (" + unit.symbol + ")", d.getOrDefault("amount", ""), true, false, v -> {
			d.put("amount", v);
			refresh.run();
		});
		choice(amount, "meal", "Comida", Arrays.asList("BREAKFAST", "LUNCH", "SNACK", "DINNER", "OTHER"),
			Arrays.asList("Desayuno", "Comida", "Merienda", "Cena", "Otros"), d.getOrDefault("meal", "OTHER"),
			v -> d.put("meal", v));
		preview[0] = text(amount, "", R.style.EnerGym_CardTitle, 4);
		preview[0].setTextSize(16);
		preview[0].setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);

		LinearLayout values = card(p == null ? "Datos de la etiqueta" : "Valores nutricionales",
			p != null && p.complete() ? "Puedes corregirlos si tu envase indica otra cosa." :
				"Copia los valores «por 100 g» o «por 100 ml» de la etiqueta.");
		textField(values, "name", "Nombre del alimento", d.getOrDefault("name", ""), false, false, v -> {
			d.put("name", v);
			refresh.run();
		});
		if (p == null)
			choice(values, "unit", "La etiqueta indica valores por", Arrays.asList("G", "ML"),
				Arrays.asList("100 g (sólidos)", "100 ml (líquidos)"), unit.name(), v -> {
					d.put("unit", v);
					app.changed();
				});
		String base = " por 100 " + unit.symbol;
		field(values, d, "kcal100", "Calorías (kcal)" + base, refresh);
		field(values, d, "protein100", "Proteínas (g)" + base, refresh);
		field(values, d, "carbs100", "Hidratos de carbono (g)" + base, refresh);
		field(values, d, "fat100", "Grasas (g)" + base, refresh);
		refresh.run();
		primary(action(rows, "Añadir al diario", !n.busy, () -> n.addFood(() -> app.navigate(Screen.NUTRITION))));
	}

	/**
	 * Atajos: escanear o repetir un alimento reciente con su ficha ya rellena.
	 */
	private void recents(NutritionController n) {
		primary(action(rows, "Escanear código de barras o QR", !n.busy, () -> NutritionFragment.scan(this)));
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
