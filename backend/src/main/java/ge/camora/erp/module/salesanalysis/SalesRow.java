package ge.camora.erp.module.salesanalysis;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesRow(
    LocalDate date,
    String productName,
    BigDecimal grossRevenue,
    BigDecimal quantity,
    BigDecimal profit
) {
}
