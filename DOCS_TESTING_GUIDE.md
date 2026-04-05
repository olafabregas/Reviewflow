# DocsController Testing Guide

**Purpose:** Execute and validate all documentation endpoints after backend startup

---

## Quick Start Testing

### Step 1: Start the Backend

```powershell
cd c:\Desktop\Reviewflow\Backend
.\mvnw.cmd spring-boot:run
```

Wait for:

```
Started ReviewFlowApplication in X.XXX seconds
```

### Step 2: Test Each Endpoint in Browser or with curl

#### Test 1: Public API Documentation

```bash
# Browser
http://localhost:8081/docs

# curl
curl -s http://localhost:8081/docs | head -50
```

**Expected Response:**

- HTTP 200 OK
- Content-Type: text/html
- HTML body with `<redoc spec-url='/api/v1/api-docs/public'></redoc>`

---

#### Test 2: Admin API Documentation

```bash
# Browser
http://localhost:8081/docs/admin

# curl
curl -s http://localhost:8081/docs/admin | head -50
```

**Expected Response:**

- HTTP 200 OK
- Content-Type: text/html
- HTML body with `<redoc spec-url='/api/v1/api-docs/admin'></redoc>`

---

#### Test 3: System Admin Documentation

```bash
# Browser
http://localhost:8081/docs/system

# curl
curl -s http://localhost:8081/docs/system | head -50
```

**Expected Response:**

- HTTP 200 OK
- Content-Type: text/html
- HTML body with `<redoc spec-url='/api/v1/api-docs/system'></redoc>`

---

#### Test 4: OpenAPI Spec - Public

```bash
curl -s http://localhost:8081/api/v1/api-docs/public | jq . | head -50
```

**Expected Response:**

- HTTP 200 OK
- Content-Type: application/json
- JSON with `openapi: "3.0.1"`, `info.title: "ReviewFlow API"`, `paths` with public endpoints

---

#### Test 5: OpenAPI Spec - Admin

```bash
curl -s http://localhost:8081/api/v1/api-docs/admin | jq . | head -50
```

**Expected Response:**

- HTTP 200 OK
- JSON with `/admin/**` endpoints only

---

#### Test 6: OpenAPI Spec - System

```bash
curl -s http://localhost:8081/api/v1/api-docs/system | jq . | head -50
```

**Expected Response:**

- HTTP 200 OK
- JSON with `/system/**` endpoints only

---

#### Test 7: Swagger UI

```bash
# Browser
http://localhost:8081/swagger-ui.html
```

**Expected Response:**

- HTTP 200 OK
- Interactive Swagger interface loads
- Can expand endpoints and see documentation

---

## PowerShell Testing Script

```powershell
$baseUrl = "http://localhost:8081"

# Test 1: Public Docs
Write-Host "Testing /docs endpoint..." -ForegroundColor Cyan
$response1 = Invoke-WebRequest -Uri "$baseUrl/docs" -ErrorAction SilentlyContinue
Write-Host "Status: $($response1.StatusCode)" -ForegroundColor Green
Write-Host "Content-Type: $($response1.Headers['Content-Type'])" -ForegroundColor Green

# Test 2: Admin Docs
Write-Host "`nTesting /docs/admin endpoint..." -ForegroundColor Cyan
$response2 = Invoke-WebRequest -Uri "$baseUrl/docs/admin" -ErrorAction SilentlyContinue
Write-Host "Status: $($response2.StatusCode)" -ForegroundColor Green
Write-Host "Content-Type: $($response2.Headers['Content-Type'])" -ForegroundColor Green

# Test 3: System Docs
Write-Host "`nTesting /docs/system endpoint..." -ForegroundColor Cyan
$response3 = Invoke-WebRequest -Uri "$baseUrl/docs/system" -ErrorAction SilentlyContinue
Write-Host "Status: $($response3.StatusCode)" -ForegroundColor Green
Write-Host "Content-Type: $($response3.Headers['Content-Type'])" -ForegroundColor Green

# Test 4: OpenAPI Spec - Public
Write-Host "`nTesting /api/v1/api-docs/public endpoint..." -ForegroundColor Cyan
$response4 = Invoke-WebRequest -Uri "$baseUrl/api/v1/api-docs/public" -ErrorAction SilentlyContinue
Write-Host "Status: $($response4.StatusCode)" -ForegroundColor Green
$json4 = $response4.Content | ConvertFrom-Json
Write-Host "OpenAPI Version: $($json4.openapi)" -ForegroundColor Green
Write-Host "Title: $($json4.info.title)" -ForegroundColor Green
Write-Host "Paths Found: $($json4.paths.PSObject.Properties.Count)" -ForegroundColor Green

