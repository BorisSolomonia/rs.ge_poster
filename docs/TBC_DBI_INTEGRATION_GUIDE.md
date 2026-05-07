# TBC DBI API Integration Guide for Camora

This guide explains how to implement TBC DBI API integration in the Camora project from zero. It is written step by step so a beginner can follow it without guessing.

Do not put real bank credentials, certificate passwords, PFX files, OTP codes, or account numbers in this file, Git, screenshots, or chat. Use placeholders such as `<TBC_DBI_USERNAME>`.

## 1. What This Integration Does

The app needs TBC account movements so Camora can analyze money coming in and money going out.

In Camora, TBC SOAP movements are converted into this internal shape:

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

The TBC flow is:

1. Load client PFX certificate.
2. Build HTTPS client with mTLS.
3. Build SOAP request with WS-Security username/password/Nonce.
4. If TBC says password must be changed, call `ChangePassword`.
5. Call `GetAccountMovements`.
6. Page through all results.
7. Convert SOAP movement records into `BankTransaction`.
8. Pass those transactions into `BankAnalysisService`.
9. Frontend calls Camora backend, not TBC directly.

Current Camora files:

```text
backend/src/main/java/ge/camora/erp/module/bankanalysis/TbcDbiClient.java
backend/src/main/java/ge/camora/erp/module/bankanalysis/TbcDbiException.java
backend/src/main/java/ge/camora/erp/module/bankanalysis/BankAnalysisService.java
backend/src/main/java/ge/camora/erp/module/bankanalysis/BankAnalysisController.java
backend/src/main/java/ge/camora/erp/config/CamoraProperties.java
backend/src/main/resources/application.yml
frontend/src/api/bank-analysis.api.ts
frontend/src/pages/BankAnalysisPage.tsx
postman/TBC_DBI_POSTMAN_INSTRUCTIONS.md
```

Official TBC docs used:

```text
https://developers.tbcbank.ge/docs/password-change
https://developers.tbcbank.ge/docs/account-movement
https://developers.tbcbank.ge/docs/attach-digital-certificate
https://developers.tbcbank.ge/docs/how-to-use-digital-certificate
https://developers.tbcbank.ge/docs/error-code-description
```

## 2. Information You Need From TBC

You need TBC DBI integration credentials. These are not the same as developer portal app key/secret.

Ask TBC for:

```text
DBI username
Temporary DBI password
Digipass/token device or OTP method for wsse:Nonce
Client certificate file, usually .pfx or .p12
Certificate password
Account IBAN activated for DBI
Currency, usually GEL
Production or test endpoint
```

Important:

```text
TBC says password change is mandatory before first use.
TBC says Nonce value in ChangePassword is mandatory.
TBC says Digipass code is required during password change in any case.
```

This means password change normally needs:

```text
certificate + username + current temporary password + Digipass OTP/Nonce + new password
```

## 3. Environment Variables

Use these variables:

```env
CAMORA_TBC_DBI_ENABLED=true
CAMORA_TBC_DBI_ENDPOINT=https://secdbi.tbconline.ge/dbi/dbiService
CAMORA_TBC_DBI_USERNAME=<TBC_DBI_USERNAME>
CAMORA_TBC_DBI_PASSWORD=<TBC_DBI_PASSWORD>
CAMORA_TBC_DBI_CERTIFICATE_PATH=<PATH_TO_PFX>
CAMORA_TBC_DBI_CERTIFICATE_BASE64=
CAMORA_TBC_DBI_CERTIFICATE_PASSWORD=<PFX_PASSWORD>
CAMORA_TBC_DBI_ACCOUNT_NUMBER=<TBC_ACCOUNT_IBAN>
CAMORA_TBC_DBI_CURRENCY=GEL
CAMORA_TBC_DBI_PAGE_SIZE=700
CAMORA_TBC_DBI_TIMEOUT_SECONDS=120
CAMORA_TBC_DBI_LARGE_CREDIT_THRESHOLD=1000.00
```

Use either certificate path or certificate Base64.

