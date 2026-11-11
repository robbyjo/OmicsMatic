/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowSetupTest {
    @TempDir Path temp;
    @Test void exportIsCompleteAndDoesNotRunSoftware()throws Exception{
        Path output=temp.resolve("setup");NextflowSetup.export(output);var record=NextflowFiles.object(NextflowFiles.json(output.resolve("export.json"),65536));assertEquals("EXPORTED_SETUP_NOT_RUN",record.get("status"));assertEquals(false,record.get("user_analysis_admitted"));var files=NextflowFiles.object(record.get("files"));assertEquals(3,files.size());
        for(var row:files.entrySet()){Path file=output.resolve(NextflowFiles.text(row.getKey()));assertEquals(NextflowFiles.fingerprint(row.getValue(),65536),NextflowFiles.pin(file,65536,()->{}));}
        String guide=Files.readString(output.resolve("README.txt"));for(String required:new String[]{"python3 install_nextflow.py","--allow-install","--allow-download","SKIP3","FAIL2","NEW paths","Cell Ranger"})assertTrue(guide.contains(required),required);
        assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertFalse(Files.exists(output.resolve("home")));
    }
    @Test void existingExportIsNeverOverwritten()throws Exception{
        Path output=temp.resolve("setup");NextflowSetup.export(output);String before=Files.readString(output.resolve("export.json"));assertThrows(IOException.class,()->NextflowSetup.export(output));assertEquals(before,Files.readString(output.resolve("export.json")));assertFalse(Files.exists(output.resolve("FAILURE.txt")));
    }
    @Test void changedBundledResourcePinIsRejected(){assertThrows(IOException.class,()->NextflowRuntime.resource("health.nf",new NextflowFiles.Pin(445,"0".repeat(64))));}
}
