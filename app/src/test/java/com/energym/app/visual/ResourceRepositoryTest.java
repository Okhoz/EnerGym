package com.energym.app.visual;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Alterar manifiesto, hash, lectura completa o asignación de clips deja de aceptar el recurso.
 */
public class ResourceRepositoryTest {
	private static final Path GLB = Paths.get("src/main/assets", ResourceManifest.MODEL_PATH);

	@Test
	public void everyCatalogExerciseHasExactlyOneVisualization() throws Exception {
		Map<?, ?> catalog = (Map<?, ?>) MiniJson.parse(
			new String(Files.readAllBytes(Paths.get("src/main/assets/catalog.json")), StandardCharsets.UTF_8));
		List<String> ids = new ArrayList<>();
		for (Object e : (List<?>) catalog.get("exercises"))
			ids.add((String) ((Map<?, ?>) e).get("id"));
		assertEquals(287, ids.size());
		ResourceRepository repo = PackagedManifest.repository();
		assertEquals(ids.size(), repo.all().size());
		for (String id : ids)
			assertNotNull(id, repo.forExercise(id));
		assertNull(repo.forExercise("unknown-exercise"));
	}

	@Test
	public void manifestClipsExistInTheSingleModelWithTheirDurations() throws Exception {
		byte[] bytes = Files.readAllBytes(GLB);
		assertEquals(ResourceManifest.MODEL_SHA256, ResourceManifest.sha256(bytes));
		Map<?, ?> doc = glbJson(bytes);
		List<?> accessors = (List<?>) doc.get("accessors");
		Map<String, Float> durations = new HashMap<>();
		for (Object a : (List<?>) doc.get("animations")) {
			Map<?, ?> anim = (Map<?, ?>) a;
			float max = 0;
			for (Object s : (List<?>) anim.get("samplers")) {
				Map<?, ?> input = (Map<?, ?>) accessors.get(((Number) ((Map<?, ?>) s).get("input")).intValue());
				max = Math.max(max, ((Number) ((List<?>) input.get("max")).get(0)).floatValue());
			}
			assertNull("Clip duplicado", durations.put((String) anim.get("name"), max));
		}
		Set<String> used = new HashSet<>();
		for (ResourceManifest r : PackagedManifest.all()) {
			assertEquals(ResourceManifest.MODEL_PATH, r.assetPath);
			assertEquals(ResourceManifest.MODEL_SHA256, r.sha256);
			assertTrue(r.exerciseId + " " + r.clipName, durations.containsKey(r.clipName));
			assertEquals(r.exerciseId, durations.get(r.clipName), r.duration, .001f);
			assertEquals(r.duration, r.markers.get("final"), 0f);
			assertTrue(r.markers.get("middle") >= 0 && r.markers.get("middle") <= r.duration);
			assertFalse(r.professionallyReviewed);
			assertEquals("CC0-1.0", r.license);
			used.add(r.clipName);
		}
		// Ningún clip queda empaquetado sin un ejercicio que lo muestre.
		assertEquals(durations.keySet(), used);
	}

	@Test
	public void capturesAndSchematicAnimationsAreLabelledApart() {
		ResourceManifest pushUp = PackagedManifest.pushUp();
		assertEquals(ResourceManifest.Kind.CC0_CAPTURE, pushUp.kind);
		assertEquals("EXACT", pushUp.match);
		assertEquals("Captura de movimiento CC0", pushUp.label());
		ResourceManifest tempo = PackagedManifest.forExercise("tempo-run");
		assertEquals(ResourceManifest.Kind.CC0_CAPTURE, tempo.kind);
		assertEquals("Captura CC0 de un gesto similar", tempo.label());
		ResourceManifest squat = PackagedManifest.forExercise("barbell-back-squat");
		assertEquals(ResourceManifest.Kind.SCHEMATIC, squat.kind);
		assertEquals("Animación esquemática", squat.label());
		assertTrue(squat.explanation().contains("esquemática"));
		int captures = 0;
		for (ResourceManifest r : PackagedManifest.all()) {
			if (r.kind == ResourceManifest.Kind.CC0_CAPTURE) {
				captures++;
				assertTrue(r.clipName, r.clipName.startsWith("cc0/"));
			} else
				assertTrue(r.clipName, r.clipName.startsWith("esq/"));
		}
		assertEquals(14, captures);
	}

	@Test
	public void editedManifestIsRejected() {
		byte[] bytes = PackagedManifest.bytes();
		String text = new String(bytes, StandardCharsets.UTF_8).replace("\"esq/squat\"", "\"cc0/jog\"");
		try {
			ResourceManifest.parse(text.getBytes(StandardCharsets.UTF_8));
			fail("Un manifiesto editado no debe aceptarse");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("verificada"));
		}
	}

	@Test
	public void singleModelKeepsTheApkBudget() throws Exception {
		assertTrue(Files.size(GLB) < 12L * 1024 * 1024);
		for (String legacy : Arrays.asList("push_up_native.glb", "jumping_jacks_native.glb", "jog_native.glb",
			"walk_native.glb"))
			assertFalse(legacy, Files.exists(Paths.get("src/main/assets/visual", legacy)));
	}

	@Test
	public void actualPackagedBytesPassIntegrity() throws Exception {
		ResourceRepository repo = PackagedManifest.repository();
		assertEquals(Files.size(GLB), repo.loadVerified(repo.forExercise("standard-pushup")).length);
	}

	@Test
	public void corruptFileFailsBeforeNativeParsing() throws Exception {
		byte[] bytes = Files.readAllBytes(GLB);
		bytes[100] ^= 1;
		ResourceRepository repo = new ResourceRepository(PackagedManifest.all(), path -> new ByteArrayInputStream(bytes));
		try {
			repo.loadVerified(repo.forExercise("standard-pushup"));
			fail("Debe activar fallback");
		} catch (IOException expected) {
			assertTrue(expected.getMessage().contains("integridad"));
		}
	}

	@Test
	public void shortReadsAndClosedStreamAreSupported() throws Exception {
		byte[] bytes = Files.readAllBytes(GLB);
		final boolean[] closed = {false};
		ResourceRepository repo = new ResourceRepository(PackagedManifest.all(), path -> new ByteArrayInputStream(bytes) {
			@Override
			public synchronized int read(byte[] b, int offset, int length) {
				return super.read(b, offset, Math.min(4093, length));
			}

			@Override
			public void close() {
				closed[0] = true;
			}
		});
		assertEquals(bytes.length, repo.loadVerified(repo.forExercise("burpee")).length);
		assertTrue(closed[0]);
	}

	private static Map<?, ?> glbJson(byte[] bytes) {
		ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(0x46546c67, header.getInt(0));
		int jsonSize = header.getInt(12);
		return (Map<?, ?>) MiniJson.parse(new String(bytes, 20, jsonSize, StandardCharsets.UTF_8));
	}
}
