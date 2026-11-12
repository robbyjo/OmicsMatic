/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractReadsCliTest {
 @TempDir Path temporary;
 @Test void helpDeclaresMethodExplicitGroupListSettingsAndRecovery() {
  var bytes=new ByteArrayOutputStream();assertEquals(0,MaltExtractReadsCli.run(new String[]{"--help"},new PrintStream(bytes),System.err));String text=bytes.toString();for(String token:new String[]{"source17-complete-projection","Descendants are not discovered","downsampling are disabled","minimum-identity-percent","Required action:","skip this dependent extraction"})assertTrue(text.contains(token),token);
 }
 @Test void missingMethodDuplicateOptionAndInvalidNumericChoiceCannotCreateOutput()throws Exception {
  var bytes=new ByteArrayOutputStream();var console=new PrintStream(bytes);Path out=temporary.resolve("never");
  assertEquals(2,MaltExtractReadsCli.run(new String[]{"--runtime","a","--runtime","b"},console,console));
  assertEquals(2,MaltExtractReadsCli.run(new String[]{"--runtime","a","--out",out.toString()},console,console));
  assertEquals(2,MaltExtractReadsCli.run(arguments(out,"--top-fraction","NaN"),console,console));assertFalse(Files.exists(out));assertTrue(bytes.toString().contains("NO RESULT ADMITTED"));assertTrue(bytes.toString().contains("Required action:"));
 }
 private String[] arguments(Path out,String... extra)throws IOException {
  Path input=temporary.resolve("source.rma6"),tree=temporary.resolve("ncbi.tre"),taxa=temporary.resolve("taxa.tsv");if(!Files.exists(input)){Files.writeString(input,"retained RMA bytes");Files.writeString(tree,"(9606:1)1:1;");Files.writeString(taxa,"taxon_id\n9606\n");}
  var args=new java.util.ArrayList<String>(java.util.List.of("--runtime",temporary.resolve("missing-provider").toString(),"--input",input.toString(),"--taxonomy",tree.toString(),"--taxa",taxa.toString(),"--mode","both","--method","source17-complete-projection","--input-provenance","supplied","--out",out.toString()));args.addAll(java.util.List.of(extra));return args.toArray(String[]::new);
 }
 @Test void existingOutputIsRetainedWithoutNewDiagnosticMembers()throws Exception {
  Path out=Files.createDirectory(temporary.resolve("prior"));Files.writeString(out.resolve("keep.txt"),"prior evidence");var bytes=new ByteArrayOutputStream();var console=new PrintStream(bytes);assertEquals(2,MaltExtractReadsCli.run(arguments(out),console,console));assertEquals("prior evidence",Files.readString(out.resolve("keep.txt")));try(var paths=Files.list(out)){assertEquals(1,paths.count());}
 }
 @Test void missingRuntimeLeavesProminentSkipDiagnosticsAndRetainsCompleteCapturedInputs()throws Exception {
  Path out=temporary.resolve("skip");var bytes=new ByteArrayOutputStream();var console=new PrintStream(bytes);assertEquals(3,MaltExtractReadsCli.run(arguments(out),console,console));assertTrue(Files.readString(out.resolve("WARNINGS_FAILURE.md")).startsWith("# SKIP3 NO RESULT"));assertTrue(Files.readString(out.resolve("FAILURE.json")).contains("Required action:"));assertEquals("retained RMA bytes",Files.readString(out.resolve("inputs/source.rma6")));assertFalse(Files.exists(out.resolve("COMPONENT_COMPLETED.json")));assertTrue(bytes.toString().contains("DEPENDENT ANALYSIS SKIPPED"));
 }
}
