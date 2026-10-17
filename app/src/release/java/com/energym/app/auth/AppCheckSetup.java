package com.energym.app.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * Publicación: App Check con Play Integrity. Firebase solo atiende a la app original instalada desde Google Play
 * (cuando se active la exigencia en la consola); una copia modificada o un script no puede usar la base de datos.
 */
final class AppCheckSetup {
	private AppCheckSetup() {
	}

	static void install(FirebaseApp app) {
		FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
	}
}
