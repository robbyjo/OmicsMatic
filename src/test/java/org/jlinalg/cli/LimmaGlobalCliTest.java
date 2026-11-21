/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Global routing, prior help projection and exact failure/skip status propagation. */
final class LimmaGlobalCliTest {
    private record Result(int code,String out,String err) { }
    @Test void globalHelpMatchesStandaloneHelpExactly() {
        assertEquals(invoke(false,"--help"),invoke(true,"--help"));
        assertTrue(invoke(true,"--help").out().contains("SKIP3"));
    }
    @Test void rootHelpAddsExactlyOneDiscoverableCommand()throws IOException {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"--help"},new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8)));
        String text=normalize(output.toString(StandardCharsets.UTF_8));String line=Arrays.stream(text.split("\n",-1)).filter(v->v.contains("java -jar omicsmatic-<version>.jar limma-tools --help")).findFirst().orElseThrow();
        assertEquals(1,Arrays.stream(text.split("\n",-1)).filter(v->v.equals(line)).count());
        String projected=String.join("\n",Arrays.stream(text.split("\n",-1)).filter(v->!v.equals(line)).toList());
        try(var input=LimmaGlobalCliTest.class.getResourceAsStream("limma-global-preceding-root-help.txt")){assertNotNull(input);assertEquals(normalize(new String(input.readAllBytes(),StandardCharsets.UTF_8)),projected);}
        assertEquals("",error.toString(StandardCharsets.UTF_8));
    }
    @Test void malformedArgumentsPreserveStandaloneFailure() {
        Result result=invoke(true,"--not-a-mode");assertEquals(2,result.code());assertEquals(invoke(false,"--not-a-mode"),result);
    }
    @Test void unknownEditionPreservesSkipBeforeCreatingOutput()throws IOException {
        Path h=home("edition"),path=h.resolve("request.json");Files.writeString(path,"{\"schema_version\":1,\"mode\":\"RUN\",\"profile\":\"unqualified-author-edition\",\"out\":\"analysis\"}");
        Result result=invoke(true,"--request",path.toString());assertEquals(3,result.code());assertEquals(invoke(false,"--request",path.toString()),result);assertTrue(result.err().contains("SKIP3"));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void unknownModePreservesStandaloneFailure()throws IOException {
        Path path=home("mode").resolve("request.json");Files.writeString(path,"{\"schema_version\":1,\"mode\":\"UNKNOWN\"}");
        Result result=invoke(true,"--request",path.toString());assertEquals(2,result.code());assertEquals(invoke(false,"--request",path.toString()),result);
    }
    @Test void incompleteCaptureVerificationPreservesRefusal()throws IOException {
        Path h=home("capture");Result result=invoke(true,"--verify",h.toString());assertEquals(2,result.code());assertEquals(invoke(false,"--verify",h.toString()),result);
    }
    private static Result invoke(boolean global,String... arguments) {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8);
        String[] routed=new String[arguments.length+1];routed[0]="limma-tools";System.arraycopy(arguments,0,routed,1,arguments.length);
        int code=global?JLinAlgCli.run(routed,out,err):LimmaWorkflowCli.run(arguments,out,err);
        return new Result(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private static Path home(String name)throws IOException {Path base=Path.of(System.getProperty("jlinalg.limma.testOutput","build/limma-global-test-output")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,name+"-");}
    private static String normalize(String value){return value.replace("\r\n","\n");}
}
