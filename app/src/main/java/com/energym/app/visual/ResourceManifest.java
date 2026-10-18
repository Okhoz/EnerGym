package com.energym.app.visual;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/**
 * Metadatos inmutables de la vista 3D de un ejercicio: clip dentro del GLB único y su tipo.
 * La revisión profesional es independiente de la reproducción técnica.
 */
public final class ResourceManifest {
	/**
	 * Huellas del manifiesto y del GLB generados por tools/build_exercise_motions.py. Cambiar cualquiera de los dos
	 * archivos sin regenerar y revisar estas constantes deja el visor en su estado de indisponibilidad.
	 */
	public static final String MANIFEST_SHA256 = "1fff29b2b326749288228a5eb7fe14e1400eee80d158b99f5152216dfca9aeb3";
	public static final String MODEL_SHA256 = "d54759e5ff47bd98f48c85380cfe1b283d61d335a70129556b91426ae17c9e50";
	public static final String MODEL_PATH = "visual/energym_motions.glb";
	static final int MAX_MANIFEST_BYTES = 256 * 1024;

	public enum Kind {
		/** Captura de movimiento con licencia CC0 (Mesh2Motion, vía OpenGym3D o retarget propio). */
		CC0_CAPTURE,
		/** Animación esquemática generada a partir del patrón de movimiento. */
		SCHEMATIC
	}

	public final String exerciseId, assetPath, sha256, clipName, license, reviewStatus, match;
	public final Kind kind;
	public final boolean professionallyReviewed;
	public final Set<String> capabilities, views, regions, clipNames;
	public final Map<String, Float> markers;
	public final Set<Float> speeds;
	public final float duration;
	/** Radio aproximado del movimiento en metros (0 = usar el límite del modelo) para alejar la cámara. */
	public final float viewRadius;

	public ResourceManifest(String exerciseId, String assetPath, String sha256, String clipName, Kind kind,
		String match, float duration, float middle, float viewRadius) {
		if (exerciseId == null || clipName == null || kind == null || match == null || !Float.isFinite(duration) ||
			duration <= 0 || !Float.isFinite(middle) || middle < 0 || middle > duration || !Float.isFinite(viewRadius) ||
			viewRadius < 0 || viewRadius > 5)
			throw new IllegalArgumentException("Fila de recurso no válida");
		this.exerciseId = exerciseId;
		this.assetPath = assetPath;
		this.sha256 = sha256;
		this.clipName = clipName;
		this.kind = kind;
		this.match = match;
		this.license = "CC0-1.0";
		this.reviewStatus = "TECHNICAL_PREVIEW_PROFESSIONAL_REVIEW_PENDING";
		this.professionallyReviewed = false;
		this.capabilities = Collections.unmodifiableSet(
			new LinkedHashSet<>(Arrays.asList("play", "pause", "speed", "view", "marker", "rotate", "zoom")));
		this.views = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("front", "side", "rear", "reset")));
		this.regions = Collections.emptySet();
		this.clipNames = Collections.singleton(clipName);
		Map<String, Float> m = new LinkedHashMap<>();
		m.put("initial", 0f);
		m.put("middle", middle);
		m.put("final", duration);
		this.markers = Collections.unmodifiableMap(m);
		this.speeds = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(.25f, .5f, 1f)));
		this.duration = duration;
		this.viewRadius = viewRadius;
	}

	/**
	 * Texto breve para la interfaz: deja claro si es una captura real o una animación esquemática.
	 */
	public String label() {
		if (kind == Kind.SCHEMATIC)
			return Txt.t(R.string.rm_schematic);
		return Txt.t("EXACT".equals(match) ? R.string.rm_capture : R.string.rm_capture_similar);
	}

	public String explanation() {
		if (kind == Kind.SCHEMATIC)
			return Txt.t(R.string.rm_schematic_body);
		if ("EXACT".equals(match))
			return Txt.t(R.string.rm_capture_body);
		return Txt.t(R.string.rm_capture_similar_body);
	}

	/**
	 * Lee el manifiesto empaquetado. Solo se acepta la versión cuya huella coincide con MANIFEST_SHA256.
	 */
	public static List<ResourceManifest> parse(byte[] bytes) {
		if (bytes == null || bytes.length > MAX_MANIFEST_BYTES)
			throw new IllegalArgumentException("Manifiesto demasiado grande");
		if (!MANIFEST_SHA256.equals(sha256(bytes)))
			throw new IllegalArgumentException("Manifiesto fuera de la versión verificada");
		Map<?, ?> root = (Map<?, ?>) MiniJson.parse(new String(bytes, StandardCharsets.UTF_8));
		if (((Number) root.get("version")).intValue() != 2)
			throw new IllegalArgumentException("Versión de recursos no admitida");
		Map<?, ?> model = (Map<?, ?>) root.get("model");
		if (!MODEL_PATH.equals(model.get("assetPath")) || !MODEL_SHA256.equals(model.get("sha256")) ||
			!"CC0-1.0".equals(model.get("license")) || !Boolean.FALSE.equals(model.get("professionallyReviewed")))
			throw new IllegalArgumentException("Modelo fuera de la lista verificada técnicamente");
		List<ResourceManifest> out = new ArrayList<>();
		for (Object item : (List<?>) root.get("exercises")) {
			Map<?, ?> row = (Map<?, ?>) item;
			out.add(new ResourceManifest((String) row.get("exerciseId"), MODEL_PATH, MODEL_SHA256,
				(String) row.get("clip"), Kind.valueOf((String) row.get("kind")), (String) row.get("match"),
				((Number) row.get("durationSeconds")).floatValue(), ((Number) row.get("middle")).floatValue(),
				((Number) row.get("viewRadius")).floatValue()));
		}
		return Collections.unmodifiableList(out);
	}

	static String sha256(byte[] bytes) {
		try {
			StringBuilder hash = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
				hash.append(String.format(Locale.ROOT, "%02x", b & 255));
			return hash.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}
}
