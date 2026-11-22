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

class MotifScanCliTest {
    @TempDir Path root; private String console;
    private void inputs() throws Exception {
        Files.writeString(root.resolve("motifs.meme"),"MEME version 4\nALPHABET= ACGT\nBackground letter frequencies\nA .1 C .2 G .3 T .4\nMOTIF asym alt\nletter-probability matrix: alength= 4 w= 3 nsites= 20\n.7 .1 .1 .1\n.1 .6 .2 .1\n.2 .1 .6 .1\n");
        Files.writeString(root.resolve("seq.fa"),">chr2:101-103 description\nACG\n>short\nAC\n");
    }
    private int execute(String... args) { var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);int code=JLinAlgCli.run(args,stream,stream);console=bytes.toString(StandardCharsets.UTF_8);return code; }
    private int run(String name,String... extra) {
        var args=new ArrayList<>(List.of("motif-scan","--motifs",root.resolve("motifs.meme").toString(),"--fasta",root.resolve("seq.fa").toString(),"--out",root.resolve(name).toString()));args.addAll(List.of(extra));return execute(args.toArray(String[]::new));
    }
    private static String[] policy() { return new String[]{"--background","motif","--strands","both","--coordinates","genomic","--threshold","1"}; }

    @Test void scannerPublishesExactOriginalAsymmetricBothStrandScienceAndHashes() throws Exception {
        inputs();assertEquals(0,run("both",policy()),console);var lines=Files.readAllLines(root.resolve("both/results.tsv"));assertEquals(3,lines.size());
        var row=lines.get(1).split("\t",-1);assertEquals("chr2",row[2]);assertEquals("101",row[3]);assertEquals("103",row[4]);assertEquals("+",row[5]);assertEquals(3.996515679442508,Double.parseDouble(row[6]),1e-12);assertEquals(.015625,Double.parseDouble(row[7]));assertEquals("",row[8]);assertEquals("ACG",row[9]);
        String session=Files.readString(root.resolve("both/session.yaml"));assertTrue(session.contains("complement_background_averaging: true"));assertTrue(session.contains("No q-values"));
        assertTrue(Files.readString(root.resolve("both/manifest.yaml")).contains(FollowupSupport.hash(root.resolve("motifs.meme"))));assertTrue(Files.readString(root.resolve("both/sequence-metadata.tsv")).contains("\"chr2:101-103 description\""));
        assertTrue(Files.readString(root.resolve("both/scan-counts.tsv")).contains("asym\t2\t5\t1\t0\t2\t2"));
    }

    @Test void forwardUsesUnaveragedBackgroundAndSequenceCoordinates() throws Exception {
        inputs();assertEquals(0,run("forward","--background","motif","--strands","forward","--coordinates","sequence","--threshold","1"),console);
        var row=Files.readAllLines(root.resolve("forward/results.tsv")).get(1).split("\t",-1);assertEquals("chr2:101-103",row[2]);assertEquals("1",row[3]);assertEquals(5.3798076923076925,Double.parseDouble(row[6]),1e-12);
        double denominator=1.000002;double expected=((.1+5e-7)/denominator)*((.2+5e-7)/denominator)*((.1+.3+1e-6)/denominator);
        assertEquals(expected,Double.parseDouble(row[7]),expected*1e-12);
    }

    @Test void resourceFailureQuarantinesEarlierScientificStreamsWithActionableWarnings() throws Exception {
        inputs();Files.writeString(root.resolve("seq.fa"),">id\nACGACG\n");var args=new ArrayList<>(List.of(policy()));args.addAll(List.of("--maximum-hits","1"));assertEquals(2,run("limited",args.toArray(String[]::new)),console);
        assertFalse(Files.exists(root.resolve("limited/results.tsv")));assertTrue(Files.exists(root.resolve("limited/partial/results.tsv")));assertTrue(Files.readString(root.resolve("limited/WARNINGS.md")).contains("Required action"));assertTrue(console.contains("MOTIF ANALYSIS FAILED"));
    }

    @Test void missingPoliciesQValueOptionsAndPreAveragingInvalidBackgroundFailClearly() throws Exception {
        inputs();assertEquals(2,run("missing"));assertTrue(Files.readString(root.resolve("missing/WARNINGS.md")).contains("--strands is required"));
        assertEquals(2,run("invalid-bg","--background",".1,-.1,.6,.4","--strands","both","--coordinates","sequence"));assertTrue(Files.readString(root.resolve("invalid-bg/WARNINGS.md")).contains("before strand averaging"));
        assertEquals(2,run("unsupported-q","--qv-thresh","true"));assertFalse(Files.exists(root.resolve("unsupported-q")));assertTrue(console.contains("Unknown option"));
    }

    @Test void invalidConversionAndCoordinateMismatchAreProminentSuccessfulQcWarnings() throws Exception {
        inputs();Files.writeString(root.resolve("seq.fa"),">chr2:101-200\nACGZ\n");var args=new ArrayList<>(List.of(policy()));args.addAll(List.of("--invalid-residues","convert-n"));assertEquals(0,run("warn",args.toArray(String[]::new)),console);
        String warnings=Files.readString(root.resolve("warn/WARNINGS.md"));assertTrue(warnings.contains("explicitly converted invalid residues to N=1"));assertTrue(warnings.contains("spans differ"));assertTrue(console.contains("MOTIF SCAN WARNINGS"));
    }

    @Test void unicodeJsonRecipeDispatchWorksAndExistingOutputIsNeverOverwritten() throws Exception {
        inputs();Path unicode=root.resolve("motifs-研究.meme");Files.move(root.resolve("motifs.meme"),unicode);Path recipe=root.resolve("options.json");
        Files.writeString(recipe,"{\"motifs\":"+RegenieRuntime.json(unicode.toString())+",\"fasta\":"+RegenieRuntime.json(root.resolve("seq.fa").toString())+",\"out\":"+RegenieRuntime.json(root.resolve("unicode-研究").toString())+",\"background\":\"uniform\",\"strands\":\"both\",\"coordinates\":\"sequence\",\"threshold\":\"1\"}");
        assertEquals(0,execute("motif-scan","--options-file",recipe.toString()),console);assertTrue(Files.exists(root.resolve("unicode-研究/results.tsv")));String before=Files.readString(root.resolve("unicode-研究/manifest.yaml"));
        assertEquals(2,execute("motif-scan","--options-file",recipe.toString()));assertEquals(before,Files.readString(root.resolve("unicode-研究/manifest.yaml")));assertTrue(console.contains("Output already exists"));
    }
}
