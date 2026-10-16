package com.energym.app.nutrition;

import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

public class SavedMealsTest {
	private static FoodEntry food(String name, double grams, double kcal100) {
		return FoodEntry.create(LocalDate.of(2026, 10, 15), FoodEntry.Meal.BREAKFAST, name, "", FoodEntry.Unit.G, grams,
			kcal100, 10, 50, 5);
	}

	@Test
	public void savesReplacesAndSurvivesReload() {
		Map<String, String> disk = new HashMap<>();
		SavedMeals.Store store = new SavedMeals.Store() {
			public String load(String owner) {
				return disk.getOrDefault(owner, "");
			}

			public void save(String owner, String encoded) {
				disk.put(owner, encoded);
			}
		};
		SavedMeals meals = new SavedMeals(store);
		assertFalse(meals.save("Desayuno", Collections.singletonList(food("Avena", 50, 370))).isEmpty());
		meals.activate("ana");
		assertEquals("", meals.save("Mi\tdesayuno", Arrays.asList(food("Avena", 50, 370), food("Leche", 250, 46))));
		assertEquals("", meals.save("mi DESAYUNO", Collections.singletonList(food("Pan", 60, 260))));
		assertFalse(meals.save("", Collections.singletonList(food("Pan", 60, 260))).isEmpty());
		assertFalse(meals.save("Vacía", Collections.emptyList()).isEmpty());
		assertEquals(1, meals.list().size());
		SavedMeals again = new SavedMeals(store);
		again.activate("ana");
		assertEquals("mi DESAYUNO", again.list().get(0).name);
		assertEquals(156, again.list().get(0).kcal(), 0.01);
		again.remove("mi DESAYUNO");
		assertTrue(again.list().isEmpty());
		again.activate("otra");
		assertTrue(again.list().isEmpty());
	}

	@Test
	public void damagedLinesAreSkipped() {
		List<SavedMeals.Meal> m = SavedMeals.decode("#Cena\nPasta\t\tG\t100\t350\t12\t70\t2\nroto\n#\n");
		assertEquals(1, m.size());
		assertEquals("Pasta", m.get(0).foods.get(0).name);
	}
}
