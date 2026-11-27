/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.splicing.RmatsResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class RmatsResultsCliTest {
    @TempDir Path temporary;
    private String fixture(){return String.join("\t",RmatsResults.Event.SE.header(true))+"\r\n"+"a\t\"g\"\ts\tchrX\t+\t0\t20\t0\t20\t0\t20\tb\t1,1\t0,0\t0,0\t1,1\t100\t50\t0.01\tNA\t1.0,1.0\t0.0,0.0\t0.9\r\n";}
    private int run(String... args){return RmatsResultsCli.run(args,new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8),new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8));}
    private String[] command(Path input,Path output){return new String[]{"--input",input.toString(),"--event","SE","--mode","JC","--group1","2","--group2","2","--max-p","0.05","--min-abs-dpsi","0.05","--out",output.toString()};}
    @Test void roundTripsExactInputAndRecomputesSavedSelection()throws Exception{
        Path input=temporary.resolve("source.tsv"),output=temporary.resolve("result");Files.writeString(input,fixture());assertEquals(0,run(command(input,output)));assertArrayEquals(Files.readAllBytes(input),Files.readAllBytes(output.resolve("input.tsv")));assertEquals(fixture().replace("\r\n","\n"),Files.readString(output.resolve("selected.tsv")));assertEquals("JC",object(json(output.resolve("summary.json"),1048576)).get("mode"));assertEquals(0,run("--verify",output.toString()));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("UNASSESSABLE"));
    }
    @Test void refusesSemanticTamperingEvenWhenAllHashesWereUpdated()throws Exception{
        Path input=temporary.resolve("source.tsv"),output=temporary.resolve("result");Files.writeString(input,fixture());assertEquals(0,run(command(input,output)));Files.writeString(output.resolve("selected.tsv"),String.join("\t",RmatsResults.Event.SE.header(true))+"\n");
        Map<?,?> old=object(json(output.resolve("manifest.json"),1048576));var members=new TreeMap<String,Pin>();for(Object key:object(old.get("files")).keySet())members.put(key.toString(),pin(output.resolve(key.toString()),67108864,()->{}));Files.delete(output.resolve("manifest.json"));writeJson(output.resolve("manifest.json"),Map.of("schema_version",1,"files",members));Files.delete(output.resolve("COMPLETED.json"));writeJson(output.resolve("COMPLETED.json"),Map.of("status","complete-native-rmats-result-selection","manifest",pin(output.resolve("manifest.json"),1048576,()->{}),"whole_tool_reimplemented",false,"whole_paper_reproduced",false));assertEquals(2,run("--verify",output.toString()));
    }
    @Test void missingInputSkipsAndInvalidInputFailsWithProminentActions()throws Exception{
        Path skipped=temporary.resolve("skipped");assertEquals(3,run(command(temporary.resolve("missing.tsv"),skipped)));assertTrue(Files.readString(skipped.resolve("WARNINGS.md")).contains("SKIP3"));Path bad=temporary.resolve("bad.tsv"),failed=temporary.resolve("failed");Files.writeString(bad,"invalid\n");assertEquals(2,run(command(bad,failed)));assertFalse(Files.exists(failed.resolve("COMPLETED.json")));assertTrue(Files.readString(failed.resolve("FAILURE.json")).contains("FAIL2"));
    }
    @Test void preservesExistingDirectoriesAndRejectsMalformedUtf8()throws Exception{
        Path input=temporary.resolve("source.tsv"),existing=temporary.resolve("existing");Files.writeString(input,fixture());Files.createDirectory(existing);Files.writeString(existing.resolve("unique"),"keep");assertEquals(2,run(command(input,existing)));assertEquals("keep",Files.readString(existing.resolve("unique")));Path output=temporary.resolve("bad-encoding");Files.write(input,new byte[]{(byte)0xC3,0x28});assertEquals(2,run(command(input,output)));assertFalse(Files.exists(output.resolve("COMPLETED.json")));
    }
}
