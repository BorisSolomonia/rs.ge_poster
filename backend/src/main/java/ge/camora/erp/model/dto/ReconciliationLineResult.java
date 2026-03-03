package ge.camora.erp.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReconciliationLineResult(
    String posterAlias,
    String rsgeOfficialName,
    String rsgeRawValue,
    BigDecimal rsgeTotal,
    BigDecimal posterTotal,
    BigDecimal diff,
    ReconciliationStatus status,
    List<String> rsgeProducts,
    List<String> posterProductsRaw,
    List<String> waybillNumbers,
    List<Integer> posterDocNumbers,
    String correctionAction
) {}
