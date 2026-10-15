# Gemini Free para EnerGym

Estado 15-10-2026: Gemini es la selección predeterminada del lanzador local, con `gemini-3.5-flash-lite`. `None` permite arrancar sin proveedor; Groq y OpenAI requieren selección explícita y no son fallback. La nueva fase pasó 67 pruebas backend, ocho casos del lanzador y una única llamada privada sintética a Gemini 3.5 Flash Lite: respuesta 200 en 2.508 ms y propuesta validada de dos ejercicios. La clave `GEMINI_API_KEY` se mantiene en el entorno privado del propietario; esta documentación no la solicita ni la registra. Si se renueva, la comprobación debe usar la clave vigente guardada por el propietario.

## Precio, cuotas y alcance

La [tabla oficial de precios](https://ai.google.dev/gemini-api/docs/pricing), comprobada el 15-10-2026, muestra entrada y salida Free para `gemini-3.5-flash-lite` y `gemini-3.8-flash`. Esos son los únicos modelos que admite la allowlist Free del gateway y del lanzador. La selección de un modelo fuera de ella se rechaza antes de llamar a la API; no se sustituye por otro.

Consultar las cuotas y el estado efectivo del proyecto en [Google AI Studio](https://aistudio.google.com/). No se fija aquí un número de peticiones o tokens por minuto/día: depende del proyecto y de las condiciones vigentes. Una clave existente no demuestra que el proyecto siga en Free. Esta preparación no activa facturación, cambia de proyecto, acepta upgrades ni garantiza disponibilidad ilimitada.

Los [términos de Gemini API](https://ai.google.dev/gemini-api/terms) exigen Paid Services para ofrecer clientes API a usuarios del EEE, Suiza o Reino Unido. Por tanto, la publicación gratuita en esos destinos sigue pendiente; usar Docker o un host distinto no elimina ese requisito. Las pruebas privadas sintéticas se registran por separado y no se presentan como autorización de distribución ni activan pagos. Antes de publicar también faltan autenticación Firebase del backend, cuotas durables por usuario/globales, HTTPS real y el cliente Android automático con origen fijo.

## Inicio privado en PowerShell 7

Desde la raíz del proyecto, con el backend compilado y Java17+ disponible. El launcher lee la clave del proceso o del usuario Windows, sin imprimirla. Generar un código nuevo de un solo uso y copiarlo al portapapeles:

```powershell
$env:ENERGYM_PAIRING_CODE = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
Set-Clipboard -Value $env:ENERGYM_PAIRING_CODE
.\backend\start-server.ps1 -CheckConfiguration
.\backend\start-server.ps1
```

`-CheckConfiguration` valida la configuración local sin iniciar Java ni consultar el modelo; no acredita la validez remota de la clave o la cuota. El comando siguiente arranca Gemini; sólo las solicitudes al chat hacen inferencias. El launcher usa `gemini-3.5-flash-lite` si no se selecciona otro modelo mediante `-Model` o `GEMINI_MODEL`. Para la alternativa Free explícita: `.\start-server.ps1 -Provider Gemini -Model gemini-3.8-flash` desde `backend`.

Para un arranque sin proveedor:

```powershell
.\backend\start-server.ps1 -Provider None -CheckConfiguration
.\backend\start-server.ps1 -Provider None
```

El emparejamiento sigue siendo privado, no autenticación Firebase de producción. Pegar el código únicamente en «Cuenta → Conectar servidor de IA» y vaciar el portapapeles después de usarlo. No imprimir variables, usar transcripciones, añadir secretos a comandos ni guardarlos en el repositorio. Regenerar el código para otra conexión; el servidor consume cada código una vez. El launcher elimina del proceso Java las claves de los proveedores no seleccionados.

La dirección local es `127.0.0.1:8080`, HTTP sólo en desarrollo y loopback. Para el emulador de desarrollo, `adb reverse tcp:8080 tcp:8080` permite usar `http://127.0.0.1:8080` en la conexión debug. Release exige HTTPS. No se ha publicado un endpoint externo.

## Contrato y privacidad

La configuración del proceso Java selecciona `ENERGYM_AI_PROVIDER=gemini`, `GEMINI_ENABLED=true`, un modelo admitido en `GEMINI_MODEL` y la clave privada `GEMINI_API_KEY`. Lanzar el JAR directamente no infiere ese opt-in de una clave presente. No se usan claves Android ni archivos de cliente Firebase como clave del proveedor.

El adaptador usa HTTPS fijo `https://generativelanguage.googleapis.com/v1beta/interactions`, `x-goog-api-key`, esquema JSON de propuestas, `store:false`, `stream:false` y `background:false`, sin herramientas ni continuación remota de conversación. El máximo de salida es 2.000 tokens, incluidos los de pensamiento; conexión 5 s, solicitud 18 s y deadline total 20 s, con respuesta limitada a 65.536 bytes. `gemini-3.8-flash` usa `thinking_level=low`; 3.5 Flash Lite conserva su configuración de razonamiento. Ocultar resúmenes no elimina el cómputo de razonamiento.

Un 429 devuelve el error de cuota sin reintentar, cambiar de modelo/proveedor ni activar pagos. Las respuestas siguen pasando por la validación de catálogo, recursos, duración y prescripciones del servidor, y requieren vista previa y aceptación local en Android. No se guardan claves, cuerpos o mensajes en logs.

`store:false` evita el almacenamiento recuperable de Interactions; no promete retención cero ni cambia las condiciones del servicio Free. Revisar el tratamiento de datos en los [términos oficiales](https://ai.google.dev/gemini-api/terms). Las pruebas nuevas usan exclusivamente un perfil sintético, sin historial, correo, UID ni datos personales. El envío de historial sigue requiriendo consentimiento separado del respaldo Firebase.

## Docker y evidencia

`compose.gemini.yaml` selecciona el proveedor sobre una base Compose y exige `ENERGYM_GEMINI_KEY_FILE` y `ENERGYM_PAIRING_CODE_FILE` en el host. Se montan como secretos de archivo `GEMINI_API_KEY_FILE` y `ENERGYM_PAIRING_CODE_FILE`; el entrypoint rechaza archivos ausentes/vacíos o una variable directa simultánea. Preparar esos archivos fuera del repositorio y del contexto Docker, con lectura limitada para el servicio. [Runbook Docker](../deploy/README.md).

Docker no está instalado en este equipo. Un host Docker propio sigue siendo necesario para comprobar configuración Compose, build y arranque; no se ha demostrado un despliegue real. La vista preview no publica puertos, y la vista TLS sigue pendiente de los controles de producción.

La fase previa verificó backend 66/0, JVM Android 156/0, instrumentales Android 73/0 aplicables, Python 9/9 y reglas 11/0. Esas cifras no son resultados del nuevo cambio Gemini. El recorrido Gemini del 14-10-2026 y la prueba Groq del 15-10-2026 se conservan como evidencia histórica, sin atribuirlos a esta fase.

Resultado nuevo confirmado en el proyecto real: Maven offline `test package` terminó BUILD SUCCESS con 67 pruebas, 0 fallos, errores u omitidas; ocho casos del lanzador pasaron. Se parsearon tres YAML con Node y se comprobaron proveedor, modelo, flags y archivos secretos obligatorios; Docker continúa sin instalarse y build/run sigue pendiente.

Se solicitó **una llamada**, modo rutina, con la nueva clave de usuario guardada y confirmada por el propietario: `provider=gemini`, `model=gemini-3.5-flash-lite`, `syntheticOnly=true`, `httpStatus=200`, `elapsedMs=2508`, `validatedResponse=true`, `replyCharacters=157`, `proposalItems=2`, `catalogVersion=2`. El probe recorrió `ProviderFactory → ChatService → ProposalValidator`; no fue un nuevo recorrido Android de extremo a extremo. Solo se conservaron metadatos del resultado, sin clave ni cuerpo. No hubo reintentos, fallback ni activación de facturación.
