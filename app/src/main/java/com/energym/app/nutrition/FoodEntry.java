package com.energym.app.nutrition;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * MODEL inmutable: alimento consumido con sus valores por 100 g o 100 ml y la cantidad real anotada.
 * Las calorías y macros se calculan siempre con la base declarada en la etiqueta; nunca convierte ml en g.
 */
public final class FoodEntry {
	public enum Unit {
		G("g"), ML("ml");

		public final String symbol;

		Unit(String symbol) {
			this.symbol = symbol;
		}
	}

	public enum Meal {BREAKFAST, LUNCH, SNACK, DINNER, OTHER}

	public final String id, name, barcode;
	public final LocalDate day;
	public final Meal meal;
	public final Unit unit;
	public final double amount, kcal100, protein100, carbs100, fat100;
	public final long createdAt;

	public FoodEntry(String id, LocalDate day, Meal meal, String name, String barcode, Unit unit, double amount,
		double kcal100, double protein100, double carbs100, double fat100, long createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.day = Objects.requireNonNull(day, "day");
		this.meal = Objects.requireNonNull(meal, "meal");
		this.name = Objects.requireNonNull(name, "name").trim();
		this.barcode = barcode == null ? "" : barcode.trim();
		this.unit = Objects.requireNonNull(unit, "unit");
		if (this.name.isEmpty() || this.name.length() > 120)
			throw new IllegalArgumentException(Txt.t(R.string.fe_name));
		if (!(amount > 0) || amount > 5000)
			throw new IllegalArgumentException(Txt.t(R.string.fe_amount, unit.symbol));
		check(kcal100, 0, 900, Txt.t(R.string.fe_kcal100));
		check(protein100, 0, 100, Txt.t(R.string.fe_protein100));
		check(carbs100, 0, 100, Txt.t(R.string.fe_carbs100));
		check(fat100, 0, 100, Txt.t(R.string.fe_fat100));
		if (protein100 + carbs100 + fat100 > 100.5 && unit == Unit.G)
			throw new IllegalArgumentException(Txt.t(R.string.fe_macros_over));
		this.amount = amount;
		this.kcal100 = kcal100;
		this.protein100 = protein100;
		this.carbs100 = carbs100;
		this.fat100 = fat100;
		this.createdAt = createdAt;
	}

	public static FoodEntry create(LocalDate day, Meal meal, String name, String barcode, Unit unit, double amount,
		double kcal100, double protein100, double carbs100, double fat100) {
		return new FoodEntry(UUID.randomUUID().toString(), day, meal, name, barcode, unit, amount, kcal100, protein100,
			carbs100, fat100, System.currentTimeMillis());
	}

	private static void check(double value, double min, double max, String label) {
		if (Double.isNaN(value) || value < min || value > max)
			throw new IllegalArgumentException(Txt.t(R.string.fe_out_of_range, label, (int) min, (int) max));
	}

	/** Nombre para mostrar: los alimentos de la tabla se guardan en español y se ven en el idioma de la app. */
	public String label() {
		return CommonFoods.label(name);
	}

	public double kcal() {
		return kcal100 * amount / 100.0;
	}

	public double protein() {
		return protein100 * amount / 100.0;
	}

	public double carbs() {
		return carbs100 * amount / 100.0;
	}

	public double fat() {
		return fat100 * amount / 100.0;
	}
}
