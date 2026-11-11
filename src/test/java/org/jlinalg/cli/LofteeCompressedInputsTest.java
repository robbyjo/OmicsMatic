/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.object;

/** Real htslib indices and compressed original sample tokens, including refusal of damaged resources. */
final class LofteeCompressedInputsTest {
    @TempDir Path temporary;
    private Path controls()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("supplied"));
        try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/org/jlinalg/genetics/loftee-original/workflow-inputs-v1.zip")))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                NextflowFiles.member(entry.getName());assertFalse(entry.isDirectory());assertFalse(entry.getName().contains("/"));
                byte[] bytes=zip.readNBytes(1<<20);assertTrue(bytes.length<1<<20);Files.write(root.resolve(entry.getName()),bytes,StandardOpenOption.CREATE_NEW);
            }
        }
        return root;
    }
    private Map<String,Object> compressed(Path root)throws Exception {
        var q=LofteeWorkflowTest.base(root);q.put("input_vcf",root.resolve("genotyped.vcf.gz").toString());q.put("input_compression","gzip");
        var fa=object(q.get("fasta"));fa.put("file",root.resolve("reference.fa.gz").toString());fa.put("index",root.resolve("reference.fa.gz.fai").toString());fa.put("gzi",root.resolve("reference.fa.gz.gzi").toString());fa.put("compression","bgzip");q.put("fasta",fa);return q;
    }
    private LofteeWorkflowInputs.Prepared prepare(Map<String,Object> q)throws Exception {
        return LofteeWorkflowInputs.prepare(Files.createDirectory(temporary.resolve("attempt")),LofteeWorkflowRequest.parse(q,temporary),new ArrayList<>());
    }
    @Test void originalGzipGenotypesAndHtslibBgzfIndicesAreValidatedWithoutRewriting()throws Exception {
        Path root=controls();var p=prepare(compressed(root));assertEquals(15L,p.inspection().get("variants"));assertEquals(2,p.inspection().get("samples"));assertEquals(true,p.inspection().get("BGZF_GZI_validated_against_complete_block_boundaries"));assertArrayEquals(Files.readAllBytes(root.resolve("genotyped.vcf.gz")),Files.readAllBytes(p.directory().resolve("input.vcf.gz")));p.verify();
    }
    @Test void incorrectCompleteFastaIndexIsRefused()throws Exception {
        Path root=controls(),index=root.resolve("reference.fa.gz.fai");Files.writeString(index,Files.readString(index).replace("4000","3999"));assertThrows(IOException.class,()->prepare(compressed(root)));
    }
    @Test void fabricatedGziOffsetIsRefused()throws Exception {
        Path root=controls();byte[] fake=new byte[24];fake[0]=1;fake[8]=1;fake[16]=1;Files.write(root.resolve("reference.fa.gz.gzi"),fake);assertThrows(IOException.class,()->prepare(compressed(root)));
    }
    @Test void ordinaryGzipCannotBeRelabeledAsIndexedBgzf()throws Exception {
        Path root=controls();try(var stream=new GZIPOutputStream(Files.newOutputStream(root.resolve("reference.fa.gz")))){stream.write(Files.readAllBytes(root.resolve("reference.fa")));}assertThrows(IOException.class,()->prepare(compressed(root)));
    }
    @Test void truncatedGzipVcfCannotYieldAnAdmittedPrefix()throws Exception {
        Path root=controls(),vcf=root.resolve("genotyped.vcf.gz");byte[] bytes=Files.readAllBytes(vcf);Files.write(vcf,Arrays.copyOf(bytes,bytes.length-5));assertThrows(IOException.class,()->prepare(compressed(root)));
    }
    @Test void decodedCapacityRefusesWholeReferenceRatherThanOmittingSequence()throws Exception {
        Path root=controls();var q=compressed(root);q.put("maximum_decoded_characters",100);assertThrows(IOException.class,()->prepare(q));
    }
    @Test void suppliedAncestralAndSqliteResourcesAreCapturedWithExactOriginalBytes()throws Exception {
        Path root=controls();var q=compressed(root);String assembly="SYNTHETIC_LOFTEE_CONTROLS";q.put("ancestor",Map.of("kind","fasta","file",root.resolve("ancestor.fa").toString(),"index",root.resolve("ancestor.fa.fai").toString(),"compression","none","assembly_label",assembly,"release_label","synthetic-ancestor"));q.put("conservation",Map.of("kind","sqlite","file",root.resolve("conservation.sqlite").toString(),"assembly_label",assembly,"release_label","synthetic-PhyloCSF"));LofteeWorkflowTest.updateFirst(q,a->{a.put("ancestral_check",true);a.put("conservation_check",true);});var p=prepare(q);assertArrayEquals(Files.readAllBytes(root.resolve("conservation.sqlite")),Files.readAllBytes(p.directory().resolve("conservation.sqlite")));p.verify();
    }
    @Test void wrongGtfIndexMagicIsRefusedBeforeOriginalExecution()throws Exception {
        Path root=controls();try(var stream=new GZIPOutputStream(Files.newOutputStream(root.resolve("genes.gtf.gz.tbi")))){stream.write(new byte[]{'B','A','D',1});}assertThrows(IOException.class,()->prepare(compressed(root)));
    }
}
