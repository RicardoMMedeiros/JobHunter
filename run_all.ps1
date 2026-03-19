param(
  [int]$Port = 8000,
  [string]$BindHost = "127.0.0.1",
  [switch]$SkipPlaywrightInstall
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "backend"
$venv = Join-Path $backend ".venv"

if (!(Test-Path $backend)) {
  throw "Backend folder not found: $backend"
}

Push-Location $backend
try {
  if (!(Test-Path $venv)) {
    Write-Host "Creating venv..."
    python -m venv .venv
  }

  $activate = Join-Path $venv "Scripts\Activate.ps1"
  if (!(Test-Path $activate)) {
    throw "Venv activation script not found: $activate"
  }

  . $activate

  Write-Host "Upgrading pip..."
  python -m pip install --upgrade pip

  Write-Host "Installing requirements..."
  pip install -r requirements.txt

  if (-not $SkipPlaywrightInstall) {
    Write-Host "Installing Playwright browser (chromium)..."
    python -m playwright install chromium
  }

  Write-Host "Starting API on http://${BindHost}:${Port}"
  uvicorn main:app --reload --host $BindHost --port $Port
}
finally {
  Pop-Location
}
