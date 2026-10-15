# Backend online en Render Free

Estado 15-10-2026: candidato preparado y probado con fixtures locales; aún requiere secretos, despliegue y una prueba autenticada real. El propietario eligió **Groq Free** para la versión online. No activar facturación, cambiar a Developer/Blaze ni añadir servicios de pago. Las instrucciones locales de emparejamiento siguen disponibles para depuración.

## Configuración del servicio

Usar la rama que contenga este cambio. En Render seleccionar Web Service, Docker, plan **Free ($0)**, Root Directory `backend`, Dockerfile Path `Dockerfile.render`, Docker Build Context Directory `.`, Health Check Path `/health`, región Frankfurt y Auto Deploy desactivado. Dejar Docker Command vacío. `render.yaml` ofrece la configuración equivalente; no crea una base de datos ni disco. Los paths son relativos al Root Directory según la [documentación de monorepos](https://render.com/docs/monorepo-support).

El Dockerfile fija las imágenes oficiales Temurin 17.0.20.1_1 Jammy por digest. El ZIP Maven 3.9.16 se contrastó con su SHA512 oficial y su SHA256 calculado se fija para el wrapper. No hay claves en build args ni capas. La lista positiva de `.dockerignore` excluye secretos incluso si Render los añade al contexto. Actualizar estos pines con verificaciones equivalentes al renovar Java/Maven.

Copiar las variables no secretas de `deploy/render.env.example` al servicio. Añadir **GROQ_API_KEY** como secreto de entorno de la cuenta Groq Free ya elegida. El modelo es `openai/gpt-oss-120b`, que ya tiene una prueba sintética previa; no hay fallback. Confirmar el plan Free en la consola y revisar privacidad/ZDR y términos antes de datos personales; ver [Groq Free](groq-free.md).

Añadir **QUOTA_UID_SALT** como secreto aleatorio estable de al menos 32 caracteres. El Blueprint puede generarlo. Conservarlo y mantener `QUOTA_SCOPE=energym` en cada redeploy: cambiar sal reinicia el identificador de cuota del usuario. No guardar estos valores en Git, APK, URLs, logs ni comandos que los muestren.

## Cuenta de servicio y Firestore

La base Firestore `(default)` del mismo proyecto Firebase debe existir. Mantener el plan Spark; si una pantalla exige activar facturación, detener ese paso. Crear una cuenta de servicio exclusiva para cuotas, sin Owner, Editor, Firebase Admin ni permisos de administración de usuarios. Asignarle un rol personalizado con exactamente estas operaciones, según [IAM de Firestore](https://docs.cloud.google.com/firestore/native/docs/security/iam):

```text
datastore.databases.get
datastore.entities.get
datastore.entities.create
datastore.entities.update
```

Esos permisos permiten transacciones y leer/crear/actualizar documentos. IAM no se restringe aquí a una colección: proteger la clave como credencial de datos del proyecto. Las reglas móviles no limitan el acceso IAM del servidor. El código únicamente construye nombres bajo `assistant_quotas`; no acepta rutas del cliente. No necesita listar, borrar, administrar índices ni cuentas. Los permisos pueden tardar unos minutos en propagarse.

Generar una clave JSON de esa cuenta y cargar su contenido en Render → Environment → Secret Files con el nombre **firebase-service-account.json**. Se monta en `/etc/secrets/firebase-service-account.json`; `GOOGLE_APPLICATION_CREDENTIALS` ya apunta allí. El usuario del contenedor pertenece también al grupo 1000, requerido por Render para leer estos archivos sin ejecutar Java como root. El JSON debe declarar el mismo `project_id` que `FIREBASE_PROJECT_ID`; una credencial ausente o incompatible impide arrancar. No copiarlo al repositorio ni a Android. Rotar/revocar la clave en IAM si se filtra. Los [secret files de Render](https://render.com/docs/docker-secrets#accessing-secret-files-at-runtime) son configuración privada del servicio.

Las reglas incluyen denegación explícita de lectura/escritura del cliente a `assistant_quotas`. Desplegarlas con el procedimiento Firebase existente, después de verificar sus pruebas. Un usuario autenticado tampoco debe poder reducir contadores. El backend usa OAuth de la cuenta de servicio y reserva dos contadores en la misma transacción: global y UID salado. Los límites predeterminados son 20 intentos/día y 3/min por UID; 200/día y 20/min globales. Se cuenta antes de llamar al proveedor, incluso si éste falla. Cambios de minuto/día usan UTC. Reinicios y filesystem efímero de Render no reinician estas cuotas. No se guardan chats, emails ni UID en claro en estos documentos.

## Contrato Android y seguridad

Android obtiene el **Firebase ID token** de la sesión y envía `Authorization: Bearer <token>` a `POST /api/chat` con el JSON existente. No es el Google OAuth ID token del selector de cuenta y no se envía UID en el cuerpo. Se verifican firma RS256 con claves Google cacheadas, `kid`, proyecto `aud`, emisor, caducidad, `iat`, `auth_time` y `sub`. Las claves proceden de un endpoint Google fijo; no se confía en endpoints indicados por el JWT. Ver [verificación Firebase](https://firebase.google.com/docs/auth/admin/verify-id-tokens). Esta verificación básica no comprueba revocación/deshabilitación inmediata: un ID token previamente emitido puede valer hasta su expiración.

En modo Render no hay `/api/session`. Fallo de identidad: 401; claves Google no disponibles: 503 `AUTH_UNAVAILABLE`; Firestore no disponible: 503 `QUOTA_UNAVAILABLE`; cuota agotada: 429 `RATE_LIMITED`. No se sustituye un fallo persistente por cuotas en memoria. No hay reintentos automáticos al proveedor ni ante resultado de commit ambiguo. La reserva tiene un presupuesto monotónico de 25 s compartido por OAuth/transacciones/reintentos: no empieza otra operación ni concede acceso tras agotarlo. Una operación de red en curso termina con sus timeouts; un commit tardío puede haber consumido cuota sin llamar al modelo. La respuesta HTTP de Render admite hasta 90 s; el proveedor conserva su propio deadline.

El servidor enlaza `0.0.0.0:$PORT` y permite HTTP interno exclusivamente en modo Render explícito, con `RENDER=true`, autenticación Firebase y el hostname `RENDER_EXTERNAL_HOSTNAME` que asigna la plataforma. Para `/api/chat` exige Host esperado y un único `X-Forwarded-Proto: https`. Esto se apoya en el ingreso administrado de Render que termina TLS; no convierte esos headers en prueba de proxy en otro host. Usar el hostname `onrender.com` asignado, sin adivinarlo ni añadir dominio personalizado sin adaptar y probar la política. El antiguo modo local continúa en loopback; el modo externo directo conserva su TLS.

`GET /health` es una sonda pública sin secretos ni llamadas al proveedor/Firestore, accesible también desde el ingreso interno de salud. Un 200 confirma proceso y catálogo, no autorización real ni disponibilidad de Groq. Render documenta sus [health checks HTTP GET](https://render.com/docs/health-checks).

## Comprobación tras desplegar

Configurar el endpoint HTTPS exacto asignado en la versión Android de distribución y timeout suficiente (90 s) para arranque en frío; no duplicar POST automáticamente. Verificar `/health` 200, petición sin token 401, token de otro proyecto/firma incorrecta 401 y una petición sintética con Firebase válido que devuelva respuesta validada. Probar en entorno separado límites 1 por UID/global: segunda llamada 429 y redeploy conserva el bloqueo. No disminuir las cuotas de uso real para este ensayo. Verificar que ningún cliente puede leer/escribir la colección de cuotas y revisar que logs no contengan claves/tokens/cuerpos.

No se ha probado un despliegue Render ni Firestore remoto desde este candidato. Render Free se suspende tras inactividad; el primer acceso puede tardar alrededor de un minuto. Su almacenamiento local es efímero y sus límites pueden suspender el servicio; Render no recomienda Free para producción con disponibilidad garantizada. Mantener planes gratuitos sin tarjeta y aceptar esos límites; no usar pings para eludir la suspensión. [Límites de Render Free](https://render.com/docs/free).
