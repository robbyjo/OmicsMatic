/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Global exact-profile dispatch over genuine original captures, without running R. */
final class TwoSampleMrGlobalCliTest {
    @TempDir Path temp;
    private record Result(int code,String out,String err) { }
    private record Capture(String kind,Path root) { }
    private Capture capture(String kind)throws IOException {
        String zip,member;
        if(kind.equals("modern-mr")){zip="twosamplemr-workflow-0.7.14.zip";member="mr-default";}
        else if(kind.equals("modern-clump")){zip="twosamplemr-clump-0.7.14.zip";member="basic";}
        else if(kind.equals("historical-clump")){zip="twosamplemr-historical-clump-0.6.8.zip";member="basic";}
        else {zip="twosamplemr-historical.zip";member=kind.substring("historical-".length())+"/mr-default";}
        Path home=temp.resolve(kind),root=home.resolve(member);Files.createDirectories(home);
        try(var input=getClass().getResourceAsStream("/org/jlinalg/cli/"+zip)) {
            assertNotNull(input);try(var stream=new ZipInputStream(input)) {
                int entries=0,selected=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=stream.getNextEntry())!=null) {
                    assertTrue(++entries<5000);if(!entry.getName().startsWith(member+"/"))continue;
                    Path p=home.resolve(entry.getName()).normalize();assertTrue(p.startsWith(root));assertFalse(entry.isDirectory());byte[] raw=stream.readAllBytes();total+=raw.length;assertTrue(total<64_000_000);
                    Files.createDirectories(p.getParent());Files.write(p,raw,StandardOpenOption.CREATE_NEW);selected++;
                }
                assertTrue(selected>30);
            }
        }
        return new Capture(kind,root);
    }
    @ParameterizedTest @ValueSource(strings={"modern-mr","modern-clump","historical-0.4.9","historical-0.5.6","historical-0.5.7","historical-0.6.8","historical-clump"})
    void genuineCaptureBothVerifyFormsMatchStandalone(String name)throws IOException {
        Capture c=capture(name);Result direct=standalone(c,"--verify",c.root().toString());assertEquals(0,direct.code());assertEquals(direct,global("--verify",c.root().toString()));
        Path request=temp.resolve("verify.json");json(request,Map.of("schema_version",1,"mode","VERIFY","analysis",temp.relativize(c.root()).toString()));assertEquals(standalone(c,"--request",request.toString()),global("--request",request.toString()));
    }
    @ParameterizedTest @ValueSource(strings={"modern-mr","modern-clump","historical-0.4.9","historical-0.5.6","historical-0.5.7","historical-0.6.8","historical-clump"})
    void replayPreservesExactTargetAndParentProtection(String name)throws IOException {
        Capture c=capture(name);String before=FollowupSupport.hash(c.root().resolve("capture-files.json"));Path request=temp.resolve("replay.json");json(request,Map.of("schema_version",1,"mode","REPLAY","analysis",c.root().toString(),"out",c.root().resolve("child").toString()));
        Result actual=global("--request",request.toString());assertEquals(2,actual.code());assertEquals(standalone(c,"--request",request.toString()),actual);assertFalse(Files.exists(c.root().resolve("child")));assertEquals(before,FollowupSupport.hash(c.root().resolve("capture-files.json")));
    }
    @Test void globalAndStandaloneHelpMatch() {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=TwoSampleMrToolsCli.run(new String[]{"--help"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));Result help=new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));assertEquals(help,global("--help"));assertEquals(0,code);assertTrue(help.out().contains(TwoSampleMrHistoricalClumpContracts.PROFILE));assertTrue(help.out().contains("SKIP3"));assertTrue(help.out().contains("FAIL2"));
    }
    @Test void rootHelpHasOnlyTheReviewedAdditiveLine()throws IOException {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"--help"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8)));String help=out.toString(StandardCharsets.UTF_8).replace("\r\n","\n"),line=Arrays.stream(help.split("\n",-1)).filter(s->s.contains("java -jar omicsmatic-<version>.jar twosamplemr-tools --help")).findFirst().orElseThrow();assertEquals(1,Arrays.stream(help.split("\n",-1)).filter(line::equals).count());String inverse=String.join("\n",Arrays.stream(help.split("\n",-1)).filter(s->!s.equals(line)).toList());
        try(var input=getClass().getResourceAsStream("twosamplemr-global-preceding-root-help.txt")){assertNotNull(input);assertEquals(new String(input.readAllBytes(),StandardCharsets.UTF_8),inverse);}assertEquals("",err.toString(StandardCharsets.UTF_8));
    }
    @Test void unqualifiedProfileSkipsWithoutChoosingAnEdition()throws IOException {Path request=temp.resolve("unknown.json"),out=temp.resolve("analysis");json(request,Map.of("mode","RUN","profile","TwoSampleMR-unknown","out",out.toString()));Result result=global("--request",request.toString());assertEquals(3,result.code());assertTrue(result.err().contains("DEPENDENT ANALYSIS SKIPPED"));assertFalse(Files.exists(out));}
    @Test void unknownModeFailsWithoutWriting()throws IOException {Path request=temp.resolve("mode.json");json(request,Map.of("mode","CHOOSE_FOR_ME"));assertEquals(2,global("--request",request.toString()).code());}
    @Test void malformedJsonHasProminentNoResultFailure()throws IOException {Path request=temp.resolve("bad.json");Files.writeString(request,"{");Result result=global("--request",request.toString());assertEquals(2,result.code());assertTrue(result.err().contains("NO COMPLETE RESULT"));}
    @Test void invalidUtf8IsRejected()throws IOException {Path request=temp.resolve("utf8.json");Files.write(request,new byte[]{(byte)0xc3,(byte)0x28});assertEquals(2,global("--request",request.toString()).code());}
    @Test void oversizedRoutingInputIsRejectedBeforeParsing()throws IOException {Path request=temp.resolve("capacity.json");Files.write(request,new byte[2_000_001]);Result result=global("--request",request.toString());assertEquals(2,result.code());assertTrue(result.err().contains("bounded regular"));}
    @Test void foreignEngineIsNeverDelegated()throws IOException {Capture c=capture("modern-mr");var manifest=read(c.root().resolve("manifest.json"));manifest.put("engine","some-other-tool");json(c.root().resolve("manifest.json"),manifest);Result result=global("--verify",c.root().toString());assertEquals(2,result.code());assertTrue(result.err().contains("no supported original TwoSampleMR engine"));}
    @Test void engineProfileConflictIsNotRelabeled()throws IOException {Capture c=capture("modern-mr");var manifest=read(c.root().resolve("manifest.json"));manifest.put("profile",TwoSampleMrHistoricalClumpContracts.PROFILE);json(c.root().resolve("manifest.json"),manifest);Result result=global("--verify",c.root().toString());assertEquals(2,result.code());assertTrue(result.err().contains("engine/profile conflict"));}
    @Test void invalidOperationUsesTheSelectedSourceContract()throws IOException {Path request=temp.resolve("operation.json");json(request,Map.of("schema_version",1,"mode","RUN","profile",TwoSampleMrClumpContracts.PROFILE,"operation","MR"));Result result=global("--request",request.toString());assertEquals(2,result.code());assertTrue(result.err().contains("Only original local CLUMP"));}
    @Test void anExtraSelectorCannotOverrideTheSelectedSource()throws IOException {Path request=temp.resolve("selector.json");json(request,Map.of("schema_version",1,"mode","RUN","profile",TwoSampleMrWorkflowContracts.PROFILE,"operation","MR","engine","some-other-tool"));assertEquals(2,global("--request",request.toString()).code());}
    @Test void incompleteCaptureCannotBecomeAResult()throws IOException {Path root=temp.resolve("incomplete");Files.createDirectory(root);json(root.resolve("manifest.json"),Map.of("engine",TwoSampleMrWorkflowCli.ENGINE,"profile",TwoSampleMrWorkflowContracts.PROFILE));assertEquals(2,global("--verify",root.toString()).code());}
    @Test void missingRPropagatesDependencySkipAndRequiredRecovery()throws IOException {
        Capture c=capture("modern-mr");var request=read(c.root().resolve("request.json"));for(String key:TwoSampleMrWorkflowContracts.INPUTS)if(request.containsKey(key))request.put(key,c.root().resolve(text(request,key)).toString());Path output=temp.resolve("missing-runtime");request.put("out",output.toString());request.put("rscript",temp.resolve("missing-Rscript.exe").toString());Path file=temp.resolve("missing.json");json(file,request);Result result=global("--request",file.toString());assertEquals(3,result.code());assertEquals(false,read(output.resolve("analysis-status.json")).get("results_admitted"));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("SKIP3"));assertFalse(Files.exists(output.resolve("results/mr.tsv")));
    }
    private static Result standalone(Capture capture,String...args) {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8);String kind=capture.kind();int code;
        if(kind.equals("modern-mr"))code=TwoSampleMrWorkflowCli.run(args,out,err);else if(kind.equals("modern-clump"))code=TwoSampleMrClumpCli.run(args,out,err);else if(kind.equals("historical-clump"))code=TwoSampleMrHistoricalClumpCli.run(args,out,err);else code=TwoSampleMrHistoricalCli.run(args,out,err);return new Result(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private static Result global(String...args) {
        String[] route=new String[args.length+1];route[0]="twosamplemr-tools";System.arraycopy(args,0,route,1,args.length);var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(route,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
}
