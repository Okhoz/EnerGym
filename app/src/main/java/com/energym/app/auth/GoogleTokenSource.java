package com.energym.app.auth;

import android.app.Activity;

/**
 * Puerto del selector Google: devuelve una credencial o su cancelación sin decidir el dueño de los datos.
 */
public interface GoogleTokenSource {
	interface TokenCallback {
		void onToken(String idToken);

		void onFailure(Exception error);
	}

	void requestGoogleIdToken(Activity activity, boolean authorizedOnly, TokenCallback callback);
}
