package com.energym.app.health;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicación que Android muestra al pedir los permisos de Health Connect: qué se envía y para qué. */
public final class HealthPrivacyActivity extends Activity {
	@Override
	protected void onCreate(Bundle saved) {
		super.onCreate(saved);
		int pad = (int) (24 * getResources().getDisplayMetrics().density);
		TextView text = new TextView(this);
		text.setPadding(pad, pad, pad, pad);
		text.setTextSize(16);
		text.setText("EnerGym y Health Connect\n\n" +
			"Si lo activas en Cuenta, EnerGym escribe en Health Connect tus entrenos terminados (hora de inicio y fin y " +
			"nombre de la rutina) y tu peso anotado, para que los veas en otras apps de salud.\n\n" +
			"EnerGym no lee datos de Health Connect ni los envía a ningún servidor. Puedes quitar el permiso cuando " +
			"quieras desde los ajustes de Health Connect.");
		ScrollView scroll = new ScrollView(this);
		scroll.addView(text);
		setContentView(scroll);
	}
}
