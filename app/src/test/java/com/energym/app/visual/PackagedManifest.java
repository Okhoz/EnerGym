package com.energym.app.visual;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/**
 * Lee el manifiesto empaquetado en pruebas JVM con el mismo analizador que la app.
 */
final class PackagedManifest {
	private PackagedManifest() {
	}

	static byte[] bytes() {
		try {
			return Files.readAllBytes(Paths.get("src/main/assets/visual/manifest.json"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<ResourceManifest> all() {
		return ResourceManifest.parse(bytes());
	}

	static ResourceManifest forExercise(String id) {
		for (ResourceManifest r : all())
			if (r.exerciseId.equals(id))
				return r;
		throw new AssertionError("Sin fila para " + id);
	}

	static ResourceManifest pushUp() {
		return forExercise("standard-pushup");
	}

	static ResourceRepository repository() {
		return new ResourceRepository(all(), path -> Files.newInputStream(Paths.get("src/main/assets", path)));
	}
}
