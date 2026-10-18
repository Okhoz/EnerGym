package com.energym.app.assistant;

import org.junit.Test;

import static org.junit.Assert.*;

/** El entrenador recibe las pautas del tema que se pregunta, en español o en inglés, y sin pasarse de tamaño. */
public class CoachKnowledgeTest {
	@Test
	public void painQuestionsGetTheSafetyCardFirst() {
		String cards = CoachKnowledge.forMessage("Me duele la rodilla al hacer sentadilla, ¿qué hago?");
		assertTrue(cards, cards.startsWith("• Dolor"));
		assertTrue(cards, cards.contains("Sentadilla"));
	}

	@Test
	public void englishQuestionsAlsoFindTheirTopic() {
		assertTrue(CoachKnowledge.forMessage("How much protein should I eat?").contains("1,6–2,2 g/kg"));
		assertTrue(CoachKnowledge.topics("should I add weight to my bench?").contains(CoachKnowledge.Topic.BENCH));
	}

	@Test
	public void cardsStayWithinTheirBudget() {
		String cards = CoachKnowledge.forMessage("dolor, subir peso, músculo, fuerza, perder grasa, proteína, calentar, " +
			"dormir, sentadilla, banca, peso muerto, dominadas, hombro, correr, motivación, principiante, casa");
		assertTrue(cards.length() <= CoachKnowledge.MAX_CHARS);
		assertTrue(cards.split("\n").length <= CoachKnowledge.MAX_CARDS);
	}

	@Test
	public void smallTalkGetsNoCards() {
		assertEquals("", CoachKnowledge.forMessage("¡Gracias!"));
	}

	@Test
	public void theExampleMatchesTheKindOfQuestion() {
		assertTrue(TrainerPrompt.example("me duele el hombro").contains("rodilla"));
		assertTrue(TrainerPrompt.example("¿cómo hago dominadas?").contains("peso muerto"));
		assertTrue(TrainerPrompt.example("¿subo peso?").contains("82,5 kg"));
	}
}
