package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BogBusinessOnlineClientTest {

    @Test
    void disabledIntegrationReturnsStableErrorCode() {
        CamoraProperties properties = new CamoraProperties();
        properties.getBogApi().setEnabled(false);
        BogBusinessOnlineClient client = new BogBusinessOnlineClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)))
            .isInstanceOf(BogApiException.class)
            .hasFieldOrPropertyWithValue("code", BogApiException.DISABLED);
    }

    @Test
    void debitStatementUsesBeneficiaryInnAsCounterpartyInn() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        BogBusinessOnlineClient client = new BogBusinessOnlineClient(properties, objectMapper);
        String json = """
            {
              "Records": [
                {
                  "EntryAmountDebit": "250.00",
                  "EntryDate": "2026-05-20",
                  "DocumentSourceCurrency": "GEL",
                  "EntryAccountNumber": "GE00BG0000000000000000GEL",
                  "BeneficiaryDetails": {
                    "Name": "Supplier X",
                    "Inn": "123456789",
                    "AccountNumber": "GE00BG0000000000000001GEL"
                  },
                  "EntryComment": "Supplier payment",
                  "EntryDocumentNumber": "BOG-REF"
                }
              ]
            }
            """;

        var transactions = client.parseRecords(objectMapper.readTree(json), config);

        assertThat(transactions).hasSize(1);
        BankTransaction tx = transactions.get(0);
        assertThat(tx.direction()).isEqualTo("DEBIT");
        assertThat(tx.counterparty()).isEqualTo("Supplier X");
        assertThat(tx.counterpartyInn()).isEqualTo("123456789");
        assertThat(tx.counterpartyAccount()).isEqualTo("GE00BG0000000000000001GEL");
    }

    @Test
    void statementRefreshesTokenOnceWhenCachedTokenIsUnauthorized() throws Exception {
        AtomicInteger tokenCalls = new AtomicInteger();
        AtomicInteger statementCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth", exchange -> {
            int call = tokenCalls.incrementAndGet();
            sendJson(exchange, 200, """
                {"access_token":"%s","expires_in":300}
                """.formatted(call == 1 ? "stale-token" : "fresh-token"));
        });
        server.createContext("/api/statement/v2", exchange -> {
            statementCalls.incrementAndGet();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if ("Bearer stale-token".equals(authorization)) {
                sendJson(exchange, 401, "{\"error\":\"expired\"}");
                return;
            }
            sendJson(exchange, 200, """
                {
                  "Count": 1,
                  "TotalCount": 1,
                  "Records": [
                    {
                      "EntryAmountDebit": "125.00",
                      "EntryDate": "2026-02-01",
                      "DocumentSourceCurrency": "GEL",
                      "EntryAccountNumber": "GE00BG0000000000000000GEL",
                      "BeneficiaryDetails": {
                        "Name": "Supplier X",
                        "Inn": "123456789",
                        "AccountNumber": "GE00BG0000000000000001GEL"
                      },
                      "EntryDocumentNumber": "BOG-REF"
                    }
                  ]
                }
                """);
        });
        server.start();
        try {
            CamoraProperties properties = new CamoraProperties();
            CamoraProperties.BogApi config = properties.getBogApi();
            int port = server.getAddress().getPort();
            config.setEnabled(true);
            config.setTokenUrl("http://localhost:" + port + "/auth");
            config.setBaseUrl("http://localhost:" + port + "/api");
            config.setClientId("client");
            config.setClientSecret("secret");
            config.setAccountNumber("GE00BG0000000000000000GEL");
            config.setCurrency("GEL");
            config.setTake(1000);
            config.setTimeoutSeconds(5);
            BogBusinessOnlineClient client = new BogBusinessOnlineClient(properties, new ObjectMapper());

            var transactions = client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2));

            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).amount()).isEqualByComparingTo("125.00");
            assertThat(tokenCalls).hasValue(2);
            assertThat(statementCalls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void statementRetriesTransientRstStreamFailures() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setEnabled(true);
        config.setTokenUrl("https://bog.example/auth");
        config.setBaseUrl("https://bog.example/api");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        config.setTake(1000);
        config.setTimeoutSeconds(5);
        config.setStatementRetryAttempts(3);
        config.setStatementRetryDelayMillis(0);
        RetryingBogClient client = new RetryingBogClient(properties, new ObjectMapper());

        var transactions = client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1));

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo("125.00");
        assertThat(client.tokenCalls).hasValue(1);
        assertThat(client.statementCalls).hasValue(3);
    }

    @Test
    void statementForbiddenErrorIncludesSafeActionableContext() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setEnabled(true);
        config.setTokenUrl("https://bog.example/auth");
        config.setBaseUrl("https://bog.example/api");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        config.setTake(1000);
        config.setTimeoutSeconds(5);
        ForbiddenBogClient client = new ForbiddenBogClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)))
            .isInstanceOf(BogApiException.class)
            .hasMessageContaining("BOG API HTTP 403")
            .hasMessageContaining("account=GE00...0GEL")
            .hasMessageContaining("currency=GEL")
            .hasMessageContaining("IP allowlist")
            .hasMessageNotContaining("GE00BG0000000000000000GEL");
        assertThat(client.tokenCalls).hasValue(1);
        assertThat(client.statementCalls).hasValue(1);
    }

    @Test
    void singleDayOverflowDoesNotCallDeniedPageEndpoint() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setEnabled(true);
        config.setTokenUrl("https://bog.example/auth");
        config.setBaseUrl("https://bog.example/api");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        config.setTake(1);
        config.setTimeoutSeconds(5);
        PagingBogClient client = new PagingBogClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1)))
            .isInstanceOf(BogApiException.class)
            .hasFieldOrPropertyWithValue("code", BogApiException.STATEMENT_FAILED)
            .hasMessageContaining("did not call BOG's statement page endpoint");

        assertThat(client.statementPaths).hasSize(1);
        assertThat(client.statementPaths).noneMatch(path -> path.endsWith("/42/3/true"));
    }

    @Test
    void statementSplitsDateRangeInsteadOfCallingPageEndpointWhenPaginationIsDisabled() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setEnabled(true);
        config.setTokenUrl("https://bog.example/auth");
        config.setBaseUrl("https://bog.example/api");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        config.setTake(1);
        config.setTimeoutSeconds(5);
        SplitBogClient client = new SplitBogClient(properties, new ObjectMapper());

        var transactions = client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2));

        assertThat(transactions).hasSize(2);
        assertThat(client.statementPaths).hasSize(3);
        assertThat(client.statementPaths).anyMatch(path -> path.contains("/2026-02-01/2026-02-02/false/true/1"));
        assertThat(client.statementPaths).anyMatch(path -> path.contains("/2026-02-01/2026-02-01/false/true/1"));
        assertThat(client.statementPaths).anyMatch(path -> path.contains("/2026-02-02/2026-02-02/false/true/1"));
        assertThat(client.statementPaths).noneMatch(path -> path.endsWith("/42/2/true"));
    }

    @Test
    void statementChunksLongDateRangesBeforeCallingBog() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setEnabled(true);
        config.setTokenUrl("https://bog.example/auth");
        config.setBaseUrl("https://bog.example/api");
        config.setClientId("client");
        config.setClientSecret("secret");
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        config.setTake(1000);
        config.setTimeoutSeconds(5);
        config.setStatementChunkDays(2);
        EmptyBogClient client = new EmptyBogClient(properties, new ObjectMapper());

        var transactions = client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));

        assertThat(transactions).isEmpty();
        assertThat(client.statementPaths).hasSize(3);
        assertThat(client.statementPaths.get(0)).contains("/2026-02-01/2026-02-02/false/true/1000");
        assertThat(client.statementPaths.get(1)).contains("/2026-02-03/2026-02-04/false/true/1000");
        assertThat(client.statementPaths.get(2)).contains("/2026-02-05/2026-02-05/false/true/1000");
    }


    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static final class RetryingBogClient extends BogBusinessOnlineClient {
        private final AtomicInteger tokenCalls = new AtomicInteger();
        private final AtomicInteger statementCalls = new AtomicInteger();

        private RetryingBogClient(CamoraProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
            String path = request.uri().getPath();
            if (path.contains("/auth")) {
                tokenCalls.incrementAndGet();
                return response(request, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
            }
            if (path.contains("/statement/v2")) {
                int call = statementCalls.incrementAndGet();
                if (call < 3) {
                    throw new IOException("Received RST_STREAM: Internal error");
                }
                return response(request, 200, """
                    {
                      "Count": 1,
                      "TotalCount": 1,
                      "Records": [
                        {
                          "EntryAmountDebit": "125.00",
                          "EntryDate": "2026-02-01",
                          "DocumentSourceCurrency": "GEL",
                          "EntryAccountNumber": "GE00BG0000000000000000GEL",
                          "BeneficiaryDetails": {
                            "Name": "Supplier X",
                            "Inn": "123456789",
                            "AccountNumber": "GE00BG0000000000000001GEL"
                          },
                          "EntryDocumentNumber": "BOG-REF"
                        }
                      ]
                    }
                    """);
            }
            throw new IOException("Unexpected request " + request.uri());
        }
    }

    private static final class PagingBogClient extends BogBusinessOnlineClient {
        private final List<String> statementPaths = new ArrayList<>();

        private PagingBogClient(CamoraProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
            String path = request.uri().getPath();
            if (path.contains("/auth")) {
                return response(request, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
            }
            if (path.contains("/statement/v2")) {
                statementPaths.add(path);
                if (statementPaths.size() == 1) {
                    return response(request, 200, """
                        {
                          "Id": 42,
                          "Count": 2,
                          "TotalCount": 3,
                          "Records": []
                        }
                        """);
                }
                if (path.endsWith("/42/2/true")) {
                    return response(request, 200, """
                        {
                          "Count": 1,
                          "TotalCount": 3,
                          "Records": [
                            {
                              "EntryAmountDebit": "125.00",
                              "EntryDate": "2026-02-01",
                              "DocumentSourceCurrency": "GEL",
                              "EntryAccountNumber": "GE00BG0000000000000000GEL",
                              "BeneficiaryDetails": {
                                "Name": "Supplier X",
                                "Inn": "123456789",
                                "AccountNumber": "GE00BG0000000000000001GEL"
                              },
                              "EntryDocumentNumber": "BOG-REF"
                            }
                          ]
                        }
                        """);
                }
            }
            throw new IOException("Unexpected request " + request.uri());
        }
    }

    private static final class SplitBogClient extends BogBusinessOnlineClient {
        private final List<String> statementPaths = new ArrayList<>();

        private SplitBogClient(CamoraProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
            String path = request.uri().getPath();
            if (path.contains("/auth")) {
                return response(request, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
            }
            if (path.contains("/statement/v2")) {
                statementPaths.add(path);
                if (path.contains("/2026-02-01/2026-02-02/")) {
                    return response(request, 200, """
                        {
                          "Id": 42,
                          "Count": 1,
                          "TotalCount": 2,
                          "Records": []
                        }
                        """);
                }
                if (path.contains("/2026-02-01/2026-02-01/")) {
                    return response(request, 200, statementResponse("2026-02-01", "111.00"));
                }
                if (path.contains("/2026-02-02/2026-02-02/")) {
                    return response(request, 200, statementResponse("2026-02-02", "222.00"));
                }
            }
            throw new IOException("Unexpected request " + request.uri());
        }
    }

    private static final class EmptyBogClient extends BogBusinessOnlineClient {
        private final List<String> statementPaths = new ArrayList<>();

        private EmptyBogClient(CamoraProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
            String path = request.uri().getPath();
            if (path.contains("/auth")) {
                return response(request, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
            }
            if (path.contains("/statement/v2")) {
                statementPaths.add(path);
                return response(request, 200, """
                    {
                      "Count": 0,
                      "TotalCount": 0,
                      "Records": []
                    }
                    """);
            }
            throw new IOException("Unexpected request " + request.uri());
        }
    }

    private static String statementResponse(String date, String amount) {
        return """
            {
              "Count": 1,
              "TotalCount": 1,
              "Records": [
                {
                  "EntryAmountDebit": "%s",
                  "EntryDate": "%s",
                  "DocumentSourceCurrency": "GEL",
                  "EntryAccountNumber": "GE00BG0000000000000000GEL",
                  "BeneficiaryDetails": {
                    "Name": "Supplier X",
                    "Inn": "123456789",
                    "AccountNumber": "GE00BG0000000000000001GEL"
                  },
                  "EntryDocumentNumber": "BOG-REF"
                }
              ]
            }
            """.formatted(amount, date);
    }

    private static final class ForbiddenBogClient extends BogBusinessOnlineClient {
        private final AtomicInteger tokenCalls = new AtomicInteger();
        private final AtomicInteger statementCalls = new AtomicInteger();

        private ForbiddenBogClient(CamoraProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
            String path = request.uri().getPath();
            if (path.contains("/auth")) {
                tokenCalls.incrementAndGet();
                return response(request, 200, "{\"access_token\":\"token\",\"expires_in\":300}");
            }
            if (path.contains("/statement/v2")) {
                statementCalls.incrementAndGet();
                return response(request, 403, "");
            }
            throw new IOException("Unexpected request " + request.uri());
        }
    }

    private static HttpResponse<String> response(HttpRequest request, int status, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(java.util.Map.of(), (key, value) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
