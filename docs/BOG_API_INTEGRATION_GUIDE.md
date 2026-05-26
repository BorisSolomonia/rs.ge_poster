# BOG API Integration Guide for Camora

This guide explains how to implement BOG Business Online API integration in the Camora project from zero. It is written step by step so a beginner can follow it without guessing.

Do not put real bank credentials in this file, Git, screenshots, or chat. Use placeholders such as `<BOG_CLIENT_ID>`.

## 1. What This Integration Does

The app needs BOG bank transactions so Camora can analyze money coming in and money going out.

In Camora, bank transactions are converted into this internal shape:

```java
BankTransaction(
    date,
    direction,
    amount,
    currency,
    accountNumber,
    counterparty,
    counterpartyInn,
    counterpartyAccount,
    description,
    reference,
    rawPayload
)
```

The BOG flow is:

1. Ask BOG for an OAuth access token.
2. Use that token to call the BOG statement endpoint.
3. Read all pages of the statement.
4. Convert each BOG record into `BankTransaction`.
5. Pass those transactions into `BankAnalysisService`.
6. Frontend calls Camora backend, not BOG directly.

Current Camora files:

```text
backend/src/main/java/ge/camora/erp/module/bankanalysis/BogBusinessOnlineClient.java
backend/src/main/java/ge/camora/erp/module/bankanalysis/BankAnalysisService.java
backend/src/main/java/ge/camora/erp/module/bankanalysis/BankAnalysisController.java
backend/src/main/java/ge/camora/erp/config/CamoraProperties.java
backend/src/main/resources/application.yml
frontend/src/api/bank-analysis.api.ts
frontend/src/pages/BankAnalysisPage.tsx
```

Official BOG docs used:

```text
https://api.businessonline.ge/Help
https://api.businessonline.ge/Help/Api/GET-api-statement-v2-accountNumber-currency-startDate-endDate-includeToday-orderByDate-take
https://api.businessonline.ge/Help/Api/GET-api-statement-v2-accountNumber-currency-id-page-orderByDate
```

## 2. Information You Need From BOG

Ask BOG for Business Online API access.

You need:

```text
Client ID
Client Secret
Account IBAN
Account currency, usually GEL
Token URL
API base URL
```

In Camora these become environment variables:

```env
CAMORA_BOG_API_ENABLED=true
CAMORA_BOG_API_TOKEN_URL=https://account.bog.ge/auth/realms/bog/protocol/openid-connect/token
CAMORA_BOG_API_BASE_URL=https://api.businessonline.ge/api
CAMORA_BOG_API_CLIENT_ID=<BOG_CLIENT_ID>
CAMORA_BOG_API_CLIENT_SECRET=<BOG_CLIENT_SECRET>
CAMORA_BOG_API_ACCOUNT_NUMBER=<BOG_ACCOUNT_IBAN>
CAMORA_BOG_API_CURRENCY=GEL
CAMORA_BOG_API_TAKE=1000
CAMORA_BOG_API_TIMEOUT_SECONDS=120
CAMORA_BOG_API_LARGE_CREDIT_THRESHOLD=1000.00
```

Never commit real values.

## 3. Add Configuration

Backend configuration is read from `application.yml` into `CamoraProperties`.

Add or verify this section exists:

```yaml
camora:
  bog-api:
    enabled: "${CAMORA_BOG_API_ENABLED:false}"
    token-url: "${CAMORA_BOG_API_TOKEN_URL:https://account.bog.ge/auth/realms/bog/protocol/openid-connect/token}"
    base-url: "${CAMORA_BOG_API_BASE_URL:https://api.businessonline.ge/api}"
    client-id: "${CAMORA_BOG_API_CLIENT_ID:}"
    client-secret: "${CAMORA_BOG_API_CLIENT_SECRET:}"
    account-number: "${CAMORA_BOG_API_ACCOUNT_NUMBER:}"
    currency: "${CAMORA_BOG_API_CURRENCY:GEL}"
    take: "${CAMORA_BOG_API_TAKE:1000}"
    timeout-seconds: "${CAMORA_BOG_API_TIMEOUT_SECONDS:120}"
    large-credit-threshold: "${CAMORA_BOG_API_LARGE_CREDIT_THRESHOLD:1000.00}"
```

