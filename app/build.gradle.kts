import com.google.gms.googleservices.GoogleServicesTask
import groovy.json.JsonSlurper
import java.net.URI
import java.security.KeyStore
import java.util.Properties
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

plugins {
	alias(libs.plugins.android.application)
}

// Las entradas privadas de release se suministran fuera del repositorio, nunca como -Pcontraseña.
val releaseFirebasePath = providers.environmentVariable("ENERGYM_FIREBASE_CONFIG").orElse(providers.gradleProperty("ENERGYM_FIREBASE_CONFIG")).orElse("").get()
val releaseBackendUrl = providers.environmentVariable("ENERGYM_BACKEND_URL").orElse(providers.gradleProperty("ENERGYM_BACKEND_URL")).orElse("").get()
val debugBackendUrl = releaseBackendUrl.trim().removeSuffix("/")
val releaseSigningPath = providers.environmentVariable("ENERGYM_RELEASE_SIGNING_PROPERTIES").orElse(providers.gradleProperty("ENERGYM_RELEASE_SIGNING_PROPERTIES")).orElse("").get()
val releaseFirebaseFile = releaseFirebasePath.takeIf { it.isNotBlank() }?.let { File(it) }
val releaseSigningFile = releaseSigningPath.takeIf { it.isNotBlank() }?.let { File(it) }
val releaseSigning = Properties().apply {
	if (releaseSigningFile?.isFile == true)
		runCatching { releaseSigningFile.inputStream().use { load(it) } }
}
val signingStore = releaseSigning.getProperty("storeFile")?.let {
	File(it).let { candidate -> if (candidate.isAbsolute) candidate else File(releaseSigningFile!!.parentFile, it) }
}

// Debug conserva su configuración opcional; release jamás reutiliza el JSON local como atajo.
if (file("google-services.json").isFile || releaseFirebaseFile?.isFile == true) {
	apply(plugin = "com.google.gms.google-services")
	tasks.withType<GoogleServicesTask>().configureEach {
		if (name.contains("Release"))
			googleServicesJsonFiles.set(listOfNotNull(releaseFirebaseFile))
		else if (!file("google-services.json").isFile)
			googleServicesJsonFiles.set(listOfNotNull(releaseFirebaseFile))
	}
} else
	// Un clon nuevo no trae el JSON (está en .gitignore): sin él la app compila, pero sin Google ni nube.
	logger.warn("EnerGym: falta app/google-services.json y ENERGYM_FIREBASE_CONFIG no apunta a uno; " +
		"«Continuar con Google» y la copia en la nube no funcionarán en esta compilación.")

@DisableCachingByDefault(because = "Verifica las entradas privadas en cada preparación de release")
abstract class ValidateReleaseConfiguration : DefaultTask() {
	@get:Input var firebasePath = ""
	@get:Input var backendUrl = ""
	@get:Input var signingPath = ""
	@get:Input var sourceRoot = ""
	@get:Input var injectedStoreFile = ""
	@get:Internal var injectedStoreType = ""
	@get:Internal var injectedStorePassword = ""
	@get:Internal var injectedKeyAlias = ""
	@get:Internal var injectedKeyPassword = ""

