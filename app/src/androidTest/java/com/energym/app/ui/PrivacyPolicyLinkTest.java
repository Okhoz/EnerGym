package com.energym.app.ui;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.energym.app.MainActivity;
import com.energym.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/** Cuenta › Preferencias abre la política de privacidad publicada, con o sin cuenta (lo pide Google Play). */
@RunWith(AndroidJUnit4.class)
public final class PrivacyPolicyLinkTest {
	@Test
	public void preferencesOpenThePublishedPrivacyPolicy() {
		Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
		IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
		filter.addDataScheme("https");
		// Bloqueante: se registra la apertura sin lanzar de verdad el navegador.
		Instrumentation.ActivityMonitor browser = instrumentation.addMonitor(filter, null, true);
		try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
			MainFlowTest.await();
			s.onActivity(a -> a.menuSelected(R.id.menu_account));
			MainFlowTest.await();
			MainFlowTest.setting(s, "preferences");
			s.onActivity(a -> {
				View link = a.getWindow().getDecorView().findViewWithTag("privacy_policy");
				assertNotNull("Enlace a la política", link);
				assertTrue(link.isEnabled());
				link.performClick();
			});
			MainFlowTest.await();
			assertEquals(1, browser.getHits());
		} finally {
			instrumentation.removeMonitor(browser);
		}
	}
}
