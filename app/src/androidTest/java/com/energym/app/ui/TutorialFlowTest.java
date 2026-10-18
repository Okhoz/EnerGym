package com.energym.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Tutorial del primer arranque: obligatorio (atrás no lo cierra), se recorre entero y queda marcado como hecho. */
@RunWith(AndroidJUnit4.class)
public class TutorialFlowTest {
	private Context context;

	@Before
	public void reset() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		context.getSharedPreferences("tutorial", Context.MODE_PRIVATE).edit().clear().commit();
	}

	@After
	public void restore() {
		context.getSharedPreferences("tutorial", Context.MODE_PRIVATE).edit().clear().commit();
	}

	private static View find(View root, String text) {
		if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
			return root;
		if (root instanceof android.view.ViewGroup)
			for (int i = 0; i < ((android.view.ViewGroup) root).getChildCount(); i++) {
				View v = find(((android.view.ViewGroup) root).getChildAt(i), text);
				if (v != null)
					return v;
			}
		return null;
	}

	@Test
	public void cannotBeSkippedAndFinishesAfterAllSteps() {
		assertFalse(TutorialActivity.done(context));
		try (ActivityScenario<TutorialActivity> scenario = ActivityScenario.launch(TutorialActivity.class)) {
			scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
			assertEquals("Atrás en el primer paso no sale", Lifecycle.State.RESUMED, scenario.getState());
			for (int i = 0; i < 8; i++)
				scenario.onActivity(a -> {
					View next = find(a.getWindow().getDecorView(), "Siguiente");
					assertNotNull(next);
					next.performClick();
				});
			scenario.onActivity(a -> {
				assertNotNull("Último paso", find(a.getWindow().getDecorView(), "TUTORIAL · PASO 9 DE 9"));
				a.getOnBackPressedDispatcher().onBackPressed();
				assertNotNull("Atrás retrocede un paso", find(a.getWindow().getDecorView(), "TUTORIAL · PASO 8 DE 9"));
				find(a.getWindow().getDecorView(), "Siguiente").performClick();
				find(a.getWindow().getDecorView(), "Empezar").performClick();
			});
			assertTrue(TutorialActivity.done(context));
			assertTrue("Al terminar, la app guía a rellenar perfil y datos físicos", TutorialActivity.setupPending(context));
		}
	}
}
