package com.energym.app.domain;

import java.util.*;

/**
 * MODELO de superseries y circuitos dentro de un entreno: cada índice enlazado va seguido del siguiente sin
 * descanso. Tras una serie del último del grupo se vuelve al primero y entonces sí se descansa.
 */
public final class Supersets {
	private Supersets() {
	}

	/** Ejercicio al que pasar tras apuntar una serie en {@code index}. */
	public static int after(Set<Integer> links, int index) {
		if (links.contains(index))
			return index + 1;
		return start(links, index);
	}

	/** Se descansa solo al acabar la vuelta (último ejercicio del grupo o ejercicio suelto). */
	public static boolean restsAfter(Set<Integer> links, int index) {
		return !links.contains(index);
	}

	public static boolean grouped(Set<Integer> links, int index) {
		return links.contains(index) || links.contains(index - 1);
	}

	public static int start(Set<Integer> links, int index) {
		int start = index;
		while (start > 0 && links.contains(start - 1))
			start--;
		return start;
	}

	/** Etiqueta estilo «A1», «A2», «B1»… para los ejercicios agrupados; "" para los sueltos. */
	public static String label(Set<Integer> links, int index) {
		if (!grouped(links, index))
			return "";
		int group = 0;
		for (int i = 0; i <= index; i++)
			if (grouped(links, i) && !links.contains(i - 1))
				group++;
		return (char) ('A' + Math.min(25, group - 1)) + Integer.toString(index - start(links, index) + 1);
	}

	public static String encode(Set<Integer> links) {
		List<String> parts = new ArrayList<>();
		for (int i : new TreeSet<>(links))
			parts.add(Integer.toString(i));
		return String.join(",", parts);
	}

	public static Set<Integer> decode(String text, int size) {
		Set<Integer> result = new TreeSet<>();
		if (text != null)
			for (String p : text.split(","))
				try {
					int i = Integer.parseInt(p.trim());
					if (i >= 0 && i + 1 < size)
						result.add(i);
				} catch (NumberFormatException ignored) {
					// Valor dañado: se ignora.
				}
		return result;
	}
}
