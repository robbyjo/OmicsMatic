/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mrpresso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.jlinalg.mrpresso.MrPressoReferenceAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

final class MrPressoExtendedReferenceTest {
    static Map<String,Map<String,Object>> references()throws Exception {
        var records=new LinkedHashMap<String,Map<String,Object>>();
        try(var source=MrPressoExtendedReferenceTest.class.getResourceAsStream("/org/jlinalg/mrpresso/extended-public-api-reference.zip")){
            assertNotNull(source);
            try(var zip=new ZipInputStream(source)){
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null)if(entry.getName().endsWith("/original.json")){
                    var record=object(GprofilerJson.read(zip.readAllBytes()));assertEquals(true,bool(field(record,"returned")),entry.getName());
                    assertNull(records.put(text(field(record,"case")),record));
                }
            }
        }
        assertEquals(20,records.size());return records;
    }
    static MrPressoOriginal.Result analyze(Map<String,Object> record) {
        var arguments=field(record,"arguments");var input=field(record,"input");var exposures=strings(field(arguments,"BetaExposure"));
        int n=list(object(input).get("row_names")).size(),k=exposures.size();double[][] x=new double[n][k],sx=new double[n][k];var errors=strings(field(arguments,"SdExposure"));
        for(int j=0;j<k;j++){
            double[] beta=column(input,exposures.get(j)),sd=column(input,errors.get(j));
            for(int i=0;i<n;i++){x[i][j]=beta[i];sx[i][j]=sd[i];}
        }
        var data=new MrPressoOriginal.Data(rawStrings(object(input).get("row_names")),exposures,column(input,text(field(arguments,"BetaOutcome"))),x,column(input,text(field(arguments,"SdOutcome"))),sx);
        Object outlier=optional(arguments,"OUTLIERtest"),distortion=optional(arguments,"DISTORTIONtest"),alpha=optional(arguments,"SignifThreshold"),b=optional(arguments,"NbDistribution");
        var options=new MrPressoOriginal.Options(outlier!=null&&bool(outlier),distortion!=null&&bool(distortion),alpha==null?.05:number(alpha),b==null?1000:(int)number(b));
        Object seed=optional(arguments,"seed");String sample=strings(field(record,"rng_kind")).get(2);
        var random=seed==null?new MrPressoRandom(state(record,"rng_before")):new MrPressoRandom((int)number(seed),sample.equals("Rounding")?MrPressoRandom.Sampling.ROUNDING:MrPressoRandom.Sampling.REJECTION);
        var result=MrPressoOriginal.analyze(data,options,random);
        if(seed==null)assertArrayEquals(state(record,"rng_before"),result.rngBefore(),"Continued source state must be used without a new seed");
        assertArrayEquals(state(record,"rng_after"),result.rngAfter(),text(field(record,"case"))+" exact original draw consumption");
        return result;
    }
    private static int[] state(Map<String,Object> record,String key){return values(field(record,key)).stream().mapToInt(v->(int)rawNumber(v)).toArray();}

    @Test void fullFiniteFitsCountsBoundsAndContinuationMatchEighteenExtendedOriginalCalls()throws Exception {
        int compared=0;
        for(var item:references().entrySet()){
            String name=item.getKey();if(name.equals("essentially-perfect-raw")||name.equals("zero-corrected-inference"))continue;
            var record=item.getValue();var actual=analyze(record);compareAnalysis(record,actual);compared++;
        }
        assertEquals(18,compared);
    }
    static void compareAnalysis(Map<String,Object> record,MrPressoOriginal.Result actual) {
        String name=text(field(record,"case"));var result=field(record,"result");var tests=field(result,"MR-PRESSO results");var global=field(tests,"Global Test");
        close(number(field(global,"RSSobs")),actual.globalRss(),name+" RSS");probability(field(global,"Pvalue"),actual.globalProbability(),name+" global P");
        var main=field(result,"Main MR results");int k=strings(field(field(record,"arguments"),"BetaExposure")).size();compareFit(main,0,k,actual.raw(),name+" raw");
        if(actual.corrected()==null)for(String key:List.of("Causal Estimate","Sd","T-stat","P-value")){
            double[] expected=column(main,key);for(int j=k;j<2*k;j++)assertTrue(Double.isNaN(expected[j]),name+" conditional corrected NA");
        }else compareFit(main,k,k,actual.corrected(),name+" corrected");
        var outlier=optional(tests,"Outlier Test");
        if(outlier==null)assertTrue(actual.outliers().isEmpty(),name);
        else{
            var labels=rawStrings(object(outlier).get("row_names"));assertEquals(labels,actual.retainedRowNames(),name+" original literal IDs");
            double[] residuals=column(outlier,"RSSobs");var p=values(columnObject(outlier,"Pvalue"));assertEquals(labels.size(),actual.outliers().size(),name);
            for(int i=0;i<labels.size();i++){
                var row=actual.outliers().get(i);assertEquals(labels.get(i),row.rowName());close(residuals[i],row.squaredResidual(),name+" residual "+i);
                probabilityElement(p.get(i),row.probability(),name+" outlier P "+i);
            }
        }
        var distortion=optional(tests,"Distortion Test");
        if(distortion==null)assertNull(actual.distortion(),name);
        else{
            var indices=field(distortion,"Outliers Indices");assertEquals("integer",object(indices).get("kind"),name);
            assertArrayEquals(values(indices).stream().mapToInt(v->(int)rawNumber(v)).toArray(),actual.distortion().outlierOrdinals(),name);
            var expected=numbers(field(distortion,"Distortion Coefficient"));var found=actual.distortion().coefficientPercent();assertEquals(expected.length,found.length);
            for(int j=0;j<expected.length;j++)close(expected[j],found[j],name+" distortion "+j);
            probability(field(distortion,"Pvalue"),actual.distortion().probability(),name+" distortion P");
        }
    }
    @Test void originalOmittedDefaultsAreActuallyAbsentAndUseTheSavedContinuationState()throws Exception {
        for(String name:List.of("omitted-all-defaults-clean","omitted-all-defaults-outlier")){
            var record=references().get(name);var arguments=field(record,"arguments");
            for(String key:List.of("OUTLIERtest","DISTORTIONtest","SignifThreshold","NbDistribution","seed"))assertNull(optional(arguments,key));
            var result=analyze(record);assertEquals(1000,result.globalProbability().distributions());assertNull(result.corrected());assertTrue(result.outliers().isEmpty());
        }
    }
    @Test void essentiallyPerfectOriginalFitIsRetainedWithoutClaimingFiniteInferenceAgreement()throws Exception {
        var record=references().get("essentially-perfect-raw");var actual=analyze(record);var source=field(field(record,"result"),"Main MR results");
        assertTrue(actual.raw().essentiallyPerfect());close(column(source,"Causal Estimate")[0],actual.raw().beta()[0],"perfect-fit beta");
        assertTrue(actual.globalRss()>=0&&Double.isFinite(actual.globalRss()));assertNull(actual.corrected());
        // The source and native QR roundoff yields unstable perfect-fit t/SE/P.
        // Those fields are deliberately not claimed as an original comparison.
        assertTrue(actual.warnings().stream().anyMatch(x->x.startsWith("NATIVE_ESSENTIALLY_PERFECT_FIT_UNREPORTABLE")));
    }
    @Test void zeroCorrectedOriginalFitKeepsZeroAndUndefinedInference()throws Exception {
        var record=references().get("zero-corrected-inference");var actual=analyze(record);var source=field(field(record,"result"),"Main MR results");
        assertNotNull(actual.corrected());compareFit(source,1,1,actual.corrected(),"zero-corrected source fields");
        assertTrue(actual.corrected().beta()[0]==0,"Source corrected estimate is numeric zero; preserve its native signed-zero bits");assertTrue(actual.corrected().standardError()[0]==0);
        assertTrue(Double.isNaN(actual.corrected().statistic()[0]));assertTrue(Double.isNaN(actual.corrected().pValue()[0]));
        assertTrue(java.util.Arrays.stream(actual.distortion().coefficientPercent()).anyMatch(x->!Double.isFinite(x)));
    }
    @Test void originalMultivariableDistortionAboveOneRemainsUnclampedAndUnreportable()throws Exception {
        for(String name:List.of("balanced-mvmr-distortion-257","balanced-mvmr-distortion-258")){
            var record=references().get(name);var actual=analyze(record);var source=field(field(field(record,"result"),"MR-PRESSO results"),"Distortion Test");
            double expected=number(field(source,"Pvalue"));assertTrue(expected>1);
            var probability=actual.distortion().probability();assertEquals(expected,probability.originalValue());
            assertFalse(probability.reportable());assertFalse(probability.lessThanBound());
            assertEquals(Math.round(expected*probability.distributions()),probability.exceedances());
            assertTrue(actual.warnings().stream().anyMatch(x->x.startsWith("SOURCE_DISTORTION_PROBABILITY_UNREPORTABLE")));
        }
    }
}
