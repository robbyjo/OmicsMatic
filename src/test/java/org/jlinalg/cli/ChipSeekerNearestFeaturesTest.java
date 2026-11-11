/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jlinalg.genetics.ChipSeekerNearestFeatures;
import org.jlinalg.genetics.ChipSeekerNearestFeatures.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChipSeekerNearestFeaturesTest {
    private static final class Reference {
        static final Map<?,?> VALUE=read();
        private static Map<?,?> read() {
            try(var in=ChipSeekerNearestFeaturesTest.class.getResourceAsStream("/org/jlinalg/genetics/chipseeker-nearest/original-controls-v2.json.gz")){
                assertNotNull(in);byte[] bytes=in.readNBytes(23588);assertEquals(23587,bytes.length);
                assertEquals("496aecfa007547abaa2f1ad816af856632c67c596a6a1d9e92e85a50b1f017e1",NextflowFiles.sha(bytes));
                try(var gzip=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){
                    byte[] raw=gzip.readNBytes(8_388_609);assertTrue(raw.length<=8_388_608);
                    return NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw),8_388_608,1_000_000));
                }
            }catch(Exception failure){throw new IllegalStateException("Original ChIPseeker reference intake failed",failure);}
        }
    }
    static Stream<Arguments> originalCases() throws Exception {
        return selectedCases("native_eligible_cases",128);
    }
    static Stream<Arguments> unsupportedBoundaryCases() throws Exception {
        return selectedCases("native_unsupported_boundary_cases",64);
    }
    private static Stream<Arguments> selectedCases(String field,int count) throws Exception {
        var fixture=Reference.VALUE;var plan=NextflowFiles.object(fixture.get("plan"));var selected=NextflowFiles.list(fixture.get(field));
        var byId=new java.util.HashMap<String,Map<?,?>>();
        for(Object raw:NextflowFiles.list(plan.get("cases"))){var row=NextflowFiles.object(raw);byId.put(NextflowFiles.text(row.get("id")),row);}
        var rows=new ArrayList<Arguments>();
        for(Object raw:NextflowFiles.list(NextflowFiles.object(fixture.get("original")).get("cases"))){
            var row=NextflowFiles.object(raw);String id=NextflowFiles.text(row.get("case_id"));
            if(selected.contains(id))rows.add(Arguments.of(NextflowFiles.text(row.get("version"))+" / "+id,byId.get(id),row));
        }
        assertEquals(count,rows.size());return rows.stream();
    }
    private static int integer(Object value) throws Exception {return Math.toIntExact(NextflowFiles.integer(value,0,Integer.MAX_VALUE));}
    @ParameterizedTest(name="{0}") @MethodSource("originalCases")
    void matchesUnchangedOriginalComponent(String label,Map<?,?> source,Map<?,?> reference) throws Exception {
        var peaks=new ArrayList<Peak>();var features=new ArrayList<Feature>();
        for(Object raw:NextflowFiles.list(source.get("peaks"))){
            var row=NextflowFiles.object(raw);assertEquals("*",row.get("strand"));
            peaks.add(new Peak(NextflowFiles.text(row.get("contig")),integer(row.get("start")),integer(row.get("end"))));
        }
        for(Object raw:NextflowFiles.list(source.get("features"))){
            var row=NextflowFiles.object(raw);features.add(new Feature(NextflowFiles.text(row.get("contig")),integer(row.get("start")),integer(row.get("end")),NextflowFiles.text(row.get("strand")).charAt(0)));
        }
        var raw=NextflowFiles.object(source.get("options"));assertEquals(false,raw.get("same_strand"));
        var options=new Options((Boolean)raw.get("ignore_overlap"),(Boolean)raw.get("ignore_upstream"),(Boolean)raw.get("ignore_downstream"),Overlap.valueOf(NextflowFiles.text(raw.get("overlap")).toUpperCase(java.util.Locale.ROOT)));
        assertNull(reference.get("source_error"));var output=NextflowFiles.object(reference.get("output"));
        var result=ChipSeekerNearestFeatures.annotate(peaks,features,options);var actual=result.sourceRetained();
        var indices=NextflowFiles.list(output.get("index_tokens"));var distances=NextflowFiles.list(output.get("distance_tokens"));var retained=NextflowFiles.list(output.get("retained_input_ordinals"));
        assertEquals(peaks.size(),result.rows().size());assertEquals(indices.size(),actual.size());assertEquals(actual.size(),distances.size());assertEquals(actual.size(),retained.size());
        for(int i=0;i<actual.size();i++){
            var row=actual.get(i);assertEquals(Integer.parseInt(NextflowFiles.text(indices.get(i))),row.sourceFeatureIndex(),label+" feature "+i);
            assertEquals(integer(retained.get(i)),row.inputIndex()+1,label+" input "+i);
            String token=NextflowFiles.text(distances.get(i));double expected=token.equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(token);
            assertEquals(expected,row.distanceToTss(),0,label+" distance "+i);
            assertEquals(Double.isInfinite(expected)?Disposition.BOUNDARY_DUMMY:Disposition.MATCHED,row.disposition());
        }
    }
    @ParameterizedTest(name="{0}") @MethodSource("unsupportedBoundaryCases")
    void originalIntegerOverflowPopulationsStayUnsupported(String label,Map<?,?> source,Map<?,?> reference) throws Exception {
        assertNull(reference.get("source_error"));
        assertTrue(NextflowFiles.list(reference.get("warnings")).stream().anyMatch(w->w.toString().contains("integer overflow")),label);
        int refused=0;
        for(Object raw:NextflowFiles.list(source.get("peaks"))){
            var row=NextflowFiles.object(raw);int start=integer(row.get("start")),end=integer(row.get("end"));
            if(end==Integer.MAX_VALUE){assertThrows(ChipSeekerNearestFeatures.UnsupportedInput.class,()->new Peak("chr2",start,end));refused++;}
        }
        for(Object raw:NextflowFiles.list(source.get("features"))){
            var row=NextflowFiles.object(raw);int start=integer(row.get("start")),end=integer(row.get("end"));char strand=NextflowFiles.text(row.get("strand")).charAt(0);
            if((strand=='+'?start:end)==Integer.MAX_VALUE){assertThrows(ChipSeekerNearestFeatures.UnsupportedInput.class,()->new Feature("chr2",start,end,strand));refused++;}
        }
        assertEquals(2,refused,label);
    }
    @Test void strictFlanksKeepDuplicateAndEqualDistanceSourceChoices(){
        var f=List.of(new Feature("x",100,180,'+'),new Feature("x",100,160,'+'),new Feature("x",220,300,'-'));
        var result=ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",200,200),new Peak("x",95,105)),f,Options.defaults());
        assertEquals(2,result.rows().get(0).sourceFeatureIndex());assertEquals(100,result.rows().get(0).distanceToTss());
        assertEquals(1,result.rows().get(1).sourceFeatureIndex());assertEquals(0,result.rows().get(1).distanceToTss());
    }
    @Test void wholeBodyChoiceAndFinalTssPriorityRemainDistinct(){
        var f=List.of(new Feature("x",100,180,'+'),new Feature("x",200,260,'+'));
        var peaks=List.of(new Peak("x",160,170),new Peak("x",160,205));
        var tss=ChipSeekerNearestFeatures.annotate(peaks,f,Options.defaults());
        var all=ChipSeekerNearestFeatures.annotate(peaks,f,new Options(false,false,false,Overlap.ALL));
        assertEquals(2,tss.rows().get(0).sourceFeatureIndex());assertEquals(-30,tss.rows().get(0).distanceToTss());
        assertEquals(1,all.rows().get(0).sourceFeatureIndex());assertEquals(60,all.rows().get(0).distanceToTss());
        assertEquals(2,all.rows().get(1).sourceFeatureIndex());assertEquals(0,all.rows().get(1).distanceToTss());
    }
    @Test void reverseFeatureDistancesUsePeakEndpointsAndOriginalSign(){
        var r=ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",250,260),new Peak("x",10,20)),List.of(new Feature("x",100,200,'-')),Options.defaults());
        assertEquals(-50,r.rows().get(0).distanceToTss());assertEquals(180,r.rows().get(1).distanceToTss());
    }
    @Test void forcedBoundaryRetainsDummyAndInfinityWithWarning(){
        var r=ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",150,160)),List.of(new Feature("x",100,120,'+')),new Options(true,true,true,Overlap.TSS));
        assertEquals(Disposition.BOUNDARY_DUMMY,r.rows().get(0).disposition());assertEquals(2,r.rows().get(0).sourceFeatureIndex());
        assertEquals(Double.POSITIVE_INFINITY,r.rows().get(0).distanceToTss());assertTrue(r.warnings().stream().anyMatch(w->w.contains("dummy is not a gene")));
    }
    @Test void unmappedInputsRemainExplicitAndSourceRetainedOrderStaysExact(){
        var r=ChipSeekerNearestFeatures.annotate(List.of(new Peak("missing",1,10),new Peak("x",150,160)),List.of(new Feature("x",100,120,'+')),Options.defaults());
        assertEquals(2,r.rows().size());assertEquals(Disposition.NO_FEATURE,r.rows().get(0).disposition());assertTrue(Double.isNaN(r.rows().get(0).distanceToTss()));
        assertEquals(1,r.sourceRetained().size());assertEquals(1,r.sourceRetained().get(0).inputIndex());
        assertEquals(Disposition.NO_FEATURE,ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",1,10)),List.of(),Options.defaults()).rows().get(0).disposition());
    }
    @Test void emptyQueryMatchesAuditedOriginalRefusal(){assertThrows(IllegalArgumentException.class,()->ChipSeekerNearestFeatures.annotate(List.of(),List.of(),Options.defaults()));}
    @Test void wildcardFeaturesNeedTheirOwnExplicitContract(){assertThrows(IllegalArgumentException.class,()->new Feature("x",1,10,'*'));}
    @Test void sourceReservedSeqlevelCollisionIsNotSilentlyRenamed(){assertThrows(IllegalArgumentException.class,()->new Feature("chrNA",1,10,'+'));}
    @Test void completeCapacityFailureDoesNotReturnPartialAnnotations(){
        assertThrows(IllegalArgumentException.class,()->ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",1,10)),List.of(new Feature("x",20,30,'+')),Options.defaults(),new Limits(1,1,1),()->{}));
    }
    @Test void cancellationDoesNotReturnPartialAnnotations(){
        assertThrows(java.util.concurrent.CancellationException.class,()->ChipSeekerNearestFeatures.annotate(List.of(new Peak("x",1,10)),List.of(new Feature("x",20,30,'+')),Options.defaults(),Limits.defaults(),()->{throw new java.util.concurrent.CancellationException();}));
    }
}
