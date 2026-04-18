package ge.camora.erp.model.dto;

import java.math.BigDecimal;

public record CashFlowAnalysisDeltaDto(
    BigDecimal amount,
    BigDecimal percent
) {
}
