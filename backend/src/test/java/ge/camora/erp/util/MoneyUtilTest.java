package ge.camora.erp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyUtilTest {

    @Test
    void parsesSignedAmountsWithoutDroppingNegativeSign() {
        assertThat(MoneyUtil.parse("-16000")).isEqualByComparingTo("-16000.00");
        assertThat(MoneyUtil.parse("\u221216000.50")).isEqualByComparingTo("-16000.50");
        assertThat(MoneyUtil.parse("(16,000.25)")).isEqualByComparingTo("-16000.25");
    }

    @Test
    void parsesDecimalCommaAndThousandsSeparators() {
        assertThat(MoneyUtil.parse("16 008,48")).isEqualByComparingTo("16008.48");
        assertThat(MoneyUtil.parse("16,008.48")).isEqualByComparingTo("16008.48");
        assertThat(MoneyUtil.parse("16.008,48")).isEqualByComparingTo("16008.48");
        assertThat(MoneyUtil.parse("0.001")).isEqualByComparingTo("0.00");
        assertThat(MoneyUtil.parse("16,000")).isEqualByComparingTo("16000.00");
    }
}
