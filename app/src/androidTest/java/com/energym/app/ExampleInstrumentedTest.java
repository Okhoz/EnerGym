package com.energym.app;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Prueba instrumental del contexto de la aplicación en un dispositivo Android.
 *
 * @see <a href="http://d.android.com/tools/testing">Documentación de pruebas</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
	@Test
	public void useAppContext() {
		// Contexto de la aplicación comprobada, distinto del contexto del runner.
		Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
		assertEquals("com.energym.app", appContext.getPackageName());
	}
}
