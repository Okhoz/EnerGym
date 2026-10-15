package com.energym.app.visual;

import java.util.*;

/**
 * Lector JSON mínimo y sin dependencias Android para el manifiesto local (también en pruebas JVM).
 * Devuelve Map, List, String, Double, Boolean o null; rechaza texto sobrante o mal formado.
 */
final class MiniJson {
	private final String s;
	private int i;

	private MiniJson(String s) {
		this.s = s;
	}

	static Object parse(String text) {
		MiniJson p = new MiniJson(text);
		Object value = p.value();
		p.space();
		if (p.i != p.s.length())
			throw new IllegalArgumentException("JSON con datos sobrantes");
		return value;
	}

	private Object value() {
		space();
		if (i >= s.length())
			throw new IllegalArgumentException("JSON incompleto");
		char c = s.charAt(i);
		if (c == '{')
			return object();
		if (c == '[')
			return array();
		if (c == '"')
			return string();
		if (s.startsWith("true", i)) {
			i += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", i)) {
			i += 5;
			return Boolean.FALSE;
		}
		if (s.startsWith("null", i)) {
			i += 4;
			return null;
		}
		return number();
	}

	private Map<String, Object> object() {
		Map<String, Object> out = new LinkedHashMap<>();
		i++;
		space();
		if (peek('}'))
			return out;
		while (true) {
			space();
			String key = string();
			space();
			expect(':');
			if (out.put(key, value()) != null)
				throw new IllegalArgumentException("Clave JSON duplicada: " + key);
			space();
			if (peek('}'))
				return out;
			expect(',');
		}
	}

	private List<Object> array() {
		List<Object> out = new ArrayList<>();
		i++;
		space();
		if (peek(']'))
			return out;
		while (true) {
			out.add(value());
			space();
			if (peek(']'))
				return out;
			expect(',');
		}
	}

	private String string() {
		expect('"');
		StringBuilder out = new StringBuilder();
		while (true) {
			if (i >= s.length())
				throw new IllegalArgumentException("Cadena JSON sin cerrar");
			char c = s.charAt(i++);
			if (c == '"')
				return out.toString();
			if (c != '\\') {
				out.append(c);
				continue;
			}
			char e = s.charAt(i++);
			switch (e) {
				case 'n':
					out.append('\n');
					break;
				case 't':
					out.append('\t');
					break;
				case 'r':
					out.append('\r');
					break;
				case 'b':
					out.append('\b');
					break;
				case 'f':
					out.append('\f');
					break;
				case 'u':
					out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
					i += 4;
					break;
				default:
					out.append(e);
			}
		}
	}

	private Double number() {
		int start = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0)
			i++;
		if (start == i)
			throw new IllegalArgumentException("Valor JSON no válido");
		return Double.valueOf(s.substring(start, i));
	}

	private void space() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i)))
			i++;
	}

	private boolean peek(char c) {
		if (i < s.length() && s.charAt(i) == c) {
			i++;
			return true;
		}
		return false;
	}

	private void expect(char c) {
		if (!peek(c))
			throw new IllegalArgumentException("Se esperaba '" + c + "' en JSON");
	}
}
