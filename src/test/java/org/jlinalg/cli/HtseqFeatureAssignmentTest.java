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
import org.jlinalg.sequence.HtseqFeatureAssignment.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class HtseqFeatureAssignmentTest {
    private static final Map<String,Object> REFERENCE=reference();
    private static Map<String,Object> reference() {
        try (var resource=HtseqFeatureAssignmentTest.class.getResourceAsStream("/org/jlinalg/sequence/htseq-intervals/original-controls.json.gz")) {
            assertNotNull(resource);
            byte[] raw;
            try (var input=new GZIPInputStream(resource)) { raw=input.readAllBytes(); }
            assertEquals("f2cc08536a55bef562c41762083b2831b603c882b9ff40e8dc026644d752dc97",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var result=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));
            assertEquals("2.1.2",result.get("HTSeq"));
            assertEquals(43,list(result.get("cases")).size());
            return result;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>)value; }
    @SuppressWarnings("unchecked") private static List<Object> list(Object value) { return (List<Object>)value; }
    private static Interval interval(Object value) {
        var row=object(value);
        return new Interval((String)row.get("chromosome"),Long.parseLong((String)row.get("start")),Long.parseLong((String)row.get("end")),((String)row.get("strand")).charAt(0));
    }
    private static List<Feature> features(Map<String,Object> population) {
        return list(population.get("features")).stream().map(HtseqFeatureAssignmentTest::object)
            .map(row->new Feature(interval(row.get("interval")),(String)row.get("feature"))).toList();
    }
    private static Index index(Map<String,Object> population) { return HtseqFeatureAssignment.index(features(population),(Boolean)population.get("stranded")); }
    private static List<Interval> blocks(Map<String,Object> query) { return list(query.get("blocks")).stream().map(HtseqFeatureAssignmentTest::interval).toList(); }
    static Stream<Arguments> successfulQueries() {
        return list(REFERENCE.get("cases")).stream().map(HtseqFeatureAssignmentTest::object).filter(c->!c.containsKey("insertion_error"))
            .flatMap(c->list(c.get("queries")).stream().map(HtseqFeatureAssignmentTest::object).filter(q->q.containsKey("original_steps"))
                .map(q->Arguments.of(c.get("id")+"/"+q.get("id"),c,q)));
    }
    static Stream<Arguments> successfulModes() {
        return list(REFERENCE.get("cases")).stream().map(HtseqFeatureAssignmentTest::object).filter(c->!c.containsKey("insertion_error"))
            .flatMap(c->list(c.get("queries")).stream().map(HtseqFeatureAssignmentTest::object).filter(q->q.containsKey("original_steps"))
                .flatMap(q->Arrays.stream(Mode.values()).map(m->Arguments.of(c.get("id")+"/"+q.get("id")+"/"+m,c,q,m))));
    }
    static Stream<Arguments> insertionRefusals() {
        return list(REFERENCE.get("cases")).stream().map(HtseqFeatureAssignmentTest::object).filter(c->c.containsKey("insertion_error")).map(c->Arguments.of(c.get("id"),c));
    }
    static Stream<Arguments> queryRefusals() {
        return list(REFERENCE.get("cases")).stream().map(HtseqFeatureAssignmentTest::object).filter(c->!c.containsKey("insertion_error"))
            .flatMap(c->list(c.get("queries")).stream().map(HtseqFeatureAssignmentTest::object).filter(q->q.containsKey("query_error"))
                .map(q->Arguments.of(c.get("id")+"/"+q.get("id"),c,q)));
    }
    @ParameterizedTest(name="{0}") @MethodSource("successfulQueries")
    void completeOriginalStepGeometryAndFeatureMembershipMatch(String label,Map<String,Object> population,Map<String,Object> query) {
        var index=index(population);
        var original=list(query.get("original_steps"));
        var blocks=blocks(query);
        assertEquals(blocks.size(),original.size());
        for (int i=0;i<blocks.size();i++) {
            var expected=list(original.get(i)).stream().map(HtseqFeatureAssignmentTest::object)
                .map(row->new Step(interval(row.get("interval")),list(row.get("features")).stream().map(String.class::cast).toList())).toList();
            assertEquals(expected,index.steps(blocks.get(i)),label);
        }
    }
    @ParameterizedTest(name="{0}") @MethodSource("successfulModes")
    void documentedModesMatchResolutionOfActualOriginalSteps(String label,Map<String,Object> population,Map<String,Object> query,Mode mode) {
        var expected=list(object(query.get("documented_mode_assignments_from_original_steps")).get(mode.name())).stream().map(String.class::cast).toList();
        var result=index(population).assign(blocks(query),mode);
        assertEquals(expected,result.features(),label);
        assertEquals(mode,result.mode());
        assertEquals(expected.isEmpty()?Outcome.NO_FEATURE:expected.size()==1?Outcome.UNIQUE:Outcome.AMBIGUOUS,result.outcome());
    }
    @ParameterizedTest(name="{0}") @MethodSource("insertionRefusals")
    void sourceInvalidZeroLengthFeatureInsertionIsRefused(String label,Map<String,Object> population) {
        assertEquals("IndexError",object(population.get("insertion_error")).get("error_type"));
        assertThrows(IllegalArgumentException.class,()->index(population),label);
    }
    @ParameterizedTest(name="{0}") @MethodSource("queryRefusals")
    void sourceInvalidZeroLengthQueryRefusesEveryMode(String label,Map<String,Object> population,Map<String,Object> query) {
        assertEquals("IndexError",object(query.get("query_error")).get("error_type"));
        var index=index(population);
        for (Mode mode:Mode.values()) assertThrows(IllegalArgumentException.class,()->index.assign(blocks(query),mode),label);
    }
    private static Interval iv(long start,long end) { return new Interval("chr1",start,end,'+'); }
    private static List<Feature> overlapping() { return List.of(new Feature(iv(0,10),"a"),new Feature(iv(5,15),"b")); }
    @Test void fixtureCensusDoesNotCountUnexecutedQueriesAsSuccessful() {
        assertEquals(538,successfulQueries().count());assertEquals(1614,successfulModes().count());
        assertEquals(44,queryRefusals().count());assertEquals(15,insertionRefusals().count());
        assertEquals(3,list(REFERENCE.get("domain_probes")).size());
        for (var value:list(REFERENCE.get("domain_probes"))) assertEquals(Boolean.FALSE,object(value).get("accepted"));
    }
    static Stream<Arguments> capacities() {
        return Stream.of(Arguments.of("features",new Limits(1,10,10,10,10)),
            Arguments.of("blocks",new Limits(10,1,10,10,10)),Arguments.of("matches",new Limits(10,10,1,10,10)),
            Arguments.of("steps",new Limits(10,10,10,1,10)),Arguments.of("labels",new Limits(10,10,10,10,1)));
    }
    @ParameterizedTest @MethodSource("capacities") void boundedCapacityNeverReturnsTruncatedAssignment(String label,Limits limits) {
        if (label.equals("features")) assertThrows(IllegalArgumentException.class,()->HtseqFeatureAssignment.index(overlapping(),true,limits,()->false));
        else {
            var index=HtseqFeatureAssignment.index(overlapping(),true,limits,()->false);
            assertThrows(IllegalArgumentException.class,()->index.assign(List.of(iv(0,15),iv(0,15)),Mode.UNION));
        }
    }
    @Test void matchBudgetCoversTheWholeMultiBlockAssignment() {
        var index=HtseqFeatureAssignment.index(List.of(new Feature(iv(0,10),"a")),true,new Limits(1,2,1,10,2),()->false);
        assertEquals(List.of("a"),index.assign(List.of(iv(0,5)),Mode.UNION).features());
        assertThrows(IllegalArgumentException.class,()->index.assign(List.of(iv(0,5),iv(0,5)),Mode.UNION));
    }
    @Test void zeroOverlapAndZeroLabelCapsStillPermitAnEmptyResult() {
        var index=HtseqFeatureAssignment.index(overlapping(),true,new Limits(2,1,0,1,0),()->false);
        assertEquals(Outcome.NO_FEATURE,index.assign(List.of(new Interval("absent",0,1,'+')),Mode.UNION).outcome());
        assertThrows(IllegalArgumentException.class,()->index.steps(iv(0,1)));
    }
    @Test void inputListMutationCannotAlterTheBuiltIndexAndResultsAreImmutable() {
        var input=new ArrayList<>(overlapping());var index=HtseqFeatureAssignment.index(input,true);input.clear();
        var steps=index.steps(iv(0,15));assertEquals(3,steps.size());
        assertThrows(UnsupportedOperationException.class,()->steps.clear());
        assertThrows(UnsupportedOperationException.class,()->steps.get(0).features().add("forged"));
        var result=index.assign(List.of(iv(0,15)),Mode.UNION);
        assertEquals(List.of("a","b"),result.features());
        assertThrows(UnsupportedOperationException.class,()->result.features().add("forged"));
    }
    @Test void cancellationDuringIndexConstructionAdmitsNoIndex() {
        var counter=new AtomicInteger();
        assertThrows(CancellationException.class,()->HtseqFeatureAssignment.index(overlapping(),true,Limits.defaults(),()->counter.incrementAndGet()>3));
    }
    @Test void cancellationDuringQueryAdmitsNoPartialSteps() {
        var input=new ArrayList<Feature>();for(int i=0;i<500;i++)input.add(new Feature(iv(i,i+30),"g"+i));
        var index=HtseqFeatureAssignment.index(input,true);var counter=new AtomicInteger();
        assertThrows(CancellationException.class,()->index.steps(iv(0,600),()->counter.incrementAndGet()>40));
    }
    @Test void interruptionIsObservedWithoutClearingTheCallersFlag() {
        var index=HtseqFeatureAssignment.index(overlapping(),true);assertFalse(Thread.currentThread().isInterrupted());
        try {Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->index.steps(iv(0,5)));assertTrue(Thread.currentThread().isInterrupted());}
        finally {Thread.interrupted();}
    }
    @Test void negativeAndDotStrandDomainsAreRefusedWithoutRepair() {
        assertThrows(IllegalArgumentException.class,()->new Interval("chr1",-1,5,'+'));
        assertThrows(IllegalArgumentException.class,()->new Interval("chr1",5,4,'+'));
        assertThrows(IllegalArgumentException.class,()->new Interval("chr1",0,5,'?'));
        var index=HtseqFeatureAssignment.index(overlapping(),true);
        assertThrows(IllegalArgumentException.class,()->index.steps(new Interval("chr1",0,5,'.')));
        assertThrows(IllegalArgumentException.class,()->HtseqFeatureAssignment.index(List.of(new Feature(new Interval("chr1",0,5,'.'),"a")),true));
    }
}
