package com.energym.app.domain;

import java.util.*;

public final class CompatibilityEngine {
	public boolean isCompatible(Exercise exercise, TrainingProfile profile) {
		if (exercise == null || profile == null)
			return false;
		return exercise.getGoals().contains(profile.getGoal()) &&
			exercise.getEnvironments().contains(profile.getEnvironment()) &&
			profile.getResources().containsAll(exercise.getRequiredResources()) &&
			exercise.getSportIds().contains(profile.getSportId()) &&
			(profile.getFocus() == TrainingProfile.Focus.COMPLEMENTARY ?
				exercise.getCoverage() == Exercise.Coverage.COMPLEMENTARY :
				exercise.getCoverage() == Exercise.Coverage.SPECIFIC);
	}

	/**
	 * Devuelve sustituciones ordenadas por catálogo para el objetivo, sin ampliar la cobertura.
	 */
	public List<Exercise> alternatives(Exercise exercise, TrainingProfile profile, List<Exercise> catalog) {
		Objects.requireNonNull(exercise, "exercise");
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(catalog, "catalog");
		if (!exercise.getGoals().contains(profile.getGoal()))
			return Collections.emptyList();
		Map<String, Exercise> candidates = new LinkedHashMap<>();
		for (Exercise candidate : catalog) {
			if (candidate != null && !candidate.getId().equals(exercise.getId()) && isCompatible(candidate, profile) &&
				candidate.getMetricType() == exercise.getMetricType() &&
				candidate.getCoverage() == exercise.getCoverage())
				candidates.putIfAbsent(candidate.getId(), candidate);
		}
		List<Exercise> result = new ArrayList<>();
		for (String id : exercise.getAlternativeIds()) {
			Exercise preferred = candidates.remove(id);
			if (preferred != null)
				result.add(preferred);
		}
		result.addAll(candidates.values());
		return Collections.unmodifiableList(result);
	}
}
