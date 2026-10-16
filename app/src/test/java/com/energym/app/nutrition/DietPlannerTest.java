package com.energym.app.nutrition;

import com.energym.app.assistant.OnDeviceModel;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Dieta personalizada en el móvil: respeta alergias, cuadra el objetivo y nunca deja a la persona sin menú. */
public class DietPlannerTest {
	private static BodyProfile body(BodyProfile.Diet diet, int meals, String allergies, String dislikes) {
		return new BodyProfile(BodyProfile.Sex.MALE, 30, 178, 78, BodyProfile.Activity.MODERATE,
			BodyProfile.Goal.MAINTAIN, allergies, diet, BodyProfile.Cooking.QUICK, meals, dislikes);
	}

	private static final class Model implements OnDeviceModel {
		int state = AVAILABLE;
		String output;
		String prompt;

		public void status(Result<Integer> result) {
			result.success(state);
		}

		public void download(Result<Void> result) {
		}

		public void generate(String prompt, Result<String> result) {
			this.prompt = prompt;
			if (output == null) result.failure("MODEL_FAILED");
			else result.success(output);
		}
	}

	private static final class Memory implements DietPlanner.Store {
		final Map<String, String> saved = new HashMap<>();

		public String load(String owner) {
			return saved.getOrDefault(owner, "");
		}

		public void save(String owner, String json) {
			saved.put(owner, json);
		}
	}

	@Test
	public void allergiesBanTheirIngredientsAndTheLocalMenuAvoidsThem() {
		BodyProfile b = body(BodyProfile.Diet.OMNIVORE, 4, "lactosa, frutos secos", "champiñones");
		List<String> banned = DietRules.banned(b);
		assertTrue(banned.contains("yogur"));
		assertTrue(banned.contains("nueces"));
		assertTrue(banned.contains("champinone"));
		DietPlan plan = DietRules.local(b, b.targets(), 0, 1);
		assertEquals(Arrays.asList("Desayuno", "Comida", "Merienda", "Cena"), names(plan));
		assertFalse(plan.mentionsAny(banned));
		assertEquals(b.targets().kcal, plan.totals()[0], 1);
	}

	@Test
	public void veganMenuOnlyUsesVeganDishes() {
		BodyProfile b = body(BodyProfile.Diet.VEGAN, 5, "", "");
		DietPlan plan = DietRules.local(b, b.targets(), 3, 1);
		assertEquals(5, plan.meals.size());
		for (DietPlan.Meal m : plan.meals) {
			String t = DietRules.normalize(m.dish + m.ingredients);
			for (String animal : Arrays.asList("pollo", "huevo", "yogur", "salmon", "atun", "pavo", "merluza", "requeson"))
				assertFalse(m.dish + " no es vegano", t.contains(animal));
		}
	}

	@Test
	public void aiMenuIsParsedScaledToTheTargetAndSaved() {
		BodyProfile b = body(BodyProfile.Diet.OMNIVORE, 3, "", "");
		Model model = new Model();
		model.output = "Aquí tienes: ```json {\"consejo\":\"Bebe agua\",\"comidas\":[" +
			"{\"nombre\":\"Desayuno\",\"plato\":\"Avena con plátano\",\"ingredientes\":\"60 g avena\",\"kcal\":600,\"proteinas\":25,\"hidratos\":90,\"grasas\":20}," +
			"{\"nombre\":\"Comida\",\"plato\":\"Pollo con arroz\",\"ingredientes\":\"150 g pollo\",\"kcal\":900,\"proteinas\":50,\"hidratos\":110,\"grasas\":28}," +
			"{\"nombre\":\"Cena\",\"plato\":\"Tortilla y ensalada\",\"ingredientes\":\"2 huevos\",\"kcal\":700,\"proteinas\":35,\"hidratos\":50,\"grasas\":38}]} ```";
		Memory store = new Memory();
		List<Integer> changes = new ArrayList<>();
		DietPlanner planner = new DietPlanner(model, store, Runnable::run, () -> changes.add(1), () -> "3 días");
		planner.activate("owner");
		planner.create(b);
		assertFalse(planner.busy);
		assertTrue(planner.plan.source.startsWith("Creado con IA"));
		assertEquals("Las raciones cuadran con el objetivo", b.targets().kcal, planner.plan.totals()[0], 2);
		assertTrue(planner.plan.meals.get(0).ingredients.contains("ración ×"));
		assertFalse(model.prompt.contains("ALERGIAS"));
		assertTrue(model.prompt.contains("exactamente 3 comidas"));
		assertTrue(store.saved.get("owner").contains("Avena con plátano"));
		planner.activate(null);
		planner.activate("owner");
		assertEquals("El menú guardado vuelve al reabrir", "Avena con plátano", planner.plan.meals.get(0).dish);
	}

