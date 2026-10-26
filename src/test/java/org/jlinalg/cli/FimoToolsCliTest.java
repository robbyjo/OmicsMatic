/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FimoToolsCliTest {
    @TempDir Path root;private String console;
    private static String motif() { return "MEME version 4\nALPHABET= ACGT\nMOTIF A8 polyA\nletter-probability matrix: alength= 4 w= 8 nsites= 100\n"+".97 .01 .01 .01\n".repeat(8); }
    private void inputs() throws Exception { Files.writeString(root.resolve("motifs.meme"),motif());Files.writeString(root.resolve("seq.fa"),">probe\nAAAAAAAA\n"); }
    private Path home() { String home=System.getenv("JLINALG_FIMO_HOME");assumeTrue(home!=null && Files.isRegularFile(Path.of(home).resolve(".jlinalg-fimo.json")),"Original FIMO profile is optional; set JLINALG_FIMO_HOME for actual original-engine tests");return Path.of(home); }
    private int execute(String... args) { var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);int code=JLinAlgCli.run(args,stream,stream);console=bytes.toString(StandardCharsets.UTF_8);return code; }
    private int run(String name,boolean actual,String... extra) {
        var o=new LinkedHashMap<String,String>();o.put("motifs",root.resolve("motifs.meme").toString());o.put("fasta",root.resolve("seq.fa").toString());o.put("out",root.resolve(name).toString());o.put("background","uniform");o.put("strands","both");o.put("coordinates","genomic");o.put("threshold-type","p");o.put("threshold","1");
        if(actual)o.put("home",home().toString());for(int i=0;i<extra.length;i+=2)o.put(extra[i],extra[i+1]);
        var args=new ArrayList<>(List.of("fimo-tools"));o.forEach((key,value)->args.addAll(List.of("--"+key,value)));return execute(args.toArray(String[]::new));
    }
    private Path copyHome(String name) throws Exception {
        Path source=home(),target=root.resolve(name);try(var paths=Files.walk(source)) { for(Path path:paths.sorted(Comparator.naturalOrder()).toList()) { Path to=target.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(to);else Files.copy(path,to); } }return target;
    }
    private List<String[]> results(String name) throws Exception { return Files.readAllLines(root.resolve(name+"/results.tsv")).stream().skip(1).map(l->l.split("\t",-1)).toList(); }

    @Test void missingRuntimeSkipsAnalysisWithPersistentRecoveryAndNoSuccessTable() throws Exception {
        inputs();assertEquals(3,run("missing",false),console);assertTrue(console.contains("SKIPPED FIMO ANALYSIS"));assertFalse(Files.exists(root.resolve("missing/results.tsv")));
        String warning=Files.readString(root.resolve("missing/WARNINGS.md"));assertTrue(warning.contains("Required action"));assertTrue(warning.contains("install_fimo.py"));assertTrue(warning.contains("installation is forbidden, SKIP"));
        assertTrue(Files.readString(root.resolve("missing/analysis-status.json")).contains("skipped-dependency-unavailable"));
    }

    @Test void unknownQTextCombinationAndUnsupportedFlagsFailBeforeStartingTheEngine() throws Exception {
        inputs();assertEquals(2,run("qtext",false,"format","text","threshold-type","q"));assertTrue(Files.readString(root.resolve("qtext/WARNINGS.md")).contains("Q-value thresholds"));assertFalse(Files.exists(root.resolve("qtext/version.stdout")));
        assertEquals(2,run("textcap",false,"format","text","maximum-stored-scores","10"));assertTrue(Files.readString(root.resolve("textcap/WARNINGS.md")).contains("applies only to full"));
        assertEquals(2,execute("fimo-tools","--shell-command","anything"));assertTrue(console.contains("Unknown option"));
    }

    @Test void changedBinaryIsNotAdmittedOrLaunched() throws Exception {
        inputs();Path profile=copyHome("changed-profile");Path binary=profile.resolve("bin/fimo.exe");byte[] bytes=Files.readAllBytes(binary);bytes[0]^=1;Files.write(binary,bytes);
        assertEquals(3,run("changed",false,"home",profile.toString()),console);assertFalse(Files.exists(root.resolve("changed/version.stdout")));assertTrue(Files.readString(root.resolve("changed/WARNINGS.md")).contains("runtime integrity failed"));
    }

    @Test void unlistedDllCannotExtendTheAuthenticatedDependencyClosure() throws Exception {
        inputs();Path profile=copyHome("extra-profile");Files.writeString(profile.resolve("bin/unlisted.dll"),"not an original runtime member");assertEquals(3,run("extra",false,"home",profile.toString()),console);
        assertFalse(Files.exists(root.resolve("extra/version.stdout")));assertTrue(Files.readString(root.resolve("extra/WARNINGS.md")).contains("unlisted files"));
    }

    @Test void actualOriginalTextPreservesPValuesBlankQAndHeaderMetadata() throws Exception {
        inputs();assertEquals(0,run("text",true,"format","text"),console);var rows=results("text");assertEquals(2,rows.size());assertEquals("+",rows.get(0)[5]);assertEquals(1.0/65536,Double.parseDouble(rows.get(0)[7]),5e-8);assertEquals("",rows.get(0)[8]);assertEquals("TTTTTTTT",rows.get(1)[9]);
        assertTrue(Files.readString(root.resolve("text/session.yaml")).contains("q_values: false"));assertTrue(Files.readString(root.resolve("text/sequence-metadata.tsv")).contains("\"probe\""));assertFalse(Files.exists(root.resolve("text/working")));
        assertTrue(Files.readString(root.resolve("text/manifest.yaml")).contains(FollowupSupport.hash(root.resolve("motifs.meme"))));
    }

    @Test void actualOriginalFullQMatchesIndependentTwoSiteBhReferenceAndRetainsReports() throws Exception {
        inputs();assertEquals(0,run("q",true),console);var rows=results("q");assertEquals(2,rows.size());var top=rows.stream().filter(r->r[5].equals("+")).findFirst().orElseThrow();assertEquals(1.0/32768,Double.parseDouble(top[8]),5e-8);
        assertTrue(Files.size(root.resolve("q/upstream/fimo.html"))>1000,"Original HTML report");assertTrue(Files.readString(root.resolve("q/upstream/fimo.html")).contains("FIMO"));assertTrue(Files.exists(root.resolve("q/upstream/cisml.xml")));assertTrue(Files.readString(root.resolve("q/session.yaml")).contains("original_sequences: 1"));assertTrue(Files.readString(root.resolve("q/WARNINGS.md")).contains("pi_zero=1.0"));assertTrue(console.contains("FIMO WARNINGS"));
    }

    @Test void actualQThresholdSelectionAndNoQModeRemainDistinct() throws Exception {
        inputs();assertEquals(0,run("q-only",true,"threshold-type","q","threshold",".001"),console);assertEquals(1,results("q-only").size());
        assertEquals(0,run("no-q",true,"q-values","false"),console);assertTrue(results("no-q").stream().allMatch(r->r[8].isEmpty()));assertTrue(Files.readString(root.resolve("no-q/WARNINGS.md")).contains("zero placeholders"));assertTrue(Files.readString(root.resolve("no-q/upstream/fimo.tsv")).contains("\t0\tAAAAAAAA"));
    }

    @Test void completeHeaderlessZeroHitOutputGetsACanonicalHeaderAfterOriginalSummaryValidation() throws Exception {
        inputs();assertEquals(0,run("zero",true,"threshold","1e-10"),console);assertEquals(0,results("zero").size());assertTrue(Files.readString(root.resolve("zero/session.yaml")).contains("upstream_headerless_empty_table: true"));assertTrue(Files.exists(root.resolve("zero/upstream/fimo.tsv")));
    }

    @Test void completeHeaderlessTextScanRequiresOriginalLoadedMotifStrandEvidence() throws Exception {
        inputs();assertEquals(0,run("zero-text",true,"format","text","threshold","1e-10"),console);assertEquals(0,results("zero-text").size());String session=Files.readString(root.resolve("zero-text/session.yaml"));assertTrue(session.contains("upstream_headerless_empty_table: true"));assertTrue(session.contains("original_text_motif_strands_and_widths"));assertTrue(session.contains("A8"));
    }

    @Test void originalStoredHitCapExitZeroIsRejectedAndEveryScientificProductIsQuarantined() throws Exception {
        inputs();Files.writeString(root.resolve("seq.fa"),java.util.stream.IntStream.range(0,5).mapToObj(i->">read"+i+"\n"+"A".repeat(16)+"\n").collect(java.util.stream.Collectors.joining()));
        assertEquals(2,run("cap",true,"maximum-stored-scores","10"),console);assertTrue(Files.exists(root.resolve("cap/partial/upstream/fimo.tsv")));assertTrue(Files.exists(root.resolve("cap/partial/sequence-metadata.tsv")));assertFalse(Files.exists(root.resolve("cap/results.tsv")));
        String warning=Files.readString(root.resolve("cap/WARNINGS.md"));assertTrue(warning.contains("INCOMPLETE ORIGINAL FIMO OUTPUT"));assertTrue(warning.contains("cannot support a negative result"));assertTrue(warning.contains("raise --maximum-stored-scores"));assertTrue(Files.readString(root.resolve("cap/fimo.stderr")).contains("Reached max stored scores"));
    }

    @Test void originalInputErrorsKeepActualLogsAndDoNotPublishSuccess() throws Exception {
        inputs();Files.writeString(root.resolve("motifs.meme"),motif().replaceFirst("\\.97 \\.01 \\.01 \\.01","1.2 .01 .01 .01"));assertEquals(2,run("bad-input",true),console);
        assertTrue(Files.exists(root.resolve("bad-input/fimo.stderr")));assertFalse(Files.exists(root.resolve("bad-input/results.tsv")));assertTrue(Files.readString(root.resolve("bad-input/WARNINGS.md")).contains("Original FIMO analysis exited"));
    }

    @Test void originalRoundedMemeRowsAreNotSilentlyNormalizedByTheAdapter() throws Exception {
        inputs();Files.writeString(root.resolve("motifs.meme"),motif().replace(".97 .01 .01 .01",".333333 .333333 .333333 0"));assertEquals(0,run("rounded",true,"strands","forward"),console);
        assertEquals(1,results("rounded").size());assertEquals(Math.pow(.75,8),Double.parseDouble(results("rounded").get(0)[7]),5e-4);assertTrue(Files.readString(root.resolve("rounded/session.yaml")).contains("not normalized or repaired"));
    }

    @Test void selectedMotifAndUnknownMotifHaveExplicitDifferentOutcomes() throws Exception {
        inputs();Files.writeString(root.resolve("motifs.meme"),motif()+"\nMOTIF OTHER\nletter-probability matrix: alength= 4 w= 1\n.25 .25 .25 .25\n");assertEquals(0,run("selected",true,"motif-id","A8"),console);assertEquals(2,results("selected").size());assertTrue(results("selected").stream().allMatch(r->r[0].equals("A8")));
        assertEquals(2,run("unknown",false,"motif-id","ABSENT"));assertFalse(Files.exists(root.resolve("unknown/version.stdout")));assertTrue(Files.readString(root.resolve("unknown/WARNINGS.md")).contains("--motif-id is absent"));
    }

    @Test void genuineCustomBackgroundIsPassedUnchangedWithOriginalForwardScoring() throws Exception {
        inputs();Path bg=root.resolve("background.txt");Files.writeString(bg,"A .1\nC .2\nG .3\nT .4\n");assertEquals(0,run("bg",true,"background","file","background-file",bg.toString(),"strands","forward"),console);assertEquals(1,results("bg").size());
        assertTrue(Double.parseDouble(results("bg").get(0)[7])<2e-8);assertTrue(Files.readString(root.resolve("bg/manifest.yaml")).contains(FollowupSupport.hash(bg)));
    }

    @Test void gzipUnicodeRecipeAndRepeatedOutputProtectionUseTheActualOriginalEngine() throws Exception {
        inputs();Path mf=root.resolve("motifs-研究.meme");Files.move(root.resolve("motifs.meme"),mf);Path fa=root.resolve("seq-研究.fa.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(fa))) { out.write(">chr2:101-108 utf8\nAAAAAAAA\n".getBytes(StandardCharsets.UTF_8)); }
        Path target=root.resolve("analysis-研究"),recipe=root.resolve("options.json");var options=new LinkedHashMap<String,String>(Map.of("motifs",mf.toString(),"fasta",fa.toString(),"home",home().toString(),"out",target.toString(),"background","uniform","strands","both","coordinates","genomic","threshold-type","q","threshold",".001"));Files.writeString(recipe,RegenieRuntime.json(options));
        assertEquals(0,execute("fimo-tools","--options-file",recipe.toString()),console);assertEquals(2,Files.readAllLines(target.resolve("results.tsv")).size());assertTrue(Files.readString(target.resolve("results.tsv")).contains("chr2\t101\t108"));assertFalse(Files.exists(target.resolve("working")));
        String before=FollowupSupport.hash(target.resolve("manifest.yaml"));assertEquals(2,execute("fimo-tools","--options-file",recipe.toString()));assertEquals(before,FollowupSupport.hash(target.resolve("manifest.yaml")));assertTrue(console.contains("Output already exists"));
    }
}
