param([int]$Port = 8000)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path $projectRoot 'ml/.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python)) { throw 'Create ml/.venv and install ml/requirements-lock.txt first; see README.' }
Push-Location (Join-Path $projectRoot 'ml')
try {
    & $python -m uvicorn api.server:app --host 127.0.0.1 --port $Port
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }
