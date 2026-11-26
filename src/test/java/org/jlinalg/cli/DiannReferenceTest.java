/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class DiannReferenceTest {
    @TempDir Path tmp;
    static DiannReference.Limits limits(){return new DiannReference.Limits(100,10000,100000,1024);}
    Path input(String value)throws IOException{return Files.writeString(tmp.resolve(UUID.randomUUID()+".fasta"),value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    @Test void preservesLiteralHeadersResiduesDuplicateSequencesAndMissingGenes()throws Exception {
        String a="sp|P1-2|ENTRY1_HUMAN Original 蛋白 OS=Homo sapiens GN=GENE1 PE=1";
        String b="tr|Q2|ENTRY2_HUMAN Original without gene";
        Path path=input(">"+a+"\r\nMUKX\r\nAC\r\n>"+b+"\nMUKXAC");byte[] original=Files.readAllBytes(path);
        var reference=DiannReference.read(path,limits(),()->{});
        assertEquals(List.of("P1-2","Q2"),new ArrayList<>(reference.proteins().keySet()));assertEquals(12,reference.residues());
        var first=reference.proteins().get("P1-2");assertEquals(a,first.header());assertEquals("sp|P1-2|ENTRY1_HUMAN",first.firstToken());assertEquals("ENTRY1_HUMAN",first.entryName());assertEquals("GENE1",first.gene());assertEquals("MUKXAC",first.sequence());
        var second=reference.proteins().get("Q2");assertEquals(b,second.header());assertEquals(first.sequence(),second.sequence());assertNull(second.gene());
        assertThrows(UnsupportedOperationException.class,()->reference.proteins().remove("Q2"));assertArrayEquals(original,Files.readAllBytes(path));
    }
    @Test void duplicateAccessionsAreNeverCollapsedOrSelected()throws Exception {
        Path path=input(">sp|P1|ENTRY1_HUMAN\nAC\n>tr|P1|ENTRY2_HUMAN\nAD\n");byte[] original=Files.readAllBytes(path);
        assertThrows(IOException.class,()->DiannReference.read(path,limits(),()->{}));assertArrayEquals(original,Files.readAllBytes(path));
    }
    @Test void rejectsUnresolvedHeaderAndGeneSyntaxWithoutSubstitution()throws Exception {
        for(String header:List.of("arbitrary-id","sp||ENTRY","sp|P1|","sp|P1|ENTRY|extra","sp|P1;P2|ENTRY","sp|P1|ENTRY GN=","sp|P1|ENTRY GN=A GN=B","sp|P1|ENTRY GN=A;B","sp|P1|ENTRY\tGN=A")){
            Path path=input(">"+header+"\nAC\n");byte[] original=Files.readAllBytes(path);
            assertThrows(IOException.class,()->DiannReference.read(path,limits(),()->{}),header);assertArrayEquals(original,Files.readAllBytes(path));
        }
    }
    @Test void malformedRecordsAndEncodingAreWholeReferenceFailures()throws Exception {
        for(String value:List.of("","AC\n",">sp|P1|ENTRY\n",">sp|P1|ENTRY\n>sp|P2|ENTRY\nAC\n",">sp|P1|ENTRY\n\nAC\n",">sp|P1|ENTRY\nAc\n",">sp|P1|ENTRY\nA C\n",">sp|P1|ENTRY\nAC*\n",">sp|P1|ENTRY\nA-C\n","\uFEFF>sp|P1|ENTRY\nAC\n")){
            Path path=input(value);assertThrows(IOException.class,()->DiannReference.read(path,limits(),()->{}));
        }
        Path invalid=Files.write(tmp.resolve("bad-utf8.fasta"),new byte[]{'>','s','p','|','P','1','|','E',(byte)0xc3,(byte)0x28,'\n','A','C'});
        assertThrows(IOException.class,()->DiannReference.read(invalid,limits(),()->{}));
    }
    @Test void recordResidueByteLineAndCancellationLimitsRejectTheWholeRead()throws Exception {
        Path path=input(">sp|P1|E\nAC\n>sp|P2|E\nAD\n");
        for(var limit:List.of(new DiannReference.Limits(1,100,1000,100),new DiannReference.Limits(2,3,1000,100),new DiannReference.Limits(2,100,1,100),new DiannReference.Limits(2,100,1000,4)))assertThrows(IOException.class,()->DiannReference.read(path,limit,()->{}));
        assertThrows(CancellationException.class,()->DiannReference.read(path,limits(),()->{throw new CancellationException("controlled cancellation");}));
    }
    @Test void capacityDefinitionsMustBeExplicitAndPositive(){
        assertThrows(IllegalArgumentException.class,()->new DiannReference.Limits(0,1,1,1));assertThrows(IllegalArgumentException.class,()->new DiannReference.Limits(1,0,1,1));assertThrows(IllegalArgumentException.class,()->new DiannReference.Limits(1,1,0,1));assertThrows(IllegalArgumentException.class,()->new DiannReference.Limits(1,1,1,0));
    }
}
