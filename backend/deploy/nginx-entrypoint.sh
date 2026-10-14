#!/bin/sh
# Renderiza el único secreto sustituyendo una variable explícita, nunca headers de cliente.
set -eu
umask 077
fail() { printf '%s\n' 'Configuración TLS/proxy inválida; revisa el runbook.' >&2; exit 1; }
[ -r /run/secrets/proxy_token ] || fail
[ "$(wc -c < /run/secrets/proxy_token)" -le 129 ] || fail
ENERGYM_PROXY_TOKEN=$(cat /run/secrets/proxy_token)
case "$ENERGYM_PROXY_TOKEN" in *[!A-Za-z0-9_-]*) fail ;; esac
[ "${#ENERGYM_PROXY_TOKEN}" -ge 43 ] && [ "${#ENERGYM_PROXY_TOKEN}" -le 128 ] || fail
export ENERGYM_PROXY_TOKEN
envsubst '${ENERGYM_PROXY_TOKEN}' < /opt/energym/nginx.conf.template > /tmp/nginx.conf
unset ENERGYM_PROXY_TOKEN
# No ejecutar nginx -T: volcaría la configuración y el secreto.
nginx -t -q -c /tmp/nginx.conf
exec nginx -c /tmp/nginx.conf -g 'daemon off;'
