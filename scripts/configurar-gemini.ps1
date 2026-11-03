param([ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$Modelo = 'gemini-3.8-flash')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
Write-Host 'Crea una clave en https://aistudio.google.com/apikey usando un proyecto con nivel gratuito.'
Write-Host 'La clave se guardara en .env del servidor, excluido de Git. No compartas ese archivo.'
Write-Host 'Solo los balotarios se enviaran a Gemini. El asistente de alumnos seguira local.'
$secureKey = Read-Host 'Pega tu clave de Gemini (entrada oculta)' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer).Trim()
    if ($apiKey -notmatch '^[A-Za-z0-9_.-]{20,200}$') { throw 'La clave esta vacia o tiene un formato no valido.' }
    $lines = if (Test-Path -LiteralPath $envPath) { @(Get-Content -LiteralPath $envPath -Encoding UTF8) } else { @() }
    $values = [ordered]@{ BULLETIN_AI_PROVIDER='gemini'; GEMINI_API_KEY=$apiKey; GEMINI_MODEL=$Modelo; GEMINI_MIN_INTERVAL_MS='6500' }
    foreach ($entry in $values.GetEnumerator()) {
        $pattern = '^\s*' + [regex]::Escape($entry.Key) + '\s*='
        $lines = @($lines | Where-Object { $_ -notmatch $pattern }) + ($entry.Key + '=' + $entry.Value)
    }
    [IO.File]::WriteAllLines($envPath, [string[]]$lines, [Text.UTF8Encoding]::new($false))
    Write-Host 'Configuracion guardada. No se activo facturacion ni se cambio la cuenta de Google.'
    Write-Host 'Desde la carpeta del proyecto ejecuta: docker compose up -d --build'
    Write-Host 'Luego ingresa como Director > Publicar balotarios > Comprobar conexion.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    $apiKey=$null; $lines=$null; $values=$null; $secureKey.Dispose()
}
