# Referencia animada local

Los 287 ejercicios del catálogo tienen vista 3D. Todo vive en un único archivo, `app/src/main/assets/visual/energym_motions.glb` (8,7 MB; ~3,5 MB comprimido en el APK), con una malla MakeHuman/MPFB CC0 y 257 clips. Antes había cuatro GLB de ~5,95 MB que repetían la malla (~11 MB comprimidos).

Hay dos tipos de clip, y la interfaz siempre dice cuál se está viendo («Captura de movimiento CC0», «Captura CC0 de un gesto similar» o «Animación esquemática»):

| Tipo | Ejercicios | Origen |
| --- | --- | --- |
| Captura CC0 exacta | `standard-pushup`, `jumping-jacks`, `running-easy`, `brisk-walk`, `prep-short-sprints`, `swimming-easy`, `swimming-intervals`, `shadow-boxing` | Clips Mesh2Motion CC0: los cuatro de OpenGym3D (copiados tal cual) y «Sprint», «Swim_Fwd», «Punch_Jab»+«Punch_Cross» retargeteados |
| Captura CC0 de gesto similar | `running-intervals`, `tempo-run`, `fartlek-run`, `long-run` (trote), `treadmill-incline-walk` (marcha), `hill-sprints` (sprint) | Mismos clips; ilustran el gesto en el sitio, no el ritmo ni la pendiente |
| Animación esquemática | Los 273 restantes (250 clips distintos) | Generada por EnerGym por patrón de movimiento |

Las animaciones esquemáticas parten de poses clave escritas por patrón (sentadilla, bisagra, zancada, empuje, tracción, curl, extensión, elevación, plancha, core, cardio, saltos y estiramientos), con parámetros por ejercicio: apertura, profundidad, inclinación del tronco, agarre (prono/supino/neutro) y carga. Un solucionador resuelve la cinemática directa e inversa (pies o manos plantados), apoya en el suelo el punto más bajo de la malla e interpola con suavizado. Se añaden props simplificados (barra, mancuernas, apoyo o banco, barra fija); las máquinas y las poleas se representan solo con el asiento o sin aparato. Son orientativas: no son capturas ni demostraciones validadas, y no se ha revisado su técnica de forma profesional.

## Generación

1. Descargar las exportaciones OpenGym3D (`push_up.glb`, `jumping_jack.glb`, `jog.glb`, `walk.glb`) y normalizarlas con `python -I tools/normalize_opengym3d.py exportaciones entradas` (verifica origen y resultado; ver abajo).
2. Añadir a `entradas` `human-base-animations.glb` de Mesh2Motion (revisión `653a9698f5f315523072f1c3ff3496100401317b`, SHA-256 `406eb0a8dc4ab366e623b79b6e3005a4951392e1bda78ae39c1099d31147733c`).
3. `python -I tools/build_exercise_motions.py entradas` escribe el GLB y `manifest.json` y actualiza en `ResourceManifest` las huellas `MANIFEST_SHA256` y `MODEL_SHA256`. Sin dependencias externas; tarda menos de un minuto.

`tools/exercise_motion_catalog.py` asigna cada ejercicio a su clip. Los clips CC0 de OpenGym3D se copian sin cambios. Los esquemáticos y los retargeteados se muestrean a 15 o 30 fps y se guardan con rotaciones en SHORT normalizado; los canales constantes usan dos claves, porque Filament ignora los muestreadores de una sola clave. Cada clip coloca `CamTarget` en el centro de su movimiento, y el manifiesto guarda un radio de encuadre (`viewRadius`) para que la cámara se aleje en ejercicios amplios, como las dominadas.

## Procedencia exacta: flexión estándar

