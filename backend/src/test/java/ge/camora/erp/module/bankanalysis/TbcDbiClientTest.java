package ge.camora.erp.module.bankanalysis;

import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TbcDbiClientTest {

    @Test
    void accountMovementsRequestStartsFromFirstPageIndexZero() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.TbcDbi config = properties.getTbcDbi();
        config.setUsername("user");
        config.setAccountNumber("GE00TB0000000000000000GEL");
        config.setCurrency("GEL");
        TbcDbiClient client = new TbcDbiClient(properties);

        String envelope = client.buildAccountMovementsEnvelope(
            config,
            "password",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 2),
            TbcDbiClient.FIRST_PAGE_INDEX,
            700
        );

        assertThat(envelope).contains("<myg:pageIndex>0</myg:pageIndex>");
        assertThat(envelope).contains("<myg:pageSize>700</myg:pageSize>");
    }
}