For local Windows path, prefer forward slashes:

```env
CAMORA_TBC_DBI_CERTIFICATE_PATH=C:/Users/<USER>/certs/company.pfx
```

Avoid this style in env files because backslashes can be lost or treated as escapes:

```env
CAMORA_TBC_DBI_CERTIFICATE_PATH=C:\Users\<USER>\certs\company.pfx
```

## 4. Add Configuration

Backend configuration is read from `application.yml` into `CamoraProperties`.

Add or verify:

```yaml
camora:
  tbc-dbi:
    enabled: "${CAMORA_TBC_DBI_ENABLED:false}"
    endpoint: "${CAMORA_TBC_DBI_ENDPOINT:https://secdbi.tbconline.ge/dbi/dbiService}"
    username: "${CAMORA_TBC_DBI_USERNAME:}"
    password: "${CAMORA_TBC_DBI_PASSWORD:}"
    certificate-path: "${CAMORA_TBC_DBI_CERTIFICATE_PATH:}"
    certificate-base64: "${CAMORA_TBC_DBI_CERTIFICATE_BASE64:}"
    certificate-password: "${CAMORA_TBC_DBI_CERTIFICATE_PASSWORD:}"
    account-number: "${CAMORA_TBC_DBI_ACCOUNT_NUMBER:}"
    currency: "${CAMORA_TBC_DBI_CURRENCY:GEL}"
    page-size: "${CAMORA_TBC_DBI_PAGE_SIZE:700}"
    timeout-seconds: "${CAMORA_TBC_DBI_TIMEOUT_SECONDS:120}"
    large-credit-threshold: "${CAMORA_TBC_DBI_LARGE_CREDIT_THRESHOLD:1000.00}"
```

Add Java properties in `CamoraProperties.TbcDbi`:

```java
private boolean enabled;
private String endpoint;
private String username;
private String password;
private String certificatePath;
private String certificateBase64;
private String certificatePassword;
private String accountNumber;
private String currency;
private int pageSize;
private int timeoutSeconds;
private BigDecimal largeCreditThreshold;
```

Add getters and setters for every property.

## 5. Create TBC Client Component

Create:

```java
@Component
public class TbcDbiClient {
    private final CamoraProperties properties;
    private volatile String runtimePassword;
}
```

`runtimePassword` is used after successful password change. It lets the running backend use the new password immediately. You still must update env or Secret Manager before restart.

## 6. Validate Configuration

Before calling TBC, validate required settings.

For account movements, require configured password:

```java
validateConfig(config, true);
```

For password change with manual temporary password override, allow config password to be blank:

```java
validateConfig(config, false);
```

Required config:

```text
CAMORA_TBC_DBI_ENABLED=true
CAMORA_TBC_DBI_ENDPOINT
CAMORA_TBC_DBI_USERNAME
CAMORA_TBC_DBI_PASSWORD, unless current password override is supplied
CAMORA_TBC_DBI_CERTIFICATE_PATH or CAMORA_TBC_DBI_CERTIFICATE_BASE64
CAMORA_TBC_DBI_CERTIFICATE_PASSWORD
CAMORA_TBC_DBI_ACCOUNT_NUMBER
CAMORA_TBC_DBI_CURRENCY
```

## 7. Load The Certificate

TBC DBI uses mTLS. That means the HTTPS connection needs the client certificate.

Java steps:

1. Read PFX bytes from `CAMORA_TBC_DBI_CERTIFICATE_BASE64`, if set.
2. Otherwise read file from `CAMORA_TBC_DBI_CERTIFICATE_PATH`.
3. Load bytes into `KeyStore` type `PKCS12`.
4. Initialize `KeyManagerFactory`.
5. Create `SSLContext`.
6. Put SSL context into `HttpClient`.

Code shape:

```java
char[] password = config.getCertificatePassword().toCharArray();
KeyStore keyStore = KeyStore.getInstance("PKCS12");
try (var input = certificateInput(config)) {
    keyStore.load(input, password);
}

KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
keyManagerFactory.init(keyStore, password);

SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
```

