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
}
