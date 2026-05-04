package ge.camora.erp.model.dto;

public record BankTransactionMappingRequest(
    String direction,
    String matchText,
    String category
) {
}
