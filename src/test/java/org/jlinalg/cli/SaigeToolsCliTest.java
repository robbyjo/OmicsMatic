package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaigeToolsCliTest {
    @TempDir Path temp;
    private int run(String... args){return SaigeToolsCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private Path recipe()throws Exception {
        Path path=temp.resolve("recipe.json");Files.writeString(path,RegenieRuntime.json(Map.of("version",1,"operation","sparse-grm","genotypes","absent-cohort","genotype_coding","diploid-autosomal-plink-bed","assembly","fixture","parameters",Map.of("random_markers",1,"relatedness_cutoff",.125,"min_maf",.05,"max_missing",.05,"diagonal","observed","seed",1),"resources",Map.of("threads",1,"memory_chunk_gib",.1))));return path;
    }
    @Test void missingDependencySkipsWithProminentRecoveryAndNoResult()throws Exception {
        Path out=temp.resolve("out");assertEquals(3,run("--recipe",recipe().toString(),"--out",out.toString()));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("ANALYSIS SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));assertTrue(Files.exists(out.resolve("partial/supplied-recipe.json")));assertFalse(Files.exists(out.resolve("results.json")));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("skipped-unavailable-dependency"));
    }
    @Test void invalidRecipeFailsWithRetainedDiagnostics()throws Exception {
        Path recipe=temp.resolve("invalid.json");Files.writeString(recipe,"{}");Path out=temp.resolve("out");assertEquals(2,run("--recipe",recipe.toString(),"--out",out.toString()));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("ANALYSIS FAILED"));assertFalse(Files.exists(out.resolve("results.json")));
    }
    @Test void existingOutputIsPreserved()throws Exception {
        Path out=Files.createDirectory(temp.resolve("out"));Files.writeString(out.resolve("keep"),"original");assertEquals(2,run("--recipe",recipe().toString(),"--out",out.toString()));assertEquals("original",Files.readString(out.resolve("keep")));assertEquals(1,Files.list(out).count());
    }
    @Test void utf8OptionsCarrierSupportsUnicodeAndDirectOverrides()throws Exception {
        Path file=temp.resolve("options.json"),out=temp.resolve("résultats 样本");Files.writeString(file,RegenieRuntime.json(Map.of("recipe",recipe().toString(),"out",temp.resolve("ignored").toString())),StandardCharsets.UTF_8);assertEquals(3,run("--options-file",file.toString(),"--out",out.toString()));assertTrue(Files.exists(out.resolve("WARNINGS.md")));assertFalse(Files.exists(temp.resolve("ignored")));
    }
    @Test void unknownRecursiveAndRepeatedOptionsFailBeforeAnalysis()throws Exception {
        assertEquals(2,run("--script","arbitrary.R"));assertEquals(2,run("--out","a","--out","b"));Path file=temp.resolve("options.json");Files.writeString(file,"{\"options-file\":\"recursive.json\"}");assertEquals(2,run("--options-file",file.toString()));
    }
    @Test void unsupportedOrAbsentRuntimeSkipsRatherThanInstalls()throws Exception {
        Path out=temp.resolve("out");assertEquals(3,run("--recipe",recipe().toString(),"--home",temp.resolve("absent-home").toString(),"--out",out.toString()));assertFalse(Files.exists(temp.resolve("absent-home")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("install_saige.py"));
    }
    @Test void helpDefinesBoundedScopeAndExitStatuses()throws Exception {
        assertEquals(0,run("--help"));String help=SaigeToolsCli.help();assertTrue(help.contains("sparse-grm"));assertTrue(help.contains("Exit0"));assertTrue(help.contains("exit3"));assertTrue(help.contains("does not certify"));
    }
}
