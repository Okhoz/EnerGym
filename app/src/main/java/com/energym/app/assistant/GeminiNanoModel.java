package com.energym.app.assistant;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * ADAPTADOR ML Kit Prompt API: Gemini Nano del sistema (AICore). Todo se ejecuta en el dispositivo; no hay
 * claves, cuotas de servidor ni datos que salgan del móvil. En móviles sin AICore informa UNAVAILABLE.
 */
public final class GeminiNanoModel implements OnDeviceModel {
	/**
	 * Respuesta acotada: el texto y la rutina caben de sobra y la latencia del modelo local se mantiene baja.
	 */
	private static final int MAX_OUTPUT_TOKENS = 700;
	private final Executor worker;
	private GenerativeModelFutures model;

	public GeminiNanoModel(Executor worker) {
		this.worker = worker;
	}

	private synchronized GenerativeModelFutures model() {
		if (model == null)
			model = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
		return model;
	}

	public void status(Result<Integer> result) {
		try {
			listen(model().checkStatus(), value -> {
				int status = value == null ? FeatureStatus.UNAVAILABLE : value;
				if (status == FeatureStatus.AVAILABLE)
					return AVAILABLE;
				if (status == FeatureStatus.DOWNLOADABLE)
					return DOWNLOADABLE;
				if (status == FeatureStatus.DOWNLOADING)
					return DOWNLOADING;
				return UNAVAILABLE;
			}, result);
		} catch (RuntimeException | LinkageError e) {
			// Sin Google Play services / AICore compatibles la propia creación del cliente puede fallar.
			result.success(UNAVAILABLE);
		}
	}

	public void download(Result<Void> result) {
		try {
			listen(model().download(new DownloadCallback() {
				@Override
				public void onDownloadFailed(GenAiException e) {
					result.failure("MODEL_DOWNLOAD_FAILED");
				}
			}), value -> null, result);
		} catch (RuntimeException | LinkageError e) {
			result.failure("MODEL_UNAVAILABLE");
		}
	}

	public void generate(String prompt, Result<String> result) {
		try {
			GenerateContentRequest.Builder builder = new GenerateContentRequest.Builder(new TextPart(prompt));
			builder.setTemperature(0.4f);
			builder.setTopK(16);
			builder.setMaxOutputTokens(MAX_OUTPUT_TOKENS);
			listen(model().generateContent(builder.build()), GeminiNanoModel::text, result);
		} catch (RuntimeException | LinkageError e) {
			result.failure("MODEL_UNAVAILABLE");
		}
	}

	private static String text(GenerateContentResponse response) {
		if (response == null || response.getCandidates().isEmpty() || response.getCandidates().get(0).getText() == null)
			throw new IllegalStateException("Sin respuesta");
		return response.getCandidates().get(0).getText();
	}

	private interface Mapper<A, B> {
		B map(A value);
	}

	private <A, B> void listen(ListenableFuture<A> future, Mapper<A, B> mapper, Result<B> result) {
		future.addListener(() -> {
			B value;
			try {
				value = mapper.map(future.get());
			} catch (ExecutionException e) {
				result.failure(e.getCause() instanceof GenAiException ? "MODEL_BUSY_OR_QUOTA" : "MODEL_FAILED");
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				result.failure("MODEL_FAILED");
				return;
			} catch (RuntimeException e) {
				result.failure("MODEL_FAILED");
				return;
			}
			result.success(value);
		}, worker);
	}
}
