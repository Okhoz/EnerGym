package com.energym.app.progress;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AchievementsTest {
	@Test
	public void showsEarnedBadgesAndTheNextOneOfEachFamily() {
		List<Achievements.Badge> badges = Achievements.of(12, 0, 0, 0, 0, 0);
		List<String> titles = new ArrayList<>();
		for (Achievements.Badge b : badges)
			titles.add(b.title + (b.earned() ? "✓" : ""));
		assertTrue(titles.contains("Primer entreno✓"));
		assertTrue(titles.contains("10 entrenos✓"));
		assertTrue(titles.contains("25 entrenos"));
		assertFalse(titles.contains("50 entrenos"));
		assertTrue("Sin récords aún se muestra el primero", titles.contains("Primer récord"));
		Achievements.Badge next = null;
		for (Achievements.Badge b : badges)
			if (b.title.equals("25 entrenos"))
				next = b;
		assertEquals("12 de 25 entrenos terminados", next.detail);
		assertEquals(6, Achievements.of(0, 0, 0, 0, 0, 0).size());
	}
}
