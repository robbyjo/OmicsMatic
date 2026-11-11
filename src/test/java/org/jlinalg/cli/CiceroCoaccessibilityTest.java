/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.singlecell.CiceroCoaccessibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class CiceroCoaccessibilityTest {
    private static Map<?,?> fixture(){try(var in=CiceroCoaccessibilityTest.class.getResourceAsStream("/org/jlinalg/singlecell/cicero-coaccessibility/original-controls-v1.json.gz")){assertNotNull(in);byte[] zipped=in.readAllBytes();assertEquals("9eea71eaaa528e74fa6d3f221e1529093e4027e8c07eaa0f44aa524f0b1000bb",NextflowFiles.sha(zipped));try(var gzip=new GZIPInputStream(new ByteArrayInputStream(zipped))){return NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(gzip.readAllBytes()),4194304,1000000));}}catch(Exception e){throw new IllegalStateException("Original Cicero reference intake failed",e);}}
    static Stream<Arguments> references()throws Exception {var f=fixture();assertEquals(true,f.get("original_package_loaded"));assertEquals("1.3.9",f.get("Cicero_version"));assertEquals("4.6.1",f.get("R_version"));var controls=NextflowFiles.list(f.get("controls"));assertEquals(498,controls.size());return controls.stream().map(raw->{try{var row=NextflowFiles.object(raw);return Arguments.of(row.get("id")+" / "+row.get("kind"),row);}catch(IOException e){throw new IllegalStateException(e);}});}
    private static double number(Object value)throws IOException {if(value instanceof String s)return Double.parseDouble(s);return Double.parseDouble(((NextflowParameters.NumberToken)value).text());}
    @ParameterizedTest @MethodSource("references") void originalControls(String label,Map<?,?> control)throws Exception {
        var input=NextflowFiles.object(control.get("input"));String kind=NextflowFiles.text(control.get("kind"));
        if(kind.equals("window")){assertEquals(control.get("result"),CiceroCoaccessibility.inWindow((long)number(input.get("start")),(long)number(input.get("end")),(long)number(input.get("window_start")),(long)number(input.get("window_end"))),label);return;}
        String expected=NextflowFiles.text(control.get("result"));double actual;
        if(kind.equals("reconcile")){var raw=NextflowFiles.list(input.get("values"));double[] values=new double[raw.size()];for(int i=0;i<values.length;i++)values[i]=number(raw.get(i));var result=CiceroCoaccessibility.reconcile(values);if(expected.equals("NA")){assertEquals(CiceroCoaccessibility.Kind.NA_MIXED_SIGNS,result.kind(),label);return;}if(expected.equals("NaN")){assertEquals(CiceroCoaccessibility.Kind.NAN_EMPTY,result.kind(),label);return;}assertEquals(CiceroCoaccessibility.Kind.VALUE,result.kind(),label);actual=result.value();}
        else if(kind.equals("penalty"))actual=CiceroCoaccessibility.penalty(number(input.get("distance")),number(input.get("parameter")),number(input.get("power")));
        else {assertEquals("correlation",kind);actual=CiceroCoaccessibility.correlation(number(input.get("covariance")),number(input.get("row_diagonal")),number(input.get("column_diagonal")));}
        double value=Double.parseDouble(expected);assertEquals(value,actual,5e-13*Math.max(Math.abs(value),1e-300),label);if(value==0)assertEquals(Double.doubleToRawLongBits(value),Double.doubleToRawLongBits(actual),label);
    }
    @Test void mixedSignUnresolvedValuesAreNotZeros(){var x=CiceroCoaccessibility.reconcile(new double[]{0.2,-0.1});assertEquals(CiceroCoaccessibility.Kind.NA_MIXED_SIGNS,x.kind());assertTrue(Double.isNaN(x.value()));}
    @Test void unsupportedNonfiniteContributionsAreRefused(){assertThrows(IllegalArgumentException.class,()->CiceroCoaccessibility.reconcile(new double[]{Double.NaN}));assertThrows(IllegalArgumentException.class,()->CiceroCoaccessibility.reconcile(new double[]{Double.POSITIVE_INFINITY}));}
    @Test void invalidPenaltyAndModelInputsAreRefused(){assertThrows(IllegalArgumentException.class,()->CiceroCoaccessibility.penalty(2,2,1));assertThrows(IllegalArgumentException.class,()->CiceroCoaccessibility.correlation(1,0,1));}
    @Test void endpointInclusionIsNotGenericOverlap(){assertFalse(CiceroCoaccessibility.inWindow(0,101,1,100));assertFalse(CiceroCoaccessibility.inWindow(1,100,1,100));assertTrue(CiceroCoaccessibility.inWindow(1,99,1,100));}
}
