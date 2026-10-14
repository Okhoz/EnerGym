# Ejecutar personalmente en PowerShell; la clave se introduce oculta y nunca se escribe en archivos.
$ErrorActionPreference = 'Stop'
$taskSecret = Read-Host 'Pega una clave Gemini NUEVA que no hayas compartido en el chat (entrada oculta)' -AsSecureString
$taskSecretPointer = [IntPtr]::Zero
$taskPlainSecret = $null
try {
    $taskSecretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
    $taskPlainSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskSecretPointer).Trim()
    if ([string]::IsNullOrWhiteSpace($taskPlainSecret) -or $taskPlainSecret.Length -gt 1024 -or $taskPlainSecret -match '[\x00-\x20\x7f]') {
        throw 'Clave vacía o con caracteres no válidos. Vuelve a copiar la nueva clave sin espacios.'
    }
    [Environment]::SetEnvironmentVariable('GEMINI_API_KEY', $taskPlainSecret, 'User')
    [Environment]::SetEnvironmentVariable('GEMINI_API_KEY', $taskPlainSecret, 'Process')
    Write-Host 'Clave guardada en la variable GEMINI_API_KEY de tu usuario de Windows. No se ha llamado a la API ni activado facturación.'
} finally {
    if ($taskSecretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskSecretPointer)
    }
    $taskPlainSecret = $null
    if ($null -ne $taskSecret) { $taskSecret.Dispose() }
}
