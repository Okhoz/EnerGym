package com.energym.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Prueba conexiones locales reales; nunca consulta proveedores ni envía credenciales. */
class HealthcheckTest {
	private boolean healthy(Map<String, String> env) {
		try {
			return (boolean) Class.forName("com.energym.server.Healthcheck")
				.getDeclaredMethod("healthy", Map.class).invoke(null, env);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("El healthcheck debe existir y comprobar el endpoint", e);
		}
	}

	private boolean response(int status, String body, boolean redirect) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		AtomicBoolean credentials = new AtomicBoolean();
		server.createContext("/health", exchange -> {
			credentials.set(exchange.getRequestHeaders().containsKey("Authorization")
				|| exchange.getRequestHeaders().containsKey("X-EnerGym-Proxy-Token"));
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			if (redirect) exchange.getResponseHeaders().add("Location", "/health");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		try {
			boolean result = healthy(Map.of("ENERGYM_HEALTH_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/health",
				"GEMINI_API_KEY", "fixture-no-real-key", "ENERGYM_PROXY_TOKEN", "fixture-no-real-token"));
			assertFalse(credentials.get(), "La sonda no debe enviar credenciales");
			return result;
		} finally {
			server.stop(0);
		}
	}

	@Test void acceptsHealthyEndpointWithoutCredentials() throws Exception {
		assertTrue(response(200, "{\"status\":\"ok\",\"catalogVersion\":2}", false));
	}
	@Test void renderChecksAssignedPortOnLoopback() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", exchange -> {
			byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			assertTrue(healthy(Map.of("ENERGYM_TRANSPORT", "render", "RENDER", "true",
				"PORT", Integer.toString(server.getAddress().getPort()))));
			assertFalse(healthy(Map.of("ENERGYM_TRANSPORT", "render", "RENDER", "true", "PORT", "70000")));
		} finally { server.stop(0); }
	}
	@Test void rejectsUnavailableEndpoint() throws Exception { assertFalse(response(503, "{\"status\":\"ok\"}", false)); }
	@Test void rejectsInvalidBody() throws Exception { assertFalse(response(200, "{\"status\":\"fail\"}", false)); }
	@Test void rejectsMalformedJson() throws Exception { assertFalse(response(200, "not-json", false)); }
	@Test void rejectsRedirect() throws Exception { assertFalse(response(302, "{\"status\":\"ok\"}", true)); }
	@Test void rejectsOversizedResponse() throws Exception { assertFalse(response(200, " ".repeat(9000) + "{\"status\":\"ok\"}", false)); }
	@Test void rejectsPlaintextOutsideLoopback() { assertFalse(healthy(Map.of("ENERGYM_HEALTH_URL", "http://192.0.2.1:8080/health"))); }
	@Test void rejectsCredentialsInUrl() { assertFalse(healthy(Map.of("ENERGYM_HEALTH_URL", "http://private@127.0.0.1:8080/health"))); }
	@Test void rejectsQueryInUrl() { assertFalse(healthy(Map.of("ENERGYM_HEALTH_URL", "http://127.0.0.1:8080/health?key=fixture"))); }

	@ParameterizedTest
	@ValueSource(strings = {"fixture-password", "fixture-password "})
	void verifiesHttpsCertificateAndHostname(String fixturePassword, @TempDir Path directory) throws Exception {
		// Certificado efímero de prueba, sin credenciales reales ni material privado en recursos.
		Path storePath = directory.resolve("fixture.p12"), passwordPath = directory.resolve("password");
		String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
		Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "health", "-keyalg", "RSA",
			"-keysize", "2048", "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
			"-keystore", storePath.toString(), "-storetype", "PKCS12", "-storepass", fixturePassword)
			.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		assertTrue(process.waitFor(20, TimeUnit.SECONDS));
		assertEquals(0, process.exitValue());
		Files.writeString(passwordPath, fixturePassword + "\n");
		KeyStore store = KeyStore.getInstance("PKCS12");
		try (var input = Files.newInputStream(storePath)) { store.load(input, fixturePassword.toCharArray()); }
		KeyManagerFactory key = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		key.init(store, fixturePassword.toCharArray());
		SSLContext context = SSLContext.getInstance("TLS");
		context.init(key.getKeyManagers(), null, null);
		HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(context));
		server.createContext("/health", exchange -> {
			byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			String endpoint = "https://localhost:" + server.getAddress().getPort() + "/health";
			Map<String, String> env = new java.util.HashMap<>(Map.of("ENERGYM_HEALTH_URL", endpoint,
				"ENERGYM_HEALTH_TRUSTSTORE", storePath.toString(), "ENERGYM_HEALTH_TRUSTSTORE_PASSWORD_FILE", passwordPath.toString()));
			assertTrue(healthy(env), "CA conocida y SAN coincidente deben funcionar");
			for (String ending : new String[]{"", "\n", "\r\n"}) {
				Files.writeString(passwordPath, fixturePassword + ending);
				assertTrue(healthy(env), "Conservar contraseña exacta con final vacío, LF o CRLF");
			}
			for (String malformed : new String[]{fixturePassword + "\n\n", fixturePassword + "\r",
				fixturePassword + "\nextra", "\uFEFF" + fixturePassword + "\n"}) {
				Files.writeString(passwordPath, malformed);
				assertFalse(healthy(env), "Rechazar multilínea, CR aislado y BOM");
			}
			Files.writeString(passwordPath, fixturePassword + "\n");
			env.put("ENERGYM_HEALTH_URL", endpoint.replace("localhost", "127.0.0.1"));
			assertFalse(healthy(env), "SAN incorrecto debe fallar");
			assertFalse(healthy(Map.of("ENERGYM_HEALTH_URL", endpoint)), "CA desconocida debe fallar");
		} finally { server.stop(0); }
	}
}
