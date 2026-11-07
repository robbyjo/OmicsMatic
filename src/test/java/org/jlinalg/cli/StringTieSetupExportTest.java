/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StringTieSetupExportTest {
    @TempDir Path temporary;
    @Test void completeStandaloneKitRetainsAllProfilesLicensesInputsAndPermissionGuidance()throws Exception{
        Path root=StringTieSetupExport.export(temporary.resolve("kit").toAbsolutePath());var manifest=NextflowFiles.object(NextflowFiles.json(root.resolve("export.json"),1000000));
        assertEquals("EXPORTED_KIT_NOT_EXECUTED",manifest.get("status"));assertEquals(Boolean.FALSE,manifest.get("scientific_runtime_admitted"));assertEquals(Boolean.FALSE,manifest.get("user_analysis_admitted"));assertEquals(Boolean.FALSE,manifest.get("installation_performed"));
        var files=NextflowFiles.object(manifest.get("files"));assertEquals(14,files.size());assertFalse(Files.exists(root.resolve("INCOMPLETE.txt")));assertFalse(Files.exists(root.resolve("FAILURE.txt")));
        for(var e:files.entrySet())assertEquals(NextflowFiles.fingerprint(e.getValue(),1048576),NextflowFiles.pin(root.resolve(NextflowFiles.text(e.getKey())),1048576,()->{}));
        var lock=NextflowFiles.object(NextflowFiles.json(root.resolve("stringtie-runtime-lock.json"),1000000));assertEquals(7,NextflowFiles.object(lock.get("profiles")).size());
        String guide=Files.readString(root.resolve("RUN-SETUP.txt"));for(String text:new String[]{"--allow-install","--allow-download","--archive-dir","SKIP3","skip dependent analysis","FAIL2","NEW","no scientific runtime admission","Cell Ranger"})assertTrue(guide.contains(text),text);
        assertTrue(Files.readString(root.resolve("licenses/1.3.0.txt")).startsWith("Artistic License 2.0"));assertTrue(Files.readString(root.resolve("licenses/3.0.3.txt")).startsWith("MIT License"));
        assertEquals(0,Files.list(root).filter(p->p.getFileName().toString().equals("bin")).count());
    }
    @Test void existingExportIsNeverOverwritten()throws Exception{
        Path root=StringTieSetupExport.export(temporary.resolve("kit").toAbsolutePath());byte[] before=Files.readAllBytes(root.resolve("export.json"));assertThrows(IOException.class,()->StringTieSetupExport.export(root));assertArrayEquals(before,Files.readAllBytes(root.resolve("export.json")));assertFalse(Files.exists(root.resolve("FAILURE.txt")));
    }
    @Test void helpAndInvalidArgumentsDoNotInstallOrAnalyze()throws Exception{
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8);
        assertEquals(0,StringTieSetupExport.run(new String[]{"--help"},out,err));assertTrue(output.toString(StandardCharsets.UTF_8).contains("Export only"));
        assertEquals(2,StringTieSetupExport.run(new String[]{"--allow-install"},out,err));assertTrue(error.toString(StandardCharsets.UTF_8).contains("FAIL2"));
        Path root=temporary.resolve("cli-kit").toAbsolutePath();assertEquals(0,StringTieSetupExport.run(new String[]{"--out",root.toString()},out,err));assertTrue(Files.exists(root.resolve("export.json")));
    }
    @Test void whitespaceAndMissingParentNeedExplicitCorrectedNewPaths(){
        assertThrows(IOException.class,()->StringTieSetupExport.export(temporary.resolve("has space").toAbsolutePath()));assertThrows(IOException.class,()->StringTieSetupExport.export(temporary.resolve("absent/kit").toAbsolutePath()));
    }
}
