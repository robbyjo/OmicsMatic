/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.vcf.VCFCodec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.*;
import org.jlinalg.genetics.GraphtyperAlignmentAudit.Region;
import org.jlinalg.sequence.FastaIndexes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;

class GraphtyperVcfReferenceAuditTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path tmp;
    private Path fasta()throws Exception {Path f=tmp.resolve("reference.fa");Files.writeString(f,">20\n"+"ACGT".repeat(100)+"\n");FastaIndexes.build(f).write(tmp.resolve("reference.fa.fai"));return f;}
    private Path vcf(String name,String records)throws Exception {
        Path f=tmp.resolve(name+".vcf.gz");try(var out=new BlockCompressedOutputStream(f.toFile())){out.write(("##fileformat=VCFv4.2\n##contig=<ID=20,length=400>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n"+records).getBytes(StandardCharsets.UTF_8));}
        IndexFactory.createTabixIndex(f,new VCFCodec(),TabixFormat.VCF,null).write(tmp.resolve(name+".vcf.gz.tbi"));return f;
    }
    @Test void completePriorQueriesAndSamplesMatchLiteralSites()throws Exception {
        Path v=vcf("sites","20\t20\ta\tT\tC\t50\tPASS\t.\n20\t40\tb\tTAC\tT\t50\tPASS\t.\n");assertEquals(Set.of(),GraphtyperVcfReferenceAudit.samples(v,10000,100,()->{}));var r=GraphtyperVcfReferenceAudit.sitesIndex(v,tmp.resolve("sites.vcf.gz.tbi"),List.of(new Region("20",20,20),new Region("20",42,44)),100,10000,10000,()->{});assertEquals(List.of(1L,1L),r.stream().map(GraphtyperVcfReferenceAudit.Query::records).toList());
    }
    @Test void completeRecordTableMatchesActualReference()throws Exception {
        Path f=fasta(),v=vcf("reference-sites","20\t20\ta\tT\tC\t50\tPASS\t.\n");Path table=tmp.resolve("records.tsv");GraphtyperVcfAudit.audit(v,Set.of(),table,new GraphtyperVcfAudit.Limits(10000,100,1000,10000,100,10,10),()->{});assertEquals(1,GraphtyperVcfReferenceAudit.reference(table,f,Map.of("20",400L),10000,100,()->{}));
        Files.writeString(table,Files.readString(table).replace("\t20\tT\tC\t","\t20\tA\tC\t"));assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.reference(table,f,Map.of("20",400L),10000,100,()->{}));
    }
    @Test void incorrectIndexOrSymbolicAllelesFailWithoutRepair()throws Exception {
        Path v=vcf("index-sites","20\t20\ta\tT\tC\t50\tPASS\t.\n");Path bad=tmp.resolve("bad.tbi");Files.write(bad,new byte[]{1,2,3,4});assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.sitesIndex(v,bad,List.of(new Region("20",1,100)),100,10000,10000,()->{}));
        Path symbolic=vcf("symbolic","20\t20\ta\tT\t<DEL>\t50\tPASS\t.\n");assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.sitesIndex(symbolic,tmp.resolve("symbolic.vcf.gz.tbi"),List.of(new Region("20",1,100)),100,10000,10000,()->{}));
    }
    @Test void byteAndRecordBoundsAndCancellationAreEnforced()throws Exception {
        Path v=vcf("bounded","20\t20\ta\tT\tC\t50\tPASS\t.\n20\t40\tb\tT\tC\t50\tPASS\t.\n");Path index=tmp.resolve("bounded.vcf.gz.tbi");var targets=List.of(new Region("20",1,100));assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.sitesIndex(v,index,targets,1,10000,10000,()->{}));assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.sitesIndex(v,index,targets,100,1,10000,()->{}));var cancelled=assertThrows(IOException.class,()->GraphtyperVcfReferenceAudit.sitesIndex(v,index,targets,100,10000,10000,()->{throw new IllegalStateException("cancelled");}));assertInstanceOf(IllegalStateException.class,cancelled.getCause());assertEquals("cancelled",cancelled.getCause().getMessage());
    }
}
