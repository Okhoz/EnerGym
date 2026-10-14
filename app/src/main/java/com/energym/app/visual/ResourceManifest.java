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
	 * Identidad exacta inspeccionada; el lector del manifiesto empaquetado debe coincidir con ella.
	 */
	public static ResourceManifest pushUp() {
		return new ResourceManifest("standard-pushup", "visual/push_up_native.glb",
			"80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f",
			"67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326",
			"Removed orphan Human weights animation: zero mesh morph targets. Mesh, rig and BIN unchanged.",
			"https://assiamahs.github.io/opengym3d/assets/push_up.glb", "CC0-1.0",
			"ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae", "TECHNICAL_PREVIEW_PROFESSIONAL_REVIEW_PENDING",
			new LinkedHashSet<>(Arrays.asList("play", "pause", "speed", "view", "marker", "rotate", "zoom")),
			new LinkedHashSet<>(Arrays.asList("front", "side", "rear", "reset")), Collections.emptySet(),
			new LinkedHashSet<>(Collections.singletonList("Human.rig")), markerMap(),
			new LinkedHashSet<>(Arrays.asList(.25f, .5f, 1f)), 1.533333f);
	}

	private static Map<String, Float> markerMap() {
		Map<String, Float> m = new LinkedHashMap<>();
		m.put("initial", 0f);
		m.put("middle", .7666665f);
		m.put("final", 1.533333f);
		return m;
	}
}
