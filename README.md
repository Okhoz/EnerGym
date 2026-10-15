# EnerGym

Aplicación Android Java/XML para planificar entrenamientos, registrar sesiones, alimentación y peso, y consultar el progreso de cada cuenta. Paquete `com.energym.app`. Diseño negro, blanco y dorado, con el logo verde y componentes compartidos.

## Estado actual — 15-10-2026

- **Cuenta:** Google Credential Manager → Firebase Authentication → SQLite/Firestore por UID. El proyecto real `energym-e4699` está configurado para debug; se verificaron inicio de sesión, sesión persistida y «Respaldo confirmado». La reinstalación con restauración remota completa y la firma definitiva aún requieren comprobación. Véase [Firebase](docs/firebase-setup.md).
- **Entreno:** formulario por disciplina, propuestas revisables antes de aceptar, versiones inmutables e históricos con snapshots. La progresión se calcula desde resultados registrados y necesita aceptación explícita.
- **Biblioteca:** 287 ejercicios con técnica escrita, idénticos en app/backend, y visor nativo sin conexión. El modelo CC0 reúne capturas y movimientos esquemáticos; son referencias técnicas pendientes de revisión profesional. No analiza el cuerpo ni usa la cámara. [Recursos](docs/resources.md) y [licencias](THIRD_PARTY_NOTICES.md).
- **Nutrición:** diario por comidas, escáner EAN/UPC y QR GS1 con Google Play services, consulta de Open Food Facts o entrada manual; cantidades en g/ml, objetivos estimados, macros y peso. El resumen diario completo conserva las fechas antiguas aunque el diario cargue una ventana acotada. Datos y borrados de alimentos se sincronizan por cuenta.
- **Progreso:** resumen, objetivos, gráficas y calendario con entrenamientos y alimentación, también antes de terminar el primer entrenamiento. Las gráficas usan los datos guardados; no inventan mediciones.
- **Asistente:** guiado local identificado como reglas y conexión privada a IA desde Cuenta. El proveedor elegido es Groq Free (`openai/gpt-oss-120b`); hay una prueba sintética real documentada con respuesta y propuesta válidas. Las claves viven en el backend. Perfil y mensajes se envían sólo al usar la conexión externa; el historial exige consentimiento separado. [Configuración Groq](backend/docs/groq-free.md).

**La app todavía no está lista para publicación:** el chat sigue usando emparejamiento privado; faltan autenticación Firebase en el backend, cuotas durables por usuario/globales y el cliente Android automático con su endpoint fijo. También faltan el despliegue HTTPS real, las comprobaciones Docker y la firma de distribución. Las guardas de release exigen configuración privada válida, pero no sustituyen estas verificaciones. [Preparación de release](docs/production-release.md) y [Docker](backend/deploy/README.md).

## Arquitectura y datos

MVC: `domain`, `catalog`, `data`, `nutrition` y `progress` contienen modelos y persistencia; los controladores coordinan mediante interfaces comprobables; Activities, Fragments y XML presentan datos y remiten eventos. Las vistas no ejecutan SQL ni deciden compatibilidad.

Aceptar guarda perfil/rutina de manera transaccional y comprueba que el perfil vigente no cambió. Rechazar no escribe. Cada sesión conserva su rutina y resultados históricos. Conversaciones y datos se separan por propietario; cambiar cuenta invalida callbacks pendientes. El respaldo del sistema está desactivado; Firebase es el canal declarado. «Borrar datos locales» afecta sólo a esa cuenta y dispositivo: el respaldo cloud confirmado puede recuperarse al iniciar sesión de nuevo.

## Compilar y comprobar

Android Studio, SDK Platform37/Build Tools36.1.0, Android API26+, Gradle Wrapper9.6.0/AGP9.4.1 y fuentes Java11. Gradle usa el JBR de Android Studio; backend Java17+ (pruebas con Java21). Configurar `sdk.dir` en `local.properties`, que se excluye de Git. El visor requiere un dispositivo compatible con Vulkan; conserva referencia escrita si no puede cargar.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Recorrido instrumental: ver docs/review-2026-10-15.md (runner ADB y exclusiones de esta instalación).
```

Las pruebas de conexión con servidor/Gemini son opt-in y no se ejecutan como inferencias reales por defecto. La prueba de instalación sin Firebase requiere un build deliberadamente sin configuración. Las pruebas instrumentales usan datos sintéticos y la cuenta de desarrollo. El tooling puede reinstalar la app; usar un emulador de pruebas y conservar previamente cualquier dato que se necesite. [Verificación y exclusiones actuales](docs/review-2026-10-15.md).

```powershell
# Backend: JAVA_HOME debe señalar un JDK17+.
$mavenCache = Join-Path $PWD 'backend/.cache/m2'
.\backend\mvnw.cmd -f backend/pom.xml "-Dmaven.repo.local=$mavenCache" test package
# Reglas Firebase locales, después de instalar las dependencias de firebase-tests:
.\firebase-tests\run-tests.ps1
```

El modo offline requiere una caché completa. La última verificación del backend reutilizó la caché local ya poblada de staging, sin transferir su código antiguo al proyecto actual. Los tests de reglas necesitan Node y Java21+, usan `demo-energym` exclusivamente en el emulador y no publican reglas.

Artefactos: `app/build/outputs/apk/debug/app-debug.apk` y `backend/target/energym-backend-1.0.0.jar`. Debug conserva la cuenta local de desarrollo; release la oculta y rechaza también su activación directa. La APK se ejecuta en Android; Docker aloja el servidor.

## XML y recursos

Abrir `app/src/main/res/layout/activity_main.xml`, `fragment_*.xml` o `row_*.xml` en **Design**/**Split**. Los XML aportan estructura y muestras `tools:`; parte del contenido se construye dinámicamente desde los datos. El visor nativo se comprueba al ejecutar. Las muestras del editor no son cuentas ni mediciones reales.

El movimiento se cancela al pausar y sólo las transiciones de ruta lo reinician. Blur está limitado a decoración estática y dispone de fallback por API, memoria y energía; no desenfoca texto, controles ni SurfaceView. El rendimiento físico y la revisión profesional de movimientos siguen pendientes. Los bytes del manifiesto 3D se conservan en LF mediante `.gitattributes`, porque el visor comprueba su SHA-256.

`dev` reúne el desarrollo y sus copias verificadas; `main` recibe bloques importantes finalizados; se conserva `codex/energym` para el proyecto terminado. [Aceptación histórica](docs/acceptance.md) conserva pruebas de versiones anteriores y no sustituye el estado actual.
