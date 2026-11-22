package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ExpressionNormalizationTest {
    @Test void independentlyCalculatedTpmAndLibraryNormalization() {
        assertArrayEquals(new double[]{500000,500000,0},ExpressionNormalization.tpm(new double[]{10,20,0},new long[]{1000,2000,500}),1e-9);
        assertArrayEquals(new double[]{10,10,0},ExpressionNormalization.perKilobaseMillion(new double[]{10,20,0},new long[]{1000,2000,500},1_000_000),1e-12);
        assertArrayEquals(new double[]{20,20,0},ExpressionNormalization.perKilobaseMillion(new double[]{10,20,0},new long[]{1000,2000,500},500_000),1e-12);
    }
    @Test void zeroTpmDenominatorAndInvalidCountsLengthsOrLibraryFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->ExpressionNormalization.tpm(new double[]{0},new long[]{1}));
        assertThrows(IllegalArgumentException.class,()->ExpressionNormalization.tpm(new double[]{Double.NaN},new long[]{1}));
        assertThrows(IllegalArgumentException.class,()->ExpressionNormalization.tpm(new double[]{1},new long[]{0}));
        assertThrows(IllegalArgumentException.class,()->ExpressionNormalization.perKilobaseMillion(new double[]{1},new long[]{1},0));
    }
}
