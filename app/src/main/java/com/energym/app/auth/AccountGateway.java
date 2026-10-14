package com.energym.app.auth;

/** MODEL de autenticación: ningún controlador acepta correo/Google subject como propietario. */
public interface AccountGateway {
    interface Callback { void onSuccess(AccountUser user); void onFailure(Exception error); }
    interface Listener { void onAccountChanged(AccountUser user); }
    interface Subscription { void close(); }
    boolean isConfigured();
    AccountUser getCurrentUser();
    void signInWithGoogleIdToken(String token,Callback callback);
    void signOut();
    Subscription observe(Listener listener);
}
