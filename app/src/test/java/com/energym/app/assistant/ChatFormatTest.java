package com.energym.app.assistant;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChatFormatTest {
	@Test
	public void boldMarksBecomeSpans() {
		ChatFormat.Formatted f = ChatFormat.format("Sube a **82,5 kg** y busca **8 reps**.");
		assertEquals("Sube a 82,5 kg y busca 8 reps.", f.text);
		assertEquals(2, f.bold.size());
		assertEquals("82,5 kg", f.text.substring(f.bold.get(0)[0], f.bold.get(0)[1]));
		assertEquals("8 reps", f.text.substring(f.bold.get(1)[0], f.bold.get(1)[1]));
	}

	@Test
	public void unpairedMarksAreRemoved() {
		ChatFormat.Formatted f = ChatFormat.format("Hola **amigo");
		assertEquals("Hola amigo", f.text);
		assertTrue(f.bold.isEmpty());
	}

	@Test
	public void emptyBoldIsIgnored() {
		ChatFormat.Formatted f = ChatFormat.format("a****b");
		assertEquals("ab", f.text);
		assertTrue(f.bold.isEmpty());
	}

	@Test
	public void headingsBecomeBoldLines() {
		ChatFormat.Formatted f = ChatFormat.format("## Técnica\n• Espalda recta");
		assertEquals("Técnica\n• Espalda recta", f.text);
		assertArrayEquals(new int[]{0, 7}, f.bold.get(0));
	}

	@Test
	public void plainTextIsUntouched() {
		assertEquals("¡Vamos! 💪\n\n• Respira.", ChatFormat.plain("¡Vamos! 💪\n\n• Respira."));
		assertEquals("", ChatFormat.plain(null));
		assertTrue(ChatFormat.format("").bold.isEmpty());
	}

	@Test
	public void revealNeverSplitsAnEmoji() {
		String text = "a💪b";
		assertEquals(0, ChatFormat.reveal(text, 0));
		assertEquals(1, ChatFormat.reveal(text, 1));
		assertEquals(3, ChatFormat.reveal(text, 2));
		assertEquals(4, ChatFormat.reveal(text, 10));
		assertEquals(0, ChatFormat.reveal(null, 3));
	}

	@Test
	public void boldSpansSurviveAcrossLinesAndBullets() {
		ChatFormat.Formatted f = ChatFormat.format("• **Sentadilla:** baja\n• **Puente:** sube");
		assertEquals("• Sentadilla: baja\n• Puente: sube", f.text);
		assertEquals("Sentadilla:", f.text.substring(f.bold.get(0)[0], f.bold.get(0)[1]));
		assertEquals("Puente:", f.text.substring(f.bold.get(1)[0], f.bold.get(1)[1]));
	}

	@Test
	public void spansAreInsideTheTextAndOrdered() {
		ChatFormat.Formatted f = ChatFormat.format("**a** b **c** d **e");
		assertEquals("a b c d e", f.text);
		int previous = 0;
		for (int[] b : f.bold) {
			assertTrue(b[0] >= previous && b[0] < b[1] && b[1] <= f.text.length());
			previous = b[1];
		}
		assertEquals(2, f.bold.size());
	}

	@Test
	public void onlyRealHeadingsBecomeBold() {
		assertEquals("#hashtag", ChatFormat.plain("#hashtag"));
		assertEquals("3 # series", ChatFormat.plain("3 # series"));
		assertEquals(1, ChatFormat.format("### Calentamiento").bold.size());
	}

	@Test
	public void revealGrowsUntilTheWholeText() {
		String text = "¡Vamos! 💪🔥 Sube a 82,5 kg.";
		int at = 0, steps = 0;
		while (at < text.length()) {
			int next = ChatFormat.reveal(text, at + 2);
			assertTrue(next > at);
			assertFalse("Nunca medio emoticono", Character.isHighSurrogate(text.charAt(next - 1)));
			at = next;
			steps++;
		}
		assertEquals(text.length(), at);
		assertTrue(steps < text.length());
	}
}
