/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GctaMtcojoWorkflowTest {
    @TempDir Path temp;
    @Test void completeOriginalRunsVerifyOfflineAndResealedInvalidConditionalRowsAreRefused() throws Exception {
        var archive=new HashMap<String,byte[]>();try(var source=getClass().getResourceAsStream("/org/jlinalg/cli/mtcojo1941-workflow-captures.zip")) {
            assertNotNull(source);try(var zip=new ZipInputStream(source)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())assertNull(archive.put(e.getName(),zip.readAllBytes()));}
        }
        var metadata=object(SimpleJson.parse(new String(archive.get("FILES.json"),StandardCharsets.UTF_8)),"metadata");var files=object(metadata.get("files"),"files");
        for(var entry:files.entrySet()) {
            var expected=object(entry.getValue(),"file");String sha=text(expected,"sha256");byte[] raw=archive.get("blobs/"+sha);assertNotNull(raw);assertEquals(((Number)expected.get("bytes")).intValue(),raw.length);assertEquals(sha,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            Path target=temp.resolve(entry.getKey()).normalize();assertTrue(target.startsWith(temp));Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);
        }
        var cases=list(metadata.get("cases"),"cases");assertEquals(8,cases.size());
        for(Object name:cases){Path root=temp.resolve((String)name);var error=new ByteArrayOutputStream();assertEquals(0,verify(root,error),name+": "+error.toString(StandardCharsets.UTF_8));var report=read(root.resolve("model-objects.json"));assertEquals(10000,((Number)report.get("conditional_rows")).intValue());assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("no liability conversion is claimed"));}
        Path root=temp.resolve("windows/multi-supplied");Path table=root.resolve("worker/result.mtcojo.cma");List<String> lines=Files.readAllLines(table);String[] row=lines.get(1).split("\t",-1);row[9]="NaN";lines.set(1,String.join("\t",row));Files.write(table,lines);GctaWorkflowCli.seal(root);
        var error=new ByteArrayOutputStream();assertEquals(2,verify(root,error));assertTrue(error.toString(StandardCharsets.UTF_8).contains("Expected finite mtCOJO result"));
    }
    private static int verify(Path root,ByteArrayOutputStream error) {return GctaWorkflowCli.run(new String[]{"--verify",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error,true,StandardCharsets.UTF_8));}
}
