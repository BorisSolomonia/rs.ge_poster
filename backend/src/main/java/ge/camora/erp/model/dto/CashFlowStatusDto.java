package ge.camora.erp.model.dto;

import java.time.LocalDateTime;

public record CashFlowStatusDto(
    boolean bogConfigured,
    boolean tbcConfigured,
    LocalDateTime lastRefreshAt,
    String lastRefreshError
) {
}
