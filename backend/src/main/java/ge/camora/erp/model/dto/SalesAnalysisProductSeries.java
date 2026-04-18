package ge.camora.erp.model.dto;

import java.util.List;

public record SalesAnalysisProductSeries(
    String productName,
    List<SalesAnalysisProductPoint> periods
) {
}
