# Preparación privada de release

Estado 15-10-2026: Firebase debug ya está configurado y Groq tiene una prueba privada real; la autenticación del backend, el cliente automático, las cuotas durables, el despliegue HTTPS y la firma final siguen pendientes. [Revisión actual](review-2026-10-15.md).

Task14 prepara las guardas de build y la política de cuentas. No configura Firebase, OAuth, Firestore, dominio ni servidor real; no crea certificados ni publica artefactos. La solicitud vigente exige una IA gratuita sin activar pagos; se ha elegido Groq Free, comprobado en desarrollo privado; aún falta su integración autenticada de producción. Gemini queda como proveedor legado de desarrollo por el conflicto documentado entre su API gratuita sin billing y distribución en EEE; Docker no lo resuelve.

## Entradas

Configura las variables en el entorno privado del proceso o mediante el gestor de secretos del entorno de compilación. No introduzcas contraseñas como parámetros Gradle, en chat, historial de comandos o logs.

| Variable | Contenido |
| --- | --- |
| `ENERGYM_FIREBASE_CONFIG` | Ruta absoluta al `google-services.json` real descargado para la app Android `com.energym.app`, fuera del proyecto. Requiere proyecto, identificador de app, API key de cliente y cliente OAuth web tipo 3. |
| `ENERGYM_BACKEND_URL` | Origen HTTPS del backend propio, con dominio público, puerto 443 o implícito, sin ruta, credenciales, query o fragmento. No acepta IP, localhost o dominios reservados. |
| `ENERGYM_RELEASE_SIGNING_PROPERTIES` | Ruta absoluta al archivo privado de propiedades de firma, fuera del proyecto. |

El archivo de firma usa las claves `storeFile`, `storePassword`, `keyAlias`, `keyPassword` y `storeType`. `storeFile` es absoluto o relativo al archivo privado; el almacén debe estar fuera del proyecto. `storeType` identifica el formato real, por ejemplo JKS o PKCS12. Suministra una clave privada válida y su cadena de certificados. El build verifica que puede abrir el almacén y recuperar esa clave; los errores no imprimen valores ni excepciones del proveedor. Conserva copias privadas seguras de la firma: perderla puede impedir actualizar una distribución existente.

La configuración Firebase de cliente, incluyendo su API key, se convierte en recursos Android y forma parte de la APK según el diseño del SDK. No es una clave privada de servidor. Restringe esa API key para la app/API correspondientes y comprueba las reglas por UID y OAuth/SHA de la firma en la configuración real. Claves de servicio Firebase, tokens privados, claves del proveedor de IA y contraseñas nunca van en la app, el JSON de cliente ni Git.

## Validar y generar

Desde la raíz del proyecto, con JBR y SDK configurados:

```powershell
.\gradlew.bat :app:validateReleaseConfiguration
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

La validación se ejecuta de nuevo antes de preparar, empaquetar y firmar release. No existe una opción de omitirla para publicar. Los inputs ausentes producen un error concreto y no impiden `:app:testDebugUnitTest`, `:app:assembleDebug` o `:app:lintDebug`. El JSON opcional local `app/google-services.json` sigue disponible para desarrollo, pero no satisface el requisito privado de release. No se usan claves o dominios ficticios como valores por defecto.

Artefactos previstos, únicamente tras un build firmado correcto: `app/build/outputs/apk/release/app-release.apk` y `app/build/outputs/bundle/release/app-release.aab`. Descarta outputs antiguos de release en cualquier flujo de distribución que haya fallado: su presencia no prueba que se hayan generado con estas guardas. Los outputs y las cachés están excluidos de Git; limita el acceso a caché Gradle/configuration-cache porque el tooling de firma trabaja con datos privados. No subas cachés ni archivos de propiedades como artefactos públicos.

## Política de cuentas y límites de esta tarea

`BuildConfig.DEMO_ENABLED` es true sólo en debug y false en release. Cuenta y el texto de Inicio requieren Google en release; incluso una llamada directa a `AccountController.enterDemo()` no abre un dueño ficticio, no cierra una sesión Google vigente ni copia datos de debug. Los tests y sus fixtures siguen en los source sets de prueba/desarrollo. Las muestras `tools:` de XML sólo ayudan al editor y se eliminan del renderizado dinámico; no son cuentas reales.

La URL HTTPS se prepara como `BuildConfig.BACKEND_BASE_URL` de release, sin secretos. Task16 debe conectar el cliente fijo con ID tokens Firebase y retirar pairing/guiado local de la interfaz release. Task14 no simula esa conexión ni declara el asistente listo.

Validar archivos, sintaxis de dominio y firma no acredita propiedad del dominio, DNS, TLS, despliegue saludable, autenticación Google, SHA/OAuth, reglas Firestore o respaldo real. Antes de distribuir se requiere verificar esos servicios y restauración con la firma definitiva, revisar privacidad y límites y comprobar que el proveedor gratuito seleccionado permite el uso de producción previsto, sin activar pagos.