Add Java properties in `CamoraProperties.BogApi`:

```java
private boolean enabled;
private String tokenUrl;
private String baseUrl;
private String clientId;
private String clientSecret;
private String accountNumber;
private String currency;
private int take;
private int timeoutSeconds;
private BigDecimal largeCreditThreshold;
```

Add getters and setters for every property.

## 4. Create The BOG Client

Create a Spring component:

```java
@Component
public class BogBusinessOnlineClient {
    private final CamoraProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Token token;
}
```

The client needs:

```text
CamoraProperties, so it can read env config.
ObjectMapper, so it can parse JSON.
Token cache, so it does not request a new token for every statement call.
```

## 5. Validate Configuration First

Before calling BOG, check that all required fields exist.

Implement:

```java
private void validateConfig(CamoraProperties.BogApi config) {
    if (!config.isEnabled()) {
        throw new IllegalStateException("BOG API integration is disabled. Set CAMORA_BOG_API_ENABLED=true.");
    }
    require(config.getTokenUrl(), "CAMORA_BOG_API_TOKEN_URL");
    require(config.getBaseUrl(), "CAMORA_BOG_API_BASE_URL");
    require(config.getClientId(), "CAMORA_BOG_API_CLIENT_ID");
    require(config.getClientSecret(), "CAMORA_BOG_API_CLIENT_SECRET");
    require(config.getAccountNumber(), "CAMORA_BOG_API_ACCOUNT_NUMBER");
    require(config.getCurrency(), "CAMORA_BOG_API_CURRENCY");
}
```

This is important because a missing env variable should fail with a clear message, not a confusing BOG error.

## 6. Get An Access Token

BOG uses OAuth client credentials.

Request:

```http
POST <CAMORA_BOG_API_TOKEN_URL>
Authorization: Basic base64(clientId:clientSecret)
Content-Type: application/x-www-form-urlencoded
Accept: application/json

grant_type=client_credentials&client_id=<BOG_CLIENT_ID>&client_secret=<BOG_CLIENT_SECRET>
```

Java steps:

1. Build string `clientId + ":" + clientSecret`.
2. Base64 encode it.
3. Put it in `Authorization: Basic <encoded>`.
4. Send form body.
5. Parse JSON response.
6. Read `access_token`.
7. Read `expires_in`.
8. Save token in memory until almost expired.

Example code shape:

```java
String auth = Base64.getEncoder().encodeToString(
    (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8)
);

String body = form("grant_type", "client_credentials")
    + "&" + form("client_id", config.getClientId())
    + "&" + form("client_secret", config.getClientSecret());
```

Cache rule:

```text
If token expires in more than 30 seconds, reuse it.
If token is missing or nearly expired, fetch a new one.
```

## 7. Call The Statement Endpoint

The first statement call is:

```http
GET <baseUrl>/statement/v2/{accountNumber}/{currency}/{startDate}/{endDate}/{includeToday}/{orderByDate}/{take}
Authorization: Bearer <access_token>
Accept: application/json
```

Example with placeholders:

```text
https://api.businessonline.ge/api/statement/v2/<IBAN>/GEL/2026-02-01/2026-02-28/false/true/1000
```

Parameter meanings:

```text
accountNumber  BOG account IBAN
currency       GEL, USD, EUR, etc.
startDate      dateFrom from UI
endDate        dateTo from UI
includeToday   true if dateTo is today or future; otherwise false
orderByDate    true in Camora
take           max records for first page, usually 1000
```

In Java build URL like this:

```java
private String statementUrl(CamoraProperties.BogApi config, LocalDate dateFrom, LocalDate dateTo, boolean includeToday) {
    return trimSlash(config.getBaseUrl())
        + "/statement/v2/"
        + encode(config.getAccountNumber()) + "/"
        + encode(config.getCurrency()) + "/"
        + dateFrom + "/"
        + dateTo + "/"
        + includeToday + "/true/"
        + Math.max(1, config.getTake());
}
```

## 8. Parse First Page

BOG response contains:

```json
{
  "Id": 1234567890,
  "Count": 1000,
  "TotalCount": 3033,
  "Records": []
}
```

