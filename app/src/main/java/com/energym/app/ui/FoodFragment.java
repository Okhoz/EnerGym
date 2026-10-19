package com.energym.app.ui;

import com.energym.app.i18n.Txt;
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
			heading(Txt.t(R.string.nut_eyebrow), Txt.t(R.string.nut_add_food), "");
			card(Txt.t(R.string.nut_sign_in), "");
			return;
		}
		heading(Txt.t(R.string.nut_eyebrow) + " · " + NutritionFragment.label(n.selected, n.today()).toUpperCase(Txt.locale()),
			n.editingFood() ? Txt.t(R.string.nut_edit_food) : n.product == null ? Txt.t(R.string.nut_add_food) : n.product.name, n.status);
		if (n.looking) {
			card(Txt.t(R.string.food_looking), Txt.t(R.string.food_looking_body));
			return;
		}
		Map<String, String> d = n.foodDraft;
		FoodProduct p = n.product;
		boolean chosen = p != null || !d.getOrDefault("barcode", "").isEmpty() || !d.getOrDefault("name", "").isEmpty();
		if (!n.editingFood() && !chosen)
			recents(n);
		// Con un alimento ya elegido, el buscador sigue arriba: si te has equivocado, eliges otro y sus datos
		// sustituyen a todos los del anterior. «Elegir otro alimento» lo deja todo en blanco.
		if (!n.editingFood() && chosen) {
			common(n, true);
			com.google.android.material.button.MaterialButton other = action(rows, Txt.t(R.string.food_choose_other), !n.busy, () -> {
				commonQuery = "";
				n.changeFood();
				render();
				if (getView() instanceof android.widget.ScrollView)
					getView().post(() -> ((android.widget.ScrollView) getView()).smoothScrollTo(0, 0));
			});
			other.setTag("food_change");
		}
		if (!n.editingFood())
			alreadyInMeal(n, d);
		FoodEntry.Unit unit = FoodEntry.Unit.valueOf(d.getOrDefault("unit", "G"));
		if (p != null)
			card(p.displayName(), (p.quantity.isEmpty() ? "" : Txt.t(R.string.food_pack, p.quantity) + "\n") +
				Txt.t(R.string.food_code_values, p.barcode, unit.symbol));

		LinearLayout amount = card(Txt.t(R.string.food_how_much), "");
		TextView[] preview = new TextView[1];
		Runnable refresh = () -> {
			if (preview[0] == null)
				return;
			FoodEntry e = n.preview();
			// Se recalcula con cada cifra que escribes: lo que vas a tomar, no los valores por 100.
			preview[0].setText(e == null ? Txt.t(R.string.food_type_amount) :
				Txt.t(R.string.food_preview, NutritionController.format(e.amount) + " " + e.unit.symbol, Math.round(e.kcal()),
					NutritionController.format(round(e.protein())),
					NutritionController.format(round(e.carbs())),
					NutritionController.format(round(e.fat()))));
		};
		textField(amount, "amount", Txt.t(R.string.food_amount, unit.symbol), d.getOrDefault("amount", ""), true, false, v -> {
			d.put("amount", v);
			refresh.run();
		});
		String pack = p == null ? "" : packAmount(p.quantity, unit);
		if (!pack.isEmpty())
			compact(columns(amount, 4), Txt.t(R.string.food_whole_pack, pack + " " + unit.symbol), !n.busy, () -> {
				d.put("amount", pack);
				render();
			}).setTag("food_whole_pack");
		choice(amount, "meal", Txt.t(R.string.food_meal), Arrays.asList("BREAKFAST", "LUNCH", "SNACK", "DINNER", "OTHER"),
			Arrays.asList(NutritionFragment.meal(0), NutritionFragment.meal(1), NutritionFragment.meal(2),
				NutritionFragment.meal(3), NutritionFragment.meal(4)), d.getOrDefault("meal", "OTHER"),
			v -> d.put("meal", v));
		preview[0] = text(amount, "", R.style.EnerGym_CardTitle, 4);
		preview[0].setTextSize(16);
		preview[0].setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);

		if (p != null) {
			scannedValues(n, d, unit, refresh);
			finish(n, d);
			return;
		}
		LinearLayout values = card(Txt.t(R.string.food_label_data), Txt.t(R.string.food_label_data_body));
		com.google.android.material.button.MaterialButton[] reuse = new com.google.android.material.button.MaterialButton[1];
		TextView[] stale = new TextView[1];
		textField(values, "name", Txt.t(R.string.food_name), d.getOrDefault("name", ""), false, false, v -> {
			d.put("name", v);
			refresh.run();
			if (reuse[0] != null)
				suggest(n, reuse[0], v);
			staleValues(n, stale[0], v);
		});
		// Si cambias el nombre de un alimento elegido de la lista, sus valores siguen siendo los del anterior: se avisa.
		stale[0] = text(values, "", R.style.EnerGym_FieldCaption, 4);
		stale[0].setTag("food_stale_values");
		stale[0].setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
		staleValues(n, stale[0], d.getOrDefault("name", ""));
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
		choice(values, "unit", Txt.t(R.string.food_label_per), Arrays.asList("G", "ML"),
			Arrays.asList(Txt.t(R.string.food_per_100g), Txt.t(R.string.food_per_100ml)), unit.name(), v -> {
				d.put("unit", v);
				app.changed();
			});
		if (p != null)
			text(values, Txt.t(R.string.food_check_base), R.style.EnerGym_FieldCaption, 0);
		String base = " " + Txt.t(R.string.food_per_100, unit.symbol);
		field(values, d, "kcal100", Txt.t(R.string.food_kcal_field) + base, refresh);
		field(values, d, "protein100", Txt.t(R.string.food_protein_field) + base, refresh);
		field(values, d, "carbs100", Txt.t(R.string.food_carbs_field) + base, refresh);
		field(values, d, "fat100", Txt.t(R.string.food_fat_field) + base, refresh);
		refresh.run();
		finish(n, d);
	}

	private void finish(NutritionController n, Map<String, String> d) {
		primary(action(rows, Txt.t(n.editingFood() ? R.string.food_save_changes : R.string.food_add_to_diary), !n.busy,
			() -> n.addFood(() -> app.navigate(Screen.NUTRITION))));
		if (!n.editingFood()) {
			// Guarda este y deja el formulario listo para otro alimento de la misma comida.
			com.google.android.material.button.MaterialButton another = action(rows, Txt.t(R.string.food_add_another), !n.busy, () -> {
				FoodEntry.Meal meal = FoodEntry.Meal.valueOf(d.getOrDefault("meal", "OTHER"));
				n.addFood(() -> {
					n.startManual(meal);
					app.changed();
					if (getView() instanceof android.widget.ScrollView)
						getView().post(() -> ((android.widget.ScrollView) getView()).smoothScrollTo(0, 0));
				});
			});
			another.setTag("food_add_another");
			another.setContentDescription(Txt.t(R.string.food_add_another_description));
		}
		action(rows, Txt.t(R.string.common_cancel), !n.busy, () -> {
			n.cancelFood();
			app.navigate(Screen.NUTRITION);
		});
	}

	/**
	 * Producto escaneado: sus valores por 100 vienen de la ficha y no se tocan; solo se piden los que la ficha no
	 * trae. Así la persona únicamente indica cuánto ha tomado.
	 */
	private void scannedValues(NutritionController n, Map<String, String> d, FoodEntry.Unit unit, Runnable refresh) {
		String base = " " + Txt.t(R.string.food_per_100, unit.symbol);
		String[][] fields = {{"kcal100", Txt.t(R.string.food_calories), "kcal"}, {"protein100", Txt.t(R.string.nut_protein), "g"},
			{"carbs100", Txt.t(R.string.nut_carbs), "g"}, {"fat100", Txt.t(R.string.nut_fat), "g"}};
		StringBuilder fixed = new StringBuilder();
		boolean missing = false;
		for (String[] f : fields) {
			String v = n.scannedValue(f[0]);
			if (v != null)
				fixed.append(fixed.length() == 0 ? "" : "\n").append(f[1]).append(": ").append(Txt.decimal(v))
					.append(" ").append(f[2]);
			else
				missing = true;
		}
		LinearLayout values = card(Txt.t(R.string.food_values) + base, (fixed.length() == 0 ? "" : fixed + "\n\n") +
			Txt.t(R.string.food_scanned_body));
		values.setTag("food_scanned_values");
		if (!missing)
			return;
		text(values, Txt.t(R.string.food_missing_values), R.style.EnerGym_FieldCaption, 8);
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
		primary(action(rows, Txt.t(R.string.food_scan_barcode), !n.busy, () -> NutritionFragment.scan(this)));
		common(n, false);
		List<FoodEntry> recent = n.recentFoods(8);
		if (recent.isEmpty())
			return;
		LinearLayout list = card(Txt.t(R.string.food_recent), "");
		for (FoodEntry e : recent) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, list, false);
			((TextView) row.findViewById(R.id.food_name)).setText(e.name);
			((TextView) row.findViewById(R.id.food_detail)).setText(NutritionController.format(e.amount) + " " +
				e.unit.symbol + " · " + NutritionController.format(e.kcal100) + " kcal/100 " + e.unit.symbol);
			((TextView) row.findViewById(R.id.food_kcal)).setText("+");
			row.setContentDescription(Txt.t(R.string.food_add_again_named, e.name));
			row.setOnClickListener(v -> {
				n.startFrom(e);
				app.changed();
			});
			list.addView(row);
		}
	}

	/**
	 * Búsqueda en los alimentos genéricos de la app: un toque rellena valores y ración. Con {@code change}, es el
	 * buscador para cambiar el alimento ya elegido por otro.
	 */
	private void common(NutritionController n, boolean change) {
		LinearLayout card = card(Txt.t(change ? R.string.food_change_title : R.string.food_common),
			Txt.t(change ? R.string.food_change_body : R.string.food_common_body));
		card.setTag(change ? "food_change_search" : "food_search");
		LinearLayout results = new LinearLayout(requireContext());
		results.setOrientation(LinearLayout.VERTICAL);
		textField(card, "common_food", Txt.t(R.string.food_search), commonQuery, false, false, Txt.t(R.string.food_search_hint), "", v -> {
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
		// Primero lo que ya anotaste alguna vez (con tu cantidad habitual): suele ser lo que buscas.
		List<String> terms = com.energym.app.domain.TextMatch.terms(q);
		int mine = 0;
		if (!terms.isEmpty())
			for (FoodEntry e : n.recentFoods(Integer.MAX_VALUE)) {
				if (mine == 3 || com.energym.app.domain.TextMatch.matchAll(terms,
					com.energym.app.domain.TextMatch.normalize(e.name)) == com.energym.app.domain.TextMatch.NONE)
					continue;
				if (mine++ == 0)
					text(results, Txt.t(R.string.food_yours), R.style.EnerGym_FieldCaption, 12);
				android.view.View row = getLayoutInflater().inflate(R.layout.row_food, results, false);
				((TextView) row.findViewById(R.id.food_name)).setText(e.name);
				((TextView) row.findViewById(R.id.food_detail)).setText(NutritionController.format(e.amount) + " " +
					e.unit.symbol + " · " + NutritionController.format(e.kcal100) + " kcal/100 " + e.unit.symbol);
				((TextView) row.findViewById(R.id.food_kcal)).setText("+");
				row.setContentDescription(Txt.t(R.string.food_add_again_named, e.name));
				row.setOnClickListener(v -> {
					commonQuery = "";
					n.startFrom(e);
					app.changed();
				});
				results.addView(row);
			}
		if (mine > 0 && !found.isEmpty())
			text(results, Txt.t(R.string.food_common_colon), R.style.EnerGym_FieldCaption, 12);
		if (found.isEmpty() && mine == 0 && q.length() >= 2)
			text(results, Txt.t(R.string.food_not_common), R.style.EnerGym_FieldCaption, 4);
		for (CommonFoods.Food f : found) {
			android.view.View row = getLayoutInflater().inflate(R.layout.row_food, results, false);
			((TextView) row.findViewById(R.id.food_name)).setText(f.name);
			((TextView) row.findViewById(R.id.food_detail)).setText(Txt.t(R.string.food_portion) + " " + NutritionController.format(f.portion) + " " +
				f.unit.symbol + " · " + NutritionController.format(f.kcal100) + " kcal/100 " + f.unit.symbol);
			((TextView) row.findViewById(R.id.food_kcal)).setText("+");
			row.setContentDescription(Txt.t(R.string.food_use, f.name));
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
			text(results, Txt.t(R.string.food_off_results), R.style.EnerGym_FieldCaption, 12);
			for (FoodProduct p : n.onlineResults) {
				android.view.View row = getLayoutInflater().inflate(R.layout.row_food, results, false);
				((TextView) row.findViewById(R.id.food_name)).setText(p.displayName());
				((TextView) row.findViewById(R.id.food_detail)).setText((p.quantity.isEmpty() ? "" : p.quantity + " · ") +
					NutritionController.format(p.kcal100) + " kcal/100 " + p.unit.symbol);
				((TextView) row.findViewById(R.id.food_kcal)).setText("+");
				row.setContentDescription(Txt.t(R.string.food_use, p.displayName()));
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
			n.searchingOnline && mine ? Txt.t(R.string.food_searching_off) : Txt.t(R.string.food_search_off, q),
			!n.searchingOnline && !n.busy, () -> n.searchOnline(q));
		search.setTag("food_search_online");
	}

	private static void staleValues(NutritionController n, TextView warning, String typed) {
		if (warning == null)
			return;
		boolean renamed = n.chosenFood != null && !n.chosenFood.trim().equalsIgnoreCase(typed.trim());
		warning.setText(renamed ? Txt.t(R.string.food_values_from, n.chosenFood) : "");
		warning.setVisibility(renamed ? android.view.View.VISIBLE : android.view.View.GONE);
	}

	private static void suggest(NutritionController n, android.widget.Button button, String typed) {
		FoodEntry known = n.known(typed);
		boolean same = known != null && known.name.equalsIgnoreCase(typed.trim()) &&
			NutritionController.format(known.kcal100).equals(n.foodDraft.getOrDefault("kcal100", ""));
		button.setVisibility(known == null || same ? android.view.View.GONE : android.view.View.VISIBLE);
		if (known != null)
			button.setText(Txt.t(R.string.food_use_known, known.name, NutritionController.format(known.kcal100) + " kcal/100 " +
				known.unit.symbol));
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
		LinearLayout card = card(Txt.t(R.string.food_already_in, NutritionFragment.meal(meal.ordinal()).toLowerCase(Txt.locale()),
			Math.round(kcal)), list.toString());
		card.setTag("food_meal_so_far");
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
