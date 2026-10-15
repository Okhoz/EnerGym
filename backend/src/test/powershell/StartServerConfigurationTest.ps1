param(
	[string]$JavaExecutable = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
)
$ErrorActionPreference = 'Stop'
$taskBackend = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$taskFixture = Join-Path $taskBackend 'target/start-server-fixture'
$taskBin = Split-Path -Parent $JavaExecutable
New-Item -ItemType Directory -Force -Path (Join-Path $taskFixture 'target') | Out-Null
Copy-Item -LiteralPath (Join-Path $taskBackend 'start-server.ps1') -Destination (Join-Path $taskFixture 'start-server.ps1') -Force
@'
public class LauncherProbe {
  public static void main(String[] args) throws Exception {
    StringBuilder metadata = new StringBuilder("provider=" + System.getenv("ENERGYM_AI_PROVIDER") + "\n");
    for (String prefix : new String[]{"GEMINI", "GROQ", "OPENAI"}) {
      metadata.append(prefix + "_enabled=" + System.getenv(prefix + "_ENABLED") + "\n");
      metadata.append(prefix + "_model=" + System.getenv(prefix + "_MODEL") + "\n");
      metadata.append(prefix + "_key=" + (System.getenv(prefix + "_API_KEY") != null) + "\n");
      metadata.append(prefix + "_file=" + (System.getenv(prefix + "_API_KEY_FILE") != null) + "\n");
    }
    java.nio.file.Files.writeString(java.nio.file.Path.of("launcher-metadata.txt"), metadata);
  }
}
'@ | Set-Content -LiteralPath (Join-Path $taskFixture 'LauncherProbe.java') -Encoding utf8
& (Join-Path $taskBin 'javac.exe') --release 17 -encoding UTF-8 -d $taskFixture (Join-Path $taskFixture 'LauncherProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar la fixture del lanzador.' }
& (Join-Path $taskBin 'jar.exe') ('-J-Djava.io.tmpdir=' + $taskFixture) --create --file (Join-Path $taskFixture 'target/energym-backend-1.0.0.jar') --main-class LauncherProbe -C $taskFixture LauncherProbe.class
if ($LASTEXITCODE -ne 0) { throw 'No se pudo empaquetar la fixture del lanzador.' }

function Assert-TaskCondition([bool]$Condition, [string]$Message) {
	if (-not $Condition) { throw $Message }
}
function Invoke-TaskLauncher([string[]]$Arguments, [bool]$InvalidGeminiKey = $false) {
	$taskMetadata = Join-Path $taskFixture 'launcher-metadata.txt'
	if (Test-Path -LiteralPath $taskMetadata) { Remove-Item -LiteralPath $taskMetadata }
	$taskStart = [Diagnostics.ProcessStartInfo]::new()
	$taskStart.FileName = (Get-Process -Id $PID).Path
	$taskStart.WorkingDirectory = $taskFixture
	$taskStart.UseShellExecute = $false
	$taskStart.CreateNoWindow = $true
	$taskStart.RedirectStandardOutput = $true
	$taskStart.RedirectStandardError = $true
	foreach ($taskArg in @('-NoProfile', '-File', (Join-Path $taskFixture 'start-server.ps1'), '-JavaExecutable', $JavaExecutable) + $Arguments) {
		$taskStart.ArgumentList.Add($taskArg)
	}
	# Se sobrescriben todas las claves de proceso con fixtures no vacías: el script nunca necesita leer claves de usuario.
	$taskStart.Environment['ENERGYM_PAIRING_CODE'] = 'fixture-pairing-012345678901234567890123456789'
	$taskStart.Environment['ENERGYM_AI_PROVIDER'] = 'groq'
	foreach ($taskPrefix in @('GEMINI', 'GROQ', 'OPENAI')) {
		$taskStart.Environment[$taskPrefix + '_API_KEY'] = 'fixture-key'
		$taskStart.Environment[$taskPrefix + '_API_KEY_FILE'] = 'fixture-only-no-file'
		$taskStart.Environment[$taskPrefix + '_ENABLED'] = 'true'
		$taskStart.Environment[$taskPrefix + '_MODEL'] = 'fixture-model'
	}
	$taskStart.Environment['GEMINI_MODEL'] = 'gemini-3.5-flash-lite'
	if ($InvalidGeminiKey) { $taskStart.Environment['GEMINI_API_KEY'] = 'fixture invalid key' }
	$taskProcess = [Diagnostics.Process]::Start($taskStart)
	try {
		$taskOutput = $taskProcess.StandardOutput.ReadToEnd() + $taskProcess.StandardError.ReadToEnd()
		$taskProcess.WaitForExit()
		if (Test-Path -LiteralPath $taskMetadata) { $taskOutput += [IO.File]::ReadAllText($taskMetadata) }
		return @{ ExitCode = $taskProcess.ExitCode; Output = $taskOutput }
	} finally {
		$taskProcess.Dispose()
	}
}
function Assert-TaskValue($Result, [string]$Line) {
	Assert-TaskCondition (($Result.Output -split '\r?\n') -ccontains $Line) ('Metadato esperado ausente: ' + $Line)
}

$taskDefault = Invoke-TaskLauncher @()
Assert-TaskCondition ($taskDefault.ExitCode -eq 0) 'El lanzador predeterminado no terminó correctamente.'
Assert-TaskValue $taskDefault 'provider=gemini'
Assert-TaskValue $taskDefault 'GEMINI_enabled=true'
Assert-TaskValue $taskDefault 'GEMINI_model=gemini-3.5-flash-lite'
Assert-TaskValue $taskDefault 'GEMINI_key=true'
foreach ($taskPrefix in @('GROQ', 'OPENAI')) {
	Assert-TaskValue $taskDefault ($taskPrefix + '_enabled=false')
	Assert-TaskValue $taskDefault ($taskPrefix + '_key=false')
	Assert-TaskValue $taskDefault ($taskPrefix + '_file=false')
	Assert-TaskValue $taskDefault ($taskPrefix + '_model=null')
}
Assert-TaskValue $taskDefault 'GEMINI_file=false'

$taskNone = Invoke-TaskLauncher @('-Provider', 'None') $true
Assert-TaskCondition ($taskNone.ExitCode -eq 0) 'None explícito no debe necesitar una clave de IA válida.'
Assert-TaskValue $taskNone 'provider=none'
foreach ($taskPrefix in @('GEMINI', 'GROQ', 'OPENAI')) {
	Assert-TaskValue $taskNone ($taskPrefix + '_enabled=false')
	Assert-TaskValue $taskNone ($taskPrefix + '_key=false')
	Assert-TaskValue $taskNone ($taskPrefix + '_file=false')
	Assert-TaskValue $taskNone ($taskPrefix + '_model=null')
}

$taskAlternate = Invoke-TaskLauncher @('-Provider', 'Gemini', '-Model', 'gemini-3.8-flash')
Assert-TaskCondition ($taskAlternate.ExitCode -eq 0) 'El modelo Gemini alternativo permitido debe funcionar.'
Assert-TaskValue $taskAlternate 'GEMINI_model=gemini-3.8-flash'
$taskPaid = Invoke-TaskLauncher @('-Provider', 'Gemini', '-Model', 'gemini-2.5-pro')
Assert-TaskCondition ($taskPaid.ExitCode -ne 0 -and -not $taskPaid.Output.Contains('provider=')) 'Un modelo fuera de la lista Free no debe iniciar Java.'
$taskMissing = Invoke-TaskLauncher @('-Provider', 'Gemini') $true
Assert-TaskCondition ($taskMissing.ExitCode -ne 0 -and $taskMissing.Output.Contains('Gemini') -and $taskMissing.Output.Contains('configurar-gemini.ps1')) 'La clave inválida debe indicar la configuración Gemini correcta.'
$taskCheck = Invoke-TaskLauncher @('-CheckConfiguration')
Assert-TaskCondition ($taskCheck.ExitCode -eq 0 -and -not $taskCheck.Output.Contains('provider=')) 'CheckConfiguration no debe iniciar el JAR.'

$taskGroq = Invoke-TaskLauncher @('-Provider', 'Groq', '-Model', 'openai/gpt-oss-20b')
Assert-TaskCondition ($taskGroq.ExitCode -eq 0) 'La selección Groq explícita conserva compatibilidad.'
Assert-TaskValue $taskGroq 'provider=groq'
Assert-TaskValue $taskGroq 'GEMINI_key=false'
$taskOpenAi = Invoke-TaskLauncher @('-Provider', 'OpenAI', '-Model', 'fixture-model')
Assert-TaskCondition ($taskOpenAi.ExitCode -eq 0) 'La selección OpenAI explícita conserva compatibilidad.'
Assert-TaskValue $taskOpenAi 'provider=openai'
Assert-TaskValue $taskOpenAi 'GEMINI_key=false'
Write-Host 'PASS: 8 casos de lanzador con JAR local, claves ficticias y sin llamadas a proveedores.'
