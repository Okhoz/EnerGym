package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;
import com.energym.app.catalog.ExerciseSearch;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regresión: el catálogo manual no comienza con ejercicios incompatibles deshabilitados. */
public final class ManualRoutineSearchTest {
	private final Exercise home = exercise("home", "Sentadilla en casa", Environment.HOME, Goal.STRENGTH);
	private final Exercise pool = exercise("pool", "Sentadilla en piscina", Environment.OUTDOORS, Goal.MOBILITY);
	private final ManualRoutineController controller = new ManualRoutineController(
		new ManualRoutineControllerTest.Store(), Arrays.asList(pool, home), () -> {}, routine -> {});

	private static Exercise exercise(String id, String name, Environment environment, Goal goal) {
		return new Exercise(id, name, MetricType.STRENGTH, EnumSet.of(goal), EnumSet.of(environment),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC,
			false, "Técnica de sentadilla", Collections.emptyList());
	}

	private void begin() {
		controller.activate("uid");
		controller.begin(new TrainingProfile.Builder().build());
	}

	@Test public void manualSearchStartsWithContextCompatibleExercises() {
		begin();
		assertEquals(Collections.singletonList(home), controller.search("sentadilla"));
		assertEquals("sentadilla", controller.query);
	}

	@Test public void compatibleSearchUsesCurrentRoutineContextWithoutChangingGlobalProfile() {
		TrainingProfile global = new TrainingProfile.Builder().build();
		controller.activate("uid");
		controller.begin(global);
		controller.contextInput("goal", "MOBILITY");
		controller.contextInput("environment", "OUTDOORS");
		assertEquals(Collections.singletonList(pool), controller.search(""));
		assertEquals(Goal.STRENGTH, global.getGoal());
		assertEquals(Environment.HOME, global.getEnvironment());
	}

	@Test public void invalidRoutineContextShowsNoAddableExercise() {
		begin();
		controller.contextInput("durationMinutes", "");
		assertTrue(controller.search("").isEmpty());
	}

	@Test public void exploreAllStillCombinesMetadataFiltersAndCannotAddAnIncompatibleExercise() {
		begin();
		ExerciseSearch.Filters outside = new ExerciseSearch.Filters("strength", Environment.OUTDOORS, "NONE", Goal.MOBILITY);
		assertEquals(Collections.singletonList(pool), controller.search("sentad", outside, false));
		assertTrue(controller.search("sentad", outside, true).isEmpty());
		assertFalse(controller.add(pool.getId()));
		assertTrue(controller.entries().isEmpty());
		assertEquals(Goal.STRENGTH, controller.context().getGoal());
	}
}
