# Backend opcional EnerGym

Java 17+, Maven y JSON. El modo gratuito de la app usa conversación guiada local por reglas (Task6). Este servidor devuelve `503 PROVIDER_NOT_CONFIGURED` mientras OpenAI esté desactivado; no inventa respuestas. No hay despliegue cloud, facturación configurada ni llamadas reales en las pruebas.

`OPENAI_ENABLED=true` exacto, `OPENAI_MODEL` explícito y `OPENAI_API_KEY` no vacíos son necesarios para habilitar el conector. Una clave existente por sí sola nunca lo activa. Habilitarlo permitiría llamadas potencialmente facturables y requiere una decisión futura del propietario; para mantener todo gratis dejarlo desactivado. La clave permanece en el proceso servidor.

## Compilar y ejecutar

Desde esta carpeta, con JDK 17 o superior y `JAVA_HOME` configurado:

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$PWD/.cache/m2" test package
$env:OPENAI_ENABLED = 'false'
$env:ENERGYM_PAIRING_CODE = '<codigo-aleatorio-privado-de-32-caracteres>'
java -jar target/energym-backend-1.0.0.jar
```

El wrapper de Apache Maven 3.9.16 se copió sin modificar ANKA. La primera compilación descarga solo dependencias públicas gratuitas de Maven Central. `.env.example` contiene plantillas vacías; el servidor lee variables del proceso y no carga archivos .env. El código debe ser aleatorio (24–128 caracteres); no reutilizar el marcador de ejemplo.

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

Modelo: ProposalValidator valida catálogo/compatibilidad/prescripciones. Controlador: ChatService coordina autenticación, consentimiento, límites y proveedor. Adaptador de entrada: Server solo convierte HTTP/JSON; OpenAiGateway encapsula el proveedor. No hay Android, SQL, cuenta Google o sincronización cloud en backend.

Token aleatorio de 256 bits, almacenado solo como SHA-256 en memoria, caduca en una hora y se revoca por DELETE o reinicio. Código también almacenado como hash y consumido atómicamente. Sin token válido no se llega al proveedor. Diez solicitudes de chat/minuto/token (incluyen solicitudes válidas a conector desactivado); Retry-After:60 en 429. Máximo dos llamadas concurrentes, pool HTTP de cuatro hilos, cola16/conexiones32. Request ≤32768 bytes, JSON profundidad20, duplicados/trailing JSON rechazados. Proveedor: conexión5s, petición18s, deadline total20s, respuesta≤65536 bytes descargados. Respuesta en español≤4000 caracteres. Sin logs de contexto, mensajes, perfiles, tokens, códigos, modelos o claves. Cache-Control:no-store. El código no configura SDKs de telemetría.

El catálogo es copia exacta versionada de app/src/main/assets/catalog.json. Contexto y candidatos se recomponen en servidor; IDs enviados por cliente o modelo nunca amplían el catálogo. Se valida objetivo/entorno/recursos/deporte/cobertura; cobertura PENDING_VALIDATION bloquea propuesta de esa especialidad. Carga externa exige requisito catalogado compatible; duración prescrita explícita no puede exceder estimación. Son controles de producto, no valoración médica/profesional.

OpenAI usa Responses en HTTPS fijo, modelo configurable, store:false, JSON schema estricto `text.format` con required y additionalProperties:false, max_output_tokens2000 y sin tools. Refusals/incomplete/malformed se rechazan; validación posterior del dominio sigue siendo obligatoria. store:false no promete ausencia de retención de seguridad del proveedor.

Documentación oficial de OpenAI consultada el 14-10-2026: [Structured Outputs para Responses](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [referencia Responses](https://developers.openai.com/api/reference/resources/responses/methods/create). Las pruebas usan proveedor falso o parseo de fixtures: cero llamadas reales.

## Verificación

`mvnw.cmd "-Dmaven.repo.local=$PWD/.cache/m2" test package`: 20 pruebas, HTTP localhost incluido, jar ejecutable. Cubre autenticación/expiry/revocación/one-use, tamaño/esquema, rate limit, timeout recuperable, default disabled aun con credenciales, consentimiento de historial, catálogo/material/cobertura/prescripción, comandos correlacionados y refusals/payload estricto. No incluye comprobación real de modelo/clave/certificado ni servicio cloud.
