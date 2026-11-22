/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
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

class GenotypeScoreCliTest {
    @TempDir Path root;
    private record Input(Path prefix, Path weights) { }
    private record Run(int code, String output, String error) { }
    private Input fixture(String name, boolean allMissing) throws Exception {
        Path home = root.resolve(name); Files.createDirectory(home); Path prefix = home.resolve("known"), weights = home.resolve("weights.tsv");
        Files.writeString(Path.of(prefix+".fam"),"F1 I1 0 0 1 -9\nF1 I2 0 0 2 -9\nF2 I1 0 0 1 -9\nF2 I2 0 0 2 -9\nF3 I1 0 0 1 -9\n");
        Files.writeString(Path.of(prefix+".bim"),"1 rs1 0 10 C A\n2 rs2 0 20 G T\n");
        Files.write(Path.of(prefix+".bed"),allMissing ? new byte[] {0x6c,0x1b,1,0x55,1,0x55,1} : new byte[] {0x6c,0x1b,1,0x78,0,(byte)0xe4,3});
        Files.writeString(weights,"variant\tea\toa\tweight\tea_frequency\nrs1\tC\tA\t2\t0.25\nrs2\tT\tG\t-0.5\t0.8\n");
        return new Input(prefix,weights);
    }
    private Run run(String... args) {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int code = JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Run(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private Run score(Input input, String missing, Path out, String... extra) {
        var args = new ArrayList<>(List.of("genotype-score","--bfile",input.prefix().toString(),"--weights",input.weights().toString(),"--missing",missing,"--genome-build","unspecified","--out",out.toString()));
        args.addAll(List.of(extra)); return run(args.toArray(String[]::new));
    }
    private List<String[]> rows(Path out) throws Exception { return Files.readAllLines(out.resolve("results.tsv")).stream().skip(1).map(line -> line.split("\t",-1)).toList(); }

    @Test void nativeMeanScoringPreservesAllelesIdentitiesDenominatorsAndHashes() throws Exception {
        var input = fixture("input",false); Path out = root.resolve("result"); Run run = score(input,"mean-impute",out);
        assertEquals(0,run.code(),run.error());assertTrue(run.error().contains("WARNINGS.md"));
        var rows = rows(out); assertEquals(5,rows.size()); assertEquals("F1",rows.get(0)[0]); assertEquals("F2",rows.get(2)[0]); assertEquals("I1",rows.get(2)[1]);
        double[] expected = {4,1.2,-.5,0,3};
        for (int i = 0; i < rows.size(); i++) { assertEquals(expected[i],Double.parseDouble(rows.get(i)[2]),1e-14);assertEquals(expected[i]/4,Double.parseDouble(rows.get(i)[3]),1e-14);assertEquals("4",rows.get(i)[8]); }
        assertEquals("2",rows.get(1)[7]);assertEquals("1",rows.get(1)[6]);
        assertTrue(Files.readString(out.resolve("variant-metadata.tsv")).contains("BIM_A2"));
        assertTrue(Files.readString(out.resolve("manifest.yaml")).contains(FollowupSupport.hash(Path.of(input.prefix()+".bed"))));
        assertTrue(Files.readString(out.resolve("session.yaml")).contains("imputed_cells: 2"));
    }
    @Test void omissionAverageUsesObservedAlleles() throws Exception {
        var input = fixture("input",false);Path out = root.resolve("result");assertEquals(0,score(input,"omit",out).code());
        var rows = rows(out);assertEquals(2,Double.parseDouble(rows.get(1)[2]));assertEquals(1,Double.parseDouble(rows.get(1)[3]));assertEquals("2",rows.get(1)[8]);
        assertEquals(-1,Double.parseDouble(rows.get(3)[2]));assertEquals(-.5,Double.parseDouble(rows.get(3)[3]));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("sample-specific denominators"));
    }
    @Test void allMissingOmissionFailsAndQuarantinesBlankScores() throws Exception {
        var input = fixture("input",true);Path out = root.resolve("result");Run run = score(input,"omit",out);
        assertEquals(2,run.code());assertTrue(run.error().contains("GENOTYPE SCORING FAILED"));assertFalse(Files.exists(out.resolve("results.tsv")));
        assertTrue(Files.exists(out.resolve("partial/results.tsv")));String warnings = Files.readString(out.resolve("WARNINGS.md"));assertTrue(warnings.contains("blank, not zero"));assertTrue(warnings.contains("No sample has a reportable score"));
    }
    @Test void fullyImputedScoresRetainConditionalReferenceWarning() throws Exception {
        var input = fixture("input",true);Path out = root.resolve("result");assertEquals(0,score(input,"mean-impute",out).code());
        for (String[] row : rows(out)) { assertEquals(.2,Double.parseDouble(row[2]),1e-15);assertEquals("0",row[7]);assertEquals("FULLY_IMPUTED",row[9]); }
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("not measured individual genotype differences"));
    }
    @Test void missingErrorHasNoSuccessfulScientificStreams() throws Exception {
        var input = fixture("input",false);Path out = root.resolve("result");assertEquals(2,score(input,"error",out).code());
        assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Missing dosage"));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("failed"));
    }
    @Test void duplicateAbsentAndMismatchedWeightsFailRatherThanChangingModel() throws Exception {
        for (String model : List.of("rs1\tC\tA\t2\t.25\nrs1\tC\tA\t2\t.25\n","absent\tC\tA\t1\t.25\n","rs2\tC\tA\t1\t.25\n")) {
            int index = model.hashCode();var input = fixture("input"+index,false);Files.writeString(input.weights(),"variant\tea\toa\tweight\tea_frequency\n"+model);Path out = root.resolve("out"+index);
            assertEquals(2,score(input,"mean-impute",out).code());assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));
        }
    }
    @Test void scoredSexChromosomesAndBadPaddingAreRejected() throws Exception {
        var input = fixture("sex",false);Files.writeString(Path.of(input.prefix()+".bim"),"X rs1 0 10 C A\n2 rs2 0 20 G T\n");Path out = root.resolve("sex-out");
        assertEquals(2,score(input,"mean-impute",out).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("autosomal subset"));
        var padding = fixture("padding",false);byte[] bytes = Files.readAllBytes(Path.of(padding.prefix()+".bed"));bytes[4]=4;Files.write(Path.of(padding.prefix()+".bed"),bytes);
        assertEquals(2,score(padding,"mean-impute",root.resolve("padding-out")).code());
    }
    @Test void suppliedFrequenciesAndWorkBoundsAreExplicit() throws Exception {
        var input = fixture("input",false);Files.writeString(input.weights(),"variant\tea\toa\tweight\nrs1\tC\tA\t1\n");Path out = root.resolve("frequency-out");
        assertEquals(2,score(input,"mean-impute",out).code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("supplied ea_frequency"));
        assertEquals(2,score(input,"omit",root.resolve("limit-out"),"--maximum-scored-cells","1").code());
        assertTrue(Files.readString(root.resolve("limit-out/WARNINGS.md")).contains("maximum-scored-cells"));
    }
    @Test void utf8RecipeSupportsUnicodePathsAndCommandLineOverrides() throws Exception {
        var input = fixture("数据",false);Path out = root.resolve("结果");Path recipe = root.resolve("recipe.json");Path unused = root.resolve("unused");
        Files.writeString(recipe,"{\"bfile\":"+RegenieRuntime.json(input.prefix().toString())+",\"weights\":"+RegenieRuntime.json(input.weights().toString())+",\"missing\":\"error\",\"genome-build\":\"unspecified\",\"out\":"+RegenieRuntime.json(unused.toString())+"}");
        Run run = run("genotype-score","--options-file",recipe.toString(),"--missing","omit","--out",out.toString());
        assertEquals(0,run.code(),run.error());assertEquals(5,rows(out).size());assertFalse(Files.exists(unused));assertTrue(Files.exists(out.resolve("options-input.json")));
    }
    @Test void existingOutputCannotBeOverwritten() throws Exception {
        var input = fixture("input",false);Path out = root.resolve("result");assertEquals(0,score(input,"omit",out).code());String before = FollowupSupport.hash(out.resolve("results.tsv"));
        assertEquals(2,score(input,"omit",out).code());assertEquals(before,FollowupSupport.hash(out.resolve("results.tsv")));
    }
    @Test void helpAndInvalidOptionsExplainNativeContract() {
        Run help = run("genotype-score","--help");assertEquals(0,help.code());assertTrue(help.output().contains("No installation"));assertTrue(help.output().contains("not trained"));
        Run unknown = run("genotype-score","--invented","1");assertEquals(2,unknown.code());assertTrue(unknown.error().contains("Required action"));
    }
}
