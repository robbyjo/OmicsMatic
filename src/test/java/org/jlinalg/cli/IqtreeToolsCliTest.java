/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.phylogeny.NewickTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IqtreeToolsCliTest {
    @TempDir Path temporary;
    @Test void helpExplainsExactRuntimeScopeCompletionAndRequiredRecovery() {
        var output=new ByteArrayOutputStream();assertEquals(0,IqtreeToolsCli.run(new String[]{"--help"},new PrintStream(output),new PrintStream(new ByteArrayOutputStream())));
        String text=output.toString(java.nio.charset.StandardCharsets.UTF_8);for(String expected:List.of("--verify","--export-runtime-setup","1.6.12","2.1.4-beta","SKIP3","FAIL2","COMPLETED.json","Cell Ranger","Partitions"))assertTrue(text.contains(expected),expected);
    }
    @Test void unavailableRuntimeHasProminentSkipAndNoCompletionMarker()throws Exception {
        Path output=temporary.toRealPath().resolve("skip");var error=new ByteArrayOutputStream();
        assertEquals(3,IqtreeToolsCli.run(new String[]{"--recipe",temporary.resolve("missing.json").toString(),"--out",output.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error)));
        assertTrue(error.toString().contains("SKIP3"));assertTrue(error.toString().contains("Required action"));assertFalse(Files.exists(output.resolve("COMPLETED.json")));
        assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("ANALYSIS SKIPPED"));assertThrows(IOException.class,()->IqtreeBundle.verify(output));
    }
    @Test void existingOutputAndUnknownFlagsArePreservedWithoutStartingWork()throws Exception {
        Path output=temporary.toRealPath().resolve("existing");Files.createDirectory(output);Files.writeString(output.resolve("keep"),"unchanged");
        assertEquals(2,IqtreeToolsCli.run(new String[]{"--recipe","missing","--out",output.toString()},System.out,new PrintStream(new ByteArrayOutputStream())));assertEquals("unchanged",Files.readString(output.resolve("keep")));
        try(var files=Files.list(output)){assertEquals(1,files.count());}
        assertEquals(2,IqtreeToolsCli.run(new String[]{"--program","/bin/sh"},System.out,new PrintStream(new ByteArrayOutputStream())));
    }
    @Test void portableIntegrityIncludesEmptyDirectoriesAndEveryMemberByte()throws Exception {
        Path root=temporary.toRealPath().resolve("constructed-byte-integrity-fixture");IqtreeBundle.create(root);Files.writeString(root.resolve("example"),"synthetic integrity test only");
        var census=IqtreeBundle.inventory(root,1000000,100,()->{});Path manifest=root.resolve("manifest.json");
        IqtreeBundle.write(manifest,Map.of("schema_version",1,"tool","IQ-TREE","status","audited-provisional-awaiting-finalization","files",IqtreeBundle.portable(census),"directories",new TreeSet<>(census.directories()),"maximum_bundle_bytes",1000000,"maximum_bundle_files",100));
        IqtreeBundle.write(root.resolve("COMPLETED.json"),Map.of("tool","IQ-TREE","scientific_result_admitted",true,"manifest_sha256",IqtreeFiles.identity(manifest,16777216,()->{}).sha256()));
        assertNotNull(IqtreeBundle.verify(root));Files.createDirectory(root.resolve("extra-empty"));assertThrows(IOException.class,()->IqtreeBundle.verify(root));Files.delete(root.resolve("extra-empty"));
        Files.writeString(root.resolve("example"),"changed");assertThrows(IOException.class,()->IqtreeBundle.verify(root));
    }
    @Test void fixedLengthAuditsUseOutputRoundingAndTreatInputDecimalsAsExact()throws Exception {
        var original=NewickTree.parse("((A:.1,B:.2):.3,(C:.4,D:.5):.6);");
        var equivalent=NewickTree.parse("(A:0.1000000000,B:0.2000000000,(C:0.4000000000,D:0.5000000000):0.9000000000);");
        IqtreeAnalysis.fixedLengths(original,equivalent,1000,new ArrayList<>());
        var changed=NewickTree.parse("(A:0.1400000000,B:0.2000000000,(C:0.4000000000,D:0.5000000000):0.9000000000);");
        assertThrows(IOException.class,()->IqtreeAnalysis.fixedLengths(original,changed,1000,new ArrayList<>()));
    }
}
