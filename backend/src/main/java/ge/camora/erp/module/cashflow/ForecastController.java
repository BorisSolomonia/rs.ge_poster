package ge.camora.erp.module.cashflow;

import ge.camora.erp.model.dto.ApiResponse;
import ge.camora.erp.model.dto.BudgetForecastDto;
import ge.camora.erp.model.dto.BudgetOverrideRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Predictive budgeting endpoints — surfaced as the "Forecast" tab of the cash-flow
 * page. Read the rolling week+month forecast, and set/clear per-cell overrides.
 */
@RestController
@RequestMapping("${camora.api-prefix}/cash-flow/budget")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BudgetForecastDto>> forecast(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return ResponseEntity.ok(ApiResponse.ok(forecastService.forecast(asOf)));
    }

    @PostMapping("/override")
    public ResponseEntity<ApiResponse<Void>> setOverride(@RequestBody BudgetOverrideRequest request) {
        forecastService.setOverride(request.periodType(), request.periodKey(), request.categoryId(), request.amount());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/override")
    public ResponseEntity<ApiResponse<Void>> clearOverride(
        @RequestParam String periodType,
        @RequestParam String periodKey,
        @RequestParam String categoryId
    ) {
        forecastService.clearOverride(periodType, periodKey, categoryId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
