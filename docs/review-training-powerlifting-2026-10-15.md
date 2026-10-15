# Entreno simplificado y powerlifting — 15/10/2026

Entreno muestra una sola sección cada vez: Mis rutinas, Con IA y Ejercicios. La siguiente acción prioriza continuar una sesión o borrador y mantiene las propuestas revisables. Los demás borradores se agrupan por su identidad y permanecen plegados; perfil y propuestas por reglas quedan en Más opciones. La selección y la búsqueda se conservan al volver de técnica y se invalidan al cambiar de cuenta.

El catálogo incluye la disciplina Powerlifting con sentadilla con barra, press de banca y peso muerto convencional/sumo. Se reutilizan sus cuatro identificadores, instrucciones y recursos 3D específicos: no se duplican ejercicios. Los recursos son referencias esquemáticas y conservan su estado real de revisión. Los catálogos Android y servidor permanecen idénticos (287 ejercicios).

Validación:
- Gradle offline: testDebugUnitTest, assembleDebug, assembleDebugAndroidTest y lintDebug correctos. 278 pruebas JVM, cero fallos; lint sin errores.
- Suite Android acotada: 27 de 28 casos correctos en la primera ejecución. El caso nuevo restante tocaba el campo de búsqueda porque compartía el nombre exacto del resultado. Se corrigió el selector a la descripción accesible del botón; repetición de los tres casos nuevos: 3/3 correctos. No se modificó comportamiento productivo para acomodar la prueba.
- Maven offline package: 70 pruebas del backend correctas y JAR generado.
- Inspección visual de las tres vistas en el emulador: botones separados, estilo negro/dorado y biblioteca independiente. Sin cambiar identidad ni guardar rutinas/sesiones de prueba.
- Revisión independiente de vista/estado/XML y catálogo; corregida la restauración de Bundle tras salir y volver a la misma cuenta mediante revisión de vinculación.

La publicación online del asistente es una tarea separada: requiere desplegar y verificar el backend HTTPS y configurar la URL real de la APK.