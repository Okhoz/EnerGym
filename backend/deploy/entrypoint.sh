#!/bin/sh
# Carga exclusivamente secretos conocidos; no evalúa código ni imprime sus valores.
set -eu
fail() { printf '%s\n' 'Configuración privada inválida; revisa deploy/README.md.' >&2; exit 1; }
load_file() {
	name="$1"
	case "$name" in
		ENERGYM_TLS_PASSWORD) file=${ENERGYM_TLS_PASSWORD_FILE-}; direct=${ENERGYM_TLS_PASSWORD-} ;;
		ENERGYM_PROXY_TOKEN) file=${ENERGYM_PROXY_TOKEN_FILE-}; direct=${ENERGYM_PROXY_TOKEN-} ;;
		GEMINI_API_KEY) file=${GEMINI_API_KEY_FILE-}; direct=${GEMINI_API_KEY-} ;;
		GROQ_API_KEY) file=${GROQ_API_KEY_FILE-}; direct=${GROQ_API_KEY-} ;;
		ENERGYM_PAIRING_CODE) file=${ENERGYM_PAIRING_CODE_FILE-}; direct=${ENERGYM_PAIRING_CODE-} ;;
		*) fail ;;
	esac
	[ -n "$file" ] || return 0
	[ -z "$direct" ] || fail
	[ -f "$file" ] && [ -r "$file" ] || fail
	[ "$(wc -c < "$file")" -le 4096 ] || fail
	value=$(cat "$file")
	[ -n "$value" ] || fail
	# export recibe una sola asignación: el contenido del archivo nunca se interpreta.
	export "$name=$value"
}
for secret in ENERGYM_TLS_PASSWORD ENERGYM_PROXY_TOKEN GEMINI_API_KEY GROQ_API_KEY ENERGYM_PAIRING_CODE; do
	load_file "$secret"
done
if [ "${ENERGYM_TRANSPORT:-}" = 'render' ]; then
	[ "${RENDER:-}" = 'true' ] && [ "${ENERGYM_AUTH_MODE:-}" = 'firebase' ] || fail
	[ "${ENERGYM_DEV_MODE:-true}" = 'false' ] || fail
	[ -n "${RENDER_EXTERNAL_HOSTNAME:-}" ] && [ -n "${FIREBASE_PROJECT_ID:-}" ] || fail
	[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" ] && [ -r "$GOOGLE_APPLICATION_CREDENTIALS" ] || fail
	[ "$(wc -c < "$GOOGLE_APPLICATION_CREDENTIALS")" -le 65536 ] || fail
	[ -n "${QUOTA_UID_SALT:-}" ] && [ "${#QUOTA_UID_SALT}" -ge 32 ] || fail
elif [ "${ENERGYM_DEV_MODE:-true}" != 'true' ]; then
	[ -n "${ENERGYM_TLS_KEYSTORE:-}" ] && [ -r "$ENERGYM_TLS_KEYSTORE" ] || fail
	[ -n "${ENERGYM_TLS_PASSWORD:-}" ] || fail
	[ -n "${ENERGYM_PROXY_TOKEN:-}" ] || fail
	case "$ENERGYM_PROXY_TOKEN" in *[!A-Za-z0-9_-]*) fail ;; esac
	[ "${#ENERGYM_PROXY_TOKEN}" -ge 43 ] && [ "${#ENERGYM_PROXY_TOKEN}" -le 128 ] || fail
fi
# Heap explícito deja margen al TLS, bibliotecas y proceso healthcheck.
exec java -Xms32m -Xmx256m -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp -jar /app/backend.jar
