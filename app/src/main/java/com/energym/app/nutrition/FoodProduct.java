package com.energym.app.nutrition;

import com.energym.app.assistant.StrictJson;

import java.util.*;

/**
 * MODEL: ficha leída de Open Food Facts. Los valores ausentes quedan como NaN para pedirlos a la persona.
 */
public final class FoodProduct {
	public final String barcode, name, brand, quantity;
	public final FoodEntry.Unit unit;
	public final double kcal100, protein100, carbs100, fat100;

	public FoodProduct(String barcode, String name, String brand, String quantity, FoodEntry.Unit unit,
		double kcal100, double protein100, double carbs100, double fat100) {
		this.barcode = barcode;
		this.name = name;
		this.brand = brand;
		this.quantity = quantity;
		this.unit = unit;
		this.kcal100 = kcal100;
		this.protein100 = protein100;
		this.carbs100 = carbs100;
		this.fat100 = fat100;
	}

	public boolean complete() {
		return !Double.isNaN(kcal100) && !Double.isNaN(protein100) && !Double.isNaN(carbs100) && !Double.isNaN(fat100);
	}

	public String displayName() {
		return brand.isEmpty() ? name : name + " · " + brand;
	}

	/**
	 * Códigos EAN-8, UPC-A, EAN-13 o GTIN-14; cualquier otro contenido no se envía a la API.
	 */
	public static boolean validBarcode(String code) {
		return code != null && code.matches("\\d{8}|\\d{12,14}");
	}

	/**
	 * Obtiene el GTIN de un código leído: EAN/UPC directo, QR GS1 Digital Link (…/01/GTIN) o cadena GS1 (01)GTIN.
	 * Devuelve null si el contenido no identifica un producto (por ejemplo, un QR con una web cualquiera).
	 */
	public static String extractCode(String raw) {
		if (raw == null)
			return null;
		String value = raw.trim();
		String code = null;
		if (value.matches("\\d{8}|\\d{12,14}"))
			code = value;
		else {
			java.util.regex.Matcher gs1 = java.util.regex.Pattern
				.compile("^\\(01\\)(\\d{14})(?=\\(|\\u001d|$)|^01(\\d{14})(?=\\d{2}|\\u001d|$)").matcher(value);
			if (gs1.find())
				code = gs1.group(1) == null ? gs1.group(2) : gs1.group(1);
			else
				try {
					java.net.URI uri = new java.net.URI(value);
					if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) &&
						uri.getHost() != null) {
						java.util.regex.Matcher path = java.util.regex.Pattern
							.compile("(?:^|/)01/(\\d{14}|\\d{13}|\\d{12}|\\d{8})(?=/|$)").matcher(uri.getRawPath());
						if (path.find())
							code = path.group(1);
					}
				} catch (java.net.URISyntaxException ignored) {
					// Un QR con texto libre no es un enlace GS1.
				}
		}
		if (code == null)
			return null;
		// GTIN-14 con indicador 0 equivale al EAN-13 que usa Open Food Facts.
		if (code.length() == 14 && code.startsWith("0"))
			code = code.substring(1);
		return validBarcode(code) ? code : null;
	}

	/**
	 * Interpreta la respuesta v2 de Open Food Facts. Open Food Facts publica *_100g por 100 g o 100 ml
	 * según el producto: si la cantidad del envase está en volumen, la base es ml.
	 */
	public static FoodProduct parse(String barcode, String json) {
		Map<String, Object> root = StrictJson.object(StrictJson.parse(json));
		Object status = root.get("status");
		if (!(status instanceof Number) || ((Number) status).intValue() != 1 || !(root.get("product") instanceof Map))
			return null;
		return fromProduct(barcode, StrictJson.object(root.get("product")));
	}

	/** Ficha a partir del objeto «product» de Open Food Facts (también el de cada resultado de búsqueda). */
	static FoodProduct fromProduct(String barcode, Map<String, Object> product) {
		String name = text(product, "product_name_es");
		if (name.isEmpty())
			name = text(product, "product_name");
		if (name.isEmpty())
			name = "Producto " + barcode;
		// La búsqueda nueva devuelve las marcas como lista; la ficha por código, como texto.
		String brand = product.get("brands") instanceof List && !((List<?>) product.get("brands")).isEmpty() &&
			((List<?>) product.get("brands")).get(0) instanceof String ?
			((String) ((List<?>) product.get("brands")).get(0)).trim() : text(product, "brands");
		if (brand.contains(","))
			brand = brand.substring(0, brand.indexOf(',')).trim();
		String quantity = text(product, "quantity");
		FoodEntry.Unit unit = volume(quantity) ? FoodEntry.Unit.ML : FoodEntry.Unit.G;
		Map<String, Object> n = product.get("nutriments") instanceof Map ?
			StrictJson.object(product.get("nutriments")) : Collections.emptyMap();
		double kcal = number(n, "energy-kcal_100g");
		if (Double.isNaN(kcal)) {
			double kj = number(n, "energy-kj_100g");
			if (!Double.isNaN(kj))
				kcal = Math.round(kj / 4.184);
		}
		if (Double.isNaN(kcal)) {
			double energy = number(n, "energy_100g");
			// OFF normaliza energy_100g en kJ; energy_unit solo describe la unidad de entrada.
			if (Double.isFinite(energy) && energy >= 0)
				kcal = Math.round(energy / 4.184);
		}
		return new FoodProduct(barcode, limit(name, 120), limit(brand, 60), limit(quantity, 40), unit, kcal,
			number(n, "proteins_100g"), number(n, "carbohydrates_100g"), number(n, "fat_100g"));
	}

	/**
	 * Resultados de la búsqueda por texto de Open Food Facts: solo los que traen al menos las calorías, sin
	 * repetir código y como mucho {@code limit}.
	 */
	public static List<FoodProduct> parseSearch(String json, int limit) {
		Map<String, Object> root = StrictJson.object(StrictJson.parse(json));
		List<FoodProduct> result = new ArrayList<>();
		Set<String> codes = new HashSet<>();
		Object list = root.get("hits") instanceof List ? root.get("hits") : root.get("products");
		if (!(list instanceof List))
			return result;
		for (Object item : (List<?>) list) {
			if (!(item instanceof Map) || result.size() >= limit)
				continue;
			Map<String, Object> product = StrictJson.object(item);
			String code = text(product, "code");
			if (!validBarcode(code) || !codes.add(code))
				continue;
			FoodProduct parsed = fromProduct(code, product);
			if (!Double.isNaN(parsed.kcal100) && !parsed.name.startsWith("Producto "))
				result.add(parsed);
		}
		return result;
	}

	static boolean volume(String quantity) {
		return quantity.toLowerCase(Locale.ROOT).matches(".*\\d\\s*(ml|cl|dl|l|litros?)\\b.*");
	}

	private static String limit(String value, int max) {
		return value.length() > max ? value.substring(0, max).trim() : value;
	}

	private static String text(Map<String, Object> map, String key) {
		Object v = map.get(key);
		return v instanceof String ? ((String) v).trim() : "";
	}

	private static double number(Map<String, Object> map, String key) {
		Object v = map.get(key);
		if (v instanceof Number)
			return ((Number) v).doubleValue();
		if (v instanceof String)
			try {
				return Double.parseDouble(((String) v).replace(',', '.'));
			} catch (NumberFormatException ignored) {
			}
		return Double.NaN;
	}
}
