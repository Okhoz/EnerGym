package com.energym.app.progress;

import com.energym.app.domain.*;
import com.energym.app.visual.TestCatalog;
import org.junit.Test;

import java.time.*;
import java.util.*;

import static org.junit.Assert.*;

public class WorkoutImportTest {
	private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
	private static final TrainingProfile PROFILE = new TrainingProfile.Builder().build();

	private static final String STRONG =
		"Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Bench Press (Barbell),W,40,10,0,0,,,\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Bench Press (Barbell),1,80,8,0,0,,,8\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Bench Press (Barbell),2,80,7,0,0,,,9\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Triceps Pushdown (Cable - Straight Bar),1,30,12,0,0,,,\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Plank,1,0,0,0,60,,,\n" +
			"2026-10-01 18:00:00,Push,1h 5m,Zercher Carry,1,60,1,0,0,,,\n" +
			"2026-10-03 10:00:00,Legs,45m,Squat (Barbell),1,100,5,0,0,\"pesado, bien\",,\n";

	private static final String HEVY =
		"title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_lbs,reps,distance_miles,duration_seconds,rpe\n" +
			"Pull,\"5 Oct 2026, 09:00\",\"5 Oct 2026, 10:10\",,Lat Pulldown (Cable),,,0,warmup,50,12,,,\n" +
			"Pull,\"5 Oct 2026, 09:00\",\"5 Oct 2026, 10:10\",,Lat Pulldown (Cable),,,1,normal,100,10,,,8\n" +
			"Pull,\"5 Oct 2026, 09:00\",\"5 Oct 2026, 10:10\",,Bicep Curl (Dumbbell),,,0,dropset,20,8,,,\n";

	@Test
	public void strongWorkoutsBecomeFinishedSessions() throws Exception {
		WorkoutImport.Result r = WorkoutImport.parse(STRONG, TestCatalog.exercises(), PROFILE, ZONE);
		assertEquals(WorkoutImport.Source.STRONG, r.source);
		assertEquals(2, r.sessions.size());
		Session push = r.sessions.get(0);
		assertEquals(Session.Status.FINISHED, push.getStatus());
		assertEquals("Push", push.getRoutine().getTitle());
		assertEquals(65 * 60_000, push.getCompletedAt() - push.getStartedAt());
		assertEquals(LocalDateTime.of(2026, 10, 1, 18, 0).atZone(ZONE).toInstant().toEpochMilli(), push.getStartedAt());
		Map<String, String> bench = push.getResults().get("barbell-bench-press");
		List<SetLog.Entry> sets = SetLog.of(bench);
		assertEquals(3, sets.size());
		assertEquals(SetLog.Kind.WARMUP, sets.get(0).kind);
		assertEquals("Las de calentamiento no cuentan en el resumen", "2", bench.get("sets"));
		assertEquals("80", bench.get("loadKg"));
		assertEquals(8, sets.get(1).rpe, 0.001);
		assertTrue(push.getResults().containsKey("cable-triceps-pushdown"));
		assertEquals(30 * 60_000 + 15 * 60_000, r.sessions.get(1).getCompletedAt() - r.sessions.get(1).getStartedAt());
		assertTrue(r.unmatched.contains("Zercher Carry"));
		assertTrue("Plancha es isométrica: no se importa", r.unmatched.contains("Plank"));
	}

	@Test
	public void hevyPoundsAndSetTypesAreConverted() throws Exception {
		WorkoutImport.Result r = WorkoutImport.parse(HEVY, TestCatalog.exercises(), PROFILE, ZONE);
		assertEquals(WorkoutImport.Source.HEVY, r.source);
		assertEquals(1, r.sessions.size());
		Session pull = r.sessions.get(0);
		assertEquals(70 * 60_000, pull.getCompletedAt() - pull.getStartedAt());
		List<SetLog.Entry> lat = SetLog.of(pull.getResults().get("lat-pulldown"));
		assertEquals(SetLog.Kind.WARMUP, lat.get(0).kind);
		assertEquals(45.36, lat.get(1).kg, 0.01);
		assertEquals(SetLog.Kind.DROP, SetLog.of(pull.getResults().get("dumbbell-biceps-curl")).get(0).kind);
		assertTrue(r.unmatched.isEmpty());
	}