If using Base64, create it in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\company.pfx")) | Set-Clipboard
```

Then paste into:

```env
CAMORA_TBC_DBI_CERTIFICATE_BASE64=<BASE64_PFX>
```

If using path locally:

```env
CAMORA_TBC_DBI_CERTIFICATE_BASE64=
CAMORA_TBC_DBI_CERTIFICATE_PATH=C:/Users/<USER>/certs/company.pfx
```

## 8. Build SOAP HTTP Request

Every TBC SOAP request needs:

```http
POST <CAMORA_TBC_DBI_ENDPOINT>
Content-Type: text/xml; charset=utf-8
SOAPAction: "<operation action>"
```

Use Java `HttpClient` built with the certificate SSL context.

Code shape:

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(config.getEndpoint()))
    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
    .header("Content-Type", "text/xml; charset=utf-8")
    .header("SOAPAction", soapAction)
    .POST(HttpRequest.BodyPublishers.ofString(envelope))
    .build();
```

Never log the SOAP envelope because it contains username, password, and OTP.

## 9. Password Change Flow

TBC docs say:

```text
The password in the envelope is temporary.
Changing the password is mandatory before first use.
Sending Nonce in ChangePassword is mandatory.
A token device is needed to generate OTP.
Digipass code is required during ChangePassword in any case.
```

Correct `ChangePassword` request contains all data in one SOAP call:

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
  xmlns:myg="http://www.mygemini.com/schemas/mygemini"
  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
  <soapenv:Header>
    <wsse:Security>
      <wsse:UsernameToken>
        <wsse:Username>USERNAME</wsse:Username>
        <wsse:Password>CURRENT_PASSWORD</wsse:Password>
        <wsse:Nonce>OTP_FROM_DIGIPASS</wsse:Nonce>
      </wsse:UsernameToken>
    </wsse:Security>
  </soapenv:Header>
  <soapenv:Body>
    <myg:ChangePasswordRequestIo>
      <myg:newPassword>NEW_PASSWORD</myg:newPassword>
    </myg:ChangePasswordRequestIo>
  </soapenv:Body>
</soapenv:Envelope>
```

SOAPAction:

```text
"http://www.mygemini.com/schemas/mygemini/ChangePassword"
```

Camora password-change request from frontend:

```json
{
  "otp": "<DIGIPASS_CODE>",
  "newPassword": "<NEW_STRONG_PASSWORD>",
  "currentPasswordOverride": "<TEMPORARY_CURRENT_PASSWORD>"
}
```

Field meaning:

```text
otp                     wsse:Nonce, generated by TBC token/Digipass
newPassword             new permanent DBI password
currentPasswordOverride temporary/current DBI password, if saved env password is stale
```

If password change succeeds:

```text
Set runtimePassword = newPassword.
Tell user to update CAMORA_TBC_DBI_PASSWORD in Secret Manager or env.
```

## 10. Password Policy

TBC password rules:

```text
At least 8 characters
At least one uppercase English letter
At least one lowercase English letter
At least one number
At least one symbol
Cannot be same as username
Cannot be same as current password
Cannot contain & or <
```

Reject bad passwords before calling TBC so the user gets a clear local error.

## 11. Account Movements Flow

After password is valid, call `GetAccountMovements`.

SOAPAction:

```text
"http://www.mygemini.com/schemas/mygemini/GetAccountMovements"
```

Request body includes:

```text
accountNumber
accountCurrencyCode
periodFrom
periodTo
pageIndex
pageSize
```

TBC docs say:

```text
Max page size is 700.
First page index is 0 in docs.
Next page index is 1.
Continue until all pages are downloaded.
```

Camora currently uses a page loop and stops when returned page size is smaller than configured page size.

Beginner-safe implementation:

```java
int pageSize = Math.max(1, Math.min(config.getPageSize(), 700));
int pageIndex = 0;
List<BankTransaction> all = new ArrayList<>();

