package com.energym.app.nutrition;

import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

public class WaterLogTest {
	@Test
	public void glassesAddUpPerAccountAndDayWithinLimits() {
		Map<String, Integer> saved = new HashMap<>();
		int[] changes = {0};
		WaterLog w = new WaterLog(new WaterLog.Store() {
			public int load(String key) {
				return saved.getOrDefault(key, 0);
			}

			public void save(String key, int ml) {
				saved.put(key, ml);
			}
		}, () -> changes[0]++);
		LocalDate day = LocalDate.of(2026, 10, 16);
		w.add(day, WaterLog.GLASS);
		assertEquals("Sin cuenta no se guarda nada", 0, w.ml(day));
		w.activate("ana");
		w.add(day, WaterLog.GLASS);
		w.add(day, WaterLog.GLASS);
		w.add(day, -WaterLog.GLASS * 5);
		assertEquals("Nunca baja de cero", 0, w.ml(day));
		w.add(day, WaterLog.GLASS * 3);
		assertEquals(750, w.ml(day));
		assertEquals(0, w.ml(day.plusDays(1)));
		w.activate("luis");
		assertEquals("Cada cuenta lleva su agua", 0, w.ml(day));
		assertEquals("0,8 L", WaterLog.liters(750));
	}

	@Test
	public void goalIs35mlPerKiloRoundedToGlasses() {
		BodyProfile b = new BodyProfile(BodyProfile.Sex.MALE, 30, 178, 70, BodyProfile.Activity.MODERATE,
			BodyProfile.Goal.MAINTAIN, "");
		assertEquals(2500, WaterLog.goal(b));
		assertEquals(2000, WaterLog.goal(null));
	}
}
