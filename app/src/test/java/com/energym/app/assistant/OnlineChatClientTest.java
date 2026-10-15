package com.energym.app.assistant;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public final class OnlineChatClientTest {
	private static final String TOKEN = "firebase.header.signature";

	@Test public void sendsFirebaseBearerDirectlyToChatWithoutPairingOrUidInTheBody() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try (Server server = new Server(false)) {
			Tokens tokens = new Tokens(true);
			OnlineChatClient client = new OnlineChatClient(worker, Runnable::run, true, server.url(), tokens);
			client.activate("uid-A");
			assertTrue(client.connected());
			Result result = new Result();
			client.send(request(), result);
			assertTrue(result.done.await(5, TimeUnit.SECONDS));
			RequestData sent = server.request.get(5, TimeUnit.SECONDS);
			assertEquals("POST /api/chat HTTP/1.1", sent.firstLine);
			assertEquals("Bearer " + TOKEN, sent.headers.get("authorization"));
			assertEquals(StrictJson.set("conversationId", "requestId", "exerciseId", "message", "profile", "historyConsent", "history"),
				StrictJson.object(StrictJson.parse(sent.body)).keySet());
			assertEquals("{}", result.json);
			assertNull(result.error);
		} finally {
			worker.shutdownNow();
		}
	}

	@Test public void absentBackendUrlReportsConfigurationWithoutAskingForAToken() {
		Tokens tokens = new Tokens(true);
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, false, "", tokens);
		client.activate("uid-A");
		assertFalse(client.connected());
		assertEquals("BACKEND_NOT_CONFIGURED", client.unavailableCode());
		Result result = new Result();
		client.send(request(), result);
		assertEquals("BACKEND_NOT_CONFIGURED", result.error);
		assertTrue(tokens.pending.isEmpty());
	}

	@Test public void releaseRejectsHttpEvenForLoopback() {
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, false,
			"http://127.0.0.1:1", new Tokens(true));
		client.activate("uid-A");
		assertFalse(client.connected());
		assertEquals("CONFIGURACION_INVALIDA", client.unavailableCode());
	}

	@Test public void accountRoundTripDiscardsATokenRequestedBeforeTheSwitch() {
		Tokens tokens = new Tokens(false);
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, true,
			"http://127.0.0.1:1", tokens);
		client.activate("uid-A");
		assertTrue(client.connected());
		Result result = new Result();
		client.send(request(), result);
		tokens.uid = "uid-B";
		client.activate("uid-B");
		tokens.uid = "uid-A";
		client.activate("uid-A");
		tokens.pending.get(0).success(TOKEN);
		assertEquals(0, result.calls.get());
		assertTrue(client.connected());
	}

	@Test public void cancellationDiscardsThePendingTokenAndKeepsTheAccountAvailable() {
		Tokens tokens = new Tokens(false);
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, true,
			"http://127.0.0.1:1", tokens);
		client.activate("uid-A");
		assertTrue(client.connected());
		Result result = new Result();
		client.send(request(), result);
		client.cancelPending();
		tokens.pending.get(0).success(TOKEN);
		assertEquals(0, result.calls.get());
		assertTrue(client.connected());
	}

	@Test public void aLateHttpResponseCannotReachTheAccountAfterARoundTrip() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try (Server server = new Server(true)) {
			Tokens tokens = new Tokens(true);
			OnlineChatClient client = new OnlineChatClient(worker, Runnable::run, true, server.url(), tokens);
			client.activate("uid-A");
			assertTrue(client.connected());
			Result result = new Result();
			client.send(request(), result);
			server.request.get(5, TimeUnit.SECONDS);
			tokens.uid = "uid-B";
			client.activate("uid-B");
			tokens.uid = "uid-A";
			client.activate("uid-A");
			server.release.countDown();
			worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
			assertEquals(0, result.calls.get());
		} finally {
			worker.shutdownNow();
		}
	}

	@Test public void tokenFailureIsReportedWithoutMakingAnHttpRequest() {
		Tokens tokens = new Tokens(false);
		OnlineChatClient client = new OnlineChatClient(Runnable::run, Runnable::run, true,
			"http://127.0.0.1:1", tokens);
		client.activate("uid-A");
		assertTrue(client.connected());
		Result result = new Result();
		client.send(request(), result);
		tokens.pending.get(0).failure("AUTHENTICATION_UNAVAILABLE");
		assertEquals("AUTHENTICATION_UNAVAILABLE", result.error);
		assertEquals(1, result.calls.get());
	}

	@Test public void unauthorizedResponseDoesNotAutomaticallyRepeatTheChatRequest() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try (Server server = new Server(false, 401, "{\"error\":\"UNAUTHORIZED\",\"recoverable\":false}")) {
			OnlineChatClient client = new OnlineChatClient(worker, Runnable::run, true, server.url(), new Tokens(true));
			client.activate("uid-A");
			Result result = new Result();
			client.send(request(), result);
			assertTrue(result.done.await(5, TimeUnit.SECONDS));
			assertEquals("UNAUTHORIZED", result.error);
			assertEquals(1, result.calls.get());
			assertEquals("POST /api/chat HTTP/1.1", server.request.get(5, TimeUnit.SECONDS).firstLine);
			server.socket.setSoTimeout(200);
			assertThrows("No pairing, token refresh retry ni segundo envío", SocketTimeoutException.class, server.socket::accept);
		} finally {
			worker.shutdownNow();
		}
	}

	@Test public void anInterruptedResponseDoesNotAutomaticallyRepeatThePost() throws Exception {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try (Server server = new Server(false, 0, "")) {
			OnlineChatClient client = new OnlineChatClient(worker, Runnable::run, true, server.url(), new Tokens(true));
			client.activate("uid-A");
			Result result = new Result();
			client.send(request(), result);
			server.request.get(5, TimeUnit.SECONDS);
			server.socket.setSoTimeout(400);
			try (Socket repeated = server.socket.accept()) {
				fail("Una respuesta interrumpida no debe repetir el POST ni consumir otra llamada de IA");
			} catch (SocketTimeoutException expected) {
				// El único POST ya terminó; no se abrió otra conexión automáticamente.
			}
			assertTrue(result.done.await(5, TimeUnit.SECONDS));
			assertEquals("CONEXION_O_RESPUESTA_INVALIDA", result.error);
			assertEquals(1, result.calls.get());
		} finally {
			worker.shutdownNow();
		}
	}

	@Test public void publicBackendAvailabilityErrorsKeepTheirContractualCodes() throws Exception {
		for (String error : Arrays.asList("AUTH_UNAVAILABLE", "QUOTA_UNAVAILABLE")) {
			Map<String, Object> response = StrictJson.map("error", error, "recoverable", true);
			if (error.equals("QUOTA_UNAVAILABLE")) {
				response.put("conversationId", "conversation");
				response.put("requestId", "request");
				response.put("exerciseId", null);
			}
			ExecutorService worker = Executors.newSingleThreadExecutor();
			try (Server server = new Server(false, 503, StrictJson.write(response))) {
				OnlineChatClient client = new OnlineChatClient(worker, Runnable::run, true, server.url(), new Tokens(true));
				client.activate("uid-A");
				Result result = new Result();
				client.send(request(), result);
				assertTrue(result.done.await(5, TimeUnit.SECONDS));
				assertEquals(error, result.error);
				assertEquals(1, result.calls.get());
			} finally {
				worker.shutdownNow();
			}
		}
	}

	private static ChatClient.Request request() {
		return new ChatClient.Request("conversation", "request", null, "hola", null, false, Collections.emptyList());
	}

	private static final class Tokens implements OnlineChatClient.TokenSource {
		String uid = "uid-A";
		final boolean immediate;
		final List<ChatClient.Callback> pending = new ArrayList<>();
		Tokens(boolean immediate) {
			this.immediate = immediate;
		}

		public String currentUid() {
			return uid;
		}
		public void getToken(String expectedUid, ChatClient.Callback callback) {
			assertEquals(uid, expectedUid);
			pending.add(callback);
			if (immediate)
				callback.success(TOKEN);
		}
	}

	private static final class Result implements ChatClient.Callback {
		final CountDownLatch done = new CountDownLatch(1);
		final AtomicInteger calls = new AtomicInteger();
		volatile String json, error;
		public void success(String json) {
			this.json = json;
			calls.incrementAndGet();
			done.countDown();
		}
		public void failure(String error) {
			this.error = error;
			calls.incrementAndGet();
			done.countDown();
		}
	}

	private static final class RequestData {
		String firstLine, body;
		final Map<String, String> headers = new HashMap<>();
	}

	private static final class Server implements AutoCloseable {
		final ServerSocket socket = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final CompletableFuture<RequestData> request = new CompletableFuture<>();
		final CountDownLatch release;
		Server(boolean hold) throws IOException {
			this(hold, 200, "{}");
		}
		Server(boolean hold, int status, String json) throws IOException {
			release = new CountDownLatch(hold ? 1 : 0);
			executor.execute(() -> {
				try (Socket client = socket.accept()) {
					client.setSoTimeout(5000);
					BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
					RequestData data = new RequestData();
					data.firstLine = reader.readLine();
					String line;
					while ((line = reader.readLine()) != null && !line.isEmpty()) {
						int colon = line.indexOf(':');
						data.headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
					}
					int length = Integer.parseInt(data.headers.getOrDefault("content-length", "0"));
					char[] body = new char[length];
					for (int offset = 0; offset < length;) {
						int read = reader.read(body, offset, length - offset);
						if (read < 0)
							throw new EOFException();
						offset += read;
					}
					data.body = new String(body);
					request.complete(data);
					if (!release.await(5, TimeUnit.SECONDS))
						throw new IOException("Response not released");
					if (status == 0)
						return;
					OutputStream output = client.getOutputStream();
					byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
					output.write(("HTTP/1.1 " + status + " OK\r\nContent-Type: application/json\r\nContent-Length: " +
						bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					output.write(bytes);
					output.flush();
				} catch (Exception failure) {
					request.completeExceptionally(failure);
				}
			});
		}
		String url() {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
		public void close() throws IOException {
			release.countDown();
			socket.close();
			executor.shutdownNow();
		}
	}
}
