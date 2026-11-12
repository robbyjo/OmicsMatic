/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaltIndexTaxonomyTest {
    @TempDir Path temporary;
    private static final MaltIndexTaxonomy.Limits LIMITS=new MaltIndexTaxonomy.Limits(1000000,100,100000,1000,10000,new MaltTaxonomy.Limits(100000,1000,100));
    /** Independent binary fixture: two literal references, three seed files and an unclassified map. */
    private Path source(String name)throws Exception {
        Path dir=Files.createDirectory(temporary.resolve(name));String[] headers={">AB.1 first reference",">XY.2 second reference"};String[] sequences={"ACGTN","TACG"};long offset=0;
        try(var db=new DataOutputStream(Files.newOutputStream(dir.resolve("ref.db")));var idx=new DataOutputStream(Files.newOutputStream(dir.resolve("ref.idx")));var taxa=new DataOutputStream(Files.newOutputStream(dir.resolve("taxonomy.idx")))) {
            taxa.writeBytes("MATaxonomyV1.1");taxa.writeInt(2);taxa.writeInt(0);taxa.writeInt(0);
            for(int i=0;i<2;i++){byte[] sequence=sequences[i].getBytes(StandardCharsets.US_ASCII),header=headers[i].getBytes(StandardCharsets.UTF_8);idx.writeLong(offset);db.writeInt(sequence.length);db.write(sequence);db.writeInt(header.length);db.write(header);offset+=8L+sequence.length+header.length;}
        }
        Files.writeString(dir.resolve("ref.inf"),"sequences\t2\nletters\t9\n");Files.writeString(dir.resolve("taxonomy.tre"),"((10,20)2)1;\n");Files.writeString(dir.resolve("taxonomy.map"),"1\troot\n10\talpha\n20\tbeta\n");
        for(String file:List.of("index0.idx","table0.idx","table0.db"))Files.writeString(dir.resolve(file),"Opaque seed member "+file);return dir;
    }
    private Path map(String name,String contents)throws Exception {Path path=temporary.resolve(name);Files.writeString(path,contents);return path;}
    @Test void everySequenceOrdinalAndSeedByteSurvivesTheNewClassifiedIndex()throws Exception {
        Path source=source("original"),mapping=map("mapping.tsv","AB.1\t10\nXY.2\t20\n"),derived=temporary.resolve("derived");var before=MaltIndexTaxonomy.inventory(source,LIMITS.fileBytes());
        var result=MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,derived,LIMITS);
        assertEquals(2,result.references());assertEquals(9,result.referenceBases());assertEquals(Map.of("AB.1",10,"XY.2",20),result.taxonByAccession());assertEquals(before,MaltIndexTaxonomy.inventory(source,LIMITS.fileBytes()));assertEquals(result,MaltIndexTaxonomy.audit(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,derived,LIMITS));
        try(var taxa=new DataInputStream(Files.newInputStream(derived.resolve("taxonomy.idx")))){assertEquals("MATaxonomyV1.1",new String(taxa.readNBytes(14),StandardCharsets.US_ASCII));assertEquals(2,taxa.readInt());assertEquals(10,taxa.readInt());assertEquals(20,taxa.readInt());assertEquals(-1,taxa.read());}
        assertThrows(UnsupportedOperationException.class,()->result.taxonByAccession().clear());
    }
    @Test void accessionVersionsAreExplicitAndNeverGuessed()throws Exception {
        Path source=source("original");Path bare=map("bare.tsv","AB\t10\nXY\t20\n");var error=assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,bare,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,temporary.resolve("missing"),LIMITS));assertTrue(error.getMessage().contains("AB.1"));assertFalse(Files.exists(temporary.resolve("missing")));
        Path ncbi=temporary.resolve("ncbi.tsv.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(ncbi))){out.write("accession\taccession.version\ttaxid\tgi\nAB\tAB.1\t10\t0\nXY\tXY.2\t20\t0\nUNUSED\tUNUSED.1\t2\t0\n".getBytes(StandardCharsets.UTF_8));}
        var result=MaltIndexTaxonomy.derive(source,ncbi,MaltIndexTaxonomy.Format.NCBI_ACCESSION2TAXID,MaltIndexTaxonomy.Namespace.ACCESSION_VERSION,temporary.resolve("versioned"),LIMITS);assertEquals(3,result.mappingRows());
        assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,ncbi,MaltIndexTaxonomy.Format.NCBI_ACCESSION2TAXID,MaltIndexTaxonomy.Namespace.ACCESSION,temporary.resolve("wrong-namespace"),LIMITS));
    }
    @Test void conflictingMissingUnknownAndMalformedMappingsCannotProduceAnIndex()throws Exception {
        Path source=source("original");int ordinal=0;
        for(String text:List.of("AB.1\t10\nAB.1\t20\nXY.2\t20\n","AB.1\t10\n","AB.1\t999\nXY.2\t20\n","AB.1\t0\nXY.2\t20\n","AB.1\t10\textra\nXY.2\t20\n")) {
            Path mapping=map("bad"+ordinal+".tsv",text),out=temporary.resolve("bad"+ordinal++);var error=assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,out,LIMITS));assertTrue(error.getMessage().contains("Required action:"));assertFalse(Files.exists(out));
        }
        Path repeated=map("repeated.tsv","AB.1\t10\nAB.1\t10\nXY.2\t20\n");assertEquals(1,MaltIndexTaxonomy.derive(source,repeated,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,temporary.resolve("repeated"),LIMITS).repeatedSelectedRows());
    }
    @Test void existingAssignmentsAndIncompletePopulationLimitsRefuseBeforeDerivation()throws Exception {
        Path source=source("original"),mapping=map("mapping.tsv","AB.1\t10\nXY.2\t20\n");var limited=new MaltIndexTaxonomy.Limits(1000000,1,100000,1000,10000,LIMITS.taxonomy());assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,temporary.resolve("limited"),limited));
        try(var file=new RandomAccessFile(source.resolve("taxonomy.idx").toFile(),"rw")){file.seek(18);file.writeInt(10);}var error=assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,temporary.resolve("assigned"),LIMITS));assertTrue(error.getMessage().contains("never overwritten"));
    }
    @Test void savedAuditRefusesChangedSequenceTaxonomyAndSeedWithoutRepairingThem()throws Exception {
        Path source=source("original"),mapping=map("mapping.tsv","AB.1\t10\nXY.2\t20\n");
        for(String member:List.of("ref.db","taxonomy.idx","table0.db")) {
            Path derived=temporary.resolve(member.replace('.','-'));MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,derived,LIMITS);byte[] bytes=Files.readAllBytes(derived.resolve(member));int at=member.equals("ref.db")?4:bytes.length-1;bytes[at]^=1;Files.write(derived.resolve(member),bytes);
            assertThrows(IOException.class,()->MaltIndexTaxonomy.audit(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,derived,LIMITS));assertArrayEquals(bytes,Files.readAllBytes(derived.resolve(member)));
        }
    }
    @Test void nestedDestinationRefusesBeforeCreatingAnythingInsideTheOriginalIndex()throws Exception {
        Path source=source("original"),mapping=map("mapping.tsv","AB.1\t10\nXY.2\t20\n"),nested=source.resolve("nested");var before=MaltIndexTaxonomy.inventory(source,LIMITS.fileBytes());
        var error=assertThrows(IOException.class,()->MaltIndexTaxonomy.derive(source,mapping,MaltIndexTaxonomy.Format.TWO_COLUMN,MaltIndexTaxonomy.Namespace.LITERAL,nested,LIMITS));assertTrue(error.getMessage().contains("must not overlap"));assertFalse(Files.exists(nested));assertEquals(before,MaltIndexTaxonomy.inventory(source,LIMITS.fileBytes()));
    }
}
