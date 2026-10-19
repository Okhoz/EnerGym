package com.energym.app.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController;
import com.energym.app.ui.effects.GlassButton;
import com.energym.app.ui.effects.GlassPerformancePolicy;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Regresión de presentación: límites reales, cancelación de resortes y navegación sin reanimar datos.
 */
@RunWith(AndroidJUnit4.class)
public final class GlassMotionTest {
	@Test
	public void backdropCollectorIsBoundedAndLifecycleUnregistersObservers() {
		Object[] controller = {null};
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			scenario.onActivity(a -> {
				controller[0] = field(a, "backdrop");
				GlassPerformancePolicy.FrameBudget budget =
					(GlassPerformancePolicy.FrameBudget) field(controller[0], "budget");
				assertTrue(budget.sampleCount() <= 32);
				assertEquals(true, field(controller[0], "observing"));
				android.util.Log.i("EnerGymGlassTest", "collector samples=" + budget.sampleCount() + " fallback=" +
					field(controller[0], "memoryFallback") + " cachedEffect=" +
					(field(controller[0], "cachedEffect") != null));
			});
			scenario.moveToState(Lifecycle.State.CREATED);
			InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
				assertEquals(false, field(controller[0], "listening"));
				assertEquals(false, field(controller[0], "observing"));
				assertEquals(false, field(controller[0], "resumed"));
			});
			scenario.moveToState(Lifecycle.State.RESUMED);
			MainFlowTest.await();
			scenario.onActivity(a -> a.onLowMemory());
			InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
				assertEquals(false, field(controller[0], "listening"));
				assertNull(field(controller[0], "cachedEffect"));
			});
		}
		InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
			assertEquals(true, field(controller[0], "closed"));
			assertEquals(false, field(controller[0], "observing"));
			assertNull(field(controller[0], "cachedEffect"));
		});
	}

	private static Object field(Object target, String name) {
		try {
			java.lang.reflect.Field value = target.getClass().getDeclaredField(name);
			value.setAccessible(true);
			return value.get(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void decorationIsBoundedAndPreviewRemainsExcludedThroughNavigation() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			scenario.onActivity(a -> {
				ViewGroup rows =
					a.getSupportFragmentManager().findFragmentById(R.id.content).getView().findViewById(R.id.rows);
				// Inicio sin cuenta tiene una sola tarjeta («Siguiente paso») desde que las cifras están en Progreso.
				assertTrue("Tarjetas runtime ausentes", rows.getChildCount() >= 1);
				View card = rows.getChildAt(0);
				assertTrue("Tarjeta sin medida: visibility=" + card.getVisibility() + " height=" + card.getHeight() +
					" measured=" + card.getMeasuredHeight(), card.getHeight() > 0);
				View decoration = a.findViewById(R.id.glass_decoration);
				assertTrue(decoration.getWidth() <= 256);
				assertTrue(decoration.getHeight() <= 512);
				assertFalse(decoration.isClickable());
				assertEquals(View.GONE, a.findViewById(R.id.home_preview).getVisibility());
				a.state().app.navigate(AppController.Screen.TRAINING);
			});
			MainFlowTest.await();
			scenario.onActivity(a -> {
				assertEquals(View.GONE, a.findViewById(R.id.home_preview).getVisibility());
				assertTrue(a.findViewById(R.id.content).getHeight() > 0);
			});
		}
	}

	@Test
	public void detachCancelsSpringAndClickSemanticsRemainIntact() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			scenario.onActivity(a -> {
				ViewGroup root = a.findViewById(R.id.main);
				GlassButton button = new GlassButton(a);
				int[] clicks = {0};
				button.setOnClickListener(v -> clicks[0]++);
				root.addView(button, new ViewGroup.LayoutParams(100, 60));
				button.setPressed(true);
				assertTrue(button.performClick());
				assertEquals(1, clicks[0]);
				root.removeView(button);
				assertEquals(1f, button.getScaleX(), 0f);
				assertEquals(1f, button.getScaleY(), 0f);
			});
		}
	}

	@Test
	public void dataRenderDoesNotRestartNavigationTransitionAndPauseSettlesIt() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			scenario.onActivity(a -> {
				MotionLayout motion = a.findViewById(R.id.screen_motion);
				motion.setProgress(.4f);
			});
			// MotionLayout publica el progreso al dibujar, no dentro de setProgress.
			MainFlowTest.await();
			scenario.onActivity(a -> {
				MotionLayout motion = a.findViewById(R.id.screen_motion);
				assertEquals(.4f, motion.getProgress(), .001f);
				androidx.fragment.app.Fragment current = a.getSupportFragmentManager().findFragmentById(R.id.content);
				AppController.Screen route = a.state().app.screen;
				ViewGroup rows = current.getView().findViewById(R.id.rows);
				View previousCard = rows.getChildAt(0);
				// El cambio MVC notifica AppState y atraviesa MainActivity.render en la misma ruta.
				a.state().app.notice = "Actualización local de prueba";
				a.state().app.changed();
				assertEquals(route, a.state().app.screen);
				assertSame(current, a.getSupportFragmentManager().findFragmentById(R.id.content));
				assertNotSame("La vista no recibió la actualización MVC", previousCard, rows.getChildAt(0));
				assertEquals(.4f, motion.getProgress(), .001f);
			});
			MainFlowTest.await();
			scenario.onActivity(a -> {
				assertEquals(.4f, ((MotionLayout) a.findViewById(R.id.screen_motion)).getProgress(), .001f);
				ViewGroup rows =
					a.getSupportFragmentManager().findFragmentById(R.id.content).getView().findViewById(R.id.rows);
				assertTrue("Tarjetas ausentes después de actualizar datos", rows.getChildCount() >= 1);
				assertTrue("Tarjeta actualizada sin medida", rows.getChildAt(0).getHeight() > 0);
			});
			scenario.moveToState(Lifecycle.State.CREATED);
			scenario.moveToState(Lifecycle.State.RESUMED);
			MainFlowTest.await();
			scenario.onActivity(
				a -> assertEquals(1f, ((MotionLayout) a.findViewById(R.id.screen_motion)).getProgress(), 0f));
			scenario.recreate();
			MainFlowTest.await();
			scenario.onActivity(a -> assertEquals(View.GONE, a.findViewById(R.id.home_preview).getVisibility()));
		}
	}
}
