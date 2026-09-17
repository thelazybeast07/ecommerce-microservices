<#
.SYNOPSIS
    Runs an end-to-end check of all three Phase 1 services and prints what happened.

.DESCRIPTION
    Creates its OWN test data (uniquely named customer, category, product) so nothing you
    already created is modified or deleted. It never deletes anything.

    Requires all three services to be running:
      user-service    http://localhost:8081
      product-service http://localhost:8082
      order-service   http://localhost:8083

.EXAMPLE
    .\scripts\verify-phase1.ps1
#>

$ErrorActionPreference = 'Stop'

$UserSvc    = 'http://localhost:8081'
$ProductSvc = 'http://localhost:8082'
$OrderSvc   = 'http://localhost:8083'

$script:Passed = 0
$script:Failed = 0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Write-Header($text) {
    Write-Host ''
    Write-Host ('=' * 70) -ForegroundColor DarkGray
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ('=' * 70) -ForegroundColor DarkGray
}

function Write-Step($text) {
    Write-Host ''
    Write-Host "-> $text" -ForegroundColor White
}

function Write-Pass($text) {
    $script:Passed++
    Write-Host "   [PASS] $text" -ForegroundColor Green
}

function Write-Fail($text) {
    $script:Failed++
    Write-Host "   [FAIL] $text" -ForegroundColor Red
}

function Write-Info($text) {
    Write-Host "          $text" -ForegroundColor DarkGray
}

function Write-Lesson($text) {
    Write-Host "   WHY:  $text" -ForegroundColor Yellow
}

<#
Calls the API and ALWAYS returns @{ Status = <int>; Body = <object or $null> },
including for 4xx/5xx. Works on both Windows PowerShell 5.1 and PowerShell 7.
#>
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Uri,
        $Body = $null
    )

    $params = @{
        Method      = $Method
        Uri         = $Uri
        ContentType = 'application/json'
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 10)
    }

    try {
        $response = Invoke-WebRequest @params -UseBasicParsing
        $parsed = $null
        $raw = $response.Content
        # PowerShell returns a byte[] instead of a string when the content type is one it
        # does not recognise as text - Actuator's application/vnd.spring-boot.actuator.v3+json
        # is exactly such a type. Decode it before parsing.
        if ($raw -is [byte[]]) { $raw = [System.Text.Encoding]::UTF8.GetString($raw) }
        if ($raw) { try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null } }
        return @{ Status = [int]$response.StatusCode; Body = $parsed }
    }
    # Untyped catch on purpose. HttpResponseException exists only in PowerShell 7;
    # naming it here makes this clause fail to resolve on Windows PowerShell 5.1 the
    # first time any request returns a non-2xx status.
    catch {
        $webResponse = $_.Exception.Response
        if ($null -eq $webResponse) {
            # No HTTP response at all (connection refused, DNS failure, timeout)
            return @{ Status = 0; Body = $null; Error = $_.Exception.Message }
        }

        # StatusCode is an enum in 5.1 and an int-like in 7; this works on both.
        $status = [int]$webResponse.StatusCode
        $content = $null
        try {
            # PowerShell 7 exposes the body on the error record
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $content = $_.ErrorDetails.Message
            }
            else {
                $stream = $webResponse.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $content = $reader.ReadToEnd()
                $reader.Close()
            }
        } catch { }

        $parsed = $null
        if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
        if ($content) { try { $parsed = $content | ConvertFrom-Json } catch { } }
        return @{ Status = $status; Body = $parsed }
    }
}

function Assert-Status {
    param(
        [hashtable]$Result,
        [int]$Expected,
        [string]$What
    )
    if ($Result.Status -eq $Expected) {
        Write-Pass "$What -> HTTP $($Result.Status)"
        return $true
    }
    Write-Fail "$What -> expected HTTP $Expected but got $($Result.Status)"
    if ($Result.Body) {
        Write-Info ("response: " + ($Result.Body | ConvertTo-Json -Compress -Depth 5))
    }
    return $false
}

# ---------------------------------------------------------------------------
# 0. Are all three services up?
# ---------------------------------------------------------------------------

Write-Header 'CHECK 0 - Are all three services running?'

$services = @(
    @{ Name = 'user-service';    Url = $UserSvc },
    @{ Name = 'product-service'; Url = $ProductSvc },
    @{ Name = 'order-service';   Url = $OrderSvc }
)

