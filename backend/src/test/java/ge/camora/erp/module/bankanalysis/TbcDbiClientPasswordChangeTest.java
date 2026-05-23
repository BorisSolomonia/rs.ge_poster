package ge.camora.erp.module.bankanalysis;

import ge.camora.erp.config.CamoraProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TbcDbiClientPasswordChangeTest {

    @Test
    void changePasswordRejectsMissingOtpBeforeCallingTbc() {
        TbcDbiClient client = new TbcDbiClient(validProperties());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> client.changePassword("", "NewPass1!", null)
        );

        assertEquals("TBC Digipass/Nonce code is required for password change.", exception.getMessage());
    }

    private CamoraProperties validProperties() {
        CamoraProperties properties = new CamoraProperties();
        CamoraProperties.TbcDbi tbcDbi = properties.getTbcDbi();
        tbcDbi.setEnabled(true);
        tbcDbi.setEndpoint("https://secdbi.tbconline.ge/dbi/dbiService");
        tbcDbi.setUsername("test-user");
        tbcDbi.setPassword("TempPass1!");
        tbcDbi.setCertificateBase64("placeholder");
        tbcDbi.setCertificatePassword("cert-pass");
        tbcDbi.setAccountNumber("GE00TB0000000000000000");
        tbcDbi.setCurrency("GEL");
        tbcDbi.setPageSize(700);
        tbcDbi.setTimeoutSeconds(120);
        return properties;
    }
}
