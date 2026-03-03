package ge.camora.erp.model.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PosterRecord(
    Integer documentNumber,
    String supplierAlias,
    String productsRaw,
    BigDecimal totalPrice,
    LocalDateTime documentDate
) {}
