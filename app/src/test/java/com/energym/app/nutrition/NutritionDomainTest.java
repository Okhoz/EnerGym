package com.energym.app.nutrition;

import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

public class NutritionDomainTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	@Test
	public void entryScalesPer100AndRejectsImpossibleValues() {
		FoodEntry e = FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G, 150, 360, 7, 79, 0.6);
		assertEquals(540, e.kcal(), 0.001);
		assertEquals(10.5, e.protein(), 0.001);
		assertThrows(IllegalArgumentException.class,
			() -> FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "X", "", FoodEntry.Unit.G, 0, 100, 1, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "X", "", FoodEntry.Unit.G, 100, 100, 60, 60, 1));
		assertThrows(IllegalArgumentException.class,
			() -> FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, " ", "", FoodEntry.Unit.G, 100, 100, 1, 1, 1));
	}

	@Test
	public void bodyTargetsFollowMifflinAndGoal() {
		BodyProfile male = new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80, BodyProfile.Activity.MODERATE,
			BodyProfile.Goal.MAINTAIN, "");
		assertEquals(1780, male.basal(), 0.001);
		assertEquals(2759, male.targets().kcal, 0.5);
		assertEquals(128, male.targets().protein, 0);
		BodyProfile.Targets t = male.targets();
		assertEquals(t.kcal, t.protein * 4 + t.carbs * 4 + t.fat * 9, 12);
		BodyProfile lose = new BodyProfile(BodyProfile.Sex.FEMALE, 30, 165, 60, BodyProfile.Activity.LIGHT,
			BodyProfile.Goal.LOSE, "lactosa");
		assertTrue(lose.targets().kcal < lose.maintenance());
		assertEquals(120, lose.targets().protein, 0);
		assertThrows(IllegalArgumentException.class, () -> new BodyProfile(BodyProfile.Sex.MALE, 10, 180, 80,
			BodyProfile.Activity.MODERATE, BodyProfile.Goal.MAINTAIN, ""));
	}

	@Test
	public void parsesOpenFoodFactsAndDetectsVolume() {
		String json = "{\"code\":\"8410000000000\",\"status\":1,\"product\":{\"product_name\":\"Leche\"," +
			"\"product_name_es\":\"Leche semidesnatada\",\"brands\":\"Marca A, Marca B\",\"quantity\":\"1 l\"," +
			"\"nutriments\":{\"energy-kcal_100g\":46,\"proteins_100g\":3.1,\"carbohydrates_100g\":4.7,\"fat_100g\":1.6}}}";
		FoodProduct p = FoodProduct.parse("8410000000000", json);
		assertEquals("Leche semidesnatada", p.name);
		assertEquals("Marca A", p.brand);
		assertEquals(FoodEntry.Unit.ML, p.unit);
		assertTrue(p.complete());
		assertEquals(3.1, p.protein100, 0);
		FoodProduct partial = FoodProduct.parse("12345678", "{\"status\":1,\"product\":{\"quantity\":\"250 g\"," +
			"\"nutriments\":{\"energy-kj_100g\":1674}}}");
		assertEquals(FoodEntry.Unit.G, partial.unit);
		assertEquals(400, partial.kcal100, 0.5);
		assertFalse(partial.complete());
		assertEquals("Producto 12345678", partial.name);
		assertNull(FoodProduct.parse("12345678", "{\"status\":0,\"status_verbose\":\"product not found\"}"));
	}

	@Test
	public void extractsGtinFromBarcodesAndGs1Qr() {
		assertEquals("8410000000000", FoodProduct.extractCode("8410000000000"));
		assertEquals("8410000000000", FoodProduct.extractCode("https://id.gs1.org/01/08410000000000/10/ABC"));
		assertEquals("8410000000000", FoodProduct.extractCode("(01)08410000000000(17)271231"));
		assertNull(FoodProduct.extractCode("https://example.org/promo"));
		assertNull(FoodProduct.extractCode("123"));
	}

	static final class Store implements NutritionStore {
		final List<FoodEntry> saved = new ArrayList<>();
		BodyProfile body;
		final SortedMap<LocalDate, Double> weights = new TreeMap<>();

		public void load(String owner, LocalDate from, Callback<Snapshot> c) {
			c.success(new Snapshot(body, saved, weights));
		}

		public void saveBody(String owner, BodyProfile b, Callback<Void> c) {
			body = b;
			c.success(null);
		}

		public void add(String owner, FoodEntry e, Callback<Void> c) {
			saved.add(e);
			c.success(null);
		}

		public void remove(String owner, String id, Callback<Void> c) {
			saved.removeIf(e -> e.id.equals(id));
			c.success(null);
		}

		public void weight(String owner, LocalDate day, double kg, Callback<Void> c) {
			weights.put(day, kg);
			c.success(null);
		}
	}

	@Test
	public void controllerScansAddsTotalsAndRemoves() {
		Store store = new Store();
		List<String> errors = new ArrayList<>();
		FoodProduct milk = new FoodProduct("8410000000000", "Leche", "", "1 l", FoodEntry.Unit.ML, 46, 3.1, 4.7, 1.6);
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(milk), () -> DAY, () -> {
		}, errors::add);
		c.activate("uid");
		assertFalse(c.scanned("https://example.org"));
		assertEquals(1, errors.size());
		assertTrue(c.scanned("8410000000000"));
		assertEquals("ML", c.foodDraft.get("unit"));
		assertEquals("46", c.foodDraft.get("kcal100"));
		c.foodDraft.put("amount", "250");
		c.addFood(() -> {
		});
		assertEquals(1, store.saved.size());
		assertEquals(115, c.totals(DAY)[0], 0.001);
		assertEquals(115, c.caloriesSeries(7)[6], 0.001);
		assertTrue(c.loggedDays().contains(DAY));
		c.remove(c.entriesOn(DAY).get(0));
		assertTrue(store.saved.isEmpty());
		assertEquals(0, c.totals(DAY)[0], 0);
	}

	@Test
	public void recentFoodsAreDistinctAndRefillTheChosenMeal() {
		Store store = new Store();
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY, () -> {
		}, e -> {
		});
		c.activate("uid");
		for (String name : new String[]{"Arroz", "Pollo", "Arroz"}) {
			c.startManual(FoodEntry.Meal.LUNCH);
			c.foodDraft.put("name", name);
			c.foodDraft.put("amount", "100");
			c.foodDraft.put("kcal100", "120");
			c.foodDraft.put("protein100", "10");
			c.foodDraft.put("carbs100", "5");
			c.foodDraft.put("fat100", "2");
			c.addFood(() -> {
			});
		}
		assertEquals(2, c.recentFoods(8).size());
		assertEquals(FoodEntry.Meal.LUNCH, store.saved.get(0).meal);
		c.startManual(FoodEntry.Meal.DINNER);
		c.startFrom(c.recentFoods(8).get(0));
		assertEquals("DINNER", c.foodDraft.get("meal"));
		assertEquals("120", c.foodDraft.get("kcal100"));
		assertNotNull(c.preview());
	}

	@Test
	public void controllerValidatesBodyAndLogsWeight() {
		Store store = new Store();
		List<String> errors = new ArrayList<>();
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY, () -> {
		}, errors::add);
		c.activate("uid");
		c.fillBody();
		c.saveBody(() -> {
		});
		assertEquals(1, errors.size());
		assertNull(store.body);
		c.bodyDraft.put("age", "30");
		c.bodyDraft.put("heightCm", "175,5");
		c.bodyDraft.put("weightKg", "72");
		c.saveBody(() -> {
		});
		assertEquals(175.5, store.body.heightCm, 0);
		assertEquals(72, store.weights.get(DAY), 0);
		c.logWeight("71,4");
		assertEquals(71.4, store.body.weightKg, 0);
		assertNotNull(c.targets());
		assertNull(c.weeklyBalance());
	}
}
