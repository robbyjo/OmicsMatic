/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SmartpcaFstReferenceTest {
    private static Map<String,Map<String,Object>> references()throws Exception {
        var rows=new LinkedHashMap<String,Map<String,Object>>();
        try(var stream=SmartpcaFstReferenceTest.class.getResourceAsStream("/org/jlinalg/genetics/smartpca-original-fst-reference.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().endsWith("/reference.json"))rows.put(entry.getName(),object(GprofilerJson.read(zip.readAllBytes())));}
        }
        return rows;
    }
    @Test void comparesTwentyFourOriginalFstProgramsIncludingEveryDetailedLocus()throws Exception {
        int checked=0,negative=0,unreportable=0,comparedLoci=0,refusals=0;
        for(var reference:references().values())if(reference.get("kind").toString().startsWith("ORIGINAL_FSTONLY_")) {
            String name=reference.get("case").toString();var input=object(reference.get("input"));var raw=list(input.get("genotypes"));byte[][] calls=new byte[raw.size()][];
            for(int m=0;m<calls.length;m++){var row=list(raw.get(m));calls[m]=new byte[row.size()];for(int i=0;i<row.size();i++)calls[m][i]=(byte)integer(row.get(i));}
            int[] population=list(input.get("sample_population")).stream().mapToInt(SmartpcaFstReferenceTest::integer).toArray();var names=list(input.get("populations"));var positions=new ArrayList<SmartpcaFst.Position>();
            for(Object value:list(input.get("positions"))){var p=object(value);positions.add(new SmartpcaFst.Position(integer(p.get("chromosome")),number(p.get("genetic_position")),integer(p.get("physical_position"))));}
            var options=new SmartpcaFst.Options((Boolean)reference.get("inbreed"),number(reference.get("block_length")),SmartpcaPrepared.Limits.defaults());
            if(reference.get("kind").equals("ORIGINAL_FSTONLY_REFUSAL")){assertThrows(IllegalArgumentException.class,()->SmartpcaFst.fit(calls,population,names.size(),positions,options));refusals++;continue;}
            var observed=new ArrayList<SmartpcaFst.Locus>();var result=SmartpcaFst.fit(calls,population,names.size(),positions,options,observed::add);
            assertEquals(integer(reference.get("blocks")),result.blockMarkerCounts().length,name);var expected=list(reference.get("pairs"));assertEquals(expected.size(),result.pairs().size(),name);
            for(int i=0;i<expected.size();i++) {
                var row=object(expected.get(i));var actual=result.pairs().get(i);assertEquals(names.get(actual.firstPopulation()),row.get("first"),name);assertEquals(names.get(actual.secondPopulation()),row.get("second"),name);
                assertEquals(number(row.get("estimate")),actual.estimate(),.50001e-6,name);assertEquals(number(row.get("standard_error")),actual.standardError(),.50001e-6,name);
                if(actual.admission()!=SmartpcaFst.Admission.COMPUTED)unreportable++;if(actual.estimate()<0)negative++;
            }
            var inbreeding=list(reference.get("inbreeding"));assertEquals(inbreeding.size(),result.inbreeding().size(),name);
            for(int i=0;i<inbreeding.size();i++){var row=object(inbreeding.get(i));var actual=result.inbreeding().get(i);assertEquals(names.get(actual.firstPopulation()),row.get("population"));assertEquals(number(row.get("estimate")),actual.estimate(),.50001e-4,name);assertEquals(number(row.get("standard_error")),actual.standardError(),.50001e-4,name);}
            var loci=list(reference.get("loci"));int at=0;var markerNames=list(input.get("marker_ids"));
            for(var locus:observed) {
                int pair=0;double[] numerator=locus.numerator(),denominator=locus.denominator();
                for(int a=0;a<names.size();a++)for(int b=a+1;b<names.size();b++) {
                    var row=object(loci.get(at++));assertEquals(names.get(a),row.get("first"),name);assertEquals(names.get(b),row.get("second"),name);assertEquals(markerNames.get(locus.markerOrdinal()),row.get("marker"),name);
                    assertEquals(number(row.get("numerator")),numerator[pair],.50001e-6,name);assertEquals(number(row.get("denominator")),denominator[pair],.50001e-6,name);
                    if(denominator[pair]>0)assertEquals(number(row.get("ratio")),numerator[pair]/denominator[pair],.50001e-6,name);else assertEquals("-",row.get("ratio"),name);pair++;
                }
            }
            assertEquals(loci.size(),at,name);comparedLoci+=at;checked++;
        }
        assertEquals(23,checked);assertEquals(1,refusals);assertTrue(negative>0);assertTrue(unreportable>0);assertTrue(comparedLoci>2000);
    }
    @Test void comparesTwentyThreeDirectUnchangedOriginalWeightedJackknifeCalls()throws Exception {
        int checked=0,refused=0;
        for(var row:references().values())if("ORIGINAL_WJACKEST".equals(row.get("kind"))) {
            var input=object(row.get("input"));double mean=number(input.get("mean"));double[] deleted=list(input.get("deleted")).stream().mapToDouble(SmartpcaFstReferenceTest::number).toArray(),weights=list(input.get("weights")).stream().mapToDouble(SmartpcaFstReferenceTest::number).toArray();
            if(row.get("expected_native").equals("REFUSE")){assertThrows(IllegalArgumentException.class,()->SmartpcaFst.weightedJackknife(mean,deleted,weights));refused++;}
            else {var expected=object(row.get("result"));double[] actual=SmartpcaFst.weightedJackknife(mean,deleted,weights);close(number(expected.get("estimate")),actual[0],row.get("case").toString());close(number(expected.get("standard_error")),actual[1],row.get("case").toString());}
            checked++;
        }
        assertEquals(23,checked);assertEquals(2,refused);
    }
    @Test void monomorphicBlocksKeepSourceZerosButNeverPretendAnIdentifiedFst() {
        byte[][] calls={{0,0,0,0},{0,0,0,0},{0,0,0,0}};int[] groups={0,0,1,1};var positions=List.of(new SmartpcaFst.Position(1,0,1),new SmartpcaFst.Position(1,.1,2),new SmartpcaFst.Position(1,.2,3));
        var result=SmartpcaFst.fit(calls,groups,2,positions,SmartpcaFst.Options.defaults());var pair=result.pairs().get(0);assertEquals(0,pair.estimate());assertEquals(0,pair.standardError());assertEquals(SmartpcaFst.Admission.NO_POSITIVE_TOTAL_DENOMINATOR,pair.admission());assertArrayEquals(new int[]{1,1,1},result.blockMarkerCounts());
        var oneBlock=SmartpcaFst.fit(calls,groups,2,List.of(new SmartpcaFst.Position(1,.01,1),new SmartpcaFst.Position(1,.02,2),new SmartpcaFst.Position(1,.03,3)),SmartpcaFst.Options.defaults());assertEquals(SmartpcaFst.Admission.SOURCE_NO_JACKKNIFE_AT_MOST_ONE_BLOCK,oneBlock.pairs().get(0).admission());assertTrue(Double.isNaN(oneBlock.pairs().get(0).totalDenominator()));
    }
    @Test void resourceBoundsInvalidCallsAndInterruptionDoNotProduceAResult() {
        byte[][] calls={{0,1,2,1},{2,1,0,1}};int[] groups={0,0,1,1};var positions=List.of(new SmartpcaFst.Position(1,0,1),new SmartpcaFst.Position(1,.1,2));
        for(var limits:List.of(new SmartpcaPrepared.Limits(1,1_000_000),new SmartpcaPrepared.Limits(1_000_000,1)))assertThrows(IllegalArgumentException.class,()->SmartpcaFst.fit(calls,groups,2,positions,new SmartpcaFst.Options(false,.05,limits)));
        assertThrows(IllegalArgumentException.class,()->SmartpcaFst.fit(new byte[][]{{0,3,2,1},{2,1,0,1}},groups,2,positions,SmartpcaFst.Options.defaults()));
        assertThrows(IllegalArgumentException.class,()->SmartpcaFst.fit(calls,new int[]{0,0,2,1},2,positions,SmartpcaFst.Options.defaults()));
        Thread.currentThread().interrupt();try{assertThrows(IllegalStateException.class,()->SmartpcaFst.fit(calls,groups,2,positions,SmartpcaFst.Options.defaults()));}finally{Thread.interrupted();}
    }
    @Test void retainedArraysAndLocusSnapshotsAreImmutable() {
        byte[][] calls={{0,1,2,1},{2,2,0,0}};int[] groups={0,0,1,1};var positions=List.of(new SmartpcaFst.Position(1,0,1),new SmartpcaFst.Position(1,.1,2));var loci=new ArrayList<SmartpcaFst.Locus>();
        var result=SmartpcaFst.fit(calls,groups,2,positions,SmartpcaFst.Options.defaults(),loci::add);int[] weights=result.blockMarkerCounts();weights[0]=99;assertEquals(1,result.blockMarkerCounts()[0]);
        double expected=loci.get(0).numerator()[0];double[] values=loci.get(0).numerator();values[0]=Double.NaN;assertEquals(expected,loci.get(0).numerator()[0]);assertNotEquals(loci.get(0).numerator()[0],loci.get(1).numerator()[0]);
        assertThrows(UnsupportedOperationException.class,()->result.markers().clear());
    }
    private static void close(double expected,double actual,String label) {
        if(Double.isNaN(expected)){assertTrue(Double.isNaN(actual),label);return;}
        if(Double.isInfinite(expected)||expected==0){assertEquals(expected,actual,label);return;}
        assertEquals(expected,actual,3e-12*Math.abs(expected)+16*Math.ulp(expected),label);
    }
    private static Map<String,Object> object(Object value){assertInstanceOf(Map.class,value);var result=new LinkedHashMap<String,Object>();((Map<?,?>)value).forEach((k,v)->result.put(k.toString(),v));return result;}
    private static List<?> list(Object value){return (List<?>)value;}
    private static String token(Object value){return value instanceof GprofilerJson.Numeric n?n.token():value.toString();}
    private static int integer(Object value){return new java.math.BigDecimal(token(value)).intValueExact();}
    private static double number(Object value){String text=token(value);return text.equals("Inf")?Double.POSITIVE_INFINITY:text.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(text);}
    public static void main(String[] args)throws Exception {
        var test=new SmartpcaFstReferenceTest();test.comparesTwentyFourOriginalFstProgramsIncludingEveryDetailedLocus();test.comparesTwentyThreeDirectUnchangedOriginalWeightedJackknifeCalls();test.monomorphicBlocksKeepSourceZerosButNeverPretendAnIdentifiedFst();test.resourceBoundsInvalidCallsAndInterruptionDoNotProduceAResult();test.retainedArraysAndLocusSnapshotsAreImmutable();
        System.out.println("PASS five native FST methods;23 finite original programs,one original refusal and23 unchanged original jackknives.");
    }
}
