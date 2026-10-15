# Preparación Docker de EnerGym

**Estado: preparación verificable, sin despliegue real.** Docker no está instalado en el entorno de preparación. La APK se instala y ejecuta en Android; Docker ejecuta exclusivamente el backend. No existe un dominio, servidor ni certificado de producción configurado aquí.

Se conserva el contrato del servidor: HTTP sólo loopback; cualquier bind externo exige TLS directo. `compose.preview.yaml` arranca un backend privado sin publicar ningún puerto. Su sonda se ejecuta dentro del contenedor. `compose.production.pending.yaml` prepara una vista HTTPS del proxy sólo en `127.0.0.1:8443`, con HTTPS también hacia el backend y CA verificada. El backend nunca tiene un mapping de puerto host.

## Pendientes que impiden llamarlo producción

Task16 debe implementar autenticación Firebase, validación del secreto proxy y cuotas por UID/globales durables, y acordar sus secretos y volumen de estado. Hoy el servidor no comprueba `X-EnerGym-Proxy-Token`; no publicar este stack. El nombre del archivo deja visible este estado. TLS no sustituye autenticación ni autorización.

Las dos bases Compose fijan proveedor `none`. La superposición `compose.gemini.yaml` selecciona explícitamente Gemini Free con `gemini-3.5-flash-lite` y exige los archivos privados de clave y emparejamiento. Groq continúa como superposición opcional, sin fallback. Las pruebas privadas se documentan por separado; no se ha arrancado este stack con Docker.

