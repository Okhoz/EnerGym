package com.energym.app.domain;

import com.energym.app.i18n.Txt;

import java.util.*;

public final class Exercise {
	public enum Coverage {SPECIFIC, COMPLEMENTARY}

	/** Nombre y técnica en inglés de un ejercicio del catálogo, y el nombre en español del que se tradujeron. */
	public static final class English {
		final String sourceName, name, description;

		public English(String sourceName, String name, String description) {
			this.sourceName = TrainingProfile.required(sourceName, "sourceName");
			this.name = TrainingProfile.required(name, "nameEn");
			this.description = TrainingProfile.required(description, "descriptionEn");
		}
	}

	/**
	 * Traducciones del catálogo publicado, por ID. Valen también para las copias guardadas en rutinas y sesiones,
	 * que solo llevan el español; si el nombre en español ya no coincide (otro ejercicio), no se traduce.
	 */
	private static volatile Map<String, English> english = Collections.emptyMap();

	private final String id, name, description;
	private final MetricType metricType;
	private final Set<TrainingProfile.Goal> goals;
	private final Set<TrainingProfile.Environment> environments;
	private final Set<TrainingProfile.Resource> requiredResources;
	private final Set<String> sportIds;
	private final Coverage coverage;
	private final boolean reviewed;
	private final List<String> alternativeIds;

	public Exercise(String id, String name, MetricType metricType, Set<TrainingProfile.Goal> goals,
		Set<TrainingProfile.Environment> environments, Set<TrainingProfile.Resource> resources,
		Set<String> sportIds, Coverage coverage, boolean reviewed, String description,
		List<String> alternatives) {
		this.id = TrainingProfile.required(id, "id");
		this.name = TrainingProfile.required(name, "name");
		this.description = TrainingProfile.required(description, "description");
		this.metricType = Objects.requireNonNull(metricType, "metricType");
		this.coverage = Objects.requireNonNull(coverage, "coverage");
		this.goals = immutable(goals);
		this.environments = immutable(environments);
		this.requiredResources = immutable(resources);
		this.sportIds = immutable(sportIds);
		if (goals.isEmpty() || environments.isEmpty() || sportIds.isEmpty())
			throw new IllegalArgumentException("Ejercicio sin compatibilidad declarada");
		for (String sportId : sportIds)
			TrainingProfile.required(sportId, "sportId");
		this.reviewed = reviewed;
		this.alternativeIds =
			Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(alternatives, "alternatives")));
		for (String alternative : alternativeIds)
			TrainingProfile.required(alternative, "alternativeId");
	}

	private static <T> Set<T> immutable(Set<T> source) {
		Objects.requireNonNull(source, "set");
		if (source.contains(null))
			throw new IllegalArgumentException("Valor nulo en catálogo");
		return Collections.unmodifiableSet(new LinkedHashSet<>(source));
	}

	/** Sustituye las traducciones al inglés del catálogo (al cargarlo). */
	public static void setEnglish(Map<String, English> byId) {
		english = Collections.unmodifiableMap(new HashMap<>(byId));
	}

	private English english() {
		if (!Txt.english())
			return null;
		English en = english.get(id);
		return en != null && en.sourceName.equals(name) ? en : null;
	}

	public String getId() {
		return id;
	}

	/** Nombre en el idioma de la app (los ejercicios propios, tal como se crearon). */
	public String getName() {
		English en = english();
		return en == null ? name : en.name;
	}

	/** Nombre original del catálogo (español): el que se guarda y el que recibe la IA. */
	public String getSourceName() {
		return name;
	}

	public MetricType getMetricType() {
		return metricType;
	}

	public Set<TrainingProfile.Goal> getGoals() {
		return goals;
	}

	public Set<TrainingProfile.Environment> getEnvironments() {
		return environments;
	}

	public Set<TrainingProfile.Resource> getRequiredResources() {
		return requiredResources;
	}

	public Set<String> getSportIds() {
		return sportIds;
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public boolean isReviewed() {
		return reviewed;
	}

	/** Técnica en el idioma de la app. */
	public String getDescription() {
		English en = english();
		return en == null ? description : en.description;
	}

	/** Técnica original del catálogo (español): la que se guarda y la que recibe la IA. */
	public String getSourceDescription() {
		return description;
	}

	public List<String> getAlternativeIds() {
		return alternativeIds;
	}
}
