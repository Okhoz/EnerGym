package com.energym.app.catalog;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.domain.*;

import java.util.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static com.energym.app.domain.TrainingProfile.*;

@RunWith(AndroidJUnit4.class)
public class CatalogRepositoryInstrumentedTest {
	@Test
	public void realAssetProvidesThreeStrengthContextsAndTypedEnduranceExamples() throws Exception {
		CatalogRepository catalog = CatalogRepository.load(InstrumentationRegistry.getInstrumentation().getTargetContext());
		assertFalse(catalog.getExercises().isEmpty());
		RoutinePlanner planner = new RoutinePlanner();
		TrainingProfile home = new TrainingProfile.Builder().build();
		Routine noEquipment = planner.create(home, catalog.getExercises());
		assertFalse(noEquipment.isEmpty());
		for (RoutineItem item : noEquipment.getItems())
			assertTrue(item.getExercise().getRequiredResources().isEmpty());
		Routine dumbbells = planner.create(home.toBuilder().category(Category.HOME_EQUIPMENT).resources(EnumSet.of(Resource.DUMBBELLS)).build(), catalog.getExercises());
		assertFalse(dumbbells.isEmpty());
		assertTrue(new CompatibilityEngine().isCompatible(catalog.findExercise("dumbbell-squat"), dumbbells.getProfile()));
		Routine gym = planner.create(home.toBuilder().category(Category.GYM).environment(Environment.GYM).resources(EnumSet.of(Resource.BARBELL)).build(), catalog.getExercises());
		assertFalse(gym.isEmpty());
		assertTrue(new CompatibilityEngine().isCompatible(catalog.findExercise("barbell-deadlift"), gym.getProfile()));
		for (String id : Arrays.asList("running-easy", "cycling-easy", "swimming-easy")) {
			Exercise exercise = catalog.findExercise(id);
			assertNotNull(exercise);
			assertEquals(MetricType.ENDURANCE, exercise.getMetricType());
			assertFalse(exercise.isReviewed());
		}
	}

	@Test
	public void pendingSportsHaveNoSpecificRoutineButPermitExplicitComplementaryPreparation() throws Exception {
		CatalogRepository catalog = CatalogRepository.load(InstrumentationRegistry.getInstrumentation().getTargetContext());
		assertFalse(catalog.getSports().isEmpty());
		for (String sportId : Arrays.asList("football", "basketball", "other")) {
			TrainingProfile profile = new TrainingProfile.Builder().sportId(sportId).build();
			assertTrue(new RoutinePlanner().create(profile, catalog.getExercises()).isEmpty());
			assertFalse(new RoutinePlanner().create(profile.toBuilder().focus(Focus.COMPLEMENTARY).build(), catalog.getExercises()).isEmpty());
		}
	}

	@Test
	public void malformedCatalogDoesNotSilentlyBecomeEmpty() {
		assertThrows(java.io.IOException.class, () -> CatalogRepository.fromJson("{broken"));
		assertThrows(java.io.IOException.class, () -> CatalogRepository.fromJson("{\"version\":99,\"sports\":[],\"exercises\":[]}"));
	}
}
