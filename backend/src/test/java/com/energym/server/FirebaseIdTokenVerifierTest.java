package com.energym.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirebaseIdTokenVerifierTest {
	static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
	static final KeyPair KEY = key();
	static KeyPair key() {
		try { KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
		catch (Exception e) { throw new IllegalStateException(e); }
	}
	static ObjectNode claims() {
		return Json.object().put("aud", "energym-test").put("iss", "https://securetoken.google.com/energym-test")
			.put("sub", "uid-a").put("user_id", "uid-a").put("iat", NOW.minusSeconds(30).getEpochSecond())
			.put("auth_time", NOW.minusSeconds(60).getEpochSecond()).put("exp", NOW.plusSeconds(300).getEpochSecond());
	}
	static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
	static String token(String header, String body, KeyPair key) throws Exception {
		String input = encode(header.getBytes(StandardCharsets.UTF_8)) + "." + encode(body.getBytes(StandardCharsets.UTF_8));
		Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(key.getPrivate());
		signature.update(input.getBytes(StandardCharsets.US_ASCII));
		return input + "." + encode(signature.sign());
	}
	static String token(ObjectNode body) throws Exception { return token("{\"alg\":\"RS256\",\"kid\":\"test-key\"}", body.toString(), KEY); }
	FirebaseIdTokenVerifier verifier() { return new FirebaseIdTokenVerifier("energym-test", Clock.fixed(NOW, ZoneOffset.UTC), kid -> "test-key".equals(kid) ? KEY.getPublic() : null); }

	@Test void signedCurrentTokenProvidesItsVerifiedUid() throws Exception {
		assertEquals("uid-a", verifier().uid(token(claims())));
	}
	@Test void projectIssuerExpiryAndIdentityAreEnforced() throws Exception {
		for (String field : new String[]{"aud", "iss", "sub", "user_id"}) {
			ObjectNode body = claims().put(field, field.equals("sub") ? "" : "another");
			assertNull(verifier().uid(token(body)), field);
		}
		for (String field : new String[]{"exp", "iat", "auth_time"}) {
			ObjectNode body = claims().put(field, NOW.plusSeconds(field.equals("exp") ? -1 : 120).getEpochSecond());
			assertNull(verifier().uid(token(body)), field);
		}
		ObjectNode missing = claims(); missing.remove("auth_time");
		assertNull(verifier().uid(token(missing)));
	}
	@Test void invalidSignaturesAlgorithmsAndDuplicateClaimsAreRejected() throws Exception {
		assertNull(verifier().uid(token("{\"alg\":\"RS256\",\"kid\":\"test-key\"}", claims().toString(), key())));
		assertNull(verifier().uid(token("{\"alg\":\"none\",\"kid\":\"test-key\"}", claims().toString(), KEY)));
		assertNull(verifier().uid(token("{\"alg\":\"RS256\",\"kid\":\"unknown\"}", claims().toString(), KEY)));
		String duplicate = claims().toString().replace("\"sub\":\"uid-a\"", "\"sub\":\"uid-a\",\"sub\":\"uid-b\"");
		assertNull(verifier().uid(token("{\"alg\":\"RS256\",\"kid\":\"test-key\"}", duplicate, KEY)));
		for (String value : new String[]{"", "a.b.c", "a..c", "x".repeat(9000)}) assertNull(verifier().uid(value));
	}
	@Test void unavailableGoogleKeysFailClosed() throws Exception {
		FirebaseIdTokenVerifier verifier = new FirebaseIdTokenVerifier("energym-test", Clock.fixed(NOW, ZoneOffset.UTC), kid -> { throw new java.io.IOException("offline"); });
		assertThrows(java.io.IOException.class, () -> verifier.uid(token(claims())));
	}
}
