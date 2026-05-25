package ge.camora.erp.module.bankanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void debitStatementUsesBeneficiaryInnAsCounterpartyInn() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.BogApi config = properties.getBogApi();
        config.setAccountNumber("GE00BG0000000000000000GEL");
        config.setCurrency("GEL");
        BogBusinessOnlineClient client = new BogBusinessOnlineClient(properties, objectMapper);
        String json = """
            {
              "Records": [
                {
                  "EntryAmountDebit": "250.00",
                  "EntryDate": "2026-05-20",
                  "DocumentSourceCurrency": "GEL",
                  "EntryAccountNumber": "GE00BG0000000000000000GEL",
                  "BeneficiaryDetails": {
                    "Name": "Supplier X",
                    "Inn": "123456789",
                    "AccountNumber": "GE00BG0000000000000001GEL"
                  },
                  "EntryComment": "Supplier payment",
                  "EntryDocumentNumber": "BOG-REF"
                }
              ]
            }
            """;

        var transactions = client.parseRecords(objectMapper.readTree(json), config);

        assertThat(transactions).hasSize(1);
        BankTransaction tx = transactions.get(0);
        assertThat(tx.direction()).isEqualTo("DEBIT");
        assertThat(tx.counterparty()).isEqualTo("Supplier X");
        assertThat(tx.counterpartyInn()).isEqualTo("123456789");
        assertThat(tx.counterpartyAccount()).isEqualTo("GE00BG0000000000000001GEL");
    }
}
