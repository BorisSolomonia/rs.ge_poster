package ge.camora.erp.module.cashflow;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import ge.camora.erp.config.CamoraProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleSheetsCashFlowClient {

    private final CamoraProperties properties;

    public GoogleSheetsCashFlowClient(CamoraProperties properties) {
        this.properties = properties;
    }

    public List<List<Object>> fetchLedgerRows() {
        CamoraProperties.CashFlow config = properties.getCashFlow();
        if (!config.isEnabled()) {
            throw new IllegalStateException("Cash flow sync is disabled");
        }
        if (config.getSheetId() == null || config.getSheetId().isBlank()) {
            throw new IllegalStateException("Cash flow Google Sheet ID is not configured");
        }
        if ((config.getServiceAccountJson() == null || config.getServiceAccountJson().isBlank())
            && (config.getServiceAccountPath() == null || config.getServiceAccountPath().isBlank())) {
            throw new IllegalStateException("Cash flow Google service account credentials are not configured");
        }

        try {
            Sheets sheets = buildSheetsService();
            ValueRange response = sheets.spreadsheets().values()
                .get(config.getSheetId(), config.getRange())
                .execute();
            return response.getValues() == null ? List.of() : response.getValues();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to fetch Google Sheets cash flow data: " + e.getMessage(), e);
        }
    }

    private Sheets buildSheetsService() throws IOException, GeneralSecurityException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(loadCredentials());
        return new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), initializer)
            .setApplicationName("Camora ERP Cash Flow")
            .build();
    }

    private GoogleCredentials loadCredentials() throws IOException {
        CamoraProperties.CashFlow config = properties.getCashFlow();
        try (InputStream stream = openCredentialsStream(config)) {
            return GoogleCredentials.fromStream(stream)
                .createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY));
        }
    }

    private InputStream openCredentialsStream(CamoraProperties.CashFlow config) throws IOException {
        if (config.getServiceAccountJson() != null && !config.getServiceAccountJson().isBlank()) {
            return new ByteArrayInputStream(config.getServiceAccountJson().getBytes(StandardCharsets.UTF_8));
        }
        return Files.newInputStream(Path.of(config.getServiceAccountPath()));
    }
}
