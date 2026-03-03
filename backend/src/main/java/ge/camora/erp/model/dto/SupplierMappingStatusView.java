package ge.camora.erp.model.dto;

import ge.camora.erp.model.config.StandaloneSupplier;
import ge.camora.erp.model.config.SupplierMapping;

import java.util.List;

public record SupplierMappingStatusView(
    List<SupplierMapping> mapped,
    List<StandaloneSupplier> unmappedPoster,
    List<StandaloneSupplier> unmappedRsge
) {}
