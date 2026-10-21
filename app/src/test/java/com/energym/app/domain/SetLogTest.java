package com.energym.app.domain;

import com.energym.app.i18n.SourceStrings;

import org.junit.After;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class SetLogTest {
	@After
	public void spanish() {
		SourceStrings.use(Locale.forLanguageTag("es-ES"));
	}

	@Test
	public void displayFollowsTheAppLanguageButWhatIsSavedKeepsTheDot() {
		assertEquals("92,5", SetLog.display(92.5));
		assertEquals("1,25", SetLog.display(1.25));
		assertEquals("Sin decimales no hay coma", "100", SetLog.display(100));
		assertEquals("92.5", SetLog.format(92.5));
		SourceStrings.use(Locale.ENGLISH);
		assertEquals("92.5", SetLog.display(92.5));
		assertEquals("22.05", SetLog.display(22.05));
		assertEquals("100", SetLog.display(100));
		// Lo guardado no depende del idioma: así se sigue leyendo en cualquier móvil y en la copia CSV.
		for (Locale l : Arrays.asList(Locale.ENGLISH, Locale.forLanguageTag("es-ES"))) {
			SourceStrings.use(l);
			List<SetLog.Entry> sets = Arrays.asList(new SetLog.Entry(92.5, 5, SetLog.Kind.NORMAL, 7.5));
			assertEquals("92.5:5:N:7.5", SetLog.encode(sets));
			assertEquals("92.5", SetLog.summary(sets, null, "90").get("loadKg"));
		}
	}

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

	@Test
	public void kindsRpeAndNotesRoundTripAndWarmupsDoNotCount() {
		List<SetLog.Entry> sets = Arrays.asList(new SetLog.Entry(40, 10, SetLog.Kind.WARMUP, 0),
			new SetLog.Entry(80, 5, SetLog.Kind.NORMAL, 8), new SetLog.Entry(80, 4, SetLog.Kind.FAILURE, 10),
			new SetLog.Entry(60, 8, SetLog.Kind.DROP, 0));
		String encoded = SetLog.encode(sets);
		assertEquals("40:10:W;80:5:N:8;80:4:F:10;60:8:D", encoded);
		assertEquals(sets, SetLog.parse(encoded));
		assertEquals("Los registros antiguos siguen igual", "60:5", SetLog.encode(Arrays.asList(new SetLog.Entry(60, 5))));
		Map<String, String> base = new HashMap<>();
		base.put(SetLog.NOTE, "asiento en el 4");
		Map<String, String> summary = SetLog.summary(sets, base, "90");
		assertEquals("El calentamiento no cuenta como serie", "3", summary.get("sets"));
		assertEquals("9", summary.get("effort"));
		assertEquals("asiento en el 4", summary.get(SetLog.NOTE));
		assertEquals(93.33, SetLog.best(sets), 0.01);
		assertEquals(0, SetLog.best(Arrays.asList(new SetLog.Entry(200, 3, SetLog.Kind.WARMUP, 0))), 0.001);
		Map<String, String> clean = new MetricValidator().normalized(MetricType.STRENGTH, summary);
		assertEquals(encoded, clean.get(SetLog.KEY));
		assertEquals("asiento en el 4", clean.get(SetLog.NOTE));
		assertTrue(new MetricValidator().validate(MetricType.STRENGTH, summary).isEmpty());
	}
}
