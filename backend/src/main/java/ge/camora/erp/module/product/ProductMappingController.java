package ge.camora.erp.module.product;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.ProductMapping;
import ge.camora.erp.model.dto.*;
import ge.camora.erp.store.ConfigStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@RestController
@RequestMapping("${camora.api-prefix}/product-mappings")
public class ProductMappingController {

    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public ProductMappingController(ConfigStore configStore, CamoraProperties properties) {
        this.configStore = configStore;
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductMapping>>> getAll(
        @RequestParam String supplierMappingId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getProductMappings(supplierMappingId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductMapping>> create(
        @Valid @RequestBody CreateProductMappingRequest request
    ) {
        ProductMapping mapping = new ProductMapping();
        mapping.setSupplierMappingId(request.getSupplierMappingId());
        mapping.setRsgeProductPattern(request.getRsgeProductPattern());
        mapping.setPosterProductPattern(request.getPosterProductPattern());
        mapping.setRegex(request.isRegex());
        mapping.setExcluded(request.isExcluded());
        mapping.setPriority(request.getPriority());
        return ResponseEntity.ok(ApiResponse.ok(configStore.addProductMapping(mapping)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductMapping>> update(
        @PathVariable String id,
        @RequestBody UpdateProductMappingRequest request
    ) {
        ProductMapping existing = configStore.getAllSupplierMappings().stream()
            .flatMap(sm -> configStore.getProductMappings(sm.getId()).stream())
            .filter(p -> p.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                properties.getMessages().getProductMappingNotFoundPrefix() + id
            ));

        ProductMapping updated = new ProductMapping();
        updated.setSupplierMappingId(existing.getSupplierMappingId());
        updated.setRsgeProductPattern(request.getRsgeProductPattern() != null
            ? request.getRsgeProductPattern() : existing.getRsgeProductPattern());
        updated.setPosterProductPattern(request.getPosterProductPattern() != null
            ? request.getPosterProductPattern() : existing.getPosterProductPattern());
        updated.setRegex(request.getIsRegex() != null ? request.getIsRegex() : existing.isRegex());
        updated.setExcluded(request.getIsExcluded() != null ? request.getIsExcluded() : existing.isExcluded());
        updated.setPriority(request.getPriority() != null ? request.getPriority() : existing.getPriority());
        return ResponseEntity.ok(ApiResponse.ok(configStore.updateProductMapping(id, updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        configStore.deleteProductMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/test-match")
    public ResponseEntity<ApiResponse<PatternTestResult>> testMatch(
        @Valid @RequestBody PatternTestRequest request
    ) {
        boolean matches;
        String error = null;
        try {
            if (request.isRegex()) {
                matches = Pattern.compile(request.getPattern())
                    .matcher(request.getTestValue()).find();
            } else {
                matches = request.getTestValue().toLowerCase()
                    .contains(request.getPattern().toLowerCase());
            }
        } catch (PatternSyntaxException e) {
            matches = false;
            error = "Invalid regex: " + e.getMessage();
        }

        return ResponseEntity.ok(ApiResponse.ok(new PatternTestResult(
            matches, request.getPattern(), request.getTestValue(), request.isRegex(), error)));
    }
}
