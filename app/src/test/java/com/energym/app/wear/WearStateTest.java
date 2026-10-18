package com.energym.app.wear;

import com.energym.app.domain.SetLog;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class WearStateTest {
	@Test
	public void restIsConvertedToWallClock() {
		WearState s = WearState.of("Press de banca", Arrays.asList(new SetLog.Entry(60, 10), new SetLog.Entry(80, 8)),
			1, "kg", 5_000 + 90_000, 5_000, 1_000_000);
		assertTrue(s.active);
		assertEquals(3, s.set);
		assertEquals("80 kg × 8", s.last);
		assertEquals(1_090_000, s.restEnds);
		Map<String, String> m = s.toMap();
		assertEquals("true", m.get(WearState.ACTIVE));
		assertEquals("1090000", m.get(WearState.REST_ENDS));
	}

	@Test
	public void finishedRestBodyweightAndPounds() {
		WearState s = WearState.of("Dominadas", Collections.singletonList(new SetLog.Entry(0, 12)), 1, "kg", 100, 200, 5);
		assertEquals(0, s.restEnds);
		assertEquals("12", s.last);
		WearState lb = WearState.of("Curl", Collections.singletonList(new SetLog.Entry(10, 10)), 2.20462, "lb", 0, 0, 0);
		assertEquals("22.05 lb × 10", lb.last);
		WearState first = WearState.of("Sentadilla", Collections.emptyList(), 1, "kg", 0, 0, 0);
		assertEquals(1, first.set);
		assertEquals("", first.last);
		assertFalse(WearState.idle().active);
	}

	@Test
	public void keysMatchTheWatchModule() {
		// El módulo :wear lee estas mismas claves y rutas (com.energym.wear.WatchState).
		assertEquals("/energym/session", WearState.PATH);
		assertEquals("/energym/repeat", WearState.REPEAT);
		assertEquals(new HashSet<>(Arrays.asList("active", "exercise", "set", "last", "restEnds")),
			WearState.idle().toMap().keySet());
	}
}
