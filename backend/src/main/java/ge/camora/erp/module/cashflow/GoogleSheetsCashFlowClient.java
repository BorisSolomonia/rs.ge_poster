package ge.camora.erp.module.cashflow;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import ge.camora.erp.config.CamoraProperties;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GoogleSheetsCashFlowClient {

    private static final String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
        } catch (GoogleJsonResponseException e) {
            if (isUnsupportedDocument(e)) {
                return fetchLedgerRowsFromDriveExport();
            }
            throw new IllegalStateException("Failed to fetch Google Sheets cash flow data: " + e.getMessage(), e);
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

    private Drive buildDriveService() throws IOException, GeneralSecurityException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(loadCredentials());
        return new Drive.Builder(transport, GsonFactory.getDefaultInstance(), initializer)
            .setApplicationName("Camora ERP Cash Flow")
            .build();
    }

    private GoogleCredentials loadCredentials() throws IOException {
        CamoraProperties.CashFlow config = properties.getCashFlow();
        try (InputStream stream = openCredentialsStream(config)) {
            return GoogleCredentials.fromStream(stream)
                .createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY, DriveScopes.DRIVE_READONLY));
        }
    }

    private InputStream openCredentialsStream(CamoraProperties.CashFlow config) throws IOException {
        if (config.getServiceAccountJson() != null && !config.getServiceAccountJson().isBlank()) {
            return new ByteArrayInputStream(config.getServiceAccountJson().getBytes(StandardCharsets.UTF_8));
        }
        return Files.newInputStream(Path.of(config.getServiceAccountPath()));
    }

    private List<List<Object>> fetchLedgerRowsFromDriveExport() throws IOException, GeneralSecurityException {
        CamoraProperties.CashFlow config = properties.getCashFlow();
        try (InputStream inputStream = buildDriveService()
            .files()
            .export(config.getSheetId(), XLSX_MIME_TYPE)
            .executeMediaAsInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            String sheetName = extractSheetName(config.getRange(), config.getSheetName());
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalStateException("Cash flow sheet not found in exported workbook: " + sheetName);
            }
            CellRange range = CellRange.parse(config.getRange());
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<Object>> rows = new ArrayList<>();
            int startRowIndex = range.startRowIndex();
            int endColumnIndex = range.endColumnIndex();
            for (int rowIndex = startRowIndex; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    rows.add(List.of());
                    continue;
                }
                List<Object> values = new ArrayList<>();
                boolean hasValue = false;
                for (int columnIndex = range.startColumnIndex(); columnIndex <= endColumnIndex; columnIndex++) {
                    String formatted = formatter.formatCellValue(row.getCell(columnIndex));
                    if (!formatted.isBlank()) {
                        hasValue = true;
                    }
                    values.add(formatted);
                }
                if (hasValue) {
                    rows.add(values);
                } else {
                    rows.add(List.of());
                }
            }
            return rows;
        }
    }

    private boolean isUnsupportedDocument(GoogleJsonResponseException exception) {
        return exception.getStatusCode() == 400
            && exception.getDetails() != null
            && "FAILED_PRECONDITION".equalsIgnoreCase(exception.getDetails().getStatus());
    }

    private String extractSheetName(String range, String configuredSheetName) {
        if (range != null) {
            int bangIndex = range.indexOf('!');
            if (bangIndex > 0) {
                return range.substring(0, bangIndex);
            }
        }
        return configuredSheetName;
    }

    private record CellRange(int startRowIndex, int startColumnIndex, int endColumnIndex) {
        private static CellRange parse(String range) {
            if (range == null || range.isBlank()) {
                return new CellRange(0, 0, 18);
            }
            String reference = range.contains("!") ? range.substring(range.indexOf('!') + 1) : range;
            String[] parts = reference.split(":");
            String start = parts[0];
            String end = parts.length > 1 ? parts[1] : parts[0];
            return new CellRange(parseRowIndex(start), parseColumnIndex(start), parseColumnIndex(end));
        }

        private static int parseRowIndex(String reference) {
            String digits = reference.replaceAll("[^0-9]", "");
            return digits.isBlank() ? 0 : Integer.parseInt(digits) - 1;
        }

        private static int parseColumnIndex(String reference) {
            String letters = reference.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            if (letters.isBlank()) {
                return 0;
            }
            int result = 0;
            for (int i = 0; i < letters.length(); i++) {
                result = result * 26 + (letters.charAt(i) - 'A' + 1);
            }
            return result - 1;
        }
    }
}
