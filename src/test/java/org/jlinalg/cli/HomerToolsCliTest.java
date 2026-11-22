package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerToolsCliTest {
    @TempDir Path root;
    private record Run(int code,String out,String error) { }
    private Run run(String... args)throws Exception{
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code;
        try(var stdout=new PrintStream(out,true,StandardCharsets.UTF_8);var stderr=new PrintStream(err,true,StandardCharsets.UTF_8)){code=HomerToolsCli.run(args,stdout,stderr);}return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private List<String> base(String mode){return new ArrayList<>(List.of(mode,"--home",root.resolve("absent").toString(),"--profile","linux-x86_64-5.1","--out",root.resolve("output").toString()));}
    @Test void helpExposesActualScopeExitStatusesAndRequiredActions()throws Exception{
        var result=run("--help");assertEquals(0,result.code);assertTrue(result.out.contains("fixed known enrichment")||result.out.contains("fixed known"));assertTrue(result.out.contains("SKIP")||result.out.contains("skip3"));assertTrue(result.out.contains("Cell Ranger"));assertTrue(result.out.contains("discovery")||result.out.contains("Discovery"));
    }
    @Test void missingRuntimeProducesProminentSkipAndRecoveryBundle()throws Exception{
        var result=run(base("check").toArray(String[]::new));assertEquals(3,result.code);assertTrue(result.error.contains("SKIPPED"));
        String warning=Files.readString(root.resolve("output/WARNINGS.md"));assertTrue(warning.contains("ANALYSIS SKIPPED"));assertTrue(warning.contains("Required action:"));assertTrue(warning.contains("Linux")||warning.contains("missing"));assertFalse(Files.exists(root.resolve("output/results.tsv")));
    }
    @Test void existingOutputIsNeverOverwritten()throws Exception{
        Files.createDirectory(root.resolve("output"));Files.writeString(root.resolve("output/sentinel"),"preserve");var result=run(base("check").toArray(String[]::new));assertEquals(2,result.code);assertEquals("preserve",Files.readString(root.resolve("output/sentinel")));
    }
    @Test void unsupportedNormalizationSkipsRatherThanSilentlyUsingNone()throws Exception{
        var options=base("known");options.addAll(List.of("--targets","targets.fa","--background","background.fa","--motifs","motifs.motif","--strands","both","--alphabet","strict-dna","--duplicate-ids","error","--overlap-policy","error","--statistic","binomial","--normalization","oligo3"));
        var result=run(options.toArray(String[]::new));assertEquals(3,result.code);assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("normalization is not admitted"));assertFalse(Files.exists(root.resolve("output/results.tsv")));
    }
    @Test void inapplicableFindOptionsFailInsteadOfBeingIgnored()throws Exception{
        var options=base("find");options.addAll(List.of("--targets","targets.fa","--motifs","motifs.motif","--strands","both","--alphabet","strict-dna","--duplicate-ids","error","--offset","0","--background","not-used.fa"));
        var result=run(options.toArray(String[]::new));assertEquals(2,result.code);assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("not silently ignored"));
    }
    @Test void unknownRecursiveAndNonStringOptionsFailBeforePublication()throws Exception{
        Path options=root.resolve("options.json");Files.writeString(options,"{\"options-file\":\"again.json\"}");assertEquals(2,run("check","--options-file",options.toString()).code);assertFalse(Files.exists(root.resolve("output")));
        Files.writeString(options,"{\"threads\":2}");assertEquals(2,run("known","--options-file",options.toString()).code);assertEquals(2,run("known","--unknown","true").code);
    }
    @Test void utf8OptionsPreserveUnicodeRequestedPathsThroughDependencySkip()throws Exception{
        Path options=root.resolve("ascii-options.json"),output=root.resolve("résultats 样本");Files.writeString(options,RegenieRuntime.json(java.util.Map.of("home",root.resolve("absent").toString(),"profile","linux-x86_64-4.11.1","out",output.toString())));
        assertEquals(3,run("check","--options-file",options.toString()).code);assertTrue(Files.readString(output.resolve("session.json")).contains("4.11.1"));assertTrue(Files.exists(output.resolve("WARNINGS.md")));
    }
    @Test void discoveryHelpStatesPrecisionSemanticsAndRetractsDenseCapacitySkip()throws Exception{
        var result=run("--help");assertEquals(0,result.code);assertTrue(result.out.contains("homer-tools denovo"));assertTrue(result.out.contains("earlier capacity skips were incorrect"));assertTrue(result.out.contains("conservative presence envelopes"));assertTrue(result.out.contains("sentinels"));
    }
    @Test void discoveryRequiresItsExplicitContrastAndSearchChoicesBeforeRuntime()throws Exception{
        var options=base("denovo");options.addAll(List.of("--targets","targets.fa","--background","background.fa","--strands","both","--alphabet","strict-dna","--duplicate-ids","error","--overlap-policy","retain","--statistic","binomial","--normalization","none"));
        assertEquals(2,run(options.toArray(String[]::new)).code);assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("motif-length"));
    }
    @Test void discoveryDoesNotAcceptIgnoredSuppliedMatricesOrFixedSiteOptions()throws Exception{
        var options=base("denovo");options.addAll(List.of("--targets","targets.fa","--background","background.fa","--strands","both","--alphabet","strict-dna","--duplicate-ids","error","--overlap-policy","retain","--statistic","binomial","--normalization","none","--motif-length","6","--mismatches","1","--motifs-to-find","2","--discovery-mode","full-mask","--motifs","unused.motif"));
        assertEquals(2,run(options.toArray(String[]::new)).code);assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("fixed-model option"));
    }
    @Test void knownDoesNotSilentlyIgnoreDiscoverySettings()throws Exception{
        var options=base("known");options.addAll(List.of("--targets","targets.fa","--motifs","motifs.motif","--strands","both","--alphabet","strict-dna","--duplicate-ids","error","--motif-length","6"));
        assertEquals(2,run(options.toArray(String[]::new)).code);assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("discovery option"));
    }
}
