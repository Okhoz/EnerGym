package com.energym.app.catalog;

import com.energym.app.domain.*;
import com.energym.app.domain.RoutineRequest.Area;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class CustomExercisesTest {
	@Test
	public void createdExercisesJoinTheCatalogAndFitAnyProfileWithTheirMaterial() throws Exception {
		CatalogRepository catalog = com.energym.app.visual.TestCatalog.repository();
		int published = catalog.getExercises().size();
		Map<String, List<Exercise>> saved = new HashMap<>();
		int[] changes = {0};
		CustomExercises custom = new CustomExercises(new CustomExercises.Store() {
			public List<Exercise> load(String owner) {
				return saved.getOrDefault(owner, Collections.emptyList());
			}

			public void save(String owner, List<Exercise> exercises) {
				saved.put(owner, new ArrayList<>(exercises));
			}
		}, catalog, () -> changes[0]++);
		assertFalse("Sin cuenta no se crea", custom.add("Algo", MetricType.STRENGTH, Area.CHEST,
			EnumSet.noneOf(Resource.class), "").isEmpty());
		custom.activate("ana");
		assertEquals("", custom.add("Press convergente", MetricType.STRENGTH, Area.CHEST, EnumSet.of(Resource.MACHINE), ""));
		assertFalse("Nombre repetido", custom.add("press convergente", MetricType.STRENGTH, Area.CHEST,
			EnumSet.noneOf(Resource.class), "").isEmpty());
		assertEquals(published + 1, catalog.getExercises().size());
		Exercise mine = custom.mine().get(0);
		assertSame(mine, catalog.findExercise(mine.getId()));
		assertEquals(EnumSet.of(Area.CHEST), RoutineRequest.areas(mine));
		assertEquals(Area.CHEST, CustomExercises.area(mine));
		TrainingProfile gym = new TrainingProfile.Builder().sportId("powerlifting").environment(Environment.GYM)
			.category(Category.GYM).resources(EnumSet.of(Resource.MACHINE)).build();
		assertTrue(new CompatibilityEngine().isCompatible(mine, gym));
		TrainingProfile home = new TrainingProfile.Builder().build();
		assertFalse("Pide su material", new CompatibilityEngine().isCompatible(mine, home));
		assertTrue(FreeWorkout.candidates(gym, catalog.getExercises(), null).contains(mine));
		custom.activate("luis");
		assertEquals("Cada cuenta ve los suyos", published, catalog.getExercises().size());
		custom.activate("ana");
		custom.remove(mine.getId());
		assertEquals(published, catalog.getExercises().size());
		assertTrue(saved.get("ana").isEmpty());
	}
}
