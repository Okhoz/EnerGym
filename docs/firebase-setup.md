# Firebase Spark: configuración y recuperación

La app usa Google Credential Manager → Firebase Authentication → UID Firebase → SQLite/Firestore. Iniciar sesión activa el respaldo de esa cuenta. `historyConsent` es exclusivamente el permiso para usar historial al personalizar propuestas con IA; iniciar sesión no envía perfil, conversaciones, limitaciones ni historial a OpenAI. Esta tarea no añade una integración IA.

## Estado actual

No hay proyecto Firebase ni `google-services.json`: la consola está bloqueada al cargar el estado de aceptación de los términos. El build funciona con `CONFIGURATION_NEEDED`; la demo explícita usa `demo:local`, sin copia automática a Google y sin respaldo Firebase. Los tests locales no prueban login Google, escritura en un proyecto real ni reinstalación conectada a nube real. No se han publicado reglas ni activado facturación.

## Crear la configuración real

1. Cuando la consola permita continuar, crear un proyecto con **Spark**, sin cuenta de facturación. No activar Blaze, Cloud Functions, Storage, extensiones ni servicios IA de pago. Se puede omitir Analytics: no hay SDK de Analytics en la app.
2. Registrar la app Android con paquete **`com.energym.app`**. Ejecutar `./gradlew.bat :app:signingReport` y registrar **SHA-1 y SHA-256** de debug. Añadir las huellas release/Play App Signing antes de distribuir una versión firmada distinta.
3. En Authentication → Sign-in method habilitar Google, seleccionar correo de soporte y guardar. Revisar la pantalla de consentimiento OAuth y los usuarios de prueba si corresponde a su estado.
4. Descargar el `google-services.json` actualizado y guardarlo en **`app/google-services.json`**. Debe contener el cliente OAuth **Web application**, que genera `default_web_client_id`. No sustituirlo por el Android client ID ni introducir tokens en código. El plugin google-services se aplica solo cuando existe este archivo. El archivo está excluido de Git por comodidad de configuración individual.
5. Crear Firestore **Standard / Native mode**, en una región elegida para el proyecto, con reglas de producción cerradas. Revisar los ficheros `firestore.rules` y `firestore.indexes.json` antes de desplegarlos. Desplegar únicamente después de autorización explícita, con el ID real: `firebase deploy --only firestore:rules,firestore:indexes --project ID_REAL`. No usar reglas abiertas/test mode para la app.
6. Compilar y probar en dispositivo Android con Credential Manager/Google Play services disponibles. Verificar dos cuentas, cierre de sesión, modo avión y reconexión. El cliente web y las huellas deben corresponder al mismo proyecto Firebase.

La configuración usa dependencias fijadas: Firebase BoM **35.0.0** (Auth 25.0.0 / Firestore 27.0.0), plugin Google services **4.5.0**, AndroidX Credentials y Play-services-auth **1.3.0**, Google ID **1.1.1**. No hay claves ficticias ni configuración de proyecto remoto en el repositorio.

## Política de datos y conflictos

Rutas: `users/{uid}/profile/current`, `users/{uid}/routines/{proposalId}`, `users/{uid}/sessions/{sessionUuid}`. Solo el UID de FirebaseUser decide la cuenta. Los IDs SQLite no identifican documentos: se normalizan a cero dentro de snapshots de rutina/sesión. La sesión conserva el perfil, ejercicios, prescripciones y resultados históricos completos.

Las subidas son incrementales: solo la cola PENDING, una transacción por documento. `cloudVersion` es la versión CAS Firestore; `revision` es un token CAS SQLite local y nunca ordena dispositivos. Una transacción reintentada con el mismo payload confirma sin nueva escritura. Si cambió `cloudVersion` después de descargar, se informa CONFLICT y se conserva PENDING. En un siguiente intento explícito, la descarga obtiene la versión actual y el pendiente local tiene prioridad frente a ese estado observado. No se fusionan campos entre dispositivos. Las rutinas aceptadas son inmutables y las sesiones terminadas tampoco pueden reemplazarse; un conflicto sobre ellas requiere revisión del dato local.

Al restaurar desde SERVER, los pendientes locales conservan prioridad. Para registros CLEAN mutables, el snapshot remoto actual tiene autoridad aunque el reloj de otro dispositivo sea menor: el adaptador ajusta la fecha de importación local para pasar la regla de orden SQLite, y omite snapshots equivalentes. Así el reloj del dispositivo no decide la versión cloud actual. La base mantiene sus validaciones atómicas, referencias a rutinas efectivas, protección de rutinas/sesiones definitivas y materialización de la rutina de una sesión aislada. Una confirmación vieja no limpia una revisión nueva ni muestra SYNCED mientras queda PENDING: solicita otro ciclo.

La restauración usa perfil por get individual y rutinas/sesiones por páginas de **100**, hasta **2000 registros por cuenta**. Si se supera ese máximo se informa ERROR sin importar un lote parcial. Se descarga el estado actual en cada ciclo; no hay consultas continuas ni listeners Firestore persistentes. Cada documento limita payload UTF-8 a **600000 bytes**, con índice de payload desactivado. Los errores de red/cuota/esquema conservan la cola; `ERROR` no significa respaldo completado. Firestore usa caché de memoria y todas las restauraciones exigen SERVER; no persiste caché en disco. Los resultados y callbacks llevan comprobaciones de cuenta/generación. Una transacción ya enviada puede terminar en su cuenta original; nunca se importa ni confirma como otra cuenta.

