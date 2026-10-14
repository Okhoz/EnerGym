package com.energym.app.assistant;

import java.util.*;
import java.math.BigDecimal;

/**
 * Frontera JSON pequeña y estricta: tipos, claves duplicadas, profundidad y contenido sobrante se rechazan.
 */
public final class StrictJson {
	private final String source;
	private int at;

	private static final class JsonNumber extends BigDecimal {
		final boolean integral;

		JsonNumber(String raw) {
			super(raw);
			integral = raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0;
		}
	}

	static boolean integral(Object v) {
		return v instanceof JsonNumber && ((JsonNumber) v).integral;
	}

	private StrictJson(String s) {
		source = s;
	}

	public static Object parse(String s) {
		return parse(s, 65536);
	}

	static Object parse(String s, int max) {
		if (s == null || s.length() > max)
			throw invalid();
		StrictJson p = new StrictJson(s);
		Object v = p.value(0);
		p.ws();
		if (p.at != s.length())
			throw invalid();
		return v;
	}

	/**
	 * Colecciones compatibles con minSdk26; no dependen de las fábricas Java9 ausentes en esos Android.
	 */
	static <K, V> Map<K, V> map(Object... pairs) {
		Map<K, V> m = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
			m.put((K) pairs[i], (V) pairs[i + 1]);
		return m;
	}

	static <T> Set<T> set(T... values) {
		return new HashSet<>(Arrays.asList(values));
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("JSON inválido");
	}

	private void ws() {
		while (at < source.length() && " \r\n\t".indexOf(source.charAt(at)) >= 0)
			at++;
	}

	private boolean take(char c) {
		ws();
		if (at < source.length() && source.charAt(at) == c) {
			at++;
			return true;
		}
		return false;
	}

	private Object value(int depth) {
		if (depth > 20)
			throw invalid();
		ws();
		if (at == source.length())
			throw invalid();
		char c = source.charAt(at);
		if (c == '"')
			return string();
		if (c == '{') {
			at++;
			Map<String, Object> m = new LinkedHashMap<>();
			if (take('}'))
				return m;
			do {
				ws();
				String k = string();
				if (m.containsKey(k) || !take(':'))
					throw invalid();
				m.put(k, value(depth + 1));
			} while (take(','));
			if (!take('}'))
				throw invalid();
			return m;
		}
		if (c == '[') {
			at++;
			List<Object> a = new ArrayList<>();
			if (take(']'))
				return a;
			do {
				a.add(value(depth + 1));
			} while (take(','));
			if (!take(']'))
				throw invalid();
			return a;
		}
		for (String literal : Arrays.asList("true", "false", "null"))
			if (source.startsWith(literal, at)) {
				at += literal.length();
				return literal.equals("null") ? null : Boolean.valueOf(literal);
			}
		int start = at;
		while (at < source.length() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0)
			at++;
		String n = source.substring(start, at);
		if (n.length() > 24 || !n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
			throw invalid();
		return new JsonNumber(n);
	}

	private String string() {
		if (at >= source.length() || source.charAt(at++) != '"')
			throw invalid();
		StringBuilder b = new StringBuilder();
		while (at < source.length()) {
			char c = source.charAt(at++);
			if (c == '"')
				return b.toString();
			if (c < 32)
				throw invalid();
			if (c == '\\') {
				if (at == source.length())
					throw invalid();
				c = source.charAt(at++);
				switch (c) {
					case '"':
					case '\\':
					case '/':
						break;
					case 'b':
						c = '\b';
						break;
					case 'f':
						c = '\f';
						break;
					case 'n':
						c = '\n';
						break;
					case 'r':
						c = '\r';
						break;
					case 't':
						c = '\t';
						break;
					case 'u':
						if (at + 4 > source.length())
							throw invalid();
						try {
							c = (char) Integer.parseInt(source.substring(at, at + 4), 16);
						} catch (NumberFormatException e) {
							throw invalid();
						}
						at += 4;
						break;
					default:
						throw invalid();
				}
			}
			b.append(c);
		}
		throw invalid();
	}

	public static String write(Object value) {
		if (value == null)
			return "null";
		if (value instanceof String) {
			StringBuilder b = new StringBuilder("\"");
			for (char c : ((String) value).toCharArray()) {
				if (c == '"' || c == '\\')
					b.append('\\').append(c);
				else if (c < 32)
					b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
				else
					b.append(c);
			}
			return b.append('"').toString();
		}
		if (value instanceof Boolean || value instanceof Number)
			return value.toString();
		List<String> rows = new ArrayList<>();
		if (value instanceof Map) {
			for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet())
				rows.add(write(e.getKey()) + ":" + write(e.getValue()));
			return "{" + String.join(",", rows) + "}";
		}
		if (value instanceof Collection) {
			for (Object v : (Collection<?>) value)
				rows.add(write(v));
			return "[" + String.join(",", rows) + "]";
		}
		throw invalid();
	}

	public static Map<String, Object> object(Object v) {
		if (!(v instanceof Map))
			throw invalid();
		return (Map<String, Object>) v;
	}

	public static List<Object> array(Object v, int max) {
		if (!(v instanceof List) || ((List<?>) v).size() > max)
			throw invalid();
		return (List<Object>) v;
	}

	public static String text(Object v, int max) {
		if (!(v instanceof String) || ((String) v).trim().isEmpty() || ((String) v).length() > max)
			throw invalid();
		return (String) v;
	}

	public static int integer(Object v, int min, int max) {
		if (!integral(v))
			throw invalid();
		try {
			int n = ((BigDecimal) v).intValueExact();
			if (n < min || n > max)
				throw invalid();
			return n;
		} catch (ArithmeticException e) {
			throw invalid();
		}
	}

	public static void keys(Map<String, Object> m, String... keys) {
		if (!m.keySet().equals(new HashSet<>(Arrays.asList(keys))))
			throw invalid();
	}
}

