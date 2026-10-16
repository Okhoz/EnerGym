package com.energym.app.nutrition;

import com.energym.app.assistant.StrictJson;

import java.util.*;

/**
 * MODEL: menú de un día creado para la persona (por la IA del móvil o por la plantilla local). Cantidades y
 * valores orientativos; no sustituye a un dietista.
 */
public final class DietPlan {
	public static final class Meal {
		public final String name, dish, ingredients;
		public final double kcal, protein, carbs, fat;
		/** Coste estimado en euros; negativo si no se conoce. */
		public final double cost;

		public Meal(String name, String dish, String ingredients, double kcal, double protein, double carbs, double fat) {
			this(name, dish, ingredients, kcal, protein, carbs, fat, -1);
		}

		public Meal(String name, String dish, String ingredients, double kcal, double protein, double carbs, double fat,
			double cost) {
			this.cost = cost;
			this.name = name;
			this.dish = dish;
			this.ingredients = ingredients;
			this.kcal = kcal;
			this.protein = protein;
			this.carbs = carbs;
			this.fat = fat;
		}

		public String icon() {
			String n = name.toLowerCase(Locale.ROOT);
			if (n.contains("desayuno")) return "🍳";
			if (n.contains("media")) return "🍎";
			if (n.contains("merienda")) return "🥪";
			if (n.contains("recena")) return "🥛";
			if (n.contains("cena")) return "🌙";
			return "🍲";
		}

		/** Comida del diario en la que se anota este plato. */
		public FoodEntry.Meal diaryMeal() {
			String n = name.toLowerCase(Locale.ROOT);
			if (n.contains("desayuno")) return FoodEntry.Meal.BREAKFAST;
			if (n.contains("cena") && !n.contains("re")) return FoodEntry.Meal.DINNER;
			if (n.contains("comida") || n.contains("almuerzo")) return FoodEntry.Meal.LUNCH;
			if (n.contains("merienda") || n.contains("media")) return FoodEntry.Meal.SNACK;
			return FoodEntry.Meal.OTHER;
		}
	}

	public final List<Meal> meals;
	public final String advice, source;
	public final long createdAt;

	public DietPlan(List<Meal> meals, String advice, String source, long createdAt) {
		this.meals = Collections.unmodifiableList(new ArrayList<>(meals));
		this.advice = advice == null ? "" : advice;
		this.source = source == null ? "" : source;
		this.createdAt = createdAt;
	}

	public double[] totals() {
		double[] t = new double[4];
		for (Meal m : meals) {
			t[0] += m.kcal;
			t[1] += m.protein;
			t[2] += m.carbs;
			t[3] += m.fat;
		}
		return t;
	}

	/** Coste estimado del día en euros; negativo si alguna comida no lo indica. */
	public double cost() {
		double total = 0;
		for (Meal m : meals) {
			if (m.cost < 0)
				return -1;
			total += m.cost;
		}
		return total;
	}

	public String encode() {
		List<Object> list = new ArrayList<>();
		for (Meal m : meals) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("nombre", m.name);
			o.put("plato", m.dish);
			o.put("ingredientes", m.ingredients);
			o.put("kcal", Math.round(m.kcal));
			o.put("proteinas", Math.round(m.protein));
			o.put("hidratos", Math.round(m.carbs));
			o.put("grasas", Math.round(m.fat));
			if (m.cost >= 0)
				o.put("coste", Math.round(m.cost * 100) / 100.0);
			list.add(o);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("comidas", list);
		root.put("consejo", advice);
		root.put("fuente", source);
		root.put("creado", createdAt);
		return StrictJson.write(root);
	}

	/** Lee un plan guardado o la respuesta de la IA; null si no es un menú usable. */
	public static DietPlan decode(Map<String, Object> root, String source, long createdAt) {
		if (root == null || !(root.get("comidas") instanceof List))
			return null;
		List<Meal> meals = new ArrayList<>();
		for (Object item : (List<?>) root.get("comidas")) {
			if (!(item instanceof Map))
				continue;
			Map<?, ?> o = (Map<?, ?>) item;
			String name = text(o.get("nombre"), 40), dish = text(o.get("plato"), 140),
				ingredients = text(o.get("ingredientes"), 400);
			double kcal = number(o.get("kcal")), protein = number(o.get("proteinas")),
				carbs = number(o.get("hidratos")), fat = number(o.get("grasas"));
			if (name.isEmpty() || dish.isEmpty() || kcal < 50 || kcal > 2500 || protein < 0 || carbs < 0 || fat < 0)
				continue;
			// La IA suma mal: si sus calorías no cuadran con sus macros, mandan los macros (4/4/9 kcal por g).
			double fromMacros = protein * 4 + carbs * 4 + fat * 9;
			if (fromMacros >= 50 && Math.abs(fromMacros - kcal) > kcal * 0.1)
				kcal = fromMacros;
			double cost = o.containsKey("coste") ? number(o.get("coste")) : -1;
			meals.add(new Meal(name, dish, ingredients, kcal, protein, carbs, fat, cost > 60 ? -1 : cost));
			if (meals.size() == 6)
				break;
		}
		if (meals.size() < 2)
			return null;
		Object saved = root.get("fuente");
		Object at = root.get("creado");
		return new DietPlan(meals, text(root.get("consejo"), 500),
			saved instanceof String && !((String) saved).isEmpty() ? (String) saved : source,
			at instanceof Number ? ((Number) at).longValue() : createdAt);
	}

	public static DietPlan decode(String json) {
		if (json == null || json.isEmpty())
			return null;
		try {
			Object value = StrictJson.parse(json);
			return value instanceof Map ? decode(cast(value), "", 0) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Ajusta las raciones para que el total cuadre con el objetivo diario (la IA del móvil calcula mal las
	 * sumas). Devuelve el mismo plan si ya está a menos de un 8 %, y null si está tan lejos (más de ×1,6 o menos
	 * de ×0,6) que el menú no es coherente con el objetivo.
	 */
	public DietPlan scaledTo(double kcal) {
		double total = totals()[0];
		if (total <= 0 || kcal <= 0)
			return null;
		double factor = kcal / total;
		if (factor > 1.6 || factor < 0.6)
			return null;
		if (Math.abs(factor - 1) < 0.08)
			return this;
		String note = String.format(Locale.forLanguageTag("es-ES"), " (ración ×%.1f)", factor);
		List<Meal> scaled = new ArrayList<>();
		for (Meal m : meals)
			scaled.add(new Meal(m.name, m.dish, m.ingredients + note, m.kcal * factor, m.protein * factor,
				m.carbs * factor, m.fat * factor, m.cost < 0 ? -1 : m.cost * factor));
		return new DietPlan(scaled, advice, source, createdAt);
	}

	/** Algún plato menciona un alimento que la persona no puede o no quiere tomar. */
	public boolean mentionsAny(List<String> banned) {
		for (Meal m : meals) {
			String text = DietRules.normalize(m.dish + " " + m.ingredients);
			for (String word : banned)
				if (!word.isEmpty() && text.contains(word))
					return true;
		}
		return false;
	}

	private static String text(Object value, int max) {
		String s = value instanceof String ? ((String) value).trim() : "";
		return s.length() > max ? s.substring(0, max) : s;
	}

	private static double number(Object value) {
		if (value instanceof Number)
			return ((Number) value).doubleValue();
		if (value instanceof String)
			try {
				return Double.parseDouble(((String) value).replace(',', '.').replaceAll("[^0-9.]", ""));
			} catch (NumberFormatException e) {
				return -1;
			}
		return -1;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object value) {
		return (Map<String, Object>) value;
	}
}
