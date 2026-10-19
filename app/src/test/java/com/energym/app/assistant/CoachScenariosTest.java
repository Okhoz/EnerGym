package com.energym.app.assistant;

import com.energym.app.controller.AssistantContextAccess;
import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Las 20 preguntas de docs/ai-eval.md, de principio a fin sin el modelo: qué conocimiento y cifras le llegan a la IA
 * del móvil, cómo se corrige su respuesta y qué contesta el guiado local cuando el móvil no tiene IA.
 */
public class CoachScenariosTest {
	private final List<Exercise> catalog = Arrays.asList(
		exercise("squat", "Sentadilla", MetricType.STRENGTH),
		exercise("bench", "Press de banca", MetricType.STRENGTH),
		exercise("deadlift", "Peso muerto", MetricType.STRENGTH),
		exercise("pullup", "Dominada", MetricType.STRENGTH),
		exercise("plank", "Plancha", MetricType.ISOMETRIC),
		exercise("jog", "Trote suave", MetricType.ENDURANCE));
	private final TrainingProfile profile = new TrainingProfile.Builder().goal(Goal.GENERAL_FITNESS)
		.durationMinutes(45).build();
	/** Contexto como el que arma AssistantContext: próxima carga calculada y cifras personales. */
	private static final String TRAINING = "[ENTRENAMIENTO]\nSesiones terminadas: 6\n" +
		"[PRÓXIMA CARGA CALCULADA]\n" +
		"Sentadilla: última vez 80 kg × 8 (rango 6–8) → sube a 82.5 kg y busca 6 repeticiones.\n" +
		"Press de banca: última vez 60 kg × 7 (rango 6–8) → mismo peso (60 kg), busca 8 repeticiones.\n" +
		"[TUS CIFRAS (calculadas por la app)]\n" +
		"Proteína recomendada: 120–165 g al día (1,6–2,2 g/kg con 75 kg). Agua: unos 2,6 L al día.";

