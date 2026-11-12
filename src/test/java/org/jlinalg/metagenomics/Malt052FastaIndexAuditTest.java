/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class Malt052FastaIndexAuditTest {
    @TempDir Path root;
    private static final Malt052FastaIndexAudit.Limits LIMITS=new Malt052FastaIndexAudit.Limits(1000000,100,1000,1000000,100000);
    static Stream<String> positives(){return Stream.of("lf-wrapped","lf-no-final-newline","crlf-wrapped","blank-between-lines","header-trailing-space","sequence-inline-space","sequence-other-ascii","semicolon-comment","leading-space-header","unicode-header","multiple-files","gzip-wrapped");}
    static Stream<String> refusals(){return Stream.of("blank-before-header","empty-sequence","unicode-sequence","duplicate-first-word");}
    private static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private Path extract(String name)throws Exception {
        byte[] archive;try(var stream=getClass().getResourceAsStream("/malt052-fasta-controls.zip")){assertNotNull(stream);archive=stream.readAllBytes();}
        assertEquals("afb5eaca60d9898c489c8a7ed959b58b249d352707d7a37a8a9416e9ada837f5",sha(archive));
        Path out=Files.createDirectory(root.resolve(name));int count=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(archive))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().startsWith(name+"/")){assertFalse(entry.isDirectory());assertTrue(++count<1000);Path target=out.resolve(entry.getName().substring(name.length()+1)).normalize();assertTrue(target.startsWith(out));Files.createDirectories(target.getParent());Files.copy(zip,target);}}
        return out;
    }
    private List<Path> fasta(Path directory)throws IOException {try(var files=Files.list(directory)){return files.filter(p->p.toString().endsWith(".fa")||p.toString().endsWith(".fa.gz")).sorted().toList();}}
    @ParameterizedTest @MethodSource("positives") void everyCompleteOriginalHeaderLengthSequenceAndDna5ValueMatches(String name)throws Exception {
        Path fixture=extract(name);var population=Malt052FastaIndexAudit.read(fasta(fixture),LIMITS);
        List<String> rows=Files.readAllLines(fixture.resolve("source/references.tsv"),StandardCharsets.UTF_8);assertEquals(rows.size()-1,population.references().size());
        for(int i=0;i<population.references().size();i++) {var actual=population.references().get(i);String[] expected=rows.get(i+1).split("\t",-1);assertEquals(i,actual.ordinal());assertEquals(i,Integer.parseInt(expected[0]));assertEquals(expected[1],Base64.getEncoder().encodeToString(actual.header().getBytes(StandardCharsets.UTF_8)));assertEquals(Integer.parseInt(expected[2]),actual.bases());assertEquals(expected[3],actual.dna5Sha256());assertEquals(sha(Files.readAllBytes(fixture.resolve("source/sequence-"+i+".bin"))),actual.dna5Sha256());assertArrayEquals(Files.readAllBytes(fixture.resolve("source/header-"+i+".bin")),actual.header().getBytes(StandardCharsets.UTF_8));}
        String[] counts=Files.readAllLines(fixture.resolve("source/counts.tsv")).get(1).split("\t");assertEquals(Integer.parseInt(counts[0]),population.references().size());assertEquals(Long.parseLong(counts[1]),population.bases());
        var lookup=Files.readAllLines(fixture.resolve("source/dna5.tsv"));assertEquals(128,lookup.size());for(int i=0;i<127;i++){String[] fields=lookup.get(i+1).split("\t");assertEquals(i,Integer.parseInt(fields[0]));assertEquals(Integer.parseInt(fields[1]),(int)Malt052FastaIndexAudit.sourceDna5(i));}
    }
    @ParameterizedTest @MethodSource("refusals") void sourceZeroPopulationErrorsAndAmbiguousAccessionsNeverAdmitASubset(String name)throws Exception {
        Path fixture=extract(name);var error=assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(fasta(fixture),LIMITS));assertTrue(error.getMessage().contains("Required action:"));assertTrue(error.getMessage().contains("No header, sequence, prefix or reference population is silently repaired or dropped"));
    }
    @Test void completePopulationCapacitiesAndDuplicateFilesRefuseRatherThanTruncate()throws Exception {
        Path fixture=extract("lf-wrapped");var inputs=fasta(fixture);
        assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(inputs,new Malt052FastaIndexAudit.Limits(1000000,100,1,1000000,100000)));
        assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(inputs,new Malt052FastaIndexAudit.Limits(1000000,100,100,19,100000)));
        assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(inputs,new Malt052FastaIndexAudit.Limits(1000000,100,100,1000000,4)));
        assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(List.of(inputs.get(0),inputs.get(0)),LIMITS));
    }
    @Test void truncatedCompressionAndMislabelledCompressionNeverAdmitDecodedPrefixes()throws Exception {
        Path fixture=extract("gzip-wrapped"),original=fasta(fixture).get(0);byte[] complete=Files.readAllBytes(original);Path truncated=root.resolve("truncated.fa.gz"),mislabelled=root.resolve("compressed.fa");Files.write(truncated,Arrays.copyOf(complete,complete.length-1));Files.write(mislabelled,complete);
        assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(List.of(truncated),LIMITS));assertThrows(IOException.class,()->Malt052FastaIndexAudit.read(List.of(mislabelled),LIMITS));assertArrayEquals(complete,Files.readAllBytes(original));
    }
    @Test void everyBinaryReferenceAndOffsetIsAuditedIncludingTheFinalRecord()throws Exception {
        Path fixture=extract("lf-wrapped"),index=Files.createDirectory(root.resolve("index"));var originals=fasta(fixture);var offsets=new ByteArrayOutputStream();var database=new ByteArrayOutputStream();long position=0;
        try(var idx=new DataOutputStream(offsets);var db=new DataOutputStream(database)){for(int i=0;i<2;i++){byte[] sequence=Files.readAllBytes(fixture.resolve("source/sequence-"+i+".bin")),header=Files.readAllBytes(fixture.resolve("source/header-"+i+".bin"));idx.writeLong(position);db.writeInt(sequence.length);db.write(sequence);db.writeInt(header.length);db.write(header);position+=8L+sequence.length+header.length;}}
        Files.write(index.resolve("ref.idx"),offsets.toByteArray());Files.write(index.resolve("ref.db"),database.toByteArray());Files.writeString(index.resolve("ref.inf"),"sequences\t2\nletters\t20\n");
        // Uninterpreted seed members exercise inventory binding only, not seed-table validity.
        for(String member:List.of("index0.idx","table0.idx","table0.db","taxonomy.idx","taxonomy.tre","taxonomy.map"))Files.writeString(index.resolve(member),"uninterpreted seed inventory fixture");
        assertEquals(2,Malt052FastaIndexAudit.audit(originals,index,LIMITS).get("references"));byte[] data=Files.readAllBytes(index.resolve("ref.db"));data[data.length-1]^=1;Files.write(index.resolve("ref.db"),data);assertThrows(IOException.class,()->Malt052FastaIndexAudit.audit(originals,index,LIMITS));
    }
}