$allUp = $true
foreach ($svc in $services) {
    $health = Invoke-Api -Method GET -Uri "$($svc.Url)/actuator/health"
    # Trust the status code. Some PowerShell versions cannot parse Actuator's content type,
    # so requiring a parsed body here would report a healthy service as down.
    if ($health.Status -eq 200) {
        Write-Pass "$($svc.Name) is UP at $($svc.Url)"
    }
    else {
        Write-Fail "$($svc.Name) is NOT reachable at $($svc.Url)"
        $allUp = $false
    }
}

if (-not $allUp) {
    Write-Host ''
    Write-Host 'Start the missing service(s) first, then re-run this script.' -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 1. Set up our own test data
# ---------------------------------------------------------------------------

Write-Header 'SETUP - Creating test data (nothing existing is touched)'

$stamp  = (Get-Date -Format 'yyyyMMddHHmmss')
$suffix = -join ((65..90) | Get-Random -Count 6 | ForEach-Object { [char]$_ })

Write-Step "Registering a customer in user-service"
$customerResult = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/customers" -Body @{
    firstName = 'Test'
    lastName  = 'Buyer'
    email     = "verify-$stamp@example.com"
    phone     = '+14165550123'
    password  = 'correct-horse-battery'
}
if (-not (Assert-Status $customerResult 201 'Create customer')) { exit 1 }
$customerId = $customerResult.Body.id
Write-Info "customerId = $customerId"

Write-Step "Adding a shipping address for that customer"
$addressResult = Invoke-Api -Method POST -Uri "$UserSvc/api/v1/customers/$customerId/addresses" -Body @{
    addressLine1 = '100 King St W'
    city         = 'Toronto'
    state        = 'ON'
    postalCode   = 'M5X 1A9'
    country      = 'CA'
    addressType  = 'SHIPPING'
}
if (-not (Assert-Status $addressResult 201 'Create address')) { exit 1 }
$addressId = $addressResult.Body.id
Write-Info "addressId = $addressId"

Write-Step "Creating a category in product-service"
$categoryResult = Invoke-Api -Method POST -Uri "$ProductSvc/api/v1/categories" -Body @{
    name        = "Verify-$stamp"
    description = 'Temporary category created by verify-phase1.ps1'
}
if (-not (Assert-Status $categoryResult 201 'Create category')) { exit 1 }
$categoryId = $categoryResult.Body.id
Write-Info "categoryId = $categoryId"

Write-Step "Creating a product priced at 24.99 CAD"
$productResult = Invoke-Api -Method POST -Uri "$ProductSvc/api/v1/products" -Body @{
    sku         = "VERIFY-$suffix"
    name        = 'Black cotton t-shirt'
    description = 'Original description'
    price       = 24.99
    currency    = 'CAD'
    categoryId  = $categoryId
}
if (-not (Assert-Status $productResult 201 'Create product')) { exit 1 }
$productId = $productResult.Body.id
Write-Info "productId = $productId  (SKU VERIFY-$suffix, 24.99 CAD)"

# ---------------------------------------------------------------------------
# 2. Place an order - all three services in one call
# ---------------------------------------------------------------------------

Write-Header 'CHECK 1 - Placing an order uses all three services'

Write-Step "POST /api/v1/orders with only IDs and quantity (no price)"
$orderResult = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @(@{ productId = $productId; quantity = 2 })
}
if (-not (Assert-Status $orderResult 201 'Place order')) { exit 1 }

$orderId = $orderResult.Body.id
$total   = $orderResult.Body.totalAmount
Write-Info "orderId = $orderId"
Write-Info "status  = $($orderResult.Body.status)"
Write-Info "total   = $total $($orderResult.Body.currency)   (2 x 24.99, computed by order-service)"

if ([decimal]$total -eq 49.98) {
    Write-Pass 'Total was computed from the catalogue price (2 x 24.99 = 49.98)'
} else {
    Write-Fail "Expected total 49.98 but got $total"
}
Write-Lesson 'order-service called user-service (customer + address) and product-service (price).'
Write-Lesson 'Check those two terminal windows - you will see log lines from this one request.'

# ---------------------------------------------------------------------------
# 3. Price forgery is structurally impossible
# ---------------------------------------------------------------------------

Write-Header 'CHECK 2 - A client cannot dictate the price'

Write-Step "Placing an order that tries to smuggle in unitPrice = 0.01"
$forgeryResult = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @(@{ productId = $productId; quantity = 1; unitPrice = 0.01 })
}
if (Assert-Status $forgeryResult 201 'Place order with a forged price') {
    $forgedTotal = $forgeryResult.Body.totalAmount
    Write-Info "total charged = $forgedTotal (the injected 0.01 was ignored)"
    if ([decimal]$forgedTotal -eq 24.99) {
        Write-Pass 'The forged price was discarded; the real catalogue price was used'
    } else {
        Write-Fail "Expected 24.99 but got $forgedTotal"
    }
}
Write-Lesson 'OrderItemRequest has no price field, so Jackson silently drops it.'
Write-Lesson 'The protection is structural, not a validation rule someone could forget.'

