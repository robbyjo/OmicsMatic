package org.jlinalg.sequence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.sequence.RsemPrintedNumber.Format.*;

class RsemPrintedNumberTest {
    @Test void fixedZeroRetainsItsActualDisplayUncertainty() {
        var value = RsemPrintedNumber.parse("0.00", FIXED_2);
        assertEquals("0.00", value.text()); assertEquals(0, value.value());
        assertTrue(value.contains(0.004999)); assertFalse(value.contains(0.005001));
    }
    @Test void generalZeroIsNotGivenInventedPositiveMass() {
        var value = RsemPrintedNumber.parse("0", GENERAL_10);
        assertEquals(0, value.lower()); assertEquals(0, value.upper()); assertFalse(value.contains(Double.MIN_VALUE));
    }
    @Test void suppressedTrailingZerosStillHaveTheDeclaredPrintPrecision() {
        var value = RsemPrintedNumber.parse("1", GENERAL_10);
        assertTrue(value.contains(0.99999999996)); assertTrue(value.contains(1.0000000004));
        assertFalse(value.contains(1.000000001));
    }
    @Test void negativeZeroIsPreservedWithoutNegativeProbabilityMass() {
        var value = RsemPrintedNumber.parse("-0.00", FIXED_2);
        assertEquals("-0.00", value.text()); assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(value.value()));
        assertEquals(0, value.lower());
    }
    @Test void intervalScientificNotationUsesSixSignificantDigits() {
        var value = RsemPrintedNumber.parse("1.19117e+06", GENERAL_6);
        assertTrue(value.contains(1191174.0)); assertFalse(value.contains(1191176.0));
    }
    @Test void subnormalSourceValuesKeepOnlyFeasibleBinary64Points() {
        var value = RsemPrintedNumber.parse("4.94066e-324", GENERAL_6);
        assertTrue(value.contains(Double.MIN_VALUE)); assertFalse(value.contains(0)); assertFalse(value.contains(Math.nextUp(Double.MIN_VALUE)));
    }
    @Test void impossibleOddLargeFixedDecimalsDoNotSilentlyRoundToAnotherCount() {
        assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse("9007199254740993.00", FIXED_2));
    }
    @Test void malformedNonfiniteAndUnderflowedValuesFail() {
        for (String text : new String[]{"NaN", "Infinity", "-1", "1e-400", "1e400", " 1", "1 ", "1,2", "1;2"})
            assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse(text, GENERAL_10), text);
    }
    @Test void integralAndFixedFormatsAreNotInterchangeable() {
        assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse("1.0", INTEGER));
        assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse("1e0", INTEGER));
        assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse("1.000", FIXED_2));
        assertEquals(1000, RsemPrintedNumber.parse("1000", INTEGER).value());
    }
    @Test void extraSignificantDigitsAreNotAcceptedAsOriginalModelPrecision() {
        assertThrows(IllegalArgumentException.class, () -> RsemPrintedNumber.parse("0.12345678901", GENERAL_10));
        assertEquals("0.100000000000", RsemPrintedNumber.parse("0.100000000000", GENERAL_10).text());
    }
    @Test void incompatibleUncertaintyIntervalsRemainSeparated() {
        var one = RsemPrintedNumber.parse("1.00", FIXED_2);
        var two = RsemPrintedNumber.parse("1.02", FIXED_2);
        assertTrue(one.upper() < two.lower()); assertFalse(one.contains(two.value()));
    }
}
