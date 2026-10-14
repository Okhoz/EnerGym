package com.energym.app.visual;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Única consulta privada entre procesos del mismo UID, sin datos de perfil ni órdenes nativas. */
public final class ViewerLeaseProvider extends ContentProvider {
	@Override public boolean onCreate() { return true; }
	@Override public Bundle call(String method, String arg, Bundle extras) {
		if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("UID ajeno");
		Bundle result = new Bundle();
		result.putBoolean("valid", "validate".equals(method) && extras != null && ViewerLeaseRegistry.valid(arg, extras.getString("exerciseId")));
		return result;
	}
	public static boolean valid(Context context, String lease, String id) {
		if (lease == null || id == null) return false;
		try {
			Bundle extras = new Bundle(); extras.putString("exerciseId", id);
			Bundle reply = context.getContentResolver().call(Uri.parse("content://" + context.getPackageName() + ".viewer-lease"), "validate", lease, extras);
			return reply != null && reply.getBoolean("valid", false);
		} catch (RuntimeException e) { return false; }
	}
	@Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
	@Override public String getType(Uri uri) { return null; }
	@Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
	@Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
	@Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