La [tabla de precios Gemini](https://ai.google.dev/gemini-api/docs/pricing) ofrece entrada/salida Free para 3.5 Flash Lite y 3.8 Flash. Sus [términos](https://ai.google.dev/gemini-api/terms) exigen Paid Services para ofrecer clientes API a usuarios del EEE/CH/UK; la publicación gratuita en esos destinos sigue pendiente. Docker, la ubicación del host y Firebase no eliminan ese requisito. `/health` no consulta un modelo ni acredita cuota, autenticación o disponibilidad del proveedor.

## Imagen y build reproducible

Usar un host Docker Linux propio ya disponible. Esta preparación no instala Docker Desktop, WSL, Hyper-V ni modifica el sistema. No crear runners, hosts ni registros cobrables.

Las imágenes son oficiales y se suministran **con digest real verificado**, no con `latest`. El catálogo oficial consultado ofrece candidatos `eclipse-temurin:17.0.20.1_1-jdk-jammy`, `eclipse-temurin:17.0.20.1_1-jre-jammy` y `nginx:1.30.6-alpine3.24`; los tags pueden cambiar y aquí no se han descargado ni resuelto sus digests. Confirmar arquitectura y digest en el host antes de crear el archivo privado de configuración. El JDK compila con `release=17`; sólo el JRE y el JAR entran en runtime. El build ejecuta la suite; tests y JUnit no entran en el JAR.

Variables obligatorias de build:

| Variable | Valor requerido |
| --- | --- |
| `ENERGYM_BUILD_IMAGE` | `eclipse-temurin:<tag JDK17 jammy>@sha256:<digest real>` |
| `ENERGYM_RUNTIME_IMAGE` | `eclipse-temurin:<tag JRE17 jammy>@sha256:<digest real>` |
| `ENERGYM_PROXY_IMAGE` | `nginx:<tag Alpine>@sha256:<digest real>` para vista TLS |
| `ENERGYM_MAVEN_SHA256` | SHA256 verificado del ZIP exacto de Maven fijado en `.mvn/wrapper/maven-wrapper.properties` |

El Dockerfile exige checksum Maven y lo introduce únicamente en la etapa de build. No modifica el wrapper fuente. La fecha fija `project.build.outputTimestamp` evita marcas variables en JAR. Registrar imagen base/digests, arquitectura, checksum Maven, hash de fuentes, JAR e imagen resultantes. No se ha demostrado aún igualdad binaria entre dos builds Docker. Las dependencias y plugins del POM están versionados; una actualización deliberada exige nueva revisión.

En PowerShell, cargar sólo la configuración privada del operador en variables de entorno (no imprimirla), y desde `backend`:

```powershell
./deploy/validate-infra.ps1
# Con todas las entradas reales y Docker instalado en el host:
./deploy/validate-infra.ps1 -DockerConfiguration
docker compose -f compose.preview.yaml build
docker compose -f compose.preview.yaml up -d
docker compose -f compose.preview.yaml ps
docker compose -f compose.preview.yaml exec backend java -Xmx64m -cp /app/backend.jar com.energym.server.Healthcheck
docker compose -f compose.preview.yaml down
```

La vista HTTP no se navega desde el host porque no publica puertos; comprobarla con la sonda interna conserva la guarda loopback sin abrir HTTP. No usar `network_mode: host`, `--privileged`, puertos `8080:8080` ni bind HTTP `0.0.0.0`.

## Certificados y secretos para la vista TLS pendiente

Guardar materiales privados **fuera de `backend`, fuera del repositorio y del contexto Docker**. No introducirlos en imágenes, build args, comandos visibles o capturas. `.dockerignore` permite sólo el build necesario, excluye `.env`, datos locales y formatos de claves. Los secretos de Compose local son archivos montados; **no son una caja fuerte cifrada**. Proteger y respaldar el host y sus permisos. Con `file`, Compose no garantiza remapear UID/GID/modo: el operador debe asegurar lectura por UID/GID10001 y ausencia de lectura por usuarios ajenos, validándolo en el host real. No resolver fallos con `chmod 777` o root.

| Variable del host | Archivo requerido |
| --- | --- |
| `ENERGYM_BACKEND_TLS_FILE` | PKCS12 privado del backend, cadena válida y SAN DNS `backend` |
| `ENERGYM_BACKEND_TLS_PASSWORD_FILE` | Contraseña PKCS12 no vacía |
| `ENERGYM_BACKEND_CA_FILE` | CA privada en PEM que firmó el certificado del backend |
| `ENERGYM_BACKEND_TRUSTSTORE_FILE` | Truststore PKCS12 sólo con esa CA para la sonda |
| `ENERGYM_BACKEND_TRUSTSTORE_PASSWORD_FILE` | Contraseña del truststore no vacía |
| `ENERGYM_FRONTEND_CERT_FILE` | Certificado/cadena del endpoint HTTPS real, con SAN correcto |
| `ENERGYM_FRONTEND_KEY_FILE` | Clave privada del certificado externo |
| `ENERGYM_PROXY_TOKEN_FILE` | Mismo secreto para proxy y backend, 43–128 caracteres base64url; mínimo32 bytes aleatorios |

Archivos de contraseña/token: UTF-8 sin BOM, una sola línea, newline final opcional. Ni valores de ejemplo ni credenciales falsas son entradas válidas. El entrypoint consume las variables `_FILE` conocidas, rechaza archivos vacíos y no permite valor directo simultáneo. `GEMINI_API_KEY_FILE` es la entrada de la clave del proveedor seleccionado por `compose.gemini.yaml`; el archivo por sí solo no activa el proveedor. `ENERGYM_PAIRING_CODE_FILE` aporta el código privado de un solo uso y también es obligatorio en esa superposición. No recibe `JAVA_TOOL_OPTIONS` ni comandos Java arbitrarios del runbook. Un admin del host puede leer secretos/procesos: preservar el control de acceso al daemon Docker.

Proxy sin root en8443, backend sin root en8080 con TLS, root filesystem de sólo lectura, capacidades retiradas, sin elevación de privilegios, límites CPU/memoria/PIDs y tmpfs limitado. Sólo `backend_link` conecta proxy/backend y es red interna. El backend dispone de red de salida independiente para proveedores; no conectar terceros a ella ni a `backend_link`. El proxy sobrescribe el token y elimina headers forwarded. Nginx verifica CA/hostname upstream y no reintenta automáticamente una petición al backend. Las restricciones de entrada Nginx no sustituyen las cuotas globales/perUID de Task16; no hay persistencia de cuotas implementada por Task15.

Después de Task16, con contrato revisado y certificados reales, validar primero en loopback:

```powershell
docker compose -f compose.production.pending.yaml config --quiet
docker compose -f compose.production.pending.yaml build
docker compose -f compose.production.pending.yaml up -d
docker compose -f compose.production.pending.yaml ps
# Consultar https://<SAN real>:8443/health mediante conexión local que conserve ese SAN.
# Verificar CA y hostname; jamás curl -k ni desactivar validación TLS.
docker compose -f compose.production.pending.yaml down
```

No ejecutar `nginx -T`: expone el token renderizado en configuración. Access log desactivado, error log mínimo y logs Docker acotados; no agregar log de cuerpos, Authorization, secretos ni mensajes. Verificar rechazos API sin token Firebase, UID distinto, proxy token ausente/duplicado/incorrecto, cuotas agotadas y persistencia tras reiniciar. Healthcheck debe fallar ante CA/SAN incorrectos, certificado caducado o backend caído. Probar permisos, OOM, reinicio y parada antes de integrar. Docker mantiene `/tmp` y secretos sólo durante la vida del contenedor; no guardar datos durables allí.

## Operación y rollback

No se ofrece un archivo de puertos públicos ni se ha autorizado publicación externa. DNS, firewall, certificado público, servicio host y el endpoint fijo de Android se configuran sólo cuando exista un destino real y se hayan cerrado los pendientes. Mantener el último digest probado y sus secretos/certificados compatibles; detener el stack candidato y volver a esa imagen ante fallos. No usar `down -v` cuando Task16 añada estado durable. Antes de actualizar cuotas/estado, respaldar y probar restauración sin mensajes/perfiles en logs.

Rotar certificados y secreto proxy coordinadamente en ambos servicios, recrear contenedores y probar rechazo del token anterior. Cuando Task16 introduzca volúmenes, validar propietario10001, cifrado/respaldo y retención. No asumir que un filesystem readonly persiste contadores.

Opcionalmente servir una APK firmada como descarga mediante otro sitio HTTPS con hash SHA256/versionado. Ese servicio entrega un archivo; no ejecuta Android ni exige meter claves Gemini en la APK. No hay APK de producción falsa empaquetada en estas imágenes.

## Coste, licencias y fuentes primarias

Esta tarea no activa billing, no llama a modelos ni compra infraestructura. Un host/registro/transferencia de datos puede tener costes según su proveedor: no prometer alojamiento gratis por usar Docker. Revisar licencias y avisos de cada imagen y su sistema base al fijar los digests, además de las dependencias del JAR; no eliminar los avisos del proyecto. La preparación usa imágenes oficiales y no distribuye nuevas imágenes.

- [Catálogo oficial Temurin](https://raw.githubusercontent.com/docker-library/official-images/master/library/eclipse-temurin), [catálogo oficial Nginx](https://raw.githubusercontent.com/docker-library/official-images/master/library/nginx): tags/arquitecturas observados, no digests verificados localmente.
- [Maven Wrapper y verificación de checksum](https://maven.apache.org/tools/wrapper/).
- [Secrets Compose](https://docs.docker.com/reference/compose-file/secrets/), [servicios Compose y restricciones de secretos file](https://docs.docker.com/reference/compose-file/services/).
- [Nginx: verificación TLS upstream](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_ssl_verify).

La revisión estática local de `validate-infra.ps1` comprueba guardas, **no es un parser YAML/Dockerfile ni demuestra que un contenedor arranque**. La fase anterior comprobó YAML localmente; eso no sustituye al parser Compose ni al runtime. Sin Docker no se ejecutó `docker compose config`, build/run ni `nginx -t` de la imagen. Esos controles siguen pendientes en el host real.

## Seleccionar Gemini Free en el contenedor

`compose.gemini.yaml` se añade a una de las bases. Selecciona Gemini, desactiva los demás proveedores y fija literalmente `gemini-3.5-flash-lite`; la variable `GEMINI_MODEL` del host no cambia ese valor. La alternativa `gemini-3.8-flash` se selecciona explícitamente con el lanzador local, no mediante esta superposición. El gateway y el lanzador rechazan otros modelos. No hay fallback ni reintentos automáticos.

Preparar fuera del repositorio y contexto Docker dos archivos UTF-8 sin BOM, una línea cada uno: la clave Gemini ya existente y un código aleatorio nuevo de emparejamiento. El operador debe asegurar que los pueda leer UID/GID10001 y no otros usuarios del host. No insertar su contenido en YAML, argumentos, imágenes ni logs. El código se consume una vez y debe renovarse para otra conexión.

Definir las rutas de esos archivos en el entorno privado del operador y, desde `backend`, en el host con Docker ya instalado:

```powershell
$env:ENERGYM_GEMINI_KEY_FILE = 'C:\EnerGym-private\gemini-api-key.txt'
$env:ENERGYM_PAIRING_CODE_FILE = 'C:\EnerGym-private\pairing-code.txt'
# Adaptar las rutas al host propio; no contienen los secretos.
docker compose -f compose.preview.yaml -f compose.gemini.yaml config --quiet
docker compose -f compose.preview.yaml -f compose.gemini.yaml build
docker compose -f compose.preview.yaml -f compose.gemini.yaml up -d
docker compose -f compose.preview.yaml -f compose.gemini.yaml exec backend java -Xmx64m -cp /app/backend.jar com.energym.server.Healthcheck
```

Las variables host obligatorias son `ENERGYM_GEMINI_KEY_FILE` y `ENERGYM_PAIRING_CODE_FILE`; dentro del contenedor se montan como `GEMINI_API_KEY_FILE` y `ENERGYM_PAIRING_CODE_FILE`. Si falta cualquiera, Compose rechaza la superposición. El entrypoint rechaza además archivo ausente/vacío o valor directo simultáneo. No basta con tener `GEMINI_API_KEY` como variable de usuario en Windows: no aparece automáticamente en Docker.

Esta vista HTTP no publica puertos ni crea un endpoint Android. Para una conexión privada con el cliente se necesita la vista TLS pendiente, certificados reales y sus restricciones loopback. No publicar este stack mientras falten la autenticación Firebase, validación del secreto proxy y cuotas durables. Las cuotas efectivas de Gemini se consultan en AI Studio. Un 429 no cambia de modelo ni activa un plan de pago. La fase Gemini pasó 67 pruebas backend y ocho casos del lanzador; tres YAML se parsearon con Node y se comprobaron los flags, proveedor, modelo y secretos obligatorios. La llamada privada Gemini fue válida, sin Docker ni Android. Véase [Gemini Free](../docs/gemini-free.md). El build/run real del contenedor sigue pendiente en el host propio.

## Groq como opción explícita de compatibilidad

`compose.groq.yaml` sigue disponible al seleccionarlo expresamente en lugar de `compose.gemini.yaml`. No combinar ambas superposiciones: una configuración selecciona un solo proveedor. Groq requiere `ENERGYM_GROQ_KEY_FILE` y `ENERGYM_PAIRING_CODE_FILE`; monta clave y código como secretos de archivo. Sin pairing, Compose rechaza su arranque. Mantener Gemini/OpenAI desactivados en esa configuración.

La prueba real del 15-10-2026 con Groq fue local, sin Docker: respondió 200 en 2,3 s con perfil sintético y esquema válido. Es evidencia histórica de aquella configuración, no una prueba del contenedor ni la selección actual. Véase [Groq opcional](../docs/groq-free.md). Si se usa con personas reales siguen pendientes ZDR, autenticación y cuotas; la prueba no acredita calidad general ni disponibilidad permanente.
