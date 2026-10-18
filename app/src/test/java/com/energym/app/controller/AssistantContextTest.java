package com.energym.app.controller;

import com.energym.app.domain.*;
import com.energym.app.nutrition.*;
import com.energym.app.progress.ProgressStats;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.*;

/** El entrenador IA recibe un resumen real de toda la app, por secciones y sin pasarse de tamaño. */
public class AssistantContextTest {
	private final Exercise squat = new Exercise("squat", "Sentadilla", MetricType.STRENGTH,
		EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.GYM),
		EnumSet.of(TrainingProfile.Resource.BARBELL), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC,
		false, "Técnica", Collections.emptyList());

	private Session session(String load, long at) {
		TrainingProfile p = new TrainingProfile.Builder().goal(TrainingProfile.Goal.STRENGTH)
			.environment(TrainingProfile.Environment.GYM).category(TrainingProfile.Category.GYM)
			.resources(EnumSet.of(TrainingProfile.Resource.BARBELL)).durationMinutes(30).build();
		Map<String, String> prescription = new LinkedHashMap<>();
		prescription.put("sets", "3");
		prescription.put("repetitions", "8");
		prescription.put("loadKg", load);
		prescription.put("restSeconds", "120");
		Routine r = new Routine("r", 1, "Fuerza", p,
			Collections.singletonList(new RoutineItem(squat, prescription, 10, "Base")), "Base");
		return new Session(UUID.randomUUID().toString(), r, Collections.singletonMap("squat", prescription),
			at - 3600000, at, Session.Status.FINISHED);
	}

	@Test
	public void progressSectionSumsSessionsStreakAndRecords() {
		long now = System.currentTimeMillis();
		ProgressStats stats = new ProgressStats(Arrays.asList(session("60", now - 7 * 86400000L), session("70", now)),
			ZoneId.systemDefault(), LocalDate.now());
		String text = AssistantContext.progress(stats);
		assertTrue(text, text.contains("Total: 2 sesiones"));
		assertTrue(text, text.contains("Racha:"));
		assertTrue(text, text.contains("Sentadilla 60→70 kg"));
		assertEquals("Aún no ha terminado ninguna sesión.", AssistantContext.progress(
			new ProgressStats(Collections.emptyList(), ZoneId.systemDefault(), LocalDate.now())));
	}

	@Test
	public void dietSectionListsTheMenuAndNoAccountMeansNoContext() {
		DietPlan plan = new DietPlan(Arrays.asList(
			new DietPlan.Meal("Comida", "Lentejas", "lentejas", 700, 35, 90, 15, 1.2),
			new DietPlan.Meal("Cena", "Tortilla", "huevo", 500, 30, 20, 25, 0.9)), "", "Creado con IA (Gemma)", 1);
		DietPlanner planner = new DietPlanner(null, new DietPlanner.Store() {
			public String load(String owner) {
				return plan.encode();
			}

			public void save(String owner, String json) {
			}
		}, Runnable::run, () -> { }, () -> "");
		planner.activate("owner");
		String text = AssistantContext.diet(planner);
		assertTrue(text, text.contains("Comida: Lentejas (700 kcal)"));
		assertTrue(text, text.contains("≈ 2.10 €"));
		assertEquals("", AssistantContext.build(null, null, null, planner, LocalDate.now()));
	}

	@Test
	public void nextLoadsGivesTheCoachTheExactNextWeight() {
		long now = System.currentTimeMillis();
		String text = AssistantContext.nextLoads(Arrays.asList(session("80", now - 86400000L), session("80", now)));
		assertTrue(text, text.startsWith("Sentadilla: última vez 80 kg × 8"));
		assertTrue(text, text.contains("sube a 82.5 kg"));
	}
}
