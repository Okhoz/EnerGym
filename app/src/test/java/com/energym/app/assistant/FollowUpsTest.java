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

	@Test
	public void stalledLiftsSuggestADeloadFirst() {
		assertEquals(DELOAD, FollowUps.suggest("Llevo semanas estancado", "Cambia el rango de repeticiones.", false).get(0));
	}

	@Test
	public void fatigueSuggestsRestAndMotivation() {
		List<FollowUps.Topic> t = FollowUps.suggest("Estoy muy cansado y con agujetas", "Hoy algo suave.", false);
		assertEquals(Arrays.asList(REST, MOTIVATION, ROUTINE_TODAY), t);
	}

	@Test
	public void warmupQuestionIsNotSuggestedBackUnlessARoutineCame() {
		assertFalse(FollowUps.suggest("¿Cómo caliento?", "5 min de movilidad.", false).contains(WARMUP));
		// Caso visto en el emulador: la sugerencia «¿Cómo caliento antes?» no debe volver a salir tras tocarla.
		assertFalse(FollowUps.suggest("¿Cómo caliento antes?", "Puedo explicarte la técnica.", false).contains(WARMUP));
		assertFalse(FollowUps.suggest("¿Cómo calentar?", "", false).contains(WARMUP));
		assertTrue(FollowUps.suggest("Rutina y calentamiento", "Aquí la tienes.", true).contains(WARMUP));
	}

	@Test
	public void everyTopicHasAWayToAppear() {
		Set<FollowUps.Topic> seen = new HashSet<>(FollowUps.STARTERS);
		String[] chats = {"me duele", "rutina", "estancado", "kg", "técnica", "dieta", "dormir", "hola"};
		for (String c : chats)
			seen.addAll(FollowUps.suggest(c, "", false));
		seen.addAll(FollowUps.suggest("", "", true));
		assertEquals(EnumSet.allOf(FollowUps.Topic.class), seen);
	}
}
