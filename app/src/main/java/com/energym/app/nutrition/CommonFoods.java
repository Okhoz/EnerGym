package com.energym.app.nutrition;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * MODELO: alimentos genéricos habituales con valores orientativos por 100 g/ml, una ración típica y sinónimos
 * («macarrones», «penne»…). Vienen en assets/foods.tsv dentro de la app (sin red ni coste); si el envase indica
 * otra cosa, manda la etiqueta.
 */
public final class CommonFoods {
	public static final class Food {
		public final String name;
		public final FoodEntry.Unit unit;
		/** Ración típica en g o ml. */
		public final double portion, kcal100, protein100, carbs100, fat100;
		final String searchable;

		Food(String name, FoodEntry.Unit unit, double portion, double kcal, double protein, double carbs, double fat,
			String aliases) {
			this.name = name;
			this.unit = unit;
			this.portion = portion;
			this.kcal100 = kcal;
			this.protein100 = protein;
			this.carbs100 = carbs;
			this.fat100 = fat;
			searchable = " " + normalize(name + " " + aliases.replace(',', ' ')) + " ";
		}
	}

	private static volatile List<Food> all = Collections.emptyList();

	private CommonFoods() {
	}

	/** Carga la tabla (una vez al arrancar). Las líneas mal formadas se ignoran. */
	public static void load(InputStream input) throws IOException {
		List<Food> foods = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (line.trim().isEmpty() || line.startsWith("#"))
					continue;
				String[] c = line.split("\t", -1);
				if (c.length < 7)
					continue;
				try {
					foods.add(new Food(c[0].trim(), FoodEntry.Unit.valueOf(c[1].trim()), Double.parseDouble(c[2]),
						Double.parseDouble(c[3]), Double.parseDouble(c[4]), Double.parseDouble(c[5]),
						Double.parseDouble(c[6]), c.length > 7 ? c[7] : ""));
				} catch (IllegalArgumentException ignored) {
					// Fila incompleta: no se ofrece.
				}
			}
		}
		all = Collections.unmodifiableList(foods);
	}

	public static List<Food> all() {
		return all;
	}

	/**
	 * Alimentos cuyo nombre o sinónimos contienen todas las palabras escritas (sin tildes ni mayúsculas; admite
	 * plurales). Primero los que empiezan por la búsqueda o la tienen como sinónimo exacto. Menos de 2 letras no busca.
	 */
	public static List<Food> search(String query, int limit) {
		String q = normalize(query);
		if (q.length() < 2)
			return Collections.emptyList();
		String[] terms = q.split(" ");
		List<Food> first = new ArrayList<>(), rest = new ArrayList<>();
		for (Food f : all) {
			boolean every = true;
			for (String t : terms)
				if (!f.searchable.contains(" " + stem(t))) {
					every = false;
					break;
				}
			if (!every)
				continue;
			String name = normalize(f.name);
			(name.startsWith(q) || name.startsWith(stem(q)) || f.searchable.contains(" " + q + " ") ? first : rest).add(f);
		}
		first.addAll(rest);
		return first.size() > limit ? first.subList(0, limit) : first;
	}

	/** «macarrones» → «macarron», «fresas» → «fresa»: así el plural encuentra el singular y al revés. */
	private static String stem(String term) {
		if (term.length() > 4 && term.endsWith("es"))
			return term.substring(0, term.length() - 2);
		if (term.length() > 3 && term.endsWith("s"))
			return term.substring(0, term.length() - 1);
		return term;
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
