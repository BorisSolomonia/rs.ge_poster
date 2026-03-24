package ge.camora.erp.model.dto;

import java.util.List;

public record CashFlowWarningsResponseDto(
    String month,
    int total,
    List<CashFlowWarningDto> warnings
) {
}
