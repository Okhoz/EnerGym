package com.energym.app.assistant;

import com.energym.app.domain.*;
import com.energym.app.domain.TrainingProfile.*;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** Mensajes sin límite de longitud, respuestas cortadas y elección del modelo del móvil. */
public class TrainerLimitsTest {
	private final List<Exercise> catalog = Collections.singletonList(new Exercise("squat", "Sentadilla",
		MetricType.STRENGTH, EnumSet.of(Goal.GENERAL_FITNESS), EnumSet.of(Environment.HOME), Collections.emptySet(),
		Collections.singleton("strength"), Exercise.Coverage.SPECIFIC, false, "Técnica", Collections.emptyList()));
	private final TrainingProfile profile = new TrainingProfile.Builder().goal(Goal.GENERAL_FITNESS)
		.durationMinutes(40).build();

	private ChatClient.Request request(String message, String training) {
		ChatClient.Request r = new ChatClient.Request("c", "r", null, message, profile, ChatProtocol.profile(profile),
			false, Collections.emptyList(), Collections.emptyList());
		r.training = training;
		return r;
	}

	private static String repeat(String s, int times) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < times; i++)
			b.append(s);
		return b.toString();
	}

	@Test
	public void aLongMessageReachesTheModelWhole() {
		String message = repeat("Quiero mejorar mi sentadilla. ", 100);
		String prompt = TrainerPrompt.build(request(message, ""), catalog);
		assertTrue(prompt.contains(message.trim()));
		assertTrue(prompt.length() <= TrainerPrompt.MAX_PROMPT + 100);
	}

	@Test
	public void aHugeMessageKeepsItsStartAndEndAndThePromptFits() {
		String message = "INICIO " + repeat("texto de relleno ", 3000) + " PREGUNTA FINAL";
		String prompt = TrainerPrompt.build(request(message, repeat("Sesión de piernas. ", 400)), catalog);
		assertTrue(prompt.contains("INICIO"));
		assertTrue(prompt.contains("PREGUNTA FINAL"));
		assertTrue(prompt.length() + " caracteres", prompt.length() <= TrainerPrompt.MAX_PROMPT + 100);
	}

	@Test
	public void middleCutRespectsTheLimit() {
		String cut = TrainerPrompt.middleCut(repeat("a", 500) + repeat("b", 500), 100);
		assertTrue(cut.length() <= 100);
		assertTrue(cut.startsWith("a"));
		assertTrue(cut.endsWith("b"));
	}

	@Test
	public void aReplyCutByTheOutputLimitStillShowsItsText() {
		String json = TrainerPrompt.reply("{\"texto\":\"Para la sentadilla, baja con la espalda recta y",
			request("¿Cómo bajo en la sentadilla?", ""), catalog, 1);
		assertTrue(json, json.contains("baja con la espalda recta"));
	}

	@Test
	public void chatterAboutExercisesDoesNotBecomeARoutine() {
		ChatClient.Request r = request("¿Qué tal la sentadilla y la plancha?", "");
		r.conversation = true;
		String json = TrainerPrompt.reply("{\"texto\":\"La Sentadilla y la Sentadilla son buenas.\",\"rutina\":null}",
			r, catalog, 1);
		assertTrue(json, json.contains("\"proposal\":null"));
	}

	@Test
	public void aFailedSystemModelDownloadFallsBackToTheDownloadableOne() {
		FakeModel nano = new FakeModel(OnDeviceModel.DOWNLOADABLE, false), gemma = new FakeModel(OnDeviceModel.DOWNLOADABLE, true);
		ChainedModel model = new ChainedModel(nano, gemma);
		int[] status = {-1};
		model.status(result(v -> status[0] = v));
		assertEquals("nano", model.label());
		model.download(new OnDeviceModel.Result<Void>() {
			public void success(Void value) {
				fail();
			}

			public void failure(String code) {
			}
		});
		assertEquals("gemma", model.label());
		model.status(result(v -> status[0] = v));
		assertEquals("gemma", model.label());
		boolean[] done = {false};
		model.download(new OnDeviceModel.Result<Void>() {
			public void success(Void value) {
				done[0] = true;
			}

			public void failure(String code) {
				fail();
			}
		});
		assertTrue(done[0]);
	}

	private static OnDeviceModel.Result<Integer> result(java.util.function.IntConsumer value) {
		return new OnDeviceModel.Result<Integer>() {
			public void success(Integer v) {
				value.accept(v);
			}

			public void failure(String code) {
				fail(code);
			}
		};
	}

	private static final class FakeModel implements OnDeviceModel {
		private final int status;
		private final boolean downloads;

		FakeModel(int status, boolean downloads) {
			this.status = status;
			this.downloads = downloads;
		}

		public void status(Result<Integer> result) {
			result.success(status);
		}

		public void download(Result<Void> result) {
			if (downloads)
				result.success(null);
			else
				result.failure("MODEL_DOWNLOAD_FAILED");
		}

		public void generate(String prompt, Result<String> result) {
			result.failure("MODEL_FAILED");
		}

		public String label() {
			return downloads ? "gemma" : "nano";
		}
	}
}
