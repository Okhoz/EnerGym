# Aceptación histórica de EnerGym

Este documento conserva evidencia de versiones anteriores. Estado y correcciones actuales: [revisión del 15-10-2026](review-2026-10-15.md). Las notas de Firebase sin configurar o de cobertura limitada al primer ejercicio describen aquellos builds.

Estado documental14-10-2026. Evidencia de bloques revisados separada de la verificación final del árbol tras formato.

| Área | Resultado comprobado | Límites |
| --- | --- | --- |
| Task7 integración Approved | 103JVM,40backend y8Android registrados; permisos privados y correlación cuenta/contexto revisados | Rendimiento físico arm64 pendiente |
| Task9 XML Approved | 19layouts; metadatos tools/muestras GONE conservan runtime y empaquetado | No se verificaron píxeles Design/Split; los grupos se inflan antes de retirar muestras |
| Task10/fixes Approved | 107JVM,5Android movimiento/visor y1captura; fuente130+IME y Guardar alcanzable | AVD192MB usó fallback; RenderEffect habilitado y benchmark físico pendientes |
| Task11 PASS | lintDebug0errores/94avisos offline existentes, release y107JVM correctos | Avisos no corregidos; nueve GradleDependency extra del lint online no son una mejora atribuible al fix |
| Task5e fix1 Approved | Backend42pruebas, test/package correctos | Caché Maven del proyecto real incompleta; ejecución usó caché staging existente |
| Gemini Android real | Tercer intento OK1/10.409s, runner13.704s; modelo3.5FlashLite/catalog2, propuesta3ejercicios,0camposfaltantes,0comandos | Demo genérica,historyConsent=false; no promete producción/gratuidad ilimitada |
| Cloud | Adaptadores/reglas y aislamiento local probados | Firebase sin provisionar; login Google remoto/reinstalación sin comprobar |
| Task8 formato | Tabs4 y parsers/formatter semánticos sobre fuentes propias; evidencias finales en informe de entrega | Root verifica árbol real y suites amplias tras transferencia |

## IA real y aceptación

Los dos primeros intentos fallidos se conservaron como evidencia; el segundo rechazó una prescripción incompleta con INVALID_PROVIDER_RESPONSE. La corrección revisada de contrato/prompt hizo explícitas las métricas, loadKg solo STRENGTH y la gramática numérica. En el tercer intento real, SQLite permaneció intacta antes de aceptación explícita; aceptar creó una versión con el perfil/rutina exactos y la recreación mantuvo los datos. El diagnóstico almacenó solo metadatos de validación, sin respuesta cruda ni claves. Una prueba backend anterior obtuvo HTTP200/2067ms. No se ejecutan nuevas llamadas reales en la verificación de formato.

Gemini3.8 sigue siendo seleccionable explícitamente y fue lento. No hay fallback automático entre modelos/proveedores. Condiciones de cuota, datos y restricción Paid Services para clientes API en EEE/Suiza/Reino Unido: [backend/README.md](../backend/README.md). Sin despliegue público ni facturación autorizada.

## Referencia anatómica y presentación

Solo standard-pushup tiene vista previa técnica, sin revisión profesional; no equivale a knee-pushup. El GLB CC0 elimina únicamente el clip huérfano Human, cuyos pesos referían morph targets inexistentes; BIN idéntico y animación real Human.rig conservada. No hay capas/activación muscular, métricas articulares ni evaluación mediante cámara. Recursos, hashes y licencias están en docs/resources.md y THIRD_PARTY_NOTICES.md.

El visor privado :viewer libera recursos y recupera únicamente su proceso al cierre final, por retención Vulkan reproducida con swapchains nuevos. Siete ciclos del núcleo aislaron el coste: principal +2KB heap/+4KB PSS y todos los PID privados desaparecieron. Recreación por configuración mantiene el proceso; nunca se mata el principal.

La explicación local conserva snapshots históricos; la externa se rechaza si difieren del catálogo del servidor. Las muestras XML de Design/Split son referencias de editor y se inflan antes de eliminar las filas GONE; no se promete coste cero ni QA visual del editor. El movimiento no se reinicia al actualizar datos. Blur se limita a decoración estática y tiene fallback conservador; el AVD192MB no ejercitó RenderEffect habilitado. No hay prueba física ni promesa60fps.

## Verificación reproducible

