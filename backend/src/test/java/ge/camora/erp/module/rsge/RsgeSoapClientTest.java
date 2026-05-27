package ge.camora.erp.module.rsge;

import com.sun.net.httpserver.HttpServer;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RsgeSoapClientTest {

    @Test
    void getBuyerWaybillsUsesPostmanCompatibleBuyerParameters() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = startServer(requestBodies, 0);
        try {
            RsgeSoapClient client = new RsgeSoapClient(properties(server, ""));

            client.getBuyerWaybills(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2));

            assertThat(requestBodies).hasSize(1);
            assertThat(requestBodies.get(0))
                .contains("<itypes>2</itypes>")
                .contains("<buyer_tin>123456789</buyer_tin>")
                .contains("<create_date_s>2025-01-01T00:00:00</create_date_s>")
                .contains("<create_date_e>2025-01-03T00:00:00</create_date_e>")
                .doesNotContain("seller_un_id");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getBuyerWaybillsKeepsSellerIdAsCompatibilityRetryOnly() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = startServer(requestBodies, -101, 0);
        try {
            RsgeSoapClient client = new RsgeSoapClient(properties(server, ""));

            client.getBuyerWaybills(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

            assertThat(requestBodies).hasSize(2);
            assertThat(requestBodies.get(0)).doesNotContain("seller_un_id");
            assertThat(requestBodies.get(1)).contains("<seller_un_id>123456789</seller_un_id>");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getBuyerWaybillsUsesExplicitBuyerTinWhenConfigured() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = startServer(requestBodies, 0);
        try {
            RsgeSoapClient client = new RsgeSoapClient(properties(server, "987654321"));

            client.getBuyerWaybills(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

            assertThat(requestBodies).hasSize(1);
            assertThat(requestBodies.get(0)).contains("<buyer_tin>987654321</buyer_tin>");
        } finally {
            server.stop(0);
        }
    }

    private CamoraProperties properties(HttpServer server, String buyerTin) {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.RsgeApi rsge = properties.getRsgeApi();
        rsge.setEndpoint("http://localhost:" + server.getAddress().getPort());
        rsge.setUsername("service-user:123456789");
        rsge.setPassword("secret");
        rsge.setBuyerTin(buyerTin);
        rsge.setWaybillTypes("2");
        rsge.setTimeoutSeconds(5);
        return properties;
    }

    private HttpServer startServer(List<String> requestBodies, int... statuses) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger callCount = new AtomicInteger();
        server.createContext("/", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int index = Math.min(callCount.getAndIncrement(), statuses.length - 1);
            byte[] response = response(statuses[index]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String response(int status) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <get_buyer_waybillsResponse xmlns="http://tempuri.org/">
                  <get_buyer_waybillsResult>
                    <STATUS>%d</STATUS>
                  </get_buyer_waybillsResult>
                </get_buyer_waybillsResponse>
              </soap:Body>
            </soap:Envelope>
            """.formatted(status);
    }
}
