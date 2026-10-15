package com.energym.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/**
 * Foto de perfil por cuenta, guardada solo en el móvil: recorte cuadrado centrado de 320 px en JPEG.
 * El nombre del archivo es un hash del propietario para no exponer identificadores en disco.
 */
public final class AvatarStore {
	private static final int SIZE = 320;
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
		Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), source),
			(decoder, info, src) -> {
				int shortest = Math.min(info.getSize().getWidth(), info.getSize().getHeight());
				int sample = Math.max(1, shortest / SIZE);
				decoder.setTargetSampleSize(sample);
				decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
			});
		int side = Math.min(decoded.getWidth(), decoded.getHeight());
		Bitmap square = Bitmap.createBitmap(decoded, (decoded.getWidth() - side) / 2,
			(decoded.getHeight() - side) / 2, side, side);
		Bitmap scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
		File target = file(owner);
		target.getParentFile().mkdirs();
		File temp = new File(target.getPath() + ".tmp");
		try (FileOutputStream out = new FileOutputStream(temp)) {
			scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
		}
		if (!temp.renameTo(target)) {
			target.delete();
			if (!temp.renameTo(target))
				throw new IllegalStateException("No se pudo guardar la foto");
		}
	}

	public void delete(String owner) {
		if (owner != null)
			file(owner).delete();
	}
}
