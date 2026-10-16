package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class SetLogTest {
	@Test
	public void encodesParsesAndSummarizesSetsForProgression() {
		List<SetLog.Entry> sets = Arrays.asList(new SetLog.Entry(60, 5), new SetLog.Entry(62.5, 5), new SetLog.Entry(62.5, 4));
		String encoded = SetLog.encode(sets);
		assertEquals("60:5;62.5:5;62.5:4", encoded);
		assertEquals(sets, SetLog.parse(encoded));
		Map<String, String> summary = SetLog.summary(sets, null, "90");
		assertEquals("3", summary.get("sets"));
		assertEquals("5", summary.get("repetitions"));
		assertEquals("62.5", summary.get("loadKg"));
		assertEquals("90", summary.get("restSeconds"));
		assertTrue("El resumen es válido para el validador de fuerza",
			new MetricValidator().validate(MetricType.STRENGTH, summary).isEmpty());
		assertEquals("El validador conserva las series", encoded,
			new MetricValidator().normalized(MetricType.STRENGTH, summary).get(SetLog.KEY));
		assertNull(SetLog.summary(Collections.emptyList(), null, "60"));
	}

	@Test
	public void oldSummariesStillShowTheirSetsAndBadInputIsIgnored() {
		Map<String, String> old = new HashMap<>();
		old.put("sets", "2");
		old.put("repetitions", "8");
		old.put("loadKg", "20");
		assertEquals(Arrays.asList(new SetLog.Entry(20, 8), new SetLog.Entry(20, 8)), SetLog.of(old));
		assertTrue(SetLog.parse("60:5;oops").isEmpty());
		assertFalse(SetLog.valid("1e9:5"));
		Map<String, String> tampered = new HashMap<>(old);
		tampered.put(SetLog.KEY, "nope");
		assertNull(new MetricValidator().normalized(MetricType.STRENGTH, tampered).get(SetLog.KEY));
	}

	@Test
	public void oneRepMaxUsesEpley() {
		assertEquals(100, new SetLog.Entry(100, 1).oneRepMax(), 0.001);
		assertEquals(116.67, new SetLog.Entry(100, 5).oneRepMax(), 0.01);
		assertEquals(116.67, SetLog.best(Arrays.asList(new SetLog.Entry(80, 5), new SetLog.Entry(100, 5))), 0.01);
	}
}
