package com.energym.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.*;
import java.util.*;

/**
 * Fotos de progreso guardadas solo en este móvil (almacenamiento privado de la app, por cuenta). Se reducen a
 * 1600 px para no ocupar de más; no se suben a ningún sitio.
 */
final class ProgressPhotos {
	private static final int MAX_SIDE = 1600;

	private ProgressPhotos() {
	}

	static File dir(Context c, String owner) {
		File d = new File(c.getFilesDir(), "progress/" + Integer.toHexString(owner == null ? 0 : owner.hashCode()));
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return d;
	}

	/** Fotos de la cuenta, de la más antigua a la más reciente (el nombre es la fecha y hora). */
	static List<File> list(Context c, String owner) {
		File[] files = dir(c, owner).listFiles((d, name) -> name.endsWith(".jpg"));
		List<File> result = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
		result.sort(Comparator.comparing(File::getName));
		return result;
	}

	/** Archivo temporal donde la cámara deja la foto antes de reducirla. */
	static File cameraFile(Context c) {
		File d = new File(c.getCacheDir(), "camera");
		//noinspection ResultOfMethodCallIgnored
		d.mkdirs();
		return new File(d, "shot.jpg");
	}

	/** Copia (reducida) una imagen elegida o hecha con la cámara; devuelve false si no se pudo leer. */
	static boolean add(Context c, String owner, Uri source) {
		try {
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			try (InputStream in = c.getContentResolver().openInputStream(source)) {
				BitmapFactory.decodeStream(in, null, bounds);
			}
			if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
				return false;
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = 1;
			while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= MAX_SIDE)
				opts.inSampleSize *= 2;
			Bitmap bitmap;
			try (InputStream in = c.getContentResolver().openInputStream(source)) {
				bitmap = BitmapFactory.decodeStream(in, null, opts);
			}
			if (bitmap == null)
				return false;
			bitmap = rotate(c, source, bitmap);
			String name = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".jpg";
			try (OutputStream out = new FileOutputStream(new File(dir(c, owner), name))) {
				bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
			}
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Respeta la orientación que guarda la cámara en la foto. */
	private static Bitmap rotate(Context c, Uri source, Bitmap bitmap) {
		try (InputStream in = c.getContentResolver().openInputStream(source)) {
			if (in == null)
				return bitmap;
			int o = new android.media.ExifInterface(in).getAttributeInt(
				android.media.ExifInterface.TAG_ORIENTATION, 1);
			int degrees = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
			if (degrees == 0)
				return bitmap;
			android.graphics.Matrix m = new android.graphics.Matrix();
			m.postRotate(degrees);
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
		} catch (IOException | RuntimeException e) {
			return bitmap;
		}
	}

	/** Miniatura para la lista (sin cargar la foto completa en memoria). */
	static Bitmap thumbnail(File file, int side) {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getPath(), bounds);
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inSampleSize = 1;
		while (Math.min(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= side)
			opts.inSampleSize *= 2;
		return BitmapFactory.decodeFile(file.getPath(), opts);
	}

	/** «16/10/2026» a partir del nombre del archivo. */
	static String date(File file) {
		String n = file.getName();
		return n.length() >= 8 ? n.substring(6, 8) + "/" + n.substring(4, 6) + "/" + n.substring(0, 4) : n;
	}
}
