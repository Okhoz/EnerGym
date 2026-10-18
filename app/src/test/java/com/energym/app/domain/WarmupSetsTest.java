package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class WarmupSetsTest {
	@Test
	public void barbellRampsFromEmptyBar() {
		List<SetLog.Entry> w = WarmupSets.of(100, 20, 2.5);
		assertEquals(Arrays.asList(new SetLog.Entry(20, 10, SetLog.Kind.WARMUP, 0),
			new SetLog.Entry(40, 5, SetLog.Kind.WARMUP, 0), new SetLog.Entry(60, 3, SetLog.Kind.WARMUP, 0),
			new SetLog.Entry(80, 2, SetLog.Kind.WARMUP, 0)), w);
		for (SetLog.Entry e : w)
			assertFalse("El calentamiento no cuenta para progresar", e.counts());
	}

	@Test
	public void roundsToStep() {
		List<SetLog.Entry> w = WarmupSets.of(67.5, 20, 2.5);
		assertEquals(27.5, w.get(1).kg, 0.001);
		assertEquals(40, w.get(2).kg, 0.001);
		assertEquals(55, w.get(3).kg, 0.001);
	}

	@Test
	public void lightWorkSkipsRepeatedOrTooLightSets() {
		// 40 kg: el 40 % (15) no supera la barra; quedan barra, 25 y 32,5.
		List<SetLog.Entry> w = WarmupSets.of(40, 20, 2.5);
		assertEquals(3, w.size());
		assertEquals(20, w.get(0).kg, 0.001);
		assertEquals(25, w.get(1).kg, 0.001);
		assertEquals(32.5, w.get(2).kg, 0.001);
		assertTrue(WarmupSets.of(22.5, 20, 2.5).isEmpty());
		assertTrue(WarmupSets.of(20, 20, 2.5).isEmpty());
	}

	@Test
	public void withoutBarStartsAtFortyPercent() {
		List<SetLog.Entry> w = WarmupSets.of(30, 0, 2);
		assertEquals(12, w.get(0).kg, 0.001);
		assertEquals(5, w.get(0).reps);
		assertEquals(3, w.size());
		for (int i = 1; i < w.size(); i++)
			assertTrue(w.get(i).kg > w.get(i - 1).kg);
	}

	@Test
	public void invalidInputsGiveNothing() {
		assertTrue(WarmupSets.of(0, 20, 2.5).isEmpty());
		assertTrue(WarmupSets.of(100, 20, 0).isEmpty());
		assertTrue(WarmupSets.of(100, -1, 2.5).isEmpty());
	}

	@Test
	public void neverReachesWorkingWeight() {
		for (double kg = 25; kg <= 300; kg += 2.5)
			for (SetLog.Entry e : WarmupSets.of(kg, 20, 2.5))
				assertTrue(kg + " → " + e.kg, e.kg < kg);
	}
}
