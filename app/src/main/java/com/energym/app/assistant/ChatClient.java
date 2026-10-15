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

		public Request(String conversation, String request, String exercise, String message, TrainingProfile profile,
			boolean consent, List<ConversationStore.Message> history) {
			this(conversation, request, exercise, message, profile, ChatProtocol.profile(profile), consent, history);
		}

		public Request(String conversation, String request, String exercise, String message, TrainingProfile profile,
			Map<String, Object> partialProfile, boolean consent, List<ConversationStore.Message> history) {
			conversationId = conversation;
			requestId = request;
			exerciseId = exercise;
			this.message = message;
			this.profile = profile;
			profileData = Collections.unmodifiableMap(new LinkedHashMap<>(partialProfile));
			historyConsent = consent;
			this.history = Collections.unmodifiableList(new ArrayList<>(consent ? history : Collections.emptyList()));
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

	default String unavailableCode() {
		return connected() ? "" : "UNAUTHORIZED";
	}

	default void cancelPending() {
	}
}

