/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
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

class StringTieTablesTest {
    private static final String SHA = "ef1939713a3356d8a49ee7c784c4b19940b446e932acfd8a4476618ac44df71a";
    private static final String BASE = "baseline/3.0.3/estimate";
    private static final StringTieGtf.Limits GTF_LIMITS = new StringTieGtf.Limits(10000,10000,1000,100,100,16384,1048576);
    private static final StringTieTables.Limits LIMITS = new StringTieTables.Limits(10000,16384,1048576,1000,1000000);
    private static final List<String> FILES = List.of("genes","t_data","e_data","i_data","e2t","i2t");
    private static final Map<String,String> SOURCE = fixtures();

    private static Map<String,String> fixtures() {
        try {
            byte[] raw;
            try (var input=StringTieTablesTest.class.getResourceAsStream("/org/jlinalg/sequence/stringtie-tables/original-table-controls.zip")) {
                assertNotNull(input);raw=input.readAllBytes();
            }
            assertEquals(SHA,java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var entries=new HashMap<String,String>();
            try (var zip=new ZipInputStream(new ByteArrayInputStream(raw))) {
                java.util.zip.ZipEntry entry;
                while ((entry=zip.getNextEntry())!=null) {
                    assertFalse(entry.isDirectory());assertFalse(entry.getName().contains(".."));
                    byte[] value=zip.readNBytes(1048577);assertTrue(value.length<=1048576);
                    assertNull(entries.put(entry.getName(),new String(value,StandardCharsets.UTF_8)));
                }
            }
            assertEquals(421,entries.size());return Map.copyOf(entries);
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    private static String source(String path) { assertTrue(SOURCE.containsKey(path),path);return SOURCE.get(path); }
    private static Map<String,String> family(String path) {
        var result=new HashMap<String,String>();result.put("guide",source(path+"/guide.gtf"));result.put("gtf",source(path+"/transcripts.gtf"));
        result.put("genes",source(path+(path.startsWith("baseline/")?"/gene-abundance.tsv":"/genes.tsv")));
        for (String name:FILES.subList(1,FILES.size()))result.put(name,source(path+"/"+name+".ctab"));return result;
    }
    private static StringTieGtf.Document gtf(String text,StringTieGtf.Profile profile) throws IOException {
        return StringTieGtf.read(new StringReader(text),Map.of("chrA",12000L,"chrB",10000L),profile,GTF_LIMITS,()->{});
    }
    private static StringTieTables.Audit audit(Map<String,String> input,StringTieGtf.PopulationPolicy policy,StringTieTables.Limits limits,Runnable checkpoint) throws IOException {
        return StringTieTables.auditEstimation(new StringTieTables.Inputs(new StringReader(input.get("genes")),new StringReader(input.get("t_data")),
                new StringReader(input.get("e_data")),new StringReader(input.get("i_data")),new StringReader(input.get("e2t")),new StringReader(input.get("i2t"))),
                gtf(input.get("guide"),StringTieGtf.Profile.ANNOTATION),gtf(input.get("gtf"),StringTieGtf.Profile.ESTIMATION),policy,limits,checkpoint);
    }
    private static StringTieTables.Audit audit(Map<String,String> input) throws IOException { return audit(input,StringTieGtf.PopulationPolicy.COMPLETE,LIMITS,()->{}); }
    private static void change(Map<String,String> family,String table,int row,int column,String value) {
        String[] lines=family.get(table).split("\n",-1),cells=lines[row].split("\t",-1);cells[column]=value;lines[row]=String.join("\t",cells);family.put(table,String.join("\n",lines));
    }
    static Stream<String> originalFamilies() {
        return Stream.of("1.3.0","1.3.6","2.1.2","2.1.7","2.2.1","3.0.0","3.0.3").flatMap(v->Stream.of("baseline/"+v+"/estimate","baseline/"+v+"/empty-estimate",
                "boundaries/"+v+"/unknown_first","boundaries/"+v+"/known_first","wildcard/"+v));
    }
    @ParameterizedTest @MethodSource("originalFamilies")
    void allThirtyFiveOriginalFamiliesRetainEverySourceLineAndCompleteTopology(String path) throws Exception {
        var input=family(path);var result=audit(input);assertEquals(4,result.transcripts().size());assertEquals(4,result.reference().emitted());assertEquals(0,result.reference().omitted());
        assertEquals(FILES,result.tables().stream().map(StringTieTables.Table::name).toList());
        long characters=0;
        for(var table:result.tables()) {
            String raw=input.get(table.name());assertEquals(raw,table.lines().stream().map(l->l.text()+l.ending()).collect(java.util.stream.Collectors.joining()));characters+=raw.length();
            for(var line:table.lines())assertEquals(line.text(),String.join("\t",line.cells()));
        }
        assertEquals(characters,result.characters());assertTrue(result.topologyWork()>0);
        if(path.startsWith("baseline")) {
            assertEquals(7,result.exons().size());assertEquals(2,result.introns().size());assertEquals(7,result.exonLinks().size());assertEquals(3,result.intronLinks().size());assertEquals(0,result.wildcardLinks());assertEquals(3,result.genes().size());
        } else if(path.startsWith("boundaries")) {
            assertEquals(6,result.exons().size());assertEquals(3,result.introns().size());assertEquals(8,result.exonLinks().size());assertEquals(4,result.intronLinks().size());assertEquals(3,result.wildcardLinks());assertEquals('-',result.exons().get(0).strand());
        } else {
            assertEquals(6,result.exons().size());assertEquals(4,result.introns().size());assertEquals(10,result.exonLinks().size());assertEquals(6,result.intronLinks().size());assertEquals(6,result.wildcardLinks());assertEquals('.',result.exons().get(0).strand());
        }
        for(var e:result.exons())assertEquals(4,e.coverageMoments().size());for(var e:result.introns())assertTrue(e.coverageMoments().isEmpty());
        for(var t:result.transcripts())assertEquals(new BigDecimal(t.coverage().text()),t.coverage().value());
        for(var relation:result.geneRelations())assertEquals(0,relation.omittedGtfTranscripts());
        if(path.contains("empty-estimate")) {
            assertTrue(result.transcripts().stream().allMatch(t->t.coverage().value().signum()==0&&t.fpkm().value().signum()==0));
            assertTrue(result.exons().stream().allMatch(e->e.readCount()==0&&e.coverageMoments().stream().allMatch(v->v.value().signum()==0)));
        }
    }
    @Test void unknownStrandSharingDoesNotReassignFeatureStrandsOrCrossChromosomeBoundaries() throws Exception {
        var result=audit(family("boundaries/3.0.3/unknown_first"));
        assertEquals("t.unknown",result.transcripts().get(2).transcriptId());assertEquals('.',result.transcripts().get(2).strand());
        assertEquals('-',result.exons().get(0).strand());assertTrue(result.exonLinks().contains(new StringTieTables.Link(1,3)));
        assertEquals("chrB",result.exons().get(4).contig());assertEquals(101,result.exons().get(4).start1());
        assertEquals(101,result.exons().get(0).start1());assertNotEquals(result.exons().get(0).id(),result.exons().get(4).id());
    }
    @Test void retainedUnknownRepresentativeAndItsZeroCountsRemainOriginalSourceResults() throws Exception {
        var result=audit(family("wildcard/3.0.3"));assertEquals('.',result.exons().get(0).strand());
        assertEquals(List.of(new StringTieTables.Link(1,1),new StringTieTables.Link(1,2),new StringTieTables.Link(1,3)),result.exonLinks().subList(0,3));
        assertEquals(0,result.exons().get(0).readCount());assertEquals("0.0000",result.exons().get(0).coverageMoments().get(0).text());
    }
    @Test void geneCoverageIsLengthWeightedOverUnionExonsAndNotSumOfTranscriptCoverage() throws Exception {
        var result=audit(family(BASE));var gene=result.genes().get(0);var a=result.transcripts().get(0);var b=result.transcripts().get(1);
        assertEquals("46.666667",gene.coverage().text());assertNotEquals(0,gene.coverage().value().compareTo(a.coverage().value().add(b.coverage().value())));
        var relation=result.geneRelations().get(0);assertEquals(300,relation.unionExonLength());assertEquals(2,relation.emittedGtfTranscripts());
        assertEquals(gene.coverage().value().multiply(new BigDecimal(300)).subtract(a.coverage().value().multiply(new BigDecimal(300)).add(b.coverage().value().multiply(new BigDecimal(250)))),relation.weightedCoverageResidual());
        assertNotEquals(0,relation.weightedCoverageResidual().signum());
    }
    @Test void originalTpmIsNotForcedToOneMillion() throws Exception {
        var result=audit(family("boundaries/3.0.3/unknown_first"));
        assertEquals("1000000.007143",result.genes().stream().filter(g->g.id().equals("g.plus")).findFirst().orElseThrow().tpm().text());
    }
    static Stream<Arguments> mutations() {
        return Stream.of(
                Arguments.of("t_data",1,0,"2"),Arguments.of("t_data",1,5,"not-reference"),Arguments.of("t_data",1,8,"gb"),
                Arguments.of("t_data",1,9,"wrong"),Arguments.of("t_data",1,1,"chrB"),Arguments.of("t_data",1,2,"."),
                Arguments.of("t_data",1,3,"0"),Arguments.of("t_data",1,4,"551"),Arguments.of("t_data",1,6,"3"),Arguments.of("t_data",1,7,"299"),
                Arguments.of("t_data",1,10,"NaN"),Arguments.of("t_data",1,10,"-1.000000"),Arguments.of("t_data",1,10,"1.0"),Arguments.of("t_data",1,11,"1e3"),
                Arguments.of("e_data",1,0,"2"),Arguments.of("e_data",1,1,"chrB"),Arguments.of("e_data",1,2,"."),Arguments.of("e_data",1,3,"102"),
                Arguments.of("e_data",1,5,"4294967296"),Arguments.of("e_data",1,6,"161"),Arguments.of("e_data",1,7,"160.01"),
                Arguments.of("e_data",1,7,"159.99"),Arguments.of("e_data",1,7,"160.0"),Arguments.of("e_data",1,8,"NaN"),
                Arguments.of("e_data",1,9,"-0.0001"),Arguments.of("e_data",1,10,"1.000"),Arguments.of("e_data",1,11,"Infinity"),
                Arguments.of("i_data",1,0,"2"),Arguments.of("i_data",1,3,"250"),Arguments.of("i_data",1,6,"41"),Arguments.of("i_data",1,7,"40.1"),
                Arguments.of("e2t",1,0,"2"),Arguments.of("e2t",1,1,"2"),Arguments.of("i2t",1,1,"2"),
                Arguments.of("genes",1,0,"unknown"),Arguments.of("genes",1,1,"."),Arguments.of("genes",1,2,"chrB"),
                Arguments.of("genes",1,3,"."),Arguments.of("genes",1,4,"102"),Arguments.of("genes",1,5,"551"),
                Arguments.of("genes",1,6,"NaN"),Arguments.of("genes",1,7,"-1.000000"),Arguments.of("genes",1,8,"1.0"));
    }
    @ParameterizedTest @MethodSource("mutations")
    void invalidIdentityTopologyCountsAndDecimalDomainsRejectCompleteFamily(String table,int row,int column,String value) {
        var input=family(BASE);change(input,table,row,column,value);assertThrows(IOException.class,()->audit(input));
    }
    @ParameterizedTest @ValueSource(strings={"genes","t_data","e_data","i_data","e2t","i2t"})
    void missingExtraReorderedOrMalformedTablesAreNeverTruncatedOrIgnored(String name) {
        for(int mutation=0;mutation<6;mutation++) {
            if (name.equals("genes")&&mutation==5)continue; // Gene rows have identities, not ordinal IDs; their original order is retained.
            var input=family(BASE);String raw=input.get(name);String[] lines=raw.split("\n",-1);
            String bad=switch(mutation){case 0->"";case 1->raw+lines[1]+"\n";case 2->raw.substring(raw.indexOf('\n')+1);case 3->raw+"\n";
                case 4->lines[0]+"\n"+String.join("\n",java.util.Arrays.copyOfRange(lines,2,lines.length));
                case 5->lines[0]+"\n"+lines[2]+"\n"+lines[1]+"\n"+String.join("\n",java.util.Arrays.copyOfRange(lines,3,lines.length));default->throw new AssertionError();};
            input.put(name,bad);assertThrows(IOException.class,()->audit(input),name+" mutation "+mutation);
        }
    }
    @Test void geneRowsAreMatchedByIdentityAndTheirOriginalOrderIsNeverSilentlyChanged() throws Exception {
        var input=family(BASE);String[] lines=input.get("genes").split("\n",-1);
        input.put("genes",lines[0]+"\n"+lines[2]+"\n"+lines[1]+"\n"+String.join("\n",java.util.Arrays.copyOfRange(lines,3,lines.length)));
        var result=audit(input);assertEquals(List.of("gb","ga","gc"),result.genes().stream().map(StringTieTables.GeneRow::id).toList());
        assertEquals(input.get("genes"),result.tables().get(0).lines().stream().map(l->l.text()+l.ending()).collect(java.util.stream.Collectors.joining()));
    }
    @Test void retainedGtfOmissionIsExplicitWhileBallgownReferencePopulationStaysComplete() throws Exception {
        var input=family(BASE);input.put("gtf",input.get("gtf").lines().filter(l->!l.contains("tc.uncovered")).collect(java.util.stream.Collectors.joining("\n","","\n")));
        assertThrows(IOException.class,()->audit(input));var result=audit(input,StringTieGtf.PopulationPolicy.RETAIN_OMISSIONS,LIMITS,()->{});
        assertEquals(1,result.reference().omitted());assertEquals(4,result.transcripts().size());var gene=result.geneRelations().stream().filter(r->r.geneId().equals("gc")).findFirst().orElseThrow();
        assertEquals(0,gene.emittedGtfTranscripts());assertEquals(1,gene.omittedGtfTranscripts());
        input.put("t_data",input.get("t_data").lines().filter(l->!l.contains("tc.uncovered")).collect(java.util.stream.Collectors.joining("\n","","\n")));
        assertThrows(IOException.class,()->audit(input,StringTieGtf.PopulationPolicy.RETAIN_OMISSIONS,LIMITS,()->{}));
    }
    @Test void structuralAuditPreservesNumericalDisagreementForTheSeparateInferenceGate() throws Exception {
        var input=family(BASE);input.put("gtf",input.get("gtf").replace("216849.625203","316849.625203"));var result=audit(input);
        assertTrue(result.geneRelations().get(0).printedTpmResidual().abs().compareTo(new BigDecimal(99999))>0);
    }
    @Test void completeCrlfAndMissingTerminalNewlinesRoundTripWithoutChangingCells() throws Exception {
        var input=family(BASE);for(String key:FILES) { String raw=input.get(key);input.put(key,raw.substring(0,raw.length()-1).replace("\n","\r\n")); }
        var result=audit(input);for(var t:result.tables())assertEquals(input.get(t.name()),t.lines().stream().map(l->l.text()+l.ending()).collect(java.util.stream.Collectors.joining()));
    }
    @ParameterizedTest @ValueSource(strings={"\r","\u0000","\uD800"})
    void malformedLineEndingsControlsAndUnicodeAreRejected(String suffix) {
        var input=family(BASE);input.put("genes",input.get("genes")+suffix);assertThrows(IOException.class,()->audit(input));
    }
    @Test void everyCompleteResourceBudgetHasAnExactBoundaryAndNeverReturnsPartialTables() throws Exception {
        var input=family(BASE);var full=audit(input);long rows=full.tables().stream().mapToLong(t->t.lines().size()).sum();
        int line=full.tables().stream().flatMap(t->t.lines().stream()).mapToInt(l->l.text().length()).max().orElseThrow();
        long chars=full.characters(),work=full.topologyWork();int features=full.exons().size()+full.introns().size();
        var exact=new StringTieTables.Limits(rows,line,chars,features,work);assertEquals(chars,audit(input,StringTieGtf.PopulationPolicy.COMPLETE,exact,()->{}).characters());
        for(var reduced:List.of(new StringTieTables.Limits(rows-1,line,chars,features,work),new StringTieTables.Limits(rows,line-1,chars,features,work),
                new StringTieTables.Limits(rows,line,chars-1,features,work),new StringTieTables.Limits(rows,line,chars,features-1,work),
                new StringTieTables.Limits(rows,line,chars,features,work-1)))assertThrows(IOException.class,()->audit(input,StringTieGtf.PopulationPolicy.COMPLETE,reduced,()->{}));
    }
    @Test void cancellationAndReaderErrorsPropagateWithoutAnAdmittedResult() throws Exception {
        assertThrows(CancellationException.class,()->audit(family(BASE),StringTieGtf.PopulationPolicy.COMPLETE,LIMITS,()->{throw new CancellationException();}));
        var failed=new java.io.Reader(){public int read(char[] b,int o,int n)throws IOException{throw new IOException("read error");}public void close(){}};
        var family=family(BASE);var empty=new StringReader("");
        assertThrows(IOException.class,()->StringTieTables.auditEstimation(new StringTieTables.Inputs(failed,empty,empty,empty,empty,empty),gtf(family.get("guide"),StringTieGtf.Profile.ANNOTATION),
                gtf(family.get("gtf"),StringTieGtf.Profile.ESTIMATION),StringTieGtf.PopulationPolicy.COMPLETE,LIMITS,()->{}));
    }
    @Test void cancellationDuringSourceTopologyReplayNeverProducesAnAdmittedResult() {
        Runnable cancel=()->{if(java.util.Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(f->f.getClassName().contains("StringTieTables$Topology")))throw new CancellationException("during topology");};
        assertThrows(CancellationException.class,()->audit(family(BASE),StringTieGtf.PopulationPolicy.COMPLETE,LIMITS,cancel));
    }
    @Test void checkedResultPopulationsAreImmutableAndPartialInputFamiliesFailAtConstruction() throws Exception {
        var result=audit(family(BASE));assertThrows(UnsupportedOperationException.class,()->result.tables().clear());assertThrows(UnsupportedOperationException.class,()->result.exons().clear());
        assertThrows(UnsupportedOperationException.class,()->result.tables().get(0).lines().clear());assertThrows(UnsupportedOperationException.class,()->result.tables().get(0).lines().get(0).cells().clear());
        assertThrows(UnsupportedOperationException.class,()->result.exons().get(0).coverageMoments().clear());
        assertThrows(NullPointerException.class,()->new StringTieTables.Inputs(null,new StringReader(""),new StringReader(""),new StringReader(""),new StringReader(""),new StringReader("")));
        assertThrows(IllegalArgumentException.class,()->new StringTieTables.Limits(5,1,1,1,1));
    }
}
