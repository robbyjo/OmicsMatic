/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FastpProcessTest {
    @TempDir Path temporary;

    private static FastpProcess.Limits limits(long wall, long memory, long cpu,
            long file, long workspace, int files, int processes) {
        return new FastpProcess.Limits(wall, memory, cpu, file, 256, workspace,
                files, processes, 64, 1073741824, 10);
    }

    @ParameterizedTest
    @ValueSource(strings={"success", "exit", "orphan", "wall", "cancel", "input-change",
            "symlink", "files", "workspace", "process-count", "hard-as", "hard-file", "hard-cpu"})
    void realOwnedProcessesHaveTerminalCleanupAndPreserveUnrelatedProcesses(String name) throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"), "Owned process controls require Linux");
        Path root=temporary.toRealPath(), work=root.resolve(name);
        var budget=limits(10000,2147483648L,10,134217728,536870912,512,8);
        var command=new ArrayList<>(List.of("/bin/sh","-c","printf complete"));
        String expected=null;
        var environment=new HashMap<>(Map.of("PATH","/usr/bin:/bin","LC_ALL","C"));
        FastpProcess.Check integrity=()->{};
        Runnable cancellation=()->{};
        switch(name) {
            case "success" -> { }
            case "exit" -> {command.set(2,"exit 7");expected="exit 7";}
            case "orphan" -> {command.set(2,"sleep 30 & printf '%s\\n' $!; exit 0");expected="live owned workers";}
            case "wall" -> {command.set(2,"sleep 30 & wait");budget=limits(200,2147483648L,10,134217728,536870912,512,8);expected="wall-time";}
            case "cancel" -> {
                command.set(2,"sleep 30 & wait");int[] calls={0};
                cancellation=()->{if(++calls[0]>3)throw new IllegalStateException("controlled cancellation");};
                expected="cancelled";
            }
            case "input-change" -> {
                Path source=root.resolve("source.txt");Files.writeString(source,"original");
                environment.put("CONTROLLED_SOURCE",source.toString());command.set(2,"printf changed > \"$CONTROLLED_SOURCE\"");
                integrity=()->{if(!Files.readString(source).equals("original"))throw new IOException("controlled source mutation");};
                expected="integrity failed";
            }
            case "symlink" -> {command.set(2,"ln -s /tmp linked");expected="linked/nonregular";}
            case "files" -> {command.set(2,"touch a b c d e f g h i");budget=limits(10000,2147483648L,10,134217728,536870912,8,8);expected="file";}
            case "workspace" -> {command.set(2,"head -c 65536 /dev/zero > payload");budget=limits(10000,2147483648L,10,134217728,8192,512,8);expected="workspace";}
            case "process-count" -> {command.set(2,"sleep 30 & wait");budget=limits(10000,2147483648L,10,134217728,536870912,512,1);expected="RSS/thread/process capacity";}
            case "hard-as" -> {command=new ArrayList<>(List.of("/usr/bin/python3","-c","bytearray(268435456)"));budget=limits(10000,67108864,10,134217728,536870912,512,8);expected="Original Fastp process failed";}
            case "hard-file" -> {command.set(2,"head -c 1048576 /dev/zero > payload");budget=limits(10000,2147483648L,10,8192,536870912,512,8);expected="Original Fastp process failed";}
            case "hard-cpu" -> {command.set(2,"while :; do :; done");budget=limits(10000,2147483648L,1,134217728,536870912,512,8);expected="Original Fastp process failed";}
            default -> throw new AssertionError(name);
        }
        Process sentinel=new ProcessBuilder("/usr/bin/sleep","60").directory(Path.of("/tmp").toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if(expected==null) {
                var result=FastpProcess.executeOwned(command,environment,work,budget,integrity,cancellation);
                assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());
                assertEquals("complete",Files.readString(result.stdout()));
            } else {
                var finalCommand=command;var finalBudget=budget;var finalIntegrity=integrity;var finalCancellation=cancellation;
                IOException failure=assertThrows(IOException.class,()->FastpProcess.executeOwned(
                        finalCommand,environment,work,finalBudget,finalIntegrity,finalCancellation));
                assertTrue(failure.toString().contains(expected),failure::toString);
            }
            Map<?,?> ledger=(Map<?,?>)SimpleJson.parse(Files.readString(work.resolve("process.json")));
            assertEquals(List.of(),ledger.get("owned_live_after_cleanup"));
            assertEquals(expected==null?"completed-process-only":"FAIL2-retained-no-result",ledger.get("status"));
            assertTrue(sentinel.isAlive(),"Unrelated process was killed");
            assertTrue(Files.readString(work.resolve("WARNINGS.md")).contains("Required action"));
            assertFalse(Files.exists(work.resolve("COMPLETED.json")),"A process exit cannot admit a scientific result");
        } finally {
            sentinel.destroyForcibly();assertTrue(sentinel.waitFor(5,TimeUnit.SECONDS));
        }
    }

    @Test void diagnosticJsonRoundTripsControlCharactersAndRejectsInvalidValues() throws Exception {
        String text="quoted \" slash \\ newline\n tab\t nul\0 Unicode α";
        assertEquals(text,((Map<?,?>)SimpleJson.parse(FastpJson.json(Map.of("value",text)))).get("value"));
        assertThrows(IOException.class,()->FastpJson.json(Double.NaN));
        assertThrows(IOException.class,()->FastpJson.json(Map.of(1,"invalid key")));
        assertThrows(IOException.class,()->FastpJson.json(new Object()));
    }
}
