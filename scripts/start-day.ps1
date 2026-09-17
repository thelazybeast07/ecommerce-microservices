<#
.SYNOPSIS
    Brings the development environment up at the start of a session.

.DESCRIPTION
    Checks Docker, starts PostgreSQL (and optionally pgAdmin), waits until the database
    is genuinely ready, verifies the credentials in .env actually work for all three
    service logins, and tells you what to do next.

    This script NEVER deletes data. It only starts containers.

.PARAMETER WithPgAdmin
    Also start the pgAdmin container at http://localhost:5050

.EXAMPLE
    .\scripts\start-day.ps1
    .\scripts\start-day.ps1 -WithPgAdmin
#>

param(
    [switch]$WithPgAdmin
)

$ErrorActionPreference = 'Stop'

function Write-Head($t) {
    Write-Host ''
    Write-Host ('=' * 66) -ForegroundColor DarkGray
    Write-Host "  $t" -ForegroundColor Cyan
    Write-Host ('=' * 66) -ForegroundColor DarkGray
}
function Ok($t)   { Write-Host "   [OK]   $t" -ForegroundColor Green }
function Bad($t)  { Write-Host "   [FAIL] $t" -ForegroundColor Red }
function Note($t) { Write-Host "          $t" -ForegroundColor DarkGray }
function Warn($t) { Write-Host "   [!]    $t" -ForegroundColor Yellow }

# Run from the project root regardless of where the script was invoked from
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

# ---------------------------------------------------------------------------
Write-Head 'STEP 1 - Is Docker Desktop running?'

$dockerOk = $false
try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $dockerOk = $true }
} catch { }

if (-not $dockerOk) {
    Bad 'Docker Desktop is not running'
    Note 'Open Docker Desktop from the Start menu and wait for the whale icon'
    Note 'near the clock to say "Docker Desktop is running", then re-run this script.'
    exit 1
}
Ok 'Docker Desktop is running'

# ---------------------------------------------------------------------------
Write-Head 'STEP 2 - Checking .env'

if (-not (Test-Path '.env')) {
    Bad '.env not found'
    Note 'Run:  copy .env.example .env    then edit it and replace every "change-me".'
    exit 1
}

$envVars = @{}
Get-Content '.env' | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $name, $value = $line -split '=', 2
        $envVars[$name.Trim()] = $value.Trim()
    }
}

$stillPlaceholder = $envVars.GetEnumerator() | Where-Object { $_.Value -eq 'change-me' }
if ($stillPlaceholder) {
    Bad 'Some values in .env are still "change-me"'
    $stillPlaceholder | ForEach-Object { Note "  $($_.Key)" }
    Note 'Open .env, set real values, then re-run.'
    exit 1
}
Ok ".env found with $($envVars.Count) values set"

# ---------------------------------------------------------------------------
Write-Head 'STEP 3 - Starting PostgreSQL'

docker compose up -d postgres | Out-Null
if ($LASTEXITCODE -ne 0) {
    Bad 'docker compose up failed - see the output above'
    exit 1
}

Write-Host '   waiting for the database to accept connections' -NoNewline -ForegroundColor DarkGray
$healthy = $false
foreach ($attempt in 1..40) {
    $state = (docker inspect --format '{{.State.Health.Status}}' ecommerce-postgres 2>$null)
    if ($state -eq 'healthy') { $healthy = $true; break }
    Write-Host '.' -NoNewline -ForegroundColor DarkGray
    Start-Sleep -Seconds 2
}
Write-Host ''

if (-not $healthy) {
    Bad 'PostgreSQL did not become healthy'
    Note 'Check the logs with:  docker compose logs postgres'
    exit 1
}
Ok 'PostgreSQL is healthy on localhost:5432'

# ---------------------------------------------------------------------------
Write-Head 'STEP 4 - Do the .env passwords actually work?'
# This is the check that catches the "password authentication failed" error BEFORE
# a service fails to start. Database roles keep the password they were created with,
# so editing .env afterwards makes the two drift apart.

$roleChecks = @(
    @{ Role = $envVars['USER_DB_USERNAME'];    Pass = $envVars['USER_DB_PASSWORD'];    Db = 'ecommerce_user_db' },
    @{ Role = $envVars['PRODUCT_DB_USERNAME']; Pass = $envVars['PRODUCT_DB_PASSWORD']; Db = 'ecommerce_product_db' },
    @{ Role = $envVars['ORDER_DB_USERNAME'];   Pass = $envVars['ORDER_DB_PASSWORD'];   Db = 'ecommerce_order_db' }
)

$mismatched = @()
foreach ($check in $roleChecks) {
    $probe = docker exec -e PGPASSWORD=$($check.Pass) ecommerce-postgres `
        psql -U $check.Role -d $check.Db -c 'SELECT 1' 2>&1
    if ($LASTEXITCODE -eq 0) {
        Ok "$($check.Role) can connect to $($check.Db)"
    } else {
        Bad "$($check.Role) CANNOT connect to $($check.Db)"
        $mismatched += $check
    }
}

if ($mismatched.Count -gt 0) {
    Write-Host ''
    Warn 'The password in .env does not match the database role.'
    Note 'This happens when .env is edited after the role was first created.'
    Note 'Fix it by resetting the role password to match .env (no data is lost):'
    Write-Host ''
    foreach ($m in $mismatched) {
        Write-Host "     docker exec -it ecommerce-postgres psql -U $($envVars['POSTGRES_SUPERUSER']) -c `"ALTER ROLE $($m.Role) WITH PASSWORD '$($m.Pass)'`"" -ForegroundColor White
    }
    Write-Host ''
    Note 'Then re-run this script.'
}

# ---------------------------------------------------------------------------
if ($WithPgAdmin) {
    Write-Head 'STEP 5 - Starting pgAdmin'
    docker compose --profile tools up -d pgadmin | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Ok 'pgAdmin starting at http://localhost:5050'
        Note "login: $($envVars['PGADMIN_EMAIL'])  /  the PGADMIN_PASSWORD from .env"
        Note 'It can take 30s on first run before the page loads.'
    } else {
        Bad 'pgAdmin failed to start'
    }
}

# ---------------------------------------------------------------------------
Write-Head 'READY'

Write-Host ''
Write-Host '  Running containers:' -ForegroundColor White
docker compose ps --format "     {{.Name}}  {{.Status}}"

Write-Host ''
Write-Host '  Next: start the services from VS Code' -ForegroundColor White
Note 'Run and Debug panel (Ctrl+Shift+D) -> "All three services" -> green play button'
Write-Host ''
Write-Host '  Then check everything works:' -ForegroundColor White
Note '.\scripts\verify-phase1.ps1'
Write-Host ''