	@TaskAction
	fun validate() {
		val errors = mutableListOf<String>()
		fun externalFile(path: String, variable: String): File? {
			if (path.isBlank()) {
				errors.add("Falta $variable (ruta absoluta a un archivo privado fuera del proyecto).")
				return null
			}
			val candidate = File(path)
			if (!candidate.isAbsolute || !candidate.isFile || candidate.canonicalFile.toPath().startsWith(File(sourceRoot).canonicalFile.toPath())) {
				errors.add("$variable debe señalar un archivo existente fuera del proyecto mediante ruta absoluta.")
				return null
			}
			return candidate
		}
		val firebase = externalFile(firebasePath, "ENERGYM_FIREBASE_CONFIG")
		if (firebase != null) {
			try {
				val root = JsonSlurper().parse(firebase) as Map<*, *>
				val projectInfo = root["project_info"] as? Map<*, *>
				val clients = root["client"] as? List<*> ?: emptyList<Any>()
				val client = clients.filterIsInstance<Map<*, *>>().firstOrNull {
					val info = it["client_info"] as? Map<*, *>
					(info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.energym.app"
				}
				val info = client?.get("client_info") as? Map<*, *>
				val oauth = client?.get("oauth_client") as? List<*> ?: emptyList<Any>()
				val webClient = oauth.filterIsInstance<Map<*, *>>().any {
					(it["client_type"] as? Number)?.toInt() == 3 &&
						(it["client_id"] as? String)?.matches(Regex("[A-Za-z0-9-]+\\.apps\\.googleusercontent\\.com")) == true
				}
				val keys = client?.get("api_key") as? List<*> ?: emptyList<Any>()
				val hasApiKey = keys.filterIsInstance<Map<*, *>>().any { !(it["current_key"] as? String).isNullOrBlank() }
				if ((projectInfo?.get("project_id") as? String).isNullOrBlank() ||
					(projectInfo?.get("project_number") as? String)?.matches(Regex("[0-9]+")) != true ||
					(info?.get("mobilesdk_app_id") as? String).isNullOrBlank() || !webClient || !hasApiKey)
					errors.add("Firebase requiere proyecto, cliente Android com.energym.app, API key de cliente y OAuth web tipo 3.")
			} catch (_: Exception) {
				errors.add("ENERGYM_FIREBASE_CONFIG no contiene un JSON Firebase legible y válido.")
			}
		}
		val uri = runCatching { URI(backendUrl) }.getOrNull()
		val host = uri?.host?.lowercase().orEmpty()
		val reserved = host == "localhost" || host == "example.com" || host == "example.net" || host == "example.org" ||
			listOf(".localhost", ".local", ".internal", ".lan", ".home.arpa", ".test", ".invalid", ".example",
				".example.com", ".example.net", ".example.org").any { host.endsWith(it) }
		val dns = host.matches(Regex("(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) &&
			!host.substringAfterLast('.').all { it.isDigit() } && host.split('.').all { it.length <= 63 }
		// El entrenador usa la IA del móvil: el backend online es opcional; si se indica, debe ser HTTPS público.
		if (backendUrl.isNotBlank() && (uri?.scheme != "https" || !dns || reserved || uri.userInfo != null || uri.rawQuery != null ||
			uri.rawFragment != null || uri.port !in listOf(-1, 443) || uri.rawPath !in listOf("", "/")))
			errors.add("ENERGYM_BACKEND_URL requiere origen HTTPS público con dominio, sin IP/localhost, credenciales, ruta, query ni fragmento.")
		fun validateKey(store: File, storeType: String?, storePassword: String, alias: String, keyPassword: String) {
			if (!store.isFile || store.canonicalFile.toPath().startsWith(File(sourceRoot).canonicalFile.toPath())) {
				errors.add("El almacén de firma debe existir fuera del proyecto.")
				return
			}
			try {
				// Sin tipo indicado (asistente de Android Studio) se detecta solo: JKS o PKCS12.
				val keyStore = if (storeType.isNullOrBlank()) KeyStore.getInstance(store, storePassword.toCharArray())
					else KeyStore.getInstance(storeType).apply { store.inputStream().use { load(it, storePassword.toCharArray()) } }
				val key = keyStore.getKey(alias, keyPassword.toCharArray())
				if (key !is java.security.PrivateKey || keyStore.getCertificateChain(alias).isNullOrEmpty())
					errors.add("La firma requiere una clave privada y su cadena de certificados para el alias configurado.")
			} catch (_: Exception) {
				errors.add("No se pudo validar la firma privada: revisa archivo, tipo, alias y contraseñas fuera de los logs.")
			}
		}
		// «Generate Signed Bundle» de Android Studio inyecta su propia firma y tiene prioridad sobre el archivo privado.
		if (injectedStoreFile.isNotBlank()) {
			if (injectedStorePassword.isBlank() || injectedKeyAlias.isBlank() || injectedKeyPassword.isBlank())
				errors.add("La firma de Android Studio está incompleta: requiere almacén, contraseñas y alias.")
			else
				validateKey(File(injectedStoreFile), injectedStoreType, injectedStorePassword, injectedKeyAlias, injectedKeyPassword)
		} else {
			val signing = externalFile(signingPath, "ENERGYM_RELEASE_SIGNING_PROPERTIES")
			if (signing != null) {
				val properties = Properties().apply { runCatching { signing.inputStream().use { load(it) } } }
				val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword", "storeType")
				if (required.any { properties.getProperty(it).isNullOrBlank() }) {
					errors.add("Firma privada incompleta: requiere storeFile, storePassword, keyAlias, keyPassword y storeType.")
				} else {
					val path = properties.getProperty("storeFile")
					val store = File(path).let { if (it.isAbsolute) it else File(signing.parentFile, path) }
					validateKey(store, properties.getProperty("storeType"), properties.getProperty("storePassword"),
						properties.getProperty("keyAlias"), properties.getProperty("keyPassword"))
				}
			}
		}
		if (errors.isNotEmpty())
			throw GradleException("Release bloqueada por configuración:\n" + errors.joinToString("\n") { "- $it" })
		logger.lifecycle("Entradas de release validadas localmente; Google/Firebase, HTTPS y proveedor de IA gratuito reales siguen requiriendo verificación.")
	}
}

val validateReleaseConfiguration = tasks.register<ValidateReleaseConfiguration>("validateReleaseConfiguration") {
	group = "verification"
	description = "Bloquea release si faltan entradas privadas Firebase, HTTPS o firma. No realiza llamadas cloud."
	firebasePath = releaseFirebasePath
	backendUrl = releaseBackendUrl
	signingPath = releaseSigningPath
	sourceRoot = rootProject.projectDir.absolutePath
	injectedStoreFile = providers.gradleProperty("android.injected.signing.store.file").orElse("").get()
	injectedStoreType = providers.gradleProperty("android.injected.signing.store.type").orElse("").get()
	injectedStorePassword = providers.gradleProperty("android.injected.signing.store.password").orElse("").get()
	injectedKeyAlias = providers.gradleProperty("android.injected.signing.key.alias").orElse("").get()
	injectedKeyPassword = providers.gradleProperty("android.injected.signing.key.password").orElse("").get()
}

@DisableCachingByDefault(because = "Valida el origen online opcional de cada APK debug")
abstract class ValidateDebugBackendConfiguration : DefaultTask() {
	@get:Input var backendUrl = ""

	@TaskAction
	fun validate() {
		if (backendUrl.isBlank())
			return
		val uri = runCatching { URI(backendUrl) }.getOrNull()
		val host = uri?.host?.lowercase().orEmpty()
		val reserved = host == "localhost" || host == "example.com" || host == "example.net" || host == "example.org" ||
			listOf(".localhost", ".local", ".internal", ".lan", ".home.arpa", ".test", ".invalid", ".example",
				".example.com", ".example.net", ".example.org").any { host.endsWith(it) }
		val dns = host.matches(Regex("(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) &&
			!host.substringAfterLast('.').all { it.isDigit() } && host.split('.').all { it.length <= 63 }
		if (uri?.scheme != "https" || !dns || reserved || uri.userInfo != null || uri.rawQuery != null ||
			uri.rawFragment != null || uri.port !in listOf(-1, 443) || uri.rawPath != "")
			throw GradleException("ENERGYM_BACKEND_URL debug requiere origen HTTPS público con dominio, sin IP/localhost, credenciales, ruta, query ni fragmento.")
	}
}

val validateDebugBackendConfiguration = tasks.register<ValidateDebugBackendConfiguration>("validateDebugBackendConfiguration") {
	group = "verification"
	description = "Valida la URL online opcional de debug; vacía conserva el emparejamiento local."
	backendUrl = debugBackendUrl
}
tasks.configureEach {
	if (name == "preReleaseBuild" || name == "assembleRelease" || name == "bundleRelease" ||
		name == "processReleaseGoogleServices" || name == "validateSigningRelease")
		dependsOn(validateReleaseConfiguration)
	if (name == "preDebugBuild" || name == "generateDebugBuildConfig")
		dependsOn(validateDebugBackendConfiguration)
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

		// Fija la app en español durante los tests de interfaz (comparan sus textos).
		testInstrumentationRunner = "com.energym.app.EnerGymTestRunner"
	}
	buildFeatures {
		buildConfig = true
	}
	signingConfigs {
		create("privateRelease") {
			storeFile = signingStore
			storePassword = releaseSigning.getProperty("storePassword")
			keyAlias = releaseSigning.getProperty("keyAlias")
			keyPassword = releaseSigning.getProperty("keyPassword")
			storeType = releaseSigning.getProperty("storeType", "JKS")
		}
	}

	buildTypes {
		debug {
			buildConfigField("boolean", "DEMO_ENABLED", "true")
			buildConfigField("String", "BACKEND_BASE_URL", "\"${debugBackendUrl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")}\"")
		}
		release {
			buildConfigField("boolean", "DEMO_ENABLED", "false")
			buildConfigField("String", "BACKEND_BASE_URL", "\"${releaseBackendUrl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")}\"")
			signingConfig = signingConfigs.getByName("privateRelease")
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
	// App Check: Play Integrity en publicación y token de prueba en depuración.
	implementation("com.google.firebase:firebase-appcheck-playintegrity")
	debugImplementation("com.google.firebase:firebase-appcheck-debug")
	implementation("androidx.credentials:credentials:1.3.0")
	implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
	implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
	implementation(libs.activity.ktx)
	implementation(libs.appcompat)
	implementation(libs.constraintlayout)
	implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
	// Escáner de Google Play services: la cámara la gestiona el sistema, la app no pide permiso de cámara.
	implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
	// Reloj Wear OS: el móvil publica el entreno en curso y recibe «otra serie igual» desde el reloj.
	implementation("com.google.android.gms:play-services-wearable:18.2.0")
	// Gemini Nano en el propio móvil (AICore): el chat funciona sin servidor cuando el dispositivo lo admite.
	implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
	// Sin Gemini Nano: Gemma 4 E2B (Apache 2.0) descargado una vez y ejecutado con LiteRT-LM en el móvil.
	implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
	implementation(libs.material)
	testImplementation(libs.junit)
	androidTestImplementation(libs.espresso.core)
	androidTestImplementation(libs.ext.junit)
}
