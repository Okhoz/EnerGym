package com.energym.app.nutrition;

import java.text.Normalizer;
import java.util.*;

/**
 * MODELO: alimentos genéricos habituales con valores orientativos por 100 g/ml y una ración típica. Va dentro de la
 * app (sin red ni coste) para anotar lo básico sin copiar etiquetas; si el envase indica otra cosa, manda la etiqueta.
 */
public final class CommonFoods {
	public static final class Food {
		public final String name;
		public final FoodEntry.Unit unit;
		/** Ración típica en g o ml. */
		public final double portion, kcal100, protein100, carbs100, fat100;

		Food(String name, FoodEntry.Unit unit, double portion, double kcal, double protein, double carbs, double fat) {
			this.name = name;
			this.unit = unit;
			this.portion = portion;
			this.kcal100 = kcal;
			this.protein100 = protein;
			this.carbs100 = carbs;
			this.fat100 = fat;
		}
	}

	private static final List<Food> ALL = new ArrayList<>();

	private static void g(String name, double portion, double kcal, double protein, double carbs, double fat) {
		ALL.add(new Food(name, FoodEntry.Unit.G, portion, kcal, protein, carbs, fat));
	}

	private static void ml(String name, double portion, double kcal, double protein, double carbs, double fat) {
		ALL.add(new Food(name, FoodEntry.Unit.ML, portion, kcal, protein, carbs, fat));
	}

