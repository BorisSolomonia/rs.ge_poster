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
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SpreadsheetAmountParser {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetAmountParser.class);

    public Map<LocalDate, BigDecimal> parse(InputStream inputStream, CamoraProperties.AmountSheet config, String label) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        int skipped = 0;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(config.getDateFormat());

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(config.getSheetIndex());
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                if (row.getRowNum() < config.getSkipHeaderRows()) {
                    continue;
                }
                if (row.getLastCellNum() < config.getMinColumns()) {
                    skipped++;
                    continue;
                }

                try {
                    LocalDate date = parseDate(row.getCell(config.getColumns().getDate()), formatter, dateFormatter);
                    BigDecimal amount = parseAmount(row.getCell(config.getColumns().getAmount()), formatter);
                    if (date == null || amount == null) {
                        skipped++;
                        continue;
                    }
                    totals.merge(date, amount, BigDecimal::add);
                } catch (Exception e) {
                    skipped++;
                    log.debug("{} row {} skipped: {}", label, row.getRowNum(), e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new FileParsingException("Failed to parse " + label + " workbook: " + e.getMessage(), e);
        }

        totals.replaceAll((date, amount) -> MoneyUtil.round(amount));
        log.info("{} workbook parsed: {} dates, {} skipped rows", label, totals.size(), skipped);
        return totals;
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
}
