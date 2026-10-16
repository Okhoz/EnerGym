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

	@Test
	public void directRetailBarcodesKeepTheirProductDigits() {
		assertEquals("96385074", FoodProduct.extractCode("96385074"));
		assertEquals("036000291452", FoodProduct.extractCode("036000291452"));
		assertEquals("3017620422003", FoodProduct.extractCode("3017620422003"));
		assertEquals("13017620422000", FoodProduct.extractCode("13017620422000"));
	}

	@Test
	public void rejectsTruncatedGtinsAndArbitraryTextInsideQr() {
		assertNull(FoodProduct.extractCode("https://id.gs1.org/01/03017620422003999"));
		assertNull(FoodProduct.extractCode("https://id.gs1.org/01/03017620422003garbage"));
		assertNull(FoodProduct.extractCode("promo text /01/03017620422003"));
		assertNull(FoodProduct.extractCode("promo (01)03017620422003"));
	}

	@Test
	public void acceptsDelimitedGs1IdentifiersWithoutChangingTheirProduct() {
		assertEquals("3017620422003", FoodProduct.extractCode("https://id.gs1.org/01/03017620422003/10/ABC?17=271231"));
		assertEquals("3017620422003", FoodProduct.extractCode("https://example.org/01/03017620422003?10=ABC"));
		assertEquals("3017620422003", FoodProduct.extractCode("010301762042200317271231"));
	}

	@Test
	public void standardEnergyRemainsKilojoulesRegardlessOfContributorUnit() {
		for (String unit : new String[]{"kJ", "kcal", "", "J"}) {
			FoodProduct product = FoodProduct.parse("3017620422003", "{\"status\":1,\"product\":{\"nutriments\":" +
				"{\"energy_100g\":2252,\"energy_unit\":\"" + unit + "\"}}}");
			assertEquals(538, product.kcal100, 0.001);
		}
	}

	@Test
	public void invalidStandardEnergyStaysMissing() {
		for (String value : new String[]{"-1", "\"Infinity\"", "\"NaN\""}) {
			FoodProduct product = FoodProduct.parse("3017620422003", "{\"status\":1,\"product\":{\"nutriments\":" +
				"{\"energy_100g\":" + value + "}}}");
			assertTrue(Double.isNaN(product.kcal100));
		}
	}

	static final class Store implements NutritionStore {
		final List<FoodEntry> saved = new ArrayList<>();
		BodyProfile body;
		final SortedMap<LocalDate, Double> weights = new TreeMap<>();

		public void load(String owner, LocalDate from, Callback<Snapshot> c) {
			List<FoodEntry> entries = new ArrayList<>();
			SortedMap<LocalDate, Double> dailyCalories = new TreeMap<>();
			for (FoodEntry e : saved) {
				dailyCalories.merge(e.day, e.kcal(), Double::sum);
				if (!e.day.isBefore(from))
					entries.add(e);
			}
			c.success(new Snapshot(body, entries, weights, dailyCalories));
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
	public void scannedProductKeepsItsLabelValuesAndOnlyAmountChanges() {
		Store store = new Store();
		FoodProduct yogurt = new FoodProduct("8410000000017", "Yogur", "Marca", "125 g", FoodEntry.Unit.G,
			61, 3.5, 4.7, Double.NaN);
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(yogurt), () -> DAY, () -> {
		}, e -> fail(e));
		c.activate("uid");
		assertTrue(c.scanned("8410000000017"));
		assertEquals("61", c.scannedValue("kcal100"));
		assertNull("La grasa no viene en la ficha: se pide", c.scannedValue("fat100"));
		c.foodDraft.put("kcal100", "999");
		c.foodDraft.put("name", "Otro nombre");
		c.foodDraft.put("fat100", "3.3");
		c.foodDraft.put("amount", "200");
		c.addFood(() -> {
		});
		FoodEntry saved = store.saved.get(0);
		assertEquals("Los valores de la ficha mandan", 61, saved.kcal100, 0.001);
		assertEquals("Yogur · Marca", saved.name);
		assertEquals("Lo que faltaba sí se toma del formulario", 3.3, saved.fat100, 0.001);
		assertEquals(200, saved.amount, 0.001);
		assertEquals("125", com.energym.app.ui.FoodFragmentTestAccess.pack("125 g", FoodEntry.Unit.G));
		assertEquals("1000", com.energym.app.ui.FoodFragmentTestAccess.pack("1 l", FoodEntry.Unit.ML));
		assertEquals("", com.energym.app.ui.FoodFragmentTestAccess.pack("6 x 125 g", FoodEntry.Unit.ML));
	}

	@Test
	public void manualDraftSurvivesAnOlderLookupResponse() {
		List<NutritionStore.Callback<FoodProduct>> pending = new ArrayList<>();
		NutritionController c = new NutritionController(new Store(), (code, cb) -> pending.add(cb), () -> DAY,
			() -> {}, e -> fail(e));
		c.activate("uid");
		c.startManual(FoodEntry.Meal.LUNCH);
		c.scanned("3017620422003");
		c.startManual(FoodEntry.Meal.DINNER);
		c.foodDraft.put("name", "Mi cena");
		pending.get(0).success(new FoodProduct("3017620422003", "Nutella", "", "400 g", FoodEntry.Unit.G,
			539, 6.3, 57.5, 30.9));
		assertEquals("Mi cena", c.foodDraft.get("name"));
		assertEquals("DINNER", c.foodDraft.get("meal"));
		assertFalse(c.looking);
		assertNull(c.product);
	}

	@Test
	public void newestLookupSurvivesAnOlderLookupFailure() {
		List<NutritionStore.Callback<FoodProduct>> pending = new ArrayList<>();
		NutritionController c = new NutritionController(new Store(), (code, cb) -> pending.add(cb), () -> DAY,
			() -> {}, e -> fail(e));
		c.activate("uid");
		c.scanned("3017620422003");
		c.scanned("8410000000000");
		pending.get(1).success(new FoodProduct("8410000000000", "Leche", "", "1 l", FoodEntry.Unit.ML,
			46, 3.1, 4.7, 1.6));
		pending.get(0).failure(new java.io.IOException("old request"));
		assertEquals("Leche", c.foodDraft.get("name"));
		assertEquals("8410000000000", c.foodDraft.get("barcode"));
		assertEquals(FoodEntry.Unit.ML, c.product.unit);
		assertFalse(c.looking);
	}

	@Test
	public void lateCameraResultCannotReplaceANewerManualDraft() {
		List<String> lookedUp = new ArrayList<>();
		NutritionController c = new NutritionController(new Store(), (code, cb) -> lookedUp.add(code), () -> DAY,
			() -> {}, e -> fail(e));
		c.activate("uid");
		c.startManual(FoodEntry.Meal.LUNCH);
		long scan = c.scanToken();
		c.startManual(FoodEntry.Meal.DINNER);
		c.foodDraft.put("name", "Mi cena");
		assertFalse(c.scanned("3017620422003", scan));
		assertEquals("Mi cena", c.foodDraft.get("name"));
		assertTrue(lookedUp.isEmpty());
		assertFalse(c.looking);
	}

	@Test
	public void lateCameraResultCannotCrossAnAccountRoundTrip() {
		List<String> lookedUp = new ArrayList<>();
		NutritionController c = new NutritionController(new Store(), (code, cb) -> lookedUp.add(code), () -> DAY,
			() -> {}, e -> fail(e));
		c.activate("uid-A");
		long scan = c.scanToken();
		c.activate("uid-B");
		c.activate("uid-A");
		assertFalse(c.scanned("3017620422003", scan));
		assertNull(c.product);
		assertTrue(lookedUp.isEmpty());
		assertFalse(c.looking);
	}

	@Test
	public void unknownAndFailedLookupsLeaveTheCodeForManualEntry() {
		for (boolean failLookup : new boolean[]{false, true}) {
			NutritionController c = new NutritionController(new Store(), (code, cb) -> {
				if (failLookup)
					cb.failure(new java.io.IOException("offline"));
				else
					cb.success(null);
			}, () -> DAY, () -> {}, e -> fail(e));
			c.activate("uid");
			c.startManual(FoodEntry.Meal.SNACK);
			assertTrue(c.scanned("9999999999994"));
			assertEquals("9999999999994", c.foodDraft.get("barcode"));
			assertEquals("SNACK", c.foodDraft.get("meal"));
			assertNull(c.product);
			assertNull(c.preview());
			assertFalse(c.looking);
		}
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
	public void loggedDaysIncludeFoodOutsideTheDiaryWindow() {
		Store store = new Store();
		LocalDate oldDay = DAY.minusDays(60);
		store.saved.add(FoodEntry.create(oldDay, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G, 100, 100, 2, 20, 1));
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY, () -> {
		}, e -> fail(e));
		c.activate("uid");
		assertTrue(c.entries.isEmpty());
		assertEquals(Collections.singleton(oldDay), c.loggedDays());
		assertEquals(100, c.historyCalories(oldDay), 0);
		assertEquals(0, c.historyCalories(DAY), 0);
	}

	@Test
	public void completeDailySummaryTracksFoodChangesAndClearsOnAccountChange() {
		Store store = new Store();
		LocalDate oldDay = DAY.minusDays(60);
		FoodEntry old = FoodEntry.create(oldDay, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G, 100, 100, 2, 20, 1);
		store.saved.add(old);
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY, () -> {
		}, e -> fail(e));
		c.activate("uid-A");
		c.select(oldDay);
		c.startManual(FoodEntry.Meal.LUNCH);
		c.foodDraft.put("name", "Pollo");
		c.foodDraft.put("amount", "100");
		c.foodDraft.put("kcal100", "150");
		c.foodDraft.put("protein100", "20");
		c.foodDraft.put("carbs100", "0");
		c.foodDraft.put("fat100", "5");
		c.addFood(() -> {
		});
		assertEquals(250, c.historyCalories(oldDay), 0);
		c.remove(old);
		assertEquals(150, c.historyCalories(oldDay), 0);
		assertTrue(c.loggedDays().contains(oldDay));
		c.remove(c.entriesOn(oldDay).get(0));
		assertEquals(0, c.historyCalories(oldDay), 0);
		assertFalse(c.loggedDays().contains(oldDay));
		store.saved.add(old);
		c.reload();
		assertEquals(100, c.historyCalories(oldDay), 0);
		c.activate(null);
		assertTrue(c.loggedDays().isEmpty());
		assertEquals(0, c.historyCalories(oldDay), 0);
		store.saved.clear();
		store.saved.add(FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "Pasta", "", FoodEntry.Unit.G, 100, 200, 5, 35, 3));
		c.activate("uid-B");
		assertEquals(Collections.singleton(DAY), c.loggedDays());
		assertEquals(200, c.historyCalories(DAY), 0);
		assertEquals(0, c.historyCalories(oldDay), 0);
	}

	@Test
	public void compatibleSnapshotBuildsDailySummaryFromItsEntries() {
		List<FoodEntry> entries = Arrays.asList(
			FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G, 100, 100, 2, 20, 1),
			FoodEntry.create(DAY, FoodEntry.Meal.DINNER, "Caldo", "", FoodEntry.Unit.ML, 100, 50, 2, 5, 1));
		NutritionStore.Snapshot snapshot = new NutritionStore.Snapshot(null, entries, new TreeMap<>());
		assertEquals(150, snapshot.dailyCalories.get(DAY), 0);
	}

	@Test
	public void historicalWeeklyCaloriesIncludeDaysBeforeTheSelectedDay() {
		for (int daysAgo : new int[]{22, 60}) {
			Store store = new Store();
			LocalDate selected = DAY.minusDays(daysAgo);
			for (int i = 0; i < 7; i++)
				store.saved.add(FoodEntry.create(selected.minusDays(i), FoodEntry.Meal.LUNCH, "Arroz", "",
					FoodEntry.Unit.G, 100, 100, 2, 20, 1));
			store.body = new BodyProfile(BodyProfile.Sex.MALE, 30, 180, 80, BodyProfile.Activity.MODERATE,
				BodyProfile.Goal.MAINTAIN, "");
			store.saved.add(FoodEntry.create(DAY, FoodEntry.Meal.LUNCH, "Comida de hoy", "",
				FoodEntry.Unit.G, 100, 200, 10, 20, 5));
			NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY, () -> {
			}, e -> fail(e));
			c.activate("uid");
			c.select(selected);
			assertArrayEquals("Selección hace " + daysAgo + " días", new double[]{100, 100, 100, 100, 100, 100, 100},
				c.caloriesSeries(7), 0);
			assertEquals(200, c.totals(DAY)[0], 0);
			assertEquals(-2559, c.weeklyBalance(), 0.001);
		}
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
