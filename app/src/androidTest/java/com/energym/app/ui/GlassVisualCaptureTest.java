package com.energym.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.ui.effects.GlassButton;

import java.io.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Capturas reproducibles de datos demo; todo ajuste global se restaura incluso si falla la prueba.
 */
@RunWith(AndroidJUnit4.class)
public final class GlassVisualCaptureTest {
	@Test
	public void normalFallbackAndLargeTextWithImeRemainUsable() throws Exception {
		String font = shell("settings get system font_scale").trim();
		String animations = shell("settings get global animator_duration_scale").trim();
		String keyboard = shell("settings get secure show_ime_with_hard_keyboard").trim();
		try {
			shell("settings put system font_scale 1.0");
			shell("settings put global animator_duration_scale 1.0");
			try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
				MainFlowTest.await();
				capture("home-normal");
				shell("settings put global animator_duration_scale 0");
				MainFlowTest.await();
				scenario.onActivity(a -> {
					a.state().app.navigate(AppController.Screen.TRAINING);
					GlassButton account = a.findViewById(R.id.account);
					account.setPressed(true);
					assertEquals(1f, account.getScaleX(), 0f);
					account.setPressed(false);
				});
				MainFlowTest.await();
				scenario.onActivity(a -> assertEquals(1f, ((MotionLayout) a.findViewById(R.id.screen_motion)).getProgress(), 0f));
				capture("training-fallback-reduced-motion");
				MainFlowTest.click(scenario, "Cuenta");
				boolean[] demo = {false};
				scenario.onActivity(a -> demo[0] = MainFlowTest.text(a.getWindow().getDecorView(), "Entrar en demo local") != null);
				if (demo[0])
					MainFlowTest.click(scenario, "Entrar en demo local");
				scenario.onActivity(a -> a.state().app.navigate(AppController.Screen.HOME));
				MainFlowTest.await();
				shell("settings put system font_scale 1.3");
				shell("settings put secure show_ime_with_hard_keyboard 1");
				MainFlowTest.await();
				scenario.onActivity(a -> a.state().app.navigate(AppController.Screen.PROFILE));
				MainFlowTest.await();
				capture("profile-130");
				scenario.onActivity(a -> {
					EditText field = a.getWindow().getDecorView().findViewWithTag("preferences");
					assertNotNull(field);
					field.requestFocus();
					((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
				});
				boolean[] ime = {false};
				for (int i = 0; i < 30 && !ime[0]; i++) {
					MainFlowTest.await();
					scenario.onActivity(a -> ime[0] = ViewCompat.getRootWindowInsets(a.findViewById(R.id.main)).isVisible(WindowInsetsCompat.Type.ime()));
				}
				assertTrue("Teclado real ausente", ime[0]);
				capture("profile-130-ime");
				// El IME puede añadir su aviso de tamaño de letra después de abrirse y cambiar sus insets.
				boolean[] saveVisible = {false};
				for (int i = 0; i < 8 && !saveVisible[0]; i++) {
					scenario.onActivity(a -> {
						ScrollView scroll = (ScrollView) a.findViewById(R.id.screen_body).getParent();
						scroll.setSmoothScrollingEnabled(false);
						scroll.fullScroll(View.FOCUS_DOWN);
					});
					MainFlowTest.await();
					scenario.onActivity(a -> {
						View save = MainFlowTest.text(a.getWindow().getDecorView(), "Guardar perfil");
						Rect visible = new Rect();
						saveVisible[0] = save != null && save.getGlobalVisibleRect(visible) && visible.height() > 40 * a.getResources().getDisplayMetrics().density;
					});
				}
				capture("profile-130-ime-save");
				scenario.onActivity(a -> {
					View save = MainFlowTest.text(a.getWindow().getDecorView(), "Guardar perfil");
					Rect visible = new Rect();
					assertNotNull(save);
					assertTrue("Guardar inaccesible con IME y 130%", save.getGlobalVisibleRect(visible));
					assertTrue("Guardar recortado: visible=" + visible.height() + " height=" + save.getHeight(), visible.height() > 40 * a.getResources().getDisplayMetrics().density);
				});
			}
		} finally {
			restore("system", "font_scale", font);
			restore("global", "animator_duration_scale", animations);
			restore("secure", "show_ime_with_hard_keyboard", keyboard);
		}
	}

	private static void restore(String table, String key, String value) throws IOException {
		shell(value.equals("null") ? "settings delete " + table + " " + key : "settings put " + table + " " + key + " " + value);
	}

	private static String shell(String command) throws IOException {
		android.os.ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
		try (InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			int count;
			while ((count = in.read(buffer)) != -1)
				out.write(buffer, 0, count);
			return out.toString("UTF-8");
		}
	}

	private static void capture(String name) throws IOException {
		Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
		File folder = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "task10-screens");
		assertTrue(folder.isDirectory() || folder.mkdirs());
		try (OutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
			assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		} finally {
			bitmap.recycle();
		}
	}
}
