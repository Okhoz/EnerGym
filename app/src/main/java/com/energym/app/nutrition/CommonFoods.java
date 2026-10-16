package com.energym.app.nutrition;

import com.energym.app.domain.TextMatch;

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
	/**
	 * Alimentos que encajan con lo escrito, de más a menos relevante: empieza por lo escrito, todas las palabras en
	 * el nombre, en sus alias, por sinónimo («papa» → patata) y, al final, con una errata («maccarones»).
	 */
	public static List<Food> search(String query, int limit) {
		String q = normalize(query);
		if (q.length() < 2)
			return Collections.emptyList();
		List<String> terms = TextMatch.terms(q);
		if (terms.isEmpty())
			return Collections.emptyList();
		List<Food> found = new ArrayList<>();
		Map<Food, Integer> rank = new HashMap<>();
		for (Food f : all) {
			String name = normalize(f.name);
			int score;
			if (name.startsWith(q) || name.startsWith(stem(q)))
				score = 0;
			else {
				int inName = TextMatch.matchAll(terms, name);
				int inAll = TextMatch.matchAll(terms, f.searchable.trim());
				score = inName <= TextMatch.PREFIX ? 1 : inAll <= TextMatch.PREFIX ? 2 :
					inAll == TextMatch.SYNONYM ? 3 : inAll == TextMatch.TYPO ? 4 : -1;
			}
			if (score >= 0) {
				found.add(f);
				rank.put(f, score);
			}
		}
		// A igual relevancia, los nombres más cortos (más genéricos) primero: «Arroz blanco» antes que «Arroz con…».
		found.sort(Comparator.<Food>comparingInt(rank::get).thenComparingInt(f -> f.name.length()));
		return found.size() > limit ? found.subList(0, limit) : found;
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
