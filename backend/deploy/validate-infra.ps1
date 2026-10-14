param([switch]$DockerConfiguration)
# Revisión estática local; DockerConfiguration añade el parser real de Compose sin arrancar nada.
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$dockerfile = Get-Content -LiteralPath (Join-Path $backendRoot 'Dockerfile') -Raw
$pending = Get-Content -LiteralPath (Join-Path $backendRoot 'compose.production.pending.yaml') -Raw
$preview = Get-Content -LiteralPath (Join-Path $backendRoot 'compose.preview.yaml') -Raw
$proxy = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'nginx.conf.template') -Raw
if ($dockerfile -notmatch 'USER 10001:10001' -or $dockerfile -notmatch 'FROM \$\{BUILD_IMAGE\} AS build' -or $dockerfile -notmatch 'FROM \$\{RUNTIME_IMAGE\} AS runtime') {
	throw 'Falta la configuración multietapa/sin root.'
}
if ($pending -notmatch '127\.0\.0\.1:8443:8443' -or $pending -match '0\.0\.0\.0:.*:8080' -or $preview -match '(?m)^\s+ports:') {
	throw 'La configuración debe permanecer privada sin publicar el puerto del backend.'
}
if ($proxy -match 'proxy_ssl_verify\s+off' -or $proxy -notmatch 'proxy_ssl_name backend;' -or $proxy -notmatch 'proxy_ssl_trusted_certificate /run/secrets/backend_ca;') {
	throw 'El salto interno debe verificar TLS y SAN backend.'
}
foreach ($content in @($preview, $pending)) {
	if ($content -notmatch 'read_only: true' -or $content -notmatch 'GEMINI_ENABLED: "false"' -or $content -notmatch 'OPENAI_ENABLED: "false"') {
		throw 'Falta la configuración segura sin proveedores activos.'
	}
}
Write-Output 'PASS: guardas estáticas de infraestructura. No equivalen a parsear Compose ni ejecutar contenedores.'
if ($DockerConfiguration) {
	if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker CLI no disponible; validación real Compose pendiente.' }
	foreach ($imageVariable in @('ENERGYM_BUILD_IMAGE', 'ENERGYM_RUNTIME_IMAGE', 'ENERGYM_PROXY_IMAGE')) {
		$image = [Environment]::GetEnvironmentVariable($imageVariable)
		if ($image -notmatch '^(eclipse-temurin|nginx):[^\s@]+@sha256:[a-f0-9]{64}$') { throw "$imageVariable exige imagen oficial fijada por digest." }
	}
	if ([Environment]::GetEnvironmentVariable('ENERGYM_BUILD_IMAGE') -notmatch '^eclipse-temurin:17\..*-jdk-jammy@sha256:' -or
		[Environment]::GetEnvironmentVariable('ENERGYM_RUNTIME_IMAGE') -notmatch '^eclipse-temurin:17\..*-jre-jammy@sha256:' -or
		[Environment]::GetEnvironmentVariable('ENERGYM_PROXY_IMAGE') -notmatch '^nginx:.*alpine.*@sha256:') {
		throw 'Se requieren JDK/JRE Temurin17 y Nginx Alpine oficiales.'
	}
	if ([Environment]::GetEnvironmentVariable('ENERGYM_MAVEN_SHA256') -notmatch '^[a-f0-9]{64}$') { throw 'Checksum Maven inválido.' }
	Push-Location $backendRoot
	try {
		foreach ($file in @('compose.preview.yaml', 'compose.production.pending.yaml')) {
			& docker compose -f $file config --quiet
			if ($LASTEXITCODE -ne 0) { throw "Compose rechazó $file; no arrancar." }
		}
	} finally { Pop-Location }
	Write-Output 'PASS: configuración Compose parseada, sin build/run ni publicación.'
} else {
	Write-Output 'PENDING: Dockerfile build, parser Compose, nginx -t, redes/secret permisos y ejecución real.'
}
