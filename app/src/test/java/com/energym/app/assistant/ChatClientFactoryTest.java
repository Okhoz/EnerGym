package com.energym.app.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ChatClientFactoryTest {
	private static final OnlineChatClient.TokenSource TOKENS = new OnlineChatClient.TokenSource() {
		public String currentUid() {
			return "uid-A";
		}
		public void getToken(String expectedUid, ChatClient.Callback callback) {
			throw new AssertionError("Elegir cliente no debe pedir tokens ni enviar datos");
		}
	};

	@Test public void blankDebugConfigurationKeepsManualPairing() {
		ChatClient client = ChatClientFactory.create(Runnable::run, Runnable::run, true, "  ", TOKENS);
		client.activate("uid-A");
		assertFalse(client.automaticConnection());
		assertFalse(client.connected());
	}

	@Test public void configuredDebugUsesTheSameAutomaticAccountFlowAsRelease() {
		ChatClient client = ChatClientFactory.create(Runnable::run, Runnable::run, true, "https://example.org", TOKENS);
		client.activate("uid-A");
		assertTrue(client.automaticConnection());
		assertTrue(client.connected());
	}

	@Test public void releaseWithoutAnEndpointNeverOffersManualPairing() {
		ChatClient client = ChatClientFactory.create(Runnable::run, Runnable::run, false, "", TOKENS);
		client.activate("uid-A");
		assertTrue(client.automaticConnection());
		assertFalse(client.connected());
		assertEquals("BACKEND_NOT_CONFIGURED", client.unavailableCode());
	}
}
