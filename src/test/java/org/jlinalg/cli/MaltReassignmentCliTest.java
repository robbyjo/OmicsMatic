/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.jlinalg.metagenomics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MaltReassignmentCliTest {
 @TempDir Path temporary;
 private static final MaltRmaReader.Limits LIMITS=new MaltRmaReader.Limits(1000000000,1000000,10000000,100000,10000000,1000000);
 private Path provider(){String value=System.getenv("OMICSMATIC_MALT_RMA_RUNTIME");assumeTrue(value!=null && Files.isRegularFile(Path.of(value).resolve("MALT.jar")),"SKIP3 Required action: run tools/literature/setup_malt_rma_runtime.py in a permitted environment and set OMICSMATIC_MALT_RMA_RUNTIME to its directory. No dependent reference test was performed.");return Path.of(value);}
 private Path fixture()throws Exception {
  Path root=temporary.resolve("fixture");Files.createDirectory(root);long bytes=0;
  try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/malt-reassignment-controls.zip")))) {
   for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
    Path target=root.resolve(entry.getName()).normalize();assertTrue(target.startsWith(root));assertFalse(entry.isDirectory());Files.createDirectories(target.getParent());byte[] raw=zip.readNBytes(16777217);bytes+=raw.length;assertTrue(raw.length<=16777216 && bytes<=33554432);Files.write(target,raw,StandardOpenOption.CREATE_NEW);
   }
  }
  return root;
 }
 private static String b64(String text){return text==null?"NULL":Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
 private static String bits(float value){return Integer.toHexString(Float.floatToRawIntBits(value));}
 @Test void missingProviderSkipsBeforeCallbacksAndReturnedRecordsAreImmutable()throws Exception {
  Path input=temporary.resolve("retained.rma6");Files.write(input,new byte[]{1,2,3});int[] calls={0};var error=assertThrows(MaltRmaReader.MissingRuntimeException.class,()->MaltRmaReader.read(temporary.resolve("missing"),input,LIMITS,read->calls[0]++));assertTrue(error.getMessage().contains("SKIP3"));assertTrue(error.getMessage().contains("Required action:"));assertEquals(0,calls[0]);assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(input));
  var match=new MaltRmaReader.Match(0,9606,100f,0.001f,99f,false,null,null,1,50,500);var source=new ArrayList<MaltRmaReader.Match>();source.add(match);var read=new MaltRmaReader.Read(7,"literal",null,null,50,1,1,source,9606);source.clear();assertEquals(1,read.matches().size());assertThrows(UnsupportedOperationException.class,()->read.matches().clear());assertEquals(new MaltAssignment.Match(9606,100f,0.001f,99f,false),match.assignmentMatch());assertThrows(IllegalArgumentException.class,()->new MaltRmaReader.Limits(0,1,1,1,1,1));
 }
 @Test void completeOriginalReadMatchAndClassFieldsSurviveIsolatedReader()throws Exception {
  Path runtime=provider(),root=fixture();long count=0,matches=0;
  for(String library:List.of("JK2782","JK2802")) {
   Path input=root.resolve("data/"+library+"/"+library+".unmapped.rma6");var rows=new ArrayList<MaltRmaReader.Read>();var report=MaltRmaReader.read(runtime,input,LIMITS,rows::add);assertTrue(report.warnings().isEmpty());assertEquals(MaltRmaReader.RAW,report.profile());count+=rows.size();matches+=report.availableMatches();
   var reads=new StringBuilder("uid\tread_name_base64\theader_base64\tsequence_base64\tread_length\tread_weight\tnumber_of_matches\tavailable_matches\n");var hits=new StringBuilder("uid\tmatch_index\ttaxon_id\tbit_score_bits\texpected_bits\tpercent_identity_bits\tignored\treference_base64\ttext_base64\taligned_query_start\taligned_query_end\treference_length\n");var members=new TreeMap<Integer,TreeSet<Long>>();
   for(var row:rows){members.computeIfAbsent(row.storedTaxon(),ignored->new TreeSet<>()).add(row.uid());reads.append(row.uid()).append('\t').append(b64(row.name())).append('\t').append(b64(row.header())).append('\t').append(b64(row.sequence())).append('\t').append(row.length()).append('\t').append(row.weight()).append('\t').append(row.totalMatches()).append('\t').append(row.matches().size()).append('\n');for(var match:row.matches())hits.append(row.uid()).append('\t').append(match.index()).append('\t').append(match.taxon()).append('\t').append(bits(match.bitScore())).append('\t').append(bits(match.expected())).append('\t').append(bits(match.percentIdentity())).append('\t').append(match.ignored()).append('\t').append(b64(match.reference())).append('\t').append(b64(match.text())).append('\t').append(match.alignedStart()).append('\t').append(match.alignedEnd()).append('\t').append(match.referenceLength()).append('\n');}
   String label=library+".unmapped.rma6";assertEquals(Files.readString(root.resolve("expected-raw/"+label+".reads.tsv")),reads.toString());assertEquals(Files.readString(root.resolve("expected-raw/"+label+".matches.tsv")),hits.toString());var classes=new StringBuilder("taxon_id\tclass_sum\tweighted_sum_bits\titerated_reads\tuids\n");for(var row:report.classifications().values()){var uids=members.getOrDefault(row.taxon(),new TreeSet<>());classes.append(row.taxon()).append('\t').append(row.reads()).append('\t').append(bits(row.weightedReads())).append('\t').append(uids.size()).append('\t').append(uids).append('\n');}assertEquals(Files.readString(root.resolve("expected-raw/"+label+".classification.tsv")),classes.toString());assertThrows(UnsupportedOperationException.class,()->report.classifications().clear());
  }
  assertEquals(172,count);assertEquals(175,matches);
 }
 @Test void all32OriginalSerialProcessorProfilesMatchFinalIdsAndSelectedThresholds()throws Exception {
  Path runtime=provider(),root=fixture();var session=new MaltReassignment.Session(runtime,new MaltTaxonomy.Limits(100000000,3000000,1024));var expected=expected(root);var thresholds=new HashMap<String,String[]>();for(String line:Files.readAllLines(root.resolve("expected/thresholds.tsv")).subList(1,33)){String[] f=line.split("\t");thresholds.put(f[0],f);}int cases=0,reads=0;
  for(String line:Files.readAllLines(root.resolve("cases.tsv")).subList(1,33)) {
   String[] f=line.split("\t",-1);var disabled=new HashSet<Integer>();if(!f[4].isEmpty())for(String value:f[4].split(","))disabled.add(Integer.valueOf(value));var result=session.reassign(root.resolve("data/"+f[1]+"/"+f[1]+".unmapped.rma6"),new MaltReassignment.Options(50,1,1,0,80,Integer.parseInt(f[2]),Float.parseFloat(f[3]),disabled),LIMITS);assertEquals(Integer.parseInt(thresholds.get(f[0])[1]),result.selectedMinimum());var observed=new TreeMap<Long,Integer>();for(var row:result.rows())assertNull(observed.put(row.uid(),row.finalTaxon()));assertEquals(expected.get(f[0]),observed,f[0]);assertEquals(MaltRmaReader.SERIAL_PROCESSOR,result.source().profile());cases++;reads+=result.rows().size();
  }
  assertEquals(32,cases);assertEquals(2752,reads);
 }
 @Test void commandKeepsIntegerOverrideSkipWarningsAndExistingResultsExplicit()throws Exception {
  Path runtime=provider(),root=fixture(),input=root.resolve("data/JK2782/JK2782.unmapped.rma6"),out=temporary.resolve("default");var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();var output=new PrintStream(stdout,true,StandardCharsets.UTF_8);var errors=new PrintStream(stderr,true,StandardCharsets.UTF_8);
  String[] args={"--rma",input.toString(),"--runtime",runtime.toString(),"--out",out.toString()};assertEquals(0,MaltReassignCli.run(args,output,errors),stderr.toString(StandardCharsets.UTF_8));assertTrue(Files.isRegularFile(out.resolve("COMPONENT_COMPLETED.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));byte[] before=Files.readAllBytes(out.resolve("assignments.tsv"));assertEquals(2,MaltReassignCli.run(args,output,errors));assertArrayEquals(before,Files.readAllBytes(out.resolve("assignments.tsv")));
  Path override=temporary.resolve("override");assertEquals(0,MaltReassignCli.run(new String[]{"--rma",input.toString(),"--runtime",runtime.toString(),"--out",override.toString(),"--minimum-support","50","--minimum-support-percent","50"},output,errors));var json=NextflowFiles.object(NextflowFiles.json(override.resolve("reassignment.json"),1048576));assertEquals(50L,((Number)json.get("selected_minimum_support")).longValue());assertEquals(0.0,((Number)NextflowFiles.object(json.get("parameters")).get("support_percent")).doubleValue());assertTrue(Files.readString(override.resolve("WARNINGS.md")).contains("MINIMUM_SUPPORT_OVERRIDE"));var observed=new TreeMap<Long,Integer>();for(String line:Files.readAllLines(override.resolve("assignments.tsv")).subList(1,94)){String[] f=line.split("\t",-1);observed.put(Long.valueOf(f[0]),Integer.valueOf(f[5]));}assertEquals(expected(root).get("2"),observed);
  Path skipped=temporary.resolve("skipped");assertEquals(3,MaltReassignCli.run(new String[]{"--rma",input.toString(),"--runtime",temporary.resolve("unavailable").toString(),"--out",skipped.toString()},output,errors));assertFalse(Files.exists(skipped.resolve("COMPONENT_COMPLETED.json")));assertTrue(Files.readString(skipped.resolve("WARNINGS_FAILURE.md")).contains("SKIP3"));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Required action:"));
 }
 private Map<String,Map<Long,Integer>> expected(Path root)throws IOException {
  var result=new HashMap<String,Map<Long,Integer>>();for(String line:Files.readAllLines(root.resolve("expected/assignments.tsv")).subList(1,2753)){String[] f=line.split("\t");assertNull(result.computeIfAbsent(f[0],ignored->new TreeMap<>()).put(Long.valueOf(f[1]),Integer.valueOf(f[2])));}return result;
 }
}
