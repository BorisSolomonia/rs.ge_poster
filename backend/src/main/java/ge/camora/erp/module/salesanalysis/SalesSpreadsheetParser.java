package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.module.ingestion.FileParsingException;
import ge.camora.erp.util.MoneyUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class SalesSpreadsheetParser {

    private static final Logger log = LoggerFactory.getLogger(SalesSpreadsheetParser.class);

    private final CamoraProperties.AmountSheet salesConfig;

    public SalesSpreadsheetParser(CamoraProperties properties) {
        this.salesConfig = properties.getParsers().getSales();
    }

    public List<SalesRow> parse(InputStream inputStream) {
        List<SalesRow> rows = new ArrayList<>();
        int skipped = 0;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(salesConfig.getDateFormat());

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(salesConfig.getSheetIndex());
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() < salesConfig.getSkipHeaderRows()) {
                    continue;
                }
                if (row.getLastCellNum() < salesConfig.getMinColumns()) {
                    skipped++;
                    continue;
                }
                try {
                    LocalDate date = parseDate(row.getCell(salesConfig.getColumns().getDate()), formatter, dateFormatter);
                    BigDecimal amount = parseAmount(row.getCell(salesConfig.getColumns().getAmount()), formatter);
                    String product = formatter.formatCellValue(row.getCell(salesConfig.getColumns().getProduct())).trim();
                    if (date == null || amount == null || product.isBlank()) {
                        skipped++;
                        continue;
                    }
                    rows.add(new SalesRow(
                        date,
                        product,
                        amount,
                        parseOptionalAmount(row, salesConfig.getColumns().getQuantity(), formatter),
                        parseOptionalAmount(row, salesConfig.getColumns().getProfit(), formatter)
                    ));
                } catch (Exception e) {
                    skipped++;
                    log.debug("Sales row {} skipped: {}", row.getRowNum(), e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new FileParsingException("Failed to parse Sales workbook: " + e.getMessage(), e);
        }

        log.info("Sales workbook parsed: {} rows, {} skipped", rows.size(), skipped);
        return rows;
    }

    private LocalDate parseDate(Cell cell, DataFormatter formatter, DateTimeFormatter dateFormatter) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
            return dateTime.toLocalDate();
        }
        String raw = formatter.formatCellValue(cell).trim();
        if (raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw, dateFormatter);
    }

    private BigDecimal parseAmount(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            default -> {
                String raw = formatter.formatCellValue(cell).trim();
                yield raw.isBlank() ? null : MoneyUtil.parse(raw);
            }
        };
    }

    private BigDecimal parseOptionalAmount(Row row, int columnIndex, DataFormatter formatter) {
        if (columnIndex < 0 || row.getLastCellNum() <= columnIndex) {
            return MoneyUtil.ZERO;
        }
        BigDecimal parsed = parseAmount(row.getCell(columnIndex), formatter);
        return parsed == null ? MoneyUtil.ZERO : parsed;
    }
}
