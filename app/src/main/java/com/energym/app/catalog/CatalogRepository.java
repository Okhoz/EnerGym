package com.energym.app.catalog;

import android.content.Context;

import com.energym.app.domain.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Catálogo local versionado de solo lectura; JSON de Android queda limitado a esta frontera.
 */
public final class CatalogRepository {
	private final int version;
	private final List<Sport> sports;
	/** Catálogo publicado más los ejercicios creados por la persona (al final); vista compartida y actualizada. */
	private final List<Exercise> live = new java.util.concurrent.CopyOnWriteArrayList<>();
	private final List<Exercise> exercises = Collections.unmodifiableList(live);
	private final Map<String, Sport> sportById;
	private final Map<String, Exercise> exerciseById;
	private final Map<String, Exercise> customById = new java.util.concurrent.ConcurrentHashMap<>();
	private int published;

	public CatalogRepository(int version, List<Sport> sports, List<Exercise> exercises) {
		if (version != 1 && version != 2)
			throw new IllegalArgumentException("Versión de catálogo no soportada: " + version);
		this.version = version;
		Map<String, Sport> sportMap = new LinkedHashMap<>();
		for (Sport sport : Objects.requireNonNull(sports, "sports")) {
			validateId(sport.getId());
			if (sportMap.put(sport.getId(), sport) != null)
				throw new IllegalArgumentException("Deporte duplicado: " + sport.getId());
		}
		Map<String, Exercise> exerciseMap = new LinkedHashMap<>();
		for (Exercise exercise : Objects.requireNonNull(exercises, "exercises")) {
			validateId(exercise.getId());
			if (exerciseMap.put(exercise.getId(), exercise) != null)
				throw new IllegalArgumentException("Ejercicio duplicado: " + exercise.getId());
			for (String sportId : exercise.getSportIds()) {
				Sport sport = sportMap.get(sportId);
				if (sport == null)
					throw new IllegalArgumentException("Deporte desconocido: " + sportId);
				Sport.CoverageStatus declared =
					exercise.getCoverage() == Exercise.Coverage.SPECIFIC ? sport.getSpecificCoverage() :
						sport.getComplementaryCoverage();
				if (declared == Sport.CoverageStatus.PENDING_VALIDATION)
					throw new IllegalArgumentException("Cobertura pendiente con ejercicio publicado: " + sportId);
			}
		}
		for (Exercise exercise : exercises) {
			for (String id : exercise.getAlternativeIds()) {
				if (!exerciseMap.containsKey(id) || id.equals(exercise.getId()))
					throw new IllegalArgumentException("Alternativa desconocida o idéntica: " + id);
			}
		}
		sportById = Collections.unmodifiableMap(sportMap);
		exerciseById = Collections.unmodifiableMap(exerciseMap);
		this.sports = Collections.unmodifiableList(new ArrayList<>(sportMap.values()));
		live.addAll(exerciseMap.values());
		published = live.size();
	}

	/**
	 * Sustituye los ejercicios propios de la cuenta activa (IDs «custom-…»). Aparecen en todas las búsquedas,
	 * rutinas y entrenos como los del catálogo; los publicados no cambian.
	 */
	public synchronized void setCustom(List<Exercise> custom) {
		List<Exercise> next = new ArrayList<>(live.subList(0, published));
		customById.clear();
		for (Exercise e : custom)
			if (e.getId().startsWith("custom-") && !exerciseById.containsKey(e.getId()) &&
				customById.put(e.getId(), e) == null)
				next.add(e);
		live.clear();
		live.addAll(next);
	}

	private static void validateId(String id) {
		if (!id.matches("[a-z][a-z0-9-]{0,79}"))
			throw new IllegalArgumentException("ID de catálogo inválido: " + id);
	}

	public int getVersion() {
		return version;
	}

	public List<Exercise> getExercises() {
		return exercises;
	}

	public Exercise findExercise(String id) {
		Exercise e = exerciseById.get(id);
		return e != null ? e : customById.get(id);
	}

	public List<Sport> getSports() {
		return sports;
	}

	public Sport findSport(String id) {
		return sportById.get(id);
	}

	public static CatalogRepository load(Context context) throws IOException {
		Objects.requireNonNull(context, "context");
		try (InputStream input = context.getAssets()
			.open("catalog.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[4096];
			int count;
			while ((count = input.read(buffer)) != -1) {
				if (bytes.size() + count > 1024 * 1024)
					throw new IOException("Catálogo demasiado grande");
				bytes.write(buffer, 0, count);
			}
			return fromJson(bytes.toString(StandardCharsets.UTF_8.name()));
		}
	}

	public static CatalogRepository fromJson(String json) throws IOException {
		try {
			JSONObject root = new JSONObject(Objects.requireNonNull(json, "json"));
			List<Sport> sports = new ArrayList<>();
			JSONArray sportRows = root.getJSONArray("sports");
			for (int i = 0; i < sportRows.length(); i++) {
				JSONObject row = sportRows.getJSONObject(i);
				sports.add(new Sport(row.getString("id"), row.getString("name"), row.optString("nameEn", null),
					Sport.CoverageStatus.valueOf(row.getString("specificCoverage")),
					Sport.CoverageStatus.valueOf(row.getString("complementaryCoverage"))));
			}
			List<Exercise> exercises = new ArrayList<>();
			Map<String, Exercise.English> english = new HashMap<>();
			JSONArray exerciseRows = root.getJSONArray("exercises");
			for (int i = 0; i < exerciseRows.length(); i++) {
				JSONObject row = exerciseRows.getJSONObject(i);
				// Nombre y técnica en inglés: solo se muestran; lo guardado y lo que va a la IA sigue en español.
				if (row.has("nameEn") && row.has("descriptionEn"))
					english.put(row.getString("id"), new Exercise.English(row.getString("name"),
						row.getString("nameEn"), row.getString("descriptionEn")));
				exercises.add(new Exercise(row.getString("id"), row.getString("name"),
					MetricType.valueOf(row.getString("metricType")),
					enumSet(row.getJSONArray("goals"), TrainingProfile.Goal.class),
					enumSet(row.getJSONArray("environments"), TrainingProfile.Environment.class),
					enumSet(row.getJSONArray("requiredResources"), TrainingProfile.Resource.class),
					new LinkedHashSet<>(strings(row.getJSONArray("sportIds"))),
					Exercise.Coverage.valueOf(row.getString("coverage")), row.getBoolean("reviewed"),
					row.getString("description"), strings(row.getJSONArray("alternativeIds"))));
			}
			CatalogRepository catalog = new CatalogRepository(root.getInt("version"), sports, exercises);
			Exercise.setEnglish(english);
			return catalog;
		} catch (JSONException | IllegalArgumentException | NullPointerException exception) {
			throw new IOException("El catálogo local es inválido", exception);
		}
	}

	private static List<String> strings(JSONArray array) throws JSONException {
		List<String> values = new ArrayList<>();
		for (int i = 0; i < array.length(); i++)
			values.add(array.getString(i));
		return values;
	}

	private static <T extends Enum<T>> Set<T> enumSet(JSONArray array, Class<T> type) throws JSONException {
		Set<T> values = EnumSet.noneOf(type);
		for (String value : strings(array))
			values.add(Enum.valueOf(type, value));
		return values;
	}
}
