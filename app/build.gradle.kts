plugins {
	alias(libs.plugins.android.application)
}

// Sin proyecto Firebase aún: compila demo y expone configuración pendiente sin credenciales ficticias.
if (file("google-services.json").exists()) {
	apply(plugin = "com.google.gms.google-services")
}

android {
	namespace = "com.energym.app"
	compileSdk {
		version = release(37)
	}

	defaultConfig {
		applicationId = "com.energym.app"
		minSdk = 26
		targetSdk = 37
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			optimization {
				enable = true
				packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}

dependencies {
	// Visor Java/JNI nativo; versiones iguales, fuente propia Java 11.
	implementation("com.google.android.filament:filament-android:1.77.3")
	implementation("com.google.android.filament:gltfio-android:1.77.3")
	implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
	implementation("com.google.firebase:firebase-auth")
	implementation("com.google.firebase:firebase-firestore")
	implementation("androidx.credentials:credentials:1.3.0")
	implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
	implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
	implementation(libs.activity.ktx)
	implementation(libs.appcompat)
	implementation(libs.constraintlayout)
	implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
	implementation(libs.material)
	testImplementation(libs.junit)
	androidTestImplementation(libs.espresso.core)
	androidTestImplementation(libs.ext.junit)
}
