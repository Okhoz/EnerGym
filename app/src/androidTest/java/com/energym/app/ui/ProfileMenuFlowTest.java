package com.energym.app.ui;

import android.view.View;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;
import com.energym.app.controller.AppController.Screen;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Comprueba el menú desplegable real sin cambiar la cuenta ni escribir datos personales. */
@RunWith(AndroidJUnit4.class)
public final class ProfileMenuFlowTest {

	@Test
	public void photoOpensMenuAndKeepsNavigationAfterRecreation() throws Exception {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			ready(scenario);
			String[] owner = {null};
			scenario.onActivity(activity -> {
				owner[0] = activity.state().app.owner();
				View avatar = activity.findViewById(R.id.avatar);
				assertTrue(avatar.isClickable());
				assertTrue(avatar.isFocusable());
				assertEquals(activity.getString(R.string.menu), avatar.getContentDescription());
			});

			scenario.onActivity(activity -> activity.findViewById(R.id.avatar).performClick());
			assertNotNull(menuItem(R.string.menu_profile));
			assertNotNull(menuItem(R.string.menu_body));
			clickMenuItem(R.string.menu_account);
			scenario.onActivity(activity -> {
				assertEquals(Screen.ACCOUNT, activity.state().app.screen);
				assertEquals(owner[0], activity.state().app.owner());
			});

			scenario.recreate();
			ready(scenario);
			scenario.onActivity(activity -> activity.findViewById(R.id.avatar).performClick());
			clickMenuItem(R.string.menu_profile);
			scenario.onActivity(activity -> {
				assertEquals(owner[0] == null ? Screen.ACCOUNT : Screen.PROFILE, activity.state().app.screen);
				assertEquals(owner[0], activity.state().app.owner());
				activity.state().app.navigate(Screen.HOME);
			});
		}
	}

	private static AccessibilityNodeInfo menuItem(int labelId) throws Exception {
		String label = InstrumentationRegistry.getInstrumentation().getTargetContext().getString(labelId);
		for (int attempt = 0; attempt < 60; attempt++) {
			InstrumentationRegistry.getInstrumentation().waitForIdleSync();
			AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
			if (root != null) {
				for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label))
					if (node.isVisibleToUser() && label.equals(node.getText() == null ? null : node.getText().toString())) return node;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Opción de menú no visible: " + label);
	}

	private static void clickMenuItem(int labelId) throws Exception {
		AccessibilityNodeInfo node = menuItem(labelId);
		Rect bounds = new Rect();
		node.getBoundsInScreen(bounds);
		assertFalse("La opción del menú debe estar dentro de la pantalla", bounds.isEmpty());
		long now = SystemClock.uptimeMillis();
		MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
		MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
		try {
			assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(down, true));
			assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(up, true));
		} finally {
			down.recycle();
			up.recycle();
		}
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
	}

	private static void ready(ActivityScenario<MainActivity> scenario) throws Exception {
		boolean[] loaded = {false};
		for (int attempt = 0; attempt < 100 && !loaded[0]; attempt++) {
			InstrumentationRegistry.getInstrumentation().waitForIdleSync();
			scenario.onActivity(activity -> loaded[0] = activity.state().app != null);
			if (!loaded[0]) Thread.sleep(50);
		}
		assertTrue("El controlador de navegación debe estar preparado", loaded[0]);
	}
}
