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
        String accessToken = accessToken(config);
        JsonNode firstPage = getJson(statementUrl(config, dateFrom, dateTo, includeToday(dateTo)), accessToken, config);
        List<BankTransaction> transactions = new ArrayList<>(parseRecords(firstPage, config));
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
        return transactions;
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
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("BOG token HTTP " + response.statusCode() + ": " + snippet(response.body()));
            }
            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalStateException("BOG token response did not contain access_token");
            }
            long expiresIn = Math.max(60, json.path("expires_in").asLong(300));
            return new Token(accessToken, Instant.now().plusSeconds(expiresIn));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BOG token request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch BOG access token: " + exception.getMessage(), exception);
        }
    }

    private JsonNode getJson(String url, String accessToken, CamoraProperties.BogApi config) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = client(config).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("BOG API HTTP " + response.statusCode() + ": " + snippet(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BOG statement request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch BOG statement: " + exception.getMessage(), exception);
        }
    }

    private List<BankTransaction> parseRecords(JsonNode response, CamoraProperties.BogApi config) {
        List<BankTransaction> transactions = new ArrayList<>();
        JsonNode records = response.path("Records");
        if (!records.isArray()) {
            return transactions;
        }
        for (JsonNode record : records) {
            BigDecimal debit = decimal(record, "EntryAmountDebit");
            BigDecimal credit = decimal(record, "EntryAmountCredit");
            if (credit.compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(toTransaction(record, "CREDIT", credit, config));
            }
            if (debit.compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(toTransaction(record, "DEBIT", debit, config));
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
        if (direction.equals("CREDIT")) {
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

    private String statementPageUrl(CamoraProperties.BogApi config, long statementId, int page) {
        return trimSlash(config.getBaseUrl())
            + "/statement/v2/"
            + encode(config.getAccountNumber()) + "/"
            + encode(config.getCurrency()) + "/"
            + statementId + "/"
            + page + "/true";
    }

    private HttpClient client(CamoraProperties.BogApi config) {
        return HttpClient.newBuilder()
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
            return value.decimalValue().abs();
        }
        try {
            return new BigDecimal(value.asText().replace(",", "")).abs();
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
            throw new IllegalStateException("BOG API integration is disabled. Set CAMORA_BOG_API_ENABLED=true.");
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
            throw new IllegalStateException(name + " is required");
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

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record Token(String value, Instant expiresAt) {
    }
}
