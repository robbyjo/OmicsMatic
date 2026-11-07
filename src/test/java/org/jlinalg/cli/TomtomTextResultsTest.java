/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
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
import org.jlinalg.sequence.MemeMotifInputs;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.TomtomResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TomtomTextResultsTest {
    @TempDir Path temporary;
    static final TomtomResults.Limits LIMITS=new TomtomResults.Limits(new MemeXml.Limits(8_000_000,8_000_000,100000,64,128),8_000_000,100000,100000,1_000_000);
    static Stream<String> complete()throws IOException {return records(archive()).stream().filter(r->r.get("kind").equals("complete-original-contract")&&!r.get("id").equals("edges/duplicate-target-databases")||r.get("id").equals("edges/paper-text")).map(r->(String)r.get("id"));}
    @ParameterizedTest @MethodSource("complete")
    void completeOriginalTextRowsAndIndependentInputsPassWithoutXml(String id)throws Exception {
        var fixture=fixture(id);var audit=read(fixture);long queries=fixture.expected().queries().stream().mapToLong(d->d.motifs().size()).sum(),targets=fixture.expected().targets().stream().mapToLong(d->d.motifs().size()).sum();
        assertEquals(queries*targets,audit.pairs().size());assertEquals(audit.matches().size(),audit.pairs().stream().filter(TomtomResults.Pair::reported).count());assertEquals(Files.size(fixture.table()),audit.bytes());assertEquals(NextflowFiles.sha(Files.readAllBytes(fixture.table())),audit.sha256());
        if(fixture.record().get("complete_reported_rows")!=null)assertEquals(((Number)fixture.record().get("complete_reported_rows")).intValue(),audit.matches().size());
        else assertEquals(Files.readAllLines(fixture.table()).stream().skip(1).filter(line->!line.isBlank()&&!line.startsWith("#")).count(),audit.matches().size());
        assertTrue(audit.warnings().stream().anyMatch(s->s.contains("No full-report run or synthetic XML")));assertTrue(audit.warnings().stream().anyMatch(s->s.contains("ORIGINAL_Q_FAMILY")));
        assertTrue(audit.matches().stream().allMatch(m->m.pvalue().float32Stage()&&m.evalue().float32Stage()&&m.qvalue().float32Stage()));
        if(id.equals("edges/paper-text"))assertTrue(fixture.expected().commandLine().contains("-text"));
    }
    @Test void duplicateDatabaseIdsAreExplicitlyUnsupportedBeforeOpeningText()throws Exception {
        var f=fixture("edges/duplicate-target-databases");assertTrue(assertThrows(TomtomResults.UnsupportedInput.class,()->TomtomResults.readText(temporary.resolve("not-opened"),f.expected(),LIMITS,()->{})).getMessage().contains("lack database indices"));
    }
    @Test void independentEArithmeticConsensusAndGeometryCannotBeOverriddenByText()throws Exception {
        var f=fixture("edges/ed-all");String original=Files.readString(f.table());var lines=new ArrayList<>(original.lines().toList());
        for(int column:List.of(0,3,6,7,8,9)){
            String[] row=lines.get(1).split("\t",-1);row[column]=switch(column){case 0->"unknown";case 3->"0.5";case 6->"512";case 7,8->"NNNNNN";default->"?";};var changed=new ArrayList<>(lines);changed.set(1,String.join("\t",row));Files.writeString(f.table(),String.join("\n",changed)+"\n");assertThrows(IOException.class,()->read(f),"column "+column);
        }
        Files.writeString(f.table(),original);assertEquals(366,read(f).matches().size());
    }
    @Test void omittedUnfilteredPairsDuplicateRowsAndLateRowsDoNotAdmitPrefixes()throws Exception {
        var f=fixture("edges/ed-all");String original=Files.readString(f.table());var lines=new ArrayList<>(original.lines().toList());var missing=new ArrayList<>(lines);missing.remove(1);Files.writeString(f.table(),String.join("\n",missing)+"\n");assertThrows(IOException.class,()->read(f));
        var repeated=new ArrayList<>(lines);repeated.add(1,lines.get(1));Files.writeString(f.table(),String.join("\n",repeated)+"\n");assertThrows(IOException.class,()->read(f));
        Files.writeString(f.table(),original+lines.get(1)+"\n");assertThrows(IOException.class,()->read(f));
    }
    @Test void truncatedFooterWrongVersionAndChangedCommandCannotComplete()throws Exception {
        var f=fixture("edges/paper-text");String original=Files.readString(f.table());
        for(String changed:List.of(original.substring(0,original.length()-1),original.replace("Version 5.5.9","Version 5.5.4"),original.replace("# tomtom ","# altered "))){Files.writeString(f.table(),changed);assertThrows(IOException.class,()->read(f));}
    }
    @Test void allWholePopulationAndRawCapacitiesFailWithoutPartialReturn()throws Exception {
        var f=fixture("edges/ed-all");for(var limits:List.of(new TomtomResults.Limits(LIMITS.xml(),8_000_000,100000,100,1_000_000),new TomtomResults.Limits(LIMITS.xml(),8_000_000,100000,100000,4),new TomtomResults.Limits(LIMITS.xml(),8_000_000,1,100000,1_000_000),new TomtomResults.Limits(LIMITS.xml(),10,100000,100000,1_000_000)))assertThrows(IOException.class,()->TomtomResults.readText(f.table(),f.expected(),limits,()->{}));
    }
    @Test void lateSameLengthMutationAndCancellationCannotPublishACompleteTextReceipt()throws Exception {
        var f=fixture("edges/ed-all");String original=Files.readString(f.table());var time=Files.getLastModifiedTime(f.table());var calls=new AtomicInteger();
        assertThrows(IOException.class,()->TomtomResults.readText(f.table(),f.expected(),LIMITS,()->{if(calls.incrementAndGet()==800)try{Files.writeString(f.table(),original.replace("compiled on","compiled XX"));Files.setLastModifiedTime(f.table(),time);}catch(IOException e){throw new java.io.UncheckedIOException(e);}}));
        Files.writeString(f.table(),original);calls.set(0);assertThrows(CancellationException.class,()->TomtomResults.readText(f.table(),f.expected(),LIMITS,()->{if(calls.incrementAndGet()==800)throw new CancellationException("controlled");}));
    }
    private static TomtomResults.TextAudit read(Fixture f)throws IOException{return TomtomResults.readText(f.table(),f.expected(),LIMITS,()->{});}
    private record Fixture(Path table,TomtomResults.Expected expected,Map<?,?> record) { }
    private Fixture fixture(String id)throws Exception {
        var files=archive();var row=records(files).stream().filter(r->r.get("id").equals(id)).findFirst().orElseThrow();var options=(Map<?,?>)row.get("expected");
        double[] raw=((List<?>)options.get("raw_background")).stream().mapToDouble(n->((Number)n).doubleValue()).toArray();var bg=TomtomResults.backgrounds(raw,(Boolean)options.get("background_from_query"));
        var q=database(files,(String)options.get("query_input"),(String)options.get("query_source"),bg.firstQuery());var targets=new ArrayList<TomtomResults.Database>();var inputs=(List<?>)options.get("target_inputs");var sources=(List<?>)options.get("target_sources");
        for(int i=0;i<inputs.size();i++)targets.add(database(files,(String)inputs.get(i),(String)sources.get(i),bg.followingDatabases()));
        var expected=new TomtomResults.Expected((String)options.get("command_line"),(String)options.get("distance"),(Boolean)options.get("reverse"),(String)options.get("threshold_kind"),num(options,"threshold"),num(options,"pseudo"),(int)num(options,"min_overlap"),(Boolean)options.get("internal"),(String)options.get("background_source"),bg.firstQuery(),List.of(q),targets);
        Path table=temporary.resolve(id.replace('/','-')+".tsv");Files.write(table,files.get(id+(id.equals("edges/paper-text")?"/stdout.log":"/result/tomtom.tsv")));return new Fixture(table,expected,row);
    }
    private TomtomResults.Database database(Map<String,byte[]> files,String input,String source,double[] bg)throws IOException {
        Path path=temporary.resolve(input.replace('/','-'));Files.write(path,files.get(input));var doc=MemeMotifInputs.read(path,new MemeMotifInputs.Limits(8_000_000,8_000_000,10000,512,1_000_000,LIMITS.xml()),()->{});String name=source.substring(source.lastIndexOf('/')+1);if(name.endsWith(".meme"))name=name.substring(0,name.length()-5);
        return new TomtomResults.Database(source,name,doc.entries().stream().map(MemeMotifInputs.Entry::model).toList(),bg);
    }
    private static double num(Map<?,?> row,String key){return ((Number)row.get(key)).doubleValue();}
    private static Map<String,byte[]> archive()throws IOException {
        byte[] raw;try(var in=TomtomTextResultsTest.class.getResourceAsStream("/org/jlinalg/sequence/tomtom-contracts/original-comparison-contracts.zip")){assertNotNull(in);raw=in.readAllBytes();}assertEquals("62a6e0090ab1a6cbddc4ef2f0bbb5936863a04601efd66e27ba686bab6c7b765",NextflowFiles.sha(raw));var files=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertNull(files.put(e.getName(),z.readAllBytes()));}}return files;
    }
    private static List<Map<?,?>> records(Map<String,byte[]> files)throws IOException {var doc=(Map<?,?>)SimpleJson.parse(new String(files.get("EXPECTED.json"),StandardCharsets.UTF_8));var rows=new ArrayList<Map<?,?>>();for(Object value:(List<?>)doc.get("cases"))rows.add((Map<?,?>)value);return List.copyOf(rows);}
}
