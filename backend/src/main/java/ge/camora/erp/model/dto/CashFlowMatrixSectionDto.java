package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CashFlowMatrixSectionDto(
    String sectionKey,
    String nameKa,
    BigDecimal total,
    Map<String, BigDecimal> monthly,
    List<CashFlowMatrixDirectionDto> directions
) {
}
