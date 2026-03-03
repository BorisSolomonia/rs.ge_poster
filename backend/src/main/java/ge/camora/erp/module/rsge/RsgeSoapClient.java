package ge.camora.erp.module.rsge;

import ge.camora.erp.config.CamoraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class RsgeSoapClient {

    private static final Logger log = LoggerFactory.getLogger(RsgeSoapClient.class);

    private static final String NS = "http://tempuri.org/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int CHUNK_DAYS = 3;

    private final CamoraProperties.RsgeApi properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    public RsgeSoapClient(CamoraProperties camoraProperties) {
        this.properties = camoraProperties.getRsgeApi();
    }

    public List<Map<String, Object>> getBuyerWaybills(LocalDate startDate, LocalDate endDate) {
        validateCredentials();

        Map<String, String> params = new HashMap<>();
        params.put("create_date_s", startDate.atStartOfDay().format(DATE_FORMAT));
        params.put("create_date_e", endDate.plusDays(1).atStartOfDay().format(DATE_FORMAT));

        try {
            return callSoapWithRetry("get_buyer_waybills", params);
        } catch (RsgeIntegrationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RsgeIntegrationException("Failed to fetch purchase waybills from rs.ge", ex);
        }
    }

    private void validateCredentials() {
        if (isBlank(properties.getEndpoint())) {
            throw new RsgeIntegrationException("RS.ge endpoint is not configured");
        }
        if (isBlank(properties.getUsername()) || isBlank(properties.getPassword())) {
            throw new RsgeIntegrationException("RS.ge credentials are not configured");
        }
    }

    private List<Map<String, Object>> callSoapWithRetry(String operation, Map<String, String> params) throws Exception {
        params.put("su", properties.getUsername());
        params.put("sp", properties.getPassword());
        params.put("seller_un_id", extractSellerId(properties.getUsername()));

        String response = sendSoapRequest(operation, params);
        Map<String, Object> result = parseSoapResponse(response, operation);
        int statusCode = getStatusCode(result);

        if (statusCode == -101) {
            String fallbackSellerId = properties.getUsername() != null ? properties.getUsername().trim() : "";
            if (!fallbackSellerId.isBlank()) {
                log.warn("rs.ge returned -101 for operation={}, retrying with fallback seller_un_id", operation);
                params.put("seller_un_id", fallbackSellerId);
                String retryResponse = sendSoapRequest(operation, params);
                Map<String, Object> retryResult = parseSoapResponse(retryResponse, operation);
                int retryStatus = getStatusCode(retryResult);
                if (retryStatus == -101) {
                    throw new RsgeIntegrationException("RS.ge rejected seller credentials");
                }
                return extractWaybillsDeep(retryResult);
            }
            throw new RsgeIntegrationException("RS.ge rejected seller credentials");
        }

        if (statusCode == -1064) {
            return fetchInChunks(operation, params);
        }

        return extractWaybillsDeep(result);
    }

    private List<Map<String, Object>> fetchInChunks(String operation, Map<String, String> originalParams) {
        LocalDate startInclusive = LocalDate.parse(originalParams.get("create_date_s").substring(0, 10));
        LocalDate endExclusive = LocalDate.parse(originalParams.get("create_date_e").substring(0, 10));
        if (!endExclusive.isAfter(startInclusive)) {
            return List.of();
        }

        LocalDate endInclusive = endExclusive.minusDays(1);
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();

        LocalDate chunkStart = startInclusive;
        while (!chunkStart.isAfter(endInclusive)) {
            LocalDate chunkEndInclusive = chunkStart.plusDays(CHUNK_DAYS - 1L);
            if (chunkEndInclusive.isAfter(endInclusive)) {
                chunkEndInclusive = endInclusive;
            }

            LocalDate requestStart = chunkStart;
            LocalDate requestEnd = chunkEndInclusive;
            futures.add(CompletableFuture.supplyAsync(() -> fetchChunk(operation, originalParams, requestStart, requestEnd)));
            chunkStart = chunkEndInclusive.plusDays(1);
        }

        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> fetchChunk(
        String operation,
        Map<String, String> originalParams,
        LocalDate startInclusive,
        LocalDate endInclusive
    ) {
        try {
            Map<String, String> chunkParams = new HashMap<>(originalParams);
            chunkParams.put("create_date_s", startInclusive.atStartOfDay().format(DATE_FORMAT));
            chunkParams.put("create_date_e", endInclusive.plusDays(1).atStartOfDay().format(DATE_FORMAT));
            String response = sendSoapRequest(operation, chunkParams);
            Map<String, Object> result = parseSoapResponse(response, operation);
            return extractWaybillsDeep(result);
        } catch (Exception ex) {
            throw new RsgeIntegrationException(
                String.format("Failed to fetch rs.ge purchase waybills chunk %s..%s", startInclusive, endInclusive),
                ex
            );
        }
    }

    private String sendSoapRequest(String operation, Map<String, String> params) throws Exception {
        String soapBody = buildSoapEnvelope(operation, params);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(properties.getEndpoint()))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "\"" + NS + operation + "\"")
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(soapBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 500) {
            throw new RsgeIntegrationException("RS.ge returned HTTP " + response.statusCode());
        }

        if (properties.isDebug() && properties.getDebugResponseSnippetLength() > 0 && response.statusCode() == 500) {
            log.debug(
                "rs.ge operation={} http500={}",
                operation,
                snippet(response.body(), properties.getDebugResponseSnippetLength())
            );
        }

        return response.body();
    }

    private String buildSoapEnvelope(String operation, Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.append('<').append(entry.getKey()).append('>')
                .append(xmlEscape(entry.getValue()))
                .append("</").append(entry.getKey()).append('>');
        }

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                           xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <%s xmlns="%s">
                  %s
                </%s>
              </soap:Body>
            </soap:Envelope>
            """.formatted(operation, NS, body, operation);
    }

    private Map<String, Object> parseSoapResponse(String xml, String operation) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        hardenXmlFactory(factory);
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));

        NodeList faults = document.getElementsByTagName("faultstring");
        if (faults.getLength() > 0) {
            throw new RsgeIntegrationException(faults.item(0).getTextContent());
        }

        NodeList results = document.getElementsByTagName(operation + "Result");
        if (results.getLength() == 0) {
            return Map.of();
        }
        return nodeToMap(results.item(0));
    }

    private void hardenXmlFactory(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception ex) {
            log.warn("XML parser hardening not fully supported: {}", ex.getMessage());
        }
    }

    private Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> map = new HashMap<>();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String name = child.getNodeName();
            NodeList grandchildren = child.getChildNodes();
            if (grandchildren.getLength() == 1 && grandchildren.item(0).getNodeType() == Node.TEXT_NODE) {
                map.put(name, child.getTextContent());
                continue;
            }

            Object existing = map.get(name);
            Map<String, Object> childMap = nodeToMap(child);
            if (existing instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> typed = (List<Map<String, Object>>) list;
                typed.add(childMap);
            } else if (existing != null) {
                List<Object> typed = new ArrayList<>();
                typed.add(existing);
                typed.add(childMap);
                map.put(name, typed);
            } else {
                map.put(name, childMap);
            }
        }
        return map;
    }

    private int getStatusCode(Map<String, Object> result) {
        Object status = result.get("STATUS");
        if (status == null) {
            Object inner = result.get("RESULT");
            if (inner instanceof Map<?, ?> innerMap) {
                status = innerMap.get("STATUS");
            }
        }
        if (status == null) {
            return 0;
        }
        try {
            return Integer.parseInt(status.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<Map<String, Object>> extractWaybillsDeep(Map<String, Object> root) {
        Object unwrapped = unwrapResult(root);
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(unwrapped);

        while (!queue.isEmpty()) {
            Object current = queue.poll();
            if (current == null) {
                continue;
            }

            if (current instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> || item instanceof List<?>) {
                        queue.add(item);
                    }
                }
                continue;
            }

            if (!(current instanceof Map<?, ?> currentMap) || !visited.add(current)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) currentMap;
            if (isWaybillCandidate(map)) {
                String id = firstNonBlank(map, "ID", "id", "waybill_id", "waybillId");
                if (id == null) {
                    id = "unknown_" + byId.size();
                }
                Map<String, Object> existing = byId.get(id);
                byId.put(id, existing == null ? map : chooseRicherWaybill(existing, map));
            }

            pushContainer(queue, map.get("WAYBILL_LIST"));
            pushContainer(queue, map.get("WAYBILL"));
            pushContainer(queue, map.get("BUYER_WAYBILL"));
            pushContainer(queue, map.get("PURCHASE_WAYBILL"));

            for (Object value : map.values()) {
                if (value instanceof Map<?, ?> || value instanceof List<?>) {
                    queue.add(value);
                }
            }
        }

        List<Map<String, Object>> waybills = new ArrayList<>(byId.values());
        if (properties.isDebug()) {
            logDebugSamples(waybills);
        }
        return waybills;
    }

    private Object unwrapResult(Map<String, Object> result) {
        Object inner = result.get("RESULT");
        if (inner instanceof Map<?, ?>) {
            return inner;
        }
        return result;
    }

    private void pushContainer(ArrayDeque<Object> queue, Object candidate) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof Map<?, ?> containerMap) {
            Object inner = containerMap.get("WAYBILL");
            queue.add(inner != null ? inner : candidate);
            return;
        }
        queue.add(candidate);
    }

    private boolean isWaybillCandidate(Map<String, Object> map) {
        boolean hasId = firstNonBlank(map, "ID", "id", "waybill_id", "waybillId") != null;
        if (!hasId) {
            return false;
        }

        return map.containsKey("FULL_AMOUNT") || map.containsKey("full_amount")
            || map.containsKey("TOTAL_AMOUNT") || map.containsKey("total_amount")
            || map.containsKey("GROSS_AMOUNT") || map.containsKey("gross_amount")
            || map.containsKey("NET_AMOUNT") || map.containsKey("net_amount")
            || map.containsKey("AMOUNT_LARI") || map.containsKey("amount_lari")
            || map.containsKey("AMOUNT") || map.containsKey("amount")
            || map.containsKey("BUYER_TIN") || map.containsKey("buyer_tin")
            || map.containsKey("SELLER_TIN") || map.containsKey("seller_tin")
            || map.containsKey("STATUS") || map.containsKey("status")
            || map.containsKey("CREATE_DATE") || map.containsKey("create_date");
    }

    private Map<String, Object> chooseRicherWaybill(Map<String, Object> left, Map<String, Object> right) {
        int leftScore = waybillCompletenessScore(left);
        int rightScore = waybillCompletenessScore(right);
        if (rightScore > leftScore) {
            return right;
        }
        if (leftScore > rightScore) {
            return left;
        }
        return right.size() > left.size() ? right : left;
    }

    private int waybillCompletenessScore(Map<String, Object> map) {
        int score = 0;
        if (hasNonBlank(map,
            "FULL_AMOUNT", "full_amount",
            "TOTAL_AMOUNT", "total_amount",
            "GROSS_AMOUNT", "gross_amount",
            "NET_AMOUNT", "net_amount",
            "AMOUNT_LARI", "amount_lari",
            "AMOUNT", "amount",
            "SUM", "sum",
            "SUMA", "suma",
            "VALUE", "value",
            "VALUE_LARI", "value_lari")) {
            score += 20;
        }
        if (hasNonBlank(map, "CREATE_DATE", "create_date", "WAYBILL_DATE", "waybill_date", "DATE", "date")) {
            score += 8;
        }
        if (hasNonBlank(map, "BUYER_TIN", "buyer_tin")) {
            score += 3;
        }
        if (hasNonBlank(map, "SELLER_TIN", "seller_tin")) {
            score += 3;
        }
        if (hasNonBlank(map, "STATUS", "status")) {
            score += 1;
        }
        score += Math.min(map.size(), 50) / 5;
        return score;
    }

    private boolean hasNonBlank(Map<String, Object> map, String... keys) {
        return firstNonBlank(map, keys) != null;
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private void logDebugSamples(List<Map<String, Object>> waybills) {
        int limit = Math.min(Math.max(properties.getDebugSampleCount(), 0), waybills.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> waybill = waybills.get(i);
            log.debug(
                "rs.ge sample idx={} id={} seller={} amount={} date={}",
                i,
                firstNonBlank(waybill, "ID", "id", "waybill_id", "waybillId"),
                firstNonBlank(waybill, "SELLER_NAME", "seller_name", "SellerName"),
                firstNonBlank(waybill, "FULL_AMOUNT", "full_amount", "TOTAL_AMOUNT", "total_amount"),
                firstNonBlank(waybill, "CREATE_DATE", "create_date", "WAYBILL_DATE", "waybill_date", "DATE", "date")
            );
        }
    }

    private String snippet(String input, int maxLength) {
        if (input == null || maxLength <= 0 || input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength) + "...";
    }

    private String extractSellerId(String username) {
        if (username == null) {
            return "";
        }
        int separatorIndex = username.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == username.length() - 1) {
            return "";
        }
        return username.substring(separatorIndex + 1);
    }

    private String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char current : value.toCharArray()) {
            switch (current) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> {
                    if (current >= 0x20 || current == 0x09 || current == 0x0A || current == 0x0D) {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