Al importar una sesión, `routine.versionId=0` es el marcador portátil de nube. La aceptación se resuelve/materializa por `proposalId` y por coincidencia del snapshot válido, dentro de la transacción SQLite; nunca se inventa ni se confía en un ID de otro dispositivo. Al cargar el borrador, SessionRepository remapea al ID SQLite real de esa cuenta, y saveDraft sigue exigiendo esa versión aceptada local. La restauración permite documentos de sesión/rutina en cualquier orden y también sesión aislada con snapshot completo.

Si Credential Manager/Firebase ocultan temporalmente el UID durante reautenticación, el ciclo que pierde identidad se invalida y libera busy. Sus callbacks no pueden importar/confirmar ni alterar un ciclo nuevo. Al terminar el intento Google, AccountController reanuda el respaldo de la identidad Firebase actual incluso si el UID coincide con el anterior; también lo hace cuando Google falla y Firebase conserva la cuenta previa.

`clearLocalData()` borra solo SQLite de la cuenta seleccionada; no elimina documentos cloud ni crea tombstones. Una futura restauración puede recuperarlos. El borrado remoto/cuenta no se expone en esta tarea, y las reglas deniegan deletes. La UI debe describir este alcance exactamente.

## Recuperar tras reinstalar

1. Antes de desinstalar, comprobar el estado **SYNCED**, con conexión, para los últimos cambios de la cuenta Google. Los cambios que quedan PENDING o demo local no están respaldados en Firebase.
2. Reinstalar una app con la misma configuración Firebase e iniciar sesión con la misma cuenta Google.
3. El controlador abre el SQLite de ese UID y solicita restauración SERVER. Después de importar, vuelve a subir cualquier pendiente de ese UID. Verificar perfil, versiones aceptadas, historial y borradores; no adoptar datos de demo.
4. Sin red o ante ERROR, mantener los datos locales y reintentar al reconectar o con acción explícita. No mostrar “restaurado” ni “respaldado” antes del resultado correcto.

## Integración UI Task4

Usar `ContextCompat.getMainExecutor(context)` para callbacks de Credential Manager/controladores y **un único `Executors.newSingleThreadExecutor()`** para SQLite, coordinador y adapter cloud. Crear GoogleAccountGateway, FirebaseCloudGateway, SyncController y AccountController (pasar SyncController como SessionTarget). Llamar `AccountController.start()` al iniciar el propietario de estado; mostrar `CONFIGURATION_NEEDED` de forma explícita. Lanzar el selector mediante `AccountController.signInWithGoogle(activity, false)` desde una acción explícita. La variante `true` filtra cuentas autorizadas; si devuelve NoCredentialException puede ofrecerse/repetirse `false`. No leer un correo/Google ID para abrir SQLite, y no mostrar/loguear tokens.

Leer/escribir con `SyncController.withDatabase(action, callback)`, creando RoutineRepository/SessionRepository dentro de la acción. No retener el database ni devolverlo a una vista. Tras una mutación de usuario confirmada, solicitar `requestSync()`. Solicitarlo también al recuperar conexión y desde “reintentar”; Task4 implementará observación de conectividad mientras la app esté activa. No llamar por cada pulsación ni añadir polling continuo: la descarga consume lecturas de cuota. `authenticated()` solicita automáticamente restauración en login/recuperación de sesión. Demo permanece separado. `AccountController.signOut()` invalida selección/auth en vuelo, cierra el scope SQLite y limpia estado Credential Manager; `close()` retira el observador y cierra el scope sin desconectar por sí mismo la sesión Firebase persistida. Cerrar controladores antes de detener el ejecutor.

Spark tiene límites gratuitos compartidos por proyecto y no garantiza servicio cuando se agota una cuota. Según documentación consultada, Firestore ofrece 1 GiB, 50000 lecturas y 20000 escrituras diarias gratuitamente; vigilar Usage en la consola. Conservar Spark: no enlazar billing para superar esos límites. Auth Google y Firestore son los únicos productos cloud usados aquí.

## Tests de reglas locales

`firebase-tests/package.json` y lock fijan Firebase CLI **15.33.0**, rules-unit-testing **6.0.0** y SDK web **13.0.0**. Requieren Node compatible con estos paquetes y Java 21+. Instalar en `firebase-tests` con `pnpm install --store-dir ./.pnpm-store --frozen-lockfile`; pnpm puede advertir scripts de dependencias omitidos. El CLI y estos tests funcionan sin ejecutarlos en este entorno. Ejecutar `./firebase-tests/run-tests.ps1` desde PowerShell con Node/Java en PATH. El script llama el CLI oficial directamente, evitando auto-reinstalación de dependencias de `pnpm test`.

El proyecto **`demo-energym`** está reservado para el emulador y no representa credenciales de nube. Las ocho pruebas reales verifican propietario, invitado, cuenta ajena, esquema/tamaño UTF-8, CAS, rutinas inmutables, transición de sesión a terminada, deletes y rutas desconocidas. No publican reglas. Los payloads de estas fixtures prueban el sobre de seguridad; el codec Android valida el JSON/modelo completo en otras pruebas.

## Fuentes oficiales consultadas (14-10-2026)

- [Google sign-in Firebase Android, incluido Java/Credential Manager](https://firebase.google.com/docs/auth/android/google-signin)
- [Credential Manager Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [Firebase Android setup / versiones](https://firebase.google.com/docs/android/setup)
- [Transacciones Firestore](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Caché Firestore de memoria](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Pruebas de reglas con Emulator Suite](https://firebase.google.com/docs/rules/unit-tests)
- [String UTF-8 en reglas](https://firebase.google.com/docs/reference/rules/rules.String)
- [Cuotas Firestore](https://firebase.google.com/docs/firestore/quotas)
- [Planes Spark/Blaze](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans)
