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

    private static void compareFit(Object frame,int offset,int k,MrPressoOriginal.Fit actual,String name) {
        double[][] outputs={actual.beta(),actual.standardError(),actual.statistic(),actual.pValue()};String[] keys={"Causal Estimate","Sd","T-stat","P-value"};
        for(int c=0;c<keys.length;c++){
            double[] expected=column(frame,keys[c]);for(int j=0;j<k;j++)close(expected[offset+j],outputs[c][j],name+" "+keys[c]+" "+j);
        }
    }
    private static void probability(Object expected,MrPressoOriginal.Probability actual,String name){probabilityElement(values(expected).get(0),actual,name);}
    private static void probabilityElement(Object expected,MrPressoOriginal.Probability actual,String name) {
        if(expected instanceof Map<?,?>){assertFalse(actual.reportable(),name);return;}
        if(expected instanceof String text&&text.startsWith("<")){
            assertTrue(actual.lessThanBound(),name);assertEquals(0,actual.originalValue(),name);assertEquals(Double.parseDouble(text.substring(1)),actual.bound(),3e-14,name);
        }else{
            assertFalse(actual.lessThanBound(),name);assertEquals(expected instanceof String text?Double.parseDouble(text):rawNumber(expected),actual.originalValue(),3e-14,name);
        }
    }
    private static void close(double expected,double actual,String name){
        if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),name);
        else assertEquals(expected,actual,Math.max(3e-13,Math.abs(expected)*3e-11),name);
    }
    private static Object columnObject(Object frame,String name){
        for(Object raw:list(object(frame).get("columns"))){Map<String,Object> value=object(raw);if(value.get("name").equals(name))return value.get("data");}
        throw new IllegalArgumentException("Missing original column "+name);
    }
    private static double[] column(Object frame,String name){return numbers(columnObject(frame,name));}
    private static double[] numbers(Object typed){return values(typed).stream().mapToDouble(v->v instanceof Map<?,?>?Double.NaN:rawNumber(v)).toArray();}
    private static Object optional(Object typed,String name){Map<String,Object> value=object(typed);List<Object> names=list(value.get("names"));int i=names.indexOf(name);return i<0?null:list(value.get("values")).get(i);}
    private static Object field(Object typed,String name){Object value=optional(typed,name);if(value==null)throw new IllegalArgumentException("Missing original field "+name);return value;}
    private static double number(Object typed){return rawNumber(values(typed).get(0));}
    private static double rawNumber(Object value){
        if(value instanceof GprofilerJson.Numeric numeric)return Double.parseDouble(numeric.token());
        if(value instanceof Number numeric)return numeric.doubleValue();
        throw new IllegalArgumentException("Original numeric token required");
    }
    private static boolean bool(Object typed){return (Boolean)values(typed).get(0);}
    private static String text(Object typed){return values(typed).get(0).toString();}
    private static List<Object> values(Object typed){return list(object(typed).get("values"));}
    private static List<String> strings(Object typed){return rawStrings(object(typed).get("values"));}
    private static List<String> rawStrings(Object raw){return list(raw).stream().map(Object::toString).toList();}
    private static List<Object> list(Object raw){if(!(raw instanceof List<?> values))throw new IllegalArgumentException("Original array required");return new ArrayList<>(values);}
    private static Map<String,Object> object(Object raw){
        if(!(raw instanceof Map<?,?> values))throw new IllegalArgumentException("Original typed object required");var result=new LinkedHashMap<String,Object>();
        for(var entry:values.entrySet())result.put(entry.getKey().toString(),entry.getValue());return result;
    }
}
