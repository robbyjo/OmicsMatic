package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerNamedCliTest {
    @TempDir Path root;
    private final ByteArrayOutputStream out=new ByteArrayOutputStream(),err=new ByteArrayOutputStream();
    private int run(String...args){return HomerNamedCli.run(args,new PrintStream(out),new PrintStream(err));}
    @Test void operationAndCombinedHelpExposeActualNamedProfile(){assertEquals(0,HomerToolsCli.run(new String[]{"named-annotate","--help"},new PrintStream(out),new PrintStream(err)));assertTrue(out.toString().contains("named-annotation-5.1-v7.0"));assertTrue(HomerToolsCli.help().contains("homer-tools named-annotate"));assertTrue(out.toString().contains("at least6GiB"));}
    @Test void absentRuntimeSkipsWithProminentCauseAndRecovery()throws Exception{var o=HomerNamedFixture.options(root);assertEquals(3,run(HomerNamedFixture.args(o)));String warning=Files.readString(root.resolve("output/WARNINGS.md"));assertTrue(warning.contains("ANALYSIS SKIPPED"));assertTrue(warning.contains("Required action:"));assertTrue(warning.contains("install_homer_named.py"));assertFalse(Files.exists(root.resolve("output/results.tsv")));}
    @Test void mismatchedAssemblyOrEditionSkipsWithoutSubstitution()throws Exception{var o=HomerNamedFixture.options(root);o.put("reference-build","hg19");assertEquals(3,run(HomerNamedFixture.args(o)));assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("no version substitution"));}
    @Test void missingExplicitResourcePolicyFailsBeforeDependencyLaunch()throws Exception{var o=HomerNamedFixture.options(root);o.remove("resource-bounds");assertEquals(2,run(HomerNamedFixture.args(o)));assertTrue(Files.readString(root.resolve("output/WARNINGS.md")).contains("resource-bounds"));}
    @Test void unsupportedFlagsAreRejectedBeforeOutputCreation()throws Exception{var o=HomerNamedFixture.options(root);o.put("motifs","ignored");assertEquals(2,run(HomerNamedFixture.args(o)));assertFalse(Files.exists(root.resolve("output")));}
    @Test void recursiveAndNumericOptionsAreRejected()throws Exception{Path p=root.resolve("options.json");Files.writeString(p,"{\"options-file\":\"again\"}");assertEquals(2,run("--options-file",p.toString()));Files.writeString(p,"{\"timeout\":600}");assertEquals(2,run("--options-file",p.toString()));}
    @Test void existingOutputIsRetained()throws Exception{var o=HomerNamedFixture.options(root);Files.createDirectory(root.resolve("output"));Files.writeString(root.resolve("output/keep"),"original");assertEquals(2,run(HomerNamedFixture.args(o)));assertEquals("original",Files.readString(root.resolve("output/keep")));assertFalse(Files.exists(root.resolve("output/session.json")));}
    @Test void insufficientMemoryAllowanceIsExplicitCapabilityFailure(){assertThrows(HomerRuntime.Unavailable.class,()->HomerNamedRuntime.memory(1073741824));}
}
