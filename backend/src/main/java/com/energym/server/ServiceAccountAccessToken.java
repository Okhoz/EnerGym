package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;

/** OAuth de cuenta de servicio con secreto local; ninguna respuesta remota se incluye en excepciones. */
public final class ServiceAccountAccessToken {
	private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");
	private static final String SCOPE = "https://www.googleapis.com/auth/datastore";
	private final PrivateKey privateKey;
	private final String clientEmail;
	private final Transport transport;
	private final Clock clock;
	private String cached;
	private Instant refreshAt = Instant.EPOCH;

	@FunctionalInterface interface Transport {
		Response post(URI uri, String bearer, String contentType, byte[] body) throws Exception;
	}
	record Response(int status, byte[] body) {}

	public ServiceAccountAccessToken(Path file, String expectedProject) throws Exception {
		this(file, expectedProject, ServiceAccountAccessToken::post, Clock.systemUTC());
	}

	ServiceAccountAccessToken(Path file, String expectedProject, Transport transport, Clock clock) throws Exception {
		projectId(expectedProject);
		try (InputStream input = Files.newInputStream(file)) {
			JsonNode secret = Json.parse(readBounded(input));
			if (!secret.isObject() || !"service_account".equals(secret.path("type").asText()) ||
				!expectedProject.equals(secret.path("project_id").asText())) throw new IOException();
			clientEmail = Json.text(secret.get("client_email"), 320, false);
			if (!clientEmail.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.iam\\.gserviceaccount\\.com")) throw new IOException();
			String pem = Json.text(secret.get("private_key"), 16384, false);
			if (!pem.startsWith("-----BEGIN PRIVATE KEY-----") || !pem.stripTrailing().endsWith("-----END PRIVATE KEY-----")) throw new IOException();
			byte[] encoded = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
			privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
			if (!(privateKey instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048) throw new IOException();
		} catch (Exception failure) {
			throw new IOException("Credencial de servicio inválida o proyecto incompatible");
		}
		this.transport = Objects.requireNonNull(transport);
		this.clock = Objects.requireNonNull(clock);
	}

	public synchronized String accessToken() throws Exception {
		Instant now = clock.instant();
		if (cached != null && now.isBefore(refreshAt)) return cached;
		try {
			long issued = now.getEpochSecond();
			String header = encode(Json.MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
			String claims = encode(Json.MAPPER.writeValueAsBytes(Map.of("iss", clientEmail, "scope", SCOPE,
				"aud", TOKEN_ENDPOINT.toString(), "iat", issued, "exp", Math.addExact(issued, 3600))));
			String unsigned = header + "." + claims;
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
			String assertion = unsigned + "." + encode(signature.sign());
			String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8) +
				"&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
			Response response = transport.post(TOKEN_ENDPOINT, null, "application/x-www-form-urlencoded", form.getBytes(StandardCharsets.UTF_8));
			if (response.status() != 200 || response.body() == null || response.body().length > 65536) throw new IOException();
			JsonNode token = Json.parse(response.body());
			String value = Json.text(token.get("access_token"), 8192, false);
			int expires = Json.integer(token.get("expires_in"), 1, 3600);
			if (!"Bearer".equalsIgnoreCase(token.path("token_type").asText()) || !value.matches("[!-~]+")) throw new IOException();
			cached = value;
			refreshAt = now.plusSeconds(Math.max(0, expires - 60));
			return value;
		} catch (Exception failure) {
			throw new IOException("No se pudo obtener autorización del servicio");
		}
	}

	private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

	static String projectId(String project) {
		if (project == null || !project.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]")) throw new IllegalArgumentException("Proyecto Firebase inválido");
		return project;
	}

	/** Transporte de producción: destinos fijados por los dos consumidores, sin redirecciones. */
	static Response post(URI uri, String bearer, String contentType, byte[] body) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		try {
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(10000);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", contentType);
			connection.setRequestProperty("Accept", "application/json");
			if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
			connection.setFixedLengthStreamingMode(body.length);
			try (OutputStream output = connection.getOutputStream()) { output.write(body); }
			int status = connection.getResponseCode();
			InputStream source = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
			if (source == null) return new Response(status, new byte[0]);
			try (InputStream input = source) { return new Response(status, readBounded(input)); }
		} finally {
			connection.disconnect();
		}
	}

	static byte[] readBounded(InputStream input) throws IOException {
		byte[] bytes = input.readNBytes(65537);
		if (bytes.length > 65536) throw new IOException("Respuesta de servicio demasiado grande");
		return bytes;
	}
}
