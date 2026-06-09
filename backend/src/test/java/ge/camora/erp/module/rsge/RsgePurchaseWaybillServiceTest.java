package ge.camora.erp.module.rsge;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import ge.camora.erp.model.record.RsgeRecord;
import ge.camora.erp.store.ConfigStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RsgePurchaseWaybillServiceTest {

    private final RsgePurchaseWaybillService service = new RsgePurchaseWaybillService(null, new FakeConfigStore());

    @Test
    void excludesOnlyCancelledStatusMinusTwoFromPurchaseTotals() {
        List<RsgeRecord> records = service.mapPurchaseRecords(List.of(
            waybill("WB-OK", "1", "100.00"),
            waybill("WB-PENDING", "-1", "200.00"),
            waybill("WB-CANCELLED", "-2", "300.00")
        ));

        assertThat(records).extracting(RsgeRecord::waybillNumber)
            .containsExactly("WB-OK", "WB-PENDING");
        assertThat(records.stream().map(RsgeRecord::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("300.00");
    }

    @Test
    void rawPurchaseAmountReturnsZeroForCancelledStatusMinusTwo() {
        assertThat(service.extractPurchaseAmount(waybill("WB-CANCELLED", "-2", "300.00")))
            .isEqualByComparingTo("0.00");
        assertThat(service.extractPurchaseAmount(waybill("WB-PENDING", "-1", "200.00")))
            .isEqualByComparingTo("200.00");
    }

    private Map<String, Object> waybill(String id, String status, String amount) {
        return Map.of(
            "ID", id,
            "STATUS", status,
            "SELLER_NAME", "Supplier X",
            "SELLER_TIN", "123456789",
            "CREATE_DATE", "2025-01-10T12:00:00",
            "FULL_AMOUNT", amount
        );
    }

    private static final class FakeConfigStore extends ConfigStore {
        private FakeConfigStore() {
            super(new ObjectMapper(), new CamoraProperties());
        }
    }
}
