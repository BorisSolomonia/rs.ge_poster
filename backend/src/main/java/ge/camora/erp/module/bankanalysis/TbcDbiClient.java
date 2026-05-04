package ge.camora.erp.module.bankanalysis;

import ge.camora.erp.config.CamoraProperties;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
public class TbcDbiClient {

    private final CamoraProperties properties;

    public TbcDbiClient(CamoraProperties properties) {
        this.properties = properties;
    }

    public List<BankTransaction> getAccountMovements(LocalDate dateFrom, LocalDate dateTo) {
        CamoraProperties.TbcDbi config = properties.getTbcDbi();
        validateConfig(config);
        int pageSize = Math.max(1, Math.min(config.getPageSize(), 700));
        int pageIndex = 1;
        List<BankTransaction> all = new ArrayList<>();
        while (true) {
            String response = postSoap(config, buildEnvelope(config, dateFrom, dateTo, pageIndex, pageSize));
            List<BankTransaction> page = parseMovements(response, config);
            all.addAll(page);
            if (page.size() < pageSize) {
                return all;
            }
            pageIndex++;
        }
    }

    private String postSoap(CamoraProperties.TbcDbi config, String envelope) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .sslContext(buildSslContext(config))
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getEndpoint()))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"http://www.mygemini.com/schemas/mygemini/GetAccountMovements\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("TBC DBI HTTP " + response.statusCode() + ": " + snippet(response.body()));
            }
            String body = response.body();
            String fault = extractSoapFault(body);
            if (!fault.isBlank()) {
                throw new IllegalStateException("TBC DBI SOAP fault: " + fault);
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TBC DBI request interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch TBC DBI movements: " + exception.getMessage(), exception);
        }
    }

    private SSLContext buildSslContext(CamoraProperties.TbcDbi config) throws Exception {
        char[] password = config.getCertificatePassword().toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = certificateInput(config)) {
            keyStore.load(input, password);
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        return sslContext;
    }

    private String buildEnvelope(
        CamoraProperties.TbcDbi config,
        LocalDate dateFrom,
        LocalDate dateTo,
        int pageIndex,
        int pageSize
    ) {
        String from = dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String to = dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String nonce = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope
              xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
              xmlns:myg="http://www.mygemini.com/schemas/mygemini"
              xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
              <soapenv:Header>
                <wsse:Security>
                  <wsse:UsernameToken>
                    <wsse:Username>%s</wsse:Username>
                    <wsse:Password>%s</wsse:Password>
                    <wsse:Nonce>%s</wsse:Nonce>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <myg:GetAccountMovementsRequestIo>
                  <myg:accountMovementFilterIo>
                    <myg:pager>
                      <myg:pageIndex>%d</myg:pageIndex>
                      <myg:pageSize>%d</myg:pageSize>
                    </myg:pager>
                    <myg:accountNumber>%s</myg:accountNumber>
                    <myg:accountCurrencyCode>%s</myg:accountCurrencyCode>
                    <myg:periodFrom>%s</myg:periodFrom>
                    <myg:periodTo>%s</myg:periodTo>
                  </myg:accountMovementFilterIo>
                </myg:GetAccountMovementsRequestIo>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(
                xml(config.getUsername()),
                xml(config.getPassword()),
                xml(nonce),
                pageIndex,
                pageSize,
                xml(config.getAccountNumber()),
                xml(config.getCurrency()),
                from,
                to
            );
    }

    private List<BankTransaction> parseMovements(String body, CamoraProperties.TbcDbi config) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            List<BankTransaction> transactions = new ArrayList<>();
            NodeList elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (!hasChild(element, "amount", "transactionAmount", "operationAmount", "creditAmount", "debitAmount")
                    || !hasChild(element, "debitCredit", "debitcredit", "debCred", "entryType", "movementType")) {
                    continue;
                }
                BankTransaction transaction = toTransaction(element, config);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
            return transactions;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not parse TBC DBI response: " + exception.getMessage(), exception);
        }
    }

    private BankTransaction toTransaction(Element element, CamoraProperties.TbcDbi config) {
        BigDecimal amount = parseAmount(firstText(element, "amount", "sum", "entryAmount", "transactionAmount", "operationAmount", "creditAmount", "debitAmount"));
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        String debitCredit = firstText(element, "debitCredit", "debitcredit", "debCred", "entryType", "movementType");
        String direction = parseDirection(debitCredit);
        LocalDate date = parseDate(firstText(element, "docDate", "date", "operationDate", "valueDate"));
        if (date == null || direction.isBlank()) {
            return null;
        }
        String counterparty = firstNonBlank(
            firstText(element, "partnerName", "partner", "counterparty", "recipientName", "payerName", "beneficiaryName", "senderName"),
            firstText(element, "partnerAccount", "recipientAccount", "payerAccount", "beneficiaryAccount", "senderAccount")
        );
        String description = firstText(element, "description", "purpose", "comment", "additionalInfo", "paymentDetails");
        String reference = firstText(element, "documentKey", "docKey", "documentNumber", "reference");
        String currency = firstNonBlank(firstText(element, "currency", "ccy"), config.getCurrency());
        String accountNumber = firstNonBlank(firstText(element, "accountNumber", "account"), config.getAccountNumber());
        return new BankTransaction(
            date,
            direction,
            amount,
            currency,
            accountNumber,
            counterparty,
            "",
            firstText(element, "partnerAccount", "recipientAccount", "payerAccount", "beneficiaryAccount", "senderAccount"),
            description,
            reference,
            element.getTextContent()
        );
    }

    private String extractSoapFault(String body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            return firstText(document.getDocumentElement(), "faultstring", "FaultString", "Reason", "Text");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasChild(Element element, String... names) {
        return !firstText(element, names).isBlank();
    }

    private String firstText(Element element, String... names) {
        List<String> wanted = IntStream.range(0, names.length)
            .mapToObj(index -> names[index].toLowerCase(Locale.ROOT))
            .toList();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement
                && wanted.contains(localName(childElement).toLowerCase(Locale.ROOT))) {
                return childElement.getTextContent() == null ? "" : childElement.getTextContent().trim();
            }
        }
        return "";
    }

    private String localName(Element element) {
        return Objects.requireNonNullElse(element.getLocalName(), element.getNodeName());
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = value.trim().replace(",", "");
        try {
            return new BigDecimal(normalized).abs();
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
            return LocalDate.parse(trimmed.substring(0, Math.min(trimmed.length(), 10)));
        } catch (Exception ignored) {
            // Try the less common SOAP date formats below.
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String parseDirection(String debitCredit) {
        String value = debitCredit == null ? "" : debitCredit.trim().toLowerCase(Locale.ROOT);
        if (value.equals("1") || value.equals("credit") || value.equals("cr") || value.equals("c")) {
            return "CREDIT";
        }
        if (value.equals("0") || value.equals("debit") || value.equals("dr") || value.equals("d")) {
            return "DEBIT";
        }
        return "";
    }

    private void validateConfig(CamoraProperties.TbcDbi config) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("TBC DBI integration is disabled. Set CAMORA_TBC_DBI_ENABLED=true.");
        }
        require(config.getEndpoint(), "CAMORA_TBC_DBI_ENDPOINT");
        require(config.getUsername(), "CAMORA_TBC_DBI_USERNAME");
        require(config.getPassword(), "CAMORA_TBC_DBI_PASSWORD");
        if (isBlank(config.getCertificatePath()) && isBlank(config.getCertificateBase64())) {
            throw new IllegalStateException("CAMORA_TBC_DBI_CERTIFICATE_PATH or CAMORA_TBC_DBI_CERTIFICATE_BASE64 is required");
        }
        require(config.getCertificatePassword(), "CAMORA_TBC_DBI_CERTIFICATE_PASSWORD");
        require(config.getAccountNumber(), "CAMORA_TBC_DBI_ACCOUNT_NUMBER");
        require(config.getCurrency(), "CAMORA_TBC_DBI_CURRENCY");
    }

    private void require(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalStateException(name + " is required");
        }
    }

    private java.io.InputStream certificateInput(CamoraProperties.TbcDbi config) throws Exception {
        if (!isBlank(config.getCertificateBase64())) {
            byte[] decoded = Base64.getDecoder().decode(config.getCertificateBase64().trim());
            return new ByteArrayInputStream(decoded);
        }
        return Files.newInputStream(Path.of(config.getCertificatePath()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String xml(String value) {
        return value == null ? "" : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