# Test 5: OpenAPI Spec - Admin
Write-Host "`nTesting /api/v1/api-docs/admin endpoint..." -ForegroundColor Cyan
$response5 = Invoke-WebRequest -Uri "$baseUrl/api/v1/api-docs/admin" -ErrorAction SilentlyContinue
Write-Host "Status: $($response5.StatusCode)" -ForegroundColor Green
$json5 = $response5.Content | ConvertFrom-Json
Write-Host "Paths Found: $($json5.paths.PSObject.Properties.Count)" -ForegroundColor Green

# Test 6: OpenAPI Spec - System
Write-Host "`nTesting /api/v1/api-docs/system endpoint..." -ForegroundColor Cyan
$response6 = Invoke-WebRequest -Uri "$baseUrl/api/v1/api-docs/system" -ErrorAction SilentlyContinue
Write-Host "Status: $($response6.StatusCode)" -ForegroundColor Green
$json6 = $response6.Content | ConvertFrom-Json
Write-Host "Paths Found: $($json6.paths.PSObject.Properties.Count)" -ForegroundColor Green

Write-Host "`n✅ All tests completed!" -ForegroundColor Green
```

---

## Postman Collection Testing

1. Open Postman
2. Import: `Backend/postman/ReviewFlow.postman_collection.json`
3. Select environment: `Backend/postman/ReviewFlow_Local.postman_environment.json`
4. Create new requests:

**Request 1: Public Docs HTML**

```
GET {{base_url}}/docs
- No auth needed
- Expected: 200 OK, text/html
```

**Request 2: Public API Spec**

```
GET {{base_url}}/api/v1/api-docs/public
- No auth needed
- Expected: 200 OK, application/json
```

**Request 3: Admin API Spec**

```
GET {{base_url}}/api/v1/api-docs/admin
- No auth needed
- Expected: 200 OK, application/json
```

**Request 4: System API Spec**

```
GET {{base_url}}/api/v1/api-docs/system
- No auth needed
- Expected: 200 OK, application/json
```

---

## Verification Checklist

After running tests, verify:

- [ ] All HTTP responses are 200 OK
- [ ] HTML endpoints return `Content-Type: text/html`
- [ ] JSON endpoints return `Content-Type: application/json`
- [ ] Redoc loads without console errors (check browser Dev Tools)
- [ ] Swagger UI interface is interactive
- [ ] Public spec contains only non-admin, non-system endpoints
- [ ] Admin spec contains only `/admin/**` endpoints
- [ ] System spec contains only `/system/**` endpoints

---

## Common Issues & Fixes

### Issue: 404 Not Found on /docs

**Cause:** DocsController not mapped  
**Fix:** Verify DocsController.java is in src/main/java/com/reviewflow/controller/  
**Verify:** `grep -r "@RequestMapping(\"/docs\")" src/`

### Issue: Redoc not loading in browser

**Cause:** CDN blocked or no internet  
**Fix:** Browser console shows error → Check network tab  
**Alternative:** Use Swagger UI at /swagger-ui.html instead

### Issue: OpenAPI specs empty (no paths)

**Cause:** No @Operation annotations on endpoints  
**Fix:** Ensure all endpoints have @Operation, @ApiResponses, etc.  
**Verify:** See OPENAPI_ANNOTATION_GUIDE.md

### Issue: Wrong endpoints in admin/system specs

**Cause:** GroupedOpenApi path filters misconfigured  
**Fix:** Check OpenApiConfig.java GroupedOpenApi definitions  
**Expected:**

- public: all paths except /admin/**, /system/**
- admin: only /admin/\*\*
- system: only /system/\*\*

---

## Performance Notes

- **Redoc loading:** First load may take 2-3 seconds (CDN fetch)
- **Swagger UI:** Typically loads in <1 second
- **Spec generation:** Cached by SpringDoc, ~100ms per request
- **No authentication required** for documentation endpoints (design choice: docs are public)

---

## Production Considerations

1. **Configure `swagger.prod-url`** before deploying to production
2. **Use HTTPS** in production (Redoc script tag will upgrade to HTTPS automatically)
3. **Rate-limit** documentation endpoints if needed (optional)
4. **Consider WAF rules** to prevent abuse of spec endpoints
5. **Monitor** CDN availability (Redoc CDN)

---