- Exportación: https://assiamahs.github.io/opengym3d/assets/push_up.glb
- SHA-256 del original: `67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326`; 5.957.800 bytes. No se incluye el original inválido en el APK.
- SHA-256 del derivado empaquetado `push_up_native.glb`: `80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f`; 5.957.652 bytes.
- Pipeline y especificación inspeccionados: OpenGym3D `ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae`. La exportación web es mutable; el hash identifica los bytes, la revisión no demuestra el despliegue web.
- Licencia por recurso: [ASSET_LIBRARY.json](https://github.com/AssiamahS/opengym3d/blob/ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae/assets/ASSET_LIBRARY.json).
- Movimiento CC0: [Mesh2Motion](https://github.com/Mesh2Motion/mesh2motion-app/blob/653a9698f5f315523072f1c3ff3496100401317b/LICENSE-CC0.MD); modelo/rig de MakeHuman/MPFB core assets CC0. Las copias de licencia se incluyen en el APK.
- El original contiene `Human.rig` y `Human`. La pista `Human` tiene una salida de 18 pesos para 2 muestras, pero la malla no incluye ningún morph target. Filament/cgltf rechaza ese archivo; la QA automática previa no detectó esta falta de conformidad glTF.
- La adaptación mínima CC0 retira exclusivamente esa pista huérfana y conserva `Human.rig`, 1,533333 segundos. No se crean morph targets ni se alteran malla, rig o movimiento. Se aplica el clip real y luego se actualizan las matrices de huesos.
- El chunk BIN completo es idéntico al original: SHA-256 `338f0a42938e2ee2de53d777bbc87c32f18101aeab82d813903c40437d39e97f`. El script `tools/normalize_opengym3d.py` verifica origen y resultado y conserva todos los demás campos JSON.
- QA automática de procedencia: 24 muestras, 0 fallos. No demuestra conformidad completa glTF ni sustituye una revisión Android o profesional.

## Procedencia exacta: saltos de tijera, trote y marcha

Descargadas e inspeccionadas el 15-10-2026 desde `https://assiamahs.github.io/opengym3d/assets/<exportación>`. Los originales quedan fuera del APK.

| Exportación | SHA-256 original (bytes) | SHA-256 derivado empaquetado (bytes) | SHA-256 chunk BIN (idéntico) |
| --- | --- | --- | --- |
| `jumping_jack.glb` | `94aa534eded3272a61f775d3a7067a9093df97c99f5505d64e39251c46697a0e` (5.955.232) | `dd6f6e860be59702714edefed5dac8265d391c4d4a2244ac7fea18b52ef2abfa` (5.955.088) | `b8c2aee85ea30f7510998da1b6d3fd0fb5828a636fc9b68902d705e1e276ae3f` |
| `jog.glb` | `3e20fe0299cd78c9534f4c1320a4e2e4f31f8b4ae51598a69065f93a43a0c020` (5.947.636) | `ad9cb7c7b9828e1f65d00a6d02b3afddf0a0fe52070eca5e335300c1231d5a2c` (5.947.488) | `05ec689556476c4419480c1688fd43456057457f1463303172866d2995fa246b` |
| `walk.glb` | `789867cd7f3ab8362f29632d1b424eb0f31193fca3a05318ac830a03b2115eb1` (5.953.452) | `fb71090b36eb0f03a17b451618e6a28a0bb83e043baf7c1f348acf3b890c92f5` (5.953.304) | `55decda8a7701e5edb2798ed3a7d1e207220a0d168afc1a3d5cd07116cc34a77` |

- Revisión desplegada: el registro de despliegue `github-pages` del repositorio apunta a `ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae` (ejecución «render & deploy» 35005945810 del 15-09-2026, con validate/render/deploy correctos) y no hay ejecuciones posteriores. A diferencia de la flexión, aquí la revisión del despliegue sí queda constatada; el hash sigue siendo la identidad de los bytes.
- En esa revisión, `exercises/jumping_jack.json`, `exercises/jog.json` y `exercises/walk.json` declaran `mocap` CC0 (`cc0/mesh2motion/...`). `jumping_jack` usó antes un clip Mixamo (`mocap_prev`, commit 80d6df7 del 09-09-2026), sustituido por el CC0 en fe3ee8d (15-09-2026), antes del despliegue de ea3a601.
- Comprobación independiente de temporización: los clips CC0 del paquete Mesh2Motion de esa revisión (`human-addon-animations.glb` SHA-256 `a0d64d555e0d492026b72d58bf8e16c5e86779295f9093e376dcc001915c2c95`, `human-base-animations.glb` SHA-256 `406eb0a8dc4ab366e623b79b6e3005a4951392e1bda78ae39c1099d31147733c`) duran 1,25 / 1,1667 / 1,6667 s a 24 fps; remuestreados a 30 fps dan 38 / 36 / 51 claves y 1,2667 / 1,2 / 1,7 s, igual que las exportaciones. El mismo patrón se cumple para `Pushup` → `push_up.glb`. Es evidencia consistente, no una prueba criptográfica del origen del movimiento.
- Licencia: `ASSET_LIBRARY.json` en ea3a601 marca `motion/cc0/jumping-jacks`, `motion/cc0/jog` y `motion/cc0/walk` como CC0-1.0 con `commercial_use: true`; la declaración `motions/cc0/mesh2motion/LICENSE-CC0.md` de esa revisión es byte a byte la ya incluida en el APK. Malla y rig: MakeHuman/MPFB core assets CC0, igual que la flexión (mismos 166 nodos, malla `Mesh`, material `Body`, skin única).
- No se usan las entradas Mixamo (`motion/mixamo/*`) ni CMU (`motion/cmu/*`, «app-only», no revendibles) del catálogo OpenGym3D.
- Las tres exportaciones tienen el mismo defecto que `push_up.glb` (pista `Human` de pesos sin morph targets) y reciben la misma adaptación mínima con `tools/normalize_opengym3d.py`, que también reproduce byte a byte `push_up_native.glb`.
- Tamaño histórico: cada GLB añadía ~5,95 MB sin comprimir y ~2,77 MB comprimido (la malla se repetía). Ahora son entradas del generador y no se empaquetan.

## Arquitectura y controles

`ResourceRepository` conserva correspondencias exactas por ejercicio y, antes de entrar en JNI, verifica el tamaño acotado (16 MB), el SHA-256 y la cabecera. `PackagedResources` lee el manifiesto local (versión 2) con un lector JSON propio y solo lo acepta si su SHA-256 coincide con `ResourceManifest.MANIFEST_SHA256` y el modelo con `MODEL_SHA256`: esa huella es la lista cerrada, así que cualquier fila editada, añadida o quitada invalida el manifiesto completo. `NativeAnimationRenderer` busca por nombre el clip del ejercicio, comprueba su duración y aplica solo ese clip. No descarga recursos durante el uso.

`ViewerController` y `ViewerCommandValidator` son código Java testeable en JVM: correlación de ejercicio, vistas, velocidades y marcadores permitidos, tiempo de reproducción, zoom acotado y restauración saneada. `AnatomyActivity` es la vista XML y adaptador de ciclo de vida. Vive en el proceso privado `:viewer`, con `exported=false`. `NativeAnimationRenderer` posee superficie y todos los objetos Filament en el hilo principal; la lectura usa un trabajador I/O cancelable. Los resultados tardíos se descartan al cerrar. Pausar elimina callbacks; liberar cancela carga, separa superficie y destruye explícitamente motor, materiales, modelo y demás objetos.

El driver Vulkan del emulador retiene ~4,6 MB por creación/render de nuevos swapchains incluso con escena vacía sin Activity ni modelo. No es posible recuperar esa memoria con GC ni liberando los objetos documentados. Tras el cierre final, el visor recupera únicamente su proceso privado; nunca mata el proceso principal. El contador de Activities evita interrumpir otra instancia viva y `isChangingConfigurations()` evita matar el proceso durante recreación. No hay caché de Activity, superficie, modelo ni motor inmortal. La UI conserva pausa, cámara y velocidad por Bundle al recrear; la integración dispone de un broadcast del paquete no exportado `AnatomyActivity.ACTION_CLOSE` para invalidar contexto y terminar la vista.

Controles admitidos: reproducir/pausar, 0,25× / 0,5× / 1×, órbita y zoom por botones o gestos, frente/lateral/restablecer. La cámara sigue el nodo `CamTarget`, que cada clip anima al centro de su movimiento; la distancia es el mayor valor entre el límite del recurso y `viewRadius`. Los marcadores inicial/medio/final son muestras de la línea temporal real; no son fases técnicas aprobadas. El final se mantiene sin aplicar módulo a cero. El clip forma un bucle casi cerrado, por lo que inicial y final se parecen.

Ausencia o corrupción del recurso, ejercicio desconocido, clip ausente o con otra duración o fallo del renderizador muestran un estado de indisponibilidad con referencia escrita cuando existe. No se habilitan controles ficticios ni comandos de texto ejecutable.

## Dependencias y validación

Motor: Filament / gltfio Android 1.77.3, Java/JNI y Vulkan, Apache 2.0. Se exige Vulkan 1.1 anunciado por PackageManager antes de entrar en JNI; si falta, se muestra la referencia escrita. OpenGL ES3 del emulador anuncia solo 256 uniformes de vértice y aborta al enlazar el shader del rig; no se intenta ese backend ni se altera la geometría para acomodarlo. Aplicación con fuentes Java 11 compiladas usando JBR 25, compileSdk 37. Sin WebView, JavaScript ni fuentes Kotlin propias. Un runtime Kotlin es dependencia transitiva declarada de gltfio, no una implementación Kotlin de la app.

Recursos adicionales (15-10-2026): `ResourceRepositoryTest` verifica, para los cuatro recursos, SHA-256 de los bytes empaquetados, cabecera GLB, chunk BIN contra el manifiesto y presencia del ejercicio en el catálogo. `AnatomyActivityTest#additionalCc0ResourcesLoadInFilamentAndMove` cargó `jumping-jacks`, `running-easy` y `brisk-walk` en Filament (emulador Medium Phone API 37, Vulkan): `Native ready Human.rig` con 1,2666667 / 1,2 / 1,7 s, malla visible (11.249 / 14.698 / 13.182 muestras no negras) y cambio de pose inicial→medio (10.050 / 16.728 / 11.630). Pendiente: dispositivo físico y revisión profesional.

Pruebas JVM de identidad, comandos, hash corrupto, lectura corta, cierre del stream, marcadores, restauración y BIN intacto: 10 casos; fallo inicial de 7 de los 9 casos originales registrado con implementación vacía, luego verdes. La prueba final usa UIAutomation/accesibilidad y capturas reales del proceso remoto, exige malla y cambios de pose, gira/amplía mediante botones, comprueba pausa sobre píxeles, reproduce y verifica cierre/reclamación del proceso. También comprueba rotación y Home conservando pausa, y siete aperturas con memoria principal estable. Resultados y capturas en `.superpowers/sdd/energym/task-7-core-report.md` del workspace.

Integración terminada: rutas desde el ejercicio y asistente, disponibilidad exacta por ejercicio, correlación de cuenta/contexto y catálogo/backend v2. AppState y MainActivity coordinan ActivityResult, leases e IPC privado con órdenes validadas; el cierre de contexto revoca la apertura y cierra el visor separado. Las pruebas de integración de Task7 cubren estas rutas y la invalidación por cuenta/contexto. Pendiente de validación: dispositivo físico arm64, RenderEffect en dispositivo físico compatible y revisión profesional de la técnica. La vista previa nunca desbloquea la demostración verificada.

Validación del 15-10-2026 (GLB único): `ResourceRepositoryTest` comprueba que los 287 ejercicios del catálogo tienen exactamente una fila, que cada clip existe en el GLB con la duración del manifiesto, que no hay clips sin uso, el etiquetado captura/esquemática, el rechazo de un manifiesto editado y el presupuesto de tamaño. En el emulador Medium Phone API 37 (Vulkan) se abrieron desde la biblioteca sentadilla trasera, flexión estándar (captura), curl con mancuernas, dominadas, peso muerto rumano, plancha sobre antebrazos, press militar, press de banca, cuestas cortas (sprint retargeteado), nado continuo (crol retargeteado), postura de la paloma, bicicleta estática y burpee: todos cargaron, se movieron y mostraron su etiqueta. Además se revisaron offline fotogramas de los 257 clips. `AnatomyActivityTest` está actualizada para recorrer capturas y clips esquemáticos de varios grupos.
