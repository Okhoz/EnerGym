$ErrorActionPreference = 'Stop'
# Ejecutar desde cualquier directorio con Node/Java 21+ disponibles en PATH. No usa proyectos reales.
Push-Location (Split-Path $PSScriptRoot -Parent)
$savedConfigHome = $env:XDG_CONFIG_HOME
try {
    $env:CI = 'true'
    $env:FIREBASE_CLI_DISABLE_UPDATE_CHECK = 'true'
    $env:XDG_CONFIG_HOME = Join-Path $PSScriptRoot '.config'
    & node (Join-Path $PSScriptRoot 'node_modules/firebase-tools/lib/bin/firebase.js') emulators:exec --only firestore --project demo-energym --config firebase.json 'node --test firebase-tests/rules.test.mjs'
    if ($LASTEXITCODE -ne 0) { throw "Pruebas Firebase fallidas: $LASTEXITCODE" }
} finally { $env:XDG_CONFIG_HOME = $savedConfigHome; Pop-Location }
