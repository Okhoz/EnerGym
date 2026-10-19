package com.energym.app.nutrition;

import com.energym.app.domain.TextMatch;
import com.energym.app.i18n.Txt;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * MODELO: alimentos genéricos habituales con valores orientativos por 100 g/ml, una ración típica y sinónimos
 * («macarrones», «penne»…). Vienen en assets/foods.tsv dentro de la app (sin red ni coste); si el envase indica
 * otra cosa, manda la etiqueta. Cada uno tiene también nombre y sinónimos en inglés: con la app en inglés se
 * muestran y se buscan en inglés (y en español), pero lo anotado se guarda siempre con el nombre en español.
 */
public final class CommonFoods {
	public static final class Food {
		/** Nombre en español: el que se guarda en el diario y recibe la IA. */
		public final String name;
		/** Nombre en inglés; vacío si la tabla no lo trae (entonces se muestra el español). */
		public final String nameEn;
		public final FoodEntry.Unit unit;
		/** Ración típica en g o ml. */
		public final double portion, kcal100, protein100, carbs100, fat100;
		final String searchable, searchableEn;

		Food(String name, FoodEntry.Unit unit, double portion, double kcal, double protein, double carbs, double fat,
			String aliases, String nameEn, String aliasesEn) {
			this.name = name;
			this.nameEn = nameEn;
			this.unit = unit;
			this.portion = portion;
			this.kcal100 = kcal;
			this.protein100 = protein;
			this.carbs100 = carbs;
			this.fat100 = fat;
			searchable = " " + normalize(name + " " + aliases.replace(',', ' ')) + " ";
			searchableEn = " " + normalize(nameEn + " " + aliasesEn.replace(',', ' ')) + searchable;
		}

		/** Nombre en el idioma de la app. */
		public String label() {
			return Txt.english() && !nameEn.isEmpty() ? nameEn : name;
		}
	}

	private static volatile List<Food> all = Collections.emptyList();
	/** Nombre en español y en inglés (en minúsculas) → alimento, para traducir lo anotado. */
	private static volatile Map<String, Food> bySpanish = Collections.emptyMap(), byEnglish = Collections.emptyMap();

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
						Double.parseDouble(c[6]), c.length > 7 ? c[7] : "", c.length > 8 ? c[8].trim() : "",
						c.length > 9 ? c[9] : ""));
				} catch (IllegalArgumentException ignored) {
					// Fila incompleta: no se ofrece.
				}
			}
		}
		Map<String, Food> spanish = new HashMap<>(), english = new HashMap<>();
		for (Food f : foods) {
			spanish.putIfAbsent(f.name.toLowerCase(Locale.ROOT), f);
			if (!f.nameEn.isEmpty())
				english.putIfAbsent(f.nameEn.toLowerCase(Locale.ROOT), f);
		}
		all = Collections.unmodifiableList(foods);
		bySpanish = spanish;
		byEnglish = english;
	}

	/** Nombre anotado tal como se muestra: los alimentos de la tabla, en el idioma de la app. */
	public static String label(String stored) {
		if (stored == null || !Txt.english())
			return stored;
		Food f = bySpanish.get(stored.trim().toLowerCase(Locale.ROOT));
		return f == null ? stored : f.label();
	}

	/** Nombre que se guarda: con la app en inglés, un alimento de la tabla vuelve a su nombre en español. */
	public static String stored(String shown) {
		if (shown == null || !Txt.english())
			return shown;
		Food f = byEnglish.get(shown.trim().toLowerCase(Locale.ROOT));
		return f == null ? shown : f.name;
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
		// En inglés: primero el nombre en inglés y, detrás, sus sinónimos y todo lo español.
		boolean english = Txt.english();
		List<Food> found = new ArrayList<>();
		Map<Food, Integer> rank = new HashMap<>();
		for (Food f : all) {
			String name = normalize(f.label());
			int score;
			// En inglés lo principal no siempre va delante («White rice, cooked»): basta con la palabra exacta.
			if (name.startsWith(q) || name.startsWith(stem(q)) ||
				english && TextMatch.matchAll(terms, name) == TextMatch.EXACT)
				score = 0;
			else {
				int inName = TextMatch.matchAll(terms, name);
				int inAll = TextMatch.matchAll(terms, (english ? f.searchableEn : f.searchable).trim());
				score = inName <= TextMatch.PREFIX ? 1 : inAll <= TextMatch.PREFIX ? 2 :
					inAll == TextMatch.SYNONYM ? 3 : inAll == TextMatch.TYPO ? 4 : -1;
			}
			if (score >= 0) {
				found.add(f);
				rank.put(f, score);
			}
		}
		// A igual relevancia se respeta el orden de la tabla (la ordenación es estable), que pone primero lo básico y
		// como se suele comer: «Arroz blanco cocido» antes que «Arroz con leche», aunque este tenga el nombre más corto.
		found.sort(Comparator.comparingInt(rank::get));
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
