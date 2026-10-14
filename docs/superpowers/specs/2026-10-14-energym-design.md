# EnerGym — diseño del MVP

## Objetivo y decisiones aprobadas

Crear una aplicación Android en Java con interfaces XML para planificar, ejecutar y registrar entrenamientos según deporte, entorno y material. El usuario aprobó un MVP con almacenamiento local y backend para OpenAI; la sincronización entre dispositivos queda para una fase posterior.

Proyecto identificado: `C:\Users\oscar\AndroidStudioProjects\EnerGym`, paquete `com.energym.app`. Actualmente contiene una actividad Java y una pantalla «Hello World». El código Gradle existente puede continuar en Kotlin DSL: todo el código de aplicación Android será Java y las vistas XML.

## Pantallas y navegación

Navegación principal: Inicio, Entrenamiento, Asistente e Historial. Inicio muestra la próxima rutina y accesos para crear una o continuar una sesión. Entrenamiento contiene Gimnasio, En casa con material, Sin material y Deportes y preparación física.

Un formulario recoge deporte, objetivo, nivel, experiencia, lugar, días por semana, duración, espacio, material específico, preferencias y limitaciones. El uso de historial en IA requiere una elección explícita, desactivada inicialmente. Silla, pared y apoyo se registran como recursos independientes; «sin material» no los presupone.

El detalle de rutina muestra ejercicios, justificación, prescripción, sustituciones compatibles e inicio de sesión. El detalle de ejercicio incluye técnica, recursos necesarios, músculos cuando exista información revisada y «Explícame cómo hacerlo».

Interfaz en español, Material Design, fondo claro, acentos verdes, tarjetas legibles, estados vacíos, etiquetas de accesibilidad y adaptación a barras del sistema. La información de disponibilidad debe aparecer junto al contenido correspondiente.

## Catálogo y cobertura

Catálogo extensible con identificadores estables para fuerza, calistenia, carrera, ciclismo, natación, fútbol, baloncesto y otras disciplinas. Cada entrada declara cobertura de entrenamiento específico y de preparación complementaria por separado.

El MVP entrega ejemplos utilizables de fuerza en gimnasio, fuerza en casa con mancuernas y peso corporal sin accesorios. Carrera y ciclismo admiten registro por tiempo, distancia e intervalos. Natación admite su registro correspondiente, condicionado al entorno. Los deportes sin programas especializados revisados muestran «contenido especializado pendiente de validación» y no reciben una rutina genérica etiquetada como específica.

Cada ejercicio declara modalidad, objetivos, entorno, recursos obligatorios, alternativas y estado de revisión. Los ejemplos de producto no se etiquetan como revisados por profesionales si no existe evidencia. El motor de compatibilidad aplica requisitos de recursos, entorno y objetivo antes de mostrar o guardar propuestas y sustituciones.

## Datos y almacenamiento

Ampliación solicitada por el usuario: cuenta individual vinculada a Google mediante Firebase Authentication y almacenamiento de rutinas, sesiones y perfil en Cloud Firestore bajo `users/{uid}`. Esta decisión sustituye la exclusión inicial de sincronización. SQLite es caché local separada por UID, no la única copia. La app restaura los datos remotos al entrar con el mismo usuario tras reinstalación. Solo los datos confirmados como sincronizados sobreviven a la desinstalación; la UI distingue pendientes de confirmados. Cambiar de cuenta cancela listeners y descarta estado visible de la cuenta anterior. Las reglas Firestore exigen `request.auth.uid == uid`; el cliente no confía en correo o un UID escrito por el usuario para determinar identidad.

El usuario no tiene todavía proyecto Firebase. Se entrega integración configurable y guía del plan Spark sin facturación, con Google habilitado, SHA de firma y configuración Android `com.energym.app`. No usar Storage ni Cloud Functions de pago. La sincronización utiliza IDs estables y escrituras idempotentes, snapshots de sesiones, pendientes persistidos y restauración que no sobrescriba cambios locales pendientes. Datos sensibles no se envían a IA por tenerlos sincronizados: el consentimiento sigue separado.

Arquitectura solicitada durante la implementación: Modelo–Vista–Controlador (MVC). Modelo: `domain/`, `catalog/` y `data/` contienen entidades, reglas y persistencia. Vista: layouts XML, actividades y fragmentos presentan estado y recogen eventos, sin decidir compatibilidad ni guardar directamente. Controlador: `controller/` coordina perfiles, creación/aceptación de rutinas, sesiones, historial, conversación y recursos visuales mediante interfaces de vista. Los controladores no dependen de widgets Android y sus decisiones se prueban como Java.

Persistencia local mediante SQLite y repositorios Java. Entidades: perfil de entrenamiento, deportes, ejercicios, rutinas versionadas, elementos de rutina, sesiones, registros de actividad y conversaciones. El catálogo inicial se carga desde recursos versionados. Los cambios de rutina crean una nueva versión; sesiones realizadas conservan una copia de su prescripción y sus métricas.

Registros tipados: fuerza (series, repeticiones, carga, descanso, esfuerzo), isométricos (duración y descanso), resistencia (tiempo, distancia e intervalos), movilidad (duración, repeticiones y lado), práctica deportiva (bloques y métricas declaradas). Valores con unidades, validación de límites y campos opcionales claramente identificados. No se convierten todas las actividades en series y repeticiones.

Una sesión permite registrar y guardar progreso antes de finalizar. El historial muestra resultados reales; no inventa estadísticas cuando no hay sesiones.

## Conversación y backend

