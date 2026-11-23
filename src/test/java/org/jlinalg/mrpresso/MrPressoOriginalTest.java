/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mrpresso;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.mrpresso.MrPressoReferenceAssertions.*;

final class MrPressoOriginalTest {
    @Test void comparesWholeFiniteNativeAnalysisAndRngAgainstTwentyOriginalPublicApiReturns()throws Exception {
        int returned=0,errors=0,unreportable=0;
        try(var source=MrPressoOriginalTest.class.getResourceAsStream("/org/jlinalg/mrpresso/original-public-api-reference.zip")){
            assertNotNull(source);
            try(var zip=new ZipInputStream(source)){
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    if(!entry.getName().endsWith("/original.json"))continue;
                    Map<String,Object> record=object(GprofilerJson.read(zip.readAllBytes()));String name=text(field(record,"case"));
                    Object arguments=field(record,"arguments"),input=field(record,"input");
                    List<String> exposures=strings(field(arguments,"BetaExposure"));int n=list(object(input).get("row_names")).size(),k=exposures.size();
                    double[] outcome=column(input,text(field(arguments,"BetaOutcome"))),sy=column(input,text(field(arguments,"SdOutcome")));
                    double[][] x=new double[n][k],sx=new double[n][k];List<String> sxNames=strings(field(arguments,"SdExposure"));
                    for(int j=0;j<k;j++){
                        double[] beta=column(input,exposures.get(j)),sd=column(input,sxNames.get(j));
                        for(int i=0;i<n;i++){x[i][j]=beta[i];sx[i][j]=sd[i];}
                    }
                    var data=new MrPressoOriginal.Data(rawStrings(object(input).get("row_names")),exposures,outcome,x,sy,sx);
                    var options=new MrPressoOriginal.Options(bool(field(arguments,"OUTLIERtest")),bool(field(arguments,"DISTORTIONtest")),number(field(arguments,"SignifThreshold")),(int)number(field(arguments,"NbDistribution")));
                    var random=new MrPressoRandom((int)number(field(arguments,"seed")),MrPressoRandom.Sampling.REJECTION);
                    if(!bool(field(record,"returned"))){
                        errors++;assertThrows(IllegalArgumentException.class,()->MrPressoOriginal.analyze(data,options,random),name);continue;
                    }
                    if(name.equals("aliased-exposures")){
                        unreportable++;
                        // Original returns a recycled coefficient frame after an
                        // aliased fit. Do not admit its second exposure as estimated.
                        assertThrows(IllegalArgumentException.class,()->MrPressoOriginal.analyze(data,options,random),name);continue;
                    }
                    MrPressoOriginal.Result actual=MrPressoOriginal.analyze(data,options,random);Object result=field(record,"result");
                    Object tests=field(result,"MR-PRESSO results"),global=field(tests,"Global Test");
                    close(number(field(global,"RSSobs")),actual.globalRss(),name+" RSS");
                    probability(field(global,"Pvalue"),actual.globalProbability(),name+" global P");
                    int[] after=values(field(record,"rng_after")).stream().mapToInt(v->(int)rawNumber(v)).toArray();
                    assertArrayEquals(after,actual.rngAfter(),name+" original RNG consumption");
                    Object main=field(result,"Main MR results");
                    compareFit(main,0,k,actual.raw(),name+" raw");
                    if(actual.corrected()==null)for(String key:List.of("Causal Estimate","Sd","T-stat","P-value")){
                        double[] expected=column(main,key);for(int j=k;j<2*k;j++)assertTrue(Double.isNaN(expected[j]),name+" must preserve missing corrected estimate");
                    }else compareFit(main,k,k,actual.corrected(),name+" corrected");
                    Object outlier=optional(tests,"Outlier Test");
                    if(outlier==null)assertTrue(actual.outliers().isEmpty(),name);
                    else{
                        List<String> labels=rawStrings(object(outlier).get("row_names"));assertEquals(labels,actual.retainedRowNames(),name+" complete-case labels");
                        double[] residual=column(outlier,"RSSobs");Object p=columnObject(outlier,"Pvalue");
                        assertEquals(labels.size(),actual.outliers().size(),name);
                        for(int i=0;i<labels.size();i++){
                            var observed=actual.outliers().get(i);assertEquals(labels.get(i),observed.rowName(),name+" original row label");
                            close(residual[i],observed.squaredResidual(),name+" outlier "+i);
                            probabilityElement(values(p).get(i),observed.probability(),name+" adjusted P "+i);
                        }
                    }
                    Object distortion=optional(tests,"Distortion Test");
                    if(distortion==null)assertNull(actual.distortion(),name);
                    else{
                        Object indices=field(distortion,"Outliers Indices");
                        if(object(indices).get("kind").equals("character")){
                            String status=text(indices);
                            assertEquals(status.startsWith("All")?MrPressoOriginal.DistortionStatus.ALL_INSTRUMENTS_OUTLIERS:MrPressoOriginal.DistortionStatus.NO_SIGNIFICANT_OUTLIERS,actual.distortion().status(),name);
                            assertFalse(actual.distortion().probability().reportable(),name);
                        }else{
                            assertArrayEquals(values(indices).stream().mapToInt(v->(int)rawNumber(v)).toArray(),actual.distortion().outlierOrdinals(),name+" source outlier ordinals");
                            double[] expected=numbers(field(distortion,"Distortion Coefficient"));double[] found=actual.distortion().coefficientPercent();assertEquals(expected.length,found.length);
                            for(int j=0;j<expected.length;j++)close(expected[j],found[j],name+" distortion "+j);
                            probability(field(distortion,"Pvalue"),actual.distortion().probability(),name+" distortion P");
                        }
                    }
                    returned++;
                }
            }
        }
        assertEquals(20,returned);assertEquals(4,errors);assertEquals(1,unreportable);
    }

}
