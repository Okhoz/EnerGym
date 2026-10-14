package com.energym.app.visual;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.nio.*;
import java.security.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Alterar hash, lectura completa o correspondencia exacta deja de aceptar el recurso.
 */
public class ResourceRepositoryTest {
	@Test
	public void derivativeKeepsExactOriginalBinaryChunk() throws Exception {
		byte[] bytes = Files.readAllBytes(Paths.get("src/main/assets/visual/push_up_native.glb"));
		int jsonSize = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
		byte[] binary = Arrays.copyOfRange(bytes, 20 + jsonSize, bytes.length);
		StringBuilder hash = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256").digest(binary))
			hash.append(String.format("%02x", b & 255));
		assertEquals("338f0a42938e2ee2de53d777bbc87c32f18101aeab82d813903c40437d39e97f", hash.toString());
		assertEquals("67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326", ResourceManifest.pushUp().sourceSha256);
	}

	@Test
	public void exactExerciseOnlyAndActualPackagedBytes() throws Exception {
		ResourceRepository repo = new ResourceRepository(Collections.singletonList(ResourceManifest.pushUp()), path -> Files.newInputStream(Paths.get("src/main/assets", path)));
		assertNull(repo.forExercise("knee-pushup"));
		assertNull(repo.forExercise("bodyweight-squat"));
		assertEquals(5957652, repo.loadVerified(repo.forExercise("standard-pushup")).length);
	}

	@Test
	public void corruptFileFailsBeforeNativeParsing() throws Exception {
		byte[] bytes = Files.readAllBytes(Paths.get("src/main/assets/visual/push_up_native.glb"));
		bytes[100] ^= 1;
		ResourceRepository repo = new ResourceRepository(Collections.singletonList(ResourceManifest.pushUp()), path -> new ByteArrayInputStream(bytes));
		try {
			repo.loadVerified(repo.forExercise("standard-pushup"));
			fail("Debe activar fallback");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("integridad"));
		}
	}

	@Test
	public void shortReadsAndClosedStreamAreSupported() throws Exception {
		byte[] bytes = Files.readAllBytes(Paths.get("src/main/assets/visual/push_up_native.glb"));
		final boolean[] closed = {false};
		ResourceRepository repo = new ResourceRepository(Collections.singletonList(ResourceManifest.pushUp()), path -> new ByteArrayInputStream(bytes) {
			@Override
			public synchronized int read(byte[] b, int offset, int length) {
				return super.read(b, offset, Math.min(13, length));
			}

			@Override
			public void close() {
				closed[0] = true;
			}
		});
		assertEquals(bytes.length, repo.loadVerified(repo.forExercise("standard-pushup")).length);
		assertTrue(closed[0]);
	}
}

