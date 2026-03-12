package ge.camora.erp.module.salesanalysis;

import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.config.SalesEvent;
import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.SalesEventRequest;
import ge.camora.erp.store.ConfigStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${camora.api-prefix}/sales-events")
public class SalesEventController {

    private final ConfigStore configStore;
    private final CamoraProperties properties;

    public SalesEventController(ConfigStore configStore, CamoraProperties properties) {
        this.configStore = configStore;
        this.properties = properties;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SalesEvent>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(configStore.getSalesEvents()));
    }

    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> suggest(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.ok(
            configStore.suggestSalesEventNames(
                query,
                properties.getSalesAnalysis().getSuggestionLimit(),
                properties.getSalesAnalysis().getMaxSuggestionDistance()
            )
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesEvent>> upsert(@RequestBody SalesEventRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            configStore.upsertSalesEvent(LocalDate.parse(request.getDate()), request.getName())
        ));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam String date) {
        configStore.deleteSalesEvent(LocalDate.parse(date));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
