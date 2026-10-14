# Backend opcional EnerGym

Java 17+, Maven y JSON. Este servidor conecta el chat de la app a Gemini o, opcionalmente, OpenAI mediante el mismo contrato `/api/chat`. Devuelve `503 PROVIDER_NOT_CONFIGURED` mientras no se haya seleccionado y habilitado un proveedor completo; no inventa respuestas. Las pruebas del repositorio usan HTTP falso en localhost y no llaman a los modelos. No hay despliegue cloud ni facturación configurada por este código.

`ENERGYM_AI_PROVIDER=gemini`, `GEMINI_ENABLED=true` exacto, `GEMINI_MODEL` y `GEMINI_API_KEY` no vacíos habilitan Gemini. El modelo sugerido para pruebas es `gemini-3.5-flash-lite`, configurable sin modificar la app. `ENERGYM_AI_PROVIDER=openai`, `OPENAI_ENABLED=true` exacto, `OPENAI_MODEL` y `OPENAI_API_KEY` completos habilitan OpenAI, que puede facturar. Sin selección (o selección desconocida) todo queda desactivado, incluso con ambas claves y ambos flags. No hay fallback entre proveedores ni reintentos automáticos. La clave permanece en el servidor y nunca viaja a Android.

## Compilar y ejecutar

Desde esta carpeta, con JDK 17 o superior y `JAVA_HOME` configurado:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$PWD/.cache/m2" test package
$env:ENERGYM_AI_PROVIDER = 'none'
$env:GEMINI_ENABLED = 'false'
$env:OPENAI_ENABLED = 'false'
$env:ENERGYM_PAIRING_CODE = '<codigo-aleatorio-privado-de-32-caracteres>'
java -jar target/energym-backend-1.0.0.jar
```

El wrapper de Apache Maven 3.9.16 se copió sin modificar ANKA. La primera compilación descarga solo dependencias públicas gratuitas de Maven Central. `.env.example` contiene una plantilla; Java lee variables del proceso y no carga archivos .env. El código debe ser aleatorio (24–128 caracteres); no reutilizar el marcador de ejemplo.

## Gemini en Windows para pruebas de desarrollo

Crear una clave nueva en [Google AI Studio](https://aistudio.google.com/api-keys) para un proyecto sin billing habilitado. Si una clave se ha compartido en un chat o captura, revocarla y sustituirla primero. No pegarla aquí, en Android, en un comando visible ni en `.env`. Ejecutar personalmente en PowerShell 7:

```powershell
.\configurar-gemini.ps1
```

La entrada está oculta mediante `Read-Host -AsSecureString`. Guarda la clave como variable `GEMINI_API_KEY` del usuario de Windows y del proceso actual, sin imprimirla, llamar a la API ni activar billing. Una variable de usuario es accesible a procesos de esa cuenta; no equivale a un almacén cifrado de secretos. No añade flags de activación. No se debe verificar con `echo $env:GEMINI_API_KEY`. Para eliminarla, establecer la variable de usuario y proceso a `$null` usando `Environment.SetEnvironmentVariable`.

Después de compilar, configurar `JAVA_HOME` con JDK 17+ y `ENERGYM_PAIRING_CODE` con el código privado, y ejecutar:

```powershell
.\start-server.ps1 -Provider Gemini -CheckConfiguration
.\start-server.ps1 -Provider Gemini
```

`start-server.ps1` consulta primero variables del proceso y luego del usuario para la clave y el código. Inyecta el opt-in únicamente en su proceso Java si se indica `-Provider Gemini`; usa `-Model` o `GEMINI_MODEL`, con `gemini-3.5-flash-lite` sugerido por defecto en este lanzador. Sin `-Provider`, inicia el backend con ambos proveedores desactivados y elimina las claves del proceso hijo. `-CheckConfiguration` solo verifica configuración y JAR, sin iniciar Java ni probar la credencial. El lanzador no cambia flags de la sesión padre, no escribe archivos de secretos y no abre ventanas adicionales.

Para lanzamiento directo con Java, configurar las cuatro variables Gemini anteriores en el proceso. Para OpenAI, indicar explícitamente `-Provider OpenAI -Model <modelo>` y disponer de su clave; no está autorizado ni habilitado por la selección de Gemini. No se puede comprobar la cuota o el estado de billing a partir de una clave: revisar el proyecto en AI Studio. El servidor no activa billing, no realiza cambios de proyecto y no garantiza impedir cargos si el propietario entrega una clave de un proyecto ya facturable.

La [tarifa oficial](https://ai.google.dev/gemini-api/docs/pricing) consultada el 14-10-2026 ofrece entrada y salida gratuitas de `gemini-3.5-flash-lite` en Free Tier, sujetas a [cuotas y límites del proyecto](https://ai.google.dev/gemini-api/docs/rate-limits), disponibilidad y cambios del servicio. Un 429 del proveedor se devuelve como `RATE_LIMITED` recuperable, sin cambiar de proveedor ni reintentar. Esta elección corresponde a pruebas de desarrollo, no a una promesa de IA gratuita ilimitada para una app publicada.

Los [términos de Gemini](https://ai.google.dev/gemini-api/terms) exigen Paid Services al ofrecer clientes API a usuarios del EEE, Suiza o Reino Unido; por tanto, distribuir la app conectada a usuarios de esas regiones requiere otra decisión y revisión antes de producción. Fuera de esas regiones, los servicios gratuitos pueden usar entradas y salidas para mejorar productos y tener revisión humana; no introducir información personal, confidencial o sensible en las pruebas. En EEE/Suiza/Reino Unido, los términos de uso de datos de Paid Services se aplican también a cuota gratuita. El proveedor puede mantener registros limitados para seguridad y cumplimiento. `store:false` evita almacenamiento recuperable de Interactions; no promete retención cero. Usar un perfil genérico y mensajes de prueba sin información de salud; el consentimiento de historial sigue siendo obligatorio y se elimina el historial cuando es falso.

La dirección predeterminada es `127.0.0.1:8080`, HTTP permitido únicamente con `ENERGYM_DEV_MODE=true` y enlace loopback. Para emulador/dispositivo de desarrollo conectado usar `adb reverse tcp:8080 tcp:8080` y URL de app `http://127.0.0.1:8080` solo debug. Release exige HTTPS.

