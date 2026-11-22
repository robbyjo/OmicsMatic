package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NegativeBinomialResidualsTest {
    @Test void handCalculatedNbPoissonFloorAndClipping(){
        assertEquals(2.0/Math.sqrt(6),NegativeBinomialResiduals.pearson(4,2,1,0,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY),1e-15);
        assertEquals(Math.sqrt(2),NegativeBinomialResiduals.pearson(4,2,Double.POSITIVE_INFINITY,0,-10,10),1e-15);
        assertEquals(.2,NegativeBinomialResiduals.pearson(4,2,1,100,-10,10),1e-15);assertEquals(.1,NegativeBinomialResiduals.pearson(4,2,1,100,-.1,.1),1e-15);
        assertEquals(0,NegativeBinomialResiduals.pearson(2,2,1,0,-1,1));assertEquals(200,NegativeBinomialResiduals.logLinkMean(new double[]{Math.log(2),Math.log(10)},new double[]{1,2}),1e-12);
    }
    @Test void invalidInputsAndArithmeticFailuresAreExplicit(){
        assertThrows(IllegalArgumentException.class,()->NegativeBinomialResiduals.pearson(1,0,1,0,-1,1));assertThrows(IllegalArgumentException.class,()->NegativeBinomialResiduals.pearson(1,1,0,0,-1,1));assertThrows(IllegalArgumentException.class,()->NegativeBinomialResiduals.pearson(1,1,1,0,1,-1));assertThrows(IllegalArgumentException.class,()->NegativeBinomialResiduals.logLinkMean(new double[]{1000},new double[]{1}));
    }
    private static double number(String value){return value.equals("Inf")?Double.POSITIVE_INFINITY:value.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(value);}
    @Test void actualStoredSctransformV1AndV2ModelsMatchNativeResiduals()throws Exception{
        var lines=Files.readAllLines(Path.of("src/test/resources/single-cell-qc/sctransform-residuals.tsv"));int count=0;
        for(String line:lines.subList(1,lines.size())){
            String[] f=line.split("\t",-1);double actual=NegativeBinomialResiduals.pearson(number(f[3]),number(f[4]),number(f[5]),number(f[6]),number(f[7]),number(f[8]));assertEquals(number(f[9]),actual,2e-11,f[0]+":"+f[1]+":"+f[2]);count++;
        }
        assertEquals(400,count);
    }
}
