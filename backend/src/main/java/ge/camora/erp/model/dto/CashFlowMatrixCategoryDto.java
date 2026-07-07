package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A category row in the matrix. {@code total}/{@code monthly} include the roll-up of
 *  {@code children} (sub-categories); {@code children} is empty for a leaf. */
public record CashFlowMatrixCategoryDto(
    String categoryId,
    String nameKa,
    BigDecimal total,
    Map<String, BigDecimal> monthly,
    int transactionCount,
    List<CashFlowMatrixCategoryDto> children
) {
}