	@Test
	public void importIsIdempotent() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		assertEquals(WorkoutImport.parse(STRONG, catalog, PROFILE, ZONE).sessions.get(0).getSyncId(),
			WorkoutImport.parse(STRONG, catalog, PROFILE, ZONE).sessions.get(0).getSyncId());
	}

	@Test
	public void semicolonsAndDecimalCommasWork() throws Exception {
		String csv = "Date;Workout Name;Duration;Exercise Name;Set Order;Weight;Reps\n" +
			"2026-10-01 18:00:00;Push;30m;Bench Press (Barbell);1;82,5;5\n";
		Session s = WorkoutImport.parse(csv, TestCatalog.exercises(), PROFILE, ZONE).sessions.get(0);
		assertEquals("82.5", s.getResults().get("barbell-bench-press").get("loadKg"));
	}

	@Test
	public void unknownFilesGiveNull() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		assertNull(WorkoutImport.parse("a,b,c\n1,2,3\n", catalog, PROFILE, ZONE));
		assertNull(WorkoutImport.parse("", catalog, PROFILE, ZONE));
		assertNull(WorkoutImport.parse(STRONG, catalog, null, ZONE));
		assertNull(WorkoutImport.parse(null, catalog, PROFILE, ZONE));
	}

	@Test
	public void matchesEnglishNamesAndOwnCatalogNames() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		assertEquals("barbell-back-squat", WorkoutImport.match("Squat (Barbell)", catalog));
		assertEquals("dumbbell-squat", WorkoutImport.match("Squat (Dumbbell)", catalog));
		assertEquals("pull-up", WorkoutImport.match("Pull Up", catalog));
		assertEquals("lat-pulldown", WorkoutImport.match("Lat Pulldown (Machine)", catalog));
		assertEquals("barbell-bench-press", WorkoutImport.match("Press de banca con barra", catalog));
		assertNull(WorkoutImport.match("Zercher Carry", catalog));
		assertNull(WorkoutImport.match("", catalog));
	}

	@Test
	public void everyMappedIdExistsInTheCatalog() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		for (String name : new String[]{"Bench Press (Barbell)", "Deadlift (Barbell)", "Overhead Press (Barbell)",
			"Bent Over Row (Barbell)", "Leg Press", "Leg Extension (Machine)", "Lying Leg Curl (Machine)",
			"Hammer Curl (Dumbbell)", "Lateral Raise (Dumbbell)", "Face Pull (Cable)", "Hip Thrust (Barbell)",
			"Chin Up", "Push Up", "Romanian Deadlift (Barbell)", "Seated Row (Cable)", "Calf Raise (Machine)"}) {
			String id = WorkoutImport.match(name, catalog);
			assertNotNull(name, id);
			assertTrue(name, catalog.stream().anyMatch(e -> e.getId().equals(id)));
		}
	}

	@Test
	public void exportInStrongFormatRoundTrips() throws Exception {
		List<Exercise> catalog = TestCatalog.exercises();
		WorkoutImport.Result first = WorkoutImport.parse(STRONG, catalog, PROFILE, ZONE);
		String csv = WorkoutImport.exportStrong(first.sessions, ZONE);
		assertTrue(csv.startsWith("Date,Workout Name"));
		assertTrue(csv.contains(",W,40,10,"));
		assertTrue(csv.contains("\"pesado, bien\""));
		WorkoutImport.Result again = WorkoutImport.parse(csv, catalog, PROFILE, ZONE);
		assertEquals(2, again.sessions.size());
		assertTrue(again.unmatched.isEmpty());
		assertEquals(first.sessions.get(0).getResults().get("barbell-bench-press").get(SetLog.KEY),
			again.sessions.get(0).getResults().get("barbell-bench-press").get(SetLog.KEY));
	}

	@Test
	public void durationsAndDates() {
		assertEquals(65, WorkoutImport.minutes("1h 5m"));
		assertEquals(45, WorkoutImport.minutes("45m"));
		assertEquals(30, WorkoutImport.minutes("1800"));
		assertEquals(60, WorkoutImport.minutes("?"));
		assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), WorkoutImport.date("5 Oct 2026, 09:00"));
		assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), WorkoutImport.date("2026-10-05T09:00:00"));
		assertNull(WorkoutImport.date("ayer"));
	}
}
