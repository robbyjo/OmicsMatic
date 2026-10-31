/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

final class TranscriptSiteCliTest {
    @TempDir Path directory;
    private String message;
    private int run(String... args)throws Exception {
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();int code;
        try(var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(errors,true,StandardCharsets.UTF_8)){code=FumaToolsCli.run(args,out,err);}
        message=output.toString(StandardCharsets.UTF_8)+errors.toString(StandardCharsets.UTF_8);return code;
    }
    private int run(Map<String,String> options)throws Exception {var args=new ArrayList<String>();options.forEach((key,value)->{args.add("--"+key);args.add(value);});return run(args.toArray(String[]::new));}
    private Map<String,String> data(String name)throws Exception {
        Path base=directory.resolve(name);Files.createDirectories(base);
        Files.writeString(base.resolve("variants.tsv"),"context\tvariant_id\tchromosome\tposition\tp\tsource_note\ntrait\tv1\t1\t100\t1e-6\tα\ntrait\tmiddle\t1\t500000\t1e-1000\tβ\ntrait\tmissing\t1\t100\tNA\tγ\ntrait\tabove\t1\t100\t0.000010000000000000000000001\tδ\ntrait\tedge\t1\t10100\t1e-5\tε\nother\tv1\t1\t100\t.9\tζ\ntrait\treverse\t2\t600\t0\tη\n");
        Files.writeString(base.resolve("transcripts.tsv"),"gene_id\ttranscript_id\tchromosome\tstart\tend\tstrand\tgene_biotype\nA\ta\t1\t100\t1000000\t+\tprotein_coding\nB\tb\t2\t500\t600\t-\tprotein_coding\nL\tl\t1\t100\t200\t+\tlncRNA\n");
        var options=new LinkedHashMap<String,String>();options.put("method","transcript-sites");options.put("analysis-id","synthetic-sites");options.put("variants",base.resolve("variants.tsv").toString());options.put("transcripts",base.resolve("transcripts.tsv").toString());options.put("out",base.resolve("analysis").toString());options.put("expected-variant-rows","7");options.put("expected-transcript-rows","3");
        for(String key:List.of("build","reference-build"))options.put(key,"synthetic-build");options.put("reference-id","synthetic-v1");for(String key:List.of("gene-namespace","transcript-namespace","variant-namespace","context-namespace"))options.put(key,key+"-v1");
        options.put("p-cutoff","1e-5");options.put("radius-bp","10000");options.put("boundary","INCLUSIVE");options.put("sites","TSS,TES");options.put("gene-biotypes","protein_coding");options.put("missing-p","EXCLUDE");return options;
    }
    private static Path out(Map<String,String> options){return Path.of(options.get("out"));}
    private static List<Map<String,String>> table(Path file)throws Exception {
        var lines=Files.readAllLines(file);var header=lines.get(0).split("\t",-1);var rows=new ArrayList<Map<String,String>>();
        for(String line:lines.subList(1,lines.size())){var values=line.split("\t",-1);assertEquals(header.length,values.length);var row=new LinkedHashMap<String,String>();for(int i=0;i<header.length;i++)row.put(header[i],values[i]);rows.add(row);}return rows;
    }
    private static void replace(Map<String,String> options,String key,String before,String after)throws Exception {Path p=Path.of(options.get(key));String text=Files.readString(p);assertTrue(text.contains(before));Files.writeString(p,text.replace(before,after));}
    private void failed(Map<String,String> options)throws Exception {
        assertEquals(2,run(options),message);assertTrue(message.contains("Required action:"));assertTrue(Files.readString(out(options).resolve("analysis-status.json")).contains("failed"));assertTrue(Files.isDirectory(out(options).resolve("partial")));
        for(String file:List.of("mapping-policy.tsv","variant-dispositions.tsv","transcript-dispositions.tsv","site-hits.tsv","gene-mappings.tsv"))assertFalse(Files.exists(out(options).resolve(file)),file);
        assertTrue(Files.readString(out(options).resolve("WARNINGS.md")).contains("Required action:"));
    }
    @Test void completeResultPreservesEveryPopulationAndExactDecimalValue()throws Exception {
        var o=data("complete");String original=FollowupSupport.hash(Path.of(o.get("variants")));assertEquals(0,run(o),message);
        var rows=table(out(o).resolve("variant-dispositions.tsv"));assertEquals(7,rows.size());assertEquals("1E-1000",rows.get(1).get("p_decimal"));assertEquals("UNMAPPED_IN_SUPPLIED_REFERENCE",rows.get(1).get("status"));assertEquals("MISSING_P_EXCLUDED",rows.get(2).get("status"));assertEquals("ABOVE_P_CUTOFF",rows.get(3).get("status"));assertEquals("MAPPED",rows.get(4).get("status"));
        assertEquals(3,table(out(o).resolve("transcript-dispositions.tsv")).size());assertEquals("false",table(out(o).resolve("transcript-dispositions.tsv")).get(2).get("selected_biotype"));assertEquals(4,table(out(o).resolve("site-hits.tsv")).size());assertEquals(3,table(out(o).resolve("gene-mappings.tsv")).size());assertEquals(original,FollowupSupport.hash(Path.of(o.get("variants"))));
        for(var resource:table(out(o).resolve("resource-ledger.tsv")))assertEquals(resource.get("sha256"),FollowupSupport.hash(out(o).resolve(resource.get("snapshot"))));
        String warnings=Files.readString(out(o).resolve("WARNINGS.md"));assertTrue(warnings.contains("REPORTED ZERO P"));assertTrue(warnings.contains("MISSING P EXCLUSIONS"));assertTrue(message.contains("REVIEW REQUIRED"));
    }
    @Test void completedManifestHashesEveryPublishedMember()throws Exception {
        var o=data("manifest");assertEquals(0,run(o),message);Map<?,?> m=new Yaml().load(Files.readString(out(o).resolve("manifest.yaml")));assertEquals("transcript-site-mapping",m.get("operation"));assertEquals("completed-with-review-warnings",m.get("analysis_status"));Map<?,?> hashes=(Map<?,?>)m.get("output_sha256");
        try(var files=Files.walk(out(o))){for(Path file:files.filter(Files::isRegularFile).toList())if(!file.getFileName().toString().equals("manifest.yaml"))assertEquals(FollowupSupport.hash(file),hashes.get(out(o).relativize(file).toString()));}
    }
    @Test void exactPinsAreEnforcedAndSourcesRemainUnmodified()throws Exception {
        var o=data("pins");for(String key:List.of("variants","transcripts"))o.put(key+"-sha256",FollowupSupport.hash(Path.of(o.get(key))));assertEquals(0,run(o),message);
        o.put("out",out(o).resolveSibling("bad-pin").toString());o.put("variants-sha256","0".repeat(64));failed(o);assertTrue(message.contains("Pinned --variants-sha256"));
    }
    @Test void expectedPopulationsIncludeMissingAndExcludedRows()throws Exception {
        for(String key:List.of("expected-variant-rows","expected-transcript-rows")){var o=data(key);o.put(key,"1");failed(o);assertTrue(message.contains(key));}
    }
    @Test void missingErrorRejectsRatherThanQuietlyDroppingARow()throws Exception {var o=data("missing");o.put("missing-p","ERROR");failed(o);assertTrue(message.contains("Missing variant P"));}
    @Test void buildMetadataMustMatchLiterally()throws Exception {var o=data("build");o.put("reference-build","other-build");failed(o);assertTrue(message.contains("builds differ"));}
    @Test void duplicateVariantContextsReject()throws Exception {var o=data("duplicate-variant");replace(o,"variants","other\tv1","trait\tv1");failed(o);assertTrue(message.contains("Duplicate variant/context"));}
    @Test void conflictingVariantCoordinatesAcrossContextsReject()throws Exception {var o=data("variant-coordinate");replace(o,"variants","other\tv1\t1\t100","other\tv1\t1\t101");failed(o);assertTrue(message.contains("conflicting coordinates"));}
    @Test void repeatedTranscriptIdsReject()throws Exception {var o=data("duplicate-transcript");replace(o,"transcripts","B\tb","B\ta");failed(o);assertTrue(message.contains("Duplicate transcript"));}
    @Test void conflictingGeneBiotypeRejectsEvenIfTheRowWouldBeExcluded()throws Exception {var o=data("gene-biotype");replace(o,"transcripts","L\tl\t1","A\tl\t1");failed(o);assertTrue(message.contains("Gene has conflicting"));}
    @Test void malformedProbabilityAndCoordinateTokensRejectWithoutRounding()throws Exception {
        for(String p:List.of("NaN","Infinity","","-0.1","1.1","0x1.0p-5"," 0.01")){var o=data("p-"+Math.abs(p.hashCode()));replace(o,"variants","1e-1000",p);failed(o);}
        var o=data("coordinate-fraction");replace(o,"variants","500000","500000.1");failed(o);
    }
    @Test void oneBasedCoordinatesAndExplicitStrandAreRequired()throws Exception {
        var o=data("zero-position");replace(o,"variants","500000","0");failed(o);o=data("reversed");replace(o,"transcripts","100\t1000000","1000000\t100");failed(o);o=data("strand");replace(o,"transcripts","\t+\t","\t.\t");failed(o);
    }
    @Test void headerAndRowShapeFailuresAreNamedAndQuarantined()throws Exception {
        var o=data("header");replace(o,"variants","position\tp","position\tposition");failed(o);assertTrue(message.contains("header"));o=data("field-count");replace(o,"variants","1e-1000\tβ","1e-1000\tβ\textra");failed(o);assertTrue(message.contains("Malformed"));
    }
    @Test void invalidUtf8AndEmbeddedControlCharactersReject()throws Exception {
        var o=data("utf8");Files.write(Path.of(o.get("variants")),new byte[]{(byte)0xc3,0x28});failed(o);assertTrue(message.contains("UTF-8"));o=data("control");replace(o,"variants","β","β\u0000");failed(o);assertTrue(message.contains("Malformed"));
    }
    @Test void crlfAndReorderedColumnsPreserveExactRawSnapshots()throws Exception {
        var o=data("crlf");replace(o,"variants","context\tvariant_id","variant_id\tcontext");replace(o,"variants","trait\tv1","v1\ttrait");replace(o,"variants","trait\tmiddle","middle\ttrait");replace(o,"variants","trait\tmissing","missing\ttrait");replace(o,"variants","trait\tabove","above\ttrait");replace(o,"variants","trait\tedge","edge\ttrait");replace(o,"variants","other\tv1","v1\tother");replace(o,"variants","trait\treverse","reverse\ttrait");replace(o,"variants","\n","\r\n");assertEquals(0,run(o),message);
        var resource=table(out(o).resolve("resource-ledger.tsv")).get(0);assertArrayEquals(Files.readAllBytes(Path.of(o.get("variants"))),Files.readAllBytes(out(o).resolve(resource.get("snapshot"))));assertEquals("trait",table(out(o).resolve("variant-dispositions.tsv")).get(0).get("context"));
    }
    @Test void missingInputsProduceExplicitFailureRecords()throws Exception {var o=data("absent");o.put("variants",directory.resolve("absent.tsv").toString());failed(o);assertTrue(message.contains("Missing, linked or oversized"));}
    @Test void combinedRowsCellsBytesAndSnapshotBoundsRejectTheWholeAnalysis()throws Exception {
        for(var e:Map.of("max-rows","9","max-cells","5","max-bytes","20","max-snapshot-bytes","20").entrySet()){var o=data(e.getKey());o.put(e.getKey(),e.getValue());failed(o);}
    }
    @Test void lineAndOutputBoundsRetainPartialDiagnostics()throws Exception {
        for(var e:Map.of("max-line-bytes","64","max-output-bytes","100","max-output-rows","1").entrySet()){var o=data(e.getKey());o.put(e.getKey(),e.getValue());failed(o);}
    }
    @Test void completeHitAndWorkBoundsNeverReturnTruncatedSuccess()throws Exception {var o=data("hit-budget");o.put("max-hits","1");failed(o);assertTrue(message.contains("hit budget"));o=data("operation-budget");o.put("max-operations","1");failed(o);assertTrue(message.contains("operation budget"));}
    @Test void zeroHitBudgetAndAnExplicitEmptySelectionResultAreValid()throws Exception {var o=data("zero-hits");o.put("gene-biotypes","absent-biotype");o.put("max-hits","0");assertEquals(0,run(o),message);assertTrue(table(out(o).resolve("site-hits.tsv")).isEmpty());assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("NO SELECTED TRANSCRIPTS"));}
    @Test void strictBoundaryAndSingleSiteSelectionChangeOnlyDeclaredMapping()throws Exception {var o=data("strict");o.put("boundary","STRICT");o.put("sites","TSS");assertEquals(0,run(o),message);assertEquals("UNMAPPED_IN_SUPPLIED_REFERENCE",table(out(o).resolve("variant-dispositions.tsv")).get(4).get("status"));assertEquals(2,table(out(o).resolve("site-hits.tsv")).size());}
    @Test void invalidPolicyAndDuplicateSelectionOptionsFailWithRecovery()throws Exception {
        for(var e:Map.of("sites","TSS,TSS","gene-biotypes","protein_coding,protein_coding","boundary","CLOSED","missing-p","DROP","radius-bp","-1","p-cutoff","2","expected-variant-rows","0").entrySet()){var o=data("policy-"+e.getKey());o.put(e.getKey(),e.getValue());failed(o);}
    }
    @Test void jsonCarrierResolvesUnicodeRelativePathsAndDirectOverrides()throws Exception {
        var o=data("carrier-α");Path base=Path.of(o.get("variants")).getParent();var carrier=new LinkedHashMap<>(o);for(String key:List.of("variants","transcripts","out"))carrier.put(key,base.relativize(Path.of(carrier.get(key))).toString());Path recipe=base.resolve("options.json");Files.writeString(recipe,RegenieRuntime.json(carrier));assertEquals(0,run("--site-options-file",recipe.toString(),"--boundary","STRICT"),message);assertEquals("STRICT",table(out(o).resolve("mapping-policy.tsv")).get(0).get("boundary"));assertEquals(3,table(out(o).resolve("resource-ledger.tsv")).size());
    }
    @Test void jsonUnknownRecursiveDuplicateAndNonStringValuesRejectBeforeOutput()throws Exception {
        for(String body:List.of("{\"unrecognized\":\"x\"}","{\"site-options-file\":\"again.json\"}","{\"radius-bp\":10000}","{\"radius-bp\":\"1\",\"radius-bp\":\"2\"}")){Path p=directory.resolve("bad-"+Math.abs(body.hashCode())+".json");Files.writeString(p,body);assertEquals(2,run("--site-options-file",p.toString()),message);assertTrue(message.contains("Required action:"));}
    }
    @Test void missingRequiredAndUnknownFlagsAreRejectedBeforeOutput()throws Exception {var o=data("options");o.remove("reference-id");assertEquals(2,run(o),message);assertFalse(Files.exists(out(o)));o=data("unknown");o.put("automatic-install","true");assertEquals(2,run(o),message);assertFalse(Files.exists(out(o)));}
    @Test void anExistingDestinationAndItsContentsArePreserved()throws Exception {var o=data("existing");Files.createDirectories(out(o));Path keep=out(o).resolve("keep.txt");Files.writeString(keep,"preserve");assertEquals(2,run(o),message);assertEquals("preserve",Files.readString(keep));assertTrue(message.contains("Output already exists"));}
    @Test void mismatchedCarrierMethodIsNotSilentlyReplaced()throws Exception {var o=data("method");o.put("method","other");Path file=directory.resolve("method.json");Files.writeString(file,RegenieRuntime.json(o));assertEquals(2,run("--site-options-file",file.toString()),message);assertFalse(Files.exists(out(o)));}
    @Test void helpIncludesSitePoliciesDecimalHandlingAndRecovery()throws Exception {assertEquals(0,run("--method","transcript-sites","--help"));for(String term:List.of("P<=cutoff","INCLUSIVE|STRICT","--expected-transcript-rows","--max-hits","Required action:"))assertTrue(message.contains(term),term);assertEquals(0,run("--help"));assertTrue(message.contains("--method transcript-sites --help"));}
}
