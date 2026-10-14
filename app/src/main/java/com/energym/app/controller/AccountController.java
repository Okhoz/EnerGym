package com.energym.app.controller;

import com.energym.app.auth.*;
import com.energym.app.BuildConfig;

import android.app.Activity;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * CONTROLADOR MVC: coordina identidad/scope sin conocer componentes de la vista.
 */
public final class AccountController {
	public enum Status {CONFIGURATION_NEEDED, SIGNED_OUT, SIGNING_IN, AUTHENTICATED, DEMO_LOCAL, ERROR}

	public interface SessionTarget {
		void authenticated(AccountUser user);

		void demo();

		void signedOut();
	}

	public interface Listener {
		void onAccount(Status status, AccountUser user, Exception error);
	}

	private final AccountGateway gateway;
	private final SessionTarget target;
	private final Executor delivery;
	private final Listener listener;
	private final boolean demoEnabled;
	private AccountGateway.Subscription subscription;
	private long generation;
	private boolean closed, demo, applied;
	private String scope;

	public AccountController(AccountGateway gateway, SessionTarget target, Executor delivery, Listener listener) {
		this(gateway, target, delivery, listener, true);
	}

	AccountController(AccountGateway gateway, SessionTarget target, Executor delivery, Listener listener,
		boolean allowDemo) {
		this.gateway = Objects.requireNonNull(gateway);
		this.target = Objects.requireNonNull(target);
		this.delivery = Objects.requireNonNull(delivery);
		this.listener = Objects.requireNonNull(listener);
		demoEnabled = BuildConfig.DEMO_ENABLED && allowDemo;
	}

	public boolean isDemoEnabled() {
		return demoEnabled;
	}

	/**
	 * Restaura la identidad Firebase persistida; nunca crea una cuenta ficticia si falta configuración.
	 */
	public synchronized void start() {
		if (closed || subscription != null)
			return;
		if (!gateway.isConfigured()) {
			target.signedOut();
			emit(Status.CONFIGURATION_NEEDED, null, null);
			return;
		}
		subscription = gateway.observe(user -> {
			synchronized (AccountController.this) {
				if (closed || demo)
					return;
				AccountUser authoritative = gateway.getCurrentUser();
				if (user != null && (authoritative == null || !user.getUid().equals(authoritative.getUid())))
					return;
				apply(authoritative);
			}
		});
		apply(gateway.getCurrentUser());
	}

	private void apply(AccountUser user) {
		apply(user, false);
	}

	private void apply(AccountUser user, boolean resumeBackup) {
		String next = user == null ? null : user.getUid();
		boolean changed = !applied || !Objects.equals(scope, next);
		if (changed) {
			generation++;
			scope = next;
			applied = true;
		}
		// Tras reautenticar, incluso el mismo UID puede tener un ciclo cancelado por identidad oculta.
		// SyncController reutiliza su SQLite actual y solicita restauración; no mezcla ni borra cuentas.
		if (changed || resumeBackup) {
			if (user == null)
				target.signedOut();
			else
				target.authenticated(user);
		}
		emit(user == null ? Status.SIGNED_OUT : Status.AUTHENTICATED, user, null);
	}

	/**
	 * Entrada recomendada para Task4: el controlador invalida también el selector Google al salir/cambiar demo.
	 */
	public synchronized void signInWithGoogle(Activity activity, boolean authorizedOnly) {
		if (closed)
			return;
		if (!gateway.isConfigured()) {
			emit(Status.CONFIGURATION_NEEDED, null, null);
			return;
		}
		if (!(gateway instanceof GoogleAccountGateway)) {
			emit(Status.ERROR, null, new IllegalStateException("Proveedor Google no disponible"));
			return;
		}
		demo = false;
		long request = ++generation;
		emit(Status.SIGNING_IN, null, null);
		((GoogleAccountGateway) gateway).requestGoogleIdToken(activity, authorizedOnly, new GoogleAccountGateway.TokenCallback() {
			public void onToken(String token) {
				synchronized (AccountController.this) {
					if (!closed && request == generation)
						signInWithGoogleIdToken(token);
				}
			}

			public void onFailure(Exception error) {
				synchronized (AccountController.this) {
					if (!closed && request == generation)
						emit(Status.ERROR, gateway.getCurrentUser(), error);
				}
			}
		});
	}

	/**
	 * El token solo se intercambia; la identidad resultante se vuelve a leer desde Firebase.
	 */
	public synchronized void signInWithGoogleIdToken(String token) {
		if (closed)
			return;
		if (!gateway.isConfigured()) {
			emit(Status.CONFIGURATION_NEEDED, null, null);
			return;
		}
		demo = false;
		final long request = ++generation;
		emit(Status.SIGNING_IN, null, null);
		gateway.signInWithGoogleIdToken(token, new AccountGateway.Callback() {
			public void onSuccess(AccountUser ignored) {
				synchronized (AccountController.this) {
					if (closed || request != generation)
						return;
					AccountUser current = gateway.getCurrentUser();
					if (current == null) {
						emit(Status.ERROR, null, new IllegalStateException("Firebase no tiene una sesión autenticada"));
						return;
					}
					apply(current, true);
				}
			}

			public void onFailure(Exception error) {
				synchronized (AccountController.this) {
					if (!closed && request == generation) {
						AccountUser current = gateway.getCurrentUser();
						apply(current, true);
						emit(Status.ERROR, current, error);
					}
				}
			}
		});
	}

	/**
	 * Invalida trabajo antes de llamar al proveedor para impedir que una respuesta antigua reabra SQLite.
	 */
	public synchronized void signOut() {
		if (closed)
			return;
		generation++;
		demo = false;
		applied = true;
		scope = null;
		target.signedOut();
		gateway.signOut();
		emit(gateway.isConfigured() ? Status.SIGNED_OUT : Status.CONFIGURATION_NEEDED, null, null);
	}

	/**
	 * Demo explícito sin copia automática a Google y sin promesa de respaldo remoto.
	 */
	public synchronized void enterDemo() {
		if (closed)
			return;
		if (!demoEnabled) {
			// Rechazar la entrada no cierra Google ni sustituye el dueño actual por un fixture.
			if (!gateway.isConfigured())
				emit(Status.CONFIGURATION_NEEDED, null, null);
			else
				apply(gateway.getCurrentUser());
			return;
		}
		generation++;
		demo = true;
		applied = true;
		scope = "demo:local";
		gateway.signOut();
		target.demo();
		emit(Status.DEMO_LOCAL, null, null);
	}

	private void emit(Status status, AccountUser user, Exception error) {
		long token = generation;
		delivery.execute(() -> {
			synchronized (AccountController.this) {
				if (!closed && token == generation)
					listener.onAccount(status, user, error);
			}
		});
	}

	/**
	 * Retirar al destruir el propietario de los controladores. No cierra sesión Firebase por sí solo.
	 */
	public synchronized void close() {
		if (closed)
			return;
		closed = true;
		generation++;
		if (subscription != null)
			subscription.close();
		target.signedOut();
		scope = null;
	}
}
