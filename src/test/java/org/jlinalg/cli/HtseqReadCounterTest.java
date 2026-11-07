/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.HtseqFeatureAssignment;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.jlinalg.sequence.HtseqFeatureAssignment.Mode;
import org.jlinalg.sequence.HtseqReadCounter;
import org.jlinalg.sequence.HtseqReadCounter.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class HtseqReadCounterTest {
    private static final Map<String,Object> REFERENCE=reference();
    private static Map<String,Object> reference() {
        try(var resource=HtseqReadCounterTest.class.getResourceAsStream("/org/jlinalg/sequence/htseq-read-count/original-counts.json.gz")) {
            assertNotNull(resource);byte[] raw;
            try(var input=new GZIPInputStream(resource)){raw=input.readAllBytes();}
            assertEquals("c69de2fccd7c4ad47dad01461d6075256245b4287332d68df344e10ca5245aba",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var result=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));
            assertEquals("2.1.2",result.get("HTSeq"));assertEquals(40,list(result.get("cases")).size());return result;
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object value){return (List<Object>)value;}
    private static Interval interval(Map<String,Object> row){return new Interval((String)row.get("chromosome"),Long.parseLong((String)row.get("start")),Long.parseLong((String)row.get("end")),((String)row.get("strand")).charAt(0));}
    private static List<Feature> features(Map<String,Object> prepared){return list(prepared.get("features")).stream().map(HtseqReadCounterTest::object).map(r->new Feature(interval(r),(String)r.get("feature"))).toList();}
    private static Alignment alignment(Object value){
        if(value==null)return null;var row=object(value);
        return new Alignment((Boolean)row.get("aligned"),(Boolean)row.get("secondary"),(Boolean)row.get("supplementary"),((Number)row.get("mapq")).intValue(),
            row.get("nh")==null?null:Math.toIntExact(((Number)row.get("nh")).longValue()),list(row.get("blocks")).stream().map(HtseqReadCounterTest::object).map(HtseqReadCounterTest::interval).toList());
    }
    private static Config config(Map<String,Object> prepared){
        var c=object(prepared.get("config"));return new Config(Layout.valueOf((String)c.get("layout")),
            Strand.valueOf(((String)c.get("stranded")).toUpperCase(Locale.ROOT)),Mode.valueOf(((String)c.get("overlap")).toUpperCase(Locale.ROOT).replace('-','_')),
            Nonunique.valueOf(((String)c.get("nonunique")).toUpperCase(Locale.ROOT)),((Number)c.get("minimum_mapq")).intValue(),
            FlagPolicy.valueOf(((String)c.get("secondary")).toUpperCase(Locale.ROOT)),FlagPolicy.valueOf(((String)c.get("supplementary")).toUpperCase(Locale.ROOT)));
    }
    private static List<Unit> units(Map<String,Object> prepared){var layout=config(prepared).layout();return list(prepared.get("units")).stream().map(HtseqReadCounterTest::object).map(r->new Unit((String)r.get("id"),layout,alignment(r.get("first")),alignment(r.get("second")))).toList();}
    static Stream<Arguments> sourceCases(){return list(REFERENCE.get("cases")).stream().map(HtseqReadCounterTest::object).map(c->Arguments.of(c.get("id"),c));}
    private static Counts count(Map<String,Object> row){var prepared=object(row.get("prepared"));var config=config(prepared);return HtseqReadCounter.count(HtseqReadCounter.prepare(features(prepared),config),units(prepared),config,100000);}
    @ParameterizedTest(name="{0}") @MethodSource("sourceCases") void everyGeneCountMatchesTheActualOriginalSAMGTFRunExactly(String label,Map<String,Object> row){
        var result=count(row);var genes=list(object(row.get("counts")).get("genes"));
        assertEquals(genes.stream().map(HtseqReadCounterTest::list).map(r->(String)r.get(0)).toList(),result.featureIds(),label);
        assertEquals(genes.size(),result.counts().size());
        for(var value:genes){var gene=list(value);double expected=Double.parseDouble((String)gene.get(1));assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(result.counts().get(gene.get(0))),label+"/"+gene.get(0));}
        assertEquals(list(object(row.get("prepared")).get("units")).size(),result.processedUnits());
    }
    @ParameterizedTest(name="{0}") @MethodSource("sourceCases") void allFiveSourceCountersRetainOriginalFilterPrecedenceAndOverlaps(String label,Map<String,Object> row){
        var result=count(row);var stats=result.statistics();
        long[] actual={stats.noFeature(),stats.ambiguous(),stats.tooLowAQual(),stats.notAligned(),stats.alignmentNotUnique()};
        var rows=list(object(row.get("counts")).get("summary"));
        assertEquals(List.of("__no_feature","__ambiguous","__too_low_aQual","__not_aligned","__alignment_not_unique"),rows.stream().map(HtseqReadCounterTest::list).map(r->r.get(0)).toList());
        for(int i=0;i<5;i++)assertEquals(Long.parseLong((String)list(rows.get(i)).get(1)),actual[i],label+"/"+i);
    }
    private static Interval iv(String chromosome,long start,long end,char strand){return new Interval(chromosome,start,end,strand);}
    private static List<Feature> annotation(){return List.of(new Feature(iv("chr1",0,100,'+'),"A"),new Feature(iv("chr1",10,40,'+'),"B"),new Feature(iv("chr1",100,200,'-'),"minus"));}
    private static Alignment read(boolean aligned,int mapq,Integer nh,boolean secondary,boolean supplementary,Interval...blocks){return new Alignment(aligned,secondary,supplementary,mapq,nh,List.of(blocks));}
    private static Alignment first(Integer nh){return read(true,60,nh,false,false,iv("chr1",0,5,'+'));}
    private static Alignment second(Integer nh){return read(true,60,nh,false,false,iv("chr1",50,55,'-'));}
    @Test void firstMateMissingNHAbortsTheSecondMateCheckAsInSource(){
        var config=Config.source212Defaults(Layout.PAIRED);var model=HtseqReadCounter.prepare(annotation(),config);
        var missing=HtseqReadCounter.assess(model,new Unit("missing-first",Layout.PAIRED,first(null),second(2)),config);
        assertEquals(NhCheck.FIRST_MATE_MISSING_TAG_ABORTED,missing.nhCheck());assertFalse(missing.alignmentNotUnique());assertEquals(State.ASSIGNED,missing.state());assertEquals(Map.of("A",1.0),missing.allocations());
        var present=HtseqReadCounter.assess(model,new Unit("present-first",Layout.PAIRED,first(1),second(2)),config);
        assertEquals(State.ALIGNMENT_NOT_UNIQUE,present.state());assertTrue(present.alignmentNotUnique());
        var absent=HtseqReadCounter.assess(model,new Unit("absent-first",Layout.PAIRED,null,second(2)),config);
        assertEquals(State.ALIGNMENT_NOT_UNIQUE,absent.state());
    }
    @Test void anUnknownChromosomeInEitherMateAbortsAllFeatureAssignment(){
        var config=Config.source212Defaults(Layout.PAIRED);var model=HtseqReadCounter.prepare(annotation(),config);
        var unknown=read(true,60,1,false,false,iv("absent",0,5,'-'));
        var result=HtseqReadCounter.assess(model,new Unit("mixed",Layout.PAIRED,first(1),unknown),config);
        assertEquals(State.NO_FEATURE,result.state());assertTrue(result.unknownChromosome());assertTrue(result.allocations().isEmpty());
    }
    @Test void anUnmappedPresentMateCanCauseLowQualityWhileAnAbsentMateDoesNot(){
        var config=Config.source212Defaults(Layout.PAIRED);var model=HtseqReadCounter.prepare(annotation(),config);
        var unmapped=read(false,0,null,false,false);
        assertEquals(State.LOW_QUALITY,HtseqReadCounter.assess(model,new Unit("unmapped",Layout.PAIRED,first(1),unmapped),config).state());
        assertEquals(State.ASSIGNED,HtseqReadCounter.assess(model,new Unit("absent",Layout.PAIRED,first(1),null),config).state());
    }
    @Test void fractionUsesTheFeatureSetNotNHAndSummaryCountersCanOverlap(){
        var config=new Config(Layout.SINGLE,Strand.NO,Mode.UNION,Nonunique.FRACTION,10,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        var model=HtseqReadCounter.prepare(annotation(),config);
        var unit=new Unit("multi",Layout.SINGLE,read(true,60,20,false,false,iv("chr1",20,25,'+')),null);
        var result=HtseqReadCounter.count(model,List.of(unit),config,1);
        assertEquals(0.5,result.counts().get("A"));assertEquals(0.5,result.counts().get("B"));
        assertEquals(1,result.statistics().alignmentNotUnique());assertEquals(1,result.statistics().ambiguous());assertEquals(1,result.processedUnits());
    }
    @Test void nonuniquePrecedesQualityAndAllModeRetainsBothCategories(){
        var none=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(annotation(),none);
        var unit=new Unit("low-multi",Layout.SINGLE,read(true,0,2,false,false,iv("chr1",0,5,'+')),null);
        assertEquals(State.ALIGNMENT_NOT_UNIQUE,HtseqReadCounter.assess(model,unit,none).state());
        var all=new Config(Layout.SINGLE,Strand.YES,Mode.UNION,Nonunique.ALL,10,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        var result=HtseqReadCounter.count(model,List.of(unit),all,1);
        assertEquals(1,result.statistics().alignmentNotUnique());assertEquals(1,result.statistics().tooLowAQual());assertEquals(0.0,result.counts().get("A"));
    }
    @Test void mappingQualityTwentyOneEnforcesThePapersStrictGreaterThanTwenty(){
        var config=new Config(Layout.SINGLE,Strand.YES,Mode.UNION,Nonunique.NONE,21,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        var model=HtseqReadCounter.prepare(annotation(),config);
        for(int quality:new int[]{20,21,255}){
            var state=HtseqReadCounter.assess(model,new Unit("q"+quality,Layout.SINGLE,read(true,quality,1,false,false,iv("chr1",0,5,'+')),null),config).state();
            assertEquals(quality==20?State.LOW_QUALITY:State.ASSIGNED,state);
        }
    }
    @Test void emptyInputPreservesZeroCountFeaturesButNotAFalseReadUnit(){
        var config=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(annotation(),config);
        var result=HtseqReadCounter.count(model,List.of(),config,1);
        assertTrue(result.emptyInput());assertEquals(0,result.processedUnits());assertEquals(3,result.counts().size());
        assertTrue(result.counts().values().stream().allMatch(x->x==0.0));
        var incompatible=new Config(Layout.SINGLE,Strand.NO,Mode.UNION,Nonunique.NONE,10,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        assertThrows(IllegalArgumentException.class,()->HtseqReadCounter.count(model,List.of(),incompatible,1));
    }
    @Test void countedResultsAndAnnotationIdentitiesAreImmutable(){
        var config=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(annotation(),config);
        var unit=new Unit("read",Layout.SINGLE,first(1),null);
        var result=HtseqReadCounter.count(model,List.of(unit),config,1);
        assertThrows(UnsupportedOperationException.class,()->result.counts().put("A",999.0));
        assertThrows(UnsupportedOperationException.class,()->result.featureIds().clear());
        assertEquals(result,HtseqReadCounter.count(model,List.of(unit),config,1));
    }
    @Test void mixedLayoutsAndExceededUnitBoundsAdmitNoCountResult(){
        var config=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(annotation(),config);
        var single=new Unit("one",Layout.SINGLE,first(1),null);var pair=new Unit("pair",Layout.PAIRED,first(1),second(1));
        assertThrows(IllegalArgumentException.class,()->HtseqReadCounter.count(model,List.of(single,pair),config,2));
        assertThrows(IllegalArgumentException.class,()->HtseqReadCounter.count(model,List.of(single,single),config,1));
        assertThrows(IllegalArgumentException.class,()->HtseqReadCounter.count(model,List.of(),config,0));
        assertThrows(IllegalArgumentException.class,()->HtseqReadCounter.count(model,List.of(),config,1_000_000_000_001L));
    }
    @Test void cancellationAndInterruptionReturnNoPartialCountResult(){
        var config=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(annotation(),config);var unit=new Unit("read",Layout.SINGLE,first(1),null);
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->HtseqReadCounter.count(model,Collections.nCopies(100,unit),config,100,()->calls.incrementAndGet()>25));
        assertFalse(Thread.currentThread().isInterrupted());
        try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->HtseqReadCounter.count(model,List.of(unit),config,1));assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
    }
    @Test void invalidPreparedFieldsAreRefusedRatherThanRepaired(){
        assertThrows(IllegalArgumentException.class,()->read(true,-1,1,false,false));assertThrows(IllegalArgumentException.class,()->read(true,256,1,false,false));
        assertThrows(IllegalArgumentException.class,()->read(false,0,null,false,false,iv("chr1",0,5,'+')));
        assertThrows(IllegalArgumentException.class,()->read(true,60,1,false,false,iv("chr1",5,5,'+')));
        assertThrows(IllegalArgumentException.class,()->read(true,60,1,false,false,iv("chr1",0,5,'.')));
        assertThrows(IllegalArgumentException.class,()->new Unit("empty",Layout.SINGLE,null,null));
        assertThrows(IllegalArgumentException.class,()->new Unit("two",Layout.SINGLE,first(1),second(1)));
        assertThrows(IllegalArgumentException.class,()->new Config(Layout.SINGLE,Strand.YES,Mode.UNION,Nonunique.NONE,256,FlagPolicy.IGNORE,FlagPolicy.IGNORE));
    }
}
