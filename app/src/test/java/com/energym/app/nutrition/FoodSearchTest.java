package com.energym.app.nutrition;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FoodSearchTest {
	@Test
	public void searchResultsKeepProductsWithCaloriesAndListBrands() {
		String json = "{\"hits\":[" +
			"{\"code\":\"8410693800900\",\"product_name\":\"Macarrones\",\"brands\":[\"Auchan\"],\"quantity\":\"500 g\"," +
			"\"nutriments\":{\"energy-kcal_100g\":349,\"proteins_100g\":12,\"carbohydrates_100g\":71,\"fat_100g\":1.5}}," +
			"{\"code\":\"8410069002211\",\"product_name\":\"Sin calorías\",\"nutriments\":{}}," +
			"{\"code\":\"8410693800900\",\"product_name\":\"Repetido\",\"nutriments\":{\"energy-kcal_100g\":1}}," +
			"{\"code\":\"nope\",\"product_name\":\"Código raro\",\"nutriments\":{\"energy-kcal_100g\":1}}]}";
		List<FoodProduct> found = FoodProduct.parseSearch(json, 10);
		assertEquals(1, found.size());
		FoodProduct p = found.get(0);
		assertEquals("Macarrones · Auchan", p.displayName());
		assertEquals(349, p.kcal100, 0.001);
		assertTrue(p.complete());
		assertEquals(FoodEntry.Unit.G, p.unit);
	}
}
