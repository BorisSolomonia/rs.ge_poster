package ge.camora.erp.model.dto;

public record TbcPasswordChangeRequest(
    String otp,
    String newPassword,
    String currentPasswordOverride
) {
}
