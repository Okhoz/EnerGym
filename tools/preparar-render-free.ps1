# Prepara un archivo privado para Render; no publica ni llama al proveedor.
param(
	[string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$projectPath = [IO.Path]::GetFullPath($ProjectRoot)
$template = Join-Path $projectPath 'backend\deploy\render.env.example'
$destination = Join-Path $projectPath 'backend\.env.render.private'
if (-not (Test-Path -LiteralPath $template -PathType Leaf)) {
	throw 'No se encuentra backend/deploy/render.env.example en el proyecto indicado.'
}

# Sólo se admite la forma de clave Groq que puede escribirse sin escapes en dotenv.
$apiKey = [Environment]::GetEnvironmentVariable('GROQ_API_KEY', 'User')
if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -cnotmatch '^gsk_[A-Za-z0-9_-]{20,256}$') {
	throw 'Guarda primero la clave privada con tools/configurar-groq.ps1. No la pegues en el chat.'
}

# Mantener la sal al actualizar la configuración evita reiniciar las cuotas por UID.
$salt = $null
if (Test-Path -LiteralPath $destination -PathType Leaf) {
	$matchesInFile = @([IO.File]::ReadAllLines($destination) | Where-Object { $_.StartsWith('QUOTA_UID_SALT=') })
	if ($matchesInFile.Count -ne 1) {
		throw 'El archivo privado existente no tiene una sal única. Revísalo sin publicar su contenido.'
	}
	$salt = $matchesInFile[0].Substring('QUOTA_UID_SALT='.Length)
	if ($salt -cnotmatch '^[A-Za-z0-9_-]{43}$') {
		throw 'La sal existente no es válida; no se reemplaza para evitar reiniciar cuotas.'
	}
} else {
	$bytes = New-Object byte[] 32
	$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
	try {
		$generator.GetBytes($bytes)
		$salt = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
	} finally {
		$generator.Dispose()
		[Array]::Clear($bytes, 0, $bytes.Length)
	}
}

$configuration = [IO.File]::ReadAllText($template).TrimEnd() + "`r`nGROQ_API_KEY=$apiKey`r`nQUOTA_UID_SALT=$salt`r`n"

# La clave sólo debe ser legible por el usuario de Windows que la prepara.
if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) {
	[IO.File]::WriteAllText($destination, '')
}
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = New-Object Security.AccessControl.FileSecurity
$acl.SetOwner($identity)
$acl.SetAccessRuleProtection($true, $false)
$rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $destination -AclObject $acl
[IO.File]::WriteAllText($destination, $configuration, (New-Object Text.UTF8Encoding($false)))

$apiKey = $null
$configuration = $null
Write-Output 'Configuración privada preparada en backend\.env.render.private (ignorada por Git).'
Write-Output 'Contiene la clave Groq y la sal: úsala sólo en tu servicio Render Free y no la compartas en el chat.'
Write-Output 'No se ha subido ningún secreto, llamado a la IA ni activado facturación.'
