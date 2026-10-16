package com.energym.app.nutrition;

import java.text.Normalizer;
import java.util.*;

/**
 * Reglas de la dieta personalizada: reparto de calorías por comida, alimentos prohibidos según alergias y
 * gustos, prompt para la IA del móvil y un menú base local cuando la IA no está disponible.
 */
public final class DietRules {
	private DietRules() {
	}

	/** Nombres de las tomas y su peso en el reparto de calorías según cuántas comidas hace al día. */
	static final String[][] SLOTS = {
		{},
		{},
		{"Comida", "Cena"},
		{"Desayuno", "Comida", "Cena"},
		{"Desayuno", "Comida", "Merienda", "Cena"},
		{"Desayuno", "Media mañana", "Comida", "Merienda", "Cena"},
		{"Desayuno", "Media mañana", "Comida", "Merienda", "Cena", "Recena"},
	};

	private static final Map<String, Double> SHARE = new HashMap<>();

	static {
		SHARE.put("Desayuno", 0.25);
		SHARE.put("Media mañana", 0.10);
		SHARE.put("Comida", 0.35);
		SHARE.put("Merienda", 0.10);
		SHARE.put("Cena", 0.25);
		SHARE.put("Recena", 0.05);
	}

	public static List<String> slots(int mealsPerDay) {
		return Arrays.asList(SLOTS[Math.max(2, Math.min(6, mealsPerDay))]);
	}

	/** Parte de las calorías del día para cada toma (suman 1). */
	public static double share(String slot, List<String> slots) {
		double total = 0;
		for (String s : slots)
			total += SHARE.get(s);
		return SHARE.get(slot) / total;
	}

