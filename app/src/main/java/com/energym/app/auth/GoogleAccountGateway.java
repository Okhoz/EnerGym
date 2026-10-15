package com.energym.app.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;

import androidx.credentials.*;
import androidx.credentials.exceptions.*;

import com.google.android.libraries.identity.googleid.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;

import java.util.concurrent.Executor;

/**
 * MODEL Android: Credential Manager obtiene el token y Firebase autentica la identidad final.
 * La ausencia de google-services.json/client web se representa sin inicializar servicios de nube.
 */
public final class GoogleAccountGateway implements AccountGateway, GoogleTokenSource {
	private final CredentialManager credentials;
	private final Executor callbacks;
	private final FirebaseAuth auth;
	private final String webClientId;
	private long generation;
	private boolean authenticating;
	private CancellationSignal credentialCancellation;

	public GoogleAccountGateway(Context context, Executor callbacks) {
		Context app = context.getApplicationContext();
		this.callbacks = java.util.Objects.requireNonNull(callbacks);
		credentials = CredentialManager.create(app);
		int resource = app.getResources().getIdentifier("default_web_client_id", "string", app.getPackageName());
		webClientId = resource == 0 ? "" : app.getString(resource);
		FirebaseApp firebase = FirebaseApp.initializeApp(app);
		auth = firebase == null || webClientId.isEmpty() ? null : FirebaseAuth.getInstance(firebase);
	}

	public boolean isConfigured() {
		return auth != null;
	}

	public synchronized AccountUser getCurrentUser() {
		return auth == null || authenticating ? null : fromFirebase(auth.getCurrentUser());
	}

	private static AccountUser fromFirebase(FirebaseUser user) {
		return user == null ? null : new AccountUser(user.getUid(), user.getDisplayName(), user.getEmail());
	}

	/**
	 * Task4 debe usar Activity como contexto para el selector del proveedor. false incluye cuentas nuevas.
	 * Un NoCredentialException con true permite repetir explícitamente con false; no activa auto-selección.
	 */
	public synchronized void requestGoogleIdToken(Activity activity, boolean authorizedOnly, TokenCallback callback) {
		if (!isConfigured()) {
			callback.onFailure(new IllegalStateException("Configura Firebase y el cliente OAuth web"));
			return;
		}
		if (credentialCancellation != null)
			credentialCancellation.cancel();
		long request = ++generation;
		credentialCancellation = new CancellationSignal();
		GetGoogleIdOption option = new GetGoogleIdOption.Builder().setServerClientId(webClientId).setFilterByAuthorizedAccounts(authorizedOnly).setAutoSelectEnabled(false).build();
		GetCredentialRequest credentialRequest = new GetCredentialRequest.Builder().addCredentialOption(option).build();
		credentials.getCredentialAsync(activity, credentialRequest, credentialCancellation, callbacks, new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
			public void onResult(GetCredentialResponse result) {
				synchronized (GoogleAccountGateway.this) {
					if (request != generation)
						return;
					credentialCancellation = null;
				}
				try {
					Credential credential = result.getCredential();
					if (!(credential instanceof CustomCredential) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType()))
						throw new IllegalArgumentException("El proveedor no devolvió una credencial Google");
					callback.onToken(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
				} catch (Exception e) {
					callback.onFailure(e);
				}
			}

			public void onError(GetCredentialException error) {
				synchronized (GoogleAccountGateway.this) {
					if (request != generation)
						return;
					credentialCancellation = null;
				}
				callback.onFailure(error);
			}
		});
	}

	/**
	 * Solo una autenticación en vuelo. Una respuesta tras signOut se revoca antes de publicar identidad.
	 */
	public synchronized void signInWithGoogleIdToken(String token, Callback callback) {
		if (auth == null) {
			callback.onFailure(new IllegalStateException("Firebase no configurado"));
			return;
		}
		if (token == null || token.trim().isEmpty()) {
			callback.onFailure(new IllegalArgumentException("Token Google vacío"));
			return;
		}
		if (authenticating) {
			callback.onFailure(new IllegalStateException("Ya hay una autenticación en curso"));
			return;
		}
		authenticating = true;
		long request = ++generation;
		auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).addOnCompleteListener(callbacks, task -> {
			AccountUser user;
			Exception error;
			synchronized (GoogleAccountGateway.this) {
				authenticating = false;
				if (request != generation) {
					auth.signOut();
					return;
				}
				user = task.isSuccessful() ? fromFirebase(auth.getCurrentUser()) : null;
				error = task.getException() == null ? new IllegalStateException("No se pudo autenticar") : task.getException();
			}
			// No mantener el monitor MODEL mientras el controlador consulta identidad o cambia SQLite.
			if (user != null)
				callback.onSuccess(user);
			else
				callback.onFailure(error);
		});
	}

	public synchronized void signOut() {
		generation++;
		if (credentialCancellation != null) {
			credentialCancellation.cancel();
			credentialCancellation = null;
		}
		if (auth != null)
			auth.signOut();
		// Limpiar el estado del proveedor permite elegir otra cuenta la próxima vez. Firebase ya está cerrado.
		credentials.clearCredentialStateAsync(new ClearCredentialStateRequest(), new CancellationSignal(), callbacks, new CredentialManagerCallback<Void, ClearCredentialException>() {
			public void onResult(Void value) {
			}

			public void onError(ClearCredentialException error) { /* No reabrir una sesión por un fallo del proveedor. */ }
		});
	}

	public Subscription observe(Listener listener) {
		if (auth == null) {
			listener.onAccountChanged(null);
			return () -> {
			};
		}
		final java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(true);
		FirebaseAuth.AuthStateListener observer = source -> {
			AccountUser user;
			synchronized (GoogleAccountGateway.this) {
				// Firebase puede emitir antes del callback final; nunca publicar una autenticación cancelada.
				if (!active.get() || authenticating)
					return;
				user = fromFirebase(source.getCurrentUser());
			}
			if (active.get())
				listener.onAccountChanged(user);
		};
		auth.addAuthStateListener(observer);
		return () -> {
			active.set(false);
			auth.removeAuthStateListener(observer);
		};
	}
}
