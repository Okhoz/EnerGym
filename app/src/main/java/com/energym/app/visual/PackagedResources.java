package com.energym.app.visual;

import android.content.Context;

import java.io.*;

/**
 * Adaptador Android de datos: lee el manifiesto local verificado por huella y el GLB único.
 */
public final class PackagedResources {
	private PackagedResources() {
	}

	/**
	 * No admite que una edición del JSON declare clips, ejercicios o revisión distintos de la versión generada.
	 */
	public static ResourceRepository load(Context context) throws IOException {
		try (InputStream input = context.getAssets()
			.open("visual/manifest.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
			byte[] chunk = new byte[8192];
			int count;
			while ((count = input.read(chunk)) != -1) {
				if (bytes.size() + count > ResourceManifest.MAX_MANIFEST_BYTES)
					throw new IOException("Manifiesto demasiado grande");
				bytes.write(chunk, 0, count);
			}
			return new ResourceRepository(ResourceManifest.parse(bytes.toByteArray()),
				path -> context.getAssets().open(path));
		} catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
			throw new IOException("Manifiesto de recursos inválido", e);
		}
	}
}
