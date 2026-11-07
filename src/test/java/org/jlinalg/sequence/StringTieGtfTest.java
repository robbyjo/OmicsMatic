/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StringTieGtfTest {
    private static final Map<String,Long> REFERENCE = Map.of("chrA",12000L,"chrB",10000L);
    private static final StringTieGtf.Limits LIMITS = new StringTieGtf.Limits(10000,10000,1000,100,100,16384,1048576);
    private static final String FIXTURE_SHA = "c090420cb1896d6a52454c651af627b35200383a3469d52db446febc9ae3c1f2";
    private static final String PARENTS = "gene_id \"g\"; transcript_id \"t\";";
    private static final String VALUES = " cov \"2.500000\"; FPKM \"300.000000\"; TPM \"1000000.000000\";";

    private static Map<String,byte[]> fixtures() throws Exception {
        byte[] raw;
        try (var input=StringTieGtfTest.class.getResourceAsStream("/org/jlinalg/sequence/stringtie-gtf/original-gtf-controls.zip")) {
            assertNotNull(input);raw=input.readAllBytes();
        }
        assertEquals(FIXTURE_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var result=new HashMap<String,byte[]>();
        try (var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry e;
            while ((e=zip.getNextEntry())!=null) {
                assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));byte[] value=zip.readNBytes(1048577);
                assertTrue(value.length<=1048576);assertNull(result.put(e.getName(),value));
            }
        }
        assertEquals(334,result.size());return result;
    }
    private static String text(Map<String,byte[]> files,String name) { assertTrue(files.containsKey(name),name);return new String(files.get(name),StandardCharsets.UTF_8); }
    private static StringTieGtf.Document parse(String value,StringTieGtf.Profile profile) throws IOException {
        return StringTieGtf.read(new StringReader(value),REFERENCE,profile,LIMITS,()->{});
    }
    private static String row(String type,int start,int end,String attributes) {
        return "chrA\tcontrol\t"+type+"\t"+start+"\t"+end+"\t.\t+\t.\t"+attributes+"\n";
    }
    private static String model(boolean quantities) {
        return row("transcript",1,20,PARENTS+(quantities?VALUES:""))
                +row("exon",1,10,PARENTS+" exon_number \"1\";")+row("exon",16,20,PARENTS+" exon_number \"2\";");
    }
    static Stream<Arguments> originals() {
        var cases=new ArrayList<Arguments>();
        for (String v:List.of("1.3.0","1.3.6","2.1.2","2.1.7","2.2.1","3.0.0","3.0.3"))
            for (String c:List.of("assembly","guided","estimate","empty-estimate","merge","paper171-documented-rf")) {
                if (v.equals("1.3.0")&&c.equals("paper171-documented-rf"))continue;
                var profile=c.equals("merge")?StringTieGtf.Profile.MERGE:c.contains("estimate")?StringTieGtf.Profile.ESTIMATION:StringTieGtf.Profile.ASSEMBLY;
                cases.add(Arguments.of(v+"/"+c,profile,c.equals("merge")?5:c.equals("assembly")||c.equals("paper171-documented-rf")?3:4));
            }
        assertEquals(41,cases.size());return cases.stream();
    }

    @ParameterizedTest @MethodSource("originals")
    void allFortyOneOriginalOutputsRetainEveryLineAttributeAndCompleteStructure(String path,StringTieGtf.Profile profile,int expected) throws Exception {
        var files=fixtures();String raw=text(files,path+"/transcripts.gtf");var result=parse(raw,profile);
        assertEquals(raw,result.lines().stream().map(l->l.text()+l.ending()).collect(java.util.stream.Collectors.joining()));
        assertEquals(raw.length(),result.characters());assertEquals(expected,result.transcripts().size());
        assertEquals(2,result.lines().stream().filter(l->l.kind()==StringTieGtf.Kind.COMMENT).count());
        for (var t:result.transcripts()) {
            assertNotNull(t.declaredTranscript());assertEquals(t.id(),t.declaredTranscript().attribute("transcript_id"));
            assertEquals(t.geneId(),t.declaredTranscript().attribute("gene_id"));
            assertEquals(t.exons().stream().mapToLong(e->(long)e.end1()-e.start1()+1).sum(),t.exonicLength());
            assertTrue(t.exonicLength()>=200);assertEquals(t.start1(),t.exons().get(0).start1());assertEquals(t.end1(),t.exons().get(t.exons().size()-1).end1());
            if (profile==StringTieGtf.Profile.MERGE)assertNull(t.abundance());
            else {
                assertNotNull(t.abundance());
                for (var v:List.of(t.abundance().coverage(),t.abundance().fpkm(),t.abundance().tpm()))assertEquals(new BigDecimal(v.text()),v.value());
            }
        }
        if (profile==StringTieGtf.Profile.ESTIMATION) {
            var reference=parse(text(files,"inputs/guide.gtf"),StringTieGtf.Profile.ANNOTATION);
            var audit=StringTieGtf.auditEstimation(result,reference,StringTieGtf.PopulationPolicy.COMPLETE);
            assertEquals(4,audit.emitted());assertEquals(0,audit.omitted());
            assertEquals(List.of("ta","ta.short","tb","tc.uncovered"),audit.rows().stream().map(StringTieGtf.ReferenceRow::transcriptId).toList());
            for (var disposition:audit.rows())assertEquals(disposition.transcriptId(),result.transcripts().get(disposition.outputOrdinal()-1).id());
            var uncovered=result.transcripts().stream().filter(t->t.id().equals("tc.uncovered")).findFirst().orElseThrow();
            assertEquals(0,uncovered.abundance().tpm().value().signum());assertEquals("0.0",uncovered.abundance().tpm().text());
            if (path.contains("empty-estimate"))assertTrue(result.transcripts().stream().allMatch(t->t.abundance().coverage().value().signum()==0));
        }
    }

    @Test void originalFailedFlagsRemainFailuresAndAreNeverRelabeledAsEmptyOutputs() throws Exception {
        var files=fixtures();
        for (String v:List.of("1.3.0","1.3.6","2.1.2","2.1.7","2.2.1","3.0.0","3.0.3")) {
            String p=v+"/paper171-literal-rf/";
            assertTrue(text(files,p+"fit.process.json").contains("\"exit_code\": 1"));
            assertFalse(files.containsKey(p+"transcripts.gtf"));assertTrue(text(files,p+"fit.stderr").contains("usage"));
        }
        assertFalse(files.containsKey("1.3.0/paper171-documented-rf/transcripts.gtf"));
    }
    @Test void historicalAbundanceDifferencesArePreservedWithoutClaimingEditionEquivalence() throws Exception {
        var files=fixtures();
        var old=parse(text(files,"1.3.0/estimate/transcripts.gtf"),StringTieGtf.Profile.ESTIMATION);
        var current=parse(text(files,"3.0.3/estimate/transcripts.gtf"),StringTieGtf.Profile.ESTIMATION);
        var a=old.transcripts().stream().filter(t->t.id().equals("ta")).findFirst().orElseThrow();
        var b=current.transcripts().stream().filter(t->t.id().equals("ta")).findFirst().orElseThrow();
        assertEquals("0.000000",a.abundance().tpm().text());assertEquals("216849.625203",b.abundance().tpm().text());
        assertNotEquals(a.abundance().tpm(),b.abundance().tpm());
    }
    @Test void originalMinusStrandExonNumbersFollowGenomicRatherThanTranscriptionOrder() throws Exception {
        var result=parse(text(fixtures(),"3.0.3/estimate/transcripts.gtf"),StringTieGtf.Profile.ESTIMATION);
        var minus=result.transcripts().stream().filter(t->t.id().equals("tb")).findFirst().orElseThrow();
        assertEquals('-',minus.strand());assertEquals(2001,minus.exons().get(0).start1());assertEquals("1",minus.exons().get(0).attribute("exon_number"));
    }
    @Test void annotationPreservesRepeatedAuxiliaryTagsAndSemicolonsInsideQuotedValues() throws Exception {
        String raw="# reference\r\n\r\n"+row("exon",16,20,PARENTS+" tag \"basic\"; tag \"second\"; note \"a;b\";")
                +row("CDS",3,8,PARENTS).replace("\t+\t.\t","\t+\t0\t")+row("exon",1,10,PARENTS);
        raw=raw.substring(0,raw.length()-1);var document=parse(raw,StringTieGtf.Profile.ANNOTATION);
        assertEquals(raw,document.lines().stream().map(l->l.text()+l.ending()).collect(java.util.stream.Collectors.joining()));
        assertEquals(2,document.transcripts().get(0).exons().size());assertNull(document.transcripts().get(0).declaredTranscript());
        assertEquals(15,document.transcripts().get(0).exonicLength());assertEquals("a;b",document.lines().get(2).feature().attribute("note"));
        assertEquals(2,document.lines().get(2).feature().attributes().stream().filter(a->a.name().equals("tag")).count());
        assertThrows(IllegalArgumentException.class,()->document.lines().get(2).feature().attribute("tag"));
    }
    @Test void missingTargetsHaveExplicitAbsentDispositionsAndNeverInventedZeroAbundance() throws Exception {
        var reference=parse(model(false),StringTieGtf.Profile.ANNOTATION);var empty=parse("# original result\n",StringTieGtf.Profile.ESTIMATION);
        assertThrows(IOException.class,()->StringTieGtf.auditEstimation(empty,reference,StringTieGtf.PopulationPolicy.COMPLETE));
        var retained=StringTieGtf.auditEstimation(empty,reference,StringTieGtf.PopulationPolicy.RETAIN_OMISSIONS);
        assertEquals(0,retained.emitted());assertEquals(1,retained.omitted());assertEquals(0,retained.rows().get(0).outputOrdinal());
        assertEquals(StringTieGtf.Disposition.OMITTED,retained.rows().get(0).disposition());assertTrue(empty.transcripts().isEmpty());
    }
    @Test void partiallyAnnotatedGuideQuantitiesStayAsSourceAttributesWithoutCompletingTheTriple() throws Exception {
        String raw=model(false).replace(PARENTS,PARENTS+" FPKM \"5.25\";");
        var guide=parse(raw,StringTieGtf.Profile.ANNOTATION);assertNull(guide.transcripts().get(0).abundance());
        assertEquals("5.25",guide.transcripts().get(0).declaredTranscript().attribute("FPKM"));
        assertNull(guide.transcripts().get(0).declaredTranscript().attribute("TPM"));
    }
    @Test void emptyOrGeneOnlyAnnotationsAreNotCompleteStringTieReferences() {
        assertThrows(IOException.class,()->parse("",StringTieGtf.Profile.ANNOTATION));
        assertThrows(IOException.class,()->parse(row("gene",1,20,"gene_id \"g\";"),StringTieGtf.Profile.ANNOTATION));
    }
    @Test void scoreIsDecimalSyntaxAndDoesNotAdmitJavaHexOrWhitespaceNumericExtensions() throws Exception {
        assertThrows(IOException.class,()->parse(model(true).replace("\t.\t+\t","\t0x1p0\t+\t"),StringTieGtf.Profile.ASSEMBLY));
        assertThrows(IOException.class,()->parse(model(true).replace("\t.\t+\t","\t 1 \t+\t"),StringTieGtf.Profile.ASSEMBLY));
        assertEquals("-1.5e2",parse(model(true).replace("\t.\t+\t","\t-1.5e2\t+\t"),StringTieGtf.Profile.ASSEMBLY).transcripts().get(0).declaredTranscript().score());
    }
    @ParameterizedTest @ValueSource(strings={"gene","identity","contig","strand","exon","extra"})
    void estimationRejectsChangedReferenceRelations(String mutation) throws Exception {
        String altered=switch(mutation) {
            case "gene"->model(true).replace("gene_id \"g\"","gene_id \"other\"");
            case "identity"->model(true).replace("transcript_id \"t\"","transcript_id \"other\"");
            case "contig"->model(true).replace("chrA","chrB");
            case "strand"->model(true).replace("\t+\t","\t-\t");
            case "exon"->model(true).replace("exon\t1\t10","exon\t1\t9");
            case "extra"->model(true)+model(true).replace("transcript_id \"t\"","transcript_id \"other\"");
            default->throw new AssertionError();
        };
        var output=parse(altered,StringTieGtf.Profile.ESTIMATION);var ref=parse(model(false),StringTieGtf.Profile.ANNOTATION);
        assertThrows(IOException.class,()->StringTieGtf.auditEstimation(output,ref,StringTieGtf.PopulationPolicy.RETAIN_OMISSIONS));
    }
    static Stream<String> malformed() {
        String base=model(true);
        return Stream.of(base.replace("gene_id \"g\";","gene_id \"g\"; gene_id \"g\";"),
                base.replace("TPM \"1000000.000000\";","TPM \"NaN\";"),base.replace("cov \"2.500000\";","cov \"-1\";"),
                base.replace("cov \"2.500000\";","cov \"1e5\";"),base.replace(" FPKM \"300.000000\";",""),
                base.replace("transcript\t1\t20","transcript\t1\t21"),base.replace("exon\t16\t20","exon\t10\t20"),
                base.replace("exon\t16\t20","exon\t1\t10"),base.replace("exon_number \"2\"","exon_number \"1\""),
                base.replace("exon_number \"2\";",""),base.replace("transcript_id \"t\";","transcript_id=t;"),
                base.replace("chrA\tcontrol\texon\t16","chrB\tcontrol\texon\t16"),base.replace("chrA","1"),
                base.replace("\t1\t20\t","\t0\t20\t"),base.replace("\t1\t20\t","\t20\t1\t"),
                base.replace("\t1\t20\t","\t1\t2147483648\t"),base.replace("\t+\t.\t","\t?\t.\t"),
                base.replace("\t+\t.\t","\t+\t3\t"),base.replace("\t.\t+\t","\tInfinity\t+\t"),
                base.replace("gene_id \"g\";","gene_id \"g\""),base.replace("gene_id \"g\";","gene_id \"g\\x\";"),
                base.replace("gene_id \"g\";","gene_id \"bad id\";"),base.replace("control\ttranscript","control\tCDS"),
                base+row("transcript",1,20,PARENTS+VALUES),base.substring(0,base.indexOf('\n')+1),
                base.substring(base.indexOf('\n')+1),base+"bad\trow\n",base.replace("\n","\r"),base+"\u0000",base+"\uD800");
    }
    @ParameterizedTest @MethodSource("malformed")
    void malformedOrAmbiguousCompleteOutputsAreRejected(String text) {
        assertThrows(IOException.class,()->parse(text,StringTieGtf.Profile.ESTIMATION));
    }
    @Test void modesKeepUnknownMergeQuantitiesAbsentAndRejectQuantitativeMergeOrPartialEstimation() throws Exception {
        var merge=parse(model(false),StringTieGtf.Profile.MERGE);assertNull(merge.transcripts().get(0).abundance());
        assertThrows(IOException.class,()->parse(model(false),StringTieGtf.Profile.ASSEMBLY));
        assertThrows(IOException.class,()->parse(model(true),StringTieGtf.Profile.MERGE));
        assertThrows(IllegalArgumentException.class,()->StringTieGtf.auditEstimation(merge,parse(model(false),StringTieGtf.Profile.ANNOTATION),StringTieGtf.PopulationPolicy.COMPLETE));
    }
    @Test void negativeZeroAndSourceDecimalPrecisionRemainVisible() throws Exception {
        var document=parse(model(true).replace("2.500000","-0.000000"),StringTieGtf.Profile.ESTIMATION);
        assertEquals("-0.000000",document.transcripts().get(0).abundance().coverage().text());
        assertEquals(6,document.transcripts().get(0).abundance().coverage().value().scale());
    }
    @Test void returnedPopulationsAreImmutable() throws Exception {
        var document=parse(model(true),StringTieGtf.Profile.ESTIMATION);
        assertThrows(UnsupportedOperationException.class,()->document.lines().clear());
        assertThrows(UnsupportedOperationException.class,()->document.transcripts().clear());
        assertThrows(UnsupportedOperationException.class,()->document.transcripts().get(0).exons().clear());
        assertThrows(UnsupportedOperationException.class,()->document.transcripts().get(0).declaredTranscript().attributes().clear());
    }
    @Test void everyResourceLimitRejectsInsteadOfReturningATruncatedPopulation() {
        var limits=List.of(new StringTieGtf.Limits(2,100,100,10,10,1000,10000),new StringTieGtf.Limits(100,2,100,10,10,1000,10000),
                new StringTieGtf.Limits(100,100,100,1,10,1000,10000),new StringTieGtf.Limits(100,100,100,10,1,1000,10000),
                new StringTieGtf.Limits(100,100,100,10,10,10,10000),new StringTieGtf.Limits(100,100,100,10,10,1000,10));
        for(var limit:limits)assertThrows(IOException.class,()->StringTieGtf.read(new StringReader(model(true)),REFERENCE,StringTieGtf.Profile.ESTIMATION,limit,()->{}));
        String two=model(true)+model(true).replace("transcript_id \"t\"","transcript_id \"u\"");
        assertThrows(IOException.class,()->StringTieGtf.read(new StringReader(two),REFERENCE,StringTieGtf.Profile.ESTIMATION,new StringTieGtf.Limits(100,100,1,10,10,1000,10000),()->{}));
    }
    @Test void exactCapacityBoundariesAndEmptyResultsAreExplicit() throws Exception {
        String source=model(true);int maximum=source.lines().mapToInt(String::length).max().orElseThrow();
        var result=StringTieGtf.read(new StringReader(source),REFERENCE,StringTieGtf.Profile.ESTIMATION,
                new StringTieGtf.Limits(3,3,1,2,5,maximum,source.length()),()->{});
        assertEquals(source.length(),result.characters());assertTrue(parse("",StringTieGtf.Profile.ASSEMBLY).transcripts().isEmpty());
    }
    @Test void cancellationAndReadFailuresNeverProduceACompletedDocument() {
        assertThrows(CancellationException.class,()->StringTieGtf.read(new StringReader(model(true)),REFERENCE,StringTieGtf.Profile.ESTIMATION,LIMITS,()->{throw new CancellationException("cancel");}));
        var failing=new java.io.Reader(){public int read(char[] b,int o,int n)throws IOException{throw new IOException("read failed");}public void close(){}};
        assertThrows(IOException.class,()->StringTieGtf.read(failing,REFERENCE,StringTieGtf.Profile.ESTIMATION,LIMITS,()->{}));
    }
    @Test void exactReferenceDomainAndInvalidLimitsFailBeforeReading() {
        for(var reference:List.of(Map.<String,Long>of(),Map.of("chrA",0L),Map.of("chrA",2147483648L),Map.of("bad name",10L)))
            assertThrows(IllegalArgumentException.class,()->StringTieGtf.read(new StringReader(model(false)),reference,StringTieGtf.Profile.ANNOTATION,LIMITS,()->{}));
        assertThrows(IllegalArgumentException.class,()->new StringTieGtf.Limits(0,1,1,1,1,1,1));
    }
}
