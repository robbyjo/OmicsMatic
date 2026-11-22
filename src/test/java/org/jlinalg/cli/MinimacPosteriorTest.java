package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MinimacPosteriorTest {
    private Map<String,String> modern(){return new LinkedHashMap<>(Map.of("GT","1|0","HDS","0.5,0.25","DS","0.75","GP","0.375,0.5,0.125","SD","0.4375"));}
    private Map<String,String> legacy(){return new LinkedHashMap<>(Map.of("GT","1|0","DS","0.750","GP","0.375,0.500,0.125"));}
    private String six(float f){return String.format(Locale.ROOT,"%.6g",f);}
    @Test void completeCurrentPosteriorHasOrderedCopiesAndVariance()throws Exception{
        var c=MinimacPosterior.cell(modern(),2,true);assertEquals(java.util.List.of(1,0),c.calls());assertEquals(.75,c.dosage().value());assertEquals(2,c.haplotypes().size());
    }
    @Test void currentSdIsVarianceRatherThanItsSquareRoot(){
        var m=modern();m.put("SD","0.661438");assertTrue(assertThrows(IOException.class,()->MinimacPosterior.cell(m,2,true)).getMessage().contains("variance"));
    }
    @Test void inconsistentGpDsAndHdsFailWithoutRepair(){
        for(var pair:java.util.List.of(Map.entry("GP","0.375,0.4,0.225"),Map.entry("DS","0.9"),Map.entry("HDS","0.1,0.2"))){
            var m=modern();m.put(pair.getKey(),pair.getValue());assertThrows(IOException.class,()->MinimacPosterior.cell(m,2,true));
        }
    }
    @Test void copyOrderThresholdAndDeclaredPloidyAreEnforced(){
        var m=modern();m.put("GT","0|1");assertThrows(IOException.class,()->MinimacPosterior.cell(m,2,true));
        assertThrows(IOException.class,()->MinimacPosterior.cell(modern(),1,true));m.put("GT","1/0");assertThrows(IOException.class,()->MinimacPosterior.cell(m,2,true));
    }
    @Test void historicalPosteriorCannotRevealOrderedHds()throws Exception{
        var c=MinimacPosterior.cell(legacy(),2,false);assertTrue(c.haplotypes().isEmpty());assertNull(c.modernHds());
        var swapped=legacy();swapped.put("GT","0|1");assertEquals(c.squaredCopies(),MinimacPosterior.cell(swapped,2,false).squaredCopies());
    }
    @Test void historicalGenotypeAndSymmetricMomentsStillConstrainRoundedValues(){
        var m=legacy();m.put("GT","0|0");m.put("DS","1.800");m.put("GP","0.010,0.180,0.810");assertThrows(IOException.class,()->MinimacPosterior.cell(m,2,false));
        var bad=legacy();bad.put("GP","0.100,0.500,0.400");assertThrows(IOException.class,()->MinimacPosterior.cell(bad,2,false));
    }
    @Test void compactLegacyZeroRetainsThreeDecimalUncertaintyButCurrentZeroIsExact()throws Exception{
        assertTrue(MinimacPosterior.number("0",1,false,false).range().upper()>.00049);
        assertEquals(new MinimacPosterior.Range(0,0),MinimacPosterior.number("0",1,true,false).range());
    }
    @Test void sourceFormattingPrecisionAndBinary32RepresentabilityAreChecked(){
        assertThrows(IOException.class,()->MinimacPosterior.number("0.1234567",1,true,false));
        assertThrows(IOException.class,()->MinimacPosterior.number("0.1234",1,false,false));
        assertThrows(IOException.class,()->MinimacPosterior.number("1e-46",1,true,false));
    }
    @Test void nonfiniteOutOfRangeOrOversizedExponentFieldsFail(){
        for(String v:java.util.List.of("NaN","Infinity","-0.1","1.1","1e-99999999","0x1.0p0","")){
            assertThrows(IOException.class,()->MinimacPosterior.number(v,1,true,false));
        }
    }
    @Test void validHaploidCurrentAndHistoricalContractsUseTwoProbabilities()throws Exception{
        var m=Map.of("GT","1","DS","0.75","GP","0.25,0.75","HDS","0.75","SD","0.1875");
        assertEquals(1,MinimacPosterior.cell(m,1,true).calls().size());
        assertTrue(MinimacPosterior.cell(Map.of("GT","1","DS","0.750","GP","0.250,0.750"),1,false).haplotypes().isEmpty());
    }
    @Test void completeConstantCurrentPanelHasUndefinedStableR2AndOriginalZeroConvention()throws Exception{
        var c=new MinimacPosterior.Cohort(true,2);c.add(MinimacPosterior.cell(Map.of("GT","0|0","HDS","0,0","DS","0","GP","1,0,0","SD","0"),2,true));
        assertNull(c.stableR2());assertEquals(new MinimacPosterior.Range(0,0),c.r2());
    }
    @Test void historicalSingleCopyUsesItsOriginalZeroAfAndR2Convention()throws Exception{
        var c=new MinimacPosterior.Cohort(false,200);c.add(MinimacPosterior.cell(Map.of("GT","1","DS","0.750","GP","0.250,0.750"),1,false));
        assertEquals(new MinimacPosterior.Range(0,0),c.af());assertEquals(new MinimacPosterior.Range(0,0),c.r2());
    }
    @Test void currentRoundingBoundsContainIndependentFloatSourceAcrossTemporaryGroups()throws Exception{
        var random=new Random(984632L);
        for(int buffer:new int[]{1,2,7,200}){
            var cohort=new MinimacPosterior.Cohort(true,buffer);float sx=0,xx=0,part=0,partSq=0;int count=0;
            for(int i=0;i<201;i++){
                float x=random.nextFloat(),y=random.nextFloat();
                float ds=x+y,g0=(1-x)*(1-y),g1=x*(1-y)+y*(1-x),g2=x*y,sd=x*(1-x)+y*(1-y);
                var fields=Map.of("GT",(x<.5?"0":"1")+"|"+(y<.5?"0":"1"),"HDS",six(x)+","+six(y),"DS",six(ds),"GP",six(g0)+","+six(g1)+","+six(g2),"SD",six(sd));
                cohort.add(MinimacPosterior.cell(fields,2,true));part+=x;part+=y;partSq+=x*x;partSq+=y*y;
                if(++count==buffer){sx+=part;xx+=partSq;part=partSq=0;count=0;}
            }
            if(count>0){sx+=part;xx+=partSq;}
            double af=(double)sx/402,r2=(Math.max(0,(double)xx-(double)sx*sx/402)/402)/(af*(1-af));
            assertTrue(cohort.af().overlaps(MinimacPosterior.number(six((float)af),1,true,true).range()));
            assertTrue(cohort.r2().overlaps(MinimacPosterior.number(six((float)r2),Integer.MAX_VALUE,true,true).range()));
            assertTrue(cohort.stableR2()>=0&&cohort.stableR2()<=1);
        }
    }
    @Test void gtAndGePostQcUseExactSourceDecimalTextAndExplicitMissingPolicy()throws Exception{
        var r=MinimacPosterior.number("0.8",Integer.MAX_VALUE,true,true);
        assertEquals("EXCLUDE_SOURCE_R2_RULE",MinimacOutputs.qc(new MinimacRecipe.Qc("source-INFO-R2","gt","0.80000000000000000000","error"),r));
        assertEquals("RETAIN_SOURCE_R2_RULE",MinimacOutputs.qc(new MinimacRecipe.Qc("source-INFO-R2","ge","0.8","error"),r));
        assertThrows(IOException.class,()->MinimacOutputs.qc(new MinimacRecipe.Qc("source-INFO-R2","ge","0.8","error"),null));
        assertEquals("EXCLUDE_UNREPORTABLE_R2_WITH_LEDGER",MinimacOutputs.qc(new MinimacRecipe.Qc("source-INFO-R2","ge","0.8","exclude-with-ledger"),null));
    }
}

