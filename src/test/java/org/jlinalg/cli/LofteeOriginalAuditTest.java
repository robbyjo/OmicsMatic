/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.genetics.LofteeVcfAudit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Whole literal original VCFs checked against independently reconstructed Python/source ledgers. */
final class LofteeOriginalAuditTest {
    @TempDir static Path corpus;static List<String> cases;
    static Map<String,Object> json(Path path)throws IOException{return object(NextflowParameters.parse(Files.readString(path,StandardCharsets.UTF_8),1<<20,200000));}
    @BeforeAll static void load()throws Exception {
        Set<String> names=new HashSet<>();
        try(var zip=new ZipInputStream(Objects.requireNonNull(LofteeOriginalAuditTest.class.getResourceAsStream("/org/jlinalg/genetics/loftee-original/controls-v1.zip")))) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                NextflowFiles.member(entry.getName());assertTrue(names.add(entry.getName()));byte[] raw=zip.readNBytes(1<<20);assertTrue(raw.length<(1<<20));Path path=corpus.resolve(entry.getName());Files.createDirectories(path.getParent());Files.write(path,raw,StandardOpenOption.CREATE_NEW);
            }
        }
        var index=json(corpus.resolve("INDEX.json"));assertEquals("complete-original-VEP105-LOFTEE-synthetic-transports",index.get("status"));assertEquals(object(index.get("files")).size()+1,names.size());
        for(var e:object(index.get("files")).entrySet())assertEquals(NextflowFiles.fingerprint(e.getValue(),1<<20),NextflowFiles.pin(corpus.resolve(e.getKey()),1<<20,()->{}));cases=strings(index.get("cases"),4);assertEquals(4,cases.size());
    }
    static Stream<String> cases(){return cases.stream();}
    @ParameterizedTest @MethodSource("cases") void retainsCompleteOriginalFamiliesAndIndependentSourceLedgers(String name)throws Exception {
        Path home=corpus.resolve(name);StringBuilder variants=new StringBuilder("row\tchromosome\tposition\tid\tref\talts\tannotations\ttargets\tHC\tLC\tOS\n");StringBuilder annotations=new StringBuilder("row\tannotation\tallele_number\tchromosome\tposition\tid\tref\talt\tgene\tfeature\tconsequence\tconfidence\tfilters\tflags\tinfo\traw_CSQ\n");List<Path> logs=new ArrayList<>(List.of(home.resolve("original/vep.stdout"),home.resolve("original/vep.stderr")));Path warning=home.resolve("original/original.warnings.txt");if(Files.exists(warning))logs.add(warning);
        var summary=LofteeVcfAudit.audit(home.resolve("inputs/input.vcf"),home.resolve("original/original.vcf"),logs,false,new LofteeVcfAudit.Limits(1000000,1000,10000,100000),x->variants.append(x.row()).append('\t').append(x.chromosome()).append('\t').append(x.position()).append('\t').append(x.id()).append('\t').append(x.reference()).append('\t').append(x.alternates()).append('\t').append(x.annotations()).append('\t').append(x.targetTranscripts()).append('\t').append(x.highConfidence()).append('\t').append(x.lowConfidence()).append('\t').append(x.otherSplice()).append('\n'),x->annotations.append(x.row()).append('\t').append(x.annotation()).append('\t').append(x.alleleNumber()).append('\t').append(x.chromosome()).append('\t').append(x.position()).append('\t').append(x.id()).append('\t').append(x.reference()).append('\t').append(x.alternate()).append('\t').append(x.gene()).append('\t').append(x.feature()).append('\t').append(x.consequence()).append('\t').append(x.confidence()).append('\t').append(x.filters()).append('\t').append(x.flags()).append('\t').append(x.info()).append('\t').append(x.rawCsq()).append('\n'));
        assertEquals(15,summary.variants());assertEquals(80,summary.annotations());assertEquals(name.endsWith("original-default")?8:10,summary.highConfidence());assertEquals(name.endsWith("original-default")?2:0,summary.lowConfidence());assertEquals(Files.readString(home.resolve("expected-variants.tsv")),variants.toString());assertEquals(Files.readString(home.resolve("expected-annotations.tsv")),annotations.toString());
    }
}
