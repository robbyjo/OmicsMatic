package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdmixtureToolsCliTest {
    @TempDir Path temp;
    private int run(String...args){return AdmixtureToolsCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private Path recipe()throws Exception {var f=new AdmixtureTestData(temp);return f.write(f.recipe());}
    @Test void missingRuntimeSkipsAndProminentlyNamesRecovery()throws Exception {Path out=temp.resolve("out");assertEquals(3,run("--recipe",recipe().toString(),"--out",out.toString()));String warnings=Files.readString(out.resolve("WARNINGS.md"));assertTrue(warnings.contains("ANALYSIS SKIPPED"));assertTrue(warnings.contains("Required action"));assertTrue(warnings.contains("install_admixture.py"));assertTrue(Files.exists(out.resolve("partial/supplied-recipe.json")));assertFalse(Files.exists(out.resolve("results.json")));}
    @Test void unavailableRuntimeIsNeverImplicitlyInstalled()throws Exception {Path home=temp.resolve("home"),out=temp.resolve("out");assertEquals(3,run("--recipe",recipe().toString(),"--home",home.toString(),"--out",out.toString()));assertFalse(Files.exists(home));assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("skipped-unavailable-dependency"));}
    @Test void invalidRecipeFailsWithoutSuccessfulPartialTables()throws Exception {Path recipe=temp.resolve("recipe.json"),out=temp.resolve("out");Files.writeString(recipe,"{}");assertEquals(2,run("--recipe",recipe.toString(),"--out",out.toString()));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("ANALYSIS FAILED"));assertFalse(Files.exists(out.resolve("Q.tsv")));}
    @Test void existingOutputIsPreserved()throws Exception {Path out=Files.createDirectory(temp.resolve("out"));Files.writeString(out.resolve("keep"),"original");assertEquals(2,run("--recipe",recipe().toString(),"--out",out.toString()));assertEquals("original",Files.readString(out.resolve("keep")));try(var stream=Files.list(out)){assertEquals(1,stream.count());}}
    @Test void unicodeOptionsCarrierAndDirectOverridesAreHonored()throws Exception {Path options=temp.resolve("options.json"),out=temp.resolve("résultats 样本");Files.writeString(options,RegenieRuntime.json(Map.of("recipe",recipe().toString(),"out",temp.resolve("ignored").toString())));assertEquals(3,run("--options-file",options.toString(),"--out",out.toString()));assertTrue(Files.exists(out.resolve("WARNINGS.md")));assertFalse(Files.exists(temp.resolve("ignored")));}
    @Test void recursiveUnknownAndDuplicateOptionsFail()throws Exception {assertEquals(2,run("--executable","untrusted"));assertEquals(2,run("--out","a","--out","b"));Path options=temp.resolve("options.json");Files.writeString(options,"{\"options-file\":\"recursive\"}");assertEquals(2,run("--options-file",options.toString()));}
    @Test void helpStatesScopePermissionsAndSeparateExitStatuses()throws Exception {assertEquals(0,run("--help"));String help=AdmixtureToolsCli.help();for(String word:List.of("fit/supervised/project","exit3","exit2","never installs","BIM allele2","CV/bootstrap"))assertTrue(help.contains(word),word);}
    @Test void malformedUtf8OrNonStringOptionsCarrierFailsBeforePublishing()throws Exception {Path file=temp.resolve("bad-options.json");Files.write(file,new byte[]{(byte)0xc3,0x28});assertEquals(2,run("--options-file",file.toString()));Files.writeString(file,"{\"timeout\":1}");assertEquals(2,run("--options-file",file.toString()));}
}
