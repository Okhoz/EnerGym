package com.energym.app.assistant;

import com.google.firebase.auth.FirebaseUser;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Adaptador Firebase: obtiene el ID token de la identidad vigente y nunca lo persiste. */
public final class FirebaseChatTokenSource implements OnlineChatClient.TokenSource {
	private final Supplier<FirebaseUser> currentUser;
	private final Executor delivery;

	public FirebaseChatTokenSource(Supplier<FirebaseUser> currentUser, Executor delivery) {
		this.currentUser = Objects.requireNonNull(currentUser);
		this.delivery = Objects.requireNonNull(delivery);
	}

	public String currentUid() {
		FirebaseUser user = currentUser.get();
		return user == null ? null : user.getUid();
	}

	public void getToken(String expectedUid, ChatClient.Callback callback) {
		FirebaseUser user = currentUser.get();
		if (user == null || !user.getUid().equals(expectedUid)) {
			delivery.execute(() -> callback.failure("SIGN_IN_REQUIRED"));
			return;
		}
		try {
			user.getIdToken(false).addOnCompleteListener(delivery, task -> {
				if (!Objects.equals(expectedUid, currentUid())) {
					callback.failure("SIGN_IN_REQUIRED");
					return;
				}
				String token = task.isSuccessful() && task.getResult() != null ? task.getResult().getToken() : null;
				if (token == null || token.isEmpty())
					callback.failure("AUTHENTICATION_UNAVAILABLE");
				else
					callback.success(token);
			});
		} catch (RuntimeException failure) {
			delivery.execute(() -> callback.failure("AUTHENTICATION_UNAVAILABLE"));
		}
	}
}
