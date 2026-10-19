package com.energym.app.nutrition;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.*;

import static org.junit.Assert.*;

public class CommonFoodsTest {
	@BeforeClass
	public static void load() throws Exception {
		try (FileInputStream in = new FileInputStream("src/main/assets/foods.tsv")) {
			CommonFoods.load(in);
		}
	}

	@Test
	public void tableIsLargeAndSearchFindsNamesSynonymsAndPlurals() {
		assertTrue("Cientos de alimentos: " + CommonFoods.all().size(), CommonFoods.all().size() >= 300);
		assertEquals("Plátano", CommonFoods.search("PLATANO", 5).get(0).name);
		assertTrue(CommonFoods.search("macarrones", 5).get(0).name.startsWith("Macarrones"));
		assertTrue(CommonFoods.search("macarron", 5).get(0).name.startsWith("Macarrones"));
		assertTrue("Sinónimo", CommonFoods.search("penne", 5).stream().anyMatch(f -> f.name.startsWith("Macarrones")));
		assertTrue(CommonFoods.search("espaguetis", 5).stream().anyMatch(f -> f.name.startsWith("Espaguetis")));
		assertTrue(CommonFoods.search("huevos", 5).stream().anyMatch(f -> f.name.equals("Huevo")));
		assertTrue(CommonFoods.search("nutella", 5).stream().anyMatch(f -> f.name.startsWith("Crema de cacao")));
		assertTrue(CommonFoods.search("pollo", 10).stream().anyMatch(f -> f.name.equals("Pechuga de pollo a la plancha")));
		assertTrue(CommonFoods.search("a", 5).isEmpty());
	}

	@Test
	public void valuesAreCoherentWithTheirCalories() {
		Set<String> names = new HashSet<>();
		for (CommonFoods.Food f : CommonFoods.all()) {
			assertTrue("Nombre repetido: " + f.name, names.add(f.name));
			double macros = f.protein100 + f.carbs100 + f.fat100;
			assertTrue(f.name + " no puede tener más de 100 g de macros por 100", macros <= 100.5);
			// Alcohol aparte, 4/4/9 kcal por gramo debe quedar cerca de las calorías declaradas.
			double estimate = f.protein100 * 4 + f.carbs100 * 4 + f.fat100 * 9;
			if (!f.name.matches("Cerveza|Vino.*|Sangría|Vinagre"))
				assertEquals(f.name, f.kcal100, estimate, Math.max(15, f.kcal100 * 0.15));
			assertTrue(f.name, f.portion > 0);
		}
	}

	@Test
	public void typosAndRegionalNamesStillFindTheFood() {
		assertTrue("Errata", CommonFoods.search("macarones", 5).stream().anyMatch(f -> f.name.startsWith("Macarrones")));
		assertTrue("Errata", CommonFoods.search("plantano", 5).stream().anyMatch(f -> f.name.startsWith("Plátano")));
		assertTrue("Papa = patata", CommonFoods.search("papa", 8).stream().anyMatch(f -> f.name.startsWith("Patata")));
		assertTrue("Jugo = zumo", CommonFoods.search("jugo", 8).stream().anyMatch(f -> f.name.startsWith("Zumo")));
		assertEquals("Lo exacto primero", "Plátano", CommonFoods.search("platano", 5).get(0).name);
	}

	/** Caso visto en el emulador: al buscar «arroz» salía primero «Arroz con leche». */
	@Test
	public void basicFoodsComeBeforeDishesWithTheSameStart() {
		assertEquals("Arroz blanco cocido", CommonFoods.search("arroz", 8).get(0).name);
		assertEquals("Pan blanco", CommonFoods.search("pan", 8).get(0).name);
		assertEquals("Leche entera", CommonFoods.search("leche", 8).get(0).name);
		assertEquals("Huevo", CommonFoods.search("huevo", 8).get(0).name);
		List<String> rice = new ArrayList<>();
		for (CommonFoods.Food f : CommonFoods.search("arroz", 8))
			rice.add(f.name);
		assertTrue("El postre va detrás del arroz normal",
			rice.indexOf("Arroz con leche") > rice.indexOf("Arroz integral cocido"));
	}
}
