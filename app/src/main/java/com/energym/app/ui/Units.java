package com.energym.app.ui;

import android.content.Context;

/** Preferencia de unidad de peso del registro de entreno (kg o lb), guardada en este móvil. */
public final class Units {
	private Units() {
	}

	public static boolean lb(Context c) {
		return c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("pounds", false);
	}

	public static void setLb(Context c, boolean pounds) {
		c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("pounds", pounds).apply();
	}
}
