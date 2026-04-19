package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record SalesAnalysisProductOption(
    String productKey,
    String productName,
    BigDecimal grossRevenueTotal
) {
}