Backend independiente en Java, con endpoint para conversación y propuestas estructuradas. La clave de OpenAI se configura exclusivamente en el servidor mediante entorno. Android utiliza HTTPS en producción; una configuración de desarrollo permite conectar al servidor local desde el emulador.

El backend valida autenticación mediante credenciales de sesión revocables, tamaños de entrada, límites de solicitudes y esquemas de respuesta; configura tiempos máximos y evita registrar secretos o conversaciones completas. Una credencial fija incrustada en el APK no se considera autenticación de producción. La entrega incluye configuración local, contrato de API e instrucciones para habilitar el proveedor real; desplegar y emitir credenciales requieren el entorno correspondiente.

El asistente recibe el ejercicio seleccionado y los datos autorizados. Solicita únicamente datos importantes faltantes, propone IDs existentes del catálogo y justifica la selección. Puede producir una propuesta nueva o ajustes a una rutina. Backend y Android vuelven a validar recursos, cobertura y métricas. La respuesta del modelo nunca se ejecuta como código.

Toda modificación se presenta como borrador con vista previa. Solo «Aceptar y guardar» cambia una rutina. Rechazar o abandonar conserva su versión previa. El historial de sesiones permanece intacto. Sin conexión o sin proveedor configurado, el catálogo y registro local funcionan; el chat muestra su estado real y no simula una respuesta de OpenAI.

## Anatomía y recursos de demostración

Actualización aprobada el 14-10-2026: buscar y utilizar recursos existentes de internet. Candidatos consultados: [Mixamo](https://helpx.adobe.com/creative-cloud/faq/mixamo-faq.html) para movimientos de personajes, [Z-Anatomy](https://github.com/Z-Anatomy/Models-of-human-anatomy) para anatomía estática con CC BY-SA 4.0 y [RepDB](https://github.com/RepDB/exercise-dataset) para ilustraciones con atribución. Mixamo requiere Adobe ID; RepDB reserva sus animaciones para una licencia comercial. Estas fuentes no acreditan por sí mismas revisión biomecánica de cada animación. No se realizará una compra sin autorización. Un modelo anatómico estático no se declarará animación de ejercicio y una animación WebP no se declarará visor 3D.

No se han proporcionado modelos ni animaciones anatómicas verificadas. La primera entrega incluye el contrato del visor, el registro de recursos y una alternativa técnica textual o ilustrada con su estado de revisión visible. La disponibilidad 3D se declara como pendiente cuando falta el recurso; no se fabrica una animación ni se presenta un movimiento generado como verificado.

Para incorporar un recurso 3D se exige manifiesto con ejercicio, archivo, licencia, fuente, estado de revisión y capacidades admitidas. El visor completo deberá admitir rotación, zoom, ángulos, transparencia, músculos principales/secundarios/estabilizadores, reproducción, pausa, velocidad, posiciones inicial/final, articulaciones, segmentos, rango de referencia y lateralidad. Las capacidades se habilitan únicamente si el recurso las respalda.

Comandos permitidos y validados: abrir recurso, seleccionar vista definida, resaltar región definida, pausar en marcador existente y modificar velocidad dentro de valores admitidos. Cada comando se restringe al recurso del ejercicio seleccionado. No acepta ángulos, músculos o marcadores inventados por el modelo. En ausencia del recurso se abre la alternativa correspondiente y se explica la limitación.

La demostración se etiqueta «Referencia, no evaluación personal». El rango se presenta como adaptable al usuario; no prescribe un ángulo universal ni simetría perfecta. No se incluye análisis por cámara en el MVP.

## Errores, privacidad y configuración

Errores de red, proveedor o validación se muestran sin perder borradores. Respuestas inválidas no guardan rutinas ni activan comandos. No hay clave de OpenAI en código, recursos o APK. Limitaciones personales e historial se envían solo si son necesarios y autorizados. Se ofrece borrar los datos locales; la política de retención del backend debe declararse en configuración de despliegue.

El repositorio conserva documentación de configuración y ejemplos de variables sin secretos. La ruta del proyecto localizado queda fuera de los directorios actualmente habilitados para escritura de este chat; antes de modificarla se deberá obtener acceso mediante el mecanismo disponible o trabajar en una copia autorizada identificada claramente.

## Validación y aceptación

El código incluye comentarios y Javadoc en español que expliquen responsabilidades MVC, contratos públicos, aislamiento por cuenta, validaciones, sincronización y límites de recursos. Evitar comentarios que se limiten a narrar getters o líneas obvias.

- Compilar el APK con Gradle y ejecutar pruebas unitarias del dominio.
- Verificar que ninguna rutina sin material incluya silla, pared o apoyo no declarados.
- Verificar sustituciones según material, entorno y objetivo.
- Verificar formularios y persistencia de métricas de cada modalidad.
- Verificar que el contexto de conversación mantiene el ejercicio seleccionado.
- Verificar que rechazar una propuesta no modifica la rutina y que aceptar conserva sesiones anteriores.
- Verificar rechazo de IDs desconocidos y comandos incompatibles con el recurso anatómico.
- Verificar estados sin red, sin clave y sin animación, y que no se presentan como funcionalidades disponibles.
- Inspeccionar pantallas en emulador si hay uno disponible; documentar cualquier limitación de ejecución.

La entrega se considera MVP funcional con catálogo, rutinas, sesiones y conexión configurable a IA. El visor anatómico 3D completo depende de incorporar recursos revisados y no se declara terminado en su ausencia. El despliegue público del backend y la sincronización no forman parte de esta primera entrega.
