param(
	[ValidateSet('None', 'Groq', 'Gemini', 'OpenAI')][string]$Provider = 'None',
	[string]$Model = '',
	[string]$JavaExecutable = '',
	[switch]$CheckConfiguration
)
# El proceso Java recibe las variables; ejecutar este script no activa el proveedor en la sesión padre.
$ErrorActionPreference = 'Stop'
function Get-TaskEnvironmentValue([string]$Name) {
	$taskValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
	if ([string]::IsNullOrWhiteSpace($taskValue)) {
		$taskValue = [Environment]::GetEnvironmentVariable($Name, 'User')
	}
	return $taskValue
}
$taskJar = Join-Path $PSScriptRoot 'target/energym-backend-1.0.0.jar'
if (-not (Test-Path -LiteralPath $taskJar -PathType Leaf)) {
	throw 'Compila primero el backend con mvnw.cmd test package.'
}
if ([string]::IsNullOrWhiteSpace($JavaExecutable)) {
	if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
		$JavaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
	}
	else {
		$JavaExecutable = (Get-Command java -ErrorAction Stop).Source
	}
}
if (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) {
	throw 'Configura JAVA_HOME con JDK 17 o superior.'
}
$taskPairing = Get-TaskEnvironmentValue 'ENERGYM_PAIRING_CODE'
if ([string]::IsNullOrWhiteSpace($taskPairing) -or $taskPairing.Length -lt 24 -or $taskPairing.Length -gt 128) {
	throw 'Define ENERGYM_PAIRING_CODE con un código aleatorio privado de 24 a 128 caracteres antes de iniciar.'
}
$taskStart = [Diagnostics.ProcessStartInfo]::new()
$taskStart.FileName = $JavaExecutable
$taskStart.WorkingDirectory = $PSScriptRoot
$taskStart.UseShellExecute = $false
$taskStart.CreateNoWindow = $true
$taskStart.ArgumentList.Add('-jar')
$taskStart.ArgumentList.Add($taskJar)
$taskStart.Environment['ENERGYM_AI_PROVIDER'] = 'none'
$taskStart.Environment['GEMINI_ENABLED'] = 'false'
$taskStart.Environment['OPENAI_ENABLED'] = 'false'
$taskStart.Environment['GROQ_ENABLED'] = 'false'
$taskStart.Environment.Remove('GEMINI_API_KEY') | Out-Null
$taskStart.Environment.Remove('OPENAI_API_KEY') | Out-Null
$taskStart.Environment.Remove('GROQ_API_KEY') | Out-Null
$taskStart.Environment['ENERGYM_PAIRING_CODE'] = $taskPairing
$taskPlainKey = $null
try {
	if ($Provider -ne 'None') {
		$taskPrefix = if ($Provider -eq 'Gemini') {
			'GEMINI'
		} elseif ($Provider -eq 'Groq') {
			'GROQ'
		} else {
			'OPENAI'
		}
		$taskPlainKey = Get-TaskEnvironmentValue ($taskPrefix + '_API_KEY')
		if ([string]::IsNullOrWhiteSpace($taskPlainKey) -or $taskPlainKey.Length -gt 1024 -or $taskPlainKey -match '[\x00-\x20\x7f]') {
			throw 'Falta una clave válida del proveedor seleccionado. Groq: ejecuta tools/configurar-groq.ps1 personalmente desde la raíz del proyecto.'
		}
		if ([string]::IsNullOrWhiteSpace($Model)) {
			$Model = Get-TaskEnvironmentValue ($taskPrefix + '_MODEL')
		}
		if ($Provider -eq 'Gemini' -and [string]::IsNullOrWhiteSpace($Model)) {
			$Model = 'gemini-3.5-flash-lite'
		}
		$taskModelPattern = if ($Provider -eq 'Gemini') {
			'^[A-Za-z0-9._-]{1,100}$'
		} else {
			'^[A-Za-z0-9._:/-]{1,100}$'
		}
		if ([string]::IsNullOrWhiteSpace($Model) -or $Model -cnotmatch $taskModelPattern) {
			throw 'Indica un modelo válido mediante -Model o la variable del proveedor.'
		}
		if ($Provider -eq 'Groq' -and @('openai/gpt-oss-120b', 'openai/gpt-oss-20b') -cnotcontains $Model) {
			throw 'Groq requiere un modelo Free documentado explícito: openai/gpt-oss-120b u openai/gpt-oss-20b. Revisa el plan Free en la consola antes de activar.'
		}
		$taskStart.Environment['ENERGYM_AI_PROVIDER'] = $Provider.ToLowerInvariant()
		$taskStart.Environment[$taskPrefix + '_ENABLED'] = 'true'
		$taskStart.Environment[$taskPrefix + '_API_KEY'] = $taskPlainKey.Trim()
		$taskStart.Environment[$taskPrefix + '_MODEL'] = $Model
	}
	if ($CheckConfiguration) {
		Write-Host 'Configuración local válida. No se ha iniciado Java ni llamado a la API.';
		return
	}
	$taskProcess = [Diagnostics.Process]::Start($taskStart)
	try {
		$taskProcess.WaitForExit()
		if ($taskProcess.ExitCode -ne 0) {
			throw 'El backend terminó con error. Revisa la configuración y README sin publicar secretos.'
		}
	} finally {
		if (-not $taskProcess.HasExited) {
			$taskProcess.Kill()
		}
		$taskProcess.Dispose()
	}
} finally {
	$taskPlainKey = $null
	$taskStart.Environment.Remove('GEMINI_API_KEY') | Out-Null
	$taskStart.Environment.Remove('OPENAI_API_KEY') | Out-Null
	$taskStart.Environment.Remove('GROQ_API_KEY') | Out-Null
}
