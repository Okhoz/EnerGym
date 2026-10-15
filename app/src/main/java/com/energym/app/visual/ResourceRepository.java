package com.energym.app.visual;

import java.io.*;
import java.util.*;
import java.nio.*;
import java.security.*;

/**
 * Repositorio local testeable sin Android; ninguna descarga sucede durante la reproducción.
 */
public final class ResourceRepository {
	public interface BytesSource {
		InputStream open(String path) throws IOException;
	}

	private final Map<String, ResourceManifest> byExercise = new LinkedHashMap<>();
	private final BytesSource source;

	public ResourceRepository(Collection<ResourceManifest> resources, BytesSource source) {
		this.source = Objects.requireNonNull(source);
		for (ResourceManifest r : resources) {
			if (byExercise.put(r.exerciseId, r) != null)
				throw new IllegalArgumentException("Recurso duplicado");
		}
	}

	/**
	 * Solo una fila del manifiesto habilita la vista 3D del ejercicio; no se deduce por nombre ni variante.
	 */
	public ResourceManifest forExercise(String id) {
		return byExercise.get(id);
	}

	public Collection<ResourceManifest> all() {
		return Collections.unmodifiableCollection(byExercise.values());
	}

	/**
	 * Lectura acotada y SHA-256 previo al motor. Un error se traduce a fallback, nunca se ignora.
	 */
	public byte[] loadVerified(ResourceManifest r) throws IOException {
		if (r == null || byExercise.get(r.exerciseId) != r)
			throw new IOException("Recurso no disponible");
		try (InputStream input = source.open(r.assetPath); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] chunk = new byte[16384];
			int count;
			while ((count = input.read(chunk)) != -1) {
				if (Thread.currentThread().isInterrupted())
					throw new InterruptedIOException("Carga cancelada");
				if (out.size() + count > 16 * 1024 * 1024)
					throw new IOException("Recurso demasiado grande");
				out.write(chunk, 0, count);
			}
			byte[] bytes = out.toByteArray();
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			StringBuilder hash = new StringBuilder();
			for (byte b : digest.digest(bytes))
				hash.append(String.format(Locale.ROOT, "%02x", b & 255));
			if (!hash.toString().equals(r.sha256))
				throw new IOException("Error de integridad del recurso local");
			if (bytes.length < 12)
				throw new IOException("Formato GLB incompleto");
			ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			if (header.getInt() != 0x46546c67 || header.getInt() != 2 || header.getInt() != bytes.length)
				throw new IOException("Formato GLB inválido");
			return bytes;
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 no disponible", e);
		}
	}
}
