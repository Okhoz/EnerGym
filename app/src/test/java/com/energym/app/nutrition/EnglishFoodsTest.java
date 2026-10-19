package com.energym.app.nutrition;

import com.energym.app.i18n.SourceStrings;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.*;

import static org.junit.Assert.*;

/** Alimentos con la app en inglés: se ven y se buscan en inglés, pero se guardan con el nombre en español. */
public class EnglishFoodsTest {
	@BeforeClass
	public static void load() throws Exception {
		try (FileInputStream in = new FileInputStream("src/main/assets/foods.tsv")) {
			CommonFoods.load(in);
		}
	}

	@After
	public void spanish() {
		SourceStrings.use(Locale.forLanguageTag("es-ES"));
	}

	@Test
	public void everyFoodHasAnEnglishName() {
		Set<String> english = new HashSet<>();
		for (CommonFoods.Food f : CommonFoods.all()) {
			assertFalse(f.name + " sin nombre en inglés", f.nameEn.trim().isEmpty());
			assertTrue("Nombre en inglés repetido: " + f.nameEn, english.add(f.nameEn.toLowerCase(Locale.ROOT)));
		}
		assertTrue(CommonFoods.all().size() >= 300);
	}

	@Test
	public void englishSearchFindsRiceAndAlsoSpanishNames() {
		SourceStrings.use(Locale.ENGLISH);
		List<CommonFoods.Food> rice = CommonFoods.search("rice", 8);
		assertEquals("Lo básico primero", "White rice, cooked", rice.get(0).label());
		for (CommonFoods.Food f : rice)
			assertTrue(f.label(), f.label().toLowerCase(Locale.ROOT).contains("rice"));
		assertEquals("Banana", CommonFoods.search("banana", 5).get(0).label());
		assertTrue("Plural", CommonFoods.search("eggs", 5).stream().anyMatch(f -> f.label().equals("Egg")));
		assertTrue("Sinónimo en inglés", CommonFoods.search("zucchini", 5).stream()
			.anyMatch(f -> f.label().equals("Courgette")));
		assertTrue("También en español", CommonFoods.search("arroz", 8).stream()
			.anyMatch(f -> f.name.equals("Arroz blanco cocido")));
	}

	@Test
	public void diaryKeepsTheSpanishNameAndShowsTheActiveLanguage() {
		CommonFoods.Food rice = CommonFoods.search("arroz blanco cocido", 1).get(0);
		assertEquals("Arroz blanco cocido", rice.label());
		assertEquals("Arroz blanco cocido", CommonFoods.label("Arroz blanco cocido"));
		SourceStrings.use(Locale.ENGLISH);
		assertEquals("White rice, cooked", rice.label());
		assertEquals("White rice, cooked", CommonFoods.label("Arroz blanco cocido"));
		assertEquals("Arroz blanco cocido", CommonFoods.stored("white rice, cooked"));
		assertEquals("Mi batido casero", CommonFoods.stored("Mi batido casero"));
		assertEquals("Mi batido casero", CommonFoods.label("Mi batido casero"));
	}
}
