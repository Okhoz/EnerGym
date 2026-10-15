package com.energym.app.nutrition;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * Adaptador HTTPS a la base abierta Open Food Facts (ODbL). Solo envía el código de barras, nunca datos de cuenta.
 */
public final class OpenFoodFactsLookup implements NutritionStore.Lookup {
	private static final String FIELDS = "product_name,product_name_es,brands,quantity,nutriments";
	private static final int MAX_BYTES = 60_000;
	private final Executor worker, main;
	private final String userAgent;

	public OpenFoodFactsLookup(Executor worker, Executor main, String version) {
		this.worker = worker;
		this.main = main;
		userAgent = "EnerGym-Android/" + version + " (https://github.com/Okhoz/EnerGym)";
	}

	public void find(String barcode, NutritionStore.Callback<FoodProduct> callback) {
		if (!FoodProduct.validBarcode(barcode)) {
			main.execute(() -> callback.failure(new IllegalArgumentException("Código no válido")));
			return;
		}
		worker.execute(() -> {
			HttpURLConnection connection = null;
			try {
				URL url = new URL("https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json?fields=" + FIELDS);
				connection = (HttpURLConnection) url.openConnection();
				connection.setInstanceFollowRedirects(false);
				connection.setConnectTimeout(6000);
				connection.setReadTimeout(10000);
				connection.setRequestProperty("User-Agent", userAgent);
				connection.setRequestProperty("Accept", "application/json");
				int code = connection.getResponseCode();
				if (code == 404) {
					main.execute(() -> callback.success(null));
					return;
				}
				if (code != 200)
					throw new IOException("Respuesta " + code);
				String body = read(connection.getInputStream());
				FoodProduct product = FoodProduct.parse(barcode, body);
				main.execute(() -> callback.success(product));
			} catch (Exception e) {
				main.execute(() -> callback.failure(e));
			} finally {
				if (connection != null)
					connection.disconnect();
			}
		});
	}

	private static String read(InputStream input) throws IOException {
		try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[4096];
			int count;
			while ((count = in.read(buffer)) != -1) {
				if (out.size() + count > MAX_BYTES)
					throw new IOException("Ficha demasiado grande");
				out.write(buffer, 0, count);
			}
			return out.toString(StandardCharsets.UTF_8.name());
		}
	}
}
