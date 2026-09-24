param(
    [Parameter(Mandatory)]
    [ValidateSet('user', 'product', 'order')]
    [string]$Service
)

Set-Location (Split-Path -Parent $PSScriptRoot)

Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
    }
}

if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Length -lt 32) {
    Write-Host "JWT_SECRET is missing or shorter than 32 characters in .env" -ForegroundColor Red
    exit 1
}

Write-Host "Starting $Service-service with .env loaded" -ForegroundColor Cyan
mvn -pl "$Service-service" spring-boot:run