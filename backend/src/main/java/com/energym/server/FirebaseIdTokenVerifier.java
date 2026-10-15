package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Base64;

/** Verifica ID tokens Firebase RS256 contra claves Google; nunca acepta un JWT sólo por decodificarlo. */
public final class FirebaseIdTokenVerifier implements Authenticator {
	@FunctionalInterface interface Keys { PublicKey key(String kid) throws Exception; }
	private final String project;
	private final Clock clock;
	private final Keys keys;

	public FirebaseIdTokenVerifier(String project) {
		this(project, Clock.systemUTC(), new GoogleSigningKeys(Clock.systemUTC())::key);
	}
	FirebaseIdTokenVerifier(String project, Clock clock, Keys keys) {
		if (project == null || !project.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]")) throw new IllegalArgumentException("Proyecto Firebase obligatorio");
		this.project = project; this.clock = clock; this.keys = keys;
	}
	public String uid(String token) throws Exception {
		if (token == null || token.length() > 8192) return null;
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3) return null;
		JsonNode header, body;
		byte[] signature;
		try {
			for (String part : parts) if (!part.matches("[A-Za-z0-9_-]+")) return null;
			header = Json.parse(Base64.getUrlDecoder().decode(parts[0]));
			body = Json.parse(Base64.getUrlDecoder().decode(parts[1]));
			signature = Base64.getUrlDecoder().decode(parts[2]);
			if (!header.isObject() || !body.isObject() || !"RS256".equals(header.path("alg").asText())) return null;
			String kid = Json.text(header.get("kid"), 128, false);
			if (!kid.matches("[A-Za-z0-9_-]+")) return null;
		} catch (Exception invalid) { return null; }
		PublicKey key = keys.key(header.get("kid").textValue());
		if (key == null || !"RSA".equals(key.getAlgorithm())) return null;
		try {
			Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
			verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
			if (!verifier.verify(signature)) return null;
			long now = clock.instant().getEpochSecond();
			if (!project.equals(body.path("aud").asText()) || !body.path("aud").isTextual() ||
				!("https://securetoken.google.com/" + project).equals(body.path("iss").asText())) return null;
			String uid = Json.text(body.get("sub"), 128, false);
			if (body.has("user_id") && (!body.get("user_id").isTextual() || !uid.equals(body.get("user_id").textValue()))) return null;
			long expiry = seconds(body.get("exp")), issued = seconds(body.get("iat")), auth = seconds(body.get("auth_time"));
			if (expiry <= now || issued > now || auth > now || expiry <= issued || auth > issued) return null;
			return uid;
		} catch (GeneralSecurityException | IllegalArgumentException invalid) { return null; }
	}
	private static long seconds(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0)
			throw new IllegalArgumentException("Fecha inválida");
		return node.longValue();
	}
}
