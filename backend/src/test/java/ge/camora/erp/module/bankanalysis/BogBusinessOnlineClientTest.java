package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BogBusinessOnlineClientTest {

    @Test
    void disabledIntegrationReturnsStableErrorCode() {
        CamoraProperties properties = new CamoraProperties();
        properties.getBogApi().setEnabled(false);
        BogBusinessOnlineClient client = new BogBusinessOnlineClient(properties, new ObjectMapper());

        assertThatThrownBy(() -> client.getStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)))
            .isInstanceOf(BogApiException.class)
            .hasFieldOrPropertyWithValue("code", BogApiException.DISABLED);
    }
}
