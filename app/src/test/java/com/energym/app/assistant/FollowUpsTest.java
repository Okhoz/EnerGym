package com.energym.app.assistant;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	public void proteinQuestionMovesOnToWhatToEatBeforeTraining() {
		List<FollowUps.Topic> t = FollowUps.suggest("¿Cuánta proteína como?", "Unos 1,6 g por kg de peso.", false);
		assertFalse("Acaba de preguntarlo", t.contains(PROTEIN));
		assertEquals(PRE_WORKOUT, t.get(0));
	}

	@Test
	public void accentsAndCaseDoNotMatter() {
		assertTrue(FollowUps.suggest("TÉCNICA del peso muerto", "Espalda recta.", false).contains(ALTERNATIVE));
	}

	@Test
	public void genericChatFallsBackToDefaults() {
		assertEquals(Arrays.asList(ROUTINE_TODAY, PROGRESSION, MOTIVATION), FollowUps.suggest("hola", "¡Hola! 👋", false));
		assertEquals(Arrays.asList(ROUTINE_TODAY, PROGRESSION, MOTIVATION), FollowUps.suggest((String) null, null, false));
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
	public void fatigueSuggestsRestDaysAndSleep() {
		List<FollowUps.Topic> t = FollowUps.suggest("Estoy muy cansado y con agujetas", "Hoy algo suave.", false);
		assertEquals(Arrays.asList(REST, SLEEP, MOTIVATION), t);
	}

	@Test
	public void warmupQuestionIsNotSuggestedBackUnlessARoutineCame() {
		assertFalse(FollowUps.suggest("¿Cómo caliento?", "5 min de movilidad.", false).contains(WARMUP));
		// Caso visto en el emulador: la sugerencia «¿Cómo caliento antes?» no debe volver a salir tras tocarla.
		assertFalse(FollowUps.suggest("¿Cómo caliento antes?", "Puedo explicarte la técnica.", false).contains(WARMUP));
		assertFalse(FollowUps.suggest("¿Cómo calentar?", "", false).contains(WARMUP));
		assertTrue(FollowUps.suggest("Rutina y calentamiento", "Aquí la tienes.", true).contains(WARMUP));
	}

	/** Caso visto en el emulador: tras preguntar el descanso entre series salía «¿Cuánto debo descansar?». */
	@Test
	public void restBetweenSetsGoesDeeperInsteadOfAskingAboutRestAgain() {
		List<FollowUps.Topic> t = FollowUps.suggest("cuanto descanso entre series para ganar musculo",
			"Descanso entre series: 2–3 min en ejercicios básicos.", false);
		assertFalse(t.contains(REST_SETS));
		assertFalse(t.contains(REST));
		assertEquals(Arrays.asList(SETS_PER_MUSCLE, SUPERSETS, PROGRESSION), t);
	}

	@Test
	public void nothingAlreadyAskedEarlierInTheChatComesBack() {
		List<FollowUps.Topic> t = FollowUps.suggest(Arrays.asList("¿Cuánta proteína necesito?",
			"Quiero perder grasa"), "Un déficit moderado y proteína alta.", false);
		assertEquals(DEFICIT, t.get(0));
		assertTrue(t.contains(CARDIO));
		assertFalse("Se preguntó al principio de la conversación", t.contains(PROTEIN));
	}

	@Test
	public void shortRepliesKeepTheThreadOfThePreviousTopic() {
		List<FollowUps.Topic> afterThanks = FollowUps.suggest(Arrays.asList("Quiero perder grasa", "vale, gracias"),
			"¡A por ello!", false);
		assertEquals(Arrays.asList(DEFICIT, CARDIO, PROTEIN), afterThanks);
	}

	@Test
	public void suggestionsMoveForwardAsTheUserTapsThem() {
		List<String> chat = new ArrayList<>();
		chat.add("cuanto descanso entre series para ganar musculo");
		List<FollowUps.Topic> first = FollowUps.suggest(chat, "2–3 min.", false);
		// El cliente toca la primera sugerencia: llega como su mensaje y no vuelve a ofrecerse.
		chat.add("¿Cuántas series hago por músculo?");
		List<FollowUps.Topic> second = FollowUps.suggest(chat, "Entre 10 y 20 series semanales.", false);
		assertEquals(SETS_PER_MUSCLE, first.get(0));
		assertFalse(second.contains(SETS_PER_MUSCLE));
		assertFalse(second.contains(REST_SETS));
		assertEquals(PROTEIN, second.get(0));
	}

	@Test
	public void exerciseQuestionsOfferTechniqueAlternativesAndHome() {
		List<FollowUps.Topic> t = FollowUps.suggest("técnica de la sentadilla", "Rodillas hacia fuera.", false);
		assertEquals(Arrays.asList(ALTERNATIVE, PROGRESSION, HOME), t);
	}

	@Test
	public void everyTopicHasAWayToAppear() {
		Set<FollowUps.Topic> seen = new HashSet<>(FollowUps.STARTERS);
		String[] chats = {"me duele", "rutina", "estancado", "kg", "técnica de la sentadilla", "proteína", "agujetas",
			"hola", "perder grasa", "descanso entre series", "poco tiempo", "fuerza", "correr", "calentar"};
		for (String c : chats)
			seen.addAll(FollowUps.suggest(c, "", false));
		seen.addAll(FollowUps.suggest("", "", true));
		assertEquals(EnumSet.allOf(FollowUps.Topic.class), seen);
	}

	/** Cada sugerencia tiene su texto en los dos idiomas y, al tocarla, se reconoce como ya preguntada. */
	@Test
	public void everyLabelExistsInBothLanguagesAndCountsAsAsked() throws Exception {
		for (String folder : new String[]{"values", "values-en"}) {
			Map<String, String> strings = strings(folder);
			for (FollowUps.Topic topic : FollowUps.Topic.values()) {
				String label = strings.get("followup_" + topic.name().toLowerCase(Locale.ROOT));
				assertNotNull(folder + ": falta followup_" + topic.name().toLowerCase(Locale.ROOT), label);
				assertTrue(folder + " «" + label + "» no se reconoce como " + topic,
					FollowUps.asked(label).contains(topic));
			}
		}
	}

	/** Las preguntas de seguimiento nuevas también tienen respuesta en el guiado local (móviles sin IA). */
	@Test
	public void newLabelsHaveACoachCardForLocalGuidance() throws Exception {
		EnumSet<FollowUps.Topic> covered = EnumSet.of(REST, REST_SETS, SETS_PER_MUSCLE, SUPERSETS, PROTEIN, DEFICIT,
			CARDIO, SLEEP, SPLIT_DAYS, PRE_WORKOUT, MOBILITY, HOME);
		for (String folder : new String[]{"values", "values-en"}) {
			Map<String, String> strings = strings(folder);
			for (FollowUps.Topic topic : covered) {
				String label = strings.get("followup_" + topic.name().toLowerCase(Locale.ROOT));
				assertFalse(folder + " «" + label + "» sin ficha del entrenador",
					CoachKnowledge.topics(label).isEmpty());
			}
		}
	}

	private static Map<String, String> strings(String folder) throws Exception {
		File file = new File("src/main/res/" + folder + "/strings.xml");
		if (!file.exists())
			file = new File("app/src/main/res/" + folder + "/strings.xml");
		String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		Map<String, String> found = new HashMap<>();
		Matcher m = Pattern.compile("<string name=\"(followup_[a-z_]+)\">([^<]*)</string>").matcher(xml);
		while (m.find())
			found.put(m.group(1), m.group(2).replace("\\'", "'"));
		return found;
	}
}
