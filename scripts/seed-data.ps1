<#
.SYNOPSIS
    Fills the shop with realistic data: customers, a catalogue, and order history.

.DESCRIPTION
    Everything goes through the real APIs, so passwords are hashed, security rules apply, and
    prices come from the catalogue - exactly as real traffic would. Nothing is ever deleted.

    Creates:
      - 1 store manager (promoted to ADMIN via one database UPDATE - no endpoint exists)
      - 8 customers with Indian names, addresses in real cities
      - 6 categories, 24 products with realistic names and INR prices
      - ~10 orders in a spread of states: delivered, shipped, paid, pending, cancelled

    Re-runnable: if a customer already exists (409), it logs in instead of failing.

.EXAMPLE
    .\scripts\seed-data.ps1
#>

$ErrorActionPreference = 'Stop'

$UserSvc    = 'http://localhost:8081'
$ProductSvc = 'http://localhost:8082'
$OrderSvc   = 'http://localhost:8083'

# One password for every seeded account, printed at the end.
$Password = 'Shop$ecure2026'

function Say($t)  { Write-Host "-> $t" -ForegroundColor White }
function Ok($t)   { Write-Host "   [OK] $t" -ForegroundColor Green }
function Skip($t) { Write-Host "   [--] $t" -ForegroundColor DarkGray }
function Die($t)  { Write-Host "   [XX] $t" -ForegroundColor Red; exit 1 }

function Invoke-Api {
    param([string]$Method, [string]$Uri, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = $Uri; ContentType = 'application/json'; ErrorAction = 'Stop' }
    if ($headers.Count -gt 0) { $params.Headers = $headers }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 10) }
    try {
        $r = Invoke-WebRequest @params -UseBasicParsing
        $raw = $r.Content
        if ($raw -is [byte[]]) { $raw = [System.Text.Encoding]::UTF8.GetString($raw) }
        $parsed = $null
        if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch { } }
        return @{ Status = [int]$r.StatusCode; Body = $parsed }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ Status = 0; Body = $null; Error = $_.Exception.Message } }
        $content = $null
        try {
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $content = $_.ErrorDetails.Message }
            else {
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $content = $reader.ReadToEnd(); $reader.Close()
            }
        } catch { }
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        $parsed = $null
        if ($content) { try { $parsed = $content | ConvertFrom-Json } catch { } }
        return @{ Status = [int]$resp.StatusCode; Body = $parsed }
    }
}

# Registers a customer, or logs in if the email already exists. Returns id + token.
<#
Endpoints in this platform return either a bare JSON array (addresses) or a paged
wrapper with the rows under .content (categories, products). This unwraps both, so a
caller never has to care which shape it got - and never silently iterates the wrapper
itself, which looks like "no results" and leads to a confusing 409 on the next create.
#>
function Get-Rows($Body) {
    if ($null -eq $Body) { return @() }
    if ($null -ne $Body.content) { return @($Body.content) }
    return @($Body)
}

function Ensure-Customer {
    param([string]$First, [string]$Last, [string]$Email, [string]$Phone)
    $r = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/customers" -Body @{
        firstName = $First; lastName = $Last; email = $Email; phone = $Phone; password = $Password
    }
    $login = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/auth/login" -Body @{ email = $Email; password = $Password }
    if ($login.Status -ne 200) { Die "Could not log in as $Email - was this email seeded with a different password before?" }
    $token = $login.Body.accessToken

    if ($r.Status -eq 201) {
        Ok "$First $Last  ($Email)"
        return @{ Id = $r.Body.id; Email = $Email; Token = $token; New = $true }
    }
    # 409: already there from an earlier run - find our id via our own token's record
    Skip "$First $Last already exists - logged in"
    # decode the token payload (middle part) to read our own id; padding-safe
    $payload = $token.Split('.')[1].Replace('-','+').Replace('_','/')
    switch ($payload.Length % 4) { 2 { $payload += '==' } 3 { $payload += '=' } }
    $json = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    return @{ Id = $json.sub; Email = $Email; Token = $token; New = $false }
}

