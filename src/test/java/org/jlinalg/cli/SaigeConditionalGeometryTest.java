package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SaigeConditionalGeometryTest {
    private static SaigeConditionalGeometry.StoredSigma positive() {
        return new SaigeConditionalGeometry.StoredSigma(2,new int[]{0,1,0,1},new int[]{0,0,1,1},new double[]{4,1,1,3});
    }
    @Test void diagonalPcgRecoversIndependentTwoByTwoSolution()throws Exception {
        var result=SaigeConditionalGeometry.pcg(positive(),new double[]{1,2},100,1e-24);
        assertTrue(result.converged());assertEquals(2,result.iterations());assertArrayEquals(new double[]{1.0/11,7.0/11},result.solution(),1e-14);
    }
    @Test void originalAbsoluteSquaredResidualCanStopBeforeFirstUpdate()throws Exception {
        var result=SaigeConditionalGeometry.pcg(positive(),new double[]{.1,0},100,.02);
        assertTrue(result.converged());assertEquals(0,result.iterations());assertArrayEquals(new double[]{0,0},result.solution());assertEquals(.01,result.residualSquared(),1e-16);
    }
    @Test void iterationLimitRemainsUnconverged()throws Exception {
        var result=SaigeConditionalGeometry.pcg(positive(),new double[]{1,2},1,1e-24);
        assertFalse(result.converged());assertEquals(1,result.iterations());assertTrue(result.residualSquared()>1e-24);
    }
    @Test void storedTriangleIsNeverSilentlyReflected()throws Exception {
        var sigma=new SaigeConditionalGeometry.StoredSigma(2,new int[]{0,1,1},new int[]{0,0,1},new double[]{4,1,3});
        assertArrayEquals(new double[]{4,4},sigma.multiply(new double[]{1,1}));
        var result=SaigeConditionalGeometry.pcg(sigma,new double[]{1,2},100,.02);assertArrayEquals(new double[]{4,1,3},sigma.values());assertTrue(Double.isFinite(result.residualSquared()));
    }
    @Test void pseudoinverseRetainsNonsymmetricSourceCovariance()throws Exception {
        var c=SaigeConditionalGeometry.conditioning(new double[]{2,.5,1,3},new double[]{1,2});
        assertEquals(2,c.numericalRank());assertEquals(.5,c.asymmetry());assertArrayEquals(new double[]{3/5.5,-.5/5.5,-1/5.5,2/5.5},c.inverse(),1e-14);
        var result=SaigeConditionalGeometry.schur(1,2,new double[]{.2,.3},c);
        assertEquals(1-1.3/5.5,result.score(),1e-14);assertEquals(2-.21/5.5,result.variance(),1e-14);
    }
    @Test void singularConditioningUsesSourceCompatibleMinimumNormInverse()throws Exception {
        var c=SaigeConditionalGeometry.conditioning(new double[]{1,1,1,1},new double[]{2,2});assertEquals(1,c.numericalRank());assertArrayEquals(new double[]{.25,.25,.25,.25},c.inverse(),1e-14);
        var result=SaigeConditionalGeometry.schur(2,1,new double[]{1,1},c);assertEquals(0,result.score(),1e-14);assertEquals(0,result.variance(),1e-14);
    }
    @Test void sourceAndResultArraysAreDefensivelyCopied()throws Exception {
        int[] rows={0};double[] values={2};var sigma=new SaigeConditionalGeometry.StoredSigma(1,rows,new int[]{0},values);rows[0]=3;values[0]=0;sigma.values()[0]=0;assertArrayEquals(new double[]{2},sigma.diagonal());
        var result=SaigeConditionalGeometry.pcg(sigma,new double[]{2},10,1e-24);result.solution()[0]=999;assertEquals(1,result.solution()[0]);
        double[] covariance={2},scores={1};var c=SaigeConditionalGeometry.conditioning(covariance,scores);covariance[0]=0;scores[0]=999;c.scores()[0]=999;c.inverse()[0]=999;assertEquals(.5,c.inverse()[0]);assertEquals(1,c.scores()[0]);
    }
    @Test void invalidGeometryAndMissingPreconditionerFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->new SaigeConditionalGeometry.StoredSigma(1,new int[]{0,0},new int[]{0,0},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->new SaigeConditionalGeometry.StoredSigma(1,new int[]{0},new int[]{0},new double[]{Double.NaN}));
        var noDiagonal=new SaigeConditionalGeometry.StoredSigma(1,new int[0],new int[0],new double[0]);assertThrows(IOException.class,()->SaigeConditionalGeometry.pcg(noDiagonal,new double[]{1},10,.02));
        assertThrows(IllegalArgumentException.class,()->SaigeConditionalGeometry.pcg(positive(),new double[]{Double.NaN,1},10,.02));
        assertThrows(IllegalArgumentException.class,()->SaigeConditionalGeometry.conditioning(new double[0],new double[0]));
        assertThrows(IllegalArgumentException.class,()->SaigeConditionalGeometry.schur(1,1,new double[]{1},SaigeConditionalGeometry.conditioning(new double[]{1,0,0,1},new double[]{1,1})));
    }
}