Meaning:

```text
Id          statement ID; use it to request later pages
Count       number of records returned in this response
TotalCount  total records available
Records     transactions
```

If `TotalCount` is bigger than `Count`, fetch more pages.

## 9. Fetch More Pages

Page URL:

```http
GET <baseUrl>/statement/v2/{accountNumber}/{currency}/{id}/{page}/{orderByDate}
Authorization: Bearer <access_token>
Accept: application/json
```

Example:

```text
https://api.businessonline.ge/api/statement/v2/<IBAN>/GEL/<STATEMENT_ID>/2/true
```

Important:

```text
First response is page 1.
Next page number is 2.
Continue until all pages are read.
```

Java logic:

```java
int count = firstPage.path("Count").asInt(transactions.size());
int totalCount = firstPage.path("TotalCount").asInt(count);
long statementId = firstPage.path("Id").asLong(0);

if (statementId > 0 && count > 0 && totalCount > count) {
    int totalPages = (int) Math.ceil((double) totalCount / count);
    for (int page = 2; page <= totalPages; page++) {
        JsonNode pageNode = getJson(statementPageUrl(config, statementId, page), accessToken, config);
        transactions.addAll(parseRecords(pageNode, config));
    }
}
```

## 10. Convert BOG Records To Camora Transactions

BOG record can have debit amount, credit amount, or both.

Camora rule:

```text
EntryAmountCredit > 0 means CREDIT.
EntryAmountDebit > 0 means DEBIT.
```

For credit:

```text
amount = EntryAmountCredit
direction = CREDIT
counterparty = SenderDetails.Name, fallback DocumentPayerName
counterpartyInn = SenderDetails.Inn, fallback DocumentPayerInn
counterpartyAccount = SenderDetails.AccountNumber
```

For debit:

```text
amount = EntryAmountDebit
direction = DEBIT
counterparty = BeneficiaryDetails.Name, fallback DocumentPayerName
counterpartyInn = BeneficiaryDetails.Inn, fallback DocumentPayerInn
counterpartyAccount = BeneficiaryDetails.AccountNumber
```

Date:

```text
Use EntryDate first.
Fallback to DocumentValueDate.
Fallback to AuthDate.
Keep only yyyy-MM-dd part.
```

Description:

```text
EntryComment
DocumentNomination
DocumentInformation
DocComment
```

Reference:

```text
EntryDocumentNumber
DocumentKey
EntryId
```

Currency:

```text
DocumentSourceCurrency
DocumentDestinationCurrency
CAMORA_BOG_API_CURRENCY
```

Keep raw JSON in `rawPayload`:

```java
record.toString()
```

This helps debugging without losing the original bank payload.

## 11. Connect Client To Bank Analysis

`BankAnalysisService.analyzeBog(dateFrom, dateTo)` should:

1. Validate `dateTo >= dateFrom`.
2. Call `bogBusinessOnlineClient.getStatement(dateFrom, dateTo)`.
3. Map each `BankTransaction` to categories using saved bank mappings.
4. Calculate totals.
5. Return `BankAnalysisOverviewDto`.

Provider name should be:

```text
BOG
```

Account number should come from:

```java
properties.getBogApi().getAccountNumber()
```

Currency should come from:

```java
properties.getBogApi().getCurrency()
```

## 12. Add Backend Endpoint

Controller endpoint:

```java
@GetMapping("/bog")
public ResponseEntity<ApiResponse<BankAnalysisOverviewDto>> bogOverview(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
) {
    return ResponseEntity.ok(ApiResponse.ok(bankAnalysisService.analyzeBog(dateFrom, dateTo)));
}
```

The frontend calls:

```http
GET /api/v1/bank-analysis/bog?dateFrom=2026-02-01&dateTo=2026-02-28
```

## 13. Add Frontend API Call

In `frontend/src/api/bank-analysis.api.ts`:

