/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact global delegation, old-help projection and visible refusal/skip propagation. */
class PeerGlobalCliTest {
    private record Result(int code,String out,String err) { }
    @Test void globalHelpMatchesStandalone() {assertEquals(invoke(false,"--help"),invoke(true,"--help"));}
    @Test void rootHelpAddsExactlyOneCommand()throws IOException {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"--help"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8)));
        String help=normalize(out.toString(StandardCharsets.UTF_8));String line=Arrays.stream(help.split("\n",-1)).filter(s->s.contains("java -jar omicsmatic-<version>.jar peer-tools --help")).findFirst().orElseThrow();assertEquals(1,Arrays.stream(help.split("\n",-1)).filter(line::equals).count());
        String projection=String.join("\n",Arrays.stream(help.split("\n",-1)).filter(s->!line.equals(s)).toList());
        try(var input=PeerGlobalCliTest.class.getResourceAsStream("peer-global-preceding-root-help.txt")){assertNotNull(input);assertEquals(normalize(new String(input.readAllBytes(),StandardCharsets.UTF_8)),projection);}assertEquals("",err.toString(StandardCharsets.UTF_8));
    }
    @Test void malformedArgumentsMatchStandaloneFailure() {var result=invoke(true,"--not-a-mode");assertEquals(2,result.code);assertEquals(invoke(false,"--not-a-mode"),result);}
    @Test void unknownModeMatchesStandaloneFailure()throws IOException {Path path=home().resolve("request.json");Files.writeString(path,"{\"schema_version\":1,\"mode\":\"UNKNOWN\"}");var result=invoke(true,"--request",path.toString());assertEquals(2,result.code);assertEquals(invoke(false,"--request",path.toString()),result);}
    @Test void incompleteCaptureMatchesStandaloneRefusal()throws IOException {Path root=home();var result=invoke(true,"--verify",root.toString());assertEquals(2,result.code);assertEquals(invoke(false,"--verify",root.toString()),result);}
    @Test void unknownEditionPropagatesSkippedAnalysisWithoutFallback()throws IOException {
        Path a=home(),b=home();Result global=invoke(true,"--request",unknown(a).toString()),standalone=invoke(false,"--request",unknown(b).toString());
        assertEquals(3,global.code);assertEquals(new Result(standalone.code,standalone.out.replace(b.toString(),"HOME"),standalone.err.replace(b.toString(),"HOME")),new Result(global.code,global.out.replace(a.toString(),"HOME"),global.err.replace(a.toString(),"HOME")));
        assertTrue(Files.readString(a.resolve("capture/WARNINGS.md")).contains("SKIP3"));assertFalse(Files.exists(a.resolve("capture/exports")));
    }
    private static Path unknown(Path root)throws IOException {
        Files.writeString(root.resolve("matrix.tsv"),"feature_id\tS1\tS2\tS3\nG1\t1\t2\t3\nG2\t2\t1\t0\nG3\t3\t2\t1\nG4\t4\t3\t2\n");
        Path path=root.resolve("request.json");Files.write(path,RsemJson.bytes(Map.of("schema_version",1,"mode","RUN","profile","PEER-9.9-unqualified","out","capture","matrix","matrix.tsv","factors",1,"seed",1,"preparation_note","Synthetic global route skip control","runtime",Map.of("executable","absent","sha256","0".repeat(64)))));return path;
    }
    private static Result invoke(boolean global,String... arguments) {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8);
        String[] routed=new String[arguments.length+1];routed[0]="peer-tools";System.arraycopy(arguments,0,routed,1,arguments.length);int code=global?JLinAlgCli.run(routed,out,err):PeerWorkflowCli.run(arguments,out,err);return new Result(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private static Path home()throws IOException {Path base=Path.of(System.getProperty("jlinalg.peer.testOutput","build/peer-global-tests")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,"global-");}
    private static String normalize(String value){return value.replace("\r\n","\n");}
}
