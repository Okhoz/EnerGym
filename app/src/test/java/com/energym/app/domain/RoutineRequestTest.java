package com.energym.app.domain;

import com.energym.app.domain.RoutineRequest.Area;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

/** La rutina se elige por lo que pide la persona y varía entre peticiones, no son siempre los primeros del catálogo. */
public class RoutineRequestTest {
	private static List<Exercise> catalog() throws Exception {
		return com.energym.app.visual.TestCatalog.exercises();
	}

	private static Exercise find(List<Exercise> catalog, String id) {
		for (Exercise e : catalog)
			if (e.getId().equals(id))
				return e;
		throw new AssertionError(id);
	}

	@Test
	public void parsesAreasIntensityCountAndNegations() {
		RoutineRequest r = RoutineRequest.parse("Hazme una rutina de piernas y glúteos suave, con cuatro ejercicios");
		assertEquals(EnumSet.of(Area.LEGS, Area.GLUTES), r.areas);
		assertEquals(RoutineRequest.Intensity.EASY, r.intensity);
		assertEquals(4, r.count);
		assertEquals("Piernas y glúteos · suave", r.title());
		RoutineRequest upper = RoutineRequest.parse("quiero tren superior pero sin brazos, algo intenso");
		assertEquals(EnumSet.of(Area.CHEST, Area.BACK, Area.SHOULDERS), upper.areas);
		assertEquals(EnumSet.of(Area.ARMS), upper.excluded);
		assertEquals(RoutineRequest.Intensity.HARD, upper.intensity);
		assertTrue(RoutineRequest.parse("hazme una rutina").isEmpty());
		assertEquals(EnumSet.of(Area.CORE), RoutineRequest.parse("algo para los abdominales").areas);
	}

	@Test
	public void classifiesCatalogExercisesByArea() throws Exception {
		List<Exercise> c = catalog();
		assertTrue(RoutineRequest.areas(find(c, "bodyweight-squat")).contains(Area.LEGS));
		assertTrue(RoutineRequest.areas(find(c, "glute-bridge")).contains(Area.GLUTES));
		assertTrue(RoutineRequest.areas(find(c, "standard-pushup")).contains(Area.CHEST));
		assertTrue(RoutineRequest.areas(find(c, "dumbbell-row")).contains(Area.BACK));
		assertTrue(RoutineRequest.areas(find(c, "pike-pushup")).contains(Area.SHOULDERS));
		assertTrue(RoutineRequest.areas(find(c, "dumbbell-biceps-curl")).contains(Area.ARMS));
		assertFalse("El curl femoral es pierna, no brazo", RoutineRequest.areas(find(c, "leg-curl")).contains(Area.ARMS));
		assertTrue(RoutineRequest.areas(find(c, "forearm-plank")).contains(Area.CORE));
		assertEquals(EnumSet.of(Area.CARDIO), RoutineRequest.areas(find(c, "running-easy")));
		assertEquals(EnumSet.of(Area.MOBILITY), RoutineRequest.areas(find(c, "cat-cow")));
		int unclassified = 0;
		for (Exercise e : c)
			if (RoutineRequest.areas(e).isEmpty())
				unclassified++;
		assertTrue("Casi todo el catálogo debe tener zona: " + unclassified, unclassified <= 10);
	}

	@Test
	public void plannerBuildsWhatWasAskedAndVariesBetweenRequests() throws Exception {
		List<Exercise> c = catalog();
		TrainingProfile home = new TrainingProfile.Builder().resources(EnumSet.of(Resource.MAT)).build();
		Routine legs = new RoutinePlanner().create(home, c, RoutineRequest.parse("rutina de piernas"), 1);
		assertFalse(legs.isEmpty());
		int legWork = 0;
		for (RoutineItem item : legs.getItems()) {
			Set<Area> areas = RoutineRequest.areas(item.getExercise());
			if (areas.contains(Area.LEGS))
				legWork++;
			assertTrue(item.getExercise().getName(), areas.contains(Area.LEGS) || areas.contains(Area.MOBILITY));
		}
		assertTrue(legWork >= 3);
		assertEquals("Piernas", legs.getTitle());

		Routine chest = new RoutinePlanner().create(home, c, RoutineRequest.parse("hoy pecho"), 1);
		for (RoutineItem item : chest.getItems())
			assertTrue(item.getExercise().getName(), RoutineRequest.areas(item.getExercise()).contains(Area.CHEST) ||
				RoutineRequest.areas(item.getExercise()).contains(Area.MOBILITY));

		Set<List<String>> variants = new HashSet<>();
		for (long seed = 0; seed < 6; seed++) {
			List<String> ids = new ArrayList<>();
			for (RoutineItem item : new RoutinePlanner().create(home, c, RoutineRequest.none(), seed).getItems())
				ids.add(item.getExercise().getId());
			variants.add(ids);
		}
		assertTrue("La misma petición no da siempre la misma rutina", variants.size() >= 3);

		Routine balanced = new RoutinePlanner().create(home, c, RoutineRequest.none(), 7);
		Set<Area> covered = EnumSet.noneOf(Area.class);
		for (RoutineItem item : balanced.getItems())
			covered.addAll(RoutineRequest.areas(item.getExercise()));
		assertTrue("Sin zona pedida, cuerpo completo: " + covered, covered.contains(Area.LEGS) &&
			(covered.contains(Area.CHEST) || covered.contains(Area.BACK)) && balanced.isValidFor(home));
	}

	@Test
	public void excludedAreasAndCountAreRespected() throws Exception {
		List<Exercise> c = catalog();
		TrainingProfile gym = new TrainingProfile.Builder().environment(Environment.GYM).category(Category.GYM)
			.resources(EnumSet.of(Resource.DUMBBELLS, Resource.BARBELL, Resource.BENCH, Resource.MACHINE))
			.durationMinutes(60).build();
		Routine r = new RoutinePlanner().create(gym, c, RoutineRequest.parse("tren superior sin brazos, 6 ejercicios"), 3);
		assertEquals(6, r.getItems().size());
		for (RoutineItem item : r.getItems()) {
			Set<Area> areas = RoutineRequest.areas(item.getExercise());
			assertFalse(item.getExercise().getName(), areas.contains(Area.ARMS) && !areas.contains(Area.CHEST) &&
				!areas.contains(Area.BACK) && !areas.contains(Area.SHOULDERS));
		}
	}
}
