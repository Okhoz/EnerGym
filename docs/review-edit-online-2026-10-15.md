# Edición y conexión online — verificación 15/10/2026

La edición de rutinas crea una nueva versión y archiva atómicamente la anterior. Borrar rutinas, sesiones y borradores conserva los snapshots históricos y sincroniza archivos inmutables por UID. Nutrición permite editar la entrada conservando UUID/día/fecha y confirmar el borrado. Una lápida remota de alimento prevalece sobre una edición local pendiente.

La entrada Crear rutina con IA abandona expresamente el modo guiado local, no envía automáticamente y se bloquea durante una respuesta pendiente. Se corrigió además la ventana de formulario con nombre vacío que mostraba atajos de alta mientras se editaba.

Verificado en el proyecto real de Android Studio:
- 307 pruebas JVM correctas; APK debug, APK de instrumentación y lintDebug correctos.
- 37 pruebas Android correctas en el emulador: navegación Entreno/buscador, SQLite de versiones y archivos, recuperación inicial, conversación y formularios de edición con fixtures aislados.
- 98 pruebas backend correctas y JAR generado.
- 16 pruebas Firestore Emulator correctas con archivos por UID, aislamiento, inmutabilidad y cuotas cerradas al cliente.
- Reglas Firestore publicadas mediante CLI en energym-e4699 antes de instalar la APK nueva. No se modificaron identidades ni datos reales de entrenamiento o alimentación.

La versión Android online utiliza Firebase ID token y URL HTTPS de configuración. La clave del proveedor permanece en el backend; Render Free + Groq Free fue elegido explícitamente. Aún falta acreditar despliegue remoto y recorrido real APK → HTTPS → Firebase → Groq. Los planes gratuitos tienen cuotas y arranque en frío; no implican disponibilidad garantizada.