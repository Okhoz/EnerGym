# Compatible con Windows PowerShell 5.1. No llama al proveedor ni activa el backend.
$ErrorActionPreference = 'Stop'
Write-Host 'Crea una clave en https://console.groq.com/keys sólo si tu cuenta permanece en Free.'
Write-Host 'No añadas tarjeta ni aceptes Upgrade/Developer. Si el alta exige pago, detente.'
Write-Host 'Una clave no demuestra que la cuenta sea gratuita. Revisa el plan y las cuotas en la consola.'
$groqSecureKey = Read-Host 'Pega la clave del servidor (entrada oculta)' -AsSecureString
$groqKeyPointer = [IntPtr]::Zero
$groqPlainKey = $null
try {
	$groqKeyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($groqSecureKey)
	$groqPlainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($groqKeyPointer)
	if ([string]::IsNullOrWhiteSpace($groqPlainKey) -or $groqPlainKey.Length -gt 1024 -or
		$groqPlainKey -match '[\x00-\x20\x7F]') {
		throw 'Clave inválida: debe tener entre 1 y 1024 caracteres, sin espacios ni caracteres de control.'
	}
	[Environment]::SetEnvironmentVariable('GROQ_API_KEY', $groqPlainKey, 'User')
	Write-Host 'Clave guardada en la variable de usuario GROQ_API_KEY. Abre otra terminal para usarla.'
	Write-Host 'El proveedor sigue desactivado. No se ha hecho ninguna llamada ni se ha activado facturación.'
} finally {
	if ($groqKeyPointer -ne [IntPtr]::Zero) {
		[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($groqKeyPointer)
	}
	$groqPlainKey = $null
	if ($null -ne $groqSecureKey) { $groqSecureKey.Dispose() }
}
