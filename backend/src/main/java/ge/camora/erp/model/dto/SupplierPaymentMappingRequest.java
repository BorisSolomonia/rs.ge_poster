package ge.camora.erp.model.dto;

public record SupplierPaymentMappingRequest(
    String provider,
    String matchText,
    String supplierKey,
    String supplierTin,
    String supplierName
) {
}