# ---------------------------------------------------------------------------
# 4. Snapshot: the order does not change when the catalogue does
# ---------------------------------------------------------------------------

Write-Header 'CHECK 3 - An order is a snapshot, not a live view'

Write-Step "Raising the product price to 99.99 and renaming it in product-service"
$updateResult = Invoke-Api -Method PUT -Uri "$ProductSvc/api/v1/products/$productId" -Body @{
    name        = 'RENAMED - Premium t-shirt'
    description = 'Price was raised after the order was placed'
    price       = 99.99
    currency    = 'CAD'
    categoryId  = $categoryId
}
Assert-Status $updateResult 200 'Update product price and name' | Out-Null

Write-Step "Re-reading the ORIGINAL order from order-service"
$reread = Invoke-Api -Method GET -Uri "$OrderSvc/api/v1/orders/$orderId"
if (Assert-Status $reread 200 'Read original order') {
    $item = $reread.Body.items[0]
    Write-Info "product name on the order : $($item.productName)"
    Write-Info "unit price on the order   : $($item.unitPrice)"
    Write-Info "order total               : $($reread.Body.totalAmount)"

    if ([decimal]$item.unitPrice -eq 24.99 -and $item.productName -notlike 'RENAMED*') {
        Write-Pass 'The order still shows the ORIGINAL price and name - unchanged'
    } else {
        Write-Fail 'The order changed when the catalogue changed - snapshot is not working'
    }
}
Write-Lesson 'order_items stores product_name, product_sku and unit_price as copied values.'
Write-Lesson 'A receipt that rewrites itself when prices change is not a receipt.'

# ---------------------------------------------------------------------------
# 5. Discontinued product -> 422, but old orders still readable
# ---------------------------------------------------------------------------

Write-Header 'CHECK 4 - Discontinuing a product (422 Unprocessable)'

Write-Step "Deactivating the product in product-service"
$deactivate = Invoke-Api -Method DELETE -Uri "$ProductSvc/api/v1/products/$productId"
Assert-Status $deactivate 204 'Deactivate product' | Out-Null

Write-Step "Trying to place a NEW order for the discontinued product"
$blocked = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @(@{ productId = $productId; quantity = 1 })
}
if (Assert-Status $blocked 422 'New order for a discontinued product is rejected') {
    Write-Info "detail: $($blocked.Body.detail)"
}
Write-Lesson '422 = the request was well-formed, but what it REFERENCES is unusable.'
Write-Lesson 'Not 400 (nothing malformed) and not 404 (the order is being created, not missing).'

Write-Step "Confirming the EXISTING order is still readable"
$stillReadable = Invoke-Api -Method GET -Uri "$OrderSvc/api/v1/orders/$orderId"
if (Assert-Status $stillReadable 200 'Existing order still readable after the product was retired') {
    Write-Info "still shows: $($stillReadable.Body.items[0].productName) at $($stillReadable.Body.items[0].unitPrice)"
}

# ---------------------------------------------------------------------------
# 6. The order state machine
# ---------------------------------------------------------------------------

Write-Header 'CHECK 5 - The order state machine'

$transitions = @('CONFIRMED', 'PAYMENT_PENDING', 'PAID')
foreach ($target in $transitions) {
    Write-Step "Moving the order to $target"
    $t = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$orderId/status" -Body @{ status = $target }
    if (Assert-Status $t 200 "Transition to $target") {
        Write-Info "status is now $($t.Body.status)"
    }
}

Write-Step "Trying to CANCEL an order that has already been PAID"
$lateCancel = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$orderId/cancel"
if (Assert-Status $lateCancel 409 'Cancelling a PAID order is rejected') {
    Write-Info "detail: $($lateCancel.Body.detail)"
}
Write-Lesson 'Money has changed hands, so this needs a refund flow, not a status flip.'

Write-Step "Trying to jump straight from PAID to DELIVERED"
$skip = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$orderId/status" -Body @{ status = 'DELIVERED' }
if (Assert-Status $skip 409 'Skipping ahead in the lifecycle is rejected') {
    Write-Info "detail: $($skip.Body.detail)"
}
Write-Lesson 'Legal transitions live in the OrderStatus enum - one place, fully tested.'

# ---------------------------------------------------------------------------
# 7. Cancel is idempotent
# ---------------------------------------------------------------------------

Write-Header 'CHECK 6 - Cancelling is idempotent'

