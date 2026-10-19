package com.energym.app;

import android.app.LocaleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.runner.AndroidJUnitRunner;

/**
 * Los tests de interfaz comparan los textos en español: mientras duran, la app se fija en español aunque el
 * móvil o el emulador estén en otro idioma, y al acabar vuelve al idioma que tenía.
 */
public final class EnerGymTestRunner extends AndroidJUnitRunner {
	private static final String SPANISH = "es";
	private LocaleList previous;
	private LocaleListCompat previousCompat;

	@Override
	public void onStart() {
		Context app = getTargetContext();
		if (Build.VERSION.SDK_INT >= 33) {
			// AppCompat (autoStoreLocales) pasa al sistema su idioma guardado al abrir la primera pantalla tras instalar;
			// recién instalada no tiene ninguno y borraría el español. Esa copia se da por hecha.
			app.getPackageManager().setComponentEnabledSetting(
				new ComponentName(app, "androidx.appcompat.app.AppLocalesMetadataHolderService"),
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
			// Sin ninguna actividad abierta AppCompat no llega al LocaleManager: se usa directamente.
			LocaleManager locales = app.getSystemService(LocaleManager.class);
			previous = locales.getApplicationLocales();
			locales.setApplicationLocales(LocaleList.forLanguageTags(SPANISH));
			// El sistema aplica el cambio de forma asíncrona.
			for (int i = 0; i < 50 && !SPANISH.equals(language(app)); i++)
				SystemClock.sleep(100);
		} else
			runOnMainSync(() -> {
				previousCompat = AppCompatDelegate.getApplicationLocales();
				AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(SPANISH));
			});
		super.onStart();
	}

	@Override
	public void finish(int resultCode, Bundle results) {
		if (previous != null)
			getTargetContext().getSystemService(LocaleManager.class).setApplicationLocales(previous);
		else if (previousCompat != null)
			runOnMainSync(() -> AppCompatDelegate.setApplicationLocales(previousCompat));
		super.finish(resultCode, results);
	}

	private static String language(Context context) {
		return context.getResources().getConfiguration().getLocales().get(0).getLanguage();
	}
}
