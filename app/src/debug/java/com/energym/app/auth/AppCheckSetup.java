package com.energym.app.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

/**
 * Depuración: App Check con token de prueba. El token aparece en logcat («DebugAppCheckProvider») y se registra en
 * la consola de Firebase para que las compilaciones de desarrollo sigan funcionando cuando se exija App Check.
 */
final class AppCheckSetup {
	private AppCheckSetup() {
	}

	static void install(FirebaseApp app) {
		FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
	}
}
