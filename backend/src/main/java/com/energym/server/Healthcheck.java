package com.energym.server;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Sonda técnica autónoma de proceso: sólo GET /health, sin credenciales ni llamadas al modelo. */
public final class Healthcheck {
	private Healthcheck() { }

	static boolean healthy(Map<String, String> env) {
		HttpURLConnection connection = null;
		try {
			URI uri = URI.create(env.getOrDefault("ENERGYM_HEALTH_URL", "http://127.0.0.1:8080/health"));
			if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
				|| uri.getFragment() != null || !"/health".equals(uri.getRawPath())) return false;
			if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme())
				&& InetAddress.getByName(uri.getHost()).isLoopbackAddress())) return false;
			connection = (HttpURLConnection) uri.toURL().openConnection();
			connection.setConnectTimeout(3000);
			connection.setReadTimeout(3000);
			connection.setInstanceFollowRedirects(false);
			connection.setRequestMethod("GET");
			String storePath = env.getOrDefault("ENERGYM_HEALTH_TRUSTSTORE", "");
			if (connection instanceof HttpsURLConnection tls && !storePath.isBlank()) {
				String passwordPath = env.get("ENERGYM_HEALTH_TRUSTSTORE_PASSWORD_FILE");
				if (passwordPath == null) return false;
				byte[] passwordBytes = Files.readAllBytes(Path.of(passwordPath));
				if (passwordBytes.length == 0 || passwordBytes.length > 4096) return false;
				char[] password;
				try {
					// Sólo se retira un LF o CRLF final; los espacios pertenecen a la contraseña.
					int length = passwordBytes.length;
					if (passwordBytes[length - 1] == '\n') {
						length--;
						if (length > 0 && passwordBytes[length - 1] == '\r') length--;
					}
					if (length >= 3 && passwordBytes[0] == (byte) 0xef
						&& passwordBytes[1] == (byte) 0xbb && passwordBytes[2] == (byte) 0xbf) return false;
					for (int i = 0; i < length; i++) {
						if (passwordBytes[i] == '\n' || passwordBytes[i] == '\r') return false;
					}
					password = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(passwordBytes, 0, length))
						.toString().toCharArray();
				} finally { Arrays.fill(passwordBytes, (byte) 0); }
				if (password.length == 0) return false;
				KeyStore store = KeyStore.getInstance("PKCS12");
				try (InputStream input = Files.newInputStream(Path.of(storePath))) {
					store.load(input, password);
				} finally { Arrays.fill(password, '\0'); }
				TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trust.init(store);
				SSLContext context = SSLContext.getInstance("TLS");
				context.init(null, trust.getTrustManagers(), null);
				tls.setSSLSocketFactory(context.getSocketFactory());
				// Se conserva la comprobación de hostname predeterminada del JDK.
			}
			if (connection.getResponseCode() != 200) return false;
			try (InputStream input = connection.getInputStream()) {
				byte[] body = input.readNBytes(8193);
				return body.length <= 8192 && "ok".equals(Json.parse(body).path("status").asText());
			}
		} catch (Exception e) {
			return false;
		} finally {
			if (connection != null) connection.disconnect();
		}
	}

	public static void main(String[] args) {
		System.exit(healthy(System.getenv()) ? 0 : 1);
	}
}