	public static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT);
	}

	/** Grupos de alergia → palabras de ingredientes que los contienen. */
	private static final String[][] ALLERGENS = {
		{"lactosa", "leche", "yogur", "queso", "nata", "mantequilla", "kefir"},
		{"lacteo", "leche", "yogur", "queso", "nata", "mantequilla", "kefir"},
		{"leche", "leche", "yogur", "queso", "nata", "mantequilla", "kefir"},
		{"gluten", "pan", "pasta", "tostada", "cuscus", "trigo", "avena", "espagueti", "tortita de trigo", "wrap"},
		{"celiac", "pan", "pasta", "tostada", "cuscus", "trigo", "avena", "espagueti", "wrap"},
		{"frutos secos", "nuez", "nueces", "almendra", "avellana", "anacardo", "pistacho", "cacahuete"},
		{"cacahuete", "cacahuete", "crema de cacahuete"},
		{"huevo", "huevo", "tortilla"},
		{"marisco", "gamba", "langostino", "mejillon", "calamar", "sepia"},
		{"pescado", "salmon", "atun", "merluza", "bacalao", "sardina", "pescado"},
		{"soja", "soja", "tofu", "tempeh", "edamame"},
	};

	/** Palabras que ningún plato puede contener: alergias (con sus ingredientes) y alimentos que no toma. */
	public static List<String> banned(BodyProfile body) {
		Set<String> words = new LinkedHashSet<>();
		for (String raw : (body.allergies + "," + body.dislikes).split("[,;/\\n]| y ")) {
			String word = normalize(raw).trim();
			if (word.length() < 3)
				continue;
			boolean group = false;
			for (String[] allergen : ALLERGENS)
				if (word.contains(allergen[0])) {
					group = true;
					words.addAll(Arrays.asList(allergen).subList(1, allergen.length));
				}
			if (!group)
				words.add(word.endsWith("s") && word.length() > 4 ? word.substring(0, word.length() - 1) : word);
		}
		return new ArrayList<>(words);
	}

	/** Prompt breve para la IA del móvil: datos, objetivo, restricciones y formato JSON exacto. */
	public static String prompt(BodyProfile body, BodyProfile.Targets t, String training, List<String> avoidDishes) {
		List<String> slots = slots(body.mealsPerDay);
		StringBuilder p = new StringBuilder();
		p.append("Eres un nutricionista deportivo. Crea el menú de UN día para tu cliente, con platos sencillos y ")
			.append("alimentos habituales en España.\n");
		p.append("Cliente: ").append(body.sex == BodyProfile.Sex.FEMALE ? "mujer" : "hombre").append(", ")
			.append(body.age).append(" años, ").append(NutritionController.format(body.heightCm)).append(" cm, ")
			.append(NutritionController.format(body.weightKg)).append(" kg. Objetivo: ").append(body.goal.label)
			.append(".\n");
		if (!training.isEmpty())
			p.append("Entrenamiento: ").append(training).append('\n');
		p.append("Total del día: ").append(Math.round(t.kcal)).append(" kcal, ").append(Math.round(t.protein))
			.append(" g de proteínas, ").append(Math.round(t.carbs)).append(" g de hidratos y ")
			.append(Math.round(t.fat)).append(" g de grasas.\n");
		p.append("Tipo de alimentación: ").append(body.diet.label).append(". Tiempo para cocinar: ")
			.append(body.cooking.label).append(".\n");
		if (!body.allergies.isEmpty())
			p.append("ALERGIAS (prohibido usarlos ni sus derivados): ").append(body.allergies).append(".\n");
		if (!body.dislikes.isEmpty())
			p.append("No incluyas: ").append(body.dislikes).append(".\n");
		if (!avoidDishes.isEmpty())
			p.append("Platos distintos a: ").append(String.join("; ", avoidDishes)).append(".\n");
		p.append("Haz exactamente ").append(slots.size()).append(" comidas, en este orden: ")
			.append(String.join(", ", slots)).append(". Reparte las calorías: ");
		List<String> parts = new ArrayList<>();
		for (String s : slots)
			parts.add(s + " " + Math.round(t.kcal * share(s, slots)) + " kcal");
		p.append(String.join(", ", parts)).append(".\n");
		p.append("Responde SOLO con JSON, sin texto alrededor:\n")
			.append("{\"consejo\":\"un consejo breve para su objetivo\",\"comidas\":[{\"nombre\":\"Desayuno\",")
			.append("\"plato\":\"nombre del plato\",\"ingredientes\":\"cantidades en gramos, separadas por comas\",")
			.append("\"kcal\":500,\"proteinas\":30,\"hidratos\":60,\"grasas\":15}]}\n");
		return p.toString();
	}

	// ---- Menú base local: plantillas por tipo de alimentación y toma ----

	private static final class Dish {
		final String name, ingredients;
		final EnumSet<BodyProfile.Diet> diets;

		Dish(String name, String ingredients, BodyProfile.Diet... diets) {
			this.name = name;
			this.ingredients = ingredients;
			this.diets = diets.length == 0 ? EnumSet.allOf(BodyProfile.Diet.class) : EnumSet.copyOf(Arrays.asList(diets));
		}
	}

	private static final BodyProfile.Diet O = BodyProfile.Diet.OMNIVORE, V = BodyProfile.Diet.VEGETARIAN,
		VG = BodyProfile.Diet.VEGAN, P = BodyProfile.Diet.PESCATARIAN, M = BodyProfile.Diet.MEDITERRANEAN;

	private static final Map<String, List<Dish>> DISHES = new LinkedHashMap<>();

	static {
		DISHES.put("Desayuno", Arrays.asList(
			new Dish("Avena con yogur, plátano y nueces", "avena, yogur natural, plátano, nueces", O, V, P, M),
			new Dish("Tostadas integrales con huevo revuelto y tomate", "pan integral, huevo, tomate, aceite de oliva", O, V, P, M),
			new Dish("Porridge de avena con bebida de soja y frutos rojos", "avena, bebida de soja, frutos rojos, semillas de chía"),
			new Dish("Tostadas con aguacate y tofu revuelto", "pan integral, aguacate, tofu, tomate"),
			new Dish("Tortitas de arroz con requesón y fresas", "tortitas de arroz, requesón, fresas, miel", O, V, P, M)));
		DISHES.put("Media mañana", Arrays.asList(
			new Dish("Yogur natural con fruta", "yogur natural, manzana", O, V, P, M),
			new Dish("Fruta con un puñado de almendras", "naranja, almendras"),
			new Dish("Hummus con palitos de zanahoria", "hummus, zanahoria")));
		DISHES.put("Comida", Arrays.asList(
			new Dish("Pollo a la plancha con arroz integral y verduras", "pechuga de pollo, arroz integral, calabacín, pimiento, aceite de oliva", O),
			new Dish("Lentejas estofadas con verduras", "lentejas, zanahoria, pimiento, cebolla, aceite de oliva", O, V, VG, P, M),
			new Dish("Salmón al horno con patata y brócoli", "salmón, patata, brócoli, aceite de oliva", O, P, M),
			new Dish("Pasta integral con atún y tomate", "pasta integral, atún al natural, tomate triturado, aceite de oliva", O, P, M),
			new Dish("Garbanzos salteados con espinacas y quinoa", "garbanzos, espinacas, quinoa, aceite de oliva"),
			new Dish("Tofu salteado con arroz y verduras", "tofu, arroz, brócoli, pimiento, salsa de soja")));
		DISHES.put("Merienda", Arrays.asList(
			new Dish("Bocadillo integral de pavo y tomate", "pan integral, pavo, tomate", O),
			new Dish("Yogur griego con avena y miel", "yogur griego, avena, miel", O, V, P, M),
			new Dish("Batido de bebida de soja, plátano y cacao", "bebida de soja, plátano, cacao puro"),
			new Dish("Tostada con crema de cacahuete y plátano", "pan integral, crema de cacahuete, plátano")));
		DISHES.put("Cena", Arrays.asList(
			new Dish("Tortilla francesa con ensalada y pan", "huevo, lechuga, tomate, pan integral, aceite de oliva", O, V, P, M),
			new Dish("Merluza a la plancha con verduras asadas", "merluza, berenjena, calabacín, pimiento, aceite de oliva", O, P, M),
			new Dish("Pavo a la plancha con puré de calabaza", "pavo, calabaza, patata, aceite de oliva", O),
			new Dish("Crema de verduras y tofu a la plancha", "calabacín, puerro, patata, tofu, aceite de oliva"),
			new Dish("Ensalada de quinoa, garbanzos y aguacate", "quinoa, garbanzos, aguacate, tomate, pepino")));
		DISHES.put("Recena", Arrays.asList(
			new Dish("Kéfir natural", "kéfir", O, V, P, M),
			new Dish("Un puñado de nueces", "nueces")));
	}

	/**
	 * Menú base sin IA: platos compatibles con el tipo de alimentación y sin alimentos prohibidos, con las calorías
	 * y macros del objetivo repartidos por toma. {@code variant} cambia los platos entre propuestas.
	 */
	public static DietPlan local(BodyProfile body, BodyProfile.Targets t, int variant, long now) {
		List<String> slots = slots(body.mealsPerDay), banned = banned(body);
		List<DietPlan.Meal> meals = new ArrayList<>();
		for (String slot : slots) {
			List<Dish> options = new ArrayList<>();
			for (Dish d : DISHES.get(slot))
				if (d.diets.contains(body.diet) && allowed(d, banned))
					options.add(d);
			double share = share(slot, slots);
			if (options.isEmpty()) {
				meals.add(new DietPlan.Meal(slot, "Elige un plato compatible con tus restricciones", "Ración equilibrada: " +
					"verdura, una fuente de proteína y un hidrato integral", t.kcal * share, t.protein * share,
					t.carbs * share, t.fat * share));
				continue;
			}
			Dish d = options.get(Math.floorMod(variant + slot.length(), options.size()));
			meals.add(new DietPlan.Meal(slot, d.name, d.ingredients, t.kcal * share, t.protein * share,
				t.carbs * share, t.fat * share));
		}
		return new DietPlan(meals, "Menú base preparado en el móvil con tus datos. Ajusta las raciones a tu hambre " +
			"y reparte la proteína a lo largo del día.", "Plan base del móvil", now);
	}

	private static boolean allowed(Dish d, List<String> banned) {
		String text = normalize(d.name + " " + d.ingredients);
		for (String word : banned)
			if (text.contains(word))
				return false;
		return true;
	}
}
