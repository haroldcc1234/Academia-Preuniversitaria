$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar. Comprueba que Docker Desktop esté abierto.' }
Write-Host 'Abre http://localhost:3000. La primera carga puede tardar mientras inicia el servidor.'