function Ensure-Address {
    param($Customer, [string]$Line1, [string]$City, [string]$State, [string]$Pin)
    $r = Invoke-Api -Method GET -Uri "$UserSvc/api/v1/customers/$($Customer.Id)/addresses" -Token $Customer.Token
    if ($r.Status -eq 200) {
        $first = Get-Rows $r.Body | Where-Object { $_.id } | Select-Object -First 1
        if ($first) { return $first.id }
    }
    $r = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/customers/$($Customer.Id)/addresses" -Token $Customer.Token -Body @{
        addressLine1 = $Line1; city = $City; state = $State
        postalCode = $Pin; country = 'IN'; addressType = 'SHIPPING'
    }
    if ($r.Status -ne 201) { Die "Could not add address for $($Customer.Email) (HTTP $($r.Status))" }
    if (-not $r.Body.id) { Die "Address created for $($Customer.Email) but no id came back" }
    return $r.Body.id
}

# ---------------------------------------------------------------------------
Say 'Checking all three services are up'
foreach ($u in @($UserSvc, $ProductSvc, $OrderSvc)) {
    $h = Invoke-Api -Method GET -Uri "$u/actuator/health"
    if ($h.Status -ne 200) { Die "$u is not reachable - start the services first" }
}
Ok 'All three answered'

