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
import static org.junit.jupiter.api.Assertions.*;

class FumaNoveltyCliTest {
    @TempDir Path directory;
    private String message;
    private int run(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();try(var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8)){int code=FumaToolsCli.run(args,out,err);message=output.toString(StandardCharsets.UTF_8)+error.toString(StandardCharsets.UTF_8);return code;}}
    private int run(Map<String,String> options){var args=new ArrayList<String>();options.forEach((k,v)->{args.add("--"+k);args.add(v);});return run(args.toArray(String[]::new));}
    private Map<String,String> options(String name)throws Exception{return FumaNoveltyTestData.options(directory.resolve(name));}
    private Path out(Map<String,String> o){return Path.of(o.get("out"));}
    private Map<?,?> session(Path path)throws Exception{return AdmixtureInputs.decode(Files.readAllBytes(path.resolve("session.json")));}
    private void failed(Map<String,String> o)throws Exception{assertEquals(2,run(o),message);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("Required action"));assertTrue(Files.readString(out(o).resolve("analysis-status.json")).contains("failed"));assertFalse(Files.exists(out(o).resolve("locus-novelty.tsv")));assertTrue(Files.isDirectory(out(o).resolve("partial")));}
    @Test void dispatchRetainsKnownNovelAndUnassessablePopulationsAndEverySourceRow()throws Exception {
        var o=options("complete");assertEquals(0,run(o),message);var s=session(out(o));assertEquals(4,((Number)s.get("lead_rows")).intValue());assertEquals(6,((Number)s.get("known_rows")).intValue());assertEquals(4,((Number)s.get("known_match_rows")).intValue());assertEquals(1,((Number)s.get("known_within_window")).intValue());assertEquals(2,((Number)s.get("novel_relative_to_reference")).intValue());assertEquals(1,((Number)s.get("unassessable_reference_coverage")).intValue());
        var summary=Files.readAllLines(out(o).resolve("locus-novelty.tsv"));assertEquals(5,summary.size());assertTrue(summary.get(1).endsWith("\ttrue\t4\t0\tKNOWN_WITHIN_WINDOW"));assertTrue(summary.get(2).endsWith("\ttrue\t0\tNA\tNOVEL_RELATIVE_TO_REFERENCE"));assertTrue(summary.get(3).endsWith("\tfalse\t0\tNA\tUNASSESSABLE_REFERENCE_COVERAGE"));assertEquals(7,Files.readAllLines(out(o).resolve("known-rows.tsv")).size());assertEquals(4,Files.readAllLines(out(o).resolve("coverage-rows.tsv")).size());assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("never relabel them novel"));
    }
    @Test void strict250kbBoundaryExcludesBothEndpointsAndKeepsDuplicatedCenterStudies()throws Exception {
        var o=options("strict");o.put("boundary","strict");assertEquals(0,run(o),message);var rows=Files.readAllLines(out(o).resolve("known-matches.tsv"));assertEquals(3,rows.size());assertTrue(rows.get(1).startsWith("1\t3\t"));assertTrue(rows.get(2).startsWith("1\t4\t"));assertTrue(rows.stream().skip(1).allMatch(s->s.endsWith("\t0")));
    }
    @Test void allKnownTraitsRequiresWildcardCoverageAndRetainsCrossTraitEvidence()throws Exception {
        var o=options("all-traits");o.put("trait-policy","all-known");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("trait *"));Files.writeString(Path.of(o.get("coverage")),"trait\tchromosome\tstart\tend\n*\t1\t1\t2500000\n");o.put("out",out(o).resolveSibling("wildcard").toString());assertEquals(0,run(o),message);assertEquals(10,((Number)session(out(o)).get("known_match_rows")).intValue());assertTrue(Files.readString(out(o).resolve("known-matches.tsv")).contains("other-trait\ttrait-c"));assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("ALL SUPPLIED KNOWN TRAITS SELECTED"));
    }
    @Test void absentCoverageDoesNotTurnNoMatchIntoNoveltyOrSuppressKnownEvidence()throws Exception {
        var o=options("unknown-coverage");o.put("coverage","none");assertEquals(0,run(o),message);var s=session(out(o));assertEquals(0,((Number)s.get("novel_relative_to_reference")).intValue());assertEquals(3,((Number)s.get("unassessable_reference_coverage")).intValue());assertEquals(1,((Number)s.get("known_within_window")).intValue());assertEquals(1,Files.readAllLines(out(o).resolve("coverage-rows.tsv")).size());
    }
    @Test void headerOnlyKnownResourceHasNoEvidenceBeyondDeclaredCoverage()throws Exception {
        var o=options("empty-known");Files.writeString(Path.of(o.get("known")),"variant\ttrait\tchromosome\tposition\n");assertEquals(0,run(o),message);var s=session(out(o));assertEquals(0,((Number)s.get("known_rows")).intValue());assertEquals(3,((Number)s.get("novel_relative_to_reference")).intValue());assertEquals(1,((Number)s.get("unassessable_reference_coverage")).intValue());assertEquals(1,Files.readAllLines(out(o).resolve("known-matches.tsv")).size());
    }
    @Test void emptyCoverageTableIsUnknownInsteadOfAnImplicitGenomeWideAssertion()throws Exception {
        var o=options("empty-coverage");Files.writeString(Path.of(o.get("coverage")),"trait\tchromosome\tstart\tend\n");assertEquals(0,run(o),message);assertEquals(3,((Number)session(out(o)).get("unassessable_reference_coverage")).intValue());
    }
    @Test void carrierPathsResolveRelativeAndExactSnapshotsIncludeAdditionalMetadataAndCRLF()throws Exception {
        var o=options("carrier");Path base=out(o).getParent();for(String key:List.of("leads","known","coverage")){Path p=Path.of(o.get(key));Files.writeString(p,Files.readString(p).replace("\n","\r\n"));o.put(key,p.getFileName().toString());}o.put("out","analysis");Path carrier=FumaNoveltyTestData.carrier(base.resolve("options.json"),o);assertEquals(0,run("--novelty-options-file",carrier.toString()),message);var ledger=Files.readAllLines(base.resolve("analysis/resource-ledger.tsv"));assertEquals(5,ledger.size());for(String row:ledger.subList(1,ledger.size())){var f=row.split("\t",-1);assertArrayEquals(Files.readAllBytes(Path.of(f[1])),Files.readAllBytes(base.resolve("analysis").resolve(f[2])));assertEquals(FollowupSupport.hash(Path.of(f[1])),f[3]);}assertTrue(Files.readString(base.resolve("analysis/source-inputs/i0003.raw")).contains("study-four\r\n"));
    }
    @Test void everyUnmatchedKnownRowAndDuplicateStudyIsAuditedWithoutDeduplication()throws Exception {
        var o=options("duplicates");assertEquals(0,run(o),message);var rows=Files.readAllLines(out(o).resolve("known-rows.tsv"));assertTrue(rows.get(3).startsWith("3\t4\tcenter\t"));assertTrue(rows.get(4).startsWith("4\t5\tcenter\t"));assertTrue(rows.get(5).endsWith("\t0\tno-selected-nearby-lead"));assertTrue(rows.get(6).contains("outside-chromosome"));assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("not deduplicated into independent evidence"));
    }
    @Test void literalChromosomeAndTraitIdsAreNotNormalized()throws Exception {
        var o=options("literal");Path p=Path.of(o.get("known"));Files.writeString(p,Files.readString(p).replace("\ttrait-a\t1\t","\tTrait-A\tchr1\t"));assertEquals(0,run(o),message);assertEquals(0,((Number)session(out(o)).get("known_match_rows")).intValue());assertTrue(Files.readString(out(o).resolve("known-rows.tsv")).contains("Trait-A\tchr1"));
    }
    @Test void combinedInputRowBudgetIncludesAllThreeTables()throws Exception {
        var o=options("rows");o.put("max-rows","12");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("Combined novelty input --max-rows"));
    }
    @Test void perFileSnapshotAndScientificByteBudgetsRetainFailureMetadata()throws Exception {
        for(String key:List.of("max-bytes","max-snapshot-bytes","max-output-bytes")){var o=options(key);o.put(key,"1");failed(o);assertTrue(Files.isRegularFile(out(o).resolve("session.json")));assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains(key)||key.equals("max-bytes"));}
    }
    @Test void completeCellMatchOutputRowAndOperationBudgetsRejectInsteadOfTruncating()throws Exception {
        for(var bound:Map.of("max-cells","10","max-matches","1","max-output-rows","1","max-operations","1").entrySet()){var o=options(bound.getKey());o.put(bound.getKey(),bound.getValue());failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("exceeded"));}
    }
    @Test void inputAndEmittedLineBoundsApplyBeforeUnboundedMaterialization()throws Exception {
        var o=options("long-input");Path p=Path.of(o.get("known"));Files.writeString(p,Files.readString(p).replace("study-four","x".repeat(700)));o.put("max-line-bytes","512");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("input line exceeds --max-line-bytes"));o=options("long-emitted");o.put("max-line-bytes","64");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("emitted novelty row"));
    }
    @Test void malformedOrMissingHeadersAreRejectedWithSourceContext()throws Exception {
        int i=0;for(String header:List.of("variant\ttrait\tposition\n","variant\ttrait\tchromosome\tposition\tposition\n","variant\ttrait\tchromosome\t\n")){var o=options("header-"+(i++));Files.writeString(Path.of(o.get("known")),header);failed(o);assertTrue(Files.readString(out(o).resolve("session.json")).contains("known-associations"));}
    }
    @Test void malformedBlankControlAndNonintegerCoordinateRowsAreRejected()throws Exception {
        int i=0;for(String row:List.of("\n","k\tt\t1\n","k\tt\t1\t1.0\n","k\tt\t1\t0\n","k\tt\t1\t2147483648\n","k\tt\t1\t999999999999999999999999\n","k\tt\u0000\t1\t10\n")){var o=options("bad-row-"+(i++));Files.writeString(Path.of(o.get("known")),"variant\ttrait\tchromosome\tposition\n"+row);failed(o);}
    }
    @Test void invalidUtf8AndOversizedHeaderColumnFamiliesFail()throws Exception {
        var o=options("utf8");Files.write(Path.of(o.get("known")),new byte[]{(byte)0xc3,(byte)0x28});failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("Invalid UTF-8"));o=options("columns");var header=new ArrayList<String>();for(int i=0;i<257;i++)header.add("c"+i);Files.writeString(Path.of(o.get("known")),String.join("\t",header)+"\n");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("maximum256 columns"));
    }
    @Test void duplicateLeadKeyEmptyLeadPopulationAndBuildMismatchDoNotPublishResults()throws Exception {
        var o=options("duplicate-lead");Path p=Path.of(o.get("leads"));Files.writeString(p,Files.readString(p)+Files.readAllLines(p).get(1)+"\n");failed(o);o=options("empty-leads");Files.writeString(Path.of(o.get("leads")),"locus\ttrait\tchromosome\tposition\n");failed(o);o=options("build-mismatch");o.put("known-build","hg19");failed(o);assertTrue(Files.readString(out(o).resolve("WARNINGS.md")).contains("builds differ"));
    }
    @Test void requiredPoliciesAndInvalidLimitsCannotBeSilentlyDefaulted()throws Exception {
        for(String key:List.of("radius","boundary","trait-policy","coverage","known-build")){var o=options("missing-"+key);o.remove(key);assertEquals(2,run(o),message);assertFalse(Files.exists(out(o)));assertTrue(message.contains("Required action"));}for(var entry:Map.of("radius","0","boundary","default","trait-policy","fuzzy","timeout","0").entrySet()){var o=options("invalid-"+entry.getKey());o.put(entry.getKey(),entry.getValue());failed(o);}
    }
    @Test void unknownRecursiveAndNonstringCarrierFieldsFailBeforeOutput()throws Exception {
        for(String key:List.of("novelty-options-file","unknown","max-rows")){var o=options("bad-carrier-"+key);var values=new LinkedHashMap<String,Object>(o);values.put(key,key.equals("max-rows")?10:"bad");Path carrier=FumaNoveltyTestData.carrier(directory.resolve(key+".json"),values);assertEquals(2,run("--novelty-options-file",carrier.toString()),message);assertFalse(Files.exists(out(o)));assertTrue(message.contains("Required action"));}
    }
    @Test void directCarrierOverridesAreExplicitAndWrongMethodFails()throws Exception {
        var o=options("override");Path carrier=FumaNoveltyTestData.carrier(directory.resolve("options.json"),o),actual=directory.resolve("actual");assertEquals(0,run("--novelty-options-file",carrier.toString(),"--out",actual.toString()),message);assertFalse(Files.exists(out(o)));assertTrue(Files.exists(actual.resolve("locus-novelty.tsv")));assertEquals(2,run("--novelty-options-file",carrier.toString(),"--method","other"),message);
    }
    @Test void existingOutputIsNotOverwritten()throws Exception {
        var o=options("existing");Files.createDirectory(out(o));Files.writeString(out(o).resolve("sentinel"),"unchanged");assertEquals(2,run(o),message);assertEquals("unchanged",Files.readString(out(o).resolve("sentinel")));try(var files=Files.list(out(o))){assertEquals(1,files.count());}
    }
    @Test void readerVerifiesBothOriginalAndSnapshotAndTracksEveryScientificByte()throws Exception {
        for(boolean snapshot:List.of(false,true)){Path base=directory.resolve("verify-"+snapshot);Files.createDirectory(base);Path source=base.resolve("source.tsv");Files.writeString(source,"A\n1\n");Path bundle=base.resolve("bundle");Files.createDirectory(bundle);var input=new FumaNoveltyInputs(bundle,new FumaNoveltyInputs.Limits(10000,100,1024,1000,10000,10000,100),()->{});Path copy=input.capture(source,"test",10000);Files.writeString(snapshot?copy:source,"A\n2\n");assertThrows(Exception.class,input::verify);}
        var o=options("bytes");assertEquals(0,run(o),message);long bytes;try(var files=Files.walk(out(o))){bytes=files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().endsWith(".tsv")||p.getParent().getFileName().toString().equals("source-inputs")).mapToLong(p->{try{return Files.size(p);}catch(Exception e){throw new IllegalStateException(e);}}).sum();}assertEquals(bytes,((Number)session(out(o)).get("scientific_files_and_snapshot_bytes")).longValue());
    }
    @Test void unicodeInputsAndOutputWorkThroughAsciiCarrier()throws Exception {
        var o=options("日本語-data");o.put("out",directory.resolve("β-output/結果").toString());Path carrier=FumaNoveltyTestData.carrier(directory.resolve("ascii-options.json"),o);assertEquals(0,run("--novelty-options-file",carrier.toString()),message);assertTrue(Files.readString(out(o).resolve("source-inputs/i0002.raw")).contains("α"));
    }
    @Test void independentTypedNoveltyCompletesAfterAnotherNativeStageFails()throws Exception {
        var o=options("parent-failure");Path base=out(o).getParent(),recipe=FumaNoveltyTestData.parent(base,o,Map.of("variant_annotation",Map.of("build","GRCh37")));assertEquals(2,run("--workflow-options-file",recipe.toString()),message);assertTrue(Files.isRegularFile(base.resolve("workflow/locus-novelty/locus-novelty.tsv")));assertTrue(Files.readString(base.resolve("workflow/workflow-journal.json")).contains("locus-novelty"));
    }
    @Test void independentTypedNoveltyCompletesWhenOriginalMagmaAnalysisIsSkipped()throws Exception {
        var o=options("parent-skip");Path base=out(o).getParent();var annotate=Map.of("method","annotate","home",base.resolve("missing-home").toString(),"snps",base.resolve("missing-snps").toString(),"genes",base.resolve("missing-genes").toString());Path recipe=FumaNoveltyTestData.parent(base,o,Map.of("magma_annotate",annotate));assertEquals(3,run("--workflow-options-file",recipe.toString()),message);assertTrue(Files.isRegularFile(base.resolve("workflow/locus-novelty/locus-novelty.tsv")));assertTrue(Files.readString(base.resolve("workflow/magma-annotation/WARNINGS.md")).contains("Required action"));
    }
    @Test void typedParentRejectsMethodBuildAndRecursiveCarrierOverridesBeforeOutput()throws Exception {
        for(String key:List.of("method","build","novelty-options-file")){var o=options("parent-invalid-"+key);Path base=out(o).getParent();Path recipe=FumaNoveltyTestData.parent(base,o,Map.of());var root=new LinkedHashMap<String,Object>();AdmixtureInputs.decode(Files.readAllBytes(recipe)).forEach((k,v)->root.put(k.toString(),v));var stage=new LinkedHashMap<String,Object>();o.forEach(stage::put);stage.remove("out");stage.put(key,"wrong");root.put("locus_novelty",stage);FumaNoveltyTestData.carrier(recipe,root);assertEquals(2,run("--workflow-options-file",recipe.toString()),message);assertFalse(Files.exists(base.resolve("workflow")));}
    }
    @Test void helpExposesRequiredChoicesAndUnassessableReferenceMeaning(){assertEquals(0,run("--method","locus-novelty","--help"),message);assertTrue(message.contains("--trait-policy exact|all-known"));assertTrue(message.contains("UNASSESSABLE"));assertEquals(0,run("--help"),message);assertTrue(message.contains("--method locus-novelty --help"));}
}