Fuera de desarrollo o para enlace no loopback, configurar `ENERGYM_TLS_KEYSTORE` (PKCS12), `ENERGYM_TLS_PASSWORD` y certificado verificable por el cliente. TLS 1.2/1.3 directo. Cabeceras X-Forwarded-Proto no autorizan HTTP. No se ha desplegado ni probado un certificado real. Emparejamiento local no es autenticación de cuentas de producción.

## Contrato HTTP v1 para Task6

Todas las entradas/salidas son JSON UTF-8; POST exige Content-Type application/json. No hay CORS, query parameters ni campos desconocidos admitidos.

- `GET /health` → 200 `{"status":"ok","catalogVersion":1}`. No devuelve clave, token, modelo ni código.
- `POST /api/session`, sin Bearer, cuerpo exacto `{"pairingCode":"<codigo-privado>"}` → 201 `{"token":"<token-aleatorio>","expiresAtEpochSeconds":1790000000}`. El código configurado se consume una sola vez, incluso con solicitudes simultáneas; código inválido/usado o pairing deshabilitado → 401. Diez intentos globales/minuto → 429.
- `DELETE /api/session` con Authorization Bearer → 200 `{"revoked":true}`; token ausente/expirado → 401.
- `POST /api/chat` con `Authorization: Bearer <token>` y cuerpo con exactamente siete campos:

```json
{
  "conversationId": "conversation-1",
  "requestId": "request-1",
  "exerciseId": "bodyweight-squat",
  "message": "Explícame cómo hacerlo",
  "profile": {
    "sportId": "strength",
    "goal": "STRENGTH",
    "environment": "HOME",
    "focus": "SPECIFIC",
    "category": "NO_EQUIPMENT",
    "resources": [],
    "daysPerWeek": 3,
    "durationMinutes": 30,
    "level": "Principiante",
    "experience": "",
    "space": "",
    "preferences": "",
    "limitations": ""
  },
  "historyConsent": false,
  "history": []
}
```

conversationId/requestId son identificadores de 1–80 caracteres ASCII letras/dígitos/guion/guion bajo. exerciseId es ID del catálogo v1 o null. message es texto no vacío de hasta 2000 caracteres. profile admite objeto parcial (incluso {}) para preguntas; una propuesta requiere los trece campos completos. No mandar `historyConsent` dentro de profile: es el campo raíz. Campos libres del perfil hasta 500 caracteres; level no vacío. durationMinutes entero 5–240; daysPerWeek entero 1–7.

Enums idénticos al dominio Android:

| Campo | Valores |
|---|---|
| goal | STRENGTH, GENERAL_FITNESS, ENDURANCE, MOBILITY, SPORT_PRACTICE |
| environment | GYM, HOME, OUTDOORS, POOL |
| focus | SPECIFIC, COMPLEMENTARY |
| category | GYM, HOME_EQUIPMENT, NO_EQUIPMENT, SPORT |
| resources | DUMBBELLS, BARBELL, BANDS, MAT, BENCH, MACHINE, CHAIR, WALL, SUPPORT, BICYCLE, POOL |
| sportId | IDs en catalog-v1.json: strength, calisthenics, running, cycling, swimming, football, basketball, other |

