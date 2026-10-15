package com.energym.app.assistant;

import com.energym.app.domain.*;

import java.util.*;

/**
 * Puerto del conector opcional. Todos los callbacks se entregan en el executor de UI del adaptador.
 */
public interface ChatClient {
	final class Request {
		public final String conversationId, requestId, exerciseId, message;
		public final TrainingProfile profile;
		public final boolean historyConsent;
		public final List<ConversationStore.Message> history;
		public final Map<String, Object> profileData;
		/**
		 * Últimos mensajes solo para un modelo que corre en el propio móvil: ChatProtocol.request no los envía.
		 */
		public final List<ConversationStore.Message> recent;

		public Request(String conversation, String request, String exercise, String message, TrainingProfile profile,
			boolean consent, List<ConversationStore.Message> history) {
			this(conversation, request, exercise, message, profile, ChatProtocol.profile(profile), consent, history);
		}

		public Request(String conversation, String request, String exercise, String message, TrainingProfile profile,
			Map<String, Object> partialProfile, boolean consent, List<ConversationStore.Message> history) {
			this(conversation, request, exercise, message, profile, partialProfile, consent, history,
				Collections.emptyList());
		}

		public Request(String conversation, String request, String exercise, String message, TrainingProfile profile,
			Map<String, Object> partialProfile, boolean consent, List<ConversationStore.Message> history,
			List<ConversationStore.Message> recent) {
			conversationId = conversation;
			requestId = request;
			exerciseId = exercise;
			this.message = message;
			this.profile = profile;
			profileData = Collections.unmodifiableMap(new LinkedHashMap<>(partialProfile));
			historyConsent = consent;
			this.history = Collections.unmodifiableList(new ArrayList<>(consent ? history : Collections.emptyList()));
			this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
		}
	}

	interface Callback {
		void success(String json);

		void failure(String code);
	}

	void send(Request request, Callback callback);

	void connect(String endpoint, String code, Callback callback);

	void disconnect();

	boolean connected();

	default void activate(String owner) {
		disconnect();
	}

	default boolean automaticConnection() {
		return false;
	}

	/**
	 * Verdadero si el modelo corre en el propio móvil: nada sale del dispositivo ni tiene coste de servidor.
	 */
	default boolean onDevice() {
		return false;
	}

	/**
	 * Texto del botón para descargar el modelo del móvil, o vacío si no hay nada que pedir al usuario.
	 */
	default String downloadOffer() {
		return "";
	}

	default void startDownload() {
	}

	/**
	 * Progreso 0–100 de la descarga del modelo, o -1 si no hay ninguna.
	 */
	default int downloadProgress() {
		return -1;
	}

	default String modelLabel() {
		return "";
	}

	default String unavailableCode() {
		return connected() ? "" : "UNAUTHORIZED";
	}

	default void cancelPending() {
	}
}

