/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mrpresso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.junit.jupiter.api.Assertions.*;

/** Typed original-R reference assertions; finite comparisons have no absolute floor. */
final class MrPressoReferenceAssertions {
    private MrPressoReferenceAssertions() { }
    static void compareFit(Object frame,int offset,int k,MrPressoOriginal.Fit actual,String name) {
        double[][] outputs={actual.beta(),actual.standardError(),actual.statistic(),actual.pValue()};String[] keys={"Causal Estimate","Sd","T-stat","P-value"};
        for(int c=0;c<keys.length;c++){
            double[] expected=column(frame,keys[c]);for(int j=0;j<k;j++)close(expected[offset+j],outputs[c][j],name+" "+keys[c]+" "+j);
        }
    }
    static void probability(Object expected,MrPressoOriginal.Probability actual,String name){probabilityElement(values(expected).get(0),actual,name);}
    static void probabilityElement(Object expected,MrPressoOriginal.Probability actual,String name) {
        if(expected instanceof Map<?,?>){assertFalse(actual.reportable(),name);return;}
        if(expected instanceof String text&&text.startsWith("<")){
            assertTrue(actual.lessThanBound(),name);assertEquals(0,actual.originalValue(),name);assertEquals(Double.parseDouble(text.substring(1)),actual.bound(),3e-14,name);
        }else{
            assertFalse(actual.lessThanBound(),name);assertEquals(expected instanceof String text?Double.parseDouble(text):rawNumber(expected),actual.originalValue(),3e-14,name);
        }
    }
    static void close(double expected,double actual,String name){
        if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),name);
        else if(Double.isInfinite(expected))assertEquals(expected,actual,name);
        else {
            assertTrue(Double.isFinite(actual),name+" must stay finite");
            if(expected==0||actual==0)assertEquals(expected,actual,0,name+" numeric zero");
            else assertEquals(expected,actual,Math.abs(expected)*3e-11+32*Math.max(Math.ulp(expected),Math.ulp(actual)),name);
        }
    }
    static Object columnObject(Object frame,String name){
        for(Object raw:list(object(frame).get("columns"))){Map<String,Object> value=object(raw);if(value.get("name").equals(name))return value.get("data");}
        throw new IllegalArgumentException("Missing original column "+name);
    }
    static double[] column(Object frame,String name){return numbers(columnObject(frame,name));}
    static double[] numbers(Object typed){return values(typed).stream().mapToDouble(v->{
        if(!(v instanceof Map<?,?> special))return rawNumber(v);
        String literal=special.get("special").toString();
        if(literal.startsWith("NA_")||literal.equals("NaN"))return Double.NaN;
        if(literal.equals("Inf"))return Double.POSITIVE_INFINITY;if(literal.equals("-Inf"))return Double.NEGATIVE_INFINITY;
        throw new IllegalArgumentException("Unknown original special value "+literal);
    }).toArray();}
    static Object optional(Object typed,String name){Map<String,Object> value=object(typed);List<Object> names=list(value.get("names"));int i=names.indexOf(name);return i<0?null:list(value.get("values")).get(i);}
    static Object field(Object typed,String name){Object value=optional(typed,name);if(value==null)throw new IllegalArgumentException("Missing original field "+name);return value;}
    static double number(Object typed){return rawNumber(values(typed).get(0));}
    static double rawNumber(Object value){
        if(value instanceof GprofilerJson.Numeric numeric)return Double.parseDouble(numeric.token());
        if(value instanceof Number numeric)return numeric.doubleValue();
        throw new IllegalArgumentException("Original numeric token required");
    }
    static boolean bool(Object typed){return (Boolean)values(typed).get(0);}
    static String text(Object typed){return values(typed).get(0).toString();}
    static List<Object> values(Object typed){return list(object(typed).get("values"));}
    static List<String> strings(Object typed){return rawStrings(object(typed).get("values"));}
    static List<String> rawStrings(Object raw){return list(raw).stream().map(Object::toString).toList();}
    static List<Object> list(Object raw){if(!(raw instanceof List<?> values))throw new IllegalArgumentException("Original array required");return new ArrayList<>(values);}
    static Map<String,Object> object(Object raw){
        if(!(raw instanceof Map<?,?> values))throw new IllegalArgumentException("Original typed object required");var result=new LinkedHashMap<String,Object>();
        for(var entry:values.entrySet())result.put(entry.getKey().toString(),entry.getValue());return result;
    }
}