Desde raíz con JBR/SDK configurados:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
$mavenCache = Join-Path $PWD 'backend/.cache/m2'
.\backend\mvnw.cmd -f backend/pom.xml "-Dmaven.repo.local=$mavenCache" test package
.\gradlew.bat :app:connectedDebugAndroidTest
```

Offline necesita caché completa, no solo carpeta existente; indicar la caché staging conocida si se verifica el proyecto real sin descargar. El dispositivo debe estar coordinado. Revisar sin material, sustitución, cada métrica, aceptación/rechazo, históricos, cuenta/contexto, referencia disponible y fallback escrito, y desconexión/revocación. Instrumentales reales Gemini solo con parámetros autorizados; Task8 no usa AVD ni claves. Firma y despliegue públicos, Firebase remoto y validación profesional permanecen pendientes. Logs detallados de investigación fuera del producto.

## Verificación fresca de staging tras formato Task8

Gradle/JBR terminó BUILD SUCCESSFUL en54s: testDebugUnitTest, assembleDebug, lintDebug, assembleRelease y compileDebugAndroidTestJavaWithJavac. 107pruebas JVM/18suites,0fallos/0errores. Lint0errores/91avisos: desaparecieron únicamente tres SuspiciousIndentation por el formato; los demás94previos menos esos tres permanecen. No se atribuye una limpieza de GradleDependency ni cambios de conducta.

Maven/Java21 con caché staging absoluta terminó BUILD SUCCESS en11.168s:42pruebas,0fallos/errores/omitidas y JAR empaquetado. El formato conserva modelos, strings, políticas, seguridad y catálogos; los8textblocks cuya indentación cambió tienen valores Java17 compilados idénticos. Task8 no ejecutó AVD ni nuevas llamadas reales.
# Correcciones finales de privacidad y persistencia

- Cuenta distingue el asistente local del envío externo explícito de mensaje y perfil; enviar historial requiere consentimiento separado de Firebase.
- Borrar datos locales exige confirmación, afecta solo al dueño seleccionado y cierra sesión/contexto. Invalida restauraciones y respuestas pendientes; limpia conversación, propuestas, perfil, rutinas y sesiones. Cancelar conserva los datos. No borra Firebase ni otras cuentas. La próxima entrada Google puede restaurar datos ya confirmados; lo pendiente únicamente local se pierde.
- Android Auto Backup y la transferencia entre dispositivos excluyen todos los dominios de datos, en reglas anteriores a API31 y en cloud/device-transfer API31+. Firebase sigue siendo el canal declarado. No se ha ejecutado una copia/restauración real del sistema.
- La rutina vigente es la aceptación más reciente según el `updatedAt` inmutable del registro ROUTINE. Nuevas aceptaciones avanzan respecto a las existentes; un empate entre dispositivos se resuelve por UUID ascendente, independiente de IDs SQLite y del orden de importación. Los snapshots legados no permiten inferir una elección adicional que nunca se guardó. Las sesiones conservan sus snapshots.
- Pruebas sintéticas cubren callback retenido después del cierre, restauración en ambos órdenes, borrado con dos dueños y conversación pendiente. No requieren Firebase ni IA reales; no amplían las afirmaciones de integración cloud provisionada.

## Entrega real de interfaz y correcciones finales

La revisión independiente de la ola final aprobó los siete hallazgos corregidos. Task13 también obtuvo Approved en cumplimiento y calidad: cabecera y navegación sobrias, accesos circulares con destinos reales, contadores locales, tarjetas y campos comunes, bordes dorados finos y texto blanco dominante. Se conservan cuatro pestañas, rutas, MVC y la política de movimiento/blur existente.

Las capturas reales normales y con fuente130%, teclado abierto y visor, junto a medición de widgets320/360dp, no mostraron solapes ni etiquetas recortadas en los escenarios comprobados. Los objetivos táctiles48/56dp y espacios24dp entre acciones,12dp horizontal y16dp vertical entre controles se mantienen. La última prueba focal5/5 y HomeNavigation2/2 tras formato numérico por locale pasaron. No equivalen a una evaluación física ni a inspección de Design/Split de Android Studio.

Tras transferir29archivos de corrección y23de interfaz con hashes coincidentes, la verificación del proyecto Android Studio real terminó BUILD SUCCESSFUL en5m6s: JVM112/0; Android68casos con66PASS y2supuestos opt-in sin configurar,0fallos funcionales/errores; debug, APK de pruebas y release correctos; lint0errores/99avisos. Los dos supuestos corresponden exclusivamente a BackendConnection y GeminiLive, sin repetir llamadas reales. Backend42/0 fue comprobado en la fuente revisada sin modificaciones posteriores del servidor. Los nueve avisos GradleDependency adicionales frente a90staging ya existían; no se presenta lint limpio de avisos.

Firebase Google y su restauración remota siguen sin provisionar. También quedan pendientes validación física arm64, RenderEffect habilitado en dispositivo compatible y revisión profesional de la técnica. La nueva petición de perfil físico, alimentación, escáner y ampliación3D se desarrolla como bloque posterior, sin presentarla como implementada en esta entrega.
