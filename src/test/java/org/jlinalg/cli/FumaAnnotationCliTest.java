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
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FumaAnnotationCliTest {
    @TempDir Path temp;
    private final ByteArrayOutputStream errors=new ByteArrayOutputStream();
    private Map<String,String> options(String output)throws Exception{return FumaAnnotationTestData.options(temp,output);}
    private int run(Map<String,String> options){var args=new ArrayList<String>();options.forEach((k,v)->{args.add("--"+k);args.add(v);});return command(args);}
    private int command(List<String> args){return FumaToolsCli.run(args.toArray(String[]::new),new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors));}
    private String read(String output,String name)throws Exception{return Files.readString(temp.resolve(output).resolve(name));}
    private Path carrier(String name,Map<String,String> options)throws Exception {Path path=temp.resolve(name);Files.writeString(path,RegenieRuntime.json(options));return path;}
    private void gzip(Path path,String value)throws Exception {try(var out=new GZIPOutputStream(Files.newOutputStream(path))){out.write(value.getBytes(StandardCharsets.UTF_8));}}
    private Map<String,String> nativeMagma(String method)throws Exception {
        Files.writeString(temp.resolve("magma-genes.tsv"),"gene_id\tchromosome\tstart\tend\tstrand\nG1\t1\t90\t110\t+\n");Files.writeString(temp.resolve("magma-snps.tsv"),"variant_id\tchromosome\tposition\nu1\t1\t100\n");var map=new LinkedHashMap<String,String>();map.put("method",method);map.put("genes",temp.resolve("magma-genes.tsv").toString());map.put("snps",temp.resolve("magma-snps.tsv").toString());if(method.equals("annotate"))map.put("home",temp.resolve("missing-magma-home").toString());return map;
    }
    private Path parent(String name,Map<String,String> stage,Map<String,String> other)throws Exception {
        var selected=new LinkedHashMap<>(stage);selected.remove("method");selected.remove("out");selected.remove("build");var root=new LinkedHashMap<String,Object>();root.put("schema_version",1);root.put("build","GRCh38");root.put("out",name);root.put("variant_annotation",selected);if(other!=null)root.put("magma_annotate",other);Path path=temp.resolve(name+".json");Files.writeString(path,RegenieRuntime.json(root));return path;
    }
    @Test void completeTablesKeepTiesBlankSymbolsRawRanksAndSourceLdCounts()throws Exception {
        assertEquals(0,run(options("ok")));String snps=read("ok","snps.tsv");assertTrue(snps.contains("u1\trs1\t1\t100\t.01\t:LEFT\t0:0\texonic\t10.0\t1a\t2\t2"));assertTrue(snps.contains("u2\trs2\t1\t200\tNA\t:TWO\t0:0\tintergenic\tNA\tNA\t2\t2"));assertTrue(read("ok","independent.tsv").contains("u1\tlead\t3\t2"));assertTrue(read("ok","independent.tsv").contains("u2\tspare\tNA\tNA"));assertEquals(4,Files.readAllLines(temp.resolve("ok/annov.tsv")).size());assertTrue(read("ok","annotation-statistics.tsv").contains("exonic\t10\t0.1\t1\t0.5\t5.0\t1.0"));assertEquals(5,Files.readAllLines(temp.resolve("ok/nearest-gene-evidence.tsv")).size());assertTrue(read("ok","WARNINGS.md").contains("MISSING SUPPLIED ANNOTATIONS"));assertTrue(read("ok","manifest.yaml").contains("completed-with-review-warnings"));
    }
    @Test void everyOriginalInputIsSnapshottedAndBoundToItsHash()throws Exception {
        var o=options("hashes");assertEquals(0,run(o));var rows=Files.readAllLines(temp.resolve("hashes/resource-ledger.tsv"));assertEquals(8,rows.size());for(String line:rows.subList(1,rows.size())){var f=line.split("\t",-1);assertEquals(f[3],FollowupSupport.hash(Path.of(f[1])));assertEquals(f[3],FollowupSupport.hash(temp.resolve("hashes").resolve(f[2])));}assertTrue(read("hashes","session.json").contains("synthetic-reference-id-v1"));assertTrue(read("hashes","analysis-status.json").contains("\"exit_code\":0"));
    }
    @Test void scoreDuplicatesRequireExplicitFirstPolicyAndKeepAllResourceRows()throws Exception {
        var o=options("duplicate-error");Files.writeString(temp.resolve("annotations.tsv"),Files.readString(temp.resolve("annotations.tsv"))+"u1\t999\t7\t15\t15\n");assertEquals(2,run(o));o.put("duplicates","source-first");o.put("out",temp.resolve("duplicate-first").toString());assertEquals(0,run(o));assertTrue(read("duplicate-first","annotation-disposition.tsv").contains("later-duplicate"));assertTrue(read("duplicate-first","snps.tsv").contains("\t10.0\t1a\t"));assertTrue(read("duplicate-first","WARNINGS.md").contains("later duplicate"));
    }
    @Test void geneDuplicatesKeepFirstSymbolAndAllNearestResourceRows()throws Exception {
        var o=options("gene-error");Files.writeString(temp.resolve("genes.tsv"),Files.readString(temp.resolve("genes.tsv"))+"G1\tLATER\t1\t100\t199\n");assertEquals(2,run(o));o.put("duplicates","source-first");o.put("out",temp.resolve("gene-first").toString());assertEquals(0,run(o));assertTrue(read("gene-first","snps.tsv").contains(":LEFT:LATER"));assertTrue(read("gene-first","WARNINGS.md").contains("Repeated gene resource IDs"));assertTrue(read("gene-first","annov.tsv").contains("exon1\t\t1\t100"));
    }
    @Test void singletonStateExtensionRequiresAnExplicitChoice()throws Exception {
        var o=options("single-error");Files.writeString(temp.resolve("annotations.tsv"),"uniqID\tCADD\tRDB\ts1\nu1\t10\t1a\t2\nu2\tNA\t\t10\n");o.put("states","s1");assertEquals(2,run(o));o.put("single-state","retain-matrix");o.put("out",temp.resolve("single-retained").toString());assertEquals(0,run(o));assertTrue(read("single-retained","WARNINGS.md").contains("SOURCE DIMENSION DIFFERENCE"));
    }
    @Test void undefinedStateModesFailWithNamedCauseAndQuarantinedInputs()throws Exception {
        var o=options("missing-states");Files.writeString(temp.resolve("annotations.tsv"),"uniqID\tCADD\tRDB\ts1\ts2\nu1\t10\t1a\tNA\tNA\nu2\tNA\t\t2\t10\n");assertEquals(2,run(o));assertTrue(read("missing-states","WARNINGS.md").contains("All chromatin states are NA for u1"));assertFalse(Files.exists(temp.resolve("missing-states/snps.tsv")));assertTrue(Files.isDirectory(temp.resolve("missing-states/partial/source-inputs")));assertTrue(read("missing-states","analysis-status.json").contains("\"exit_code\":2"));
    }
    @Test void zeroBackgroundRejectsSourceDegenerateProbabilityAsUnadmittedInference()throws Exception {
        var o=options("zero");Files.writeString(temp.resolve("reference.tsv"),"annot\tcount\tprop\nunknown\t0\t0\n");assertEquals(2,run(o));assertTrue(read("zero","WARNINGS.md").contains("population must be positive"));assertFalse(Files.exists(temp.resolve("zero/annotation-statistics.tsv")));
    }
    @Test void unknownConsequenceCandidateCannotAcquireFabricatedCoordinates()throws Exception {
        var o=options("unknown");Files.writeString(temp.resolve("consequences.tsv"),Files.readString(temp.resolve("consequences.tsv"))+"outside\tG1\texonic\t0\tNA\tNA\n");assertEquals(2,run(o));assertTrue(read("unknown","WARNINGS.md").contains("unknown candidate UID: outside"));assertFalse(Files.exists(temp.resolve("unknown/annov.tsv")));
    }
    @Test void negativeFisherCellsKeepTheOriginalFailureCauseProminent()throws Exception {
        var o=options("subset");Files.writeString(temp.resolve("reference.tsv"),"annot\tcount\tprop\nexonic\t0\t0\nintergenic\t20\t1\n");assertEquals(2,run(o));assertTrue(read("subset","WARNINGS.md").contains("negative cells"));assertTrue(read("subset","WARNINGS.md").contains("Required action"));
    }
    @Test void explicitPlainAndGzipBedFormatsKeepDuplicateHitsAndZeroHitStatus()throws Exception {
        var o=options("bed");gzip(temp.resolve("A.gz"),"1\t99\t100\n1\t99\t100\n1\t199\t200\n");Files.writeString(temp.resolve("Z.bed"),"9\t0\t10\n");Files.writeString(temp.resolve("tracks.tsv"),"resource_id\tpath\tformat\nRoad/A.bed.gz\tA.gz\tbed-gzip\nZ.bed.gz\tZ.bed\tbed\n");o.put("bed-manifest",temp.resolve("tracks.tsv").toString());assertEquals(0,run(o));assertEquals(4,Files.readAllLines(temp.resolve("bed/annot.bed")).size());assertTrue(read("bed","annotations.tsv").startsWith("uniqID\tCADD\tRDB\ts1\ts2\tRoad_A\n"));assertFalse(read("bed","annotations.tsv").contains("\tZ\n"));assertTrue(read("bed","bed-tracks.tsv").contains("Z.bed.gz\tZ\tZ\t0\tfalse"));
    }
    @Test void compressedBedGrowthIsBoundedBeforeNativeAnalysis()throws Exception {
        var o=options("expanded");gzip(temp.resolve("A.gz"),"1\t99\t100\t"+"x".repeat(10000)+"\n");Files.writeString(temp.resolve("tracks.tsv"),"resource_id\tpath\tformat\nA.bed.gz\tA.gz\tbed-gzip\n");o.put("bed-manifest",temp.resolve("tracks.tsv").toString());o.put("max-expanded-bytes","64");assertEquals(2,run(o));assertTrue(read("expanded","WARNINGS.md").contains("--max-expanded-bytes"));assertFalse(Files.exists(temp.resolve("expanded/snps.tsv")));
    }
    @Test void sourceFilenameColumnCollisionsFailWithNoAdmittedTable()throws Exception {
        var o=options("collision");Files.writeString(temp.resolve("A.bed"),"1\t99\t100\n");Files.writeString(temp.resolve("tracks.tsv"),"resource_id\tpath\tformat\nA/B.bed.gz\tA.bed\tbed\nA_B.bed.gz\tA.bed\tbed\n");o.put("bed-manifest",temp.resolve("tracks.tsv").toString());assertEquals(2,run(o));assertTrue(read("collision","WARNINGS.md").contains("colliding source BED track column"));
    }
    @Test void stringCarrierResolvesPathsAndRetainsItsOwnBytes()throws Exception {
        var o=options("relative");for(String key:List.of("snps","genes","annotations","consequences","ld","independent","reference-counts","out"))o.put(key,temp.relativize(Path.of(o.get(key))).toString());Path p=carrier("options.json",o);assertEquals(0,command(List.of("--annotation-options-file",p.toString())));assertTrue(read("relative","resource-ledger.tsv").contains("options-carrier"));assertTrue(read("relative","manifest.yaml").contains("options-sha256"));
    }
    @Test void unicodeResourcesAndOutputParentsWorkThroughAsciiCarrier()throws Exception {
        Path unicode=temp.resolve("inputs Ω");var o=FumaAnnotationTestData.options(unicode,"result µ");Path p=carrier("unicode.json",o);assertEquals(0,command(List.of("--annotation-options-file",p.toString())));assertTrue(Files.exists(unicode.resolve("result µ/snps.tsv")));
    }
    @Test void recursiveUnknownAndNonStringCarrierValuesRejectBeforeAnalysis()throws Exception {
        for(String json:List.of("{\"annotation-options-file\":\"x\"}","{\"timeout\":1}","{\"bad-key\":\"x\"}")){Path p=temp.resolve("bad.json");Files.writeString(p,json);assertEquals(2,command(List.of("--annotation-options-file",p.toString())));}assertTrue(errors.toString().contains("Required action"));
    }
    @Test void malformedWidthsAndNonfiniteNumericTokensFailWithResourceContext()throws Exception {
        var o=options("nonfinite");Files.writeString(temp.resolve("annotations.tsv"),"uniqID\tCADD\tRDB\ts1\ts2\nu1\tNaN\t1a\t2\t2\nu2\t1\t2\t2\t2\n");assertEquals(2,run(o));assertTrue(read("nonfinite","WARNINGS.md").contains("CADD-RDB-states"));o.put("out",temp.resolve("width").toString());Files.writeString(temp.resolve("annotations.tsv"),"uniqID\tCADD\tRDB\ts1\ts2\nu1\t1\n");assertEquals(2,run(o));assertTrue(read("width","WARNINGS.md").contains("Malformed annotation TSV row"));
    }
    @Test void inputCellSnapshotAndEvidenceBudgetsRejectCompletePopulations()throws Exception {
        var o=options("cap");int i=0;for(String key:List.of("max-bytes","max-cells","max-snapshot-bytes","max-evidence","max-operations","max-fisher-states","max-reference-population")){var limited=new LinkedHashMap<>(o);limited.put(key,"1");String out="cap"+(i++);limited.put("out",temp.resolve(out).toString());assertEquals(2,run(limited),key);assertTrue(read(out,"WARNINGS.md").contains("Required action"));assertFalse(Files.exists(temp.resolve(out+"/snps.tsv")));}
    }
    @Test void outputCapQuarantinesAnActuallyStartedResultTable()throws Exception {
        var o=options("budget-base");assertEquals(0,run(o));long snapshot;try(var files=Files.list(temp.resolve("budget-base/source-inputs"))){snapshot=files.mapToLong(p->{try{return Files.size(p);}catch(Exception failure){throw new IllegalStateException(failure);}}).sum();}long budget=snapshot+Files.size(temp.resolve("budget-base/resource-ledger.tsv"))+Files.size(temp.resolve("budget-base/snps.tsv"))-1;o.put("out",temp.resolve("budget-limited").toString());o.put("max-output-bytes",""+budget);assertEquals(2,run(o));assertFalse(Files.exists(temp.resolve("budget-limited/snps.tsv")));assertTrue(Files.exists(temp.resolve("budget-limited/partial/snps.tsv")));assertTrue(read("budget-limited","WARNINGS.md").contains("--max-output-bytes"));
    }
    @Test void excessiveNearestLabelExpansionRejectsBeforeWritingAnOversizedRow()throws Exception {
        var o=options("line");Files.writeString(temp.resolve("genes.tsv"),"ensembl_gene_id\texternal_gene_name\tchromosome_name\tstart_position\tend_position\nG1\t"+"A".repeat(400)+"\t1\t100\t199\nG2\t"+"B".repeat(400)+"\t1\t100\t199\n");o.put("max-line-bytes","512");assertEquals(2,run(o));assertTrue(read("line","WARNINGS.md").contains("nearest-gene summary"));assertTrue(Files.exists(temp.resolve("line/partial/snps.tsv")));
    }
    @Test void originalSourceMutationAndSnapshotMutationAreDetected()throws Exception {
        var o=options("unused");Path stage=temp.resolve("reader");Files.createDirectory(stage);var input=new FumaAnnotationInputs(stage,new FumaAnnotationInputs.Limits(1048576,1048576,1000,10,10,1048576,100000,1000000,1000000),()->{});input.prepare(o);input.verify();Path genes=Path.of(o.get("genes"));String original=Files.readString(genes);Files.writeString(genes,original+"\n");assertTrue(assertThrows(Exception.class,input::verify).getMessage().contains("Input changed"));Files.writeString(genes,original);Files.writeString(stage.resolve("source-inputs/i0001.raw"),"tampered");assertTrue(assertThrows(Exception.class,input::verify).getMessage().contains("snapshot changed"));
    }
    @Test void existingDestinationsAndUnsupportedChoicesArePreserved()throws Exception {
        var o=options("existing");Files.createDirectory(temp.resolve("existing"));Files.writeString(temp.resolve("existing/keep"),"original");assertEquals(2,run(o));assertEquals("original",read("existing","keep"));o.put("out",temp.resolve("policy").toString());o.put("duplicates","last");assertEquals(2,run(o));assertTrue(read("policy","WARNINGS.md").contains("source-first"));
    }
    @Test void parentStagePublishesItsOwnStatusAndCompleteLedgers()throws Exception {
        Path p=parent("workflow",options("unused"),null);assertEquals(0,command(List.of("--workflow-options-file",p.toString())));assertTrue(read("workflow","manifest.json").contains("completed-with-review-warnings"));assertTrue(read("workflow","workflow-journal.json").contains("variant-annotation"));assertTrue(Files.exists(temp.resolve("workflow/variant-annotation/annotation-statistics.tsv")));
    }
    @Test void independentNativeStageSurvivesAnnotationFailure()throws Exception {
        var o=options("unused");Files.writeString(temp.resolve("reference.tsv"),"annot\tcount\tprop\nunknown\t0\t0\n");Path p=parent("failed-parent",o,nativeMagma("annotate-native"));assertEquals(2,command(List.of("--workflow-options-file",p.toString())));var result=AdmixtureInputs.decode(Files.readAllBytes(temp.resolve("failed-parent/manifest.json")));assertEquals("failed",result.get("status"));assertTrue(read("failed-parent","magma-annotation/analysis-status.json").contains("completed"));assertTrue(read("failed-parent","variant-annotation/WARNINGS.md").contains("positive"));
    }
    @Test void annotationCompletesAlongsideAnUnavailableOriginalRuntime()throws Exception {
        Path p=parent("skipped-parent",options("unused"),nativeMagma("annotate"));assertEquals(3,command(List.of("--workflow-options-file",p.toString())));assertTrue(read("skipped-parent","manifest.json").contains("partially-skipped"));assertTrue(read("skipped-parent","variant-annotation/analysis-status.json").contains("completed-with-review-warnings"));assertTrue(read("skipped-parent","magma-annotation/WARNINGS.md").contains("Required action"));
    }
    @Test void parentBuildAndMethodOverridesCannotBypassTypedContracts()throws Exception {
        var o=options("unused");Path p=parent("mismatch",o,null);String original=Files.readString(p);for(String conflict:List.of("build","method")){var root=new LinkedHashMap<Object,Object>(AdmixtureInputs.decode(original.getBytes(StandardCharsets.UTF_8)));var stage=new LinkedHashMap<String,String>(o);stage.remove("out");stage.put(conflict,conflict.equals("build")?"GRCh37":"expression");root.put("variant_annotation",stage);Files.writeString(p,RegenieRuntime.json(root));assertEquals(2,command(List.of("--workflow-options-file",p.toString())));assertFalse(Files.exists(temp.resolve("mismatch")));}
    }
    @Test void helpAndMissingResourcesNameScopeAndRequiredAction()throws Exception {
        assertEquals(0,command(List.of("--method","annotate-variants","--help")));var o=options("missing");o.put("snps",temp.resolve("not-present.tsv").toString());assertEquals(2,run(o));assertTrue(read("missing","WARNINGS.md").contains("SNP annotation candidates input"));assertTrue(read("missing","WARNINGS.md").contains("Required action"));assertFalse(read("missing","WARNINGS.md").contains("ADMIXTURE"));
    }
}
