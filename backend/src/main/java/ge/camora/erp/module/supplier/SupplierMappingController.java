package ge.camora.erp.module.supplier;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.StandaloneSupplier;
import ge.camora.erp.model.config.SupplierMapping;
import ge.camora.erp.model.dto.*;
import ge.camora.erp.store.ConfigStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/supplier-mappings")
public class SupplierMappingController {

    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public SupplierMappingController(ConfigStore configStore, CamoraProperties properties) {
        this.configStore = configStore;
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplierMapping>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getAllSupplierMappings()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SupplierMappingStatusView>> getStatus() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getStatusView()));
    }

    @GetMapping("/unmapped")
    public ResponseEntity<ApiResponse<List<StandaloneSupplier>>> getUnmapped() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getUnmappedSuppliers()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierMapping>> create(
        @Valid @RequestBody CreateSupplierMappingRequest request
    ) {
        SupplierMapping mapping = new SupplierMapping();
        mapping.setPosterAlias(request.getPosterAlias());
        mapping.setRsgeRawValue(request.getRsgeRawValue());
        return ResponseEntity.ok(ApiResponse.ok(configStore.addSupplierMapping(mapping)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierMapping>> update(
        @PathVariable String id,
        @RequestBody UpdateSupplierMappingRequest request
    ) {
        SupplierMapping existing = configStore.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(
                properties.getMessages().getGenericNotFoundPrefix() + id
            ));

        SupplierMapping updated = new SupplierMapping();
        updated.setPosterAlias(request.getPosterAlias() != null
            ? request.getPosterAlias() : existing.getPosterAlias());
        updated.setRsgeRawValue(request.getRsgeRawValue() != null
            ? request.getRsgeRawValue() : existing.getRsgeRawValue());
        updated.setPosterExcluded(request.getPosterExcluded() != null
            ? request.getPosterExcluded() : existing.isPosterExcluded());
        updated.setRsgeExcluded(request.getRsgeExcluded() != null
            ? request.getRsgeExcluded() : existing.isRsgeExcluded());
        return ResponseEntity.ok(ApiResponse.ok(configStore.updateSupplierMapping(id, updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        configStore.deleteSupplierMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}/exclude-poster")
    public ResponseEntity<ApiResponse<SupplierMapping>> togglePosterExcluded(@PathVariable String id) {
        SupplierMapping existing = configStore.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(
                properties.getMessages().getGenericNotFoundPrefix() + id
            ));
        existing.setPosterExcluded(!existing.isPosterExcluded());
        return ResponseEntity.ok(ApiResponse.ok(configStore.updateSupplierMapping(id, existing)));
    }

    @PatchMapping("/{id}/exclude-rsge")
    public ResponseEntity<ApiResponse<SupplierMapping>> toggleRsgeExcluded(@PathVariable String id) {
        SupplierMapping existing = configStore.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(
                properties.getMessages().getGenericNotFoundPrefix() + id
            ));
        existing.setRsgeExcluded(!existing.isRsgeExcluded());
        return ResponseEntity.ok(ApiResponse.ok(configStore.updateSupplierMapping(id, existing)));
    }

    @PatchMapping("/standalone/exclude")
    public ResponseEntity<ApiResponse<Void>> excludeStandalone(
        @Valid @RequestBody StandaloneExcludeRequest request
    ) {
        configStore.markStandaloneExcluded(request.getPlatform(), request.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
