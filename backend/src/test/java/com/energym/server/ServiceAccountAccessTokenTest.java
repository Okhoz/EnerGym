package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class ServiceAccountAccessTokenTest {
	@TempDir Path directory;
	static KeyPair keys;
	@BeforeAll static void keyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		keys = generator.generateKeyPair();
	}

	@Test void signsDatastoreAssertionForFixedGoogleEndpointAndCachesUntilRefresh() throws Exception {
		MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));
		int[] calls = {0};
		ServiceAccountAccessToken tokens = new ServiceAccountAccessToken(secret("test-project"), "test-project",
			(uri, bearer, type, body) -> {
				assertEquals(URI.create("https://oauth2.googleapis.com/token"), uri);
				assertNull(bearer);
				assertEquals("application/x-www-form-urlencoded", type);
				String form = new String(body, StandardCharsets.UTF_8);
				Map<String, String> values = new HashMap<>();
				for (String pair : form.split("&")) {
					String[] parts = pair.split("=", 2);
					values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
				}
				assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", values.get("grant_type"));
				String[] jwt = values.get("assertion").split("\\.");
				JsonNode header = Json.parse(Base64.getUrlDecoder().decode(jwt[0]));
				JsonNode claims = Json.parse(Base64.getUrlDecoder().decode(jwt[1]));
				assertEquals("RS256", header.path("alg").asText());
				assertEquals("worker@test-project.iam.gserviceaccount.com", claims.path("iss").asText());
				assertEquals("https://www.googleapis.com/auth/datastore", claims.path("scope").asText());
				assertEquals("https://oauth2.googleapis.com/token", claims.path("aud").asText());
				assertEquals(clock.instant().getEpochSecond(), claims.path("iat").asLong());
				assertEquals(3600, claims.path("exp").asLong() - claims.path("iat").asLong());
				Signature signature = Signature.getInstance("SHA256withRSA");
				signature.initVerify(keys.getPublic());
				signature.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
				assertTrue(signature.verify(Base64.getUrlDecoder().decode(jwt[2])));
				calls[0]++;
				return response(200, "{\"access_token\":\"access-" + calls[0] + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
			}, clock);
		assertEquals("access-1", tokens.accessToken());
		clock.advance(Duration.ofMinutes(58));
		assertEquals("access-1", tokens.accessToken());
		clock.advance(Duration.ofMinutes(1));
		assertEquals("access-2", tokens.accessToken());
	}

	@Test void projectMismatchFailsBeforeMakingAnOAuthRequest() throws Exception {
		Exception failure = assertThrows(Exception.class, () -> new ServiceAccountAccessToken(secret("other-project"),
			"test-project", (uri, bearer, type, body) -> { fail("No network for mismatched project"); return null; }, Clock.systemUTC()));
		assertFalse(failure.getMessage().contains("PRIVATE KEY"));
	}

	@Test void providerErrorsAndMalformedTokenResponsesFailWithoutExposingSecrets() throws Exception {
		for (String body : List.of("{\"access_token\":\"sensitive-token\",\"expires_in\":0,\"token_type\":\"Bearer\"}",
			"{\"access_token\":\"sensitive-token\",\"expires_in\":3600,\"token_type\":\"Basic\"}",
			"{\"access_token\":\"line\\nbreak\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
			"{\"access_token\":\"first\",\"access_token\":\"second\",\"expires_in\":3600,\"token_type\":\"Bearer\"}", "{}")) {
			ServiceAccountAccessToken tokens = new ServiceAccountAccessToken(secret("test-project"), "test-project",
				(uri, bearer, type, bytes) -> response(200, body), Clock.systemUTC());
			Exception failure = assertThrows(Exception.class, tokens::accessToken);
			assertFalse(failure.getMessage().contains("sensitive-token"));
		}
		ServiceAccountAccessToken tokens = new ServiceAccountAccessToken(secret("test-project"), "test-project",
			(uri, bearer, type, body) -> response(302, "sensitive-token"), Clock.systemUTC());
		assertThrows(Exception.class, tokens::accessToken);
	}

	@Test void refusesOversizedSecretsAndResponses() throws Exception {
		Path large = directory.resolve("large.json");
		Files.write(large, new byte[65537]);
		assertThrows(Exception.class, () -> new ServiceAccountAccessToken(large, "test-project"));
		assertThrows(IOException.class, () -> ServiceAccountAccessToken.readBounded(new ByteArrayInputStream(new byte[65537])));
		assertEquals(65536, ServiceAccountAccessToken.readBounded(new ByteArrayInputStream(new byte[65536])).length);
	}

	private Path secret(String project) throws Exception {
		String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
		Path path = directory.resolve(UUID.randomUUID() + ".json");
		Files.write(path, Json.MAPPER.writeValueAsBytes(Map.of("type", "service_account", "project_id", project,
			"client_email", "worker@test-project.iam.gserviceaccount.com", "private_key", pem,
			"token_uri", "http://attacker.invalid/token")));
		return path;
	}
	static ServiceAccountAccessToken.Response response(int status, String body) {
		return new ServiceAccountAccessToken.Response(status, body.getBytes(StandardCharsets.UTF_8));
	}
	static final class MutableClock extends Clock {
		private Instant now;
		MutableClock(Instant now) { this.now = now; }
		void advance(Duration delta) { now = now.plus(delta); }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
