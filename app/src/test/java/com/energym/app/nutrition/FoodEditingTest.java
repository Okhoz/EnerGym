package com.energym.app.nutrition;

import org.junit.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

/** Edición real del registro, sin duplicados ni pérdida de identidad al sincronizar. */
public final class FoodEditingTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 15);

	@Test public void editingPreservesIdentityDateAndRecalculatesTotalsWithoutAddingAnotherEntry() {
		Store store = new Store();
		FoodEntry original = entry("one", 150);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		assertTrue(c.editingFood());
		c.foodDraft.put("amount", "250");
		c.foodDraft.put("meal", "DINNER");
		c.foodDraft.put("name", "Arroz corregido");
		c.addFood(() -> {});
		assertEquals(0, store.adds);
		assertEquals(1, store.updates);
		assertEquals(1, c.entries.size());
		FoodEntry changed = c.entries.get(0);
		assertEquals(original.id, changed.id);
		assertEquals(original.createdAt, changed.createdAt);
		assertEquals(original.day, changed.day);
		assertEquals(FoodEntry.Meal.DINNER, changed.meal);
		assertEquals(900, c.totals(DAY)[0], 0.001);
		assertEquals(900, c.historyCalories(DAY), 0.001);
		assertFalse(c.editingFood());
	}

	@Test public void progressSeesTheFoodLoggedTodayEvenWhenAnotherDayIsSelected() {
		Store store = new Store();
		store.entries.add(entry("one", 150));
		NutritionController c = controller(store);
		c.selected = DAY.minusDays(5);
		double[] kcal = c.recentSeries(3, 0);
		assertArrayEquals(new double[]{0, 0, 540}, kcal, 0.001);
		assertEquals("15/10", c.recentLabels(3)[2]);
		assertEquals(540, NutritionController.loggedAverage(kcal, kcal), 0.001);
		assertEquals(0, NutritionController.loggedAverage(new double[3], new double[3]), 0.001);
	}

	@Test public void typingAKnownFoodOffersItsLabelWithoutTouchingAmountOrMeal() {
		Store store = new Store();
		store.entries.add(entry("one", 150));
		NutritionController c = controller(store);
		assertNull(c.known("ar"));
		assertNull(c.known("pan"));
		FoodEntry known = c.known("arr");
		assertNotNull(known);
		c.startManual();
		c.foodDraft.put("amount", "80");
		c.useLabelOf(known);
		assertEquals("Arroz", c.foodDraft.get("name"));
		assertEquals("80", c.foodDraft.get("amount"));
		assertEquals(NutritionController.format(known.kcal100), c.foodDraft.get("kcal100"));
	}

	@Test public void plusSavesTheFoodAndLeavesTheFormReadyForAnotherInTheSameMeal() {
		Store store = new Store();
		NutritionController c = controller(store);
		c.startManual(FoodEntry.Meal.DINNER);
		for (String[] food : new String[][]{{"Pollo", "150", "165"}, {"Arroz", "80", "360"}}) {
			c.foodDraft.put("name", food[0]);
			c.foodDraft.put("amount", food[1]);
			c.foodDraft.put("kcal100", food[2]);
			c.foodDraft.put("protein100", "10");
			c.foodDraft.put("carbs100", "10");
			c.foodDraft.put("fat100", "1");
			c.addFood(() -> c.startManual(FoodEntry.Meal.DINNER));
			assertEquals("DINNER", c.foodDraft.get("meal"));
			assertTrue("El formulario queda vacío para el siguiente", c.foodDraft.getOrDefault("name", "").isEmpty());
		}
		assertEquals(2, c.entriesOn(DAY).size());
		for (FoodEntry e : c.entriesOn(DAY))
			assertEquals(FoodEntry.Meal.DINNER, e.meal);
	}

	@Test public void cancelOrSwitchingAccountCannotOverwriteThePreviousFood() {
		Store store = new Store();
		FoodEntry original = entry("one", 150);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		long scan = c.scanToken();
		c.cancelFood();
		assertFalse(c.editingFood());
		assertFalse(c.scanned("3017620422003", scan));
		assertEquals(0, store.updates);
		c.edit(original);
		c.activate("other");
		assertFalse(c.editingFood());
		assertTrue(c.foodDraft.isEmpty());
	}

	@Test public void aDifferentMealOrNewDayCannotMoveAnEditedHistoricalEntry() {
		Store store = new Store();
		FoodEntry original = entry("one", 100);
		store.entries.add(original);
		NutritionController c = controller(store);
		c.edit(original);
		c.selected = DAY.minusDays(1);
		c.foodDraft.put("amount", "200");
		c.addFood(() -> {});
		assertEquals(DAY, c.entries.get(0).day);
		assertEquals(720, c.historyCalories(DAY), 0.001);
	}

	@Test public void aReloadStartedBeforeTheEditCannotUndoTheSavedTotals() {
		Store store = new Store();
		FoodEntry original = entry("one", 100);
		store.entries.add(original);
		NutritionController c = controller(store);
		store.holdLoad = true;
		c.reload();
		NutritionStore.Snapshot stale = new NutritionStore.Snapshot(null, Collections.singletonList(original), new TreeMap<>());
		c.edit(original);
		c.foodDraft.put("amount", "200");
		c.addFood(() -> {});
		store.delayedLoad.success(stale);
		assertEquals(720, c.totals(DAY)[0], 0.001);
		assertEquals(720, c.historyCalories(DAY), 0.001);
	}

	/** Si eliges un alimento por error y luego otro, no queda nada del primero (cantidad, valores ni nombre). */
	@Test public void choosingAnotherFoodReplacesEveryValueOfTheFirst() throws Exception {
		try (java.io.FileInputStream in = new java.io.FileInputStream("src/main/assets/foods.tsv")) {
			CommonFoods.load(in);
		}
		NutritionController c = controller(new Store());
		c.startManual(FoodEntry.Meal.DINNER);
		CommonFoods.Food rice = CommonFoods.search("arroz con leche", 1).get(0);
		CommonFoods.Food chicken = CommonFoods.search("pechuga de pollo", 1).get(0);
		c.startCommon(rice);
		c.foodDraft.put("amount", "320");
		assertEquals(rice.name, c.chosenFood);
		c.startCommon(chicken);
		assertEquals(chicken.name, c.foodDraft.get("name"));
		assertEquals(NutritionController.format(chicken.portion), c.foodDraft.get("amount"));
		assertEquals(NutritionController.format(chicken.kcal100), c.foodDraft.get("kcal100"));
		assertEquals(NutritionController.format(chicken.protein100), c.foodDraft.get("protein100"));
		assertEquals("La comida elegida se conserva", "DINNER", c.foodDraft.get("meal"));
		assertEquals(chicken.name, c.chosenFood);
		// «Elegir otro alimento» lo deja todo en blanco, salvo la comida.
		c.changeFood();
		assertNull(c.chosenFood);
		assertEquals("", c.foodDraft.getOrDefault("name", ""));
		assertEquals("", c.foodDraft.getOrDefault("kcal100", ""));
		assertEquals("", c.foodDraft.getOrDefault("amount", ""));
		assertEquals("DINNER", c.foodDraft.get("meal"));
	}

	private static FoodEntry entry(String id, double amount) {
		return new FoodEntry(id, DAY, FoodEntry.Meal.LUNCH, "Arroz", "", FoodEntry.Unit.G,
			amount, 360, 7, 79, 1, 1000);
	}

	private static NutritionController controller(Store store) {
		NutritionController c = new NutritionController(store, (code, cb) -> cb.success(null), () -> DAY,
			() -> {}, message -> { throw new AssertionError(message); });
		c.activate("owner");
		return c;
	}

	private static final class Store implements NutritionStore {
		final List<FoodEntry> entries = new ArrayList<>();
		int adds, updates;
		boolean holdLoad;
		Callback<Snapshot> delayedLoad;
		public void load(String uid, LocalDate from, Callback<Snapshot> c) {
			if (holdLoad) { delayedLoad = c; return; }
			c.success(new Snapshot(null, entries, new TreeMap<>()));
		}
		public void saveBody(String uid, BodyProfile body, Callback<Void> c) { c.success(null); }
		public void add(String uid, FoodEntry entry, Callback<Void> c) { adds++; c.success(null); }
		public void update(String uid, FoodEntry entry, Callback<Void> c) {
			updates++;
			entries.removeIf(old -> old.id.equals(entry.id));
			entries.add(entry);
			c.success(null);
		}
		public void remove(String uid, String id, Callback<Void> c) { c.success(null); }
		public void weight(String uid, LocalDate day, double kg, Callback<Void> c) { c.success(null); }
	}
}
