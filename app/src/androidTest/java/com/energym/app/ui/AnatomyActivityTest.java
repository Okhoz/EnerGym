package com.energym.app.ui;


import android.app.UiAutomation;

import androidx.test.core.app.ActivityScenario;

import com.energym.app.MainActivity;

import com.energym.app.domain.Exercise;

import com.energym.app.controller.AppController;
import com.energym.app.assistant.StrictJson;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import android.content.*;

import android.graphics.*;

import android.os.*;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.visual.*;

import java.io.*;

import java.nio.charset.StandardCharsets;

import org.junit.*;

import org.junit.runner.RunWith;

import static org.junit.Assert.*;


/**
 * GPU real entre procesos: accesibilidad y captura del sistema, sin depender de ActivityScenario.
 */

@RunWith(AndroidJUnit4.class)

public class AnatomyActivityTest {

	private ActivityScenario<MainActivity> parent;

	private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	private final UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

	@Before
	public void reportIds() throws Exception {
		parent = ActivityScenario.launch(MainActivity.class);
		for (int i = 0; i < 80; i++) {
			boolean[] ready = {false};
			parent.onActivity(a -> ready[0] = a.state().app != null);
			if (ready[0])
				break;
			Thread.sleep(100);
		}
		parent.onActivity(a -> a.state().accounts.enterDemo());
		Thread.sleep(500);
		android.accessibilityservice.AccessibilityServiceInfo info = automation.getServiceInfo();
		info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
		automation.setServiceInfo(info);
	}