while (true) {
    String response = postSoap(
        config,
        buildAccountMovementsEnvelope(config, currentPassword(config), dateFrom, dateTo, pageIndex, pageSize),
        "\"http://www.mygemini.com/schemas/mygemini/GetAccountMovements\""
    );
    List<BankTransaction> page = parseMovements(response, config);
    all.addAll(page);
    if (page.size() < pageSize) {
        return all;
    }
    pageIndex++;
}
```

Use date-time format expected by TBC:

```text
yyyy-MM-dd'T'HH:mm:ss.SSS
```

Example:

```text
2026-02-01T00:00:00.000
2026-02-28T23:59:59.000
```

## 12. Parse TBC Movements

TBC SOAP response contains movement fields such as:

```text
amount
currency
debitCredit
valueDate
description
accountNumber
partnerName
partnerTaxCode
partnerAccountNumber
documentNumber
movementId
externalPaymentId
```

Conversion rules:

```text
debitCredit 1 = CREDIT
debitCredit 0 = DEBIT
amount.amount = amount
amount.currency = currency
valueDate or documentDate = date
partnerName = counterparty
partnerTaxCode = counterpartyInn
partnerAccountNumber = counterpartyAccount
description or additionalInformation = description
documentNumber or movementId = reference
```

Keep raw XML text in `rawPayload` for debugging.

## 13. Handle SOAP Faults

Parse SOAP faults from:

```text
faultcode
faultstring
```

Normalize fault code by removing namespace prefix.

Examples:

```text
a:CREDENTIALS_MUST_BE_CHANGED -> CREDENTIALS_MUST_BE_CHANGED
a:INCORRECT_CREDENTIALS -> INCORRECT_CREDENTIALS
a:OTP_FAILED -> OTP_FAILED
a:USER_IS_BLOCKED -> USER_IS_BLOCKED
```

Return app error codes:

```text
TBC_PASSWORD_CHANGE_REQUIRED
TBC_INCORRECT_CREDENTIALS
TBC_OTP_FAILED
TBC_USER_IS_BLOCKED
TBC_SECURITY_POLICIES_NOT_MET
TBC_DBI_REQUEST_FAILED
```

Recommended HTTP statuses:

```text
TBC_PASSWORD_CHANGE_REQUIRED -> 409 Conflict
TBC_INCORRECT_CREDENTIALS -> 401 Unauthorized
TBC_USER_IS_BLOCKED -> 423 Locked
Other TBC SOAP faults -> 502 Bad Gateway
```

## 14. Add Backend Endpoints

Get TBC analysis:

```java
@GetMapping("/tbc")
public ResponseEntity<ApiResponse<BankAnalysisOverviewDto>> tbcOverview(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
) {
    return ResponseEntity.ok(ApiResponse.ok(bankAnalysisService.analyzeTbc(dateFrom, dateTo)));
}
```

Change TBC password:

```java
@PostMapping("/tbc/password-change")
public ResponseEntity<ApiResponse<TbcPasswordChangeResultDto>> changeTbcPassword(
    @RequestBody TbcPasswordChangeRequest request
) {
    String message = tbcDbiClient.changePassword(
        request.otp(),
        request.newPassword(),
        request.currentPasswordOverride()
    );
    return ResponseEntity.ok(ApiResponse.ok(new TbcPasswordChangeResultDto(
        message + " Update CAMORA_TBC_DBI_PASSWORD in Secret Manager before restarting or redeploying.",
        "TBC_PASSWORD_CHANGED",
        true
    )));
}
```

## 15. Add Frontend Calls

In `frontend/src/api/bank-analysis.api.ts`:

```ts
export async function getTbcBankAnalysis(dateFrom: string, dateTo: string): Promise<BankAnalysisOverview> {
  const res = await client.get<ApiResponse<BankAnalysisOverview>>(`${BASE}/tbc`, {
    params: { dateFrom, dateTo },
  })
  return res.data.data
}
```

Password change:

```ts
export async function changeTbcPassword(
  otp: string,
  newPassword: string,
  currentPasswordOverride?: string,
): Promise<TbcPasswordChangeResult> {
  const res = await client.post<ApiResponse<TbcPasswordChangeResult>>(`${BASE}/tbc/password-change`, {
    otp,
    newPassword,
    currentPasswordOverride,
  })
  return res.data.data
}
```

The frontend should show:

```text
Token code / Nonce
Temporary/current DBI password
New DBI password
Returned TBC error code
Returned TBC safe message
```

Do not show raw SOAP in frontend.

## 16. Test In Postman First

Use the existing files:

```text
postman/tbc-dbi-account-movement.postman_collection.json
postman/tbc-dbi-local.postman_environment.json
postman/TBC_DBI_POSTMAN_INSTRUCTIONS.md
```

Add certificate in Postman:

```text
Host: secdbi.tbconline.ge
Port: 443
PFX file: C:\path\company.pfx
Passphrase: <PFX_PASSWORD>
```

Environment values:

```text
tbc_dbi_base_url=https://secdbi.tbconline.ge/dbi/dbiService
tbc_dbi_username=<TBC_DBI_USERNAME>
tbc_dbi_password=<TBC_DBI_PASSWORD>
tbc_dbi_account_number=<TBC_ACCOUNT_IBAN>
tbc_dbi_currency=GEL
tbc_dbi_period_from=2026-02-01T00:00:00.000
tbc_dbi_period_to=2026-02-28T23:59:59.000
tbc_dbi_page_index=0
tbc_dbi_page_size=700
```

Success means:

```text
HTTP 200
SOAP Envelope returned
No SOAP Fault
GetAccountMovementsResponseIo exists
```

## 17. Run Locally

Start backend:

```powershell
$env:CAMORA_TBC_DBI_ENABLED="true"
$env:CAMORA_TBC_DBI_ENDPOINT="https://secdbi.tbconline.ge/dbi/dbiService"
$env:CAMORA_TBC_DBI_USERNAME="<TBC_DBI_USERNAME>"
$env:CAMORA_TBC_DBI_PASSWORD="<TBC_DBI_PASSWORD>"
$env:CAMORA_TBC_DBI_CERTIFICATE_PATH="C:/Users/<USER>/certs/company.pfx"
$env:CAMORA_TBC_DBI_CERTIFICATE_BASE64=""
$env:CAMORA_TBC_DBI_CERTIFICATE_PASSWORD="<PFX_PASSWORD>"
$env:CAMORA_TBC_DBI_ACCOUNT_NUMBER="<TBC_ACCOUNT_IBAN>"
$env:CAMORA_TBC_DBI_CURRENCY="GEL"
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

