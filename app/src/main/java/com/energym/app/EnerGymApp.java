package com.energym.app;

import android.app.Application;
import android.content.res.Configuration;

import com.energym.app.i18n.Txt;

public class EnerGymApp extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		Txt.init(getResources());
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Txt.init(getResources());
	}
}
