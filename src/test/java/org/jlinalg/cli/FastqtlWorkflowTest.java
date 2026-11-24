/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.junit.jupiter.api.Assertions.*;

/** Closed contracts and honest producer/refusal behavior; real upstream comparisons use the packaged JAR. */
final class FastqtlWorkflowTest {
    @TempDir Path temp;
    private Map<String,Object> request()throws Exception {
        return GprofilerWorkflowContracts.object(GprofilerJson.read(("""
          {"schema_version":1,"mode":"RUN","profile":"fastqtl-2.184-v7-ubuntu-noble-prepared-v1",
           "operation":"NOMINAL","runtime_home":"runtime","out":"result","assembly":"GRCh38",
           "region":"22:100-200","inputs":{"vcf":"g.gz","vcf_index":"g.gz.tbi","phenotypes":"p.gz","phenotype_index":"p.gz.tbi"},
           "options":{"seed":17,"window":1000,"normal":false,"maf_threshold":0,"ma_sample_threshold":0,
           "anchor_policy":"bed-start-plus-one","input_reviewed":true,"threshold":1}}
          """).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void rejectsHistoricalEditionSubstitution()throws Exception {var r=request();r.put("profile","fastqtl-2.165");assertThrows(FastqtlWorkflowRuntime.Unavailable.class,()->FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));}
    @Test void rejectsUnreviewedAnchorsAndUnknownFlags()throws Exception {var r=request();var o=GprofilerWorkflowContracts.object(r.get("options"));o.put("anchor_policy","infer-TSS");r.put("options",o);assertThrows(IOException.class,()->FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));o.put("anchor_policy","bed-start-plus-one");o.put("map-full",true);assertThrows(IOException.class,()->FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));}
    @Test void rejectsUnsafePermutationsAndCoordinates()throws Exception {var r=request();r.put("operation","PERMUTATION");var o=GprofilerWorkflowContracts.object(r.get("options"));o.remove("threshold");o.put("permutations",List.of(100,10));r.put("options",o);assertThrows(IOException.class,()->FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));o.put("permutations",List.of(10,100));r.put("region","22:200-100");assertThrows(IOException.class,()->FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));}
    @Test void normalizesPathsAndKeepsAdaptiveSemantics()throws Exception {var r=request();r.put("operation","PERMUTATION");var o=GprofilerWorkflowContracts.object(r.get("options"));o.remove("threshold");o.put("permutations",List.of(10,5,100));r.put("options",o);var result=FastqtlWorkflowContracts.validate(r,temp.resolve("r.json"));assertEquals(temp.resolve("result").toString(),result.get("out"));assertEquals(List.of(10,5,100),GprofilerWorkflowContracts.object(result.get("options")).get("permutations"));}
    @Test void looseClassesCannotBecomeScientificProducer(){assertThrows(FastqtlWorkflowRuntime.Unavailable.class,FastqtlToolsCli::producer);}
    @Test void validatesIndependentFullAndRegionalIndexCursors()throws Exception {
        var r=request();r.put("region","22:100-200");Path inputs=Files.createDirectories(temp.resolve("capture/inputs"));
        String samples="S1\tS2\tS3\tS4\tS5\tS6\tS7\tS8";
        indexed(inputs.resolve("phenotypes.bed.gz"),htsjdk.tribble.index.tabix.TabixFormat.BED,"#chr\tstart\tend\tid\t"+samples+"\n",List.of("22\t49\t50\tpBefore\t1\t2\t3\t4\t5\t6\t7\t8","22\t149\t150\tpInside\t1\t2\t3\t4\t5\t6\t7\t8","22\t249\t250\tpAfter\t1\t2\t3\t4\t5\t6\t7\t8"),new int[]{50,150,250});
        indexed(inputs.resolve("genotypes.vcf.gz"),htsjdk.tribble.index.tabix.TabixFormat.VCF,"##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t"+samples+"\n",List.of("22\t150\tv1\tA\tG\t.\tPASS\t.\tGT\t0/0\t0/1\t1/1\t0/0\t0/1\t1/1\t0/0\t0/1"),new int[]{150});
        var data=FastqtlWorkflowData.inputs(inputs.getParent(),FastqtlWorkflowContracts.validate(r,temp.resolve("r.json")));assertEquals(Set.of("pInside"),data.phenotypes().keySet());assertEquals(Set.of("v1"),data.variants().keySet());
    }
    private static void indexed(Path file,htsjdk.tribble.index.tabix.TabixFormat format,String header,List<String> rows,int[] positions)throws Exception {
        var index=new htsjdk.tribble.index.tabix.TabixIndexCreator(format);long end;
        try(var out=new htsjdk.samtools.util.BlockCompressedOutputStream(file.toFile())){out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));for(int i=0;i<rows.size();i++){index.addFeature(new htsjdk.tribble.bed.SimpleBEDFeature(positions[i],positions[i],"22"),out.getFilePointer());out.write((rows.get(i)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}end=out.getFilePointer();}
        index.finalizeIndex(end).writeBasedOnFeaturePath(file);
    }
    @Test void boundedLiteralTextRejectsRepairAndGrowth()throws Exception {var r=FastqtlWorkflowContracts.validate(request(),temp.resolve("r.json"));Path f=temp.resolve("input.txt");Files.writeString(f,"a\r\n");try(var lines=new FastqtlWorkflowData.Lines(f,r)){assertThrows(IOException.class,lines::next);}var limits=GprofilerWorkflowContracts.object(r.get("limits"));limits.put("maximum_line_characters",2);r.put("limits",limits);Files.writeString(f,"abc\n");try(var lines=new FastqtlWorkflowData.Lines(f,r)){assertThrows(IOException.class,lines::next);}}
    @Test void helpAndInvalidRequestsReturnDistinctCodes(){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(0,FastqtlToolsCli.run(new String[]{"--help"},new PrintStream(out),new PrintStream(err)));assertTrue(out.toString().contains("not historical 2.165"));assertEquals(2,FastqtlToolsCli.run(new String[]{"--invented"},new PrintStream(out),new PrintStream(err)));assertTrue(err.toString().contains("FAIL2_NO_COMPLETE_RESULT"));}
}
