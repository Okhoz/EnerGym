plugins {
	alias(libs.plugins.android.application)
}

// App del reloj (Wear OS): mismo applicationId que el móvil para que el Data Layer los empareje.
android {
	namespace = "com.energym.wear"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.energym.app"
		minSdk = 30
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

dependencies {
	implementation("com.google.android.gms:play-services-wearable:18.2.0")
	testImplementation(libs.junit)
}
