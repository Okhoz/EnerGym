package com.energym.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Imagen pública del perfil: HTTPS, sin tokens de autenticación, con tamaño y espera acotados. */
public final class GooglePhotoLoader {
	private static final int MAX_BYTES = 1024 * 1024, EDGE = 256;
	private GooglePhotoLoader() { }

	public static Bitmap fetch(String address) throws IOException {
		URL url = secureUrl(address);
		long deadline = System.nanoTime() + 15_000_000_000L;
		for (int redirect = 0; redirect < 4; redirect++) {
			check(deadline);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			try {
				connection.setInstanceFollowRedirects(false);
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);
				connection.setRequestProperty("Accept", "image/*");
				int status = connection.getResponseCode();
				if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
					String location = connection.getHeaderField("Location");
					if (location == null) throw new IOException("Redirección de foto sin destino");
					url = secureUrl(new URL(url, location).toExternalForm());
					continue;
				}
				String type = connection.getContentType();
				if (status != 200 || type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/") ||
					connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Foto no disponible");
				try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
					byte[] buffer = new byte[4096];
					int count;
					while ((count = in.read(buffer)) != -1) {
						check(deadline);
						if (out.size() + count > MAX_BYTES) throw new IOException("Foto demasiado grande");
						out.write(buffer, 0, count);
					}
					return decode(out.toByteArray());
				}
			} finally {
				connection.disconnect();
			}
		}
		throw new IOException("Demasiadas redirecciones de foto");
	}

	static URL secureUrl(String address) throws IOException {
		if (address == null) throw new IOException("Falta la URL de foto");
		URL url = new URL(address);
		if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null ||
			url.getRef() != null || (url.getPort() != -1 && url.getPort() != 443)) throw new IOException("La foto requiere HTTPS");
		return url;
	}

	static Bitmap decode(byte[] data) throws IOException {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Foto no válida");
		int sample = 1;
		while (Math.max(bounds.outWidth, bounds.outHeight) / sample > EDGE) sample *= 2;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = sample;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
		if (bitmap == null) throw new IOException("No se pudo leer la foto");
		return bitmap;
	}

	private static void check(long deadline) throws IOException {
		if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
			throw new IOException("Carga de foto cancelada o agotada");
	}
}
