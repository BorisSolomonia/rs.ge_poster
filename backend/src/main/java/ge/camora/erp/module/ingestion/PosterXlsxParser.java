package ge.camora.erp.module.ingestion;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.record.PosterRecord;
import ge.camora.erp.util.MoneyUtil;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class PosterXlsxParser {

    private static final Logger log = LoggerFactory.getLogger(PosterXlsxParser.class);

    private final CamoraProperties.Poster posterProperties;

    public PosterXlsxParser(CamoraProperties properties) {
        this.posterProperties = properties.getParsers().getPoster();
    }

    public List<PosterRecord> parse(InputStream is) {
        List<PosterRecord> records = new ArrayList<>();
        int skipped = 0;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(posterProperties.getDateFormat());

        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(posterProperties.getSheetIndex());
            DataFormatter formatter = new DataFormatter();

            for (Row row : sheet) {
                int rowIdx = row.getRowNum();
                if (rowIdx < posterProperties.getSkipHeaderRows()) continue;

                Cell docCell = row.getCell(posterProperties.getColumns().getDocumentNumber());
                if (docCell == null || isBlankCell(docCell, formatter)) {
                    skipped++;
                    continue;
                }

                try {
                    Integer docNum = (int) getNumericValue(docCell);
                    String supplierAlias = getCellString(row, posterProperties.getColumns().getSupplier(), formatter).trim();
                    String productsRaw   = getCellString(row, posterProperties.getColumns().getProductsRaw(), formatter).trim();
                    BigDecimal total     = getNumericBigDecimal(row, posterProperties.getColumns().getTotalPrice());
                    LocalDateTime date   = parsePosterDate(
                        row,
                        posterProperties.getColumns().getDate(),
                        formatter,
                        dateFormatter
                    );

                    records.add(new PosterRecord(docNum, supplierAlias, productsRaw, total, date));
                } catch (Exception e) {
                    log.warn("Poster row {} parse error: {}", rowIdx, e.getMessage());
                    skipped++;
                }
            }
        } catch (Exception e) {
            throw new FileParsingException("Failed to parse Poster XLSX: " + e.getMessage(), e);
        }

        log.info("Poster XLSX: {} records parsed, {} skipped", records.size(), skipped);
        return records;
    }

    private String getCellString(Row row, int col, DataFormatter formatter) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return formatter.formatCellValue(cell);
    }

    private boolean isBlankCell(Cell cell, DataFormatter formatter) {
        if (cell == null) return true;
        if (cell.getCellType() == CellType.BLANK) return true;
        return formatter.formatCellValue(cell).isBlank();
    }

    private double getNumericValue(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> Double.parseDouble(cell.getStringCellValue().trim());
            default -> throw new IllegalArgumentException("Not numeric: " + cell.getCellType());
        };
    }

    private BigDecimal getNumericBigDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return MoneyUtil.ZERO;
        return BigDecimal.valueOf(getNumericValue(cell)).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime parsePosterDate(
        Row row,
        int col,
        DataFormatter formatter,
        DateTimeFormatter dateFormatter
    ) {
        Cell cell = row.getCell(col);
        if (cell == null) throw new IllegalArgumentException("Date cell is null");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String raw = formatter.formatCellValue(cell).trim();
        return LocalDateTime.parse(raw, dateFormatter);
    }
}
