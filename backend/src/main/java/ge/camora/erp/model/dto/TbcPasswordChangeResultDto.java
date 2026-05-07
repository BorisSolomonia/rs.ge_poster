package ge.camora.erp.model.dto;

public record TbcPasswordChangeResultDto(
    String message,
    String code,
    boolean secretManagerUpdateRequired
) {
}
