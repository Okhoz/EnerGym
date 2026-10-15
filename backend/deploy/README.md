# Preparación Docker de EnerGym

**Estado: preparación verificable, sin despliegue real.** Docker no está instalado en el entorno de preparación. La APK se instala y ejecuta en Android; Docker ejecuta exclusivamente el backend. No existe un dominio, servidor ni certificado de producción configurado aquí.

Se conserva el contrato del servidor: HTTP sólo loopback; cualquier bind externo exige TLS directo. `compose.preview.yaml` arranca un backend privado sin publicar ningún puerto. Su sonda se ejecuta dentro del contenedor. `compose.production.pending.yaml` prepara una vista HTTPS del proxy sólo en `127.0.0.1:8443`, con HTTPS también hacia el backend y CA verificada. El backend nunca tiene un mapping de puerto host.

## Pendientes que impiden llamarlo producción

Task16 debe implementar autenticación Firebase, validación del secreto proxy y cuotas por UID/globales durables, y acordar sus secretos y volumen de estado. Hoy el servidor no comprueba `X-EnerGym-Proxy-Token`; no publicar este stack. El nombre del archivo deja visible este estado. TLS no sustituye autenticación ni autorización.

Ambos Compose fijan proveedor `none`, Gemini desactivado y OpenAI desactivado. El usuario ha revocado Gemini y exige una IA gratuita, sin billing ni fallback de pago; la investigación del proveedor sustituto sigue pendiente. Gemini queda como integración heredada opcional, nunca requisito exclusivo del despliegue. La investigación oficial previa no encontró una excepción al requisito de Paid Services para sus clientes en EEE/CH/UK: contenedores, ubicación del host y Firebase no resuelven ese bloqueo. No habilitar Gemini ni presentar este stack como servicio europeo gratuito listo. `/health` no consulta un modelo ni demuestra cuota, Firebase o disponibilidad del proveedor.

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

Archivos de contraseña/token: UTF-8 sin BOM, una sola línea, newline final opcional. Ni valores de ejemplo ni credenciales falsas son entradas válidas. El entrypoint consume las variables `_FILE` conocidas, rechaza archivos vacíos y no permite valor directo simultáneo. `GEMINI_API_KEY_FILE` sólo conserva compatibilidad heredada opcional; no activa un proveedor. Acordar el contrato de secretos del nuevo proveedor gratuito cuando se haya elegido y verificado. No recibe `JAVA_TOOL_OPTIONS` ni comandos Java arbitrarios del runbook. Un admin del host puede leer secretos/procesos: preservar el control de acceso al daemon Docker.

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

La revisión estática local de `validate-infra.ps1` comprueba guardas, **no es un parser YAML/Dockerfile ni demuestra que un contenedor arranque**. Sin Docker y sin parser YAML instalado no se ejecutó `docker compose config`, build/run ni `nginx -t` de la imagen. Esos controles siguen pendientes en el host real.

## Activar Groq Free en el contenedor

`compose.groq.yaml` es una superposición opcional sobre `compose.preview.yaml` o `compose.production.pending.yaml`. Fija `ENERGYM_AI_PROVIDER=groq`, `GROQ_ENABLED=true`, el modelo Free `openai/gpt-oss-120b` (o `openai/gpt-oss-20b` mediante `GROQ_MODEL`) y monta la clave como secreto `GROQ_API_KEY_FILE`; el entrypoint la carga sin imprimirla y rechaza definir a la vez `GROQ_API_KEY` directa.

1. Crear en el host, **fuera del repositorio**, un archivo UTF-8 sin BOM con la clave en una línea, legible solo por UID 10001.
2. `ENERGYM_GROQ_KEY_FILE=<ruta absoluta> docker compose -f compose.preview.yaml -f compose.groq.yaml up -d`.
3. Comprobar con la sonda interna. La primera llamada real (15/10/2026, perfil sintético, ejecución local sin Docker) respondió 200 en 2,3 s; ver `docs/groq-free.md`.

Antes de usuarios reales: ZDR activado en Groq, autenticación Firebase y cuotas por usuario (Task16). Mientras falten, mantener la vista sin publicar a Internet.
