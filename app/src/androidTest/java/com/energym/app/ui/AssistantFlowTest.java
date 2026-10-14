package com.energym.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.core.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.data.*;
import com.energym.app.domain.*;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Pruebas de eventos reales de vistas Android, sin el inyector InputManager incompatible con API37.
 */
@RunWith(AndroidJUnit4.class)
public class AssistantFlowTest {
	@Before
	public void clearDemo() throws Exception {
		Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
		try (EnerGymDatabase db = new EnerGymDatabase(c, EnerGymDatabase.DEMO_OWNER_UID)) {
			db.clearLocalData();
		}
		StringBuilder name = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256")
			.digest(EnerGymDatabase.DEMO_OWNER_UID.getBytes(StandardCharsets.UTF_8)))
			name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
		File file = new File(new File(c.getNoBackupFilesDir(), "conversations"), name + ".json");
		if (file.exists())
			assertTrue(file.delete());
	}

	@Test
	public void localQuestionsPreviewRejectAcceptContextAndRecreation() throws Exception {
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			enter(s);
			capture("assistant");
			send(s, "Quiero entrenar tres días en casa, sin material, 30min");
			s.onActivity(a -> {
				assertNull(a.state().assistant.preview);
				assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "objetivo"));
				assertNull(a.state().app.profile);
			});
			capture("questions");
			send(s, "fuerza principiante");
			s.onActivity(a -> {
				assertNotNull(a.state().assistant.preview);
				assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(), "Vista previa · sin guardar"));
				assertTrue(a.state().app.versions.isEmpty());
			});
			scrollToText(s, "Guardar perfil y aceptar");
			capture("proposal-actions");
			MainFlowTest.click(s, "Rechazar propuesta");
			s.onActivity(a -> {
				assertNull(a.state().app.profile);
				assertTrue(a.state().app.versions.isEmpty());
				assertNull(a.state().assistant.preview);
			});
			send(s, "fuerza principiante");
			s.recreate();
			MainFlowTest.await();
			s.onActivity(a -> assertNotNull(a.state().assistant.preview));
			MainFlowTest.click(s, "Reducir 5 minutos");
			s.onActivity(a -> assertEquals(25, a.state().assistant.preview.getProfile().getDurationMinutes()));
			MainFlowTest.click(s, "Guardar perfil y aceptar");
			waitUntil(s, a -> a.state().app.profile != null);
			s.onActivity(a -> {
				assertEquals(25, a.state().app.profile.getDurationMinutes());
				assertEquals(3, a.state().app.profile.getDaysPerWeek());
				assertNotNull(a.state().app.routines.accepted);
				assertFalse(a.state().app.versions.isEmpty());
			});
			send(s, "prepara rutina de fuerza");
			MainFlowTest.click(s, "Consultar técnica");
			MainFlowTest.click(s, "Explicación guiada");
			s.onActivity(a -> {
				assertEquals(AppController.Screen.ASSISTANT, a.state().app.screen);
				assertNotNull(a.state().assistant.exercise);
				assertTrue(MainFlowTest.textContaining(a.getWindow().getDecorView(),
					a.state().assistant.exercise.getDescription()));
			});
			capture("exercise-context");
			MainFlowTest.await();
			s.onActivity(a -> {
				com.energym.app.assistant.ConversationStore.State persisted =
					new com.energym.app.assistant.LocalConversationStore(a, Runnable::run).load(
						EnerGymDatabase.DEMO_OWNER_UID);
				assertNotNull("Conversación no persistida en archivo local", persisted);
				assertEquals(a.state().assistant.exercise.getDescription(), persisted.exercise.getDescription());
				assertEquals(a.state().assistant.messages.size(), persisted.messages.size());
			});
			MainFlowTest.edit(s, "assistant_message", "Texto sin enviar al rotar");
			int[] count = {0};
			s.onActivity(a -> count[0] = a.state().assistant.messages.size());
			s.recreate();
			MainFlowTest.await();
			s.onActivity(a -> {
				assertEquals(count[0], a.state().assistant.messages.size());
				assertEquals("Texto sin enviar al rotar",
					((EditText) a.getWindow().getDecorView().findViewWithTag("assistant_message")).getText()
						.toString());
			});
			MainFlowTest.click(s, "Cuenta");
			MainFlowTest.click(s, "Salir de la cuenta / demo");
			s.onActivity(a -> {
				assertTrue(a.state().assistant.messages.isEmpty());
				assertNull(a.state().assistant.exercise);
				assertNull(a.state().assistant.preview);
			});
		}
	}

	@Test
	public void assistantComposerAndActionRemainReachableWithKeyboard() throws Exception {
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			enter(s);
			s.onActivity(a -> {
				EditText input = a.getWindow().getDecorView().findViewWithTag("assistant_message");
				input.requestFocus();
				((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,
					InputMethodManager.SHOW_IMPLICIT);
				WindowInsetsControllerCompat insets =
					WindowCompat.getInsetsController(a.getWindow(), a.findViewById(R.id.main));
				insets.show(WindowInsetsCompat.Type.ime());
			});
			boolean[] visible = {false};
			for (int i = 0; i < 12 && !visible[0]; i++) {
				MainFlowTest.await();
				s.onActivity(a -> visible[0] =
					ViewCompat.getRootWindowInsets(a.findViewById(R.id.main)).isVisible(WindowInsetsCompat.Type.ime()));
			}
			assertTrue("Teclado real no disponible sin cambiar ajustes del emulador", visible[0]);
			s.onActivity(a -> {
				float d = a.getResources().getDisplayMetrics().density;
				assertTrue(a.findViewById(R.id.content).getHeight() > 120 * d);
				View send = MainFlowTest.text(a.getWindow().getDecorView(), "Enviar local");
				Rect box = new Rect();
				send.requestRectangleOnScreen(new Rect(0, 0, send.getWidth(), send.getHeight()), true);
			});
			MainFlowTest.await();
			capture("keyboard");
			s.onActivity(a -> {
				View send = MainFlowTest.text(a.getWindow().getDecorView(), "Enviar local");
				Rect box = new Rect();
				assertTrue(send.getGlobalVisibleRect(box));
				assertTrue(box.height() > 40 * a.getResources().getDisplayMetrics().density);
			});
		}
	}

	@Test
	public void assistantLargeFontUsesSameThemeAndReadableControls() throws Exception {
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			enter(s);
			float[] scale = {1};
			s.onActivity(a -> {
				scale[0] = a.getResources().getConfiguration().fontScale;
				Configuration larger = new Configuration(a.getResources().getConfiguration());
				larger.fontScale = 1.3f;
				a.getResources().updateConfiguration(larger, a.getResources().getDisplayMetrics());
				a.state().app.changed();
			});
			try {
				MainFlowTest.await();
				capture("assistant-font130");
				send(s, "3 días en casa sin material 30 min fuerza principiante");
				scrollToText(s, "Guardar perfil y aceptar");
				capture("proposal-font130");
				s.onActivity(a -> {
					View accept = MainFlowTest.text(a.getWindow().getDecorView(), "Guardar perfil y aceptar");
					assertNotNull(accept);
					assertTrue(accept.getHeight() >= 48 * a.getResources().getDisplayMetrics().density);
				});
			} finally {
				s.onActivity(a -> {
					Configuration normal = new Configuration(a.getResources().getConfiguration());
					normal.fontScale = scale[0];
					a.getResources().updateConfiguration(normal, a.getResources().getDisplayMetrics());
					a.state().app.changed();
				});
			}
		}
	}

	private void enter(ActivityScenario<MainActivity> s) {
		MainFlowTest.await();
		MainFlowTest.click(s, "Cuenta");
		boolean[] needs = {false};
		s.onActivity(a -> needs[0] = MainFlowTest.text(a.getWindow().getDecorView(), "Entrar en demo local") != null);
		if (needs[0])
			MainFlowTest.click(s, "Entrar en demo local");
		MainFlowTest.await();
		s.onActivity(a -> ((com.google.android.material.bottomnavigation.BottomNavigationView) a.findViewById(
			R.id.navigation)).setSelectedItemId(R.id.tab_assistant));
		MainFlowTest.await();
	}

	private void send(ActivityScenario<MainActivity> s, String text) {
		MainFlowTest.edit(s, "assistant_message", text);
		MainFlowTest.click(s, "Enviar local");
	}

	private void scrollToText(ActivityScenario<MainActivity> s, String label) {
		s.onActivity(a -> {
			View target = MainFlowTest.text(a.getWindow().getDecorView(), label);
			assertNotNull(target);
			target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true);
		});
		MainFlowTest.await();
	}

	private void scrollBottom(ActivityScenario<MainActivity> s) {
		s.onActivity(a -> ((ScrollView) a.findViewById(R.id.screen_body).getParent()).fullScroll(View.FOCUS_DOWN));
		MainFlowTest.await();
	}

	interface Condition {
		boolean test(MainActivity a);
	}

	private void waitUntil(ActivityScenario<MainActivity> s, Condition condition) {
		boolean[] ready = {false};
		for (int i = 0; i < 40 && !ready[0]; i++) {
			MainFlowTest.await();
			s.onActivity(a -> ready[0] = condition.test(a));
		}
		assertTrue("No llegó la operación local", ready[0]);
	}

	static void capture(String name) throws Exception {
		Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
		File f = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),
			"assistant-" + name + ".png");
		try (OutputStream out = new FileOutputStream(f)) {
			assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
		}
		image.recycle();
		android.os.ParcelFileDescriptor d = InstrumentationRegistry.getInstrumentation().getUiAutomation()
			.executeShellCommand("cp " + f.getAbsolutePath() + " /data/local/tmp/energym-task6-" + name + ".png");
		try (InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(d)) {
			while (in.read() != -1) {
			}
		}
	}
}


