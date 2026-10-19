package com.energym.app.domain;

import java.text.Normalizer;
import java.util.*;

/**
 * MODELO: coincidencia de texto para los buscadores (ejercicios y alimentos). Sin tildes ni mayúsculas, plural y
 * singular iguales, sinónimos habituales y tolerancia a erratas (1 letra en palabras de 4 a 6, 2 desde 7), también
 * mientras se escribe (la palabra a medias se compara con el principio de cada palabra).
 */
public final class TextMatch {
	/** Grado de coincidencia de un término con un texto: menor es mejor; NONE si no coincide. */
	public static final int EXACT = 0, PREFIX = 1, SYNONYM = 2, TYPO = 3, NONE = 99;

	/** Grupos de palabras equivalentes (ya normalizadas). */
	private static final String[][] SYNONYMS = {
		// Gimnasio
		{"pecho", "pectoral", "pectorales"}, {"biceps", "bicep"}, {"triceps", "tricep"},
		{"abdominales", "abdomen", "abs", "core", "abdominal"}, {"gluteo", "gluteos", "culo", "trasero", "cadera"},
		{"cuadriceps", "muslo", "quads"}, {"femoral", "isquios", "isquiotibiales", "isquiotibial"},
		{"espalda", "dorsal", "dorsales", "lumbar"}, {"hombro", "hombros", "deltoides", "deltoide"},
		{"pierna", "piernas", "tren inferior"}, {"brazo", "brazos"}, {"gemelo", "gemelos", "pantorrilla", "soleo"},
		{"sentadilla", "squat"}, {"zancada", "lunge", "estocada"}, {"flexion", "flexiones", "push up", "pushup"},
		{"dominada", "dominadas", "pull up", "pullup"}, {"remo", "row"}, {"jalon", "jalones", "pulldown"},
		{"press", "empuje"}, {"peso muerto", "deadlift"}, {"plancha", "plank"}, {"fondos", "dips"},
		{"mancuerna", "mancuernas", "pesas"}, {"barra", "barbell"}, {"polea", "cable"}, {"goma", "banda", "bandas"},
		{"correr", "carrera", "running", "trotar"}, {"bici", "bicicleta", "ciclismo"}, {"estiramiento", "estirar", "movilidad"},
		// Alimentos
		{"patata", "papa", "patatas", "papas"}, {"zumo", "jugo"}, {"judias", "alubias", "frijoles", "porotos"},
		{"yogur", "yogurt"}, {"maiz", "elote", "choclo"}, {"melocoton", "durazno"}, {"aguacate", "palta"},
		{"guisantes", "arvejas", "chicharos"}, {"cacahuete", "cacahuetes", "mani"}, {"platano", "banana"},
		{"pasta", "macarrones", "espaguetis", "fideos", "tallarines"}, {"ternera", "vaca", "carne de vaca", "res"},
		{"cerdo", "lomo", "chancho"}, {"pollo", "pechuga"}, {"refresco", "gaseosa", "soda"}, {"tortilla", "omelette"},
		{"bocadillo", "sandwich", "bocata"}, {"batido", "smoothie"}, {"gambas", "camarones"}, {"pavo", "fiambre de pavo"},
	};
	private static final Map<String, Set<String>> GROUPS = new HashMap<>();

	static {
		for (String[] group : SYNONYMS)
			for (String word : group)
				GROUPS.computeIfAbsent(stem(word), k -> new HashSet<>()).addAll(Arrays.asList(group));
	}

	private TextMatch() {
	}

