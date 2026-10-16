package com.energym.app.nutrition;

import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

public class BodyMeasuresTest {
	@Test
	public void savesValidatesAndComputesChange() {
		Map<String, String> disk = new HashMap<>();
		BodyMeasures.Store store = new BodyMeasures.Store() {
			public String load(String owner) {
				return disk.get(owner);
			}

			public void save(String owner, String encoded) {
				disk.put(owner, encoded);
			}
		};
		BodyMeasures m = new BodyMeasures(store);
		m.activate("ana");
		Map<BodyMeasures.Part, String> typed = new EnumMap<>(BodyMeasures.Part.class);
		typed.put(BodyMeasures.Part.WAIST, "85,04");
		assertEquals("", m.save(LocalDate.of(2026, 9, 1), typed));
		assertNull("Con una sola medida no hay cambio", m.change(BodyMeasures.Part.WAIST));
		typed.put(BodyMeasures.Part.WAIST, "82");
		typed.put(BodyMeasures.Part.FAT, "18");
		assertEquals("", m.save(LocalDate.of(2026, 10, 1), typed));
		typed.put(BodyMeasures.Part.FAT, "90");
		assertFalse(m.save(LocalDate.of(2026, 10, 2), typed).isEmpty());
		typed.clear();
		assertFalse(m.save(LocalDate.of(2026, 10, 2), typed).isEmpty());
		BodyMeasures again = new BodyMeasures(store);
		again.activate("ana");
		assertArrayEquals(new double[]{85, 82}, again.change(BodyMeasures.Part.WAIST), 0.001);
		assertEquals(2, again.days().size());
		again.remove(LocalDate.of(2026, 9, 1));
		assertEquals(1, again.days().size());
	}
}
