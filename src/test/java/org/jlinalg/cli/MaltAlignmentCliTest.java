/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.jlinalg.metagenomics.MaltAlignmentAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaltAlignmentCliTest {
 @TempDir Path temporary;
 private static final MaltAlignmentAudit.Limits LIMITS=new MaltAlignmentAudit.Limits(1000000,100000000,10000000,1000000,1000000000);
 private Path fixture()throws Exception {
  Path root=temporary.resolve("fixture");Files.createDirectory(root);long bytes=0;var seen=new HashSet<String>();
  try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/malt-alignment-controls.zip")))) {
   for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){Path target=root.resolve(entry.getName()).normalize();assertTrue(target.startsWith(root));assertFalse(entry.isDirectory());assertTrue(seen.add(entry.getName()));byte[] raw=zip.readNBytes(16777217);bytes+=raw.length;assertTrue(raw.length<=16777216&&bytes<=33554432);Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);}
  }return root;
 }
 @Test void bothCompleteLibrariesMatchEveryOriginalAlignmentAndCompletionCounter()throws Exception {
  Path root=fixture();long queries=0,hits=0;
  for(String lib:List.of("JK2782","JK2802")){var audit=MaltAlignmentAudit.audit(root.resolve("data/"+lib+"/"+lib+".unmapped.fastq.gz"),root.resolve("data/"+lib+"/"+lib+".unmapped.blastn.sam.gz"),root.resolve("database"),LIMITS);var counters=MaltAlignCli.sourceCounters(root.resolve("expected/"+lib+".completion.stderr"));assertEquals(counters.get("queries").longValue(),audit.queries());assertEquals(counters.get("alignments").longValue(),audit.alignments());assertEquals(counters.get("aligned_queries").longValue(),audit.alignedQueries());assertEquals(audit.queries(),audit.queryLengths().size());assertEquals(audit.alignedQueries(),audit.counts().size());assertEquals(audit.alignments(),audit.counts().values().stream().mapToLong(Integer::longValue).sum());assertThrows(UnsupportedOperationException.class,()->audit.counts().clear());queries+=audit.queries();hits+=audit.alignments();}
  assertEquals(16850,queries);assertEquals(175,hits);
 }
 @Test void sourceGeometryAndCompletePopulationLimitsRefuseAlteredOrTruncatedAssessment()throws Exception {
  Path root=fixture(),query=root.resolve("data/JK2782/JK2782.unmapped.fastq.gz"),sam=root.resolve("data/JK2782/JK2782.unmapped.blastn.sam.gz"),db=root.resolve("database");byte[] before=Files.readAllBytes(query);assertThrows(IOException.class,()->MaltAlignmentAudit.audit(query,sam,db,new MaltAlignmentAudit.Limits(1,100000000,10000000,1000000,1000000000)));assertThrows(IOException.class,()->MaltAlignmentAudit.audit(query,sam,db,new MaltAlignmentAudit.Limits(1000000,100000000,1,1000000,1000000000)));assertArrayEquals(before,Files.readAllBytes(query));String text;try(var input=new GZIPInputStream(Files.newInputStream(sam))){text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}String altered=text.replaceFirst("NM:i:[0-9]+","NM:i:999999");assertNotEquals(text,altered);Path bad=temporary.resolve("altered.sam.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(bad))){out.write(altered.getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertThrows(IOException.class,()->MaltAlignmentAudit.audit(query,bad,db,LIMITS));
 }
 @Test void duplicateMissingAndNondecimalSourceCountersCannotProveCompleteConsumption()throws Exception {
  Path root=fixture(),source=root.resolve("expected/JK2782.completion.stderr");String original=Files.readString(source);Path duplicate=temporary.resolve("duplicate.stderr");Files.writeString(duplicate,original+"\nNum. of queries: 8642\n");assertThrows(IOException.class,()->MaltAlignCli.sourceCounters(duplicate));Path missing=temporary.resolve("missing.stderr");Files.writeString(missing,original.replace("Num. of queries:","Not a completion counter:"));assertThrows(IOException.class,()->MaltAlignCli.sourceCounters(missing));Path negative=temporary.resolve("negative.stderr");Files.writeString(negative,original.replaceFirst("Num\\. of queries: *8642","Num. of queries: -1"));assertThrows(IOException.class,()->MaltAlignCli.sourceCounters(negative));
 }
 @Test void originalLocalSourceClipDefectRefusesTheNamedReadWithoutRepair()throws Exception {
  Path root=fixture();var error=assertThrows(IOException.class,()->MaltAlignmentAudit.audit(root.resolve("data/JK2782/JK2782.unmapped.fastq.gz"),root.resolve("negative/original-local-clip-discrepancy.sam.gz"),root.resolve("database"),LIMITS));assertTrue(error.getMessage().contains("M_AHSTEUER006:166:C5UJMANXX:8:1101:12708:28682"));assertTrue(error.getMessage().contains("CIGAR=48H18M1D41M2H"));assertTrue(error.getMessage().contains("expected sequence/quality length=60, actual=59/59"));assertTrue(error.getMessage().contains("Required action:"));
 }
 @Test void originalBlastnDefaultsAndIdentityPrecisionUseTheSelectedMode()throws Exception {
  var p=MaltAlignCli.parameters(Map.of("--profile","STANDALONE","--minimum-identity","89.6551724137931"));assertEquals(7,p.gapOpen());assertEquals(3,p.gapExtend());assertEquals("Local",p.alignment());assertEquals(89.6551724137931,p.minimumIdentity());
 }
 @Test void helpAndExistingDirectoryKeepWarningsAndPriorFilesExplicit()throws Exception {
  var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();var out=new PrintStream(stdout);var error=new PrintStream(stderr);assertEquals(0,MaltAlignCli.run(new String[]{"--help"},out,error));assertTrue(stdout.toString().contains("Required action:"));Path existing=temporary.resolve("existing");Files.createDirectory(existing);Path keep=existing.resolve("retained.txt");Files.writeString(keep,"prior result");assertEquals(2,MaltAlignCli.run(new String[]{"--runtime",temporary.resolve("none").toString(),"--query",temporary.resolve("missing.fastq").toString(),"--database",temporary.resolve("no-index").toString(),"--out",existing.toString()},out,error));assertEquals("prior result",Files.readString(keep));try(var files=Files.list(existing)){assertEquals(1,files.count());}assertTrue(stderr.toString().contains("Required action:"));
 }
}
