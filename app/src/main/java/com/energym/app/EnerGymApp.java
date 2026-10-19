package com.energym.app;

import android.app.Application;
import android.app.LocaleManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppLocalesMetadataHolderService;

import com.energym.app.i18n.Txt;

public class EnerGymApp extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		keepSystemLanguage();
		Txt.init(getResources());
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Txt.init(getResources());
	}

	/**
	 * En Android 13 o posterior el idioma de la app lo guarda el sistema. AppCompat (autoStoreLocales) le pasa una sola
	 * vez el que guardó en Android 12 o anterior al abrir la primera pantalla, pero sin ninguna abierta no ve el que ya
	 * tenga el sistema y lo pisa: recién instalada borraría el elegido en Ajustes o restaurado de una copia. Si el
	 * sistema ya tiene uno, esa copia se da por hecha; si no, AppCompat la hace como siempre.
	 */
	private void keepSystemLanguage() {
		if (Build.VERSION.SDK_INT < 33)
			return;
		ComponentName copied = new ComponentName(this, AppLocalesMetadataHolderService.class);
		PackageManager packages = getPackageManager();
		if (packages.getComponentEnabledSetting(copied) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
			&& !getSystemService(LocaleManager.class).getApplicationLocales().isEmpty())
			packages.setComponentEnabledSetting(copied, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
				PackageManager.DONT_KILL_APP);
	}
}