```ts
export async function getBogBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/bog`, {
    params: { dateFrom, dateTo },
  })
  return res.data.data
}
```

In the page, provider `BOG` should call `getBogBankAnalysis`.

## 14. Run Locally

Start backend:

```powershell
$env:CAMORA_BOG_API_ENABLED="true"
$env:CAMORA_BOG_API_CLIENT_ID="<BOG_CLIENT_ID>"
$env:CAMORA_BOG_API_CLIENT_SECRET="<BOG_CLIENT_SECRET>"
$env:CAMORA_BOG_API_ACCOUNT_NUMBER="<BOG_ACCOUNT_IBAN>"
$env:CAMORA_BOG_API_CURRENCY="GEL"
$env:SERVER_PORT="8082"
mvn -f backend\pom.xml spring-boot:run
```

Start frontend in another terminal:

```powershell
npm run dev
```

Open:

```text
http://localhost:5173/bank-analysis
```

Select:

```text
Provider: BOG
Date From: 2026-02-01
Date To: 2026-02-28
```

Click run.

## 15. Test Backend Directly

Use `curl.exe`, not PowerShell `curl` alias:

```powershell
curl.exe -i "http://localhost:8082/api/v1/bank-analysis/bog?dateFrom=2026-02-01&dateTo=2026-02-28"
```

Success should return:

```json
{
  "success": true,
  "data": {
    "provider": "BOG",
    "totalCredits": 0,
    "totalDebits": 0,
    "transactions": []
  },
  "error": null
}
```

Amounts and transactions will depend on the selected period.

## 16. Common Errors

### `BOG API integration is disabled`

Cause:

```text
CAMORA_BOG_API_ENABLED is missing or false.
```

Fix:

```powershell
$env:CAMORA_BOG_API_ENABLED="true"
```

Restart backend.

### `CAMORA_BOG_API_CLIENT_ID is required`

Cause:

```text
Client ID env var is missing.
```

Fix:

```powershell
$env:CAMORA_BOG_API_CLIENT_ID="<BOG_CLIENT_ID>"
```

Restart backend.

### `BOG token HTTP 401`

Likely causes:

```text
Wrong client ID.
Wrong client secret.
Credentials not activated by BOG.
Using wrong token URL.
```

Fix:

1. Recheck values in BOG portal or with BOG support.
2. Make sure there are no spaces around env values.
3. Restart backend after changing env.

### `BOG API HTTP 403`

Likely causes:

```text
Token works, but account access is not allowed.
Account IBAN is not activated for this client.
```

Fix:

```text
Ask BOG to confirm that the client has permission for this account and currency.
```

### Empty transactions

Possible causes:

```text
No bank activity in selected period.
Wrong account IBAN.
Wrong currency.
dateFrom/dateTo period is wrong.
```

Test with a known active day.

## 17. Security Rules

Do not:

```text
Commit client secret.
Print token in logs.
Send screenshots with credentials.
Store secrets in frontend .env.
Expose BOG token to browser.
```

Do:

```text
Store secrets in backend env or GCP Secret Manager.
Use backend endpoint from frontend.
Mask errors if they contain sensitive payloads.
Keep raw bank JSON only inside backend objects.
```

## 18. Production Deployment Checklist

Before deploy:

```text
CAMORA_BOG_API_ENABLED=true
CAMORA_BOG_API_TOKEN_URL is correct
CAMORA_BOG_API_BASE_URL is correct
CAMORA_BOG_API_CLIENT_ID is set in Secret Manager
CAMORA_BOG_API_CLIENT_SECRET is set in Secret Manager
CAMORA_BOG_API_ACCOUNT_NUMBER is correct
CAMORA_BOG_API_CURRENCY is correct
Backend can reach api.businessonline.ge
Frontend calls /camora/api/v1/bank-analysis/bog in production path
```

Validate production compose:

```powershell
docker compose -f docker/compose.production.yml config
```

## 19. Minimal Implementation Order

If starting from zero, implement in this order:

1. Add env properties.
2. Add `BogApi` config class.
3. Add `BankTransaction` model if missing.
4. Add token request.
5. Add token cache.
6. Add first statement request.
7. Add pagination.
8. Add JSON parser.
9. Add `BankAnalysisService.analyzeBog`.
10. Add controller endpoint.
11. Add frontend API function.
12. Add provider option in UI.
13. Test direct backend endpoint.
14. Test frontend page.
15. Deploy secrets.
