package com.energym.app.domain;
import java.util.*;
public final class Exercise {
    public enum Coverage { SPECIFIC, COMPLEMENTARY }
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
            Set<String> sportIds, Coverage coverage, boolean reviewed, String description, List<String> alternatives) {
        this.id = TrainingProfile.required(id, "id");
        this.name = TrainingProfile.required(name, "name");
        this.description = TrainingProfile.required(description, "description");
        this.metricType = Objects.requireNonNull(metricType, "metricType");
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.goals = immutable(goals);
        this.environments = immutable(environments);
        this.requiredResources = immutable(resources);
        this.sportIds = immutable(sportIds);
        if (goals.isEmpty() || environments.isEmpty() || sportIds.isEmpty()) throw new IllegalArgumentException("Ejercicio sin compatibilidad declarada");
        for (String sportId : sportIds) TrainingProfile.required(sportId, "sportId");
        this.reviewed = reviewed;
        this.alternativeIds = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(alternatives, "alternatives")));
        for (String alternative : alternativeIds) TrainingProfile.required(alternative, "alternativeId");
    }

    private static <T> Set<T> immutable(Set<T> source) {
        Objects.requireNonNull(source, "set");
        if (source.contains(null)) throw new IllegalArgumentException("Valor nulo en catálogo");
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public MetricType getMetricType() { return metricType; }
    public Set<TrainingProfile.Goal> getGoals() { return goals; }
    public Set<TrainingProfile.Environment> getEnvironments() { return environments; }
    public Set<TrainingProfile.Resource> getRequiredResources() { return requiredResources; }
    public Set<String> getSportIds() { return sportIds; }
    public Coverage getCoverage() { return coverage; }
    public boolean isReviewed() { return reviewed; }
    public String getDescription() { return description; }
    public List<String> getAlternativeIds() { return alternativeIds; }
}
