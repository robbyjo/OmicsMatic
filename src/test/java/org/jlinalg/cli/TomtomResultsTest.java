/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeDnaMotifs;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.TomtomResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TomtomResultsTest {
    @TempDir Path temporary;
    static final String RESOURCE="/org/jlinalg/sequence/tomtom-contracts/original-comparison-contracts.zip";
    static final TomtomResults.Limits LIMITS=new TomtomResults.Limits(new MemeXml.Limits(8_000_000,8_000_000,100000,64,128),8_000_000,100000,100000,1_000_000);
    static Stream<String> ordinary()throws IOException{return cases(archive()).stream().filter(c->c.get("kind").equals("complete-original-contract")).map(c->(String)c.get("id"));}
    @ParameterizedTest @MethodSource("ordinary")
    void completeOriginalPopulationsModelsAndEveryReportedScalarPass(String id)throws Exception {
        Fixture fixture=fixture(id);var result=read(fixture);
        assertEquals(((java.lang.Number)fixture.record().get("complete_reported_rows")).intValue(),result.matches().size());
        long q=fixture.expected().queries().stream().mapToLong(d->d.motifs().size()).sum(),t=fixture.expected().targets().stream().mapToLong(d->d.motifs().size()).sum();
        assertEquals(q*t,result.pairs().size());assertEquals(result.matches().size(),result.pairs().stream().filter(TomtomResults.Pair::reported).count());
        assertTrue(result.printedModels()>=q);assertTrue(result.printedMatrixValues()>=q*4);
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("OUTPUT_CONTRACT_ONLY")));
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("not just reported")));
        for(var match:result.matches()){
            assertTrue(match.pvalue().float32Stage());assertTrue(match.evalue().float32Stage());assertTrue(match.qvalue().float32Stage());
            assertTrue(match.pvalue().lower().compareTo(match.pvalue().upper())<=0);
        }
        if(id.contains("threshold-zero")){assertTrue(result.matches().isEmpty());assertEquals(0,result.pairs().stream().filter(TomtomResults.Pair::reported).count());}
        if(id.contains("duplicate-target")){assertEquals(2,fixture.expected().targets().size());assertEquals(186,result.pairs().size());}
    }
    @Test void originalFractionalSitesKeepTheirReportedValueButUseSourceIntegerSmoothing()throws Exception {
        var f=fixture("edges/ed-all");assertEquals(7.9,f.expected().queries().get(0).motifs().stream().filter(m->m.id().equals("fractional")).findFirst().orElseThrow().sites());
        var audit=read(f);assertTrue(audit.xml().root().one("queries").children().stream().anyMatch(m->m.attributes().get("nsites").equals("7.9")));
    }
    @Test void rawAndProcessedBackgroundsRemainSeparateAndImmutable(){
        double[] raw={.4,.1,.1,.4};var result=TomtomResults.backgrounds(raw,true);assertArrayEquals(raw,result.original());assertEquals(2,result.normalizationPasses());
        assertTrue(result.firstQuery()[0]<.4&&result.firstQuery()[1]>.1);assertTrue(result.followingDatabases()[0]<result.firstQuery()[0]);
        double[] exposed=result.firstQuery();exposed[0]=0;assertTrue(result.firstQuery()[0]>.39);assertArrayEquals(new double[]{.4,.1,.1,.4},raw);
        assertEquals(1,TomtomResults.backgrounds(new double[]{.1,.4,.3,.2},false).normalizationPasses());
    }
    @Test void originalXmlTargetsRetainOnlyReportedTargetsWhilePairLedgerRetainsEveryone()throws Exception {
        var f=fixture("edges/threshold-small");var audit=read(f);assertTrue(audit.matches().size()<audit.pairs().size());
        int allTargets=f.expected().targets().stream().mapToInt(d->d.motifs().size()).sum();assertTrue(audit.xml().root().one("targets").children().size()<allTargets);
        assertTrue(audit.pairs().stream().anyMatch(p->!p.reported()));assertTrue(audit.warnings().stream().anyMatch(s->s.startsWith("UNREPORTED PAIRS")));
    }
    @Test void bothOriginalFilesCanAgreeAndStillFailTheIndependentEArithmetic()throws Exception {
        var f=fixture("base/tomtom-ed-both");String xml=Files.readString(f.xml());Files.writeString(f.xml(),xml.replaceFirst("pv=\"[^\"]+\"","pv=\"1.00e-01\""));
        List<String> lines=Files.readAllLines(f.tsv());String[] row=lines.get(1).split("\t",-1);row[3]="0.1";lines.set(1,String.join("\t",row));Files.writeString(f.tsv(),String.join("\n",lines)+"\n");
        IOException invalid=assertThrows(IOException.class,()->read(f));assertTrue(invalid.getMessage().contains("E does not agree"),invalid.getMessage());
    }
    @Test void changedOriginalMatrixAndMissingLastRowCannotReturnACompletePrefix()throws Exception {
        var f=fixture("base/tomtom-kullback-both");String xml=Files.readString(f.xml());Files.writeString(f.xml(),xml.replaceFirst("0.0102996","0.1102996"));
        assertThrows(IOException.class,()->read(f));Files.writeString(f.xml(),xml);
        List<String> rows=Files.readAllLines(f.tsv());rows.remove(9);Files.writeString(f.tsv(),String.join("\n",rows)+"\n");assertThrows(IOException.class,()->read(f));
    }
    @Test void sourceFloatConsensusAndUnequalWidthGeometryAreCheckedIndependently()throws Exception {
        var f=fixture("edges/pseudo-zero");var audit=read(f);assertTrue(audit.matches().stream().anyMatch(m->m.offset()!=0));
        List<String> rows=Files.readAllLines(f.tsv());String[] row=rows.get(1).split("\t",-1);row[7]="N".repeat(row[7].length());rows.set(1,String.join("\t",row));Files.writeString(f.tsv(),String.join("\n",rows)+"\n");
        assertTrue(assertThrows(IOException.class,()->read(f)).getMessage().contains("consensus"));
    }
    @Test void lateFooterDataInvalidProbabilityAndTruncatedTerminalLfFail()throws Exception {
        var f=fixture("base/tomtom-ed-forward");String text=Files.readString(f.tsv());
        Files.writeString(f.tsv(),text+text.lines().skip(1).findFirst().orElseThrow()+"\n");assertThrows(IOException.class,()->read(f));
        Files.writeString(f.tsv(),text.substring(0,text.length()-1));assertThrows(IOException.class,()->read(f));
        List<String> rows=text.lines().collect(java.util.stream.Collectors.toCollection(ArrayList::new));String[] row=rows.get(1).split("\t",-1);row[3]="NaN";rows.set(1,String.join("\t",row));Files.writeString(f.tsv(),String.join("\n",rows)+"\n");assertThrows(IOException.class,()->read(f));
    }
    @Test void completePairMatrixRowAndRawCapacitiesRejectBeforeReturningPartialEvidence()throws Exception {
        var f=fixture("edges/ed-all");
        for(var limits:List.of(new TomtomResults.Limits(LIMITS.xml(),8_000_000,100000,100,1_000_000),new TomtomResults.Limits(LIMITS.xml(),8_000_000,100000,100000,4),new TomtomResults.Limits(LIMITS.xml(),8_000_000,1,100000,1_000_000),new TomtomResults.Limits(LIMITS.xml(),10,100000,100000,1_000_000)))
            assertThrows(IOException.class,()->TomtomResults.read(f.xml(),f.tsv(),f.expected(),limits,()->{}));
    }
    @Test void cancellationDoesNotPublishAStagedMatchList()throws Exception {
        var f=fixture("edges/ed-all");var calls=new AtomicInteger();assertThrows(CancellationException.class,()->TomtomResults.read(f.xml(),f.tsv(),f.expected(),LIMITS,()->{if(calls.incrementAndGet()==500)throw new CancellationException("controlled");}));
    }
    @Test void knownOriginalAllrCrashAndSourceExclusionsAreRejectedBeforeOpeningOutputs()throws Exception {
        for(String id:List.of("edges/zero-excluded-allr","single/mixed-zero-allr")){
            var f=fixture(id);assertThrows(TomtomResults.UnsupportedInput.class,()->TomtomResults.read(temporary.resolve("not-opened"),temporary.resolve("not-opened-tsv"),f.expected(),LIMITS,()->{}));
        }
    }
    @Test void originalSubunitSiteCountAssertionBecomesAnExplicitInputFailure()throws Exception {
        Map<String,byte[]> all=archive();Map<?,?> row=caseById(all,"edges/subunit-site-count");assertEquals(-6,((java.lang.Number)row.get("exit_code")).intValue());
        assertTrue(new String(all.get("edges/subunit-site-count/stderr.log"),StandardCharsets.UTF_8).contains("site_count > 0"));
        assertThrows(IllegalArgumentException.class,()->new TomtomResults.Motif("subunit","",.5,new double[][]{{.7,.1,.1,.1}},BigDecimal.ZERO,""));
    }
    @Test void textModeSuccessDoesNotBecomeAnXmlContract()throws Exception {
        Map<String,byte[]> all=archive();Map<?,?> row=caseById(all,"edges/paper-text");assertEquals(0,((java.lang.Number)row.get("exit_code")).intValue());
        assertNull(all.get("edges/paper-text/result/tomtom.xml"));assertTrue(new String(all.get("edges/paper-text/stdout.log"),StandardCharsets.UTF_8).startsWith("Query_ID\t"));
    }
    private static TomtomResults.Audit read(Fixture f)throws IOException{return TomtomResults.read(f.xml(),f.tsv(),f.expected(),LIMITS,()->{});}
    private record Fixture(Path xml,Path tsv,TomtomResults.Expected expected,Map<?,?> record) { }
    private Fixture fixture(String id)throws Exception {
        Map<String,byte[]> all=archive();Map<?,?> row=caseById(all,id),options=(Map<?,?>)row.get("expected");
        Path folder=temporary.resolve(id.replace('/','-'));Files.createDirectories(folder);
        Path xml=folder.resolve("tomtom.xml"),tsv=folder.resolve("tomtom.tsv");if(all.containsKey(id+"/result/tomtom.xml"))Files.write(xml,all.get(id+"/result/tomtom.xml"));if(all.containsKey(id+"/result/tomtom.tsv"))Files.write(tsv,all.get(id+"/result/tomtom.tsv"));
        double[] raw=((List<?>)options.get("raw_background")).stream().mapToDouble(n->((java.lang.Number)n).doubleValue()).toArray();var bg=TomtomResults.backgrounds(raw,(Boolean)options.get("background_from_query"));
        String query=(String)options.get("query_input");var qdb=database(all,folder,query,(String)options.get("query_source"),bg.firstQuery());var targets=new ArrayList<TomtomResults.Database>();
        List<?> targetInputs=(List<?>)options.get("target_inputs"),targetSources=(List<?>)options.get("target_sources");
        for(int i=0;i<targetInputs.size();i++)targets.add(database(all,folder,(String)targetInputs.get(i),(String)targetSources.get(i),bg.followingDatabases()));
        var expected=new TomtomResults.Expected((String)options.get("command_line"),(String)options.get("distance"),(Boolean)options.get("reverse"),(String)options.get("threshold_kind"),num(options,"threshold"),num(options,"pseudo"),(int)num(options,"min_overlap"),(Boolean)options.get("internal"),(String)options.get("background_source"),bg.firstQuery(),List.of(qdb),targets);
        return new Fixture(xml,tsv,expected,row);
    }
    private static TomtomResults.Database database(Map<String,byte[]> all,Path folder,String name,String source,double[] bg)throws IOException {
        Path input=folder.resolve(name.replace('/','-'));Files.write(input,all.get(name));var doc=MemeDnaMotifs.read(input,false);
        var models=doc.motifs().stream().map(m->new TomtomResults.Motif(m.id(),m.alternateId(),m.sites(),m.frequencies(),BigDecimal.ZERO,"")).toList();
        String display=name.substring(name.lastIndexOf('/')+1);if(display.endsWith(".meme"))display=display.substring(0,display.length()-5);
        return new TomtomResults.Database(source,display,models,bg);
    }
    private static double num(Map<?,?> row,String key){return ((java.lang.Number)row.get(key)).doubleValue();}
    private static Map<String,byte[]> archive()throws IOException{
        byte[] raw;try(var input=TomtomResultsTest.class.getResourceAsStream(RESOURCE)){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals("62a6e0090ab1a6cbddc4ef2f0bbb5936863a04601efd66e27ba686bab6c7b765",NextflowFiles.sha(raw));
        var all=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertNull(all.put(e.getName(),z.readAllBytes()));}}return all;
    }
    private static List<Map<?,?>> cases(Map<String,byte[]> all)throws IOException {
        var doc=(Map<?,?>)SimpleJson.parse(new String(all.get("EXPECTED.json"),StandardCharsets.UTF_8));var result=new ArrayList<Map<?,?>>();for(Object row:(List<?>)doc.get("cases"))result.add((Map<?,?>)row);return List.copyOf(result);
    }
    private static Map<?,?> caseById(Map<String,byte[]> all,String id)throws IOException{return cases(all).stream().filter(v->v.get("id").equals(id)).findFirst().orElseThrow();}
}
