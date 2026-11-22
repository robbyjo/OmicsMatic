package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerAnnotationCliTest {
    @TempDir Path root;
    private final ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
    private int run(String...args){return HomerAnnotationCli.run(args,new PrintStream(out),new PrintStream(err));}
    @Test void mainHomerOperationDispatchShowsActualAnnotationHelp(){assertEquals(0,HomerToolsCli.run(new String[]{"annotate","--help"},new PrintStream(out),new PrintStream(err)));String help=out.toString(StandardCharsets.UTF_8);assertTrue(help.contains("homer-tools annotate"));assertTrue(help.contains("annotation-5.1"));assertTrue(help.contains("skip3"));}
    @Test void fullHomerHelpIncludesSeparateAnnotationAdmission(){assertTrue(HomerToolsCli.help().contains("install_homer_annotation.py"));assertTrue(HomerToolsCli.help().contains("core-only profiles are insufficient"));}
    @Test void missingDependencySkipsWithProminentCauseAndRequiredAction()throws Exception{var o=HomerAnnotationFixture.options(root);assertEquals(3,run(HomerAnnotationFixture.args(o)));String warning=Files.readString(root.resolve("output/WARNINGS.md"));assertTrue(warning.contains("ANALYSIS SKIPPED"));assertTrue(warning.contains("Required action:"));assertFalse(Files.exists(root.resolve("output/results.tsv")));}
    @Test void unadmittedGeneIdModeSkipsInsteadOfSubstitutingTranscriptMode()throws Exception{var o=HomerAnnotationFixture.options(root);o.put("gtf-id","gene");assertEquals(3,run(HomerAnnotationFixture.args(o)));assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("gene-ID mode"));}
    @Test void missingExplicitReferenceChoiceFailsWithRetainedDiagnostics()throws Exception{var o=HomerAnnotationFixture.options(root);o.remove("reference-build");assertEquals(2,run(HomerAnnotationFixture.args(o)));assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("reference-build"));assertFalse(Files.exists(root.resolve("output/results.tsv")));}
    @Test void ignoredMotifFlagsAreRejectedBeforeCreatingOutput()throws Exception{var o=HomerAnnotationFixture.options(root);o.put("motifs","ignored.motif");assertEquals(2,run(HomerAnnotationFixture.args(o)));assertFalse(Files.exists(root.resolve("output")));}
    @Test void recursiveAndNonStringOptionsFilesFailExplicitly()throws Exception{Path p=root.resolve("options.json");Files.writeString(p,"{\"options-file\":\"again.json\"}");assertEquals(2,run("--options-file",p.toString()));Files.writeString(p,"{\"timeout\":12}");assertEquals(2,run("--options-file",p.toString()));}
    @Test void existingOutputsAreNeverOverwritten()throws Exception{var o=HomerAnnotationFixture.options(root);Path output=root.resolve("output");Files.createDirectory(output);Files.writeString(output.resolve("sentinel"),"retain");assertEquals(2,run(HomerAnnotationFixture.args(o)));assertEquals("retain",Files.readString(output.resolve("sentinel")));assertFalse(Files.exists(output.resolve("session.json")));}
}
