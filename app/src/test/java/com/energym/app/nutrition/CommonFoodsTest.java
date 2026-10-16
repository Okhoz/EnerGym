package com.energym.app.nutrition;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CommonFoodsTest {
	@Test
	public void searchIgnoresAccentsAndCaseAndPutsPrefixMatchesFirst() {
		List<CommonFoods.Food> found = CommonFoods.search("PLATANO", 5);
		assertEquals("Plátano", found.get(0).name);
		List<CommonFoods.Food> rice = CommonFoods.search("arroz", 5);
		assertTrue(rice.size() >= 2);
		assertTrue(rice.get(0).name.startsWith("Arroz"));
		assertTrue("Busca por palabras dentro del nombre", CommonFoods.search("pollo", 5).stream()
			.anyMatch(f -> f.name.equals("Pechuga de pollo a la plancha")));
		assertTrue(CommonFoods.search("a", 5).isEmpty());
		assertEquals(2, CommonFoods.search("leche", 2).size());
	}

	@Test
	public void valuesAreCoherentWithTheirCalories() {
		for (CommonFoods.Food f : CommonFoods.all()) {
			double macros = f.protein100 + f.carbs100 + f.fat100;
			assertTrue(f.name + " no puede tener más de 100 g de macros por 100", macros <= 100.5);
			// Alcohol aparte, 4/4/9 kcal por gramo debe quedar cerca de las calorías declaradas.
			double estimate = f.protein100 * 4 + f.carbs100 * 4 + f.fat100 * 9;
			if (!f.name.equals("Cerveza") && !f.name.equals("Vino tinto"))
				assertEquals(f.name, f.kcal100, estimate, Math.max(15, f.kcal100 * 0.15));
			assertTrue(f.portion > 0);
		}
	}
}