	static {
		// Cereales, tubérculos y legumbres (cocinados salvo que se indique).
		g("Arroz blanco cocido", 150, 130, 2.7, 28, 0.3);
		g("Arroz integral cocido", 150, 123, 2.7, 25.6, 1);
		g("Pasta cocida", 150, 158, 5.8, 31, 0.9);
		g("Pan blanco", 60, 265, 9, 49, 3.2);
		g("Pan integral", 60, 247, 13, 41, 3.4);
		g("Copos de avena", 40, 389, 16.9, 66, 6.9);
		g("Cereales de desayuno (copos de maíz)", 40, 357, 7.5, 84, 0.4);
		g("Patata cocida", 200, 87, 1.9, 20, 0.1);
		g("Boniato cocido", 200, 90, 2, 20.7, 0.2);
		g("Quinoa cocida", 150, 120, 4.4, 21.3, 1.9);
		g("Lentejas cocidas", 200, 116, 9, 20, 0.4);
		g("Garbanzos cocidos", 200, 164, 8.9, 27.4, 2.6);
		g("Alubias cocidas", 200, 127, 8.7, 22.8, 0.5);
		g("Hummus", 50, 166, 7.9, 14.3, 9.6);
		// Carnes, pescados y huevos.
		g("Pechuga de pollo a la plancha", 150, 165, 31, 0, 3.6);
		g("Muslo de pollo asado", 150, 209, 26, 0, 10.9);
		g("Pechuga de pavo", 150, 135, 30, 0, 1);
		g("Ternera magra a la plancha", 150, 170, 26, 0, 7);
		g("Lomo de cerdo a la plancha", 150, 143, 26, 0, 3.5);
		g("Carne picada de vacuno cocinada", 125, 250, 26, 0, 15);
		g("Jamón serrano", 40, 241, 31, 0.5, 13);
		g("Jamón cocido (york)", 50, 110, 18, 1.5, 3.5);
		g("Salmón", 150, 208, 20, 0, 13);
		g("Merluza", 150, 90, 18, 0, 1.5);
		g("Atún al natural (lata, escurrido)", 80, 116, 26, 0, 1);
		g("Atún en aceite (lata, escurrido)", 80, 198, 29, 0, 8);
		g("Sardinas en aceite (lata)", 80, 208, 25, 0, 11.5);
		g("Gambas cocidas", 100, 99, 24, 0.2, 0.3);
		g("Huevo", 55, 143, 12.6, 0.7, 9.5);
		g("Clara de huevo", 100, 52, 11, 0.7, 0.2);
		g("Tortilla de patata", 150, 180, 6.5, 13, 11);
		g("Tofu firme", 150, 120, 13, 2, 7);
		// Lácteos y bebidas vegetales.
		ml("Leche entera", 250, 64, 3.2, 4.7, 3.6);
		ml("Leche semidesnatada", 250, 47, 3.3, 4.8, 1.6);
		ml("Leche desnatada", 250, 35, 3.4, 5, 0.1);
		ml("Bebida de soja", 250, 39, 3.3, 2.5, 1.8);
		g("Yogur natural", 125, 61, 3.5, 4.7, 3.3);
		g("Yogur griego", 125, 122, 3.6, 4, 10);
		g("Skyr o yogur proteico", 150, 63, 11, 4, 0.2);
		g("Queso fresco", 60, 174, 12.4, 3, 12.8);
		g("Queso curado", 30, 410, 26, 0.5, 34);
		g("Requesón", 100, 98, 11, 3.4, 4.3);
		// Grasas, frutos secos y semillas.
		g("Aceite de oliva", 10, 884, 0, 0, 100);
		g("Mantequilla", 10, 717, 0.9, 0.1, 81);
		g("Aguacate", 100, 160, 2, 8.5, 14.7);
		g("Almendras", 30, 579, 21, 21.6, 50);
		g("Nueces", 30, 654, 15, 13.7, 65);
		g("Cacahuetes", 30, 567, 25.8, 16, 49);
		g("Crema de cacahuete", 20, 588, 25, 20, 50);
		// Fruta.
		g("Plátano", 120, 89, 1.1, 22.8, 0.3);
		g("Manzana", 180, 52, 0.3, 13.8, 0.2);
		g("Naranja", 200, 47, 0.9, 11.8, 0.1);
		g("Pera", 180, 57, 0.4, 15, 0.1);
		g("Kiwi", 100, 61, 1.1, 14.7, 0.5);
		g("Fresas", 150, 32, 0.7, 7.7, 0.3);
		g("Uvas", 150, 69, 0.7, 18, 0.2);
		g("Sandía", 300, 30, 0.6, 7.6, 0.2);
		g("Melón", 300, 34, 0.8, 8.2, 0.2);
		// Verdura y hortalizas.
		g("Brócoli", 150, 34, 2.8, 7, 0.4);
		g("Lechuga", 80, 15, 1.4, 2.9, 0.2);
		g("Tomate", 150, 18, 0.9, 3.9, 0.2);
		g("Zanahoria", 100, 41, 0.9, 9.6, 0.2);
		g("Espinacas", 100, 23, 2.9, 3.6, 0.4);
		g("Calabacín", 200, 17, 1.2, 3.1, 0.3);
		g("Pimiento rojo", 150, 31, 1, 6, 0.3);
		g("Cebolla", 100, 40, 1.1, 9.3, 0.1);
		g("Champiñones", 100, 22, 3.1, 3.3, 0.3);
		g("Judías verdes", 150, 31, 1.8, 7, 0.2);
		ml("Gazpacho", 250, 40, 0.8, 4, 2.4);
		// Dulces, platos y otros.
		g("Pizza", 200, 266, 11, 33, 10);
		g("Croissant", 60, 406, 8.2, 45.8, 21);
		g("Galletas tipo María", 30, 436, 7, 74, 12);
		g("Chocolate negro (70 %)", 20, 598, 7.8, 46, 43);
		g("Miel", 20, 304, 0.3, 82, 0);
		g("Azúcar", 10, 387, 0, 100, 0);
		g("Mermelada", 20, 250, 0.4, 60, 0.1);
		g("Patatas fritas de bolsa", 30, 536, 7, 53, 34);
		g("Proteína en polvo (whey)", 30, 400, 78, 8, 6);
		// Bebidas.
		ml("Zumo de naranja", 200, 45, 0.7, 10.4, 0.2);
		ml("Refresco de cola", 330, 42, 0, 10.6, 0);
		ml("Cerveza", 330, 43, 0.5, 3.6, 0);
		ml("Vino tinto", 150, 85, 0.1, 2.6, 0);
		ml("Café solo", 50, 2, 0.1, 0, 0);
	}

	private CommonFoods() {
	}

	public static List<Food> all() {
		return Collections.unmodifiableList(ALL);
	}

	/**
	 * Alimentos cuyo nombre contiene todas las palabras escritas (sin tildes ni mayúsculas); primero los que
	 * empiezan por la búsqueda. Menos de 2 letras no busca.
	 */
	public static List<Food> search(String query, int limit) {
		String q = normalize(query);
		if (q.length() < 2)
			return Collections.emptyList();
		String[] terms = q.split(" ");
		List<Food> starts = new ArrayList<>(), contains = new ArrayList<>();
		for (Food f : ALL) {
			String name = normalize(f.name);
			boolean all = true;
			for (String t : terms)
				if (!(" " + name).contains(" " + t)) {
					all = false;
					break;
				}
			if (all)
				(name.startsWith(q) ? starts : contains).add(f);
		}
		starts.addAll(contains);
		return starts.size() > limit ? starts.subList(0, limit) : starts;
	}

	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
	}
}
