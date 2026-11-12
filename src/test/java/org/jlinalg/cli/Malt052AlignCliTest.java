/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.Malt052RmaReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Malt052AlignCliTest {
 @TempDir Path temporary;
 @Test void reportedCustomProfileAnd130GResourceRequestAreRepresentable()throws Exception {
  var p=Malt052AlignCli.parameters(Map.of("--index-provenance","native-alternative"));var c=Malt052AlignCli.capacities(Map.of("--heap-mib","133120","--gc-threads","1"));var command=Malt052AlignCli.command(Path.of("provider"),Path.of("reads.fastq.gz"),Path.of("index"),p,c);
  assertEquals("SemiGlobal",p.alignment());assertEquals(90,p.minimumIdentity());assertEquals(25,p.maximumAlignments());assertEquals(100000,p.maximumPerReference());assertEquals(7,p.gapOpen());assertEquals(3,p.gapExtend());assertFalse(command.contains("--lcaCoveragePercent"));assertTrue(command.contains("-Xmx133120m"));assertTrue(command.contains("-XX:ParallelGCThreads=1"));assertEquals("rma",command.get(command.indexOf("--output")+1));assertEquals("sam",command.get(command.indexOf("--alignments")+1));assertEquals("aligned",command.get(command.indexOf("--outAligned")+1));assertEquals("100000",command.get(command.indexOf("--maxAlignmentsPerRef")+1));
 }
 @Test void finiteIdentityPrecisionAndExplicitVersionDefaultsStayDistinct()throws Exception {
  var p=Malt052AlignCli.parameters(Map.of("--minimum-identity","89.6551724137931","--profile","STANDALONE"));assertEquals(89.6551724137931,p.minimumIdentity());assertEquals("Local",p.alignment());assertEquals(1,p.maximumPerReference());assertThrows(IOException.class,()->Malt052AlignCli.parameters(Map.of("--minimum-identity","NaN")));assertThrows(IOException.class,()->Malt052AlignCli.capacities(Map.of("--gc-threads","0")));
 }
 @Test void helpNamesNativeAlternativeAndRequiredRecovery() {
  var bytes=new ByteArrayOutputStream();assertEquals(0,Malt052AlignCli.run(new String[]{"--help"},new PrintStream(bytes),System.err));String text=bytes.toString();assertTrue(text.contains("native-alternative"));assertTrue(text.contains("133120"));assertTrue(text.contains("Required action:"));assertTrue(text.contains("skip dependent alignment"));
 }
 @Test void existingOutputAndNestedSourceOutputRemainUntouched()throws Exception {
  Path db=Files.createDirectory(temporary.resolve("db")),runtime=temporary.resolve("provider"),out=Files.createDirectory(temporary.resolve("prior")),keep=out.resolve("keep.txt");Files.writeString(keep,"prior result");var errors=new ByteArrayOutputStream();var console=new PrintStream(errors);
  for(Path candidate:List.of(out,db.resolve("nested")))assertEquals(2,Malt052AlignCli.run(new String[]{"--runtime",runtime.toString(),"--query",temporary.resolve("reads.fastq").toString(),"--database",db.toString(),"--index-provenance","native-alternative","--out",candidate.toString()},console,console));
  assertEquals("prior result",Files.readString(keep));assertFalse(Files.exists(db.resolve("nested")));try(var rows=Files.list(out)){assertEquals(1,rows.count());}assertTrue(errors.toString().contains("Required action:"));
 }
 @Test void missingAndCorruptReaderProvidersSkipWithExplicitInstructionsAndPreservedInput()throws Exception {
  Path input=temporary.resolve("input.rma6");Files.writeString(input,"retained source bytes");var limits=new Malt052RmaReader.Limits(1000,10,10,10,1000,10);Path provider=temporary.resolve("provider");
  for(int attempt=0;attempt<2;attempt++){if(attempt==1){Files.createDirectories(provider.resolve("jars"));for(String n:List.of("malt.jar","megan.jar","jloda.jar"))Files.writeString(provider.resolve("jars").resolve(n),"not the permitted provider");}var failure=assertThrows(Malt052RmaReader.MissingRuntimeException.class,()->Malt052RmaReader.read(provider,input,limits,row->fail("No provisional row should be emitted")));assertTrue(failure.getMessage().contains("SKIP3"));assertTrue(failure.getMessage().contains("Required action:"));assertTrue(failure.getMessage().contains("skip this dependent RMA analysis"));assertEquals("retained source bytes",Files.readString(input));}
 }
 @Test void duplicateOptionsAndMissingProvenanceCannotStartAnalysis() {
  var errors=new ByteArrayOutputStream();var console=new PrintStream(errors);assertEquals(2,Malt052AlignCli.run(new String[]{"--runtime","a","--runtime","b"},console,console));assertEquals(2,Malt052AlignCli.run(new String[]{"--runtime","a","--query","b","--database","c","--out",temporary.resolve("never").toString()},console,console));assertFalse(Files.exists(temporary.resolve("never")));assertTrue(errors.toString().contains("NO RESULT ADMITTED"));
 }
}
