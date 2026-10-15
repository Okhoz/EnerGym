package com.energym.app.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Entrada inmutable de planificación; los recursos incluyen explícitamente mobiliario y apoyos.
 */
public final class TrainingProfile {
	public enum Goal {STRENGTH, GENERAL_FITNESS, ENDURANCE, MOBILITY, SPORT_PRACTICE}

	public enum Environment {GYM, HOME, OUTDOORS, POOL}

	public enum Resource {DUMBBELLS, BARBELL, BANDS, MAT, BENCH, MACHINE, CHAIR, WALL, SUPPORT, BICYCLE, POOL}

	public enum Focus {SPECIFIC, COMPLEMENTARY}

	public enum Category {GYM, HOME_EQUIPMENT, NO_EQUIPMENT, SPORT}

	private final String sportId, level, experience, space, preferences, limitations;
	private final Goal goal;
	private final Environment environment;
	private final Focus focus;
	private final Category category;
	private final Set<Resource> resources;
	private final Map<String, String> details;
	private final int daysPerWeek, durationMinutes;
	private final boolean historyConsent;

	private TrainingProfile(Builder builder) {
		sportId = required(builder.sportId, "sportId");
		goal = Objects.requireNonNull(builder.goal, "goal");
		environment = Objects.requireNonNull(builder.environment, "environment");
		focus = Objects.requireNonNull(builder.focus, "focus");
		category = Objects.requireNonNull(builder.category, "category");
		resources = Collections.unmodifiableSet(
			builder.resources.isEmpty() ? EnumSet.noneOf(Resource.class) : EnumSet.copyOf(builder.resources));
		if (builder.daysPerWeek < 1 || builder.daysPerWeek > 7)
			throw new IllegalArgumentException("Días: 1–7");
		if (builder.durationMinutes < 5 || builder.durationMinutes > 240)
			throw new IllegalArgumentException("Duración: 5–240 min");
		daysPerWeek = builder.daysPerWeek;
		durationMinutes = builder.durationMinutes;
		level = required(builder.level, "level");
		experience = Objects.requireNonNull(builder.experience, "experience").trim();
		space = Objects.requireNonNull(builder.space, "space").trim();
		preferences = Objects.requireNonNull(builder.preferences, "preferences").trim();
		limitations = Objects.requireNonNull(builder.limitations, "limitations").trim();
		historyConsent = builder.historyConsent;
		details = details(builder.details);
	}

	/**
	 * Datos propios de la disciplina (p. ej. distancia objetivo o posición): claves simples, textos cortos y sin vacíos.
	 */
	private static Map<String, String> details(Map<String, String> values) {
		Map<String, String> result = new TreeMap<>();
		for (Map.Entry<String, String> e : Objects.requireNonNull(values, "details").entrySet()) {
			String value = e.getValue() == null ? "" : e.getValue().trim();
			if (value.isEmpty())
				continue;
			if (e.getKey() == null || !e.getKey().matches("[a-zA-Z]{1,32}"))
				throw new IllegalArgumentException("Detalle inválido: " + e.getKey());
			if (value.length() > MAX_DETAIL)
				throw new IllegalArgumentException("Detalle demasiado largo: " + e.getKey());
			result.put(e.getKey(), value);
		}
		if (result.size() > MAX_DETAILS)
			throw new IllegalArgumentException("Demasiados detalles de disciplina");
		return Collections.unmodifiableMap(result);
	}

	public static final int MAX_DETAILS = 12, MAX_DETAIL = 120;

	static String required(String value, String field) {
		if (value == null || value.trim().isEmpty())
			throw new IllegalArgumentException(field + " vacío");
		return value.trim();
	}

	public String getSportId() {
		return sportId;
	}

	public Goal getGoal() {
		return goal;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public Focus getFocus() {
		return focus;
	}

	public Category getCategory() {
		return category;
	}

	public Set<Resource> getResources() {
		return resources;
	}

	public int getDaysPerWeek() {
		return daysPerWeek;
	}

	public int getDurationMinutes() {
		return durationMinutes;
	}

	public String getLevel() {
		return level;
	}

	public String getExperience() {
		return experience;
	}

	public String getSpace() {
		return space;
	}

	public String getPreferences() {
		return preferences;
	}

	public String getLimitations() {
		return limitations;
	}

	public boolean isHistoryConsent() {
		return historyConsent;
	}

	public Map<String, String> getDetails() {
		return details;
	}

	public TrainingProfile withResources(Set<Resource> value) {
		return toBuilder().resources(value).build();
	}

	public Builder toBuilder() {
		return new Builder().sportId(sportId).goal(goal).environment(environment).focus(focus).category(category)
			.resources(resources).daysPerWeek(daysPerWeek).durationMinutes(durationMinutes).level(level)
			.experience(experience).space(space).preferences(preferences).limitations(limitations)
			.historyConsent(historyConsent).details(details);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof TrainingProfile))
			return false;
		TrainingProfile p = (TrainingProfile) other;
		return daysPerWeek == p.daysPerWeek && durationMinutes == p.durationMinutes &&
			historyConsent == p.historyConsent && sportId.equals(p.sportId) && goal == p.goal &&
			environment == p.environment && focus == p.focus && category == p.category &&
			resources.equals(p.resources) && level.equals(p.level) && experience.equals(p.experience) &&
			space.equals(p.space) && preferences.equals(p.preferences) && limitations.equals(p.limitations) &&
			details.equals(p.details);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sportId, goal, environment, focus, category, resources, daysPerWeek, durationMinutes, level,
			experience, space, preferences, limitations, historyConsent, details);
	}

	public static final class Builder {
		private String sportId = "strength", level = "Principiante", experience = "", space = "", preferences = "",
			limitations = "";
		private Goal goal = Goal.STRENGTH;
		private Environment environment = Environment.HOME;
		private Focus focus = Focus.SPECIFIC;
		private Category category = Category.NO_EQUIPMENT;
		private Set<Resource> resources = EnumSet.noneOf(Resource.class);
		private Map<String, String> details = Collections.emptyMap();
		private int daysPerWeek = 3, durationMinutes = 30;
		private boolean historyConsent;

		public Builder sportId(String value) {
			sportId = value;
			return this;
		}

		public Builder goal(Goal value) {
			goal = value;
			return this;
		}

		public Builder environment(Environment value) {
			environment = value;
			return this;
		}

		public Builder resources(Set<Resource> value) {
			Objects.requireNonNull(value, "resources");
			resources = value.isEmpty() ? EnumSet.noneOf(Resource.class) : EnumSet.copyOf(value);
			return this;
		}

		public Builder focus(Focus value) {
			focus = value;
			return this;
		}

		public Builder category(Category value) {
			category = value;
			return this;
		}

		public Builder durationMinutes(int value) {
			durationMinutes = value;
			return this;
		}

		public Builder daysPerWeek(int value) {
			daysPerWeek = value;
			return this;
		}

		public Builder level(String value) {
			level = value;
			return this;
		}

		public Builder experience(String value) {
			experience = value;
			return this;
		}

		public Builder space(String value) {
			space = value;
			return this;
		}

		public Builder preferences(String value) {
			preferences = value;
			return this;
		}

		public Builder limitations(String value) {
			limitations = value;
			return this;
		}

		public Builder historyConsent(boolean value) {
			historyConsent = value;
			return this;
		}

		public Builder details(Map<String, String> value) {
			details = new TreeMap<>(Objects.requireNonNull(value, "details"));
			return this;
		}

		public TrainingProfile build() {
			return new TrainingProfile(this);
		}
	}
}
