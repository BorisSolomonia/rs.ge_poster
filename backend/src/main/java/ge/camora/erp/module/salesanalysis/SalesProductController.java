package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.model.config.SalesProductExclusion;
import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.SalesProductExclusionRequest;
import ge.camora.erp.store.ConfigStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/sales-products")
public class SalesProductController {

    private final ConfigStore configStore;

    public SalesProductController(ConfigStore configStore) {
        this.configStore = configStore;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SalesProductExclusion>>> listAll(@RequestParam(required = false) String search) {
        List<SalesProductExclusion> all = configStore.getSalesProductExclusions();
        if (search == null || search.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(all));
        }
        String normalized = ConfigStore.normalizeSalesKey(search);
        List<SalesProductExclusion> filtered = all.stream()
            .filter(item -> item.getNormalizedName().contains(normalized))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(filtered));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesProductExclusion>> create(@RequestBody SalesProductExclusionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            configStore.upsertSalesProduct(request.getDisplayName(), Boolean.TRUE.equals(request.getExcluded()), "manual")
        ));
    }

    @PatchMapping("/exclude")
    public ResponseEntity<ApiResponse<Void>> updateExcluded(@RequestBody SalesProductExclusionRequest request) {
        configStore.setSalesProductExcluded(
            ConfigStore.normalizeSalesKey(request.getDisplayName()),
            Boolean.TRUE.equals(request.getExcluded())
        );
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
