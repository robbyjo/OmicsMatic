/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Malt052BuildCliTest {
    @TempDir Path root;
    private String[] args(Path output,String... additions)throws Exception {
        Path fasta=root.resolve("reference.fa"),mapping=root.resolve("mapping.tsv");if(!Files.exists(fasta))Files.writeString(fasta,">alpha\nACGT\n");if(!Files.exists(mapping))Files.writeString(mapping,"alpha\t1\n");
        var options=new ArrayList<>(List.of("--runtime",root.resolve("provider").toString(),"--fasta",fasta.toString(),"--mapping",mapping.toString(),"--format","two-column","--namespace","literal","--method","native-accession-alternative","--out",output.toString()));options.addAll(List.of(additions));return options.toArray(String[]::new);
    }
    @Test void helpStatesMethodDifferenceCompleteInputsAndRequiredRecovery() {
        var bytes=new ByteArrayOutputStream();assertEquals(0,Malt052BuildCli.run(new String[]{"--help"},new PrintStream(bytes),System.err));String text=bytes.toString();assertTrue(text.contains("native-accession-alternative"));assertTrue(text.contains("fasta-list"));assertTrue(text.contains("133120"));assertTrue(text.contains("Required action:"));assertTrue(text.contains("skip dependent"));
    }
    @Test void completeSourceCountersRequireUniqueWellFormedFinalNumbers()throws Exception {
        Path stderr=root.resolve("stderr");Files.writeString(stderr,"Number of sequences:           3\nNumber of letters:        52,244\n");assertEquals(Map.of("references",3L,"reference_bases",52244L),Malt052BuildCli.sourceCounters(stderr));
        for(String text:List.of("Number of sequences: 3\n","Number of sequences: 3\nNumber of letters: 52,24\n","Number of sequences: 3\nNumber of sequences: 3\nNumber of letters: 52244\n")){Files.writeString(stderr,text);assertThrows(IOException.class,()->Malt052BuildCli.sourceCounters(stderr));}
    }
    @Test void paperResourceRequestAndMultiFileOrderingAreRepresentableWithoutNameFallback()throws Exception {
        var limits=Malt052BuildCli.limits(Map.of("--threads","16","--heap-mib","133120","--gc-threads","1"));var inputs=List.of(Path.of("first.fa"),Path.of("second.fa.gz"));var command=Malt052BuildCli.command(Path.of("provider"),inputs,Path.of("mapping.tsv"),Path.of("index"),Path.of("output"),limits);int position=command.indexOf("--input");assertEquals("first.fa",command.get(position+1));assertEquals("second.fa.gz",command.get(position+2));assertEquals("false",command.get(command.indexOf("--parseTaxonNames")+1));assertEquals("false",command.get(command.indexOf("--firstWordOnly")+1));assertTrue(command.contains("-Xmx133120m"));assertTrue(command.contains("-XX:ParallelGCThreads=1"));var process=Malt052BuildCli.processLimits(limits);assertEquals(558345748480L,process.addressSpaceBytes());assertEquals(279172874240L,process.sampledRssBytes());assertEquals(256,process.threads());assertThrows(IOException.class,()->Malt052BuildCli.limits(Map.of("--gc-threads","0")));
    }
    @Test void existingOutputAndRuntimeNestedOutputRemainUntouched()throws Exception {
        Path provider=Files.createDirectory(root.resolve("provider")),old=Files.createDirectory(root.resolve("old")),keep=old.resolve("keep.txt");Files.writeString(keep,"retained");var output=new ByteArrayOutputStream();var console=new PrintStream(output);
        assertEquals(2,Malt052BuildCli.run(args(old),console,console));assertEquals("retained",Files.readString(keep));try(var files=Files.list(old)){assertEquals(1,files.count());}
        assertEquals(2,Malt052BuildCli.run(args(provider.resolve("nested")),console,console));assertFalse(Files.exists(provider.resolve("nested")));assertTrue(output.toString().contains("Required action:"));
    }
    @Test void missingOriginalRuntimeSkipsWithProminentInstructionsAndNoProcess()throws Exception {
        Path out=root.resolve("skipped");var output=new ByteArrayOutputStream();var console=new PrintStream(output);assertEquals(3,Malt052BuildCli.run(args(out),console,console));assertTrue(output.toString().contains("SKIP3"));assertTrue(output.toString().contains("Required action:"));assertTrue(Files.readString(out.resolve("WARNINGS_FAILURE.md")).contains("skip dependent index building and analysis"));assertTrue(Files.isRegularFile(out.resolve("FAILURE.json")));assertFalse(Files.exists(out.resolve("native/process")));assertFalse(Files.exists(out.resolve("COMPONENT_COMPLETED.json")));assertEquals(">alpha\nACGT\n",Files.readString(root.resolve("reference.fa")));
    }
    @Test void duplicateOptionsAndUndeclaredMethodCannotCreateResults()throws Exception {
        Path out=root.resolve("never");var output=new ByteArrayOutputStream();var console=new PrintStream(output);assertEquals(2,Malt052BuildCli.run(args(out,"--method","original"),console,console));assertFalse(Files.exists(out));assertTrue(output.toString().contains("NO RESULT ADMITTED"));
        String[] options=args(out);for(int i=0;i<options.length;i++)if(options[i].equals("native-accession-alternative"))options[i]="original";assertEquals(2,Malt052BuildCli.run(options,console,console));assertFalse(Files.exists(out));
    }
    @Test void duplicateReferenceListEntriesFailBeforeRuntimeOrOutputCreation()throws Exception {
        Path out=root.resolve("never"),list=root.resolve("files.tsv");String[] original=args(out);Files.writeString(list,"fasta\nreference.fa\nreference.fa\n");for(int i=0;i<original.length;i++)if(original[i].equals("--fasta")){original[i]="--fasta-list";original[i+1]=list.toString();}var output=new ByteArrayOutputStream();var console=new PrintStream(output);assertEquals(2,Malt052BuildCli.run(original,console,console));assertFalse(Files.exists(out));assertTrue(output.toString().contains("Duplicate FASTA"));
    }
}
