package com.energym.app.ui;

import com.energym.app.nutrition.FoodEntry;

/** Acceso de pruebas al cálculo del envase entero de FoodFragment. */
public final class FoodFragmentTestAccess {
	public static String pack(String quantity, FoodEntry.Unit unit) {
		return FoodFragment.packAmount(quantity, unit);
	}
}
