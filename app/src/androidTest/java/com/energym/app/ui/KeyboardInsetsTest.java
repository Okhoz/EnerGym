package com.energym.app.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * Regresión real de IME: el formulario y sus acciones deben caber por encima del teclado.
 */
@RunWith(AndroidJUnit4.class)
public class KeyboardInsetsTest {
	@Test
	public void focusedProfileFieldAndSaveActionRemainReachableWithIme() throws Exception {
		String original = shell("settings get secure show_ime_with_hard_keyboard").trim();
		shell("settings put secure show_ime_with_hard_keyboard 1");
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			MainFlowTest.click(scenario, "Cuenta");
			boolean[] enter = {false};
			scenario.onActivity(
				a -> enter[0] = MainFlowTest.text(a.getWindow().getDecorView(), "Entrar en demo local") != null);
			if (enter[0])
				MainFlowTest.click(scenario, "Entrar en demo local");
			scenario.onActivity(a -> a.findViewById(R.id.back).performClick());
			MainFlowTest.await();
			// El perfil se abre desde el menú ☰ aunque ya exista uno guardado.
			scenario.onActivity(a -> a.menuSelected(R.id.menu_profile));
			MainFlowTest.await();
			scenario.onActivity(a -> {
				EditText field = a.getWindow().getDecorView().findViewWithTag("preferences");
				field.requestFocus();
				((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(field,
					InputMethodManager.SHOW_IMPLICIT);
			});
			boolean[] ime = {false};
			for (int i = 0; i < 30 && !ime[0]; i++) {
				MainFlowTest.await();
				scenario.onActivity(a -> {
					WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(a.findViewById(R.id.main));
					ime[0] = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
				});
			}
			assertTrue("El teclado real no se abrió", ime[0]);
			MainFlowTest.await();
			scenario.onActivity(a -> {
				View root = a.findViewById(R.id.main), content = a.findViewById(R.id.content), nav =
					a.findViewById(R.id.navigation);
				WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
				float density = a.getResources().getDisplayMetrics().density;
				String sizes = "window=" + a.getWindow().getDecorView().getHeight() + " root=" + root.getHeight() +
					" rootPadding=" + root.getPaddingBottom() + " content=" + content.getHeight() + " nav=" +
					nav.getHeight() + " navPadding=" + nav.getPaddingBottom() + " ime=" +
					insets.getInsets(WindowInsetsCompat.Type.ime()).bottom + " system=" +
					insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
				android.util.Log.i("EnerGymInsets", sizes);
				assertTrue("Formulario colapsado: " + sizes, content.getHeight() > 120 * density);
				Rect visible = new Rect();
				View field = a.getWindow().getDecorView().findViewWithTag("preferences");
				assertTrue("Campo enfocado oculto", field.getGlobalVisibleRect(visible));
				assertTrue("Campo enfocado recortado", visible.height() > 24 * density);
				assertTrue("Navegación con margen IME duplicado: " + sizes, nav.getHeight() < 200 * density);
				ScrollView scroll = (ScrollView) a.findViewById(R.id.screen_body).getParent();
				scroll.fullScroll(View.FOCUS_DOWN);
			});
			MainFlowTest.await();
			scenario.onActivity(a -> {
				assertTrue("El teclado se cerró durante el desplazamiento",
					ViewCompat.getRootWindowInsets(a.findViewById(R.id.main)).isVisible(WindowInsetsCompat.Type.ime()));
				View save = MainFlowTest.text(a.getWindow().getDecorView(), "Guardar perfil");
				Rect visible = new Rect();
				assertNotNull(save);
				assertTrue("Guardar perfil no se puede alcanzar con el teclado abierto",
					save.getGlobalVisibleRect(visible));
				assertTrue("Guardar perfil recortado",
					visible.height() > 40 * a.getResources().getDisplayMetrics().density);
			});
		} finally {
			shell(original.equals("null") ? "settings delete secure show_ime_with_hard_keyboard" :
				"settings put secure show_ime_with_hard_keyboard " + original);
		}
	}

	private static String shell(String command) throws Exception {
		android.os.ParcelFileDescriptor descriptor =
			InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
		try (InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
			descriptor); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			byte[] bytes = new byte[1024];
			int count;
			while ((count = in.read(bytes)) != -1)
				out.write(bytes, 0, count);
			return out.toString("UTF-8");
		}
	}
}

