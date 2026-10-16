package com.energym.app.domain;

import com.energym.app.domain.RoutineRequest.Area;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class WeeklyPlanTest {
	private static TrainingProfile gym(int days) {
		return new TrainingProfile.Builder().environment(Environment.GYM).category(Category.GYM)
			.resources(EnumSet.of(Resource.DUMBBELLS, Resource.BARBELL, Resource.BENCH, Resource.MACHINE))
			.daysPerWeek(days).durationMinutes(45).build();
	}

	@Test
	public void splitFollowsDaysPerWeekAndExplicitRequests() {
		assertEquals(Arrays.asList("Empuje", "Tirón", "Pierna"), names(WeeklyPlan.split(3, "")));
		assertEquals(Arrays.asList("Torso", "Pierna"), names(WeeklyPlan.split(2, "")));
		assertEquals(Arrays.asList("Empuje", "Tirón", "Pierna"), names(WeeklyPlan.split(2, "quiero un push pull legs")));
		assertEquals(Arrays.asList("Torso", "Pierna", "Torso", "Pierna"), names(WeeklyPlan.split(4, "torso pierna")));
		assertEquals(1, WeeklyPlan.split(1, "").size());
		assertTrue(WeeklyPlan.asksForPlan("Hazme un plan semanal"));
		assertTrue(WeeklyPlan.asksForPlan("quiero una rutina empuje tirón pierna"));
		assertFalse(WeeklyPlan.asksForPlan("hazme una rutina de piernas"));
	}

	@Test
	public void eachDayTrainsItsMuscleGroupsAndDaysChainInOrder() throws Exception {
		List<Exercise> catalog = com.energym.app.visual.TestCatalog.exercises();
		List<Routine> plan = WeeklyPlan.create(gym(3), catalog, "plan semanal", 42);
		assertEquals(3, plan.size());
		assertTrue(plan.get(0).getTitle().startsWith("Día 1/3 · Empuje"));
		assertTrue(plan.get(1).getTitle().startsWith("Día 2/3 · Tirón"));
		assertTrue(plan.get(2).getTitle().startsWith("Día 3/3 · Pierna"));
		for (RoutineItem item : plan.get(0).getItems()) {
			Set<Area> a = RoutineRequest.areas(item.getExercise());
			assertTrue(item.getExercise().getName(), a.contains(Area.CHEST) || a.contains(Area.SHOULDERS) ||
				a.contains(Area.MOBILITY));
		}
		for (RoutineItem item : plan.get(2).getItems()) {
			Set<Area> a = RoutineRequest.areas(item.getExercise());
			assertTrue(item.getExercise().getName(), a.contains(Area.LEGS) || a.contains(Area.GLUTES) ||
				a.contains(Area.CORE) || a.contains(Area.MOBILITY));
		}
		for (Routine r : plan)
			assertTrue(r.getTitle(), r.isValidFor(gym(3)));
		assertSame(plan.get(1), WeeklyPlan.next(plan.get(0), plan));
		assertSame("Tras el último vuelve al primero", plan.get(0), WeeklyPlan.next(plan.get(2), plan));
		assertNull(WeeklyPlan.dayOf(new RoutinePlanner().create(gym(3), catalog)));
		Thread.sleep(1100);
		List<Routine> later = WeeklyPlan.create(gym(3), catalog, "plan semanal", 44);
		List<Routine> both = new ArrayList<>(later);
		both.addAll(plan);
		assertSame("Otro plan con los mismos días no se mezcla", plan.get(1), WeeklyPlan.next(plan.get(0), both));
		assertSame(later.get(1), WeeklyPlan.next(later.get(0), both));
	}

	@Test
	public void repeatedDaysGetLettersAndDifferentExercises() throws Exception {
		List<Routine> plan = WeeklyPlan.create(gym(4), com.energym.app.visual.TestCatalog.exercises(), "", 7);
		assertEquals("Día 1/4 · Torso A", plan.get(0).getTitle());
		assertEquals("Día 3/4 · Torso B", plan.get(2).getTitle());
		assertNotEquals(ids(plan.get(0)), ids(plan.get(2)));
	}

	private static List<String> names(List<WeeklyPlan.Day> days) {
		List<String> result = new ArrayList<>();
		for (WeeklyPlan.Day d : days)
			result.add(d.name);
		return result;
	}

	private static List<String> ids(Routine r) {
		List<String> result = new ArrayList<>();
		for (RoutineItem i : r.getItems())
			result.add(i.getExercise().getId());
		return result;
	}
}
