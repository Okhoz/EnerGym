package com.energym.app.nutrition;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.time.LocalDate;
import java.util.*;

/**
 * MODELO de comidas guardadas («Mi desayuno», recetas): varios alimentos con su cantidad que se anotan de una vez.
 * Se guardan en este móvil por cuenta, en un texto simple (una línea por alimento).
 */
public final class SavedMeals {
	public interface Store {
		String load(String owner);

		void save(String owner, String encoded);
	}

	public static final class Meal {
		public final String name;
		public final List<FoodEntry> foods;

		Meal(String name, List<FoodEntry> foods) {
			this.name = name;
			this.foods = Collections.unmodifiableList(new ArrayList<>(foods));
		}

		public double kcal() {
			double k = 0;
			for (FoodEntry f : foods)
				k += f.kcal();
			return k;
		}
	}

	public static final int MAX_MEALS = 50, MAX_FOODS = 30;
	private final Store store;
	private String owner;
	private List<Meal> meals = Collections.emptyList();

	public SavedMeals(Store store) {
		this.store = store;
	}

	public void activate(String next) {
		owner = next;
		meals = owner == null ? Collections.emptyList() : decode(store.load(owner));
	}

	public List<Meal> list() {
		return meals;
	}

	/** Guarda (o sustituye si ya existe ese nombre) una comida; devuelve un error o "". */
	public String save(String name, List<FoodEntry> foods) {
		String clean = name == null ? "" : name.replaceAll("[\\t\\r\\n#]", " ").replaceAll("\\s+", " ").trim();
		if (owner == null)
			return Txt.t(R.string.sm_signed_out);
		if (clean.isEmpty() || clean.length() > 60)
			return Txt.t(R.string.sm_name);
		if (foods == null || foods.isEmpty())
			return Txt.t(R.string.sm_empty);
		List<Meal> next = new ArrayList<>(meals);
		next.removeIf(m -> m.name.equalsIgnoreCase(clean));
		if (next.size() >= MAX_MEALS)
			return Txt.t(R.string.sm_max, MAX_MEALS);
		next.add(0, new Meal(clean, foods.subList(0, Math.min(MAX_FOODS, foods.size()))));
		meals = Collections.unmodifiableList(next);
		store.save(owner, encode(meals));
		return "";
	}

	public void remove(String name) {
		if (owner == null)
			return;
		List<Meal> next = new ArrayList<>(meals);
		next.removeIf(m -> m.name.equals(name));
		meals = Collections.unmodifiableList(next);
		store.save(owner, encode(meals));
	}

	static String encode(List<Meal> meals) {
		StringBuilder s = new StringBuilder();
		for (Meal m : meals) {
			s.append('#').append(m.name).append('\n');
			for (FoodEntry f : m.foods)
				s.append(String.join("\t", f.name.replaceAll("[\\t\\r\\n]", " "), f.barcode, f.unit.name(),
					num(f.amount), num(f.kcal100), num(f.protein100), num(f.carbs100), num(f.fat100))).append('\n');
		}
		return s.toString();
	}

	static List<Meal> decode(String text) {
		List<Meal> result = new ArrayList<>();
		if (text == null)
			return result;
		String name = null;
		List<FoodEntry> foods = new ArrayList<>();
		for (String line : (text + "\n#").split("\n")) {
			if (line.startsWith("#")) {
				if (name != null && !foods.isEmpty())
					result.add(new Meal(name, foods));
				name = line.substring(1).trim();
				foods = new ArrayList<>();
				continue;
			}
			String[] p = line.split("\t", -1);
			if (name == null || p.length != 8)
				continue;
			try {
				foods.add(new FoodEntry("saved", LocalDate.of(2000, 1, 1), FoodEntry.Meal.OTHER, p[0], p[1],
					FoodEntry.Unit.valueOf(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]),
					Double.parseDouble(p[5]), Double.parseDouble(p[6]), Double.parseDouble(p[7]), 0));
			} catch (RuntimeException ignored) {
				// Una línea dañada no impide leer el resto.
			}
		}
		return result;
	}

	private static String num(double v) {
		return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
	}
}
