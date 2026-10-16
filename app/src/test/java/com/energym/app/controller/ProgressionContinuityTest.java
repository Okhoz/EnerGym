package com.energym.app.controller;

import com.energym.app.catalog.CatalogRepository;
import com.energym.app.domain.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Una rutina nueva del entrenador no reinicia la carga que la persona ya completó. */
public class ProgressionContinuityTest {
	private final Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH,
		EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.GYM),
		EnumSet.of(TrainingProfile.Resource.BARBELL), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC,
		false, "Técnica", Collections.emptyList());

	private final TrainingProfile profile = new TrainingProfile.Builder().goal(TrainingProfile.Goal.STRENGTH)
		.environment(TrainingProfile.Environment.GYM).category(TrainingProfile.Category.GYM)
		.resources(EnumSet.of(TrainingProfile.Resource.BARBELL)).durationMinutes(30).level("Intermedio").build();

	private Routine routine(String id, String load) {
		Map<String, String> p = new LinkedHashMap<>();
		p.put("sets", "3");
		p.put("repetitions", "8");
		p.put("loadKg", load);
		p.put("restSeconds", "120");
		return new Routine(id, 0, "Fuerza", profile,
			Collections.singletonList(new RoutineItem(squat, p, 10, "Base")), "Base");
	}

	@Test
	public void aNewRoutineStartsFromTheLastCompletedLoad() {
		AppController app = new AppController(new AppControllerTest.Store(),
			new CatalogRepository(1, Collections.singletonList(new Sport("strength", "Fuerza",
				Sport.CoverageStatus.EXAMPLE_UNREVIEWED, Sport.CoverageStatus.EXAMPLE_UNREVIEWED)),
				Collections.singletonList(squat)));
		Routine old = routine("old", "60");
		Map<String, String> done = new LinkedHashMap<>(old.getItems().get(0).getPrescription());
		done.put("loadKg", "72.5");
		long now = System.currentTimeMillis();
		app.history = Collections.singletonList(new Session(UUID.randomUUID().toString(), old,
			Collections.singletonMap("squat", done), now - 3600000, now, Session.Status.FINISHED));
		Routine adjusted = app.continueFrom(routine("new", "20"));
		RoutineItem item = adjusted.getItems().get(0);
		assertEquals("72.5", item.getPrescription().get("loadKg"));
		assertTrue(item.getJustification().contains("72,5 kg"));
		assertEquals("new", adjusted.getProposalId());
		app.history = Collections.emptyList();
		Routine untouched = routine("fresh", "20");
		assertSame(untouched, app.continueFrom(untouched));
	}
}
