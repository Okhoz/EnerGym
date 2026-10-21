package com.energym.app.domain;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class PlatesTest {
	@Test
	public void loadsTheBarPerSide() {
		Plates.Load l = Plates.load(100, Plates.KG_BAR, Plates.KG_PLATES);
		assertEquals(Arrays.asList(25.0, 15.0), l.perSide);
		assertEquals(100, l.total, 0.001);
		Plates.Load odd = Plates.load(63, Plates.KG_BAR, Plates.KG_PLATES);
		assertEquals(Arrays.asList(20.0, 1.25), odd.perSide);
		assertEquals(62.5, odd.total, 0.001);
		assertTrue(Plates.load(15, Plates.KG_BAR, Plates.KG_PLATES).perSide.isEmpty());
		assertEquals(Arrays.asList(45.0, 45.0), Plates.load(225, Plates.LB_BAR, Plates.LB_PLATES).perSide);
	}

	@Test
	public void poundsRoundTripToTheSameNumber() {
		double kg = Plates.toKg(135, true);
		assertEquals(61.23, kg, 0.001);
		assertEquals(135, Plates.toUnit(kg, true), 0.001);
		assertEquals("135 lb", Plates.text(kg, true));
		assertEquals("60 kg", Plates.text(60, false));
		assertEquals("92,5 kg", Plates.text(92.5, false));
		assertEquals("Peso corporal", Plates.text(0, true));
	}

	@Test
	public void percentTableUsesEpley() {
		assertEquals(1, Plates.repsAt(100));
		assertEquals(8, Plates.repsAt(80));
		assertEquals(10, Plates.repsAt(75));
		assertEquals(80, Plates.atPercent(100, 80, false), 0.001);
		assertEquals(160, Plates.atPercent(Plates.toKg(225, true), 70, true), 0.001);
	}
}
