# EnerGym

Aplicación Android Java/XML para crear rutinas, conservar versiones y registrar sesiones. ID: `com.energym.app`. Tema negro/blanco/dorado, logo verde transparente, paneles tintados y movimiento acotado.

## Funciones y arquitectura

El perfil declara objetivo, lugar, recursos, días y duración. Las propuestas siguen siendo provisionales hasta «Guardar perfil y aceptar»; rechazar no escribe. La aceptación compara el perfil vigente y guarda perfil/rutina en una transacción. Sesiones e históricos conservan snapshots y métricas de su versión. Datos locales y conversación se separan por propietario.

MVC: domain/catalog/data contienen modelos, compatibilidad, validación y persistencia; controller y los controladores del asistente coordinan acciones mediante interfaces comprobables en JVM; Activities, Fragments y XML presentan datos y remiten eventos. Las vistas no ejecutan SQL ni deciden compatibilidad.

El guiado local gratuito se identifica como conversación por reglas. La IA necesita conexión al backend y envío externo explícito. Las claves del proveedor permanecen en el servidor; el token Android vive en memoria. El consentimiento solo autoriza historial acotado; revocarlo elimina el payload e invalida respuestas pendientes consentidas. No hay cambio automático de proveedor/modelo ni respuesta local presentada como IA.

El catálogo v2 añade Flexión estándar (`standard-pushup`), distinta de `knee-pushup`. Su vista previa técnica nativa permite reproducción, pausa, velocidad, marcadores, giro, zoom y vistas. Para otros recursos/dispositivos incompatibles conserva referencia escrita. No hay capas musculares, evaluación personal con cámara ni revisión profesional. Consulta [recursos](docs/resources.md) y [licencias](THIRD_PARTY_NOTICES.md).

## Compilar y ejecutar

Android Studio, SDK Platform37 y Build Tools36.1.0; Android API26+, compileSdk/targetSdk37. El visor requiere Vulkan1.1. Gradle Wrapper9.6.0/AGP9.4.1, fuentes Java11; JBR25.0.3 ejecuta Gradle. Backend Java17+; pruebas verificadas con Java21. Configura sdk.dir en local.properties y selecciona JBR como Gradle JDK; no publiques esa configuración local.

Desde la raíz del proyecto, PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
$mavenCache = Join-Path $PWD 'backend/.cache/m2'
.\backend\mvnw.cmd -f backend/pom.xml "-Dmaven.repo.local=$mavenCache" test package
```

El primer acceso puede descargar dependencias públicas gratuitas. Para -o/--offline, la caché indicada debe contener todas las dependencias: su existencia como carpeta no garantiza completitud. La verificación del proyecto real utilizó la caché ya poblada de staging; su caché propia no tenía maven-resources-plugin3.4.0. Ajusta la ruta a una caché conocida o permite resolución pública normal.

APK debug: app/build/outputs/apk/debug/app-debug.apk; JAR: backend/target/energym-backend-1.0.0.jar. Debug conserva la demo local para desarrollo y pruebas. Release oculta esa entrada y el controlador también la rechaza, conservando la identidad Google vigente. Abre el proyecto y ejecuta app sobre un dispositivo; reutiliza un emulador abierto. Pruebas instrumentales: `.\gradlew.bat :app:connectedDebugAndroidTest` con dispositivo conectado y coordinado. Estas pruebas no llaman a Gemini salvo parámetros opt-in explícitos del fixture real.

Release está bloqueada si faltan las entradas privadas Firebase, origen HTTPS y firma. [Preparación de release](docs/production-release.md) explica las variables, validación local y artefactos. Esa validación no demuestra Google/Firebase, restauración remota, servidor real ni permiso para distribuir Gemini gratuito en España. La conexión automática autenticada del asistente sigue pendiente de Task16.

## Gemini legado en desarrollo

[backend/README.md](backend/README.md) detalla configuración y privacidad. La clave Windows ya configurada se usa solo en el servidor. Desde backend en **PowerShell7**, genera un código privado nuevo para el emparejamiento y cópialo sin registrarlo en logs:

```powershell
$env:ENERGYM_PAIRING_CODE = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(16))
Set-Clipboard $env:ENERGYM_PAIRING_CODE
.\start-server.ps1 -Provider Gemini -CheckConfiguration
.\start-server.ps1 -Provider Gemini
```

En otra terminal, con adb del SDK disponible: `adb reverse tcp:8080 tcp:8080`. En la app debug introduce http://127.0.0.1:8080 y pega el código en conexión privada. El código se consume una vez; genera otro para un nuevo emparejamiento. HTTP loopback solo debug; release exige HTTPS. No hay servidor público/certificado real comprobado ni facturación autorizada.

**Gemini real comprobado:** el tercer recorrido Android del 14-10-2026 pasó 1 prueba en10.409s con gemini-3.5-flash-lite y catálogo2. Recibió una propuesta válida de tres ejercicios, sin prescripciones faltantes; SQLite permaneció intacta antes de aceptar. La aceptación creó la versión con perfil/rutina exactos y persistió tras recrear. Se usó una demo genérica con historyConsent=false. Dos intentos anteriores fallidos motivaron la corrección revisada schema/prompt; backend final42pruebas verdes. Una prueba anterior de backend recibió HTTP200 en2067ms.

Gemini3.8 fue una opción de desarrollo, pero superó los plazos en las pruebas. La cuota gratuita tiene límites/condiciones; no se promete uso ilimitado. Según los términos oficiales enlazados en backend, ofrecer clientes API Gemini a usuarios del EEE/Suiza/Reino Unido exige Paid Services. Gemini queda como proveedor legado: la solicitud vigente exige una IA gratuita sin activar pagos y la alternativa de producción está pendiente de selección y verificación. No se ha autorizado un despliegue de pago.

Google Authentication/Firestore tienen adaptadores y pruebas locales; **Firebase real sigue sin configurar**. Login remoto y restauración tras reinstalar no están comprobados.

## XML y rendimiento

En Android Studio abre app/src/main/res/layout/activity_main.xml, fragment_*.xml o row_*.xml y selecciona **Design** o **Split**. Las muestras tools/GONE se inflan y luego las filas de ejemplo se retiran al renderizar; no se afirma coste cero. La revisión comprobó XML y empaquetado de19layouts, no píxeles del editor. La animación nativa se muestra al ejecutar.

El movimiento solo se inicia al cambiar ruta y se cancela al pausar/detach. RenderEffect se limita a decoración estática acotada y tiene fallback por API, memoria, energía, animación deshabilitada y visor. El AVD192MB utilizó fallback; RenderEffect habilitado y rendimiento físico no están medidos. No hay blur de controles, texto, cámara ni SurfaceView. La memoria del visor se recupera al cerrar su proceso privado; conserva principal y recreaciones.

[docs/acceptance.md](docs/acceptance.md) registra pruebas, artefactos y límites. El formato Task8 no modifica comportamiento ni añade cambios de diseño.

Evidencia histórica de staging tras Task8:107JVM sin fallos, debug/release correctos y lint0errores/91avisos. Desde Task14, un release sin las entradas privadas exigidas falla expresamente; aquel build anterior no acreditaba una app operativa en producción. Root verifica el proyecto real y el recorrido Android, sin confundir la evidencia staging con una instalación nueva.
