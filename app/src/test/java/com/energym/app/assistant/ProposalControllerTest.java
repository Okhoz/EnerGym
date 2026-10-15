package com.energym.app.assistant;

import com.energym.app.controller.*;
import com.energym.app.domain.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ProposalControllerTest {
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
		c.sendLocal("Quiero entrenar tres días en casa sin material, 30min");
		c.sendLocal("fuerza principiante");
	}

	@Test
	public void localSpanishProgressionCreatesCompatiblePreview() {
		ProposalController c = controller();
		c.sendLocal("3 días en casa sin material 30 min");
		assertNull(c.preview);
		assertFalse(c.messages.isEmpty());
		assertTrue(c.messages.get(c.messages.size() - 1).text.contains("objetivo"));
		c.sendLocal("fuerza principiante");
		assertNotNull(c.preview);
		assertEquals(3, c.preview.getProfile().getDaysPerWeek());
		assertEquals(30, c.preview.getProfile().getDurationMinutes());
		assertTrue(c.preview.isValidFor(c.preview.getProfile()));
		assertEquals(0, store.writes);
	}

	@Test
	public void goalWordsKeepTheSavedDisciplineAndItsDetailsReachTheAi() {
		profile[0] = new TrainingProfile.Builder().sportId("swimming").environment(TrainingProfile.Environment.POOL)
			.category(TrainingProfile.Category.SPORT).details(Collections.singletonMap("stroke", "FREESTYLE")).build();
		ProposalController c = controller();
		c.sendExternal("quiero fuerza");
		assertEquals("swimming", client.request.profileData.get("sportId"));
		assertEquals(Collections.singletonMap("stroke", "FREESTYLE"), client.request.profileData.get("details"));
	}

	@Test
	public void namingAnotherDisciplineSwitchesItAndDropsTheOldDetails() {
		profile[0] = new TrainingProfile.Builder().sportId("swimming").environment(TrainingProfile.Environment.POOL)
			.category(TrainingProfile.Category.SPORT).details(Collections.singletonMap("stroke", "FREESTYLE")).build();
		ProposalController c = controller();
		c.sendExternal("ahora quiero hacer yoga");
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
		c.sendLocal("3 dias en casa 30min fuerza principiante. No tengo mancuernas");
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
		c.sendLocal("quiero casa y gimnasio");
		assertNull(c.preview);
		assertTrue(c.status.contains("lugar"));
		c.sendLocal("8 dias en casa sin material 30min");
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
		c.sendLocal("3 días en casa sin material pero tengo mancuernas, 30min fuerza principiante");
		assertNull(c.preview);
		assertTrue(c.status.contains("material"));
		assertEquals(0, store.writes);
		c.sendExternal("3 días en casa sin material pero tengo mancuernas, 30min fuerza principiante");
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
		c.sendExternal("tres días en casa sin material 30min");
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
		assertEquals(0, store.writes);
		c.accept();
		assertEquals(1, store.writes);
	}
}


