package com.energym.app.ui;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.system.Os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Foto de perfil por cuenta, guardada solo en el móvil: recorte cuadrado centrado de 320 px en JPEG.
 * El nombre del archivo es un hash del propietario para no exponer identificadores en disco.
 */
public final class AvatarStore {
	private static final int SIZE = 320;
	private static final int MAX_DECODE_EDGE = 2048;
	private static final long MAX_DECODE_PIXELS = 1024 * 1024;
	private final Context context;

	public AvatarStore(Context context) {
		this.context = context.getApplicationContext();
	}

	private File file(String owner) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			StringBuilder name = new StringBuilder();
			for (byte b : digest.digest(owner.getBytes("UTF-8")))
				name.append(String.format(java.util.Locale.ROOT, "%02x", b));
			return new File(new File(context.getFilesDir(), "avatars"), name.substring(0, 32) + ".jpg");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean has(String owner) {
		return owner != null && file(owner).isFile();
	}

	public Bitmap load(String owner) {
		return has(owner) ? BitmapFactory.decodeFile(file(owner).getPath()) : null;
	}

	/**
	 * Lee la imagen elegida (reducida al decodificar), la recorta en cuadrado y la guarda. Lanza si no es válida.
	 */
	public void save(String owner, Uri source) throws Exception {
		if (owner == null || source == null)
			throw new IllegalArgumentException(Txt.t(R.string.av_missing));
		Bitmap decoded = null, square = null, scaled = null, oriented = null;
		File temp = null;
		try {
			decoded = decode(source);
			int orientation = orientation(source);
			int side = Math.min(decoded.getWidth(), decoded.getHeight());
			square = Bitmap.createBitmap(decoded, (decoded.getWidth() - side) / 2,
				(decoded.getHeight() - side) / 2, side, side);
			scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
			// El recorte centrado es simétrico: orientamos solo los 320 px para ahorrar memoria.
			oriented = Bitmap.createBitmap(scaled, 0, 0, SIZE, SIZE, orientationMatrix(orientation), true);
			File target = file(owner);
			File directory = target.getParentFile();
			if (!directory.isDirectory() && !directory.mkdirs())
				throw new IOException(Txt.t(R.string.av_prepare_failed));
			temp = File.createTempFile(target.getName(), ".tmp", directory);
			try (FileOutputStream out = new FileOutputStream(temp)) {
				if (!oriented.compress(Bitmap.CompressFormat.JPEG, 88, out))
					throw new IOException(Txt.t(R.string.av_encode_failed));
				out.getFD().sync();
			}
			// rename sustituye el archivo de forma atómica; un fallo conserva la foto anterior.
			Os.rename(temp.getPath(), target.getPath());
		} catch (OutOfMemoryError e) {
			throw new IOException(Txt.t(R.string.av_no_memory), e);
		} finally {
			if (temp != null) temp.delete();
			recycle(oriented);
			recycle(scaled);
			recycle(square);
			recycle(decoded);
		}
	}

	private Bitmap decode(Uri source) throws IOException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		try (InputStream in = open(source)) {
			BitmapFactory.decodeStream(in, null, options);
		}
		if (options.outWidth <= 0 || options.outHeight <= 0)
			throw new IOException(Txt.t(R.string.av_invalid));
		int sample = 1;
		int shortest = Math.min(options.outWidth, options.outHeight);
		while (shortest / (sample * 2L) >= SIZE) sample *= 2;
		// También acota panorámicas: reducir solo por el lado corto puede agotar la memoria.
		while (true) {
			long width = (options.outWidth + (long) sample - 1) / sample;
			long height = (options.outHeight + (long) sample - 1) / sample;
			if (width <= MAX_DECODE_EDGE && height <= MAX_DECODE_EDGE && width * height <= MAX_DECODE_PIXELS)
				break;
			sample *= 2;
		}
		options.inJustDecodeBounds = false;
		options.inSampleSize = sample;
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		options.inScaled = false;
		Bitmap bitmap = null;
		try (InputStream in = open(source)) {
			bitmap = BitmapFactory.decodeStream(in, null, options);
			if (bitmap == null) throw new IOException(Txt.t(R.string.av_invalid));
		} catch (IOException | RuntimeException | Error e) {
			recycle(bitmap);
			throw e;
		}
		return bitmap;
	}

	private static void recycle(Bitmap bitmap) {
		if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
	}

	private InputStream open(Uri source) throws IOException {
		InputStream stream = context.getContentResolver().openInputStream(source);
		if (stream == null) throw new IOException(Txt.t(R.string.av_open_failed));
		return stream;
	}

	private int orientation(Uri source) throws IOException {
		try (InputStream in = open(source)) {
			return new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		} catch (IOException ignored) {
			// PNG y otros formatos válidos pueden no contener EXIF en Android 8.
			return ExifInterface.ORIENTATION_NORMAL;
		}
	}

	private static Matrix orientationMatrix(int orientation) {
		Matrix matrix = new Matrix();
		switch (orientation) {
			case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
			case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
			case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
			case ExifInterface.ORIENTATION_TRANSPOSE:
				matrix.setRotate(90); matrix.postScale(-1, 1); break;
			case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
			case ExifInterface.ORIENTATION_TRANSVERSE:
				matrix.setRotate(270); matrix.postScale(-1, 1); break;
			case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
		}
		return matrix;
	}

	public void delete(String owner) {
		if (owner != null)
			file(owner).delete();
	}
}
