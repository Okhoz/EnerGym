package com.energym.app.domain;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ManualRoutineMetadataTest {
	private Routine routine(RoutineMetadata metadata) {
		TrainingProfile profile = new TrainingProfile.Builder().build();
		Exercise exercise = new Exercise("fixture", "Fixture", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
			"Fixture", Collections.emptyList());
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sets", "2"); values.put("repetitions", "8"); values.put("loadKg", "0"); values.put("restSeconds", "60");
		return new Routine("fixture-base", 1, "Fixture", profile,
			Collections.singletonList(new RoutineItem(exercise, values, 5, "Fixture")), "Fixture", metadata);
	}

	@Test public void legacyConstructorRemainsWithoutMetadataAndProgressesByDefault() {
		Routine template = routine(null);
		Routine legacy = new Routine(template.getProposalId(), template.getVersionId(), template.getTitle(),
			template.getProfile(), template.getItems(), template.getExplanation());
		assertNull(legacy.getMetadata());
		assertTrue(legacy.isProgressionEnabled());
		Routine marked = legacy.withMetadata(new RoutineMetadata(RoutineMetadata.Origin.AI, true));
		assertNull(legacy.getMetadata());
		assertEquals(legacy.getProposalId(), marked.getProposalId());
		assertEquals(legacy.getVersionId(), marked.getVersionId());
		assertSame(legacy.getProfile(), marked.getProfile());
		assertEquals(legacy.getItems(), marked.getItems());
	}

	@Test public void metadataSurvivesLocalIdRemappingAndNewProgressionVersion() {
		for (RoutineMetadata.Origin origin : RoutineMetadata.Origin.values()) {
			RoutineMetadata metadata = new RoutineMetadata(origin, true);
			Routine routine = routine(metadata);
			assertSame(metadata, routine.withVersionId(7).getMetadata());
			Session finished = new Session("fixture-session", routine,
				Collections.singletonMap("fixture", routine.getItems().get(0).getPrescription()), 100, 200, Session.Status.FINISHED);
			ProgressionEngine.Plan next = new ProgressionEngine().next(routine, Collections.singletonList(finished));
			assertNotNull(next);
			assertSame(metadata, next.routine.getMetadata());
			assertNotEquals(routine.getProposalId(), next.routine.getProposalId());
			assertEquals("8", routine.getItems().get(0).getPrescription().get("repetitions"));
		}
	}

	@Test public void explicitOffPreventsProgressionDespiteCompletedSession() {
		Routine routine = routine(new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
		Session finished = new Session("fixture-session", routine,
			Collections.singletonMap("fixture", routine.getItems().get(0).getPrescription()), 100, 200, Session.Status.FINISHED);
		assertFalse(routine.isProgressionEnabled());
		assertNull(new ProgressionEngine().next(routine, Collections.singletonList(finished)));
	}

	@Test public void metadataRejectsUnknownOriginAndHasValueSemantics() {
		assertThrows(NullPointerException.class, () -> new RoutineMetadata(null, true));
		RoutineMetadata first = new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false);
		assertEquals(first, new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
		assertEquals(first.hashCode(), new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false).hashCode());
		assertNotEquals(first, new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
	}
}
