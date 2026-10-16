package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class SupersetsTest {
	@Test
	public void circuitsGoRoundAndRestAtTheEnd() {
		// 0 suelto · 1-2-3 circuito · 4-5 superserie.
		Set<Integer> links = Supersets.decode("1,2,4,9,x", 6);
		assertEquals("1,2,4", Supersets.encode(links));
		assertEquals(0, Supersets.after(links, 0));
		assertTrue(Supersets.restsAfter(links, 0));
		assertEquals(2, Supersets.after(links, 1));
		assertFalse(Supersets.restsAfter(links, 1));
		assertEquals(3, Supersets.after(links, 2));
		assertEquals(1, Supersets.after(links, 3));
		assertTrue(Supersets.restsAfter(links, 3));
		assertEquals(5, Supersets.after(links, 4));
		assertEquals(4, Supersets.after(links, 5));
		assertEquals("", Supersets.label(links, 0));
		assertEquals("A1", Supersets.label(links, 1));
		assertEquals("A3", Supersets.label(links, 3));
		assertEquals("B2", Supersets.label(links, 5));
	}
}
