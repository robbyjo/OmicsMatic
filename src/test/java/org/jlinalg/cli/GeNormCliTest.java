/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeNormCliTest {
    @TempDir Path temp;
    private final ByteArrayOutputStream console = new ByteArrayOutputStream();
    private int run(String... args) {return GeNormCli.run(args,new PrintStream(console),new PrintStream(console));}
    private Path refs() throws Exception {
        Path file = temp.resolve("refs.tsv");
        Files.writeString(file,"sample_id\tA\tB\tC\ns1\t0\t0\t0\ns2\t0\t1\t2\ns3\t0\t2\t4\n");return file;
    }
    private Path success(String name,boolean targets) throws Exception {
        Path input=refs(),out=temp.resolve(name);var args=new java.util.ArrayList<>(List.of("--references",input.toString(),"--scale","log2-quantity","--reference-count","2","--out",out.toString()));
        if(targets) {Path target=temp.resolve("targets.tsv");Files.writeString(target,"sample_id\tPLA2G2D\ns1\t2\ns2\t4\ns3\t6\n");args.addAll(List.of("--targets",target.toString()));}
        assertEquals(0,run(args.toArray(String[]::new)),console.toString());return out;
    }
    private static String hash(Path path) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void reseal(Path out,String file) throws Exception {
        Properties p=new Properties();try(var in=Files.newInputStream(out.resolve("COMPLETED.properties"))){p.load(in);}
        p.setProperty("sha256."+file,hash(out.resolve(file)));
        try(var writer=Files.newBufferedWriter(out.resolve("COMPLETED.properties"))){p.store(writer,null);}
    }
    private static Map<String,String> identities(Path out) throws Exception {
        Map<String,String> hashes=new TreeMap<>();try(var paths=Files.list(out)){for(Path p:paths.toList())hashes.put(p.getFileName().toString(),hash(p));}return hashes;
    }
    @Test void realStandaloneRunCapturesInputsAndReplaysAllResultsAfterOriginalsDisappear() throws Exception {
        Path out=success("success",true);
        assertEquals("sample_id\tlog2_factor\ns1\t0.0\ns2\t1.5\ns3\t3.0\n",Files.readString(out.resolve("normalization.tsv")));
        assertEquals("sample_id\tPLA2G2D\ns1\t2.0\ns2\t2.5\ns3\t3.0\n",Files.readString(out.resolve("normalized-targets-log2.tsv")));
        assertTrue(Files.readString(out.resolve("ranking.tsv")).startsWith("gene\trank\tinput_column_1based\tselected_for_factor\nB\t1\t2\ttrue\nC\t1\t3\ttrue\nA\t3\t1\tfalse\n"));
        Files.delete(temp.resolve("refs.tsv"));Files.delete(temp.resolve("targets.tsv"));
        var before=identities(out);assertEquals(0,run("--verify",out.toString()));assertEquals(before,identities(out));
        assertTrue(console.toString().contains("PASS_NATIVE_GENORM_FULL_BUNDLE_REPLAY"));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Cq/Ct values are not implicitly converted"));
    }
    @Test void resealedScientificOutputCannotPassComputationalReplay() throws Exception {
        Path out=success("resealed",false);
        Files.writeString(out.resolve("normalization.tsv"),"sample_id\tlog2_factor\ns1\t0.0\ns2\t2.5\ns3\t3.0\n");
        reseal(out,"normalization.tsv");var before=identities(out);
        assertEquals(2,run("--verify",out.toString()));assertEquals(before,identities(out));
        assertTrue(console.toString().contains("Scientific recomputation differs"));
    }
    @Test void contradictoryScientificOptionsAndDuplicateRecordedKeysAreRejectedAfterResealing() throws Exception {
        for(boolean duplicate:new boolean[]{false,true}) {
            Path out=success("options-"+duplicate,false),command=out.resolve("command.properties");
            String text=Files.readString(command);
            Files.writeString(command,duplicate ? text+"scale=quantity\n" : text.replace("reference_count=2","reference_count=3"));
            reseal(out,"command.properties");assertEquals(2,run("--verify",out.toString()));
        }
    }
    @Test void undeclaredFilesAndTamperedWarningsAreRefusedEvenWithNewHashes() throws Exception {
        Path extra=success("extra",false);Files.writeString(extra.resolve("extra.tsv"),"unrelated");reseal(extra,"extra.tsv");
        assertEquals(2,run("--verify",extra.toString()));
        Path warnings=success("warnings",false);Files.writeString(warnings.resolve("WARNINGS.md"),"Qualified as whole-paper reproduction\n");reseal(warnings,"WARNINGS.md");
        assertEquals(2,run("--verify",warnings.toString()));
    }
    @Test void invalidScaleAndIncompleteInputsLeaveProminentFailureWithoutCompletion() throws Exception {
        for(String scale:new String[]{"cq","log2-quantity"}) {
            Path input=refs();if(scale.equals("log2-quantity"))Files.writeString(input,"sample_id\tA\tB\ns1\tNaN\t0\ns2\t1\t1\n");
            Path out=temp.resolve("bad-"+scale);
            assertEquals(2,run("--references",input.toString(),"--scale",scale,"--reference-count","2","--out",out.toString()));
            assertTrue(Files.exists(out.resolve("WARNINGS.md")));assertTrue(Files.exists(out.resolve("FAILURE.txt")));
            assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertTrue(console.toString().contains("FAIL2_NATIVE_GENORM"));
        }
    }
    @Test void sampleReorderingAndDuplicateIdentifiersAreRejectedWithoutRepair() throws Exception {
        Path refs=refs(),target=temp.resolve("target.tsv"),out=temp.resolve("mismatch");
        Files.writeString(target,"sample_id\tT\ns2\t4\ns1\t2\ns3\t6\n");
        assertEquals(2,run("--references",refs.toString(),"--targets",target.toString(),"--scale","log2-quantity","--reference-count","2","--out",out.toString()));
        assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("exactly match"));
        Files.writeString(refs,"sample_id\tA\tB\ns1\t0\t0\ns1\t1\t2\n");
        assertEquals(2,run("--references",refs.toString(),"--scale","log2-quantity","--reference-count","2","--out",temp.resolve("dups").toString()));
    }
    @Test void anExistingBundleIsNeverOverwrittenAndOptionsCannotBeRepeated() throws Exception {
        Path out=success("existing",false);var before=identities(out);
        assertEquals(2,run("--references",temp.resolve("refs.tsv").toString(),"--scale","log2-quantity","--reference-count","2","--out",out.toString()));
        assertEquals(before,identities(out));
        assertEquals(2,run("--verify",out.toString(),"--verify",out.toString()));assertEquals(0,run("--help"));
    }
    @Test void literalUnicodeAndSpaceIdentifiersAndTwoReferencesAreSupported() throws Exception {
        Path input=temp.resolve("unicode.tsv"),out=temp.resolve("unicode-result");
        Files.writeString(input,"sample_id\tACTB α\tHPRT1\n样本 1\t0\t1\n样本 2\t2\t4\n",StandardCharsets.UTF_8);
        assertEquals(0,run("--references",input.toString(),"--scale","log2-quantity","--reference-count","2","--out",out.toString()));
        assertTrue(Files.readString(out.resolve("ranking.tsv")).contains("ACTB α\t1\t1\ttrue"));
        assertTrue(Files.readString(out.resolve("normalization.tsv")).contains("样本 2\t3.0"));
    }
    @Test void oversizedReplayPayloadIsRejectedBeforeReadingItsContents() throws Exception {
        Path out=success("oversized",false),payload=out.resolve("normalization.tsv");
        try(var file=new java.io.RandomAccessFile(payload.toFile(),"rw")){file.setLength(64L*1024*1024+1);}
        assertEquals(2,run("--verify",out.toString()));
        assertTrue(console.toString().contains("exceeds 64 MiB"));
    }
    @Test void implementationIdentityIsRequiredEvenWhenItsMetadataIsResealed() throws Exception {
        Path out=success("implementation",false),session=out.resolve("session.properties");
        String text=Files.readString(session);
        String changed=text.replaceFirst("implementation_sha256\\.org\\.jlinalg\\.expression\\.GeNorm=[0-9a-f]+",
            "implementation_sha256.org.jlinalg.expression.GeNorm="+"0".repeat(64));
        assertNotEquals(text,changed);Files.writeString(session,changed);reseal(out,"session.properties");
        assertEquals(2,run("--verify",out.toString()));assertTrue(console.toString().contains("implementation bytes differ"));
    }
}