# ---------------------------------------------------------------------------
Say 'Store manager (ADMIN)'
$manager = Ensure-Customer -First 'Meera' -Last 'Joshi' -Email 'meera.joshi@rewamart.in' -Phone '+919826012345'
if ($manager.New) {
    $envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
    $superuser = 'postgres'
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*POSTGRES_SUPERUSER=(.+)$') { $superuser = $matches[1].Trim() }
    }
    docker exec ecommerce-postgres psql -U $superuser -d ecommerce_user_db `
        -c "UPDATE customers SET role = 'ADMIN' WHERE id = '$($manager.Id)'" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Die 'Could not promote the manager - is ecommerce-postgres running?' }
    # fresh token so it carries the new role
    $login = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/auth/login" -Body @{ email = $manager.Email; password = $Password }
    $manager.Token = $login.Body.accessToken
    Ok 'Promoted to ADMIN and re-logged-in'
}
$adminToken = $manager.Token

# ---------------------------------------------------------------------------
Say 'Customers'
$customerSpecs = @(
    @{ F='Ananya';  L='Sharma';    E='ananya.sharma87@gmail.com';   P='+919876543210'; A='42 MG Road';              C='Indore';    S='MP'; Pin='452001' },
    @{ F='Rohan';   L='Verma';     E='rohan.verma@outlook.com';     P='+919812345678'; A='18 Civil Lines';          C='Rewa';      S='MP'; Pin='486001' },
    @{ F='Priya';   L='Patel';     E='priya.patel92@gmail.com';     P='+919898989898'; A='B-204 Shanti Residency';  C='Ahmedabad'; S='GJ'; Pin='380015' },
    @{ F='Arjun';   L='Singh';     E='arjun.singh.dl@gmail.com';    P='+919911223344'; A='C-56 Lajpat Nagar II';    C='New Delhi'; S='DL'; Pin='110024' },
    @{ F='Kavita';  L='Iyer';      E='kavita.iyer@yahoo.in';        P='+919840012345'; A='7 Gandhi Street, Adyar';  C='Chennai';   S='TN'; Pin='600020' },
    @{ F='Vikram';  L='Malhotra';  E='vikram.malhotra@gmail.com';   P='+919820098200'; A='1101 Sea Breeze Towers';  C='Mumbai';    S='MH'; Pin='400050' },
    @{ F='Sneha';   L='Reddy';     E='sneha.reddy21@gmail.com';     P='+919000112233'; A='Plot 88, Jubilee Hills';  C='Hyderabad'; S='TS'; Pin='500033' },
    @{ F='Aarav';   L='Gupta';     E='aarav.gupta@hotmail.com';     P='+919755512345'; A='23 Arera Colony';         C='Bhopal';    S='MP'; Pin='462016' }
)
$customers = @()
foreach ($spec in $customerSpecs) {
    $c = Ensure-Customer -First $spec.F -Last $spec.L -Email $spec.E -Phone $spec.P
    $c.AddressId = Ensure-Address -Customer $c -Line1 $spec.A -City $spec.C -State $spec.S -Pin $spec.Pin
    $customers += $c
}

# ---------------------------------------------------------------------------
Say 'Categories'
$categoryNames = @{
    'Electronics'       = 'Audio, wearables, chargers and accessories'
    "Men's Clothing"    = 'Kurtas, jeans, shirts and t-shirts'
    "Women's Clothing"  = 'Kurta sets, sarees and everyday wear'
    'Home & Kitchen'    = 'Cookware, storage and small appliances'
    'Books'             = 'Fiction and non-fiction paperbacks'
    'Sports & Fitness'  = 'Home gym, yoga and outdoor games'
}
# Read the existing categories once so re-runs reuse them instead of colliding.
# size=100 because the default page is 20 and six categories could otherwise fall off
# a later page as the catalogue grows.
$existing = Invoke-Api -Method GET -Uri "$ProductSvc/api/v1/categories?size=100"
$existingCats = @{}
foreach ($row in (Get-Rows $existing.Body)) {
    if ($row.name) { $existingCats[$row.name] = $row.id }
}

$catIds = @{}
foreach ($name in $categoryNames.Keys) {
    if ($existingCats.ContainsKey($name)) {
        $catIds[$name] = $existingCats[$name]
        Skip "$name already exists"
        continue
    }
    $r = Invoke-Api -Method POST -Uri "$ProductSvc/api/v1/categories" -Token $adminToken -Body @{
        name = $name; description = $categoryNames[$name]
    }
    if ($r.Status -ne 201) { Die "Could not create category '$name' (HTTP $($r.Status)): $($r.Body.detail)" }
    $catIds[$name] = $r.Body.id
    Ok $name
}

# ---------------------------------------------------------------------------
Say 'Products (INR pricing)'
$productSpecs = @(
    @{ Sku='ELEC-BOAT-A141';  N='boAt Airdopes 141 TWS earbuds';           P=1299;  C='Electronics' },
    @{ Sku='ELEC-NOIS-CF2';   N='Noise ColorFit Pulse 2 smartwatch';       P=1799;  C='Electronics' },
    @{ Sku='ELEC-MI-PB20';    N='Mi 20000 mAh power bank';                 P=2199;  C='Electronics' },
    @{ Sku='ELEC-LOGI-M235';  N='Logitech M235 wireless mouse';            P=995;   C='Electronics' },
    @{ Sku='ELEC-SAMS-25W';   N='Samsung 25 W travel charger';             P=1499;  C='Electronics' },
    @{ Sku='ELEC-JBL-GO3';    N='JBL Go 3 portable speaker';               P=2799;  C='Electronics' },
    @{ Sku='MCLO-KURT-WHT';   N="Men's cotton kurta, white";               P=899;   C="Men's Clothing" },
    @{ Sku='MCLO-LEVI-511';   N="Levi's 511 slim fit jeans";               P=2999;  C="Men's Clothing" },
    @{ Sku='MCLO-ASOL-POLO';  N='Allen Solly polo t-shirt';                P=1099;  C="Men's Clothing" },
    @{ Sku='MCLO-PETR-SHRT';  N='Peter England formal shirt';              P=1299;  C="Men's Clothing" },
    @{ Sku='WCLO-ANRK-SET';   N="Women's Anarkali kurta set";              P=1599;  C="Women's Clothing" },
    @{ Sku='WCLO-SARE-BAN';   N='Banarasi silk blend saree';               P=2499;  C="Women's Clothing" },
    @{ Sku='WCLO-W-PLZO';     N='W solid palazzo pants';                   P=799;   C="Women's Clothing" },
    @{ Sku='WCLO-BIBA-DUP';   N='Biba printed dupatta';                    P=549;   C="Women's Clothing" },
    @{ Sku='HOME-PRES-INDC';  N='Prestige induction cooktop 1200 W';       P=2890;  C='Home & Kitchen' },
    @{ Sku='HOME-MILT-FLSK';  N='Milton Thermosteel flask 1 L';            P=1145;  C='Home & Kitchen' },
    @{ Sku='HOME-CELL-DIN18'; N='Cello opalware dinner set, 18 pieces';    P=1299;  C='Home & Kitchen' },
    @{ Sku='HOME-WCHF-BLND';  N='Wonderchef Nutri-Blend mixer';            P=2999;  C='Home & Kitchen' },
    @{ Sku='BOOK-PSY-MONEY';  N='The Psychology of Money, paperback';      P=299;   C='Books' },
    @{ Sku='BOOK-ATOM-HAB';   N='Atomic Habits, paperback';                P=449;   C='Books' },
    @{ Sku='BOOK-IKIGAI';     N='Ikigai, hardcover';                       P=350;   C='Books' },
    @{ Sku='SPRT-WAKE-YOGA';  N='Wakefit yoga mat, 6 mm';                  P=649;   C='Sports & Fitness' },
    @{ Sku='SPRT-NIVI-FTBL';  N='Nivia Storm football, size 5';            P=499;   C='Sports & Fitness' },
    @{ Sku='SPRT-COSC-BADM';  N='Cosco badminton racket set';              P=1150;  C='Sports & Fitness' }
)
# Pre-load whatever already exists so re-runs reuse products instead of failing on 409.
# The search endpoint has no sku filter, so we match SKUs on our side.
$skuMap = @{}
$page = Invoke-Api -Method GET -Uri "$ProductSvc/api/v1/products?size=100"
foreach ($item in (Get-Rows $page.Body)) {
    if ($item.sku) { $skuMap[$item.sku] = $item.id }
}

$products = @()
foreach ($p in $productSpecs) {
    if ($skuMap.ContainsKey($p.Sku)) {
        Skip "$($p.Sku) already exists"
        $products += @{ Id = $skuMap[$p.Sku]; Price = $p.P }
        continue
    }
    $r = Invoke-Api -Method POST -Uri "$ProductSvc/api/v1/products" -Token $adminToken -Body @{
        sku = $p.Sku; name = $p.N; description = $p.N
        price = $p.P; currency = 'INR'; categoryId = $catIds[$p.C]
    }
    if ($r.Status -eq 201) { Ok "$($p.N)  (Rs. $($p.P))"; $products += @{ Id = $r.Body.id; Price = $p.P } }
    else { Die "Could not create product $($p.Sku) (HTTP $($r.Status)): $($r.Body.detail)" }
}

# ---------------------------------------------------------------------------
Say 'Orders - a spread of real-looking history'

function Place-Order {
    param($Customer, $Items)
    $body = @{
        customerId = $Customer.Id
        shippingAddressId = $Customer.AddressId
        items = $Items
    }
    $r = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body $body -Token $Customer.Token
    if ($r.Status -ne 201) {
        Write-Host ''
        Write-Host "   Order for $($Customer.Email) failed with HTTP $($r.Status)" -ForegroundColor Red
        if ($r.Body) { Write-Host "   server said: $($r.Body | ConvertTo-Json -Compress -Depth 5)" -ForegroundColor DarkGray }
        Write-Host "   request was: $($body | ConvertTo-Json -Compress -Depth 5)" -ForegroundColor DarkGray
        Write-Host ''
        Write-Host '   A 500 means an unhandled exception inside order-service. The full stack' -ForegroundColor Yellow
        Write-Host '   trace is in the order-service terminal - look for the LAST "Caused by:" line.' -ForegroundColor Yellow
        exit 1
    }
    return $r.Body.id
}

function Advance-Order {
    param([string]$OrderId, [string[]]$Path)
    $reached = 'PENDING'
    foreach ($status in $Path) {
        $r = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$OrderId/status" -Token $adminToken -Body @{ status = $status }
        if ($r.Status -ne 200) {
            Die "Stuck at $reached -> $status (HTTP $($r.Status)): $($r.Body.detail)"
        }
        $reached = $status
    }
}

# These must follow the state machine in OrderStatus.java EXACTLY:
#   PENDING -> CONFIRMED -> PAYMENT_PENDING -> PAID -> PROCESSING -> SHIPPED -> DELIVERED
# PROCESSING is not optional - PAID cannot jump straight to SHIPPED. The service rejects
# any skipped step with 409, which is the state machine doing its job.
$toDelivered = @('CONFIRMED','PAYMENT_PENDING','PAID','PROCESSING','SHIPPED','DELIVERED')
$toShipped   = @('CONFIRMED','PAYMENT_PENDING','PAID','PROCESSING','SHIPPED')
$toPaid      = @('CONFIRMED','PAYMENT_PENDING','PAID')

# item helper: n-th product, quantity q
function It($n, $q) { @{ productId = $products[$n].Id; quantity = $q } }

$o = Place-Order $customers[0] @( (It 0 1), (It 19 1) );            Advance-Order $o $toDelivered; Ok 'Ananya - earbuds + a book - DELIVERED'
$o = Place-Order $customers[1] @( (It 14 1) );                       Advance-Order $o $toDelivered; Ok 'Rohan - induction cooktop - DELIVERED'
$o = Place-Order $customers[2] @( (It 10 1), (It 13 2) );            Advance-Order $o $toShipped;   Ok 'Priya - kurta set + dupattas - SHIPPED'
$o = Place-Order $customers[3] @( (It 7 1), (It 8 2) );              Advance-Order $o $toShipped;   Ok 'Arjun - jeans + polos - SHIPPED'
$o = Place-Order $customers[4] @( (It 21 1), (It 23 1) );            Advance-Order $o $toPaid;      Ok 'Kavita - yoga mat + badminton set - PAID'
$o = Place-Order $customers[5] @( (It 5 1) );                        Advance-Order $o $toPaid;      Ok 'Vikram - JBL speaker - PAID'
$o = Place-Order $customers[6] @( (It 11 1) );                                                      Ok 'Sneha - saree - PENDING'
$o = Place-Order $customers[7] @( (It 18 1), (It 20 1), (It 19 1) );                                Ok 'Aarav - three books - PENDING'

# one realistic cancellation: customer changed their mind while still PENDING
$o = Place-Order $customers[0] @( (It 3 1) )
$r = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$o/cancel" -Token $customers[0].Token
if ($r.Status -eq 200) { Ok 'Ananya - mouse - CANCELLED by the customer' }

# ---------------------------------------------------------------------------
Write-Host ''
Write-Host ('=' * 68) -ForegroundColor DarkGray
Write-Host '  The shop now has real-looking data' -ForegroundColor Cyan
Write-Host ('=' * 68) -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Store manager (ADMIN): meera.joshi@rewamart.in' -ForegroundColor White
Write-Host '  8 customers, 6 categories, 24 products, ~9 orders across every state' -ForegroundColor White
Write-Host ''
Write-Host "  Every seeded account logs in with:  $Password" -ForegroundColor Yellow
Write-Host ''
Write-Host '  Have a look in pgAdmin:' -ForegroundColor White
Write-Host '    SELECT status, COUNT(*), SUM(total_amount) FROM orders GROUP BY status;' -ForegroundColor DarkGray
Write-Host ''
