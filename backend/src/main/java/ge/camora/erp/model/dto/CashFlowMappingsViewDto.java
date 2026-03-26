package ge.camora.erp.model.dto;

import java.util.List;

public record CashFlowMappingsViewDto(
    List<String> canonicalCategories,
    List<CashFlowCategoryMappingView> mappings,
    List<CashFlowUnmappedCategoryDto> unmappedCategories
) {
}
