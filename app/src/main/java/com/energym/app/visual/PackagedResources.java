package com.energym.app.visual;

import android.content.Context;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adaptador Android de datos: coteja el manifiesto con los artefactos auditados de esta versión.
 */
public final class PackagedResources {
	private PackagedResources() {
	}

	/**
	 * No admite que una edición del JSON declare mallas, regiones o revisión inexistentes.
	 */
	public static ResourceRepository load(Context context) throws IOException {
		try (InputStream input = context.getAssets()
			.open("visual/manifest.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
			byte[] chunk = new byte[4096];
			int count;
			while ((count = input.read(chunk)) != -1) {
				if (bytes.size() + count > 65536)
					throw new IOException("Manifiesto demasiado grande");
				bytes.write(chunk, 0, count);
			}
			JSONObject root = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
			JSONArray rows = root.getJSONArray("resources");
			Map<String, ResourceManifest> expected = new LinkedHashMap<>();
			for (ResourceManifest resource : ResourceManifest.verified())
				expected.put(resource.exerciseId, resource);
			if (root.getInt("version") != 1 || rows.length() != expected.size())
				throw new IOException("Versión de recursos no admitida");
			List<ResourceManifest> resources = new ArrayList<>();
			for (int i = 0; i < rows.length(); i++) {
				ResourceManifest actual = parse(rows.getJSONObject(i));
				// remove() impide que una fila duplicada ocupe el lugar de otro recurso verificado.
				ResourceManifest audited = expected.remove(actual.exerciseId);
				if (rows.getJSONObject(i).getBoolean("professionallyReviewed") || audited == null ||
					!matches(actual, audited))
					throw new IOException("Manifiesto fuera de la lista verificada técnicamente");
				resources.add(actual);
			}
			return new ResourceRepository(resources, path -> context.getAssets().open(path));
		} catch (JSONException | IllegalArgumentException e) {
			throw new IOException("Manifiesto de recursos inválido", e);
		}
	}

	private static ResourceManifest parse(JSONObject row) throws JSONException {
		Map<String, Float> markers = new LinkedHashMap<>();
		JSONObject markerJson = row.getJSONObject("markers");
		Iterator<String> keys = markerJson.keys();
		while (keys.hasNext()) {
			String key = keys.next();
			markers.put(key, (float) markerJson.getDouble(key));
		}
		Set<Float> speeds = new LinkedHashSet<>();
		JSONArray speedJson = row.getJSONArray("speeds");
		for (int i = 0; i < speedJson.length(); i++)
			speeds.add((float) speedJson.getDouble(i));
		return new ResourceManifest(row.getString("exerciseId"), row.getString("assetPath"), row.getString("sha256"),
			row.getString("sourceSha256"), row.getString("adaptation"), row.getString("sourceUrl"),
			row.getString("license"), row.getString("sourceRevision"), row.getString("reviewStatus"),
			strings(row.getJSONArray("capabilities")), strings(row.getJSONArray("views")),
			strings(row.getJSONArray("regions")), strings(row.getJSONArray("clipNames")), markers, speeds,
			(float) row.getDouble("durationSeconds"));
	}

	private static Set<String> strings(JSONArray array) throws JSONException {
		Set<String> result = new LinkedHashSet<>();
		for (int i = 0; i < array.length(); i++)
			result.add(array.getString(i));
		return result;
	}

	private static boolean matches(ResourceManifest a, ResourceManifest b) {
		return a.exerciseId.equals(b.exerciseId) && a.assetPath.equals(b.assetPath) && a.sha256.equals(b.sha256) &&
			a.sourceSha256.equals(b.sourceSha256) && a.adaptation.equals(b.adaptation) &&
			a.sourceUrl.equals(b.sourceUrl) && a.license.equals(b.license) &&
			a.sourceRevision.equals(b.sourceRevision) && a.reviewStatus.equals(b.reviewStatus) &&
			a.capabilities.equals(b.capabilities) && a.views.equals(b.views) && a.regions.equals(b.regions) &&
			a.clipNames.equals(b.clipNames) && a.markers.equals(b.markers) && a.speeds.equals(b.speeds) &&
			Math.abs(a.duration - b.duration) < .000001f;
	}
}
