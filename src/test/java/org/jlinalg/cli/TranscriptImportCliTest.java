package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscriptImportCliTest {
    @TempDir Path dir;private String console;
    private Path file(String name,String text)throws Exception{Path path=dir.resolve(name);Files.writeString(path,text);return path;}
    private Path quant(String name,String a,String b)throws Exception{return file(name,"transcript\tcounts\tabundance\tlength\n001.1\t"+a+"\t1\t10\n002.1\t"+b+"\t3\t30\n");}
    private int run(String output,Path manifest,String...extra)throws Exception{
        var args=new ArrayList<>(List.of("transcript-import","--samples",manifest.toString(),"--format","tsv","--level","gene","--counts-from-abundance","no","--id-policy","strip-version","--transcriptome-id","test-reference-v1","--inferential-replicates","drop","--tx2gene",dir.resolve("map.tsv").toString(),"--unmapped","error","--out",dir.resolve(output).toString(),"--no-config"));
        for(int i=0;i<extra.length;i+=2){int index=args.indexOf(extra[i]);if(index>=0)args.set(index+1,extra[i+1]);else{args.add(extra[i]);args.add(extra[i+1]);}}
        return call(args);
    }
    private int call(List<String> args){var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);console=bytes.toString();return code;}
    private Path fixture()throws Exception{quant("one.tsv","1.5","2.5");quant("two.tsv","3.25","4.75");file("map.tsv","transcript\tgene\n001\t001G\n002\t001G\nunused\tother\n");return file("samples.tsv","sample\tpath\r\n1\ttwo.tsv\r\n001\tone.tsv\r\n");}
    @Test void relativeManifestNumericIdsFractionsAndReferenceAuditAreRetained()throws Exception{
        Path samples=fixture();String before=Files.readString(dir.resolve("one.tsv"));assertEquals(0,run("result",samples),console);assertEquals("feature\t1\t001",Files.readAllLines(dir.resolve("result/results.tsv")).get(0));assertEquals("001G\t8.0\t4.0",Files.readAllLines(dir.resolve("result/results.tsv")).get(1));
        assertEquals("001G\t25.0\t25.0",Files.readAllLines(dir.resolve("result/length.tsv")).get(1));assertTrue(Files.readString(dir.resolve("result/annotation-status.tsv")).contains("unused-annotation"));assertTrue(Files.readString(dir.resolve("result/session.yaml")).contains("source_sha256"));assertTrue(Files.readString(dir.resolve("result/WARNINGS.md")).contains("LENGTH OFFSET ACTION"));assertTrue(console.contains("ANALYSIS WARNINGS"));assertEquals(before,Files.readString(dir.resolve("one.tsv")));
    }
    @Test void formatsAndGzipProduceActualFractionalGeneCounts()throws Exception{
        fixture();String[] formats={"salmon","kallisto","rsem"};String[] data={"Name\tLength\tEffectiveLength\tTPM\tNumReads\n001.1\t100\t10\t1\t1.5\n002.1\t200\t30\t3\t2.5\n","target_id\tlength\teff_length\test_counts\ttpm\n001.1\t100\t10\t1.5\t1\n002.1\t200\t30\t2.5\t3\n","transcript_id\tgene_id\tlength\teffective_length\texpected_count\tTPM\tFPKM\tIsoPct\n001.1\tG\t100\t0\t1.5\t1\t0\t0\n002.1\tG\t200\t30\t2.5\t3\t0\t0\n"};
        for(int i=0;i<formats.length;i++) {Path path=dir.resolve(formats[i]+".tsv.gz");try(var stream=new GZIPOutputStream(Files.newOutputStream(path))){stream.write(data[i].getBytes(java.nio.charset.StandardCharsets.UTF_8));}Path manifest=file(formats[i]+"-samples.tsv","sample\tpath\n1\t"+path.getFileName()+"\n");assertEquals(0,run(formats[i],manifest,"--format",formats[i]),console);assertEquals("001G\t4.0",Files.readAllLines(dir.resolve(formats[i]+"/results.tsv")).get(1));if(formats[i].equals("rsem"))assertTrue(Files.readString(dir.resolve("rsem/WARNINGS.md")).contains("RSEM LENGTH CLAMP"));}
    }
    @Test void unannotatedDropIsExplicitAndLibraryTotalsReflectOmission()throws Exception{
        Path manifest=fixture();file("map.tsv","transcript\tgene\n001\tG\n");assertEquals(2,run("missing",manifest),console);assertFalse(Files.exists(dir.resolve("missing/results.tsv")));assertTrue(Files.readString(dir.resolve("missing/WARNINGS.md")).contains("Unmapped transcript"));
        assertEquals(0,run("drop",manifest,"--unmapped","drop","--counts-from-abundance","scaledTPM"),console);assertTrue(Files.readString(dir.resolve("drop/transcript-status.tsv")).contains("omitted-unmapped"));assertTrue(Files.readString(dir.resolve("drop/library-totals.tsv")).contains("1\t8.0\t3.25\t3.25"));assertTrue(Files.readString(dir.resolve("drop/WARNINGS.md")).contains("NO DOUBLE LENGTH CORRECTION"));
    }
    @Test void conflictingMapOrderCollisionsAndNonfiniteInputsFailWithoutSuccessfulOutput()throws Exception{
        Path manifest=fixture();file("map.tsv","transcript\tgene\n001\tA\n001\tB\n002\tA\n");assertEquals(2,run("conflict",manifest),console);assertTrue(Files.readString(dir.resolve("conflict/WARNINGS.md")).contains("Conflicting gene"));
        file("map.tsv","transcript\tgene\n001\tA\n002\tA\n");file("one.tsv","transcript\tcounts\tabundance\tlength\n002.1\t1\t1\t10\n001.1\t1\t1\t10\n");assertEquals(2,run("order",manifest),console);assertTrue(Files.readString(dir.resolve("order/WARNINGS.md")).contains("IDs/order differ"));
        for(String value:List.of("NaN","-1","Infinity")){quant("one.tsv",value,"2");assertEquals(2,run("invalid"+value,manifest),console);assertFalse(Files.exists(dir.resolve("invalid"+value+"/results.tsv")));}
    }
    @Test void resourceCapsZeroScalingAndRequiredUncertaintyChoiceAreActionable()throws Exception{
        Path manifest=fixture();assertEquals(2,run("cells",manifest,"--max-cells","1"),console);assertTrue(Files.readString(dir.resolve("cells/WARNINGS.md")).contains("max-cells"));assertEquals(2,run("bytes",manifest,"--max-bytes","10"),console);
        assertEquals(2,run("uncertainty",manifest,"--inferential-replicates","retain"),console);assertTrue(console.contains("point estimates"));
        for(String name:List.of("one.tsv","two.tsv"))file(name,"transcript\tcounts\tabundance\tlength\n001.1\t0\t0\t10\n002.1\t0\t0\t20\n");assertEquals(0,run("zero-no",manifest),console);assertEquals(2,run("zero-scale",manifest,"--counts-from-abundance","scaledTPM"),console);assertTrue(Files.readString(dir.resolve("zero-scale/WARNINGS.md")).contains("Zero abundance scaling denominator in sample 1"));
    }
    @Test void transcriptDtuAndBarOutputPreserveExplicitIdentityContract()throws Exception{
        Path quant=file("q.tsv","transcript\tcounts\tabundance\tlength\na|v\t2\t1\t10\nb|v\t3\t3\t30\nc|v\t5\t2\t20\n");Path manifest=file("s.tsv","sample\tpath\n1\t"+quant.getFileName()+"\n");Path map=file("map.tsv","transcript\tgene\na\tA\nb\tA\nc\tC\n");
        assertEquals(0,call(List.of("transcript-import","--samples",manifest.toString(),"--format","tsv","--level","transcript","--counts-from-abundance","dtuScaledTPM","--id-policy","strip-bar","--transcriptome-id","reference","--inferential-replicates","drop","--tx2gene",map.toString(),"--out",dir.resolve("dtu").toString(),"--no-config")),console);assertEquals("a\t1.6666666666666665",Files.readAllLines(dir.resolve("dtu/results.tsv")).get(1));
        assertEquals(0,call(List.of("transcript-import","--help","--no-config")),console);assertTrue(console.contains("no R required"));
    }
}
