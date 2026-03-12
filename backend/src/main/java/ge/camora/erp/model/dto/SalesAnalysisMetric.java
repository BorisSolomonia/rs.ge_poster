package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record SalesAnalysisMetric(
    BigDecimal current,
    BigDecimal previous,
    BigDecimal delta,
    BigDecimal deltaPercent
) {
}
