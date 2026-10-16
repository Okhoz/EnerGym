package com.energym.app.domain;

import com.energym.app.assistant.ChatProtocol;
import com.energym.app.domain.TrainingProfile.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class FreeWorkoutTest {
	@Test
	public void addingExercisesBuildsValidManualVersionsWithEverythingKept() throws Exception {
		List<Exercise> catalog = com.energym.app.visual.TestCatalog.exercises();
		TrainingProfile home = new TrainingProfile.Builder().resources(EnumSet.of(Resource.MAT)).build();
		List<Exercise> options = FreeWorkout.candidates(home, catalog, null);
		assertTrue(options.size() > 20);
		Routine first = FreeWorkout.with(home, null, options.get(0), "16/10");
		assertTrue(FreeWorkout.is(first));
		assertEquals("Entreno libre · 16/10", first.getTitle());
		Exercise other = null;
		for (Exercise e : FreeWorkout.candidates(home, catalog, first))
			if (e.getMetricType() != options.get(0).getMetricType()) {
				other = e;
				break;
			}
		assertNotNull(other);
		assertFalse("No se ofrece lo ya añadido", FreeWorkout.candidates(home, catalog, first).contains(options.get(0)));
		Routine second = FreeWorkout.with(home, first, other, "16/10");
		assertEquals(2, second.getItems().size());
		assertNotEquals("Cada cambio es una versión nueva", first.getProposalId(), second.getProposalId());
		assertEquals(first.getTitle(), second.getTitle());
		for (Routine r : Arrays.asList(first, second)) {
			assertEquals(RoutineMetadata.Origin.MANUAL, r.getMetadata().getOrigin());
			assertTrue("Válida como rutina manual", r.isValidFor(r.getProfile()));
			for (RoutineItem item : r.getItems())
				ChatProtocol.validatePrescription(item.getExercise(), item.getPrescription(), item.getEstimatedMinutes());
		}
	}
}
