package com.energym.app.progress;

import com.energym.app.nutrition.BodyMeasures;
import com.energym.app.nutrition.FoodEntry;

import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class CsvBackupTest {
	@Test
	public void foodsWeightsAndMeasuresRoundTrip() {
		LocalDate day = LocalDate.of(2026, 10, 15);
		List<FoodEntry> foods = Collections.singletonList(FoodEntry.create(day, FoodEntry.Meal.LUNCH,
			"Macarrones, \"integrales\"", "8410", FoodEntry.Unit.G, 120.5, 350, 12, 70, 2));
		SortedMap<LocalDate, Double> weights = new TreeMap<>(Collections.singletonMap(day, 78.4));
		SortedMap<LocalDate, Map<BodyMeasures.Part, Double>> measures = new TreeMap<>();
		measures.put(day, new EnumMap<>(Collections.singletonMap(BodyMeasures.Part.WAIST, 82.5)));
		String csv = CsvBackup.export(Collections.emptyList(), ZoneOffset.UTC, foods, weights, measures);
		assertTrue(csv.startsWith(CsvBackup.HEADER + "\n"));
		CsvBackup.Imported back = CsvBackup.parse(csv);
		assertEquals(0, back.skipped);
		assertEquals(1, back.foods.size());
		FoodEntry f = back.foods.get(0);
		assertEquals("Macarrones, \"integrales\"", f.name);
		assertEquals(120.5, f.amount, 0.001);
		assertEquals(FoodEntry.Meal.LUNCH, f.meal);
		assertEquals("8410", f.barcode);
		assertEquals(78.4, back.weights.get(day), 0.001);
		assertEquals("82.5", back.measures.get(day).get(BodyMeasures.Part.WAIST));
	}

	@Test
	public void badRowsAreCountedAndFormulasNeutralized() {
		CsvBackup.Imported r = CsvBackup.parse(CsvBackup.HEADER + "\nfoo,bar\npeso,2026-01-01,,,,,,,,,,,,,,500,\n");
		assertEquals(2, r.skipped);
		LocalDate day = LocalDate.of(2026, 1, 1);
		String csv = CsvBackup.export(Collections.emptyList(), ZoneOffset.UTC, Collections.singletonList(
			FoodEntry.create(day, FoodEntry.Meal.OTHER, "=HYPERLINK(1)", "", FoodEntry.Unit.G, 10, 1, 0, 0, 0)),
			new TreeMap<>(), new TreeMap<>());
		assertTrue(csv.contains(",'=HYPERLINK(1),"));
	}
}
