package com.energym.app.assistant;

import java.util.concurrent.Executor;

/** Selección única: release siempre online; debug vacío conserva el emparejamiento local. */
public final class ChatClientFactory {
	private ChatClientFactory() {
	}

	public static ChatClient create(Executor worker, Executor delivery, boolean debug, String backend,
		OnlineChatClient.TokenSource tokens) {
		if (debug && (backend == null || backend.trim().isEmpty()))
			return new HttpChatClient(worker, delivery, true);
		return new OnlineChatClient(worker, delivery, false, backend, tokens);
	}
}