Write-Step "Placing a fresh order to cancel (using a second, still-active product)"
$product2 = Invoke-Api -Method POST -Uri "$ProductSvc/api/v1/products" -Body @{
    sku        = "VERIFY2-$suffix"
    name       = 'Cotton cap'
    price      = 10.00
    currency   = 'CAD'
    categoryId = $categoryId
}
Assert-Status $product2 201 'Create a second product' | Out-Null
$product2Id = $product2.Body.id

$cancelMe = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @(@{ productId = $product2Id; quantity = 1 })
}
Assert-Status $cancelMe 201 'Place order to be cancelled' | Out-Null
$cancelId = $cancelMe.Body.id

Write-Step "Cancelling it while still PENDING"
$c1 = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$cancelId/cancel"
if (Assert-Status $c1 200 'First cancel') { Write-Info "status = $($c1.Body.status)" }

Write-Step "Cancelling the SAME order a second time"
$c2 = Invoke-Api -Method PATCH -Uri "$OrderSvc/api/v1/orders/$cancelId/cancel"
if (Assert-Status $c2 200 'Second cancel returns 200, not an error') {
    Write-Info "status = $($c2.Body.status)"
}
Write-Lesson 'Idempotent: repeating the operation gives the same result instead of failing.'
Write-Lesson 'This matters because networks retry requests.'

# ---------------------------------------------------------------------------
# 8. Bad references -> 422
# ---------------------------------------------------------------------------

Write-Header 'CHECK 7 - Bad references are rejected with 422'

Write-Step "Ordering with a customer id that does not exist"
$noCustomer = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = [guid]::NewGuid().ToString()
    shippingAddressId = $addressId
    items             = @(@{ productId = $product2Id; quantity = 1 })
}
if (Assert-Status $noCustomer 422 'Unknown customer is rejected') {
    Write-Info "detail: $($noCustomer.Body.detail)"
}
Write-Lesson 'user-service returned 404; order-service translated it into OUR 422.'
Write-Lesson 'Without that translation the caller would see a misleading 500.'

Write-Step "Ordering with a product id that does not exist"
$noProduct = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @(@{ productId = [guid]::NewGuid().ToString(); quantity = 1 })
}
if (Assert-Status $noProduct 422 'Unknown product is rejected') {
    Write-Info "detail: $($noProduct.Body.detail)"
}

Write-Step "Ordering with an empty cart (caught by validation, so 400 not 422)"
$emptyCart = Invoke-Api -Method POST -Uri "$OrderSvc/api/v1/orders" -Body @{
    customerId        = $customerId
    shippingAddressId = $addressId
    items             = @()
}
if (Assert-Status $emptyCart 400 'Empty cart is rejected before any remote call') {
    Write-Info "detail: $($emptyCart.Body.detail)"
}
Write-Lesson '400 = the request itself is malformed. It never reached the other services.'

# ---------------------------------------------------------------------------
# 9. Customer order history
# ---------------------------------------------------------------------------

Write-Header 'CHECK 8 - Customer order history'

Write-Step "GET /api/v1/customers/{id}/orders (served by order-service, not user-service)"
$history = Invoke-Api -Method GET -Uri "$OrderSvc/api/v1/customers/$customerId/orders"
if (Assert-Status $history 200 'Fetch order history') {
    Write-Info "orders found: $($history.Body.totalElements)"
    foreach ($o in $history.Body.content) {
        Write-Info ("  {0}  {1,-16} {2} {3}" -f $o.id.Substring(0,8), $o.status, $o.totalAmount, $o.currency)
    }
    if ($null -eq $history.Body.content[0].items) {
        Write-Pass 'The list view omits line items (summary projection)'
    }
}
Write-Lesson 'This URL starts with /customers but lives in order-service: ownership follows the data.'

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

Write-Header 'SUMMARY'

Write-Host ''
Write-Host "  Passed: $script:Passed" -ForegroundColor Green
Write-Host "  Failed: $script:Failed" -ForegroundColor $(if ($script:Failed -gt 0) { 'Red' } else { 'DarkGray' })
Write-Host ''
Write-Host '  Test data created by this run (left in place, nothing deleted):' -ForegroundColor DarkGray
Write-Host "    customer  $customerId" -ForegroundColor DarkGray
Write-Host "    order     $orderId" -ForegroundColor DarkGray
Write-Host ''

if ($script:Failed -eq 0) {
    Write-Host '  All checks passed. Phase 1 works end to end.' -ForegroundColor Green
} else {
    Write-Host '  Some checks failed - scroll up for the [FAIL] lines.' -ForegroundColor Red
}
Write-Host ''
