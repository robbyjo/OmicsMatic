/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.GraphtyperAlignmentAudit;
import org.jlinalg.genetics.GraphtyperAlignmentAudit.*;
import org.jlinalg.sequence.FastaIndexes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;

class GraphtyperAlignmentAuditTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path tmp;
    private final Limits limits=new Limits(100,100000,10000,151);
    private Path reference()throws Exception {
        Path p=tmp.resolve("reference.fa");Files.writeString(p,">20\n"+"ACGT".repeat(100)+"\n");FastaIndexes.build(p).write(tmp.resolve("reference.fa.fai"));return p;
    }
    private Path bam(String name,String md5,boolean rg,boolean index,int second)throws Exception {
        Path p=tmp.resolve(name+".bam");var h=new SAMFileHeader();h.setSortOrder(SAMFileHeader.SortOrder.coordinate);var sequence=new SAMSequenceRecord("20",400);if(md5!=null)sequence.setMd5(md5);h.addSequence(sequence);
        var group=new SAMReadGroupRecord("group1");group.setSample("literal.sample-1");h.addReadGroup(group);
        try(var writer=new SAMFileWriterFactory().setCreateIndex(index).makeBAMWriter(h,true,p.toFile())){
            for(int start:new int[]{20,second}){var r=new SAMRecord(h);r.setReadName("read-"+start);r.setReferenceName("20");r.setAlignmentStart(start);r.setReadString("ACGTACGTACGT");r.setBaseQualityString("I".repeat(12));r.setCigarString("12M");r.setMappingQuality(60);if(rg)r.setAttribute("RG","group1");writer.addAlignment(r);}
        }return p;
    }
    private Result audit(Path p,Path f,Limits bounds,Runnable check)throws Exception {
        return GraphtyperAlignmentAudit.audit(p,tmp.resolve(p.getFileName().toString().replace(".bam",".bai")),f,GraphtyperAlignmentAudit.reference(f,tmp.resolve("reference.fa.fai"),10000,check),List.of(new Region("20",25,100),new Region("20",180,230)),bounds,check);
    }
    @Test void fullSequentialAndIndexedPopulationsAgree()throws Exception {
        Path f=reference(),p=bam("valid",null,true,true,200);var r=audit(p,f,limits,()->{});
        assertEquals(2,r.records());assertEquals(2,r.mappedRecords());assertEquals(List.of("literal.sample-1"),r.sampleIds());assertEquals(12,r.maximumReadLength());assertEquals(List.of(1L,1L),r.queries().stream().map(Query::records).toList());assertNotEquals(r.queries().get(0).allSamFieldsSha256(),r.queries().get(1).allSamFieldsSha256());
    }
    @Test void actualReferenceAndDeclaredMd5MustAgree()throws Exception {
        Path f=reference(),p=bam("bad-md5","0".repeat(32),true,true,200);assertThrows(IOException.class,()->audit(p,f,limits,()->{}));
        Files.writeString(tmp.resolve("reference.fa.fai"),"20\t400\t4\t399\t400\n");assertThrows(IOException.class,()->GraphtyperAlignmentAudit.reference(f,tmp.resolve("reference.fa.fai"),10000,()->{}));
    }
    @Test void missingMappedReadGroupAndMissingOrInvalidIndexFail()throws Exception {
        Path f=reference(),p=bam("no-rg",null,false,true,200);assertThrows(IOException.class,()->audit(p,f,limits,()->{}));
        Path q=bam("bad-index",null,true,false,200);Files.write(tmp.resolve("bad-index.bai"),new byte[]{'B','A','I',1,0,0,0,0});assertThrows(IOException.class,()->audit(q,f,limits,()->{}));
    }
    @Test void completeReadAndCharacterBoundsAndCancellationFail()throws Exception {
        Path f=reference(),p=bam("bounded",null,true,true,200);assertThrows(IOException.class,()->audit(p,f,new Limits(1,100000,10000,151),()->{}));assertThrows(IOException.class,()->audit(p,f,new Limits(100,1,10000,151),()->{}));assertThrows(IOException.class,()->audit(p,f,new Limits(100,100000,10000,11),()->{}));assertThrows(IllegalStateException.class,()->audit(p,f,limits,()->{throw new IllegalStateException("cancelled");}));
    }
}
