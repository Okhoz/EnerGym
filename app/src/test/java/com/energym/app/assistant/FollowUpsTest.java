package com.energym.app.assistant;

import org.junit.Test;

import java.util.*;

import static com.energym.app.assistant.FollowUps.Topic.*;
import static org.junit.Assert.*;

public class FollowUpsTest {
	@Test
	public void neverMoreThanThreeAndNoDuplicates() {
		List<FollowUps.Topic> t = FollowUps.suggest("me duele la rodilla con la sentadilla y no avanzo de peso",
			"Baja la carga y revisa la técnica; come proteína y descansa.", true);
		assertTrue(t.size() <= FollowUps.MAX);
		assertEquals(new HashSet<>(t).size(), t.size());
	}

	@Test
	public void painSuggestsAlternativesAndRestFirst() {
		assertEquals(Arrays.asList(ALTERNATIVE, REST), FollowUps.suggest("Me molesta el hombro", "Para y consulta.",
			false).subList(0, 2));
	}

	@Test
	public void routineReplySuggestsWarmupAndNeverAnotherRoutine() {
		List<FollowUps.Topic> t = FollowUps.suggest("Hazme una rutina para hoy", "Aquí tienes tu rutina.", true);
		assertEquals(WARMUP, t.get(0));
		assertFalse(t.contains(ROUTINE_TODAY));
	}

	@Test
	public void loadQuestionsSuggestDeloadButNotTheSameQuestion() {
		List<FollowUps.Topic> t = FollowUps.suggest("¿Subo peso en press banca?", "Sí, sube a 62,5 kg con RPE 8.",
			false);
		assertFalse(t.contains(PROGRESSION));
		assertTrue(t.contains(DELOAD));
	}

	@Test
	public void nutritionTopics() {
		List<FollowUps.Topic> t = FollowUps.suggest("¿Cuánta proteína como?", "Unos 1,6 g por kg de peso.", false);
		assertFalse("Acaba de preguntarlo", t.contains(NUTRITION));
		assertTrue(t.contains(REST));
	}

	@Test
	public void accentsAndCaseDoNotMatter() {
		assertTrue(FollowUps.suggest("TÉCNICA del peso muerto", "Espalda recta.", false).contains(ALTERNATIVE));
	}

	@Test
	public void genericChatFallsBackToDefaults() {
		assertEquals(Arrays.asList(ROUTINE_TODAY, PROGRESSION, MOTIVATION), FollowUps.suggest("hola", "¡Hola! 👋", false));
		assertEquals(Arrays.asList(ROUTINE_TODAY, PROGRESSION, MOTIVATION), FollowUps.suggest(null, null, false));
	}

	@Test
	public void startersCoverTheMainUses() {
		assertTrue(FollowUps.STARTERS.contains(ROUTINE_TODAY));
		assertTrue(FollowUps.STARTERS.size() >= 3);
	}
}