	@After
	public void closeRemote() throws Exception {
		context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName()));
		awaitGone();
		if (parent != null)
			parent.close();
	}

	@Test
	public void capturesAndSchematicClipsFromSeveralGroupsLoadInFilamentAndMove() throws Exception {
		// Capturas CC0 (incluidas las retargeteadas) y clips esquemáticos de pierna, espalda, pecho, core y movilidad.
		for (String id : new String[]{"jumping-jacks", "running-easy", "brisk-walk", "hill-sprints", "swimming-easy",
			"barbell-back-squat", "pull-up", "barbell-bench-press", "forearm-plank", "pigeon-stretch"}) {
			launch(id);
			awaitReady();
			click("viewer_initial");
			Bitmap initial = captureSurface("viewer-" + id + "-initial.png");
			assertTrue(id, nonBlack(initial) > 1500);
			click("viewer_middle");
			Bitmap middle = captureSurface("viewer-" + id + "-middle.png");
			assertTrue("Superficie vacía en pose intermedia: " + id, nonBlack(middle) > 1500);
			// Los isométricos sostienen su postura; exigir desplazamiento falsearía su técnica.
			if (!id.equals("forearm-plank"))
				assertTrue("No cambia la pose real: " + id, different(initial, middle) > 150);
			android.util.Log.i("EnerGymViewerQA", id + " initialSamples=" + nonBlack(initial) + " initialMiddle=" +
				different(initial, middle));
			context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName()));
			awaitGone();
		}
	}

	@Test
	public void repairedVariantsRenderTheirDifferentMarkedPoses() throws Exception {
		for (String id : new String[]{"band-triceps-extension", "dumbbell-sumo-squat", "plank-shoulder-taps"}) {
			launch(id);
			awaitReady();
			click("viewer_initial");
			Bitmap initial = captureSurface("viewer-repaired-" + id + "-initial.png");
			assertTrue("Superficie inicial vacía: " + id, nonBlack(initial) > 1500);
			click("viewer_middle");
			Bitmap middle = captureSurface("viewer-repaired-" + id + "-middle.png");
			assertTrue("Superficie intermedia vacía: " + id, nonBlack(middle) > 1500);
			assertTrue("El marcador debe mostrar otra fase del movimiento: " + id, different(initial, middle) > 150);
			context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName()));
			awaitGone();
		}
	}

	@Test
	public void remoteNativeMeshMovesAndRealButtonsControlCamera() throws Exception {

		launch("standard-pushup");
		awaitReady();
		String pid = pid();
		assertFalse(pid.isEmpty());

		click("viewer_initial");
		Bitmap initial = captureSurface("viewer-initial.png");
		assertTrue(nonBlack(initial) > 1500);

		click("viewer_middle");
		Bitmap middle = captureSurface("viewer-middle.png");
		assertTrue("No cambia la pose real", different(initial, middle) > 150);

		click("viewer_final");
		Bitmap end = captureSurface("viewer-final.png");
		assertTrue(different(middle, end) > 150);

		android.util.Log.i("EnerGymViewerQA",
			"remoteNative initialSamples=" + nonBlack(initial) + " initialMiddle=" + different(initial, middle) +
				" middleFinal=" + different(middle, end) + " initialFinal=" + different(initial, end));

		click("viewer_rotate_left");
		Bitmap rotated = captureSurface("viewer-rotated.png");
		assertTrue(different(end, rotated) > 150);

		click("viewer_zoom_in");
		Bitmap zoomed = captureSurface("viewer-zoomed.png");
		assertTrue(different(rotated, zoomed) > 150);

		click("viewer_reset");
		click("viewer_speed_quarter");
		assertTrue(node("viewer_speed_quarter").isSelected());

		click("viewer_initial");
		Bitmap paused = captureSurface("viewer-paused.png");
		Thread.sleep(400);
		Bitmap frozen = captureSurface("viewer-frozen.png");
		assertTrue("La malla no respeta pausa", different(paused, frozen) < 100);

		click("viewer_play");
		Thread.sleep(650);
		click("viewer_play");
		Bitmap advanced = captureSurface("viewer-advanced.png");
		assertTrue("Reproducir no avanza la animación", different(frozen, advanced) > 150);

		scrollTop();
		Thread.sleep(800);
		save(automation.takeScreenshot(), "viewer-screen.png");
		scrollBottom();
		Thread.sleep(500);
		save(automation.takeScreenshot(), "viewer-availability.png");

		assertEquals("El proceso principal fue sustituido", context.getPackageName(),
			android.app.Application.getProcessName());

		initial.recycle();
		middle.recycle();
		end.recycle();
		rotated.recycle();
		zoomed.recycle();
		paused.recycle();
		frozen.recycle();
		advanced.recycle();

	}

	@Test
	public void sevenFinalClosesReclaimPrivateProcessAndMainMemoryStaysBounded() throws Exception {

		long baseline = 0, last = 0;

		for (int cycle = 0; cycle < 7; cycle++) {

			launch("standard-pushup");
			awaitReady();
			String child = pid();
			assertFalse(child.isEmpty());
			assertNotEquals(Integer.toString(android.os.Process.myPid()), child);

			click("viewer_back");
			awaitGone();
			Runtime.getRuntime().gc();
			System.runFinalization();
			Thread.sleep(300);

			long heap = android.os.Debug.getNativeHeapAllocatedSize() / 1024;
			if (cycle == 0)
				baseline = heap;
			last = heap;

			android.util.Log.i("EnerGymViewerQA",
				"remoteClosedCycle=" + cycle + " childGone=true mainNativeHeapKb=" + heap + " mainPssKb=" +
					android.os.Debug.getPss());

		}

		assertTrue("Retención nativa en el proceso principal", last - baseline < 8192);

		for (int i = 0; i < 2; i++) {
			launch("standard-pushup");
			awaitNode("viewer_back");
			context.sendBroadcast(new Intent(AnatomyActivity.ACTION_CLOSE).setPackage(context.getPackageName()));
			awaitGone();
		}

	}

	@Test
	public void rotationAndHomePreservePausedPoseWithoutKillingOnRecreation() throws Exception {

		String rotation = shell("wm user-rotation").trim();
		assertTrue(rotation.equals("free") || rotation.matches("lock [0-3]"));

		try {

			launch("standard-pushup");
			awaitReady();
			click("viewer_speed_half");
			click("viewer_initial");
			String child = pid();

			shell("wm user-rotation lock 1");
			awaitReady();
			Thread.sleep(1200);
			assertEquals("Se mató el proceso al recrear", child, pid());
			assertTrue(text("viewer_status").contains("En pausa"));
			assertTrue(text("viewer_status").contains("0,50"));

			shell("wm user-rotation lock 0");
			awaitReady();
			Thread.sleep(900);
			assertEquals(child, pid());
			assertTrue(text("viewer_status").contains("En pausa"));

			automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
			Thread.sleep(500);

			context.startActivity(new Intent(context, AnatomyActivity.class).addFlags(
				Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));

			awaitReady();
			assertEquals(child, pid());
			assertTrue(text("viewer_status").contains("En pausa"));

		} finally {
			shell("wm user-rotation " + rotation);
		}

	}

	@Test
	public void variantsHaveTheirOwnLabelledViewAndUnscopedChildRejected() throws Exception {

		parent.onActivity(a -> {
			Exercise knee = a.state().app.catalog.findExercise("knee-pushup");
			a.state().app.openExercise(knee);
			assertTrue(a.state().app.visualizationAvailable());
			assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "Animación esquemática"));
		});

		context.startActivity(
			new Intent(context, AnatomyActivity.class).putExtra(AnatomyActivity.EXTRA_EXERCISE_ID, "standard-pushup")
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		Thread.sleep(700);
		awaitGone();

		ResourceRepository repo = PackagedResources.load(context);
		assertEquals(ResourceManifest.Kind.SCHEMATIC, repo.forExercise("knee-pushup").kind);
		assertEquals(ResourceManifest.Kind.CC0_CAPTURE, repo.forExercise("standard-pushup").kind);
		assertFalse(repo.forExercise("standard-pushup").professionallyReviewed);

	}

	@Test
	public void realTrainingDetailViewerBackAndAccountClosure() throws Exception {

		parent.onActivity(a -> a.state().app.navigate(AppController.Screen.TRAINING));
		Thread.sleep(400);
		MainFlowTest.trainingPane(parent, "exercises");
		save(automation.takeScreenshot(), "viewer-training.png");

		MainFlowTest.edit(parent, "exercise_search", "Flexión estándar");
		MainFlowTest.click(parent, "Abrir técnica de Flexión estándar");
		save(automation.takeScreenshot(), "viewer-exercise.png");

		MainFlowTest.click(parent, "Vista previa 3D");
		awaitReady();
		click("viewer_back");
		awaitGone();

		parent.onActivity(a -> {
			assertEquals(AppController.Screen.EXERCISE, a.state().app.screen);
			assertNotNull(MainFlowTest.text(a.getWindow().getDecorView(), "Flexión estándar"));
		});

		MainFlowTest.click(parent, "Explicación guiada");
		parent.onActivity(a -> {
			assertEquals(AppController.Screen.ASSISTANT, a.state().app.screen);
			assertEquals("standard-pushup", a.state().assistant.exercise.getId());
			assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "Técnica de Flexión estándar"));
			a.state().app.back();
			a.state().app.openExercise(a.state().app.catalog.findExercise("standard-pushup"));
		});

		launch("standard-pushup");
		awaitReady();
		parent.onActivity(a -> a.state().accounts.signOut());
		awaitGone();
		parent.onActivity(a -> assertNull(a.state().app.owner()));

	}

	@Test
	public void revokedPendingLaunchCannotRenderOrCreateNativeResources() throws Exception {

		final VisualizationController.Request[] pending = {null};

		parent.onActivity(a -> {
			a.state().bindViewer(request -> pending[0] = request);
			a.state().app.openExercise(a.state().app.catalog.findExercise("standard-pushup"));
			a.state().app.requestVisualization();
			a.state().app.navigate(AppController.Screen.HOME);
		});

		assertNotNull(pending[0]);
		assertFalse(ViewerLeaseProvider.valid(context, pending[0].lease, pending[0].exerciseId));

		context.startActivity(new Intent(context, AnatomyActivity.class).putExtra(AnatomyActivity.EXTRA_EXERCISE_ID,
				pending[0].exerciseId).putExtra(AnatomyActivity.EXTRA_LEASE, pending[0].lease)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		Thread.sleep(700);
		awaitGone();
		assertNull(node("viewer_play"));

		parent.onActivity(a -> a.state().visualization.result(pending[0].lease));

	}

	@Test
	public void largeFontTrainingViewerAndAssistantKeyboardRemainUsable() throws Exception {
		String font = shell("settings get system font_scale").trim();
		assertTrue(font.matches("[0-9]+(\\.[0-9]+)?"));
		try {
			shell("settings put system font_scale 1.3");
			Thread.sleep(1200);
			parent.onActivity(a -> a.state().app.navigate(AppController.Screen.TRAINING));
			Thread.sleep(300);
			MainFlowTest.trainingPane(parent, "exercises");
			save(automation.takeScreenshot(), "viewer-training-font130.png");
			MainFlowTest.edit(parent, "exercise_search", "Flexión estándar");
			MainFlowTest.click(parent, "Abrir técnica de Flexión estándar");
			save(automation.takeScreenshot(), "viewer-exercise-font130.png");
			MainFlowTest.click(parent, "Vista previa 3D");
			awaitReady();
			scrollTop();
			save(automation.takeScreenshot(), "viewer-screen-font130.png");
			scrollBottom();
			save(automation.takeScreenshot(), "viewer-controls-font130.png");
			click("viewer_initial");
			click("viewer_speed_half");
			assertTrue(node("viewer_speed_half").isSelected());
			click("viewer_back");
			awaitGone();
			MainFlowTest.click(parent, "Explicación guiada");
			parent.onActivity(a -> {
				android.widget.EditText input = a.getWindow().getDecorView().findViewWithTag("assistant_message");
				input.requestFocus();
				((android.view.inputmethod.InputMethodManager) a.getSystemService(
					Context.INPUT_METHOD_SERVICE)).showSoftInput(input,
					android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
				androidx.core.view.WindowCompat.getInsetsController(a.getWindow(),
					a.findViewById(com.energym.app.R.id.main)).show(androidx.core.view.WindowInsetsCompat.Type.ime());
			});
			boolean[] visible = {false};
			for (int n = 0; n < 20 && !visible[0]; n++) {
				Thread.sleep(200);
				parent.onActivity(a -> visible[0] =
					androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(com.energym.app.R.id.main))
						.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
			}
			assertTrue("Teclado real ausente", visible[0]);
			parent.onActivity(a -> {
				android.view.View send = MainFlowTest.text(a.getWindow().getDecorView(), "Enviar mensaje");
				send.requestRectangleOnScreen(new Rect(0, 0, send.getWidth(), send.getHeight()), true);
			});
			Thread.sleep(300);
			save(automation.takeScreenshot(), "viewer-assistant-keyboard-font130.png");
			parent.onActivity(a -> {
				android.view.View send = MainFlowTest.text(a.getWindow().getDecorView(), "Enviar mensaje");
				assertTrue(send.getGlobalVisibleRect(new Rect()));
			});
			automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
		} finally {
			shell("settings put system font_scale " + font);
			Thread.sleep(700);
		}
	}

	@Test
	public void privateProcessCrashReturnsWrittenParentAndAllowsFreshLease() throws Exception {
		launch("standard-pushup");
		awaitReady();
		String child = pid();
		int mainPid = android.os.Process.myPid();
		assertTrue(child.matches("[0-9]+"));
		assertNotEquals(mainPid, Integer.parseInt(child));
		assertEquals("com.energym.app:viewer", shell("cat /proc/" + child + "/cmdline").replace("\u0000", "").trim());
		android.os.Process.killProcess(Integer.parseInt(child));
		awaitGone();
		Thread.sleep(500);
		assertEquals(mainPid, android.os.Process.myPid());
		parent.onActivity(a -> {
			assertEquals(AppController.Screen.EXERCISE, a.state().app.screen);
			assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "Técnica y ejecución"));
			assertTrue(a.state().app.visualizationAvailable());
		});
		MainFlowTest.click(parent, "Vista previa 3D");
		awaitReady();
		assertNotEquals(child, pid());
		click("viewer_back");
		awaitGone();
	}

	@Test
	public void correlatedHttpOpenUsesCurrentContextAndLateReplyCannotStealFocus() throws Exception {
		try (OpenFixture fixture = new OpenFixture()) {
			parent.onActivity(a -> {
				a.state().app.openExercise(a.state().app.catalog.findExercise("standard-pushup"));
				a.state().app.requestExplanation();
				a.state().assistant.connect("http://127.0.0.1:" + fixture.port(), "synthetic-pairing-code-123456789");
			});
			waitAssistant();
			parent.onActivity(a -> assertTrue(a.state().assistant.connected()));
			MainFlowTest.edit(parent, "assistant_message", "Abre la referencia");
			MainFlowTest.click(parent, "Enviar mensaje");
			awaitReady();
			assertEquals("standard-pushup", fixture.lastExercise);
			click("viewer_back");
			awaitGone();
			parent.onActivity(a -> assertEquals(AppController.Screen.ASSISTANT, a.state().app.screen));
			fixture.hold = true;
			MainFlowTest.edit(parent, "assistant_message", "Abre otra vez");
			MainFlowTest.click(parent, "Enviar mensaje");
			assertTrue(fixture.received.await(8, TimeUnit.SECONDS));
			parent.onActivity(a -> {
				a.state().app.navigate(AppController.Screen.TRAINING);
				a.state().app.navigate(AppController.Screen.ASSISTANT);
			});
			fixture.release.countDown();
			waitAssistant();
			Thread.sleep(500);
			assertTrue(pid().isEmpty());
			parent.onActivity(a -> {
				assertEquals(AppController.Screen.ASSISTANT, a.state().app.screen);
				assertTrue(a.state().assistant.status.contains("contexto actual"));
				a.state().assistant.disconnect();
			});
		}
	}

	private void waitAssistant() throws Exception {
		for (int i = 0; i < 100; i++) {
			boolean[] idle = {false};
			parent.onActivity(a -> idle[0] = !a.state().assistant.busy);
			if (idle[0])
				return;
			Thread.sleep(100);
		}
		fail("El callback de conexión no terminó");
	}

	/**
	 * Backend HTTP sintético local: prueba el contrato real sin llamar a ningún proveedor de IA.
	 */
	private static final class OpenFixture implements AutoCloseable {
		final ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
		final CountDownLatch received = new CountDownLatch(1), release = new CountDownLatch(1);
		volatile boolean hold, closed;
		volatile String lastExercise;
		final Thread thread;

		OpenFixture() throws IOException {
			thread = new Thread(this::run, "viewer-contract-fixture");
			thread.setDaemon(true);
			thread.start();
		}

		private static Map<String, Object> map(Object... pairs) {
			Map<String, Object> result = new LinkedHashMap<>();
			for (int i = 0; i < pairs.length; i += 2)
				result.put((String) pairs[i], pairs[i + 1]);
			return result;
		}

		int port() {
			return server.getLocalPort();
		}

		private void run() {
			while (!closed) {
				try (Socket socket = server.accept()) {
					socket.setSoTimeout(10000);
					BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
					String first = line(in), header;
					int length = 0;
					while (!(header = line(in)).isEmpty())
						if (header.toLowerCase(Locale.ROOT).startsWith("content-length:"))
							length = Integer.parseInt(header.substring(15).trim());
					byte[] body = new byte[length];
					int count = 0;
					while (count < length) {
						int n = in.read(body, count, length - count);
						if (n < 0)
							throw new EOFException();
						count += n;
					}
					String response;
					int status;
					if (first.contains("/api/session") && !first.startsWith("DELETE")) {
						status = 201;
						response = StrictJson.write(
							map("token", "synthetic_token_12345678901234567890", "expiresAtEpochSeconds",
								System.currentTimeMillis() / 1000 + 120));
					} else if (first.contains("/api/chat")) {
						Map<String, Object> request =
							StrictJson.object(StrictJson.parse(new String(body, StandardCharsets.UTF_8)));
						lastExercise = (String) request.get("exerciseId");
						if (hold) {
							received.countDown();
							release.await(10, TimeUnit.SECONDS);
						}
						status = 200;
						response = StrictJson.write(
							map("text", "Referencia solicitada", "missingFields", Collections.emptyList(), "proposal",
								null, "commands",
								Collections.singletonList(map("type", "OPEN_EXERCISE", "exerciseId", lastExercise)),
								"catalogVersion", 2, "proposalNotice", "Vista previa sin revisión profesional",
								"conversationId", request.get("conversationId"), "requestId", request.get("requestId"),
								"exerciseId", lastExercise));
					} else {
						status = 200;
						response = "{}";
					}
					byte[] payload = response.getBytes(StandardCharsets.UTF_8);
					OutputStream out = socket.getOutputStream();
					out.write(("HTTP/1.1 " + status + " OK\r\nContent-Type: application/json\r\nContent-Length: " +
						payload.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
					out.write(payload);
					out.flush();
				} catch (Exception e) {
					if (!closed)
						android.util.Log.e("EnerGymViewerQA", "Fixture local", e);
				}
			}
		}

		private static String line(InputStream in) throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int b;
			while ((b = in.read()) != -1 && b != '\n')
				if (b != '\r')
					out.write(b);
			return out.toString(StandardCharsets.US_ASCII.name());
		}

		@Override
		public void close() throws Exception {
			closed = true;
			release.countDown();
			server.close();
			thread.join(1000);
		}
	}

	private void launch(String id) throws Exception {
		parent.onActivity(a -> {
			Exercise exercise = a.state().app.catalog.findExercise(id);
			assertNotNull(exercise);
			a.state().app.openExercise(exercise);
			assertTrue(a.state().app.visualizationAvailable());
			a.state().app.requestVisualization();
		});
		Thread.sleep(400);
	}

	private void awaitReady() throws Exception {
		awaitText("viewer_status", "Sin conexión");
		Thread.sleep(700);
	}

	private void awaitNode(String id) throws Exception {
		for (int i = 0; i < 60; i++) {
			if (node(id) != null)
				return;
			Thread.sleep(100);
		}
		fail("Vista no creada: " + id);
	}

	private void awaitText(String id, String expected) throws Exception {
		for (int i = 0; i < 80; i++) {
			if (text(id).contains(expected))
				return;
			Thread.sleep(250);
		}
		fail("Estado esperado ausente: " + expected + "; " + text("viewer_load_message"));
	}

	private String text(String id) {
		AccessibilityNodeInfo n = node(id);
		return n == null || n.getText() == null ? "" : n.getText().toString();
	}

	private AccessibilityNodeInfo node(String id) {
		AccessibilityNodeInfo root = automation.getRootInActiveWindow();
		return root == null ? null : find(root, "com.energym.app:id/" + id);
	}

	private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String id) {
		if (id.equals(n.getViewIdResourceName()))
			return n;
		for (int i = 0; i < n.getChildCount(); i++) {
			AccessibilityNodeInfo child = n.getChild(i);
			if (child != null) {
				AccessibilityNodeInfo found = find(child, id);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	private void click(String id) throws Exception {

		AccessibilityNodeInfo target = node(id);
		if (target != null)
			target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
		Thread.sleep(250);
		target = node(id);

		for (int i = 0; i < 12 && (target == null || !target.isVisibleToUser()); i++) {
			AccessibilityNodeInfo scroll = node("viewer_scroll");
			assertNotNull(scroll);
			scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
			Thread.sleep(200);
			target = node(id);
		}

		assertNotNull("Control ausente: " + id, target);
		assertTrue("Control no responde: " + id, target.performAction(AccessibilityNodeInfo.ACTION_CLICK));
		Thread.sleep(300);

	}

	private void scrollTop() throws Exception {
		for (int i = 0; i < 12; i++) {
			AccessibilityNodeInfo n = node("viewer_scroll");
			if (n == null || !n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
				break;
			Thread.sleep(100);
		}
		Thread.sleep(250);
	}

	private void scrollBottom() throws Exception {
		for (int i = 0; i < 12; i++) {
			AccessibilityNodeInfo n = node("viewer_scroll");
			if (n == null || !n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
				break;
			Thread.sleep(100);
		}
	}

	private Bitmap captureSurface(String name) throws Exception {

		scrollTop();
		Thread.sleep(300);
		AccessibilityNodeInfo surface = node("viewer_surface");
		assertNotNull(surface);
		Rect bounds = new Rect();
		surface.getBoundsInScreen(bounds);

		Bitmap screen = automation.takeScreenshot();
		assertNotNull(screen);
		assertTrue(bounds.width() > 0 && bounds.height() > 0 && bounds.left >= 0 && bounds.top >= 0 &&
			bounds.right <= screen.getWidth() && bounds.bottom <= screen.getHeight());

		Bitmap image = Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height());
		screen.recycle();
		save(image, name);
		return image;

	}

	private String pid() throws IOException {
		return shell("pidof com.energym.app:viewer").trim();
	}

	private void awaitGone() throws Exception {
		for (int i = 0; i < 60; i++) {
			if (pid().isEmpty())
				return;
			Thread.sleep(100);
		}
		fail("El proceso privado no se reclamó al cerrar");
	}

	private String shell(String command) throws IOException {
		try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
			automation.executeShellCommand(command)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] b = new byte[1024];
			int n;
			while ((n = in.read(b)) != -1)
				out.write(b, 0, n);
			return out.toString(StandardCharsets.UTF_8.name());
		}
	}

	private static int nonBlack(Bitmap a) {
		int n = 0;
		for (int y = 0; y < a.getHeight(); y += 2)
			for (int x = 0; x < a.getWidth(); x += 2)
				if ((a.getPixel(x, y) & 0xffffff) > 0x202020)
					n++;
		return n;
	}

	private static int different(Bitmap a, Bitmap b) {
		assertEquals(a.getWidth(), b.getWidth());
		assertEquals(a.getHeight(), b.getHeight());
		int n = 0;
		for (int y = 0; y < a.getHeight(); y += 2)
			for (int x = 0; x < a.getWidth(); x += 2) {
				int ca = a.getPixel(x, y), cb = b.getPixel(x, y);
				if (Math.abs(((ca >> 16) & 255) - ((cb >> 16) & 255)) +
					Math.abs(((ca >> 8) & 255) - ((cb >> 8) & 255)) + Math.abs((ca & 255) - (cb & 255)) > 30)
					n++;
			}
		return n;
	}

	private void save(Bitmap image, String file) throws IOException {
		try (OutputStream out = new FileOutputStream(new File(context.getExternalFilesDir(null), file))) {
			assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
		}
	}

}