	private static Exercise exercise(String id, String name, MetricType type) {
		return new Exercise(id, name, type, EnumSet.of(Goal.GENERAL_FITNESS), EnumSet.of(Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
			"Técnica de " + name, Collections.emptyList());
	}

	private ChatClient.Request ask(String message) {
		ChatClient.Request r = new ChatClient.Request("c", "r", null, message, profile, ChatProtocol.profile(profile),
			false, Collections.emptyList(), Collections.emptyList());
		r.training = TRAINING;
		r.conversation = true;
		return r;
	}

	private String prompt(String message) {
		return TrainerPrompt.build(ask(message), catalog);
	}

	private String replyText(String message, String modelText) {
		String json = TrainerPrompt.reply("{\"texto\":\"" + modelText + "\",\"rutina\":null}", ask(message), catalog, 1);
		return String.valueOf(StrictJson.parse(json) instanceof Map ? ((Map<?, ?>) StrictJson.parse(json)).get("text") : "");
	}

	/** Respuesta del guiado local (sin IA) a una pregunta suelta. */
	private String local(String message) {
		ProposalControllerTest fixture = new ProposalControllerTest();
		ProposalController c = fixture.controller();
		c.sendLocal(message);
		return c.messages.get(c.messages.size() - 1).text;
	}

	// 1
	@Test
	public void increaseSquatUsesTheCalculatedLoadAndFixesAWrongAnswer() {
		String p = prompt("¿Subo peso en sentadilla?");
		assertTrue(p.contains("doble progresión"));
		assertTrue(p.contains("sube a 82.5 kg y busca 6 repeticiones"));
		String fixed = replyText("¿Subo peso en sentadilla?", "¡Sí! Sube un poco el peso.");
		assertTrue(fixed, fixed.contains("Sentadilla: 82,5 kg × 6 reps"));
		String right = replyText("¿Subo peso en sentadilla?", "¡Sí! Sube a 82,5 kg y busca 6 repeticiones.");
		assertFalse(right, right.contains("Según tu historial"));
	}

	// 2
	@Test
	public void stalledBenchGetsDeloadAdviceAndItsOwnNumbers() {
		String p = prompt("Llevo semanas sin mejorar en press de banca");
		assertTrue(p.contains("descarga"));
		String fixed = replyText("Llevo semanas estancado en press de banca", "Cambia de variante.");
		assertTrue(fixed, fixed.contains("Press de banca: 60 kg × 8 reps"));
	}

	// 3
	@Test
	public void muscleVolumeQuestionGetsWeeklySets() {
		assertTrue(prompt("¿Cuántas series hago para ganar músculo en pierna?").contains("10–20 series efectivas"));
	}

	// 4
	@Test
	public void kneePainPutsSafetyFirstInPromptExampleAndLocalGuidance() {
		String message = "Me duele la rodilla al hacer sentadilla";
		assertEquals(CoachKnowledge.Topic.PAIN, CoachKnowledge.topics(message).get(0));
		assertTrue(prompt(message).contains("No diagnostiques"));
		assertTrue(TrainerPrompt.example(message).contains("fisio"));
		assertTrue(local(message).startsWith("Dolor:"));
	}

	// 5
	@Test
	public void deadliftTechniqueGetsCuesAndATechniqueExample() {
		String message = "¿Cómo hago bien el peso muerto?";
		assertTrue(prompt(message).contains("barra sobre el medio del pie"));
		assertTrue(TrainerPrompt.example(message).contains("empujar el suelo"));
	}

	// 6
	@Test
	public void proteinQuestionGetsTheRangeAndThePersonalGrams() {
		String p = prompt("¿Cuánta proteína necesito?");
		assertTrue(p.contains("1,6–2,2 g/kg"));
		assertTrue(p.contains("120–165 g al día"));
	}

	// 7
	@Test
	public void fatLossWithoutLosingMuscleGetsDeficitAndStaysWithinBudget() {
		String message = "Quiero perder grasa sin perder músculo";
		String cards = CoachKnowledge.forMessage(message);
		assertTrue(cards.contains("déficit de 300–500 kcal"));
		assertTrue(cards.length() <= CoachKnowledge.MAX_CHARS);
	}

	// 8
	@Test
	public void whatDoIDoTodayAlwaysEndsWithARoutineCard() {
		String json = TrainerPrompt.reply("{\"texto\":\"Hoy toca cuerpo completo.\",\"rutina\":null}", ask("¿Qué hago hoy?"),
			catalog, 1);
		Map<?, ?> m = (Map<?, ?>) StrictJson.parse(json);
		assertNotNull(m.get("proposal"));
	}

	// 9
	@Test
	public void specificRoutineRequestTellsTheModelWhatToTrain() {
		ChatClient.Request r = ask("Hazme una rutina de espalda y bíceps de 45 minutos");
		r.conversation = false;
		String p = TrainerPrompt.build(r, catalog);
		assertTrue(p.contains("LO QUE PIDE PARA HOY"));
		assertTrue(p.contains("espalda"));
	}

	// 10
	@Test
	public void noMotivationGetsAMinimalPlanAndAMotivationExample() {
		String message = "No tengo ganas de entrenar";
		assertTrue(prompt(message).contains("plan mínimo"));
		assertTrue(TrainerPrompt.example(message).contains("20 minutos"));
	}

	// 11
	@Test
	public void warmUpGetsRampUpSets() {
		assertTrue(prompt("¿Cómo caliento antes de pierna?").contains("series de aproximación"));
	}

	// 12
	@Test
	public void sorenessIsAnsweredEvenWithoutAi() {
		String message = "Tengo agujetas, ¿entreno igual?";
		assertTrue(prompt(message).contains("agujetas no indican"));
		assertTrue(local(message).contains("agujetas no indican"));
	}

	// 13
	@Test
	public void creatineGetsTheEvidenceBasedDose() {
		assertTrue(prompt("¿Me tomo creatina?").contains("3–5 g al día"));
	}

	// 14
	@Test
	public void homeWithoutEquipmentGetsProgressions() {
		assertTrue(prompt("Entreno en casa sin material, ¿cómo progreso?").contains("flexiones inclinadas"));
	}

	// 15
	@Test
	public void restBetweenSetsBeatsGeneralRecovery() {
		String message = "¿Cuánto descanso entre series?";
		assertEquals(CoachKnowledge.Topic.REST_SETS, CoachKnowledge.topics(message).get(0));
		assertTrue(local(message).startsWith("Descanso entre series"));
	}

	// 16
	@Test
	public void firstPullUpGetsNegativesAndBands() {
		assertTrue(prompt("Quiero hacer mi primera dominada").contains("negativas lentas"));
	}

	// 17
	@Test
	public void weeklyCheckInGetsRealNumbersFromHistory() {
		String personal = AssistantContextAccess.personal(sessions());
		assertTrue(personal, personal.contains("1RM estimado (mejor serie): Sentadilla"));
		String loads = AssistantContextAccess.nextLoads(sessions());
		assertTrue(loads, loads.contains("Sentadilla: última vez 80 kg × 8"));
		assertTrue(prompt("¿Cómo voy esta semana?").contains("Sesiones terminadas: 6"));
	}

	// 18
	@Test
	public void englishProgressionQuestionFindsItsTopicsAndTheExercise() {
		String message = "Should I increase the weight on squats?";
		List<CoachKnowledge.Topic> topics = CoachKnowledge.topics(message);
		assertTrue(topics.contains(CoachKnowledge.Topic.PROGRESSION));
		assertTrue(topics.contains(CoachKnowledge.Topic.SQUAT));
	}

	// 19
	@Test
	public void englishFatLossQuestionFindsItsTopic() {
		assertEquals(CoachKnowledge.Topic.FAT_LOSS, CoachKnowledge.topics("I want to lose fat").get(0));
	}

	// 20
	@Test
	public void diagnosisRequestsAreRefused() {
		String message = "¿Puedes diagnosticarme una tendinitis?";
		assertEquals(CoachKnowledge.Topic.PAIN, CoachKnowledge.topics(message).get(0));
		assertTrue(prompt(message).contains("No diagnostiques"));
	}

	// Extra: el modelo pequeño a veces repite frases; no deben llegar dobles al chat.
	@Test
	public void repeatedSentencesAreRemoved() {
		String text = TrainerPrompt.dedupe("Sube a 82,5 kg y busca 6 repeticiones. Sube a 82,5 kg y busca 6 repeticiones.\n" +
			"• Calienta bien.\n• Calienta bien.");
		assertEquals("Sube a 82,5 kg y busca 6 repeticiones.\n• Calienta bien.", text);
	}

	private List<Session> sessions() {
		TrainingProfile p = new TrainingProfile.Builder().goal(Goal.STRENGTH).durationMinutes(45).build();
		Map<String, String> done = new LinkedHashMap<>();
		done.put("sets", "3");
		done.put("repetitions", "8");
		done.put("loadKg", "80");
		Routine r = new Routine("r", 1, "Fuerza", p,
			Collections.singletonList(new RoutineItem(catalog.get(0), done, 10, "Base")), "Base");
		long now = System.currentTimeMillis();
		return Arrays.asList(
			new Session("s1", r, Collections.singletonMap("squat", done), now - 90_000_000L, now - 86_400_000L,
				Session.Status.FINISHED),
			new Session("s2", r, Collections.singletonMap("squat", done), now - 3_600_000L, now, Session.Status.FINISHED));
	}
}
