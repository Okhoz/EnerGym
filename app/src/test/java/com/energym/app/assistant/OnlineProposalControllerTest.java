package com.energym.app.assistant;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** El modo automático informa de disponibilidad; el guiado local requiere una elección explícita. */
public final class OnlineProposalControllerTest {
	@Test public void missingOnlineConfigurationDoesNotSilentlyProduceALocalReply() {
		AutomaticClient client = new AutomaticClient(false);
		ProposalController controller = controller(client);
		int before = controller.messages.size();
		controller.send("hola");
		assertEquals("No se debe simular una respuesta online con reglas locales", before, controller.messages.size());
		assertTrue(controller.status.contains("online"));
		assertFalse(controller.busy);
	}

	@Test public void unavailableOnDeviceAiAnswersWithTheLocalGuideInsteadOfDroppingTheMessage() {
		AutomaticClient client = new AutomaticClient(false) {
			public boolean onDevice() {
				return true;
			}

			public String unavailableCode() {
				return "MODEL_UNAVAILABLE";
			}
		};
		ProposalController controller = new ProposalController(new ProposalControllerTest.Store(),
			new ProposalControllerTest.Memory(), client, Collections.singletonList(new com.energym.app.domain.Exercise(
				"squat", "Sentadilla sin accesorios", com.energym.app.domain.MetricType.STRENGTH,
				EnumSet.of(com.energym.app.domain.TrainingProfile.Goal.STRENGTH),
				EnumSet.of(com.energym.app.domain.TrainingProfile.Environment.HOME), Collections.emptySet(),
				Collections.singleton("strength"), com.energym.app.domain.Exercise.Coverage.SPECIFIC, false,
				"Baja la cadera con la espalda neutra.", Collections.emptyList())), () -> null, () -> {}, routine -> {});
		controller.activate("uid-A");
		int before = controller.messages.size();
		controller.send("¿cómo hago bien una sentadilla?");
		assertNull("Nada se envía al modelo no disponible", client.callback);
		assertTrue("Se guarda la pregunta y la respuesta local", controller.messages.size() >= before + 2);
		assertEquals("assistant", controller.messages.get(controller.messages.size() - 1).role);
		assertFalse(controller.busy);
		assertTrue("Explica la técnica del ejercicio nombrado", controller.messages.get(controller.messages.size() - 1)
			.text.contains("Técnica de"));
	}

	@Test public void ambiguousExerciseNameListsTheVariantsAndAFullNameExplainsIt() {
		AutomaticClient client = new AutomaticClient(false) {
			public boolean onDevice() {
				return true;
			}
		};
		ProposalController controller = new ProposalController(new ProposalControllerTest.Store(),
			new ProposalControllerTest.Memory(), client, Arrays.asList(plank("plank", "Plancha sobre antebrazos"),
			plank("side-plank", "Plancha lateral")), () -> null, () -> {}, routine -> {});
		controller.activate("uid-A");
		controller.send("¿cómo hago la plancha?");
		String reply = controller.messages.get(controller.messages.size() - 1).text;
		assertTrue(reply.contains("2 variantes") && reply.contains("Plancha lateral") && reply.contains("Plancha sobre antebrazos"));
		controller.send("¿y la plancha lateral?");
		assertTrue(controller.messages.get(controller.messages.size() - 1).text.startsWith("Técnica de Plancha lateral"));
	}

	private static com.energym.app.domain.Exercise plank(String id, String name) {
		return new com.energym.app.domain.Exercise(id, name, com.energym.app.domain.MetricType.ISOMETRIC,
			EnumSet.of(com.energym.app.domain.TrainingProfile.Goal.STRENGTH),
			EnumSet.of(com.energym.app.domain.TrainingProfile.Environment.HOME), Collections.emptySet(),
			Collections.singleton("strength"), com.energym.app.domain.Exercise.Coverage.SPECIFIC, false,
			"Cuerpo en línea recta.", Collections.emptyList());
	}

