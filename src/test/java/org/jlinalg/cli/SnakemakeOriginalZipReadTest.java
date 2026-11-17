/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalZipReadTest {
    @TempDir Path temp;
    @Test void completePinnedArchiveBytesDoNotRequireUnsupportedZipStreamOptions()throws Exception {
        Path archive=temp.resolve("payload.zip");byte[] payload="/unavailable/producer/payload/link-target\n".getBytes(StandardCharsets.UTF_8);
        try(var zip=FileSystems.newFileSystem(archive,Map.of("create","true"))){Files.write(zip.getPath("/literal-link-data"),payload);}
        byte[] before=Files.readAllBytes(archive);
        try(var zip=FileSystems.newFileSystem(archive,Map.of())){
            Path entry=zip.getPath("/literal-link-data");
            assertEquals(new SnakemakeOriginalFiles.Pin(payload.length,SnakemakeOriginalFiles.sha(payload)),SnakemakeOriginalFiles.pin(entry,1000,()->{}));
            assertArrayEquals(payload,SnakemakeOriginalFiles.bytes(entry,1000));
        }
        assertArrayEquals(before,Files.readAllBytes(archive));
    }
}
