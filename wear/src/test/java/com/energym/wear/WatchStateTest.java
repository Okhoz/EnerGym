package com.energym.wear;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class WatchStateTest {
	@Test
	public void readsWhatThePhonePublishes() {
		Map<String, String> m = new HashMap<>();
		m.put("active", "true");
		m.put("exercise", "Press de banca");
		m.put("set", "3");
		m.put("last", "80 kg × 8");
		m.put("restEnds", "100000");
		WatchState s = WatchState.from(m);
		assertTrue(s.active);
		assertEquals("Press de banca", s.exercise);
		assertEquals(3, s.set);
		assertEquals("80 kg × 8", s.last);
		assertEquals(90, s.restLeft(10_000));
		assertEquals(1, s.restLeft(99_500));
		assertEquals(0, s.restLeft(100_000));
	}

	@Test
	public void inactiveOrBrokenDataIsIdle() {
		assertFalse(WatchState.from(null).active);
		assertFalse(WatchState.from(Collections.singletonMap("active", "false")).active);
		Map<String, String> m = new HashMap<>();
		m.put("active", "true");
		m.put("set", "x");
		assertEquals(0, WatchState.from(m).set);
		assertEquals(0, WatchState.from(m).restLeft(0));
	}

	@Test
	public void clockFormat() {
		assertEquals("1:05", WatchState.clock(65));
		assertEquals("0:09", WatchState.clock(9));
	}
}
