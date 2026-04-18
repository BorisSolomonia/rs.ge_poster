package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesAnalysisProductPoint(
    String key,
    String dateFrom,
    String dateTo,
    BigDecimal grossRevenue,
    BigDecimal quantity,
    BigDecimal profit,
    BigDecimal profitPercentage,
    List<String> events
) {
}
