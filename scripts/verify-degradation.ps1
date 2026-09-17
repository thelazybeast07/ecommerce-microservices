<#
.SYNOPSIS
    Shows what happens when a downstream service is unavailable.

.DESCRIPTION
    Run verify-phase1.ps1 FIRST, then stop product-service (Ctrl+C in its window)
    and run this. It checks that order-service degrades gracefully instead of
    crashing: writes fail with 503, but reads keep working.

    Pass an existing order id to prove reads still work:
      .\scripts\verify-degradation.ps1 -OrderId <id-from-verify-phase1>

    Nothing is created, changed or deleted by this script.
#>

param(
    [string]$OrderId
)

$ErrorActionPreference = 'Stop'

$OrderSvc   = 'http://localhost:8083'
$ProductSvc = 'http://localhost:8082'

function Invoke-Api {
    param([string]$Method, [string]$Uri, $Body = $null)

    $params = @{ Method = $Method; Uri = $Uri; ContentType = 'application/json'; ErrorAction = 'Stop' }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 10) }

    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        $parsed = $null
        $raw = $r.Content
        # PowerShell returns a byte[] instead of a string when the content type is one it
        # does not recognise as text - Actuator's application/vnd.spring-boot.actuator.v3+json
        # is exactly such a type. Decode it before parsing.
        if ($raw -is [byte[]]) { $raw = [System.Text.Encoding]::UTF8.GetString($raw) }
        if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null } }
        return @{ Status = [int]$r.StatusCode; Body = $parsed }
    }
    # Untyped catch on purpose. HttpResponseException exists only in PowerShell 7;
    # naming it here makes this clause fail to resolve on Windows PowerShell 5.1 the
    # first time any request returns a non-2xx status.
    catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ Status = 0; Body = $null } }
        $content = $null
        try {
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $content = $_.ErrorDetails.Message }
            else {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $content = $reader.ReadToEnd(); $reader.Close()
            }
        } catch { }
        $parsed = $null
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        if ($content) { try { $parsed = $content | ConvertFrom-Json } catch { } }
        return @{ Status = [int]$resp.StatusCode; Body = $parsed }
    }
}

Write-Host ''
Write-Host ('=' * 70) -ForegroundColor DarkGray
Write-Host '  GRACEFUL DEGRADATION CHECK' -ForegroundColor Cyan
Write-Host ('=' * 70) -ForegroundColor DarkGray

# --- confirm product-service really is down -------------------------------

Write-Host ''
Write-Host '-> Confirming product-service is stopped' -ForegroundColor White
$productHealth = Invoke-Api -Method GET -Uri "$ProductSvc/actuator/health"
if ($productHealth.Status -eq 200) {
    Write-Host '   product-service is still RUNNING.' -ForegroundColor Yellow
    Write-Host '   Stop it first (Ctrl+C in its window), then re-run this script.' -ForegroundColor Yellow
    exit 1
}
Write-Host '   [OK] product-service is unreachable, as intended' -ForegroundColor Green

# --- order-service itself is still healthy --------------------------------

Write-Host ''
Write-Host '-> Is order-service itself still healthy?' -ForegroundColor White
$orderHealth = Invoke-Api -Method GET -Uri "$OrderSvc/actuator/health"
if ($orderHealth.Status -eq 200) {
    Write-Host "   [PASS] order-service is UP even though a dependency is down" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] order-service is not responding (HTTP $($orderHealth.Status))" -ForegroundColor Red
}

# --- writes fail fast, with 503 -------------------------------------------

Write-Host ''
Write-Host '-> Trying to PLACE an order (needs product-service for pricing)' -ForegroundColor White

$started = Get-Date
$attempt = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = [guid]::NewGuid().ToString()
    shippingAddressId = [guid]::NewGuid().ToString()
    items             = @(@{ productId = [guid]::NewGuid().ToString(); quantity = 1 })
}
$elapsed = ((Get-Date) - $started).TotalSeconds

Write-Host "   HTTP $($attempt.Status) after $([math]::Round($elapsed,1))s" -ForegroundColor DarkGray
if ($attempt.Body) {
    Write-Host "   title : $($attempt.Body.title)" -ForegroundColor DarkGray
    Write-Host "   detail: $($attempt.Body.detail)" -ForegroundColor DarkGray
}

if ($attempt.Status -eq 503) {
    Write-Host '   [PASS] 503 Service Unavailable - not 500' -ForegroundColor Green
    Write-Host '   WHY:  503 means "I am fine, a dependency is not - retry shortly".' -ForegroundColor Yellow
    Write-Host '   WHY:  500 would mean "I am broken", which would page the wrong person.' -ForegroundColor Yellow
}
elseif ($attempt.Status -eq 422) {
    Write-Host '   [INFO] Got 422 - user-service rejected the fake customer before' -ForegroundColor Yellow
    Write-Host '          product-service was ever called. Re-run with real IDs to see the 503,' -ForegroundColor Yellow
    Write-Host '          or stop user-service instead.' -ForegroundColor Yellow
}
else {
    Write-Host "   [FAIL] Expected 503 but got $($attempt.Status)" -ForegroundColor Red
}

if ($elapsed -lt 10) {
    Write-Host "   [PASS] Failed fast ($([math]::Round($elapsed,1))s) rather than hanging" -ForegroundColor Green
    Write-Host '   WHY:  connectTimeout/readTimeout bound the call. Without them a hung' -ForegroundColor Yellow
    Write-Host '         dependency would tie up a thread forever and exhaust the pool.' -ForegroundColor Yellow
}

# --- reads still work -----------------------------------------------------

Write-Host ''
Write-Host '-> Trying to READ an existing order (needs nothing downstream)' -ForegroundColor White

if (-not $OrderId) {
    Write-Host '   No -OrderId passed, so skipping this check.' -ForegroundColor Yellow
    Write-Host '   Re-run as: .\scripts\verify-degradation.ps1 -OrderId <id>' -ForegroundColor Yellow
}
else {
    $read = Invoke-Api -Method GET -Uri "$OrderSvc/api/v1/orders/$OrderId"
    if ($read.Status -eq 200) {
        Write-Host '   [PASS] The order read back fine with product-service down' -ForegroundColor Green
        Write-Host "   item : $($read.Body.items[0].productName) at $($read.Body.items[0].unitPrice)" -ForegroundColor DarkGray
        Write-Host '   WHY:  the product name and price were snapshotted onto the order when' -ForegroundColor Yellow
        Write-Host '         it was placed, so reading it needs no downstream call at all.' -ForegroundColor Yellow
    } else {
        Write-Host "   [FAIL] Read returned HTTP $($read.Status)" -ForegroundColor Red
    }
}

Write-Host ''
Write-Host ('=' * 70) -ForegroundColor DarkGray
Write-Host '  Restart product-service and ordering will work again.' -ForegroundColor Cyan
Write-Host ('=' * 70) -ForegroundColor DarkGray
Write-Host ''
