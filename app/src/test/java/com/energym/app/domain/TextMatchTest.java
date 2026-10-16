package com.energym.app.domain;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TextMatchTest {
	private static int m(String term, String text) {
		return TextMatch.match(TextMatch.normalize(term), TextMatch.normalize(text));
	}

	@Test
	public void accentsPluralsPrefixesSynonymsAndTypos() {
		assertEquals(TextMatch.EXACT, m("Flexión", "flexion de brazos"));
		assertEquals(TextMatch.EXACT, m("macarrones", "Macarrón cocido"));
		assertEquals(TextMatch.PREFIX, m("sentad", "Sentadilla búlgara"));
		assertEquals(TextMatch.SYNONYM, m("papa", "Patata cocida"));
		assertEquals(TextMatch.SYNONYM, m("pectoral", "pecho"));
		assertEquals(TextMatch.TYPO, m("biseps", "Curl de bíceps"));
		assertEquals(TextMatch.TYPO, m("sentadila", "Sentadilla con mancuernas"));
		assertEquals("Transposición", TextMatch.TYPO, m("pollo", "plolo"));
		assertEquals("Escribiendo a medias con errata", TextMatch.TYPO, m("macar", "maccarrones"));
		assertEquals("Palabras cortas sin errata permitida", TextMatch.NONE, m("pan", "pez"));
		assertEquals(TextMatch.NONE, m("chocolate", "Pechuga de pollo"));
	}

	@Test
	public void everyTermMustAppearAndConnectorsAreIgnored() {
		List<String> terms = TextMatch.terms("press de banca");
		assertEquals(Arrays.asList("press", "banca"), terms);
		assertEquals(TextMatch.EXACT, TextMatch.matchAll(terms, "press banca plano"));
		assertEquals(TextMatch.NONE, TextMatch.matchAll(terms, "press militar"));
		assertEquals(TextMatch.PREFIX, TextMatch.matchAll(TextMatch.terms("pr"), "press"));
		assertEquals("Mínimo de letras configurable", TextMatch.NONE, TextMatch.matchAll(TextMatch.terms("pr"), "press", 3));
	}
}
