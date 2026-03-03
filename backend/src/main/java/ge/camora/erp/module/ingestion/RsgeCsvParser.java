package ge.camora.erp.module.ingestion;

import com.opencsv.CSVReader;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.CSVReaderBuilder;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.util.GeorgianDateParser;
import ge.camora.erp.util.MoneyUtil;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class RsgeCsvParser {

    private static final Logger log = LoggerFactory.getLogger(RsgeCsvParser.class);

    private final CamoraProperties.Rsge rsgeProperties;

    public RsgeCsvParser(CamoraProperties properties) {
        this.rsgeProperties = properties.getParsers().getRsge();
    }

    public List<RsgeRecord> parse(InputStream is) {
        List<RsgeRecord> records = new ArrayList<>();
        int rowNum = 0;
        int skipped = 0;

        try (BOMInputStream bomIs = BOMInputStream.builder().setInputStream(is).get();
             InputStreamReader reader = new InputStreamReader(bomIs, StandardCharsets.UTF_8)) {

            RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();
            CSVReader csvReader = new CSVReaderBuilder(reader)
                .withCSVParser(rfc4180Parser)
                .build();

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                rowNum++;
                if (rowNum <= rsgeProperties.getSkipHeaderRows()) {
                    continue;
                }
                if (row.length < rsgeProperties.getMinColumns()) {
                    skipped++;
                    continue;
                }
                String totalRaw = row[rsgeProperties.getColumns().getTotalPrice()].trim();
                if (totalRaw.isEmpty()) {
                    skipped++;
                    continue;
                }
                BigDecimal total = MoneyUtil.parse(totalRaw);
                if (total.compareTo(BigDecimal.ZERO) == 0) {
                    skipped++;
                    continue;
                }

                try {
                    RsgeRecord record = new RsgeRecord(
                        row[rsgeProperties.getColumns().getWaybill()].trim(),
                        row[rsgeProperties.getColumns().getSupplier()].trim().replaceAll("\\s+", " "),
                        row[rsgeProperties.getColumns().getProductName()].trim(),
                        row[rsgeProperties.getColumns().getUnit()].trim(),
                        MoneyUtil.parse(row[rsgeProperties.getColumns().getQuantity()].trim()),
                        MoneyUtil.parse(row[rsgeProperties.getColumns().getUnitPrice()].trim()),
                        total,
                        GeorgianDateParser.parse(
                            row[rsgeProperties.getColumns().getDate()].trim(),
                            rsgeProperties.getDateFormat()
                        )
                    );
                    records.add(record);
                } catch (Exception e) {
                    log.warn("Row {} parse error: {}", rowNum, e.getMessage());
                    skipped++;
                }
            }
        } catch (Exception e) {
            throw new FileParsingException("Failed to parse rs.ge CSV: " + e.getMessage(), e);
        }

        log.info("rs.ge CSV: {} records parsed, {} skipped (total rows: {})",
                 records.size(), skipped, rowNum);
        return records;
    }
}
