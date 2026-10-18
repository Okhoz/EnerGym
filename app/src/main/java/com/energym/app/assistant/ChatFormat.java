package com.energym.app.assistant;

import java.util.*;

/**
 * MODELO del formato de las respuestas del chat: Markdown mínimo (**negrita** y títulos «## ») a texto limpio
 * más los tramos en negrita, para que la vista los pinte sin depender de Android. Un «**» sin pareja se quita.
 */
public final class ChatFormat {
	public static final class Formatted {
		public final String text;
		/** Pares [inicio, fin) en {@link #text} que van en negrita. */
		public final List<int[]> bold;

		Formatted(String text, List<int[]> bold) {
			this.text = text;
			this.bold = Collections.unmodifiableList(bold);
		}
	}

	private ChatFormat() {
	}

	public static Formatted format(String raw) {
		String source = raw == null ? "" : raw.replaceAll("(?m)^#{1,6}\\s+(.+)$", "**$1**");
		StringBuilder text = new StringBuilder();
		List<int[]> bold = new ArrayList<>();
		int at = 0;
		while (at < source.length()) {
			int open = source.indexOf("**", at);
			int close = open < 0 ? -1 : source.indexOf("**", open + 2);
			if (open < 0 || close < 0) {
				text.append(source.substring(at).replace("**", ""));
				break;
			}
			text.append(source, at, open);
			int start = text.length();
			text.append(source, open + 2, close);
			if (text.length() > start)
				bold.add(new int[]{start, text.length()});
			at = close + 2;
		}
		return new Formatted(text.toString(), bold);
	}

	/** Solo el texto, sin marcas: para copiar, leer en voz alta o la descripción de accesibilidad. */
	public static String plain(String raw) {
		return format(raw).text;
	}

	/** Prefijo visible tras {@code chars} caracteres, sin cortar un emoticono por la mitad. */
	public static int reveal(String text, int chars) {
		if (text == null || chars >= text.length())
			return text == null ? 0 : text.length();
		if (chars <= 0)
			return 0;
		return Character.isHighSurrogate(text.charAt(chars - 1)) ? chars + 1 : chars;
	}
}
