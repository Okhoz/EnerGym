package com.energym.app.assistant;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class OnDeviceTrainerTest {
	private final List<Exercise> catalog = Arrays.asList(
		exercise("squat", "Sentadilla", MetricType.STRENGTH),
		exercise("plank", "Plancha", MetricType.ISOMETRIC),
		exercise("hips", "Movilidad de cadera", MetricType.MOBILITY),
		exercise("jog", "Trote suave", MetricType.ENDURANCE));
	private final TrainingProfile profile = new TrainingProfile.Builder().goal(Goal.GENERAL_FITNESS)
		.durationMinutes(40).details(Collections.singletonMap("priority", "piernas")).build();

	@Test
	public void promptIsATrainerWithProfileDetailsExercisesAndLocalMemory() {
		ChatClient.Request r = request(profile, "Hazme la rutina de hoy",
			Collections.singletonList(new ConversationStore.Message("user", "Me duelen un poco los gemelos")));
		String prompt = TrainerPrompt.build(r, catalog);
		assertTrue(prompt.contains("entrenador personal"));
		assertTrue(prompt.contains("piernas"));
		assertTrue(prompt.contains("squat | Sentadilla | fuerza"));
		assertTrue(prompt.contains("Me duelen un poco los gemelos"));
		assertTrue(prompt.contains("sobrecarga progresiva"));
		// El historial local nunca forma parte de la petición de red.
		assertFalse(ChatProtocol.request(r).contains("gemelos"));
	}

	@Test
	public void promptStaysWithinTheOnDeviceInputLimit() {
		List<Exercise> big = new ArrayList<>();
		for (int i = 0; i < 300; i++)
			big.add(exercise("exercise-" + i, "Ejercicio con un nombre bastante largo número " + i, MetricType.STRENGTH));
		char[] longText = new char[5000];
		Arrays.fill(longText, 'a');
		List<ConversationStore.Message> recent = new ArrayList<>();
		for (int i = 0; i < 20; i++)
			recent.add(new ConversationStore.Message(i % 2 == 0 ? "user" : "assistant", new String(longText)));
		String prompt = TrainerPrompt.build(request(profile, new String(longText), recent), big);
		// Unos 4 caracteres por token: debe quedar claramente por debajo de las 4000 de Gemini Nano.
		assertTrue(prompt.length() + " caracteres", prompt.length() < 12000);
	}

	@Test
	public void modelRoutineBecomesAValidProposalWithRoomToProgress() {
		ChatClient.Request r = request(profile, "rutina", Collections.emptyList());
		String output = "```json\n{\"texto\":\"¡Vamos a por ello!\",\"rutina\":{\"titulo\":\"Piernas y core\"," +
			"\"ejercicios\":[{\"id\":\"jog\",\"motivo\":\"Cardio suave\"},{\"id\":\"squat\",\"series\":9," +
			"\"repeticiones\":40,\"motivo\":\"Prioridad piernas\"},{\"id\":\"inventado\"},{\"id\":\"plank\"}]}}\n```";
		ChatProtocol.Reply reply = ChatProtocol.reply(TrainerPrompt.reply(output, r, catalog, 2), r, catalog, 2);
		assertEquals("¡Vamos a por ello!", reply.text);
		Routine routine = reply.proposal;
		assertNotNull(routine);
		assertEquals("Piernas y core", routine.getTitle());
		List<String> ids = new ArrayList<>();
		for (RoutineItem item : routine.getItems())
			ids.add(item.getExercise().getId());
		assertEquals("El ID inventado se descarta y la resistencia va al final", Arrays.asList("squat", "plank", "jog"),
			ids);
		Map<String, String> squat = routine.getItems().get(0).getPrescription();
		assertEquals("Las series del modelo se acotan", "3", squat.get("sets"));
		assertEquals("15", squat.get("repetitions"));
		assertEquals("Prioridad piernas", routine.getItems().get(0).getJustification());
		assertTrue("Deja margen para progresar", routine.getEstimatedMinutes() < profile.getDurationMinutes());
		assertTrue(routine.isValidFor(profile));
	}

	@Test
	public void plainTextAnswerAndIncompleteProfileNeverProduceARoutine() {
		ChatClient.Request partial = new ChatClient.Request("c", "r", null, "hola", null,
			Collections.singletonMap("goal", "STRENGTH"), false, Collections.emptyList(), Collections.emptyList());
		ChatProtocol.Reply reply = ChatProtocol.reply(TrainerPrompt.reply("¡Hola! ¿Qué disciplina practicas?",
			partial, catalog, 2), partial, catalog, 2);
		assertEquals("¡Hola! ¿Qué disciplina practicas?", reply.text);
		assertNull(reply.proposal);
		assertTrue(TrainerPrompt.build(partial, catalog).contains("Perfil incompleto"));
	}

	@Test
	public void clientUsesTheModelOnlyWhenAvailableAndDownloadsItOtherwise() {
		FakeModel model = new FakeModel(OnDeviceModel.DOWNLOADABLE);
		OnDeviceChatClient client = new OnDeviceChatClient(model, catalog, 2, Runnable::run, Runnable::run);
		int[] changes = {0};
		client.listen(() -> changes[0]++);
		assertFalse(client.connected());
		assertTrue(client.onDevice());
		client.activate("uid");
		assertEquals(1, model.downloads);
		assertEquals("MODEL_DOWNLOADING", client.unavailableCode());
		model.download.success(null);
		assertTrue(client.connected());
		assertTrue(changes[0] > 0);
		client.activate("uid");
		assertEquals("No vuelve a descargar ni a comprobar", 1, model.downloads);
		String[] result = new String[1];
		client.send(request(profile, "hola", Collections.emptyList()), new ChatClient.Callback() {
			public void success(String json) {
				result[0] = json;
			}

			public void failure(String code) {
				result[0] = "ERROR " + code;
			}
		});
		assertTrue(model.prompt.contains("MENSAJE DEL CLIENTE: hola"));
		assertTrue(result[0], result[0].contains("\"text\":\"¡Hola!\""));
	}

	@Test
	public void unavailableModelFailsFastSoTheChatFallsBackToLocalGuidance() {
		OnDeviceChatClient client = new OnDeviceChatClient(new FakeModel(OnDeviceModel.UNAVAILABLE), catalog, 2,
			Runnable::run, Runnable::run);
		client.activate("uid");
		assertFalse(client.connected());
		assertEquals("MODEL_UNAVAILABLE", client.unavailableCode());
	}

	@Test
	public void bigDownloadWaitsForTheUserAndThenRuns() {
		FakeModel gemma = new FakeModel(OnDeviceModel.DOWNLOADABLE);
		gemma.consent = true;
		OnDeviceChatClient client = new OnDeviceChatClient(gemma, catalog, 2, Runnable::run, Runnable::run);
		client.activate("uid");
		assertEquals("Sin pedirlo no se descargan 2,6 GB", 0, gemma.downloads);
		assertEquals("MODEL_NEEDS_DOWNLOAD", client.unavailableCode());
		assertTrue(client.downloadOffer(), client.downloadOffer().contains("2,6 GB"));
		client.startDownload();
		client.startDownload();
		assertEquals("Un segundo toque no duplica la descarga", 1, gemma.downloads);
		assertEquals("", client.downloadOffer());
		assertEquals("MODEL_DOWNLOADING", client.unavailableCode());
		gemma.download.failure("MODEL_DOWNLOAD_FAILED");
		assertEquals("Tras un fallo se puede reintentar", "MODEL_NEEDS_DOWNLOAD", client.unavailableCode());
		client.startDownload();
		gemma.download.success(null);
		assertTrue(client.connected());
	}

	@Test
	public void chainedModelPrefersGeminiNanoAndFallsBackToGemma() {
		FakeModel nano = new FakeModel(OnDeviceModel.AVAILABLE), gemma = new FakeModel(OnDeviceModel.DOWNLOADABLE);
		nano.name = "Gemini Nano";
		gemma.name = "Gemma";
		gemma.consent = true;
		ChainedModel withNano = new ChainedModel(nano, gemma);
		int[] status = {-1};
		withNano.status(new OnDeviceModel.Result<Integer>() {
			public void success(Integer value) {
				status[0] = value;
			}

			public void failure(String code) {
			}
		});
		assertEquals(OnDeviceModel.AVAILABLE, status[0]);
		assertEquals("Gemini Nano", withNano.label());
		assertFalse(withNano.downloadNeedsConsent());
		ChainedModel withoutNano = new ChainedModel(new FakeModel(OnDeviceModel.UNAVAILABLE), gemma);
		withoutNano.status(new OnDeviceModel.Result<Integer>() {
			public void success(Integer value) {
				status[0] = value;
			}

			public void failure(String code) {
			}
		});
		assertEquals(OnDeviceModel.DOWNLOADABLE, status[0]);
		assertEquals("Gemma", withoutNano.label());
		assertTrue(withoutNano.downloadNeedsConsent());
	}

	private ChatClient.Request request(TrainingProfile p, String message, List<ConversationStore.Message> recent) {
		return new ChatClient.Request("c", "r", null, message, p, ChatProtocol.profile(p), false,
			Collections.emptyList(), recent);
	}

	private static Exercise exercise(String id, String name, MetricType type) {
		return new Exercise(id, name, type, EnumSet.of(Goal.GENERAL_FITNESS), EnumSet.of(Environment.HOME),
			Collections.emptySet(), Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false,
			"Técnica de " + name, Collections.emptyList());
	}

	private static final class FakeModel implements OnDeviceModel {
		final int status;
		boolean consent;
		String name = "IA";
		int downloads;
		Result<Void> download;
		String prompt;

		FakeModel(int status) {
			this.status = status;
		}

		public void status(Result<Integer> result) {
			result.success(status);
		}

		public void download(Result<Void> result) {
			downloads++;
			download = result;
		}

		public boolean downloadNeedsConsent() {
			return consent;
		}

		public long downloadBytes() {
			return consent ? 2588147712L : 0;
		}

		public String label() {
			return name;
		}

		public void generate(String prompt, Result<String> result) {
			this.prompt = prompt;
			result.success("{\"texto\":\"¡Hola!\",\"rutina\":null}");
		}
	}
}
