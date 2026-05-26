package ge.camora.erp.module.supplierdebt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RsgePurchaseLedgerStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void persistsAndDetectsChangedAndMissingRsgeRows() {
        RsgePurchaseLedgerStore store = store();
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        RsgePurchaseFingerprint first = fingerprint("row-1", "hash-1", "WB-1", "100.00");
        RsgePurchaseFingerprint second = fingerprint("row-2", "hash-2", "WB-2", "200.00");

        var initial = store.compareAndSave(from, to, List.of(first, second));
        var changed = store.compareAndSave(
            from,
            to,
            List.of(fingerprint("row-1", "hash-1-changed", "WB-1", "150.00"))
        );

        assertThat(initial.newCount()).isEqualTo(2);
        assertThat(changed.changedCount()).isEqualTo(1);
        assertThat(changed.missingCount()).isEqualTo(1);
        assertThat(changed.hasRisk()).isTrue();
        assertThat(changed.technicalDetails()).contains("CHANGED");
        assertThat(changed.technicalDetails()).contains("MISSING");
    }

    private RsgePurchaseLedgerStore store() {
        CamoraProperties properties = new CamoraProperties();
        properties.setConfigDir(tempDir.toString());
        properties.getConfigFiles().setRsgePurchaseLedger("ledger.json");
        RsgePurchaseLedgerStore store = new RsgePurchaseLedgerStore(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            properties
        );
        store.init();
        return store;
    }

    private RsgePurchaseFingerprint fingerprint(String rowKey, String hash, String waybill, String amount) {
        return new RsgePurchaseFingerprint(
            rowKey,
            hash,
            waybill,
            "123456789",
            "Supplier X",
            LocalDate.of(2025, 1, 10),
            new BigDecimal(amount)
        );
    }
}
