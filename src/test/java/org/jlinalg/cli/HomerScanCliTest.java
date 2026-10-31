package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerScanCliTest {
    @TempDir Path directory;
    private Path fasta,motifs;
    private List<String> arguments(Path output)throws Exception{
        if(fasta==null){fasta=directory.resolve("input.fa");motifs=directory.resolve("motifs.motif");Files.writeString(fasta,">S source header\nACNACGT\n");Files.writeString(motifs,">AC\tM\t2\t0\n.97\t.01\t.01\t.01\n.01\t.97\t.01\t.01\n");}
        return new ArrayList<>(List.of("--motifs",motifs.toString(),"--fasta",fasta.toString(),"--out",output.toString(),"--strands","both","--offset","0","--alphabet","strict-dna","--duplicate-ids","error"));
    }
    private int run(List<String> args)throws Exception{
        try(var out=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8);var err=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8)){
            return HomerScanCli.run(args.toArray(String[]::new),out,err);
        }
    }
    private Path output(){return directory.resolve("results");}
    @Test void completeCommandPreservesRawInputsAndEmitsExplicitCoordinatesAndReviewActions()throws Exception{
        Path out=output();var args=arguments(out);String raw=Files.readString(fasta);assertEquals(0,run(args));
        var lines=Files.readAllLines(out.resolve("results.tsv"));assertEquals(4,lines.size());assertTrue(lines.get(0).contains("homer_position"));assertTrue(lines.get(1).contains("\t0\t2\t+\t0\tAC\t"));
        assertTrue(lines.get(3).contains("\t5\t7\t-\t6\tGT\t"));assertEquals(raw,Files.readString(out.resolve("input-fasta")));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("not motif P/q/E values"));
    }
    @Test void invalidMatrixQuarantinesEveryProvisionalInputAndTable()throws Exception{
        Path out=output();var args=arguments(out);Files.writeString(motifs,">A\tM\t1\n0\t0\t0\t0\n");assertEquals(2,run(args));
        assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.exists(out.resolve("partial/input-motifs")));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("failed"));
    }
    @Test void nativeMaskingRequiresTheExplicitAlphabetPolicy()throws Exception{
        Path out=output();var args=arguments(out);Files.writeString(fasta,">S\nACUAC\n");assertEquals(2,run(args));
        Path other=directory.resolve("masked");args.set(args.indexOf(out.toString()),other.toString());args.set(args.indexOf("strict-dna"),"mask-n");assertEquals(0,run(args));assertTrue(Files.readString(other.resolve("WARNINGS.md")).contains("masked noncanonical residues=1"));
    }
    @Test void duplicateIdsAreNotMergedOrSilentlyAccepted()throws Exception{
        Path out=output();var args=arguments(out);Files.writeString(fasta,">S first\nAC\n>S second\nAC\n");assertEquals(2,run(args));
        Path other=directory.resolve("duplicates");args.set(args.indexOf(out.toString()),other.toString());args.set(args.indexOf("error"),"retain-ordinal");assertEquals(0,run(args));assertEquals(3,Files.readAllLines(other.resolve("sequences.tsv")).size());assertTrue(Files.readString(other.resolve("WARNINGS.md")).contains("not merged"));
    }
    @Test void hitsAndWorkCapsApplyAcrossAllModelsAndSequences()throws Exception{
        for(String cap:List.of("--maximum-hits","--maximum-operations")){
            Path out=directory.resolve(cap.substring(2));var args=arguments(out);args.addAll(List.of(cap,"1"));assertEquals(2,run(args));assertFalse(Files.exists(out.resolve("results.tsv")));assertTrue(Files.exists(out.resolve("partial/results.tsv")));
        }
    }
    @Test void inputSnapshotAndAuditWorkspaceHaveExplicitByteLimits()throws Exception{
        Path out=output();var args=arguments(out);args.addAll(List.of("--maximum-input-bytes","10"));assertEquals(2,run(args));assertFalse(Files.exists(out.resolve("results.tsv")));
        Path other=directory.resolve("space");args=arguments(other);args.addAll(List.of("--workspace-bytes","20"));assertEquals(2,run(args));assertTrue(Files.readString(other.resolve("WARNINGS.md")).contains("workspace"));
    }
    @Test void unsupportedOffsetAndMissingSciencePoliciesFailClearly()throws Exception{
        Path out=output();var args=arguments(out);args.set(args.indexOf("0"),"-12345678");assertEquals(2,run(args));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("sentinel"));
        Path other=directory.resolve("no-offset");args=arguments(other);int i=args.indexOf("--offset");args.remove(i);args.remove(i);assertEquals(2,run(args));
    }
    @Test void noHitsAndShortSequencesRemainCompleteWithWarningsAndFullLedgers()throws Exception{
        Path out=output();var args=arguments(out);Files.writeString(fasta,">short\nA\n>empty\n>ambiguous\nNNNN\n");
        assertEquals(0,run(args));assertEquals(1,Files.readAllLines(out.resolve("results.tsv")).size());assertEquals(4,Files.readAllLines(out.resolve("sequences.tsv")).size());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("NO MOTIF HITS"));
    }
    @Test void gzipAndCrlfAreDecodedWithOriginalBytesRetained()throws Exception{
        Path out=output();var args=arguments(out);
        try(var stream=new GZIPOutputStream(Files.newOutputStream(fasta))){stream.write(">S\r\nACGT\r\n".getBytes(StandardCharsets.UTF_8));}
        byte[] original=Files.readAllBytes(fasta);assertEquals(0,run(args));assertArrayEquals(original,Files.readAllBytes(out.resolve("input-fasta")));assertEquals(3,Files.readAllLines(out.resolve("results.tsv")).size());
    }
    @Test void unicodePathsAndHeadersUseTheUtf8OptionsCarrier()throws Exception{
        Path out=directory.resolve("資料");arguments(out);Path unicode=directory.resolve("配列.fa");Files.move(fasta,unicode);fasta=unicode;Files.writeString(fasta,">配列 description\nAC\n");
        Path options=directory.resolve("options.json");Files.writeString(options,RegenieRuntime.json(Map.of("motifs",motifs.toString(),"fasta",fasta.toString(),"out",out.toString(),"strands","forward","offset","midpoint","alphabet","strict-dna","duplicate-ids","error")));
        assertEquals(0,run(new ArrayList<>(List.of("--options-file",options.toString()))));assertTrue(Files.readString(out.resolve("results.tsv")).contains("配列"));assertTrue(Files.exists(out.resolve("input-options-file")));
    }
    @Test void unknownOrRecursiveJsonOptionsFailBeforePublication()throws Exception{
        Path out=output();arguments(out);Path options=directory.resolve("options.json");Files.writeString(options,"{\"options-file\":\"recursive\"}");assertEquals(2,run(new ArrayList<>(List.of("--options-file",options.toString()))));assertFalse(Files.exists(out));
    }
    @Test void existingOutputAndSourcesAreNeverReplaced()throws Exception{
        Path out=output();var args=arguments(out);Files.createDirectory(out);Files.writeString(out.resolve("sentinel"),"keep");String source=Files.readString(fasta);
        assertEquals(2,run(args));assertEquals("keep",Files.readString(out.resolve("sentinel")));assertEquals(source,Files.readString(fasta));
    }
    @Test void malformedFastaAlphabetsHeadersAndSequenceWhitespaceFail()throws Exception{
        for(String text:List.of("AC\n",">S\nAC GT\n","> S\nAC\n",">bad\u2028id\nAC\n",">S\nAα\n")){
            Path out=Files.createTempDirectory(directory,"parent-").resolve("result");var args=arguments(out);Files.writeString(fasta,text);assertEquals(2,run(args));assertFalse(Files.exists(out.resolve("results.tsv")));
        }
    }
    @Test void sequenceDecodedLineAndEstimatedMemoryCapsAreEnforced()throws Exception{
        for(var option:List.of(Map.entry("--maximum-sequence-residues","2"),Map.entry("--maximum-decoded-characters","4"),Map.entry("--maximum-line-characters","16"),Map.entry("--maximum-sequences","1000000"))){
            Path out=Files.createTempDirectory(directory,"capacity-").resolve("result");var args=arguments(out);
            if(option.getKey().equals("--maximum-line-characters"))Files.writeString(fasta,">S\n"+"A".repeat(20)+"\n");
            args.addAll(List.of(option.getKey(),option.getValue()));assertEquals(2,run(args));
        }
    }
    @Test void helpDistinguishesNativeScoresFromUnimplementedUpstreamWorkflows()throws Exception{
        var bytes=new ByteArrayOutputStream();try(var out=new PrintStream(bytes,true,StandardCharsets.UTF_8)){assertEquals(0,HomerScanCli.run(new String[]{"--help"},out,System.err));}
        String text=bytes.toString(StandardCharsets.UTF_8);assertTrue(text.contains("Native Java"));assertTrue(text.contains("no P/q/E values"));assertTrue(text.contains("No installation needed"));
    }
}

