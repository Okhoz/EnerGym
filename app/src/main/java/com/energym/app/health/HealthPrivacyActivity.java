package com.energym.app.health;

import com.energym.app.i18n.Txt;
import com.energym.app.R;
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
		text.setText(Txt.t(R.string.hp_text));
		ScrollView scroll = new ScrollView(this);
		scroll.addView(text);
		setContentView(scroll);
	}
}
