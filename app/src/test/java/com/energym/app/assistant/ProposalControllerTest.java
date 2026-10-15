package com.energym.app.assistant;

import com.energym.app.controller.*;
import com.energym.app.domain.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ProposalControllerTest {
	@Test
	public void manualReplacementDropsExternalLoadForBodyweightAndKeepsItsOriginalSnapshot() {
		Exercise loaded = new Exercise("loaded", "Sentadilla con mancuernas", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			EnumSet.of(TrainingProfile.Resource.DUMBBELLS), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Fixture", Collections.emptyList());
		catalog = Arrays.asList(loaded, exercise("body", "Fixture"));
		profile[0] = new TrainingProfile.Builder().category(TrainingProfile.Category.HOME_EQUIPMENT)
			.resources(EnumSet.of(TrainingProfile.Resource.DUMBBELLS)).build();
		Map<String, String> values = new LinkedHashMap<>(originFixture().getItems().get(0).getPrescription());
		values.put("loadKg", "5");
		Routine original = new Routine("manual-loaded", 0, "Manual", profile[0],
			Collections.singletonList(new RoutineItem(loaded, values, 5, "Fixture")), "Fixture",
			new RoutineMetadata(RoutineMetadata.Origin.MANUAL, false));
		RoutineController c = new RoutineController(store, () -> profile[0], catalog, () -> {}, e -> fail(e));
		c.preview = original;
		c.substitute(0, "body");
		assertEquals("body", c.preview.getItems().get(0).getExercise().getId());
		assertEquals("0", c.preview.getItems().get(0).getPrescription().get("loadKg"));
		assertEquals("5", original.getItems().get(0).getPrescription().get("loadKg"));
		assertEquals(RoutineMetadata.Origin.MANUAL, c.preview.getMetadata().getOrigin());
		assertFalse(c.preview.isProgressionEnabled());
		ChatProtocol.validatePrescription(c.preview.getItems().get(0).getExercise(),
			c.preview.getItems().get(0).getPrescription(), 5);
	}

	@Test
	public void manualProgressionUsesItsOwnContextAndManualPort() {
		catalog = Arrays.asList(exercise("one", "One"), exercise("two", "Two"));
		profile[0] = new TrainingProfile.Builder().build();
		Routine manual = originFixture().withMetadata(new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
		profile[0] = profile[0].toBuilder().environment(TrainingProfile.Environment.GYM)
			.category(TrainingProfile.Category.GYM).build();
		int[] manualWrites = {0};
		Store ownStore = new Store() {
			@Override public void acceptManual(Routine value, Callback<Routine> callback) {
				manualWrites[0]++;
				callback.success(value.withVersionId(1));
			}
		};
		List<String> errors = new ArrayList<>();
		RoutineController c = new RoutineController(ownStore, () -> profile[0], catalog, () -> {}, errors::add);
		c.preview = manual;
		c.accept();
		assertEquals("Una progresión manual no depende del perfil general", 1, manualWrites[0]);
		assertEquals(0, ownStore.writes);
		assertTrue(errors.isEmpty());
		assertEquals(RoutineMetadata.Origin.MANUAL, c.accepted.getMetadata().getOrigin());
		assertEquals(TrainingProfile.Environment.HOME, c.accepted.getProfile().getEnvironment());
		assertEquals(TrainingProfile.Environment.GYM, profile[0].getEnvironment());
	}

	@Test
	public void manualSubstitutionUsesTheRoutineContextInsteadOfTheGeneralProfile() {
		catalog = Arrays.asList(exercise("one", "One"), exercise("two", "Two"));
		profile[0] = new TrainingProfile.Builder().build();
		Routine manual = originFixture().withMetadata(new RoutineMetadata(RoutineMetadata.Origin.MANUAL, true));
		profile[0] = profile[0].toBuilder().environment(TrainingProfile.Environment.GYM)
			.category(TrainingProfile.Category.GYM).build();
		RoutineController c = new RoutineController(store, () -> profile[0], catalog, () -> {}, e -> fail(e));
		c.preview = manual;
		assertEquals(1, c.alternatives(0).size());
		assertEquals("two", c.alternatives(0).get(0).getId());
	}

	@Test
	public void localPlanningRecordsItsRealOrigin() {
		Routine routine = new RoutinePlanner().create(new TrainingProfile.Builder().build(), catalog);
		assertNotNull(routine.getMetadata());
		assertEquals(RoutineMetadata.Origin.LOCAL_RULES, routine.getMetadata().getOrigin());
		assertTrue(routine.isProgressionEnabled());
	}

	@Test
	public void assistantSubstitutionRetainsOriginAndDisabledProgression() {
		catalog = Arrays.asList(exercise("one", "One"), exercise("two", "Two"));
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		c.preview = originFixture();
		c.substituteFirst();
		assertEquals("two", c.preview.getItems().get(0).getExercise().getId());
		assertNotNull(c.preview.getMetadata());
		assertEquals(RoutineMetadata.Origin.AI, c.preview.getMetadata().getOrigin());
		assertFalse(c.preview.isProgressionEnabled());
	}

	@Test
	public void swapButtonCyclesAlternativesForAnyExerciseAndSaysWhatChanged() {
		catalog = Arrays.asList(exercise("one", "One"), exercise("two", "Two"), exercise("three", "Three"),
			exercise("four", "Four"));
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sets", "2");
		values.put("repetitions", "8");
		values.put("loadKg", "0");
		values.put("restSeconds", "60");
		c.preview = new Routine("p", 0, "Dos", profile[0], Arrays.asList(new RoutineItem(catalog.get(0), values, 5, "a"),
			new RoutineItem(catalog.get(1), values, 5, "b")), "x", new RoutineMetadata(RoutineMetadata.Origin.AI, true));
		int messages = c.messages.size();
		c.substitute(1);
		assertEquals("Cambia el segundo, no el primero", "one", c.preview.getItems().get(0).getExercise().getId());
		String first = c.preview.getItems().get(1).getExercise().getId();
		assertTrue(first, first.equals("three") || first.equals("four"));
		assertTrue(c.status, c.status.startsWith("🔄 Cambiado"));
		assertEquals("El aviso no se esconde encima de la tarjeta", messages, c.messages.size());
		c.substitute(1);
		String second = c.preview.getItems().get(1).getExercise().getId();
		assertNotEquals("Cada toque muestra otra alternativa", first, second);
		assertNotEquals("one", second);
	}

	@Test
	public void routineSubstitutionRetainsOriginAndDisabledProgression() {
		catalog = Arrays.asList(exercise("one", "One"), exercise("two", "Two"));
		profile[0] = new TrainingProfile.Builder().build();
		RoutineController c = new RoutineController(store, () -> profile[0], catalog, () -> {}, e -> fail(e));
		c.preview = originFixture();
		c.substitute(0, "two");
		assertEquals("two", c.preview.getItems().get(0).getExercise().getId());
		assertNotNull(c.preview.getMetadata());
		assertEquals(RoutineMetadata.Origin.AI, c.preview.getMetadata().getOrigin());
		assertFalse(c.preview.isProgressionEnabled());
	}

	private Routine originFixture() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sets", "2");
		values.put("repetitions", "8");
		values.put("loadKg", "0");
		values.put("restSeconds", "60");
		return new Routine("origin-fixture", 0, "Origin fixture", profile[0],
			Collections.singletonList(new RoutineItem(catalog.get(0), values, 5, "Fixture")), "Fixture",
			new RoutineMetadata(RoutineMetadata.Origin.AI, false));
	}

	private void assertGeneralQuestionDoesNotAuthorizePlanning(String message) {
		profile[0] = new TrainingProfile.Builder().limitations("Restricción privada").build();
		ProposalController c = controller();
		c.sendExternal(message);
		assertNull(client.request.profile);
		assertFalse(client.request.profileData.containsKey("limitations"));
		assertNull(c.preview);
		assertEquals(0, store.writes);
	}

	@Test public void whyMyRoutineLeavesMeTiredIsAConversation() {
		assertGeneralQuestionDoesNotAuthorizePlanning("Dime por qué mi rutina me deja tan cansado");
	}

	@Test public void askingWhetherRestIsNeededIsAConversation() {
		assertGeneralQuestionDoesNotAuthorizePlanning("¿Necesito descansar entre entrenamientos?");
	}

	@Test
	public void routineDefinitionsAndNegationsAreConversationWithoutPrivatePlanningData() {
		profile[0] = new TrainingProfile.Builder().limitations("Restricción privada")
			.details(Collections.singletonMap("privateDetail", "No enviar")).build();
		ProposalController c = controller();
		for (String message : Arrays.asList("Dime qué es una rutina", "No quiero una rutina",
			"No prepares una rutina", "Quiero entender qué es una rutina",
			"Dime por qué mi rutina me deja tan cansado", "¿Necesito descansar entre entrenamientos?",
			"Quiero entrenar más pero no sé por dónde empezar")) {
			c.sendExternal(message);
			assertNull("No se ha pedido crear un plan: " + message, client.request.profile);
			assertFalse(client.request.profileData.containsKey("limitations"));
			assertFalse(client.request.profileData.containsKey("details"));
			client.callback.success(validReply(client.request));
		}
		assertEquals(0, store.writes);
	}

	@Test
	public void explicitRoutineIntakeAcceptsShortAnswersAndAllowsGeneralQuestionsBetweenThem() {
		ProposalController c = controller();
		c.sendExternal("Prepara una rutina");
		client.callback.success(validReply(client.request));
		c.sendExternal("20 min");
		assertEquals(20, client.request.profileData.get("durationMinutes"));
		assertNull(client.request.profile);
		client.callback.success(validReply(client.request));
		c.sendExternal("¿Por qué conviene descansar?");
		assertNull(client.request.profile);
		assertTrue(client.request.profileData.isEmpty());
		client.callback.success(validReply(client.request));
		c.sendExternal("en casa");
		assertEquals("HOME", client.request.profileData.get("environment"));
		assertEquals(20, client.request.profileData.get("durationMinutes"));
		client.callback.success(validReply(client.request));
		c.sendExternal("sin material");
		client.callback.success(validReply(client.request));
		c.sendExternal("3 días");
		assertEquals(3, client.request.profileData.get("daysPerWeek"));
		client.callback.success(validReply(client.request));
		c.sendExternal("fuerza principiante");
		assertNotNull(client.request.profile);
		assertEquals(20, client.request.profile.getDurationMinutes());
		assertEquals(0, store.writes);
	}

	@Test
	public void decliningRoutineStopsItsPendingIntakeBeforeLaterTimeComments() {
		ProposalController c = controller();
		c.sendExternal("Quiero una rutina");
		client.callback.success(validReply(client.request));
		c.sendExternal("No quiero una rutina");
		assertNull(client.request.profile);
		client.callback.success(validReply(client.request));
		c.sendExternal("20 min");
		assertTrue(client.request.profileData.isEmpty());
		assertNull(client.request.profile);
	}

	@Test
	public void casualThreeTurnConversationDoesNotStartRoutineIntakeFromTimeOrPreferences() {
		ProposalController c = controller();
		c.historyContext(true);
		for (String message : Arrays.asList("Hola", "Hoy tengo 20 min", "Prefiero piernas")) {
			c.sendExternal(message);
			assertNull(client.request.profile);
			assertTrue(client.request.profileData.isEmpty());
			assertNull(c.preview);
			client.callback.success(validReply(client.request));
		}
		assertTrue(client.request.history.stream().anyMatch(m -> m.text.equals("Hoy tengo 20 min")));
		client.callback.success(validReply(client.request));
		c.sendExternal("en casa");
		assertTrue(client.request.profileData.isEmpty());
		assertNull(client.request.profile);
		assertEquals(0, store.writes);
	}

	@Test
	public void laterProfileRevocationAlsoOverridesAnEarlierChatOptIn() {
		profile[0] = new TrainingProfile.Builder().historyConsent(true).build();
		ProposalController c = controller();
		c.historyContext(true);
		c.sendExternal("Hablemos de mi constancia");
		ChatClient.Request request = client.request;
		int count = c.messages.size();
		profile[0] = profile[0].toBuilder().historyConsent(false).build();
		client.callback.success(validReply(request));
		assertEquals(count, c.messages.size());
		assertFalse(c.historyContextEnabled());
		assertFalse(c.busy);
	}

	@Test
	public void conversationOptInIsNotRestoredWithTheLocalConversationSnapshot() {
		ProposalController c = controller();
		c.historyContext(true);
		c.sendExternal("Hola, hablemos de hábitos");
		client.callback.success(validReply(client.request));
		ProposalController recreated = controller();
		assertFalse(recreated.historyContextEnabled());
		recreated.sendExternal("Sigamos");
		assertTrue(client.request.history.isEmpty());
	}

	@Test
	public void greetingLeavesExerciseContextButTechniqueQuestionsKeepTheirGuard() {
		ProposalController c = controller();
		c.explain(exercise("squat", "Técnica histórica distinta"));
		c.sendExternal("Hola, ¿cómo estás?");
		assertNull(c.exercise);
		assertNull(client.request.exerciseId);
		assertNull(client.request.profile);
	}

	@Test
	public void explicitConversationOptInWorksWithoutCreatingAProfileAndResetsOnAccountChange() {
		ProposalController c = controller();
		assertFalse(c.historyContextEnabled());
		c.historyContext(true);
		c.sendExternal("Hola, quiero hablar sobre constancia");
		assertTrue(client.request.historyConsent);
		assertFalse(client.request.history.isEmpty());
		assertNull(profile[0]);
		assertEquals(0, store.writes);
		client.callback.success(validReply(client.request));
		c.activate("uid-b");
		assertFalse(c.historyContextEnabled());
		c.sendExternal("Hola desde otra cuenta");
		assertFalse(client.request.historyConsent);
		assertTrue(client.request.history.isEmpty());
	}

	@Test
	public void chatOptOutOverridesSavedProfileConsentAndDropsPendingContextReply() {
		profile[0] = new TrainingProfile.Builder().historyConsent(true).build();
		ProposalController c = controller();
		assertTrue(c.historyContextEnabled());
		c.sendExternal("Hoy me cuesta empezar");
		ChatClient.Callback pending = client.callback;
		ChatClient.Request request = client.request;
		int count = c.messages.size();
		c.historyContext(false);
		assertFalse(c.historyContextEnabled());
		pending.success(validReply(request));
		assertFalse(c.busy);
		assertEquals(count, c.messages.size());
		c.sendExternal("Sigamos sin compartir mensajes anteriores");
		assertFalse(client.request.historyConsent);
		assertTrue(client.request.history.isEmpty());
		assertTrue(profile[0].isHistoryConsent());
	}

	@Test
	public void localGreetingWithoutProfileDoesNotTurnIntoRoutineIntake() {
		ProposalController c = controller();
		c.sendLocal("Hola, ¿qué tal?");
		assertNull(c.preview);
		assertFalse(c.messages.get(c.messages.size() - 1).text.contains("me falta"));
		assertTrue(c.status.contains("Guiado local"));
		assertEquals(0, store.writes);
	}

	@Test
	public void localGeneralQuestionWithSavedProfileDoesNotCreateAnUnrequestedRoutine() {
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		c.sendLocal("¿Cómo puedo ser más constante con el ejercicio?");
		assertNull(c.preview);
		assertEquals(0, store.writes);
		assertTrue(c.status.contains("sin IA"));
	}

	@Test
	public void genericRemoteConversationUsesOnlyMinimalProfileAndCannotPropose() {
		profile[0] = new TrainingProfile.Builder().limitations("Dato médico privado")
			.preferences("Texto privado").experience("Historial privado").space("Dirección privada")
			.details(Collections.singletonMap("privateDetail", "No enviar")).build();
		ProposalController c = controller();
		c.sendExternal("Hola, hoy me cuesta mantener la constancia");
		assertNotNull(client.request);
		assertNull("Una conversación no autoriza propuestas", client.request.profile);
		assertEquals("strength", client.request.profileData.get("sportId"));
		for (String field : Arrays.asList("limitations", "preferences", "experience", "space", "details"))
			assertFalse("No enviar datos privados del perfil en conversación: " + field,
				client.request.profileData.containsKey(field));
		assertFalse(client.request.historyConsent);
		assertTrue(client.request.history.isEmpty());
		assertEquals(0, store.writes);
	}

	@Test
	public void twoConversationalTurnsKeepOptionalContextAndNeverSaveARoutine() {
		profile[0] = new TrainingProfile.Builder().historyConsent(true).build();
		ProposalController c = controller();
		c.sendExternal("Quiero mejorar mi constancia");
		String firstConversation = client.request.conversationId;
		client.callback.success(validReply(client.request));
		c.sendExternal("¿Y cómo puedo mantener ese hábito?");
		assertEquals(firstConversation, client.request.conversationId);
		assertTrue(client.request.history.stream().anyMatch(m -> m.text.equals("Quiero mejorar mi constancia")));
		assertTrue(client.request.history.stream().anyMatch(m -> m.text.equals("Respuesta válida.")));
		assertTrue(client.request.history.size() <= 12);
		assertNull(client.request.profile);
		client.callback.success(validReply(client.request));
		profile[0] = profile[0].toBuilder().historyConsent(false).build();
		c.sendExternal("Sigamos hablando");
		assertFalse(client.request.historyConsent);
		assertTrue(client.request.history.isEmpty());
		assertEquals(0, store.writes);
	}

	@Test
	public void greetingWithoutProfileCanReceiveTextWithoutMissingRoutineFields() {
		ProposalController c = controller();
		c.sendExternal("Hola, ¿en qué puedes ayudarme?");
		assertTrue(client.request.profileData.isEmpty());
		assertNull(client.request.profile);
		client.callback.success(validReply(client.request));
		assertEquals("Respuesta válida.", c.messages.get(c.messages.size() - 1).text);
		assertNull(c.preview);
		assertEquals(0, store.writes);
	}

	@Test
	public void genericConversationDoesNotTreatAPlanningWordInsideAGeneralQuestionAsRoutineRequest() {
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		c.sendExternal("¿Es buena idea entrenar si estoy muy cansado?");
		assertNull(client.request.profile);
		assertNull(c.preview);
		client.callback.success(validReply(client.request));
		c.sendExternal("Quiero saber si entrenar todos los días es adecuado");
		assertNull(client.request.profile);
	}

	private static Exercise plank(String description) {
		return new Exercise("forearm-plank", "Plancha sobre antebrazos", MetricType.ISOMETRIC,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
			description, Collections.emptyList());
	}

	@Test
	public void plankTechniqueQuestionKeepsCurrentExerciseContext() {
		Exercise currentPlank = plank("Técnica vigente");
		catalog = Collections.singletonList(currentPlank);
		ProposalController c = controller();
		c.explain(currentPlank);

		c.sendExternal("Explica la plancha y dónde colocar las manos");

		assertNotNull(client.request);
		assertEquals("forearm-plank", client.request.exerciseId);
		assertSame(currentPlank, c.exercise);
	}

	@Test
	public void historicalPlankTechniqueQuestionCannotBypassSnapshotGuard() {
		catalog = Collections.singletonList(plank("Técnica vigente"));
		Exercise historical = plank("Técnica histórica distinta");
		ProposalController c = controller();
		c.explain(historical);

		c.sendExternal("Explica la plancha y dónde colocar las manos");

		assertNull(client.request);
		assertSame(historical, c.exercise);
		assertTrue(c.status.contains("versión histórica"));
	}

	@Test
	public void explicitRoutineRequestLeavesHistoricalExerciseContextBeforeContactingAi() {
		ProposalController c = controller();
		c.explain(exercise("squat", "Técnica histórica distinta"));

		c.sendExternal("Prepara una rutina de fuerza, 3 días en casa sin material 30 min principiante");

		assertNotNull(client.request);
		assertNull(client.request.exerciseId);
		assertNull(c.exercise);
		assertNull(memory.load("uid-a").exercise);
		assertEquals(0, store.writes);
	}

	@Test
	public void replacingLoadedExerciseWithBodyweightRemovesLoadWithoutChangingOriginalSnapshot() {
		Exercise bodyweight = exercise("bodyweight-squat", "Sin accesorios");
		Exercise loaded = new Exercise("dumbbell-squat", "Sentadilla con mancuernas", MetricType.STRENGTH,
			EnumSet.of(TrainingProfile.Goal.STRENGTH), EnumSet.of(TrainingProfile.Environment.HOME),
			EnumSet.of(TrainingProfile.Resource.DUMBBELLS), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, "Con mancuernas", Collections.singletonList(bodyweight.getId()));
		catalog = Arrays.asList(loaded, bodyweight);
		profile[0] = new TrainingProfile.Builder().category(TrainingProfile.Category.HOME_EQUIPMENT)
			.resources(EnumSet.of(TrainingProfile.Resource.DUMBBELLS)).build();
		ProposalController c = controller();
		c.sendExternal("Prepara una rutina de fuerza");
		Map<String, Object> reply = StrictJson.object(StrictJson.parse(validReply(client.request)));
		reply.put("proposal", Map.of("title", "Propuesta con carga", "items", Collections.singletonList(
			Map.of("exerciseId", loaded.getId(), "estimatedMinutes", 5, "prescription",
				Arrays.asList(Map.of("key", "sets", "value", "2"), Map.of("key", "repetitions", "value", "8"),
					Map.of("key", "loadKg", "value", "5"), Map.of("key", "restSeconds", "value", "60")),
				"justification", "Compatible con el material declarado."))));
		client.callback.success(StrictJson.write(reply));
		assertNotNull(c.preview);
		Routine original = c.preview;
		Session historical = new Session(original);

		c.substituteFirst();

		RoutineItem replacement = c.preview.getItems().get(0);
		assertEquals("bodyweight-squat", replacement.getExercise().getId());
		assertEquals("0", replacement.getPrescription().get("loadKg"));
		assertEquals("2", replacement.getPrescription().get("sets"));
		assertEquals("8", replacement.getPrescription().get("repetitions"));
		ChatProtocol.validatePrescription(replacement.getExercise(), replacement.getPrescription(),
			replacement.getEstimatedMinutes());
		assertEquals("5", original.getItems().get(0).getPrescription().get("loadKg"));
		assertSame(original, historical.getRoutine());
		assertEquals(0, store.writes);
		c.accept();
		assertEquals(1, store.writes);
		assertEquals("bodyweight-squat", store.saved.getItems().get(0).getExercise().getId());
		assertEquals("0", store.saved.getItems().get(0).getPrescription().get("loadKg"));
	}

	@Test
	public void localEraseClearsPreviewConversationAndRejectsRetainedReplyOnlyForSelectedOwner() {
		ProposalController c = controller();
		propose(c);
		assertNotNull(c.preview);
		ConversationStore.State own = memory.load("uid-a");
		memory.save("uid-b", own);
		c.sendExternal("ayuda");
		ChatClient.Callback late = client.callback;
		assertNotNull(late);
		c.clearLocalData();
		late.success(validReply(client.request));
		assertNull(c.preview);
		assertNull(c.accepted);
		assertTrue(c.messages.isEmpty());
		assertNull(memory.load("uid-a"));
		assertSame(own, memory.load("uid-b"));
	}

	static Exercise exercise(String id, String description) {
		return new Exercise(id, "Sentadilla", MetricType.STRENGTH, EnumSet.of(TrainingProfile.Goal.STRENGTH),
			EnumSet.of(TrainingProfile.Environment.HOME), Collections.emptySet(), Collections.singleton("strength"),
			Exercise.Coverage.SPECIFIC, false, description, Collections.emptyList());
	}

	static class Store implements AppStore {
		int writes;
		Routine saved;

		public void load(Callback<Snapshot> c) {
		}

		public void profile(TrainingProfile p, Callback<Void> c) {
			throw new AssertionError("Perfil no debe escribirse por separado");
		}

		public void accept(Routine r, Callback<Routine> c) {
			writes++;
			saved = r;
			c.success(r.withVersionId(9));
		}

		public void acceptProposal(Routine r, TrainingProfile expected, Callback<Routine> c) {
			accept(r, c);
		}

		public void draft(Session s, Callback<Long> c) {
		}

		public void openDraft(String s, Callback<OpenDraft> c) {
		}

		public void finish(long id, Callback<Void> c) {
		}
	}

	static class Memory implements ConversationStore {
		Map<String, State> states = new HashMap<>();

		public State load(String o) {
			return states.get(o);
		}

		public void save(String o, State s) {
			states.put(o, s);
		}

		public void clear(String o) {
			states.remove(o);
		}
	}

	static class Client implements ChatClient {
		Request request;
		Callback callback;

		public void send(Request r, Callback c) {
			request = r;
			callback = c;
		}

		public void connect(String e, String p, Callback c) {
			c.success("");
		}

		public void disconnect() {
		}

		public boolean connected() {
			return true;
		}
	}

	Store store = new Store();
	Memory memory = new Memory();
	Client client = new Client();
	TrainingProfile[] profile = {null};
	List<Exercise> catalog = Collections.singletonList(exercise("squat", "Técnica actual"));

	ProposalController controller() {
		ProposalController c = new ProposalController(store, memory, client, catalog, () -> profile[0], () -> {
		}, r -> {
		});
		c.activate("uid-a");
		return c;
	}

	void propose(ProposalController c) {
		c.sendLocal("Quiero una rutina para entrenar tres días en casa sin material, 30min");
		c.sendLocal("fuerza principiante");
	}

	@Test
	public void localSpanishProgressionCreatesCompatiblePreview() {
		ProposalController c = controller();
		c.sendLocal("Prepara una rutina de 3 días en casa sin material 30 min");
		assertNull(c.preview);
		assertFalse(c.messages.isEmpty());
		assertTrue(c.messages.get(c.messages.size() - 1).text.contains("objetivo"));
		c.sendLocal("fuerza principiante");
		assertNotNull(c.preview);
		assertEquals(3, c.preview.getProfile().getDaysPerWeek());
		assertNotNull(c.preview.getMetadata());
		assertEquals(RoutineMetadata.Origin.LOCAL_RULES, c.preview.getMetadata().getOrigin());
		assertEquals(30, c.preview.getProfile().getDurationMinutes());
		assertTrue(c.preview.isValidFor(c.preview.getProfile()));
		assertEquals(0, store.writes);
	}

	@Test
	public void goalWordsKeepTheSavedDisciplineAndItsDetailsReachTheAi() {
		profile[0] = new TrainingProfile.Builder().sportId("swimming").environment(TrainingProfile.Environment.POOL)
			.category(TrainingProfile.Category.SPORT).details(Collections.singletonMap("stroke", "FREESTYLE")).build();
		ProposalController c = controller();
		c.sendExternal("Prepara una rutina de fuerza");
		assertEquals("swimming", client.request.profileData.get("sportId"));
		assertEquals(Collections.singletonMap("stroke", "FREESTYLE"), client.request.profileData.get("details"));
	}

	@Test
	public void namingAnotherDisciplineSwitchesItAndDropsTheOldDetails() {
		profile[0] = new TrainingProfile.Builder().sportId("swimming").environment(TrainingProfile.Environment.POOL)
			.category(TrainingProfile.Category.SPORT).details(Collections.singletonMap("stroke", "FREESTYLE")).build();
		ProposalController c = controller();
		c.sendExternal("Prepara una rutina de yoga");
		assertEquals("yoga", client.request.profileData.get("sportId"));
		assertFalse(client.request.profileData.containsKey("details"));
	}

	@Test
	public void rejectConservesRoutineVersionAndSessionSnapshot() {
		ProposalController c = controller();
		propose(c);
		Routine old = new RoutinePlanner().create(new TrainingProfile.Builder().build(), catalog).withVersionId(4);
		Session session = new Session(old);
		c.accepted = old;
		c.reject();
		assertSame(old, c.accepted);
		assertSame(old, session.getRoutine());
		assertNull(c.preview);
		assertEquals(0, store.writes);
		assertNull(profile[0]);
	}

	@Test
	public void changedResourcesCannotAcceptAndCannotMutateProfile() {
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		propose(c);
		profile[0] = profile[0].withResources(EnumSet.of(TrainingProfile.Resource.BANDS));
		c.accept();
		assertEquals(0, store.writes);
		assertEquals(EnumSet.of(TrainingProfile.Resource.BANDS), profile[0].getResources());
		assertFalse(c.status.isEmpty());
	}

	@Test
	public void historyConsentLimitsPayloadAndRevocation() {
		profile[0] = new TrainingProfile.Builder().historyConsent(true).build();
		ProposalController c = controller();
		for (int i = 0; i < 16; i++)
			c.sendLocal("hola " + i);
		c.sendExternal("ayuda");
		assertNotNull(client.request);
		assertEquals(12, client.request.history.size());
		profile[0] = profile[0].toBuilder().historyConsent(false).build();
		client.callback.failure("PROVIDER_NOT_CONFIGURED");
		c.sendExternal("ayuda");
		assertTrue(client.request.history.isEmpty());
		assertFalse(client.request.historyConsent);
	}

	@Test
	public void accountAndContextDiscardLateCallbacks() {
		ProposalController c = controller();
		c.explain(exercise("squat", "Técnica actual"));
		c.sendExternal("explícame");
		assertNotNull(client.callback);
		ChatClient.Callback late = client.callback;
		c.activate("uid-b");
		int size = c.messages.size();
		late.success("{}");
		assertEquals(size, c.messages.size());
		assertNull(c.exercise);
		c.explain(exercise("squat", "Técnica actual"));
		c.sendExternal("explícame");
		late = client.callback;
		c.explain(exercise("squat", "Otra versión"));
		size = c.messages.size();
		late.success("{}");
		assertEquals(size, c.messages.size());
		assertEquals("Otra versión", c.exercise.getDescription());
	}

	@Test
	public void localExplanationUsesSelectedSnapshotAndPersistence() {
		ProposalController c = controller();
		c.explain(exercise("squat", "Técnica de la versión histórica"));
		assertFalse(c.messages.isEmpty());
		assertTrue(c.messages.get(c.messages.size() - 1).text.contains("Técnica de la versión histórica"));
		ProposalController recreated = controller();
		assertEquals(c.messages.size(), recreated.messages.size());
		assertEquals("Técnica de la versión histórica", recreated.exercise.getDescription());
		assertFalse(recreated.busy);
	}

	@Test
	public void invalidExternalReplyIsNotPersisted() {
		ProposalController c = controller();
		c.sendExternal("ayuda");
		int size = c.messages.size();
		assertNotNull(client.callback);
		client.callback.success("{\"text\":\"respuesta sin IDs\"}");
		assertEquals(size, c.messages.size());
		assertNull(c.preview);
		assertFalse(c.status.isEmpty());
		assertEquals(size, memory.load("uid-a").messages.size());
	}

	@Test
	public void acceptRequiresExplicitDecisionAndOnlyOneWrite() {
		ProposalController c = controller();
		propose(c);
		assertEquals(0, store.writes);
		c.accept();
		assertEquals(1, store.writes);
		assertEquals(9, c.accepted.getVersionId());
		assertNull(c.preview);
		c.accept();
		assertEquals(1, store.writes);
	}

	@Test
	public void equipmentNegationCannotAddResource() {
		ProposalController c = controller();
		c.sendLocal("Prepara una rutina de 3 dias en casa 30min fuerza principiante. No tengo mancuernas");
		assertNull(c.preview);
		assertTrue(c.messages.get(c.messages.size() - 1).text.contains("material"));
		c.sendLocal("sin equipo");
		assertNotNull(c.preview);
		assertTrue(c.preview.getProfile().getResources().isEmpty());
	}

	@Test
	public void contradictoryPlaceAndInvalidDaysRequireCorrection() {
		profile[0] = new TrainingProfile.Builder().build();
		ProposalController c = controller();
		c.sendLocal("Prepara una rutina para casa y gimnasio");
		assertNull(c.preview);
		assertTrue(c.status.contains("lugar"));
		c.sendLocal("Prepara una rutina de 8 dias en casa sin material 30min");
		assertNull(c.preview);
		assertTrue(c.status.contains("días"));
		assertEquals(0, store.writes);
	}

	@Test
	public void consentRevokedWhilePendingDropsValidReply() {
		profile[0] = new TrainingProfile.Builder().historyConsent(true).build();
		ProposalController c = controller();
		c.sendExternal("ayuda");
		int size = c.messages.size();
		ChatClient.Request r = client.request;
		profile[0] = profile[0].toBuilder().historyConsent(false).build();
		client.callback.success(validReply(r));
		assertEquals(size, c.messages.size());
		assertFalse(c.busy);
		assertTrue(c.status.contains("revocaste"));
	}

	@Test
	public void validOpenCommandCarriesOriginalSnapshotAndIds() {
		ProposalController c = controller();
		Exercise historical = exercise("squat", "Técnica actual");
		c.explain(historical);
		List<ProposalController.OpenExercise> opened = new ArrayList<>();
		c.openCommands(opened::add);
		c.sendExternal("ayuda");
		ChatClient.Request r = client.request;
		String json = validReply(r).replace("\"commands\":[]",
			"\"commands\":[{\"type\":\"OPEN_EXERCISE\",\"exerciseId\":\"squat\"}]");
		client.callback.success(json);
		assertEquals(1, opened.size());
		assertSame(historical, opened.get(0).snapshot);
		assertEquals(r.requestId, opened.get(0).requestId);
		assertEquals(r.conversationId, opened.get(0).conversationId);
	}

	@Test
	public void historicalSnapshotCannotBeExplainedByDifferentExternalCatalog() {
		ProposalController c = controller();
		c.explain(exercise("squat", "Técnica histórica distinta"));
		c.sendExternal("Explica este movimiento");
		assertNull(client.request);
		assertTrue(c.status.contains("versión histórica"));
		assertEquals("Técnica histórica distinta", c.exercise.getDescription());
	}

	@Test
	public void conflictingEquipmentCannotSilentlyChooseNoEquipment() {
		ProposalController c = controller();
		c.sendLocal("Prepara una rutina de 3 días en casa sin material pero tengo mancuernas, 30min fuerza principiante");
		assertNull(c.preview);
		assertTrue(c.status.contains("material"));
		assertEquals(0, store.writes);
		c.sendExternal("Prepara una rutina de 3 días en casa sin material pero tengo mancuernas, 30min fuerza principiante");
		assertNull(client.request);
	}

	static class CasStore extends Store {
		TrainingProfile persisted = new TrainingProfile.Builder().category(TrainingProfile.Category.HOME_EQUIPMENT)
			.resources(EnumSet.of(TrainingProfile.Resource.DUMBBELLS)).build();
		final List<Routine> versions = new ArrayList<>();
		final Session past;

		CasStore(List<Exercise> catalog) {
			Routine old = new RoutinePlanner().create(persisted, catalog).withVersionId(4);
			versions.add(old);
			past = new Session(old);
		}

		public void acceptProposal(Routine r, TrainingProfile expected, Callback<Routine> c) {
			if (!Objects.equals(persisted, expected)) {
				c.failure(new IllegalArgumentException("CAS distinto"));
				return;
			}
			persisted = r.getProfile();
			versions.add(r.withVersionId(9));
			accept(r, c);
		}

		void removeEquipment() {
			persisted =
				persisted.toBuilder().category(TrainingProfile.Category.NO_EQUIPMENT).resources(Collections.emptySet())
					.build();
		}
	}

	private ProposalController previewA(CasStore s) {
		ProposalController c = new ProposalController(s, memory, client, catalog, () -> s.persisted, () -> {
		}, r -> {
		});
		c.activate("uid-a");
		c.accepted = s.versions.get(0);
		c.sendLocal("Prepara rutina de fuerza");
		assertNotNull(c.preview);
		assertTrue(c.preview.getProfile().getResources().contains(TrainingProfile.Resource.DUMBBELLS));
		return c;
	}

	private void assertAccountBPreserved(CasStore s, ProposalController c) {
		assertTrue("La aceptación no puede restaurar mancuernas retiradas", s.persisted.getResources().isEmpty());
		assertEquals(0, s.writes);
		assertEquals(1, s.versions.size());
		assertSame(s.versions.get(0), c.accepted);
		assertSame(s.versions.get(0), s.past.getRoutine());
	}

	@Test
	public void changedProfileThenFailedExternalCannotRebaseOldPreview() {
		CasStore s = new CasStore(catalog);
		ProposalController c = previewA(s);
		s.removeEquipment();
		c.sendExternal("Ayuda");
		client.callback.failure("PROVIDER_NOT_CONFIGURED");
		c.accept();
		assertAccountBPreserved(s, c);
	}

	@Test
	public void changedProfileThenTextOnlyExternalCannotRebaseOldPreview() {
		CasStore s = new CasStore(catalog);
		ProposalController c = previewA(s);
		s.removeEquipment();
		c.sendExternal("Ayuda");
		client.callback.success(validReply(client.request));
		c.accept();
		assertAccountBPreserved(s, c);
	}

	@Test
	public void localSubstitutionCannotRebaseOldPreview() {
		CasStore s = new CasStore(catalog);
		ProposalController c = previewA(s);
		s.removeEquipment();
		c.sendLocal("Sustituir primer ejercicio");
		c.accept();
		assertAccountBPreserved(s, c);
	}

	@Test
	public void restoredPreviewThenFailedExternalCannotRebaseOldPreview() {
		CasStore s = new CasStore(catalog);
		ProposalController original = previewA(s);
		original.activate("uid-b");
		s.removeEquipment();
		ProposalController restored = new ProposalController(s, memory, client, catalog, () -> s.persisted, () -> {
		}, r -> {
		});
		restored.activate("uid-a");
		restored.accepted = s.versions.get(0);
		assertNotNull(restored.preview);
		restored.sendExternal("Ayuda");
		client.callback.failure("PROVIDER_NOT_CONFIGURED");
		restored.accept();
		assertAccountBPreserved(s, restored);
	}

	static String validReply(ChatClient.Request r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("text", "Respuesta válida.");
		m.put("missingFields", Collections.emptyList());
		m.put("proposal", null);
		m.put("commands", Collections.emptyList());
		m.put("catalogVersion", 1);
		m.put("proposalNotice", "Ejemplo sin revisión profesional.");
		m.put("conversationId", r.conversationId);
		m.put("requestId", r.requestId);
		m.put("exerciseId", r.exerciseId);
		return StrictJson.write(m);
	}

	@Test
	public void externalPartialProfileAccumulatesWithoutSendingHistoryOrSavingProfile() {
		ProposalController c = controller();
		c.sendExternal("Prepara una rutina de tres días en casa sin material 30min");
		assertEquals(3, client.request.profileData.get("daysPerWeek"));
		assertEquals("HOME", client.request.profileData.get("environment"));
		assertEquals("NO_EQUIPMENT", client.request.profileData.get("category"));
		assertFalse(client.request.profileData.containsKey("goal"));
		assertTrue(client.request.history.isEmpty());
		client.callback.failure("PROVIDER_NOT_CONFIGURED");
		c.sendExternal("fuerza principiante");
		assertEquals(13, client.request.profileData.size());
		assertNotNull(client.request.profile);
		assertEquals(3, client.request.profile.getDaysPerWeek());
		assertTrue(client.request.history.isEmpty());
		assertNull(profile[0]);
		assertEquals(0, store.writes);
		Map<String, Object> reply = StrictJson.object(StrictJson.parse(validReply(client.request)));
		reply.put("proposal", Map.of("title", "Rutina IA validada", "items", Collections.singletonList(
			Map.of("exerciseId", "squat", "estimatedMinutes", 5, "prescription",
				Arrays.asList(Map.of("key", "sets", "value", "2"), Map.of("key", "repetitions", "value", "8"),
					Map.of("key", "loadKg", "value", "0"), Map.of("key", "restSeconds", "value", "60")),
				"justification", "Compatible con lo declarado."))));
		client.callback.success(StrictJson.write(reply));
		assertNotNull(c.preview);
		assertEquals(3, c.preview.getProfile().getDaysPerWeek());
		assertNotNull(c.preview.getMetadata());
		assertEquals(RoutineMetadata.Origin.AI, c.preview.getMetadata().getOrigin());
		assertEquals(0, store.writes);
		c.accept();
		assertEquals(1, store.writes);
	}
}


