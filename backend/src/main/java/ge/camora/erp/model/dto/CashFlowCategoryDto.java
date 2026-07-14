package ge.camora.erp.model.dto;

/** A category in the editable tree (with resolved Georgian section/direction labels). */
public record CashFlowCategoryDto(
    String id,
    String code,
    String sectionKey,
    String sectionNameKa,
    String direction,
    String directionNameKa,
    String nameKa,
    String parentId,
    boolean hasChildren,
    int order,
    boolean builtin
) {
}