resources no admite repetidos; NO_EQUIPMENT exige resources vacío. history admite hasta 12 mensajes exactos `{"role":"user|assistant","text":"hasta 1000 caracteres no vacíos"}`. Se valida siempre y se elimina antes del proveedor si historyConsent=false. No enviar sesiones históricas, correo, UID Google ni datos cloud: el contrato solo admite mensajes explícitamente autorizados. Servidor sin almacenamiento de conversaciones; el cliente mantiene estado separado por usuario y borra/revalida consentimiento al cambiar de cuenta.

Respuesta 200:

```json
{
  "text": "Ejemplo sin revisión profesional.",
  "missingFields": [],
  "proposal": {
    "title": "Propuesta de ejemplo",
    "items": [{
      "exerciseId": "bodyweight-squat",
      "estimatedMinutes": 5,
      "prescription": [
        {"key":"sets","value":"2"},
        {"key":"repetitions","value":"8"},
        {"key":"loadKg","value":"0"},
        {"key":"restSeconds","value":"60"}
      ],
      "justification": "Compatible con el perfil."
    }]
  },
  "commands": [{"type":"OPEN_EXERCISE","exerciseId":"bodyweight-squat"}],
  "catalogVersion": 1,
  "proposalNotice": "Ejemplo sin revisión profesional; requiere vista previa y aceptación local.",
  "conversationId": "conversation-1",
  "requestId": "request-1",
  "exerciseId": "bodyweight-squat"
}
```

proposal puede ser null; items 1–5, sin ejercicios duplicados; presupuesto total de estimatedMinutes ≤ duración del perfil. missingFields contiene solo claves de perfil (máximo trece sin repetidos). prescription es lista de pares key/value, que Task6 convierte a Map después de validar. El conjunto de campos depende de metricType: fuerza sets/repetitions/loadKg/restSeconds; isométrico sets/durationSeconds/restSeconds; resistencia timeMinutes/intervals (distanceKm opcional); movilidad durationSeconds/repetitions/side; práctica blocks/durationMinutes. Números finitos positivos, salvo carga/descanso/distancia que pueden ser cero; series/repeticiones/intervalos/bloques enteros. side: ambos/izquierdo/derecho. Se aceptan coma o punto decimal.

El único comando permitido en v1 es OPEN_EXERCISE sobre el exerciseId actual. No admite reproducción, rotación, capas musculares, 3D ni movimientos. Task7 podrá ampliar capacidades solo con manifiestos validados. Task6 debe correlacionar los tres IDs y rechazar respuestas tardías tras cambiar de contexto, revalidar perfil/recursos al aceptar y no modificar sesiones pasadas. Una propuesta permanece pendiente hasta aceptación local explícita.

Errores exactos: `{"error":"CODE","recoverable":true|false}`. Una solicitud válida añade conversationId/requestId/exerciseId a errores de proveedor/carga concurrente y respuestas 200; errores de autenticación/validación/tamaño pueden carecer de correlación.

| HTTP | error | recoverable |
|---|---|---|
| 400 | INVALID_REQUEST | false |
| 401 | UNAUTHORIZED | false |
| 404 | NOT_FOUND | false |
| 405 | METHOD_NOT_ALLOWED | false |
| 413 | REQUEST_TOO_LARGE | false |
| 415 | JSON_REQUIRED | false |
| 429 | RATE_LIMITED, SERVER_BUSY | true |
| 502 | INVALID_PROVIDER_RESPONSE, PROVIDER_UNAVAILABLE | true |
| 503 | PROVIDER_NOT_CONFIGURED, PROVIDER_UNAVAILABLE | true |
| 504 | PROVIDER_TIMEOUT | true |
| 500 | SERVER_ERROR | true |

En 503 PROVIDER_NOT_CONFIGURED, Task6 debe explicar que el conector opcional está desactivado y permitir continuar en el asistente guiado local gratuito, sin llamarlo IA de modelo. No realizar reintentos automáticos al proveedor.

## Validación, privacidad y límites

Modelo: ProposalValidator valida catálogo/compatibilidad/prescripciones. Controlador: ChatService coordina autenticación, consentimiento, límites y proveedor. Adaptador de entrada: Server solo convierte HTTP/JSON; ProviderFactory selecciona exactamente GeminiGateway u OpenAiGateway. No hay Android, SQL, cuenta Google o sincronización cloud en backend.