## 18. Test Backend Directly

Use `curl.exe`, not PowerShell `curl` alias:

```powershell
curl.exe -i "http://localhost:8082/api/v1/bank-analysis/tbc?dateFrom=2026-02-01&dateTo=2026-02-02"
```

If password change is required:

```json
{
  "success": false,
  "data": null,
  "error": "TBC DBI password must be changed before fetching movements.",
  "code": "TBC_PASSWORD_CHANGE_REQUIRED"
}
```

Then use frontend password-change panel or direct request:

```powershell
curl.exe -i -X POST "http://localhost:8082/api/v1/bank-analysis/tbc/password-change" `
  -H "Content-Type: application/json" `
  -d "{\"otp\":\"<DIGIPASS_CODE>\",\"newPassword\":\"<NEW_PASSWORD>\",\"currentPasswordOverride\":\"<TEMPORARY_PASSWORD>\"}"
```

After success:

```text
Update CAMORA_TBC_DBI_PASSWORD to the new permanent password.
Restart backend before final verification.
```

## 19. Common Errors

### `TBC_PASSWORD_CHANGE_REQUIRED`

Meaning:

```text
TBC accepted the request enough to know the user must change password.
```

Fix:

```text
Use ChangePassword with temporary password, new password, and Digipass OTP/Nonce.
```

### `TBC_OTP_FAILED`

Meaning:

```text
TBC requires a valid Digipass/token OTP in wsse:Nonce.
The OTP is missing, expired, or wrong.
```

