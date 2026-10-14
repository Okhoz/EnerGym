# Referencia animada local

Esta versión incorpora una sola vista previa técnica, para `standard-pushup` / «Flexión estándar». No corresponde a `knee-pushup` ni se anuncia como demostración profesional verificada. El recurso tiene un cuerpo único animado; no posee capas de músculos separadas, regiones seleccionables ni mediciones de articulaciones. Los colores de vértices no indican activación muscular.

## Procedencia exacta

- Exportación: https://assiamahs.github.io/opengym3d/assets/push_up.glb
- SHA-256 del original: `67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326`; 5.957.800 bytes. No se incluye el original inválido en el APK.
- SHA-256 del derivado empaquetado `push_up_native.glb`: `80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f`; 5.957.652 bytes.
- Pipeline y especificación inspeccionados: OpenGym3D `ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae`. La exportación web es mutable; el hash identifica los bytes, la revisión no demuestra el despliegue web.
- Licencia por recurso: [ASSET_LIBRARY.json](https://github.com/AssiamahS/opengym3d/blob/ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae/assets/ASSET_LIBRARY.json).
- Movimiento CC0: [Mesh2Motion](https://github.com/Mesh2Motion/mesh2motion-app/blob/653a9698f5f315523072f1c3ff3496100401317b/LICENSE-CC0.MD); modelo/rig de MakeHuman/MPFB core assets CC0. Las copias de licencia se incluyen en el APK.
- El original contiene `Human.rig` y `Human`. La pista `Human` tiene una salida de 18 pesos para 2 muestras, pero la malla no incluye ningún morph target. Filament/cgltf rechaza ese archivo; la QA automática previa no detectó esta falta de conformidad glTF.
- La adaptación mínima CC0 retira exclusivamente esa pista huérfana y conserva `Human.rig`, 1,533333 segundos. No se crean morph targets ni se alteran malla, rig o movimiento. Se aplica el clip real y luego se actualizan las matrices de huesos.
- El chunk BIN completo es idéntico al original: SHA-256 `338f0a42938e2ee2de53d777bbc87c32f18101aeab82d813903c40437d39e97f`. El script `tools/normalize_push_up.py` verifica origen y resultado y conserva todos los demás campos JSON.
- QA automática de procedencia: 24 muestras, 0 fallos. No demuestra conformidad completa glTF ni sustituye una revisión Android o profesional.

## Arquitectura y controles

`ResourceRepository` conserva correspondencias exactas y verifica tamaño acotado, SHA-256 y cabecera antes de entrar en JNI. `PackagedResources` lee el manifiesto local y coteja identidad y capacidades con el único artefacto inspeccionado. No descarga recursos durante el uso.

`ViewerController` y `ViewerCommandValidator` son código Java testeable en JVM: correlación de ejercicio, vistas, velocidades y marcadores permitidos, tiempo de reproducción, zoom acotado y restauración saneada. `AnatomyActivity` es la vista XML y adaptador de ciclo de vida. Vive en el proceso privado `:viewer`, con `exported=false`. `NativeAnimationRenderer` posee superficie y todos los objetos Filament en el hilo principal; la lectura usa un trabajador I/O cancelable. Los resultados tardíos se descartan al cerrar. Pausar elimina callbacks; liberar cancela carga, separa superficie y destruye explícitamente motor, materiales, modelo y demás objetos.

El driver Vulkan del emulador retiene ~4,6 MB por creación/render de nuevos swapchains incluso con escena vacía sin Activity ni modelo. No es posible recuperar esa memoria con GC ni liberando los objetos documentados. Tras el cierre final, el visor recupera únicamente su proceso privado; nunca mata el proceso principal. El contador de Activities evita interrumpir otra instancia viva y `isChangingConfigurations()` evita matar el proceso durante recreación. No hay caché de Activity, superficie, modelo ni motor inmortal. La UI conserva pausa, cámara y velocidad por Bundle al recrear; la integración dispone de un broadcast del paquete no exportado `AnatomyActivity.ACTION_CLOSE` para invalidar contexto y terminar la vista.

Controles admitidos: reproducir/pausar, 0,25× / 0,5× / 1×, órbita y zoom por botones o gestos, frente/lateral/restablecer. La cámara usa el nodo `CamTarget` existente para encuadrar el cuerpo horizontal; el tamaño se deriva del límite del recurso. Los marcadores inicial/medio/final son muestras de la línea temporal real; no son fases técnicas aprobadas. El final se mantiene sin aplicar módulo a cero. El clip forma un bucle casi cerrado, por lo que inicial y final se parecen.

Ausencia o corrupción del recurso, ejercicio desconocido, variante sin mapeo, clips inesperados o fallo del renderizador muestran un estado de indisponibilidad con referencia escrita cuando existe. No se habilitan controles ficticios ni comandos de texto ejecutable.

## Dependencias y validación

Motor: Filament / gltfio Android 1.77.3, Java/JNI y Vulkan, Apache 2.0. Se exige Vulkan 1.1 anunciado por PackageManager antes de entrar en JNI; si falta, se muestra la referencia escrita. OpenGL ES3 del emulador anuncia solo 256 uniformes de vértice y aborta al enlazar el shader del rig; no se intenta ese backend ni se altera la geometría para acomodarlo. Aplicación con fuentes Java 11 compiladas usando JBR 25, compileSdk 37. Sin WebView, JavaScript ni fuentes Kotlin propias. Un runtime Kotlin es dependencia transitiva declarada de gltfio, no una implementación Kotlin de la app.

Pruebas JVM de identidad, comandos, hash corrupto, lectura corta, cierre del stream, marcadores, restauración y BIN intacto: 10 casos; fallo inicial de 7 de los 9 casos originales registrado con implementación vacía, luego verdes. La prueba final usa UIAutomation/accesibilidad y capturas reales del proceso remoto, exige malla y cambios de pose, gira/amplía mediante botones, comprueba pausa sobre píxeles, reproduce y verifica cierre/reclamación del proceso. También comprueba rotación y Home conservando pausa, y siete aperturas con memoria principal estable. Resultados y capturas en `.superpowers/sdd/energym/task-7-core-report.md` del workspace.

Integración terminada: rutas desde el ejercicio y asistente, disponibilidad exacta por ejercicio, correlación de cuenta/contexto y catálogo/backend v2. AppState y MainActivity coordinan ActivityResult, leases e IPC privado con órdenes validadas; el cierre de contexto revoca la apertura y cierra el visor separado. Las pruebas de integración de Task7 cubren estas rutas y la invalidación por cuenta/contexto. Pendiente de validación: dispositivo físico arm64, RenderEffect en dispositivo físico compatible y revisión profesional de la técnica. La vista previa nunca desbloquea la demostración verificada.