	public static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}

	/** «macarrones» → «macarron», «fresas» → «fresa»: plural y singular coinciden. */
	public static String stem(String term) {
		if (term.length() > 4 && term.endsWith("es"))
			return term.substring(0, term.length() - 2);
		if (term.length() > 3 && term.endsWith("s"))
			return term.substring(0, term.length() - 1);
		return term;
	}

	/** Mejor grado de un término (normalizado) frente a las palabras de un texto (normalizado). */
	public static int match(String term, String text) {
		return match(term, text, 2);
	}

	/** Igual, exigiendo al menos {@code minPrefix} letras para coincidir con el principio de una palabra. */
	public static int match(String term, String text, int minPrefix) {
		if (term.isEmpty())
			return EXACT;
		String[] words = text.split(" ");
		int best = NONE;
		String t = stem(term);
		for (String w : words) {
			if (w.isEmpty())
				continue;
			String sw = stem(w);
			if (w.equals(term) || sw.equals(t))
				return EXACT;
			if (term.length() >= minPrefix && w.startsWith(term))
				best = Math.min(best, PREFIX);
			else if (typo(t, sw) || typo(term, w))
				best = Math.min(best, TYPO);
		}
		if (best <= PREFIX)
			return best;
		// Sinónimo: cualquier palabra del grupo presente en el texto (también expresiones de dos palabras).
		Set<String> group = GROUPS.get(t);
		if (group != null) {
			String padded = " " + text + " ";
			for (String alt : group)
				if (padded.contains(" " + alt + " ") || padded.contains(" " + stem(alt) + " ") ||
					(alt.length() >= 4 && padded.contains(" " + stem(alt))))
					return SYNONYM;
		}
		return best;
	}

	/**
	 * Grado de toda la consulta: el peor de sus términos (todos deben aparecer), NONE si alguno falta. Los
	 * conectores («de», «con»…) se ignoran.
	 */
	public static int matchAll(List<String> terms, String text) {
		return matchAll(terms, text, 2);
	}

	public static int matchAll(List<String> terms, String text, int minPrefix) {
		int worst = EXACT;
		for (String term : terms) {
			int m = match(term, text, minPrefix);
			if (m == NONE)
				return NONE;
			worst = Math.max(worst, m);
		}
		return worst;
	}

	private static final Set<String> CONNECTORS = new HashSet<>(Arrays.asList(
		"a", "al", "de", "del", "el", "la", "las", "los", "y", "en", "con", "por", "para", "un", "una", "sin",
		"an", "the", "of", "with", "and", "on", "for"));

	/** Términos útiles de una consulta (normalizados, sin conectores ni letras sueltas). */
	public static List<String> terms(String query) {
		List<String> terms = new ArrayList<>();
		for (String term : normalize(query).split(" "))
			if (term.length() >= 2 && !CONNECTORS.contains(term))
				terms.add(term);
		return terms;
	}

	/**
	 * Errata: distancia de edición (con transposición) dentro del margen. Si lo escrito es más corto que la
	 * palabra, se compara con su principio (búsqueda mientras se escribe).
	 */
	static boolean typo(String typed, String word) {
		if (typed.length() < 4)
			return false;
		int allowed = typed.length() >= 7 ? 2 : 1;
		if (Math.abs(typed.length() - word.length()) > allowed && word.length() > typed.length())
			word = word.substring(0, Math.min(word.length(), typed.length() + 1));
		if (Math.abs(typed.length() - word.length()) > allowed)
			return false;
		return distance(typed, word, allowed) <= allowed;
	}

	/** Damerau-Levenshtein acotada (para con el primer valor que supera el margen). */
	static int distance(String a, String b, int limit) {
		int n = a.length(), m = b.length();
		int[][] d = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++)
			d[i][0] = i;
		for (int j = 0; j <= m; j++)
			d[0][j] = j;
		for (int i = 1; i <= n; i++) {
			int rowMin = Integer.MAX_VALUE;
			for (int j = 1; j <= m; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				rowMin = Math.min(rowMin, d[i][j]);
			}
			if (rowMin > limit)
				return limit + 1;
		}
		// Escribiendo a medias: lo tecleado puede ser el principio de la palabra.
		int best = d[n][m];
		for (int j = Math.max(0, n - limit); j <= m; j++)
			best = Math.min(best, d[n][j]);
		return best;
	}
}