Fix:

```text
Ask TBC how this DBI user obtains the OTP/Nonce.
Do not keep retrying random values because the user can be blocked.
```

### `TBC_INCORRECT_CREDENTIALS`

Meaning:

```text
Username or current password is wrong.
The temporary password may already be used or changed.
```

Fix:

```text
Check DBI username.
Check current temporary or permanent DBI password.
Use currentPasswordOverride if env password is stale.
Ask TBC to reset credentials if needed.
```

### `TBC_USER_IS_BLOCKED`

Meaning:

```text
Too many incorrect attempts or bank-side block.
```

Fix:

```text
Stop retrying.
Ask TBC or personal banker to unblock/reset the DBI user.
```

### `Illegal base64 character`

Meaning:

```text
CAMORA_TBC_DBI_CERTIFICATE_BASE64 contains placeholder text or invalid Base64.
```

Fix:

```text
Set CAMORA_TBC_DBI_CERTIFICATE_BASE64 empty if using certificate path.
Or put real Base64 PFX content.
```

### `NoSuchFileException` for PFX

Meaning:

```text
Certificate path is wrong.
Windows backslashes may have been stripped.
```

Fix:

```env
CAMORA_TBC_DBI_CERTIFICATE_PATH=C:/Users/<USER>/certs/company.pfx
```

Restart backend.

### `SOAPAction header not found`

Meaning:

```text
Request did not include SOAPAction.
```

Fix:

```text
Set SOAPAction exactly with quotes:
"http://www.mygemini.com/schemas/mygemini/GetAccountMovements"
"http://www.mygemini.com/schemas/mygemini/ChangePassword"
```

### `SOAPAction contains an invalid value`

Meaning:

```text
SOAPAction is not the full TBC action URL.
```

Fix:

```text
Do not use only GetAccountMovements.
Use full URL inside quotes.
```

## 20. Security Rules

Do not:

```text
Commit .pfx files.
Commit DBI username/password.
Commit certificate password.
Commit OTP/Digipass code.
Log SOAP request envelope.
Show raw SOAP response in frontend.
Put bank secrets in frontend .env.
```

Do:

```text
Use backend env or GCP Secret Manager.
Use certificate path only on local machine.
Use certificate Base64 in container/cloud if file mount is hard.
Mask values in logs.
Show sanitized TBC error codes to frontend.
```

## 21. Production Deployment Checklist

Before deploy:

```text
CAMORA_TBC_DBI_ENABLED=true
CAMORA_TBC_DBI_ENDPOINT is production endpoint
CAMORA_TBC_DBI_USERNAME is DBI username, not app key
CAMORA_TBC_DBI_PASSWORD is permanent DBI password after password change
CAMORA_TBC_DBI_CERTIFICATE_BASE64 or certificate mount exists
CAMORA_TBC_DBI_CERTIFICATE_PASSWORD is correct
CAMORA_TBC_DBI_ACCOUNT_NUMBER is activated for DBI
CAMORA_TBC_DBI_CURRENCY is correct
Backend can reach secdbi.tbconline.ge:443
Frontend uses backend route, not direct TBC URL
```

Validate production compose:

```powershell
docker compose -f docker/compose.production.yml config
```

## 22. Minimal Implementation Order

If starting from zero, implement in this order:

1. Add env properties.
2. Add `TbcDbi` config class.
3. Add `TbcDbiException`.
4. Add certificate loader.
5. Add SOAP HTTP sender.
6. Add SOAP fault parser.
7. Add password-change envelope.
8. Add password-change backend endpoint.
9. Test password change with real Digipass OTP/Nonce.
10. Save permanent password to env or Secret Manager.
11. Add account-movement envelope.
12. Add movement parser.
13. Add paging.
14. Add `BankAnalysisService.analyzeTbc`.
15. Add controller endpoint.
16. Add frontend provider option.
17. Add frontend password-change form.
18. Test direct backend endpoint.
19. Test frontend page.
20. Deploy secrets.
