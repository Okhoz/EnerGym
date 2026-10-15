package com.energym.app.visual;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.Exercise;

import java.util.*;

/**
 * Un ID no sustituye la versión histórica: todas las propiedades deben coincidir.
 */
public final class ExerciseResourcePolicy {
	private final CatalogRepository catalog;
	private final Map<String, ResourceManifest> resources = new HashMap<>();

	public ExerciseResourcePolicy(CatalogRepository catalog, Collection<ResourceManifest> resources) {
		this.catalog = Objects.requireNonNull(catalog);
		for (ResourceManifest resource : resources)
			this.resources.put(resource.exerciseId, resource);
	}

	public boolean available(Exercise snapshot) {
		return snapshot != null && resources.containsKey(snapshot.getId()) &&
			same(snapshot, catalog.findExercise(snapshot.getId()));
	}

	/**
	 * Explicación del tipo de vista 3D (captura CC0 o esquemática) para la pantalla del ejercicio.
	 */
	public String explanation(Exercise snapshot) {
		return available(snapshot) ? resources.get(snapshot.getId()).explanation() : null;
	}

	public List<Exercise> available() {
		List<Exercise> result = new ArrayList<>();
		for (Exercise exercise : catalog.getExercises())
			if (available(exercise))
				result.add(exercise);
		return Collections.unmodifiableList(result);
	}

	public static boolean same(Exercise a, Exercise b) {
		return a != null && b != null && a.getId().equals(b.getId()) && a.getName().equals(b.getName()) &&
			a.getDescription().equals(b.getDescription()) && a.getMetricType() == b.getMetricType() &&
			a.getGoals().equals(b.getGoals()) && a.getEnvironments().equals(b.getEnvironments()) &&
			a.getRequiredResources().equals(b.getRequiredResources()) && a.getSportIds().equals(b.getSportIds()) &&
			a.getCoverage() == b.getCoverage() && a.isReviewed() == b.isReviewed() &&
			a.getAlternativeIds().equals(b.getAlternativeIds());
	}
}
