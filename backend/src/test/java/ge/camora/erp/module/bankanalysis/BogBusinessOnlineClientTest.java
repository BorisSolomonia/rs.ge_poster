package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