Token aleatorio de 256 bits, almacenado solo como SHA-256 en memoria, caduca en una hora y se revoca por DELETE o reinicio. Código también almacenado como hash y consumido atómicamente. Sin token válido no se llega al proveedor. Diez solicitudes de chat/minuto/token (incluyen solicitudes válidas a conector desactivado); Retry-After:60 en 429. Máximo dos llamadas concurrentes, pool HTTP de cuatro hilos, cola16/conexiones32. Request ≤32768 bytes, JSON profundidad20, duplicados/trailing JSON rechazados. Proveedor: conexión5s, petición18s, deadline total20s, respuesta≤65536 bytes descargados. Respuesta en español≤4000 caracteres. Sin logs de contexto, mensajes, perfiles, tokens, códigos, modelos o claves. Cache-Control:no-store. El código no configura SDKs de telemetría.

El catálogo es copia exacta versionada de app/src/main/assets/catalog.json. Contexto y candidatos se recomponen en servidor; IDs enviados por cliente o modelo nunca amplían el catálogo. Se valida objetivo/entorno/recursos/deporte/cobertura; cobertura PENDING_VALIDATION bloquea propuesta de esa especialidad. Carga externa exige requisito catalogado compatible; duración prescrita explícita no puede exceder estimación. Son controles de producto, no valoración médica/profesional.

OpenAI usa Responses en HTTPS fijo, modelo configurable, store:false, JSON schema estricto `text.format` con required y additionalProperties:false, max_output_tokens2000 y sin tools. Refusals/incomplete/malformed se rechazan; validación posterior del dominio sigue siendo obligatoria. store:false no promete ausencia de retención de seguridad del proveedor.

Gemini usa `POST https://generativelanguage.googleapis.com/v1beta/interactions`, clave en cabecera `x-goog-api-key`, `response_format` textual JSON con el mismo esquema de propuestas, `store:false`, `stream:false`, `background:false`, máximo 2000 tokens y sin tools ni `previous_interaction_id`. El modelo recomendado `gemini-3.5-flash-lite` mantiene su razonamiento predeterminado. Si se selecciona explícitamente el modelo exacto `gemini-3.8-flash`, fija `generation_config.thinking_level=low`, opción documentada para reducir el razonamiento y la latencia frente a su valor predeterminado `medium`; otros modelos configurables conservan su valor predeterminado y no reciben ese parámetro. `thinking_summaries=none` solo oculta resúmenes, no desactiva el razonamiento. El límite de tokens incluye pensamiento y respuesta. HTTPS fijo en producción, sin redirecciones. Solo acepta estado `completed`, pasos auxiliares `thought` documentados y exactamente un `model_output` con un único bloque `text`. El campo opcional `errors` debe ser una lista vacía; si contiene fallos o tiene un tipo inválido (incluido null), se rechaza incluso con estado `completed`. Rechazos, herramientas, estados parciales, salida ambigua, contenido corrupto y anotaciones de retrieval se rechazan; ProposalValidator valida después catálogo, comandos y prescripciones. Se mantienen conexión 5s, solicitud 18s, deadline total 20s (incluye el cuerpo) y descarga máxima 65536 bytes.

Documentación oficial consultada antes de implementar: [Structured Outputs de Gemini](https://ai.google.dev/gemini-api/docs/structured-output), [referencia Interactions](https://ai.google.dev/api/interactions-api), [Thinking y niveles compatibles](https://ai.google.dev/gemini-api/docs/thinking), [Structured Outputs para Responses](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [referencia Responses](https://developers.openai.com/api/reference/resources/responses/methods/create). Las pruebas usan proveedor falso o parseo de fixtures: cero llamadas reales.

## Verificación

`mvnw.cmd "-Dmaven.repo.local=$PWD/.cache/m2" test package`: 38 pruebas, HTTP localhost incluido, jar ejecutable. Cubre autenticación/expiry/revocación/one-use, tamaño/esquema, rate limit, timeouts de cabeceras y cuerpo, default disabled aun con credenciales, selección explícita sin fallback, razonamiento low solo en el modelo compatible, consentimiento de historial, catálogo/material/cobertura/prescripción, propuestas tipadas, comandos correlacionados, refusals/payload estricto, cuota externa y descarga limitada. No incluye comprobación real de modelo/clave/cuota/billing/certificado ni servicio cloud. Los scripts tienen comprobación de sintaxis y el lanzador se verifica con credenciales ficticias, sin ejecutar el ayudante de captura de claves reales.
