# Groq Free para EnerGym (preparación, desactivado)

El usuario pidió una IA gratuita. Se prepara GroqCloud Free con `openai/gpt-oss-120b`; no es ChatGPT. La alternativa permitida es `openai/gpt-oss-20b`. Ambos aparecen en el [plan Free](https://console.groq.com/docs/rate-limits) y admiten [JSON Schema estricto](https://console.groq.com/docs/structured-outputs). No hay modelo predeterminado, selección automática, reintentos ni fallback a un proveedor de pago.

## Activación pendiente

Esta entrega no crea cuenta, clave, tarjeta ni facturación y no hace inferencias reales. Antes de activarla, confirmar el plan **Free** en la consola y las condiciones presentadas en el alta; no añadir tarjeta ni aceptar Upgrade/Developer. Si el alta exige pago, detenerse. Una clave o el nombre de un modelo abierto no demuestra gratuidad. Este software no puede evitar cargos si el propietario cambia la cuenta a un plan pagado.

El [Services Agreement](https://console.groq.com/docs/legal/services-agreement) permite integrar la API para usuarios finales y contempla clientes del EEE; revisar la restricción de edad de 18 años y las condiciones efectivas antes del lanzamiento. Preparar autorización Firebase y cuotas durables por usuario/global corresponde a Task16; este adaptador no completa producción.

La configuración del **servidor** es explícita (el servidor no lee archivos `.env` automáticamente):

```text
ENERGYM_AI_PROVIDER=groq
GROQ_ENABLED=true
GROQ_MODEL=openai/gpt-oss-120b
GROQ_API_KEY=<clave privada del servidor>
```

Sin cualquiera de esas condiciones el proveedor queda desactivado. `TRUE`, `1` y modelos fuera de allowlist no sirven como opt-in. Mantener Gemini/OpenAI desactivados. Nunca introducir la clave en Android/APK, URLs, argumentos de consola, payloads o repositorio. El helper del workspace `tools/configurar-groq.ps1` pide la entrada oculta y guarda exclusivamente `GROQ_API_KEY` como variable de usuario Windows; no activa el proveedor. Las variables de usuario persisten sin cifrado y pueden leerlas procesos de ese usuario. No ejecutar el helper desde la automatización ni mostrar la clave. Para contenedores, el soporte `_FILE` de Groq y el runbook Docker se integrarán por separado tras revisión de Task15; no asumir que una variable Windows aparece automáticamente en Docker.

## Cuotas y límite provisional

A 14/10/2026 la [tabla publicada](https://console.groq.com/docs/rate-limits) para cada modelo permitido indica 30 peticiones/minuto, 1.000/día, 8.000 tokens/minuto y 200.000/día por organización; se aplica el primer límite alcanzado. La consola de la organización manda. Free no equivale a disponibilidad ilimitada ni garantiza gratuidad futura.

El adaptador limita el **payload HTTP serializado completo** a 18.000 bytes UTF-8 antes de enviar (incluye instrucciones, contexto consentido y esquema) y `max_completion_tokens` a 2.000 (salida y razonamiento). Rechaza el exceso con `429 RATE_LIMITED`, sin borrar historial, perfil o catálogo para encajarlo. El límite de bytes es una guarda operativa finita; **no es tokenización ni prueba de caber en 8K TPM**. Una estimación de bytes/3 deja 6K de entrada y 2K de generación, pero no es una cota garantizada: texto multilingüe, tokens especiales y esquema pueden cambiarla. La ventana de contexto 131K no es la cuota TPM. Varias consultas en un minuto pueden agotar Free incluso cuando cada payload pasa la guarda. No prometer 1.000 rutinas diarias.

Antes de activar, medir tokens de perfiles sintéticos representativos y máximos, confirmar los límites reales de la cuenta y ajustar la guarda si es necesario, además de imponer las cuotas durables de Task16. El adapter solo hace un intento por solicitud; 429 nunca cambia de modelo ni rota cuentas. No propaga `Retry-After` porque el contrato público actual no incluye ese campo.

## Contrato y privacidad

HTTPS fijo `https://api.groq.com/openai/v1/chat/completions`, sin variable de endpoint ni redirects. HTTP Java17, timeout de conexión 5s, solicitud 18s y deadline total de descarga 20s; respuesta máxima 65.536 bytes. Las claves y cuerpos de error no se registran ni retornan. 401/403 del proveedor producen `503 PROVIDER_NOT_CONFIGURED`; cuota y presupuesto `429 RATE_LIMITED`; timeout `504 PROVIDER_TIMEOUT`; errores HTTP restantes conservan `PROVIDER_UNAVAILABLE`; JSON/refusal/truncamiento producen `INVALID_PROVIDER_RESPONSE`.

`response_format.json_schema.strict=true` envía **exactamente** `proposal-schema.json`, incluidos `minItems`/`maxItems`, sin adaptación ni downgrade a JSON mode. Su aceptación exacta por Groq queda pendiente de una prueba sintética con clave autorizada: la documentación de [Structured Outputs](https://console.groq.com/docs/structured-outputs) no confirma expresamente esos dos keywords. Si Groq rechaza el esquema, la solicitud falla; no se reintenta con una versión menos estricta. La validación local permanece completa (métricas, carga, equipo, catálogo y duración); un esquema aceptado no reemplaza esa validación.

El sistema español incluye el catálogo filtrado y `exerciseContext` que prepara el servidor. No amplía el historial y solo lo recibe con consentimiento existente; ese consentimiento sigue separado de sincronización Firebase. El parser exige una choice, `finish_reason=stop`, mensaje assistant y contenido JSON objeto único; rechaza herramientas, rechazo, Markdown, duplicados, resultados parciales o ambiguos. Nunca ejecuta comandos de herramientas. El servidor añade correlación, versión del catálogo y aviso de propuesta; la aplicación exige vista previa y aceptación local.

`reasoning_effort=low` y `include_reasoning=false` ocultan la salida de razonamiento; **no desactivan su cómputo ni coste en tokens**. Se omite `store`: la [API de Groq](https://console.groq.com/docs/api-reference) declara que no está soportado. No afirmar retención cero. [Your Data](https://console.groq.com/docs/your-data) permite retención de inputs/outputs para fiabilidad/abuso hasta 30 días y excepciones legales; conserva metadatos. Habilitar ZDR en Data Controls antes de datos reales y revisar [DPA](https://console.groq.com/docs/legal/customer-data-processing-addendum), transferencias y condiciones aplicables. Mensaje, historial y campos libres pueden contener datos sensibles: no enviar identidad, email, UID ni diagnósticos, y usar solo perfiles sintéticos en la primera prueba. Ni ZDR ni el aviso eliminan esa posibilidad.

## Comprobaciones realizadas y pendientes

Las pruebas usan fixtures sintéticas y servidor HTTP loopback; la entrada de endpoint/timeout interna existe exclusivamente como seam de pruebas y no se expone por configuración. No se ha probado alta, claves reales, aceptación remota del schema ni calidad de rutinas españolas. Todo ello, plan Free sin pago, términos/edad, ZDR, tokenización, auth y cuotas siguen pendientes antes de habilitar llamadas.
