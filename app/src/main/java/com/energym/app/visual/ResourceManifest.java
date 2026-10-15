package com.energym.app.visual;

import java.util.*;

/**
 * Metadatos inmutables; la revisión profesional es independiente de la reproducción técnica.
 */
public final class ResourceManifest {
	public final String exerciseId, assetPath, sha256, sourceSha256, adaptation, sourceUrl, license, sourceRevision,
		reviewStatus;
	public final boolean professionallyReviewed;
	public final Set<String> capabilities, views, regions, clipNames;
	public final Map<String, Float> markers;
	public final Set<Float> speeds;
	public final float duration;

	public ResourceManifest(String exerciseId, String assetPath, String sha256, String sourceSha256, String adaptation,
		String sourceUrl, String license, String sourceRevision, String reviewStatus, Set<String> capabilities,
		Set<String> views, Set<String> regions, Set<String> clips, Map<String, Float> markers, Set<Float> speeds,
		float duration) {
		this.exerciseId = exerciseId;
		this.assetPath = assetPath;
		this.sha256 = sha256;
		this.sourceSha256 = sourceSha256;
		this.adaptation = adaptation;
		this.sourceUrl = sourceUrl;
		this.license = license;
		this.sourceRevision = sourceRevision;
		this.reviewStatus = reviewStatus;
		this.professionallyReviewed = false;
		this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
		this.views = Collections.unmodifiableSet(new LinkedHashSet<>(views));
		this.regions = Collections.unmodifiableSet(new LinkedHashSet<>(regions));
		this.clipNames = Collections.unmodifiableSet(new LinkedHashSet<>(clips));
		this.markers = Collections.unmodifiableMap(new LinkedHashMap<>(markers));
		this.speeds = Collections.unmodifiableSet(new LinkedHashSet<>(speeds));
		this.duration = duration;
	}

	/**
	 * Identidades exactas inspeccionadas; el lector del manifiesto empaquetado debe coincidir con ellas.
	 */
	public static List<ResourceManifest> verified() {
		return Collections.unmodifiableList(Arrays.asList(pushUp(), jumpingJacks(), runningEasy(), briskWalk()));
	}

	public static ResourceManifest pushUp() {
		return openGym3d("standard-pushup", "visual/push_up_native.glb",
			"80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f",
			"67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326", "push_up.glb", 1.533333f,
			.7666665f);
	}

	public static ResourceManifest jumpingJacks() {
		return openGym3d("jumping-jacks", "visual/jumping_jacks_native.glb",
			"dd6f6e860be59702714edefed5dac8265d391c4d4a2244ac7fea18b52ef2abfa",
			"94aa534eded3272a61f775d3a7067a9093df97c99f5505d64e39251c46697a0e", "jumping_jack.glb", 1.266667f,
			.6333335f);
	}

	/**
	 * Trote en el sitio: ilustra zancada y braceo, no el desplazamiento ni el ritmo de la carrera.
	 */
	public static ResourceManifest runningEasy() {
		return openGym3d("running-easy", "visual/jog_native.glb",
			"ad9cb7c7b9828e1f65d00a6d02b3afddf0a0fe52070eca5e335300c1231d5a2c",
			"3e20fe0299cd78c9534f4c1320a4e2e4f31f8b4ae51598a69065f93a43a0c020", "jog.glb", 1.2f, .6f);
	}

	/**
	 * Marcha en el sitio: ilustra paso y braceo, no la velocidad de la caminata rápida.
	 */
	public static ResourceManifest briskWalk() {
		return openGym3d("brisk-walk", "visual/walk_native.glb",
			"fb71090b36eb0f03a17b451618e6a28a0bb83e043baf7c1f348acf3b890c92f5",
			"789867cd7f3ab8362f29632d1b424eb0f31193fca3a05318ac830a03b2115eb1", "walk.glb", 1.7f, .85f);
	}

	/**
	 * Todas las exportaciones comparten malla, rig, licencia CC0 y la misma adaptación mínima.
	 */
	private static ResourceManifest openGym3d(String exerciseId, String assetPath, String sha256, String sourceSha256,
		String export, float duration, float middle) {
		Map<String, Float> markers = new LinkedHashMap<>();
		markers.put("initial", 0f);
		markers.put("middle", middle);
		markers.put("final", duration);
		return new ResourceManifest(exerciseId, assetPath, sha256, sourceSha256,
			"Removed orphan Human weights animation: zero mesh morph targets. Mesh, rig and BIN unchanged.",
			"https://assiamahs.github.io/opengym3d/assets/" + export, "CC0-1.0",
			"ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae", "TECHNICAL_PREVIEW_PROFESSIONAL_REVIEW_PENDING",
			new LinkedHashSet<>(Arrays.asList("play", "pause", "speed", "view", "marker", "rotate", "zoom")),
			new LinkedHashSet<>(Arrays.asList("front", "side", "rear", "reset")), Collections.emptySet(),
			new LinkedHashSet<>(Collections.singletonList("Human.rig")), markers,
			new LinkedHashSet<>(Arrays.asList(.25f, .5f, 1f)), duration);
	}
}
