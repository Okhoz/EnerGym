// Configuración compartida de plugins; cada módulo conserva sus dependencias y opciones propias.
plugins {
	alias(libs.plugins.android.application) apply false
	id("com.google.gms.google-services") version "4.5.0" apply false
}
