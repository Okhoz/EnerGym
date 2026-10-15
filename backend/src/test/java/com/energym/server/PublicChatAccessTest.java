package com.energym.server;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicChatAccessTest {
	@Test void invalidIdentityNeverConsumesQuotaOrReachesProvider() throws Exception {
		ChatService service = new ChatService(token -> null, uid -> { throw new AssertionError("No quota"); },
			request -> { throw new AssertionError("No provider"); }, new ProposalValidator());
		assertEquals(401, service.chat("invalid", ChatServiceTest.request()).status());
	}
	@Test void quotaUsesVerifiedUidAndCannotBeResetByChangingTokens() throws Exception {
		Set<String> used = new HashSet<>();
		ChatService service = new ChatService(token -> token.startsWith("a-") ? "uid-a" : "uid-b", used::add,
			request -> ChatServiceTest.answer(), new ProposalValidator());
		assertEquals(200, service.chat("a-token-1", ChatServiceTest.request()).status());
		assertEquals(429, service.chat("a-token-refreshed", ChatServiceTest.request()).status());
		assertEquals(200, service.chat("b-token", ChatServiceTest.request()).status());
	}
	@Test void quotaAndVerificationFailuresAreUnavailableWithoutProviderCalls() throws Exception {
		Provider never = request -> { throw new AssertionError("No provider"); };
		ChatService authDown = new ChatService(token -> { throw new IOException("offline"); }, uid -> true, never, new ProposalValidator());
		assertEquals(503, authDown.chat("token", ChatServiceTest.request()).status());
		ChatService quotaDown = new ChatService(token -> "uid", uid -> { throw new IOException("offline"); }, never, new ProposalValidator());
		assertEquals(503, quotaDown.chat("token", ChatServiceTest.request()).status());
	}
	@Test void disabledProviderDoesNotConsumeUserBudget() throws Exception {
		ChatService service = new ChatService(token -> "uid", uid -> { throw new AssertionError("No quota"); },
			ProviderFactory.fromEnvironment(Map.of()), new ProposalValidator());
		assertEquals(503, service.chat("token", ChatServiceTest.request()).status());
	}
}
