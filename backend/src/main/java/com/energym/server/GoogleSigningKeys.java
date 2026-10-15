package com.energym.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Cache de certificados públicos de Firebase con origen fijo y refresco acotado para kids desconocidos. */
final class GoogleSigningKeys {
	record KeySet(Map<String, PublicKey> keys, Duration lifetime) {}
	@FunctionalInterface interface Loader { KeySet load() throws Exception; }
	private final Clock clock;
	private final Loader loader;
	private Map<String, PublicKey> keys = Map.of();
	private Instant expiry = Instant.MIN, lastAttempt = Instant.MIN;
	GoogleSigningKeys(Clock clock) { this(clock, GoogleSigningKeys::download); }
	GoogleSigningKeys(Clock clock, Loader loader) { this.clock = clock; this.loader = loader; }
	synchronized PublicKey key(String kid) throws Exception {
		Instant now = clock.instant();
		if (now.isBefore(expiry) && keys.containsKey(kid)) return keys.get(kid);
		if (now.isBefore(lastAttempt.plusSeconds(60))) {
			if (!now.isBefore(expiry)) throw new IOException("Certificados no disponibles");
			return null;
		}
		lastAttempt = now;
		KeySet loaded = loader.load();
		if (loaded.keys().isEmpty() || loaded.lifetime().isNegative() || loaded.lifetime().isZero()) throw new IOException("Certificados inválidos");
		keys = Map.copyOf(loaded.keys()); expiry = now.plus(loaded.lifetime());
		return keys.get(kid);
	}
	private static KeySet download() throws Exception {
		HttpURLConnection connection = (HttpURLConnection) URI.create("https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com").toURL().openConnection();
		try {
			connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(5000);
			connection.setRequestProperty("Accept", "application/json");
			if (connection.getResponseCode() != 200) throw new IOException("No se pudieron obtener certificados");
			JsonNode root;
			try (InputStream input = connection.getInputStream()) {
				byte[] bytes = input.readNBytes(32769); if (bytes.length > 32768) throw new IOException("Certificados demasiado grandes");
				root = Json.parse(bytes);
			}
			if (!root.isObject() || root.size() > 10) throw new IOException("Certificados inválidos");
			Map<String, PublicKey> keys = new HashMap<>();
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			for (Iterator<Map.Entry<String, JsonNode>> entries = root.fields(); entries.hasNext();) {
				Map.Entry<String, JsonNode> entry = entries.next();
				String pem = Json.text(entry.getValue(), 8192, false);
				PublicKey key = factory.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))).getPublicKey();
				if (!"RSA".equals(key.getAlgorithm())) throw new IOException("Certificado inválido");
				keys.put(entry.getKey(), key);
			}
			String cache = Objects.toString(connection.getHeaderField("Cache-Control"), "");
			Matcher age = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)").matcher(cache);
			long seconds = age.find() ? Math.min(21600, Long.parseLong(age.group(1))) : 300;
			return new KeySet(keys, Duration.ofSeconds(Math.max(1, seconds)));
		} finally { connection.disconnect(); }
	}
}