	@Test
	public void aiMenuWithAnAllergenOrNoModelFallsBackToTheSafeLocalMenu() {
		BodyProfile b = body(BodyProfile.Diet.OMNIVORE, 3, "huevo", "");
		Model model = new Model();
		model.output = "{\"comidas\":[{\"nombre\":\"Desayuno\",\"plato\":\"Huevos revueltos\",\"kcal\":500,\"proteinas\":30,\"hidratos\":10,\"grasas\":30}," +
			"{\"nombre\":\"Cena\",\"plato\":\"Tortilla de patata\",\"kcal\":600,\"proteinas\":20,\"hidratos\":50,\"grasas\":30}]}";
		DietPlanner planner = new DietPlanner(model, new Memory(), Runnable::run, () -> { }, () -> "");
		planner.activate("owner");
		planner.create(b);
		assertEquals("Plan base del móvil", planner.plan.source);
		assertTrue(planner.status.contains("alimento que evitas"));
		assertTrue(model.prompt.contains("ALERGIAS"));
		assertFalse(planner.plan.mentionsAny(DietRules.banned(b)));
		model.state = OnDeviceModel.DOWNLOADING;
		planner.create(b);
		assertEquals("Plan base del móvil", planner.plan.source);
		assertTrue(planner.status.contains("aún no está lista"));
		// Un menú de la IA muy lejos de las calorías objetivo no se infla: se usa el menú base.
		model.state = OnDeviceModel.AVAILABLE;
		model.output = "{\"comidas\":[{\"nombre\":\"Comida\",\"plato\":\"Ensalada\",\"kcal\":300,\"proteinas\":10,\"hidratos\":20,\"grasas\":10}," +
			"{\"nombre\":\"Cena\",\"plato\":\"Crema de verduras\",\"kcal\":250,\"proteinas\":8,\"hidratos\":30,\"grasas\":8}]}";
		planner.create(b);
		assertEquals("Plan base del móvil", planner.plan.source);
		assertTrue(planner.status.contains("no cuadraba"));
	}

	@Test
	public void caloriesThatDoNotMatchTheMacrosAreRecomputedAndIconsFollowTheMeal() {
		DietPlan plan = DietPlanner.parse("{\"comidas\":[{\"nombre\":\"Comida\",\"plato\":\"Pollo con arroz\",\"kcal\":800," +
			"\"proteinas\":40,\"hidratos\":100,\"grasas\":15},{\"nombre\":\"Cena\",\"plato\":\"Tortilla\",\"kcal\":400," +
			"\"proteinas\":25,\"hidratos\":20,\"grasas\":22}]}", "Gemma", 1);
		assertEquals(695, plan.meals.get(0).kcal, 0.01);
		assertEquals("Dentro del 10 % se respeta", 400, plan.meals.get(1).kcal, 0.01);
		assertEquals("🍲", plan.meals.get(0).icon());
		assertEquals("🌙", plan.meals.get(1).icon());
		assertEquals(FoodEntry.Meal.DINNER, plan.meals.get(1).diaryMeal());
	}

	private static List<String> names(DietPlan plan) {
		List<String> names = new ArrayList<>();
		for (DietPlan.Meal m : plan.meals)
			names.add(m.name);
		return names;
	}
}
