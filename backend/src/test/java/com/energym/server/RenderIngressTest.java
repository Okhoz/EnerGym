package com.energym.server;

import com.sun.net.httpserver.Headers;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderIngressTest {
	Map<String,String> env() { return Map.of("ENERGYM_TRANSPORT", "render", "ENERGYM_AUTH_MODE", "firebase", "RENDER", "true", "RENDER_EXTERNAL_HOSTNAME", "energym-test.onrender.com", "PORT", "0"); }
	@Test void publicHttpModeRequiresTheExplicitManagedRenderBoundary() {
		for (String missing : new String[]{"RENDER", "RENDER_EXTERNAL_HOSTNAME", "ENERGYM_AUTH_MODE"}) {
			Map<String,String> env = new HashMap<>(env()); env.remove(missing);
			assertThrows(IllegalArgumentException.class, () -> RenderIngress.fromEnvironment(env));
		}
		assertThrows(IllegalArgumentException.class, () -> RenderIngress.fromEnvironment(Map.of("RENDER", "true", "RENDER_EXTERNAL_HOSTNAME", "localhost", "ENERGYM_AUTH_MODE", "firebase")));
	}
	@Test void acceptsOnlyTheExpectedHostAndSingleHttpsProxyHeader() {
		RenderIngress ingress = RenderIngress.fromEnvironment(env());
		Headers headers = new Headers(); headers.set("Host", "energym-test.onrender.com"); headers.set("X-Forwarded-Proto", "https");
		assertTrue(ingress.accepts(headers));
		headers.add("X-Forwarded-Proto", "https"); assertFalse(ingress.accepts(headers));
		headers.set("X-Forwarded-Proto", "http"); assertFalse(ingress.accepts(headers));
		headers.set("X-Forwarded-Proto", "https"); headers.set("Host", "attacker.example"); assertFalse(ingress.accepts(headers));
	}
	@Test void publicModeHasNoPairingAndAcceptsJwtLengthCredentials() throws Exception {
		String jwt = "x".repeat(900);
		ChatService service = new ChatService(token -> jwt.equals(token) ? "uid" : null, uid -> true,
			request -> ChatServiceTest.answer(), new ProposalValidator());
		try (Server server = Server.configured(env(), service, null)) {
			server.start(); HttpClient client = HttpClient.newHttpClient();
			URI endpoint = URI.create("http://127.0.0.1:" + server.port());
			HttpResponse<String> health = client.send(HttpRequest.newBuilder(endpoint.resolve("/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, health.statusCode());
			HttpRequest noProxy = HttpRequest.newBuilder(endpoint.resolve("/api/chat")).header("Content-Type", "application/json").header("Authorization", "Bearer " + jwt)
				.POST(HttpRequest.BodyPublishers.ofString(ChatServiceTest.request().toString())).build();
			assertEquals(403, client.send(noProxy, HttpResponse.BodyHandlers.ofString()).statusCode());
			assertEquals(200, rawPost(server.port(), "/api/chat", jwt));
			assertEquals(401, rawPost(server.port(), "/api/chat", "wrong"));
			assertEquals(404, rawPost(server.port(), "/api/session", jwt));
		}
	}
	private int rawPost(int port, String path, String token) throws Exception {
		try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
			socket.setSoTimeout(5000);
			byte[] body = ChatServiceTest.request().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
			String headers = "POST " + path + " HTTP/1.1\r\nHost: energym-test.onrender.com\r\nX-Forwarded-Proto: https\r\n" +
				"Authorization: Bearer " + token + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
			socket.getOutputStream().write(headers.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			socket.getOutputStream().write(body); socket.getOutputStream().flush();
			String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII)).readLine();
			return Integer.parseInt(status.split(" ")[1]);
		}
	}
}
