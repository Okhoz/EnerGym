# EnerGym

Aplicación Android con lógica en Java y pantallas XML. Namespace e ID: `com.energym.app`.

## Entorno

- Android Studio con Android SDK Platform 37 (`android-37.0`), Platform Tools y Build Tools 36.1.0 instalados.
- Dispositivo o emulador Android 8.0 (API 26) o superior; compileSdk y targetSdk: 37.
- Gradle Wrapper 9.6.0 y Android Gradle Plugin 9.4.1, incluidos en el proyecto.
- La compilación de base se ha verificado con el JBR de Android Studio, OpenJDK 25.0.3. Java 11 es la compatibilidad del código fuente/bytecode; no identifica el JDK que ejecuta Gradle.

Configura el SDK desde Android Studio o mediante `sdk.dir` en tu `local.properties` local. No publiques ese archivo. En Android Studio selecciona el JBR incluido como Gradle JDK.

## Compilar y comprobar (PowerShell)

Desde la carpeta del proyecto, usando el JBR instalado en esta máquina:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

El APK se genera en `app\build\outputs\apk\debug\app-debug.apk`. Ajusta la ruta de `JAVA_HOME` si Android Studio está instalado en otro lugar.

## Ejecutar

Abre el proyecto en Android Studio, selecciona un dispositivo en ejecución y pulsa Run en el módulo `app`. También puedes iniciar el AVD local y ejecutar desde PowerShell (ajusta la ruta del SDK y el nombre del AVD según tu equipo):

```powershell
$sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
Start-Process -FilePath "$sdkPath\emulator\emulator.exe" -ArgumentList '-avd', 'Medium_Phone_API_37.0' -WindowStyle Hidden
& "$sdkPath\platform-tools\adb.exe" wait-for-device
& "$sdkPath\platform-tools\adb.exe" install -r '.\app\build\outputs\apk\debug\app-debug.apk'
& "$sdkPath\platform-tools\adb.exe" shell am start -n 'com.energym.app/.MainActivity'
```

Espera a que el emulador termine de arrancar antes de instalar. Si hay varios dispositivos, selecciona uno mediante `adb -s <serial>`. En la revisión del entorno existe el AVD `Medium_Phone_API_37.0`; no había dispositivos conectados.

## Coste y credenciales

El desarrollo debe usar únicamente recursos gratuitos: sin llamadas a APIs de pago ni assets de pago. La integración con OpenAI es opcional y debe permanecer desactivada mientras no haya acceso a una API financiada y autorización explícita para usarla. No incluyas claves ni otros secretos en Java, XML, recursos, configuración empaquetada o APK; cualquier integración futura necesita un backend que mantenga las credenciales fuera de la aplicación.
