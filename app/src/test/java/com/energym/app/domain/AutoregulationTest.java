package com.energym.app.domain;

import org.junit.Test;

import static org.junit.Assert.*;

public class AutoregulationTest {
	@Test
	public void adjustsLoadTowardsRpeEight() {
		assertNull(Autoregulation.next(new SetLog.Entry(60, 8), 2.5));
		assertNull("El calentamiento no cuenta", Autoregulation.next(new SetLog.Entry(40, 10, SetLog.Kind.WARMUP, 5), 2.5));
		assertEquals(65, Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 6), 2.5).kg, 0.001);
		assertEquals(62.5, Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 7), 2.5).kg, 0.001);
		assertEquals(60, Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 8), 2.5).kg, 0.001);
		assertEquals(60, Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 9), 2.5).kg, 0.001);
		assertEquals(55, Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 10), 2.5).kg, 0.001);
		assertEquals(55, Autoregulation.next(new SetLog.Entry(60, 6, SetLog.Kind.FAILURE, 8), 2.5).kg, 0.001);
		assertTrue(Autoregulation.next(new SetLog.Entry(60, 8, SetLog.Kind.NORMAL, 7), 2.5).text.contains("62,5 kg"));
	}

	@Test
	public void bodyweightAdjustsRepetitions() {
		assertEquals(12, Autoregulation.next(new SetLog.Entry(0, 10, SetLog.Kind.NORMAL, 6), 2.5).reps);
		assertEquals(11, Autoregulation.next(new SetLog.Entry(0, 10, SetLog.Kind.NORMAL, 7), 2.5).reps);
		assertEquals(10, Autoregulation.next(new SetLog.Entry(0, 10, SetLog.Kind.NORMAL, 8), 2.5).reps);
		assertEquals(8, Autoregulation.next(new SetLog.Entry(0, 10, SetLog.Kind.NORMAL, 10), 2.5).reps);
	}
}
