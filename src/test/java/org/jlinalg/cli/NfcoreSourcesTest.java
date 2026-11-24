/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NfcoreSourcesTest {
    @TempDir Path temporary;
    @Test void exactSevenSourcesAnd998MembersDoNotClaimExecution()throws Exception{
        var profiles=NfcoreSources.profiles();assertEquals(7,profiles.size());int files=0;
        for(var entry:profiles.entrySet()){
            var p=NextflowFiles.object(entry.getValue());assertEquals("MIT",p.get("license"));assertEquals(false,p.get("analysis_admitted"));
            assertEquals(40,NextflowFiles.text(p.get("commit")).length());assertTrue(NextflowFiles.text(p.get("source_url")).endsWith(NextflowFiles.text(p.get("commit"))));
            files+=NextflowFiles.object(p.get("files")).size();
        }
        assertEquals(998,files);assertFalse(profiles.containsKey("nf-core/rnaseq-1.0.4"));
        assertEquals("!>=20.07.1",NfcoreSources.profile("nf-core/eager-2.3.4").get("nextflow_constraint_literal"));
        assertTrue(NextflowJson.json(NfcoreSources.profile("nf-core/eager-2.4.4")).contains("GATK 3.5"));
    }
    @Test void exportContainsExactHelpersAndReadableScope()throws Exception{
        Path output=temporary.resolve("setup");NfcoreSources.export(output);
        assertArrayEquals(NfcoreSources.resource("source-lock.json",NfcoreSources.LOCK),Files.readAllBytes(output.resolve("source-lock.json")));
        assertArrayEquals(NfcoreSources.resource("setup.py",NfcoreSources.SETUP),Files.readAllBytes(output.resolve("setup.py")));
        String text=Files.readString(output.resolve("README.txt"));
        for(String phrase:List.of("--allow-install","--allow-download","SKIP3","FAIL2","GATK 3.5","no engine","NEW_REPORT"))assertTrue(text.contains(phrase),phrase);
        assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertEquals(3,Files.list(output).count());
    }
    @Test void existingExportAndItsExactBytesArePreserved()throws Exception{
        Path output=temporary.resolve("setup");NfcoreSources.export(output);byte[] before=Files.readAllBytes(output.resolve("setup.py"));
        assertThrows(FileAlreadyExistsException.class,()->NfcoreSources.export(output));assertArrayEquals(before,Files.readAllBytes(output.resolve("setup.py")));assertFalse(Files.exists(output.resolve("FAILURE.txt")));
    }
    @Test void changedBundledIdentityIsRejected(){assertThrows(IOException.class,()->NfcoreSources.resource("source-lock.json",new NextflowFiles.Pin(NfcoreSources.LOCK.bytes(),"0".repeat(64))));}
    @Test void missingOrUnsupportedPrerequisitesSkipWithAction()throws Exception{
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(3,NfcoreToolsCli.run(new String[]{"--inspect-source","nf-core/chipseq-1.2.1",temporary.resolve("missing").toString()},new PrintStream(out),new PrintStream(err)));
        assertTrue(err.toString().contains("SKIP3")&&err.toString().contains("export source setup"));
        assertThrows(NfcoreSources.Unavailable.class,()->NfcoreSources.profile("nf-core/chipseq-latest"));
    }
    @Test void executionIsNotSilentlySubstitutedBySourceInspection(){
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(2,NfcoreToolsCli.run(new String[]{"--request","anything.json"},new PrintStream(out),new PrintStream(err)));assertTrue(err.toString().contains("FAIL2"));assertEquals(0,out.size());
    }
}
