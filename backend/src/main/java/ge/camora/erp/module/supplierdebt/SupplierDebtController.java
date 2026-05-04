package ge.camora.erp.module.supplierdebt;

import ge.camora.erp.model.config.SupplierPaymentMapping;
import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.SupplierDebtOverviewDto;
import ge.camora.erp.model.dto.SupplierPaymentMappingRequest;
import ge.camora.erp.store.ConfigStore;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/supplier-debts")
public class SupplierDebtController {

    private final SupplierDebtService supplierDebtService;
    private final ConfigStore configStore;

    public SupplierDebtController(SupplierDebtService supplierDebtService, ConfigStore configStore) {
        this.supplierDebtService = supplierDebtService;
        this.configStore = configStore;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SupplierDebtOverviewDto>> overview(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.analyze(dateFrom, dateTo)));
    }

    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<List<SupplierPaymentMapping>>> mappings() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getSupplierPaymentMappings()));
    }

    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<SupplierPaymentMapping>> upsertMapping(
        @RequestBody SupplierPaymentMappingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(configStore.upsertSupplierPaymentMapping(
            request.provider(),
            request.matchText(),
            request.supplierKey(),
            request.supplierTin(),
            request.supplierName(),
            "user"
        )));
    }

    @DeleteMapping("/mappings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable String id) {
        configStore.deleteSupplierPaymentMapping(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
