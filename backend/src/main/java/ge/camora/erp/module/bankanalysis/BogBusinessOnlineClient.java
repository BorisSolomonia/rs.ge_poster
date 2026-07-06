package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class BogBusinessOnlineClient {

    private final CamoraProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Token token;

    public BogBusinessOnlineClient(CamoraProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<BankTransaction> getStatement(LocalDate dateFrom, LocalDate dateTo) {
        CamoraProperties.BogApi config = properties.getBogApi();
        validateConfig(config);
        int chunkDays = config.getStatementChunkDays();
        if (chunkDays <= 0 || !dateTo.isAfter(dateFrom.plusDays(chunkDays - 1L))) {
            return getStatementRange(config, dateFrom, dateTo);
        }
        List<BankTransaction> transactions = new ArrayList<>();
        LocalDate chunkStart = dateFrom;
        while (!chunkStart.isAfter(dateTo)) {
            LocalDate chunkEnd = chunkStart.plusDays(chunkDays - 1L);
            if (chunkEnd.isAfter(dateTo)) {
                chunkEnd = dateTo;
            }
            transactions.addAll(getStatementRange(config, chunkStart, chunkEnd));
            chunkStart = chunkEnd.plusDays(1);
        }
        return transactions;
    }

    private List<BankTransaction> getStatementRange(CamoraProperties.BogApi config, LocalDate dateFrom, LocalDate dateTo) {
        JsonNode firstPage = getJsonWithTokenRefresh(statementUrl(config, dateFrom, dateTo, includeToday(dateTo)), config);
        List<BankTransaction> transactions = new ArrayList<>(parseRecords(firstPage, config));
        int count = firstPage.path("Count").asInt(transactions.size());
        int totalCount = firstPage.path("TotalCount").asInt(count);
        if (totalCount <= count || totalCount <= transactions.size()) {
            return transactions;
        }
        if (dateFrom.isBefore(dateTo)) {
            LocalDate midpoint = dateFrom.plusDays(ChronoUnit.DAYS.between(dateFrom, dateTo) / 2);
            List<BankTransaction> splitTransactions = new ArrayList<>(getStatementRange(config, dateFrom, midpoint));
            splitTransactions.addAll(getStatementRange(config, midpoint.plusDays(1), dateTo));
            return splitTransactions;
        }
        throw new BogApiException(
            BogApiException.STATEMENT_FAILED,
            "BOG statement for " + dateFrom + " returned " + count + " of " + totalCount
                + " records. Camora did not call BOG's statement page endpoint because this BOG client denies it. "
                + "Increase CAMORA_BOG_API_TAKE if BOG allows a larger value, or ask BOG to enable "
                + "/statement/v2/{account}/{currency}/{id}/{page}/{orderByDate} for this client."
        );
    }

    private String accessToken(CamoraProperties.BogApi config) {
        Token current = token;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return current.value();
        }
        synchronized (this) {
            current = token;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
                return current.value();
            }
            token = fetchToken(config);
            return token.value();
        }
    }

    private Token fetchToken(CamoraProperties.BogApi config) {
        try {
            HttpClient client = client(config);
            String auth = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8)
            );
            String body = form("grant_type", "client_credentials")
                + "&" + form("client_id", config.getClientId())
                + "&" + form("client_secret", config.getClientSecret());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getTokenUrl()))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = send(client, request);
            if (response.statusCode() >= 400) {
                throw new BogApiException(
                    BogApiException.HTTP_ERROR,
                    "BOG token HTTP " + response.statusCode() + ": " + snippet(response.body())
                );
            }
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new BogApiException(BogApiException.TOKEN_FAILED, "BOG token response did not contain access_token");
            }
            long expiresIn = Math.max(60, json.path("expires_in").asLong(300));
            return new Token(accessToken, Instant.now().plusSeconds(expiresIn));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BogApiException(BogApiException.TOKEN_FAILED, "BOG token request interrupted", exception);
        } catch (BogApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BogApiException(
                BogApiException.TOKEN_FAILED,
                "Failed to fetch BOG access token: " + exception.getMessage(),
                exception
            );
        }
    }

    private JsonNode getJsonWithTokenRefresh(String url, CamoraProperties.BogApi config) {
        int maxAttempts = Math.max(1, config.getStatementRetryAttempts());
        String currentToken = accessToken(config);
        boolean tokenRefreshed = false;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                return getJson(url, currentToken, config);
            } catch (BogApiException exception) {
                if (isUnauthorizedStatementResponse(exception) && !tokenRefreshed) {
                    invalidateToken(currentToken);
                    currentToken = accessToken(config);
                    tokenRefreshed = true;
                    continue;
                }
                if (isTransientStatementTransportFailure(exception) && attempt < maxAttempts) {
                    sleepBeforeRetry(config, attempt);
                    attempt++;
                    continue;
                }
                throw exception;
            }
        }
        throw new BogApiException(BogApiException.STATEMENT_FAILED, "Failed to fetch BOG statement after retries");
    }

    private void sleepBeforeRetry(CamoraProperties.BogApi config, int attempt) {
        long delayMillis = Math.max(0, config.getStatementRetryDelayMillis());
        if (delayMillis == 0) {
            return;
        }
        long waitMillis = Math.min(30_000L, delayMillis * (long) attempt);
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BogApiException(BogApiException.STATEMENT_FAILED, "BOG statement retry interrupted", exception);
        }
    }

    private JsonNode getJson(String url, String accessToken, CamoraProperties.BogApi config) {
        try {
            HttpClient client = client(config);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = send(client, request);
            if (response.statusCode() >= 400) {
                String message = "BOG API HTTP " + response.statusCode()
                    + " calling " + statementRequestDescription(request, config)
                    + ": " + snippet(response.body());
                if (response.statusCode() == 403) {
                    message += " BOG issued a token, but denied statement access. Verify CAMORA_BOG_API_ACCOUNT_NUMBER, "
                        + "CAMORA_BOG_API_CURRENCY, client permissions/scopes, IP allowlist, and statement API activation.";
                }
                throw new BogApiException(
                    BogApiException.HTTP_ERROR,
                    message
                );
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BogApiException(BogApiException.STATEMENT_FAILED, "BOG statement request interrupted", exception);
        } catch (BogApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BogApiException(
                BogApiException.STATEMENT_FAILED,
                "Failed to fetch BOG statement: " + exception.getMessage(),
                exception
            );
        }
    }

    HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isUnauthorizedStatementResponse(BogApiException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        return BogApiException.HTTP_ERROR.equals(exception.getCode())
            && message.startsWith("BOG API HTTP 401");
    }

    boolean isTransientStatementTransportFailure(BogApiException exception) {
        if (!BogApiException.STATEMENT_FAILED.equals(exception.getCode())) {
            return false;
        }
        String message = throwableMessages(exception).toLowerCase();
        return message.contains("rst_stream")
            || message.contains("goaway")
            || message.contains("connection reset")
            || message.contains("http/2")
            || message.contains("stream was reset")
            || message.contains("stream reset");
    }

    private String throwableMessages(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private void invalidateToken(String staleToken) {
        synchronized (this) {
            if (token != null && token.value().equals(staleToken)) {
                token = null;
            }
        }
    }

    List<BankTransaction> parseRecords(JsonNode response, CamoraProperties.BogApi config) {
        List<BankTransaction> transactions = new ArrayList<>();
        JsonNode records = response.path("Records");
        if (!records.isArray()) {
            return transactions;
        }
        for (JsonNode record : records) {
            BigDecimal debit = decimal(record, "EntryAmountDebit");
            BigDecimal credit = decimal(record, "EntryAmountCredit");
            if (credit.signum() != 0) {
                transactions.add(toTransaction(record, BankTransaction.CREDIT, credit, config));
            }
            if (debit.signum() != 0) {
                transactions.add(toTransaction(record, BankTransaction.DEBIT, debit, config));
            }
        }
        return transactions;
    }

    private BankTransaction toTransaction(JsonNode record, String direction, BigDecimal amount, CamoraProperties.BogApi config) {
        return new BankTransaction(
            date(record),
            direction,
            amount,
            firstNonBlank(text(record, "DocumentSourceCurrency"), text(record, "DocumentDestinationCurrency"), config.getCurrency()),
            firstNonBlank(text(record, "EntryAccountNumber"), config.getAccountNumber()),
            counterparty(record, direction),
            counterpartyInn(record, direction),
            counterpartyAccount(record, direction),
            firstNonBlank(
                text(record, "EntryComment"),
                text(record, "DocumentNomination"),
                text(record, "DocumentInformation"),
                text(record, "DocComment")
            ),
            firstNonBlank(
                text(record, "EntryDocumentNumber"),
                text(record, "DocumentKey"),
                text(record, "EntryId")
            ),
            record.toString()
        );
    }

    private LocalDate date(JsonNode record) {
        return parseDate(firstNonBlank(
            text(record, "EntryDate"),
            text(record, "DocumentValueDate"),
            text(record, "AuthDate")
        ));
    }

    private String counterparty(JsonNode record, String direction) {
        if (BankTransaction.CREDIT.equals(direction)) {
            return firstNonBlank(
                text(record.path("SenderDetails"), "Name"),
                text(record, "DocumentPayerName")
            );
        }
        return firstNonBlank(
            text(record.path("BeneficiaryDetails"), "Name"),
            text(record, "DocumentPayerName")
        );
    }

    private String counterpartyInn(JsonNode record, String direction) {
        if (BankTransaction.CREDIT.equals(direction)) {
            return firstNonBlank(
                text(record.path("SenderDetails"), "Inn"),
                text(record, "DocumentPayerInn")
            );
        }
        return firstNonBlank(
            text(record.path("BeneficiaryDetails"), "Inn"),
            text(record, "DocumentPayerInn")
        );
    }

    private String counterpartyAccount(JsonNode record, String direction) {
        if (BankTransaction.CREDIT.equals(direction)) {
            return text(record.path("SenderDetails"), "AccountNumber");
        }
        return text(record.path("BeneficiaryDetails"), "AccountNumber");
    }

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

    private HttpClient client(CamoraProperties.BogApi config) {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .build();
    }

    private boolean includeToday(LocalDate dateTo) {
        return !dateTo.isBefore(LocalDate.now());
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return BigDecimal.ZERO;
        }
        if (value.isNumber()) {
            // Sign is preserved so reversal entries net against their originals.
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asText().replace(",", ""));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed.substring(0, Math.min(10, trimmed.length())));
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private void validateConfig(CamoraProperties.BogApi config) {
        if (!config.isEnabled()) {
            throw new BogApiException(BogApiException.DISABLED, "BOG API integration is disabled. Set CAMORA_BOG_API_ENABLED=true.");
        }
        require(config.getTokenUrl(), "CAMORA_BOG_API_TOKEN_URL");
        require(config.getBaseUrl(), "CAMORA_BOG_API_BASE_URL");
        require(config.getClientId(), "CAMORA_BOG_API_CLIENT_ID");
        require(config.getClientSecret(), "CAMORA_BOG_API_CLIENT_SECRET");
        require(config.getAccountNumber(), "CAMORA_BOG_API_ACCOUNT_NUMBER");
        require(config.getCurrency(), "CAMORA_BOG_API_CURRENCY");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BogApiException(BogApiException.CONFIG_MISSING, name + " is required");
        }
    }

    private String form(String key, String value) {
        return encode(key) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String statementRequestDescription(HttpRequest request, CamoraProperties.BogApi config) {
        return "statement endpoint path=" + maskPath(request.uri().getPath(), config)
            + ", account=" + maskAccount(config.getAccountNumber())
            + ", currency=" + firstNonBlank(config.getCurrency(), "-");
    }

    private String maskPath(String path, CamoraProperties.BogApi config) {
        String account = config.getAccountNumber();
        if (account == null || account.isBlank()) {
            return path;
        }
        return path.replace(account, maskAccount(account)).replace(encode(account), maskAccount(account));
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return "-";
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private record Token(String value, Instant expiresAt) {
    }
}