	@Test public void activatingTheAccountEnablesOnlineWithoutPairing() {
		OnlineChatClient.TokenSource tokens = new OnlineChatClient.TokenSource() {
			public String currentUid() {
				return "uid-A";
			}

			public void getToken(String uid, ChatClient.Callback callback) {
			}
		};
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, false,
			"https://example.org", tokens);
		ProposalController controller = controller(client);
		controller.send("hola");
		assertTrue("El primer envío inicia la obtención del token de la cuenta", controller.busy);
		assertEquals("user", controller.messages.get(controller.messages.size() - 1).role);
	}

	@Test public void disabledProviderDoesNotTurnTheFollowingSendIntoLocalGuidance() {
		AutomaticClient client = new AutomaticClient(true);
		ProposalController controller = controller(client);
		controller.send("hola");
		assertTrue(controller.busy);
		client.callback.failure("PROVIDER_NOT_CONFIGURED");
		int before = controller.messages.size();
		controller.send("quiero conversar");
		assertEquals(before, controller.messages.size());
		assertFalse(controller.busy);
		assertFalse(controller.remoteActive());
	}

	@Test public void localGuidanceRequiresAnExplicitChoiceAndIsResetForTheNextAccount() {
		ProposalController controller = controller(new AutomaticClient(false));
		controller.selectLocalGuidance(true);
		controller.send("hola");
		assertTrue(controller.localGuidance());
		assertEquals("assistant", controller.messages.get(controller.messages.size() - 1).role);
		assertTrue(controller.status.contains("sin IA"));
		controller.activate("uid-B");
		assertFalse(controller.localGuidance());
		int before = controller.messages.size();
		controller.send("hola");
		assertEquals(before, controller.messages.size());
	}

	@Test public void cancellationStopsWaitingAndDiscardsTheLateAnswer() {
		AutomaticClient client = new AutomaticClient(true);
		ProposalController controller = controller(client);
		controller.send("hola");
		assertTrue(controller.busy);
		int before = controller.messages.size();
		controller.cancelPending();
		client.callback.success("{}");
		assertFalse(controller.busy);
		assertEquals(before, controller.messages.size());
		assertTrue(controller.status.contains("cancelada"));
	}

	@Test public void rejectedFirebaseSessionExplainsThatTheUserMustSignInAgain() {
		AutomaticClient client = new AutomaticClient(true);
		ProposalController controller = controller(client);
		controller.send("hola");
		client.callback.failure("UNAUTHORIZED");
		assertFalse(controller.busy);
		assertTrue(controller.status.contains("iniciar sesión"));
		assertTrue(controller.status.contains("Google"));
	}

	@Test public void revokingHistoryBeforeTheTokenArrivesPreventsSendingThatHistory() {
		List<Runnable> pendingHttp = new ArrayList<>();
		ChatClient.Callback[] tokenResult = new ChatClient.Callback[1];
		OnlineChatClient.TokenSource tokens = new OnlineChatClient.TokenSource() {
			public String currentUid() {
				return "uid-A";
			}

			public void getToken(String uid, ChatClient.Callback callback) {
				tokenResult[0] = callback;
			}
		};
		OnlineChatClient client = new OnlineChatClient(pendingHttp::add, Runnable::run, false,
			"https://example.org", tokens);
		ProposalController controller = controller(client);
		controller.historyContext(true);
		controller.send("hola");
		assertTrue(controller.busy);
		controller.historyContext(false);
		tokenResult[0].success("firebase.header.signature");
		assertFalse(controller.busy);
		assertTrue("El historial revocado no llega a la cola HTTP", pendingHttp.isEmpty());
	}

	private static ProposalController controller(ChatClient client) {
		ProposalController controller = new ProposalController(new ProposalControllerTest.Store(),
			new ProposalControllerTest.Memory(), client, Collections.emptyList(), () -> null, () -> {}, routine -> {});
		controller.activate("uid-A");
		return controller;
	}

	private static class AutomaticClient implements ChatClient {
		final boolean configured;
		Callback callback;
		AutomaticClient(boolean configured) {
			this.configured = configured;
		}

		public void send(Request request, Callback callback) {
			this.callback = callback;
		}

		public void connect(String endpoint, String code, Callback callback) {
			throw new AssertionError("Sin pairing");
		}

		public void disconnect() {
		}

		public boolean connected() {
			return configured;
		}

		public boolean automaticConnection() {
			return true;
		}

		public String unavailableCode() {
			return configured ? "" : "BACKEND_NOT_CONFIGURED";
		}
	}
}
