/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class VelocytoNativeTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path root;
    private Path copy(String name)throws IOException {Path p=root.resolve(name);try(var in=getClass().getResourceAsStream("/org/jlinalg/singlecell/velocyto01717/"+name)){assertNotNull(in);Files.copy(in,p);}return p;}
    private Map<?,?> oracle(String name)throws IOException {try(var in=getClass().getResourceAsStream("/org/jlinalg/singlecell/velocyto01717/original-oracles.json")){var file=object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));for(Object row:list(file.get("cases"))){var m=object(row);if(m.get("name").equals(name))return m;}}throw new IOException("Unknown original case");}
    private VelocytoCountMatrices.Contract contract(Map<?,?> oracle)throws IOException {
        var hashes=new LinkedHashMap<String,String>();for(var e:object(oracle.get("dense_sha256")).entrySet())hashes.put(text(e.getKey()),text(e.getValue()));return new VelocytoCountMatrices.Contract(list(oracle.get("genes")).size(),list(oracle.get("cells")).size(),VelocytoCountMatrices.Counter.valueOf(text(oracle.get("counter"))),((Number)oracle.get("maximum_molecules_per_cell")).longValue(),hashes);
    }
    private final VelocytoCountMatrices.Limits matrixLimits=new VelocytoCountMatrices.Limits(100,100,10000,10000,1000000);
    @ParameterizedTest @ValueSource(strings={"original-cli-standard","original-cli-mask","original-cli-infer","original-cli-overflow","original-api-uint64-standard","original-api-uint64-overflow"})
    void completeOriginalLayerPopulationsAndExactExpressionTotals(String name)throws Exception {
        var o=oracle(name);Path p=copy(text(o.get("sparse_file")));var c=contract(o);
        if(Boolean.TRUE.equals(o.get("expected_source_overflow_refusal"))){assertThrows(IOException.class,()->VelocytoCountMatrices.audit(p,c,matrixLimits,()->{}));return;}
        var r=VelocytoCountMatrices.audit(p,c,matrixLimits,()->{});var nz=object(o.get("nonzero_entries"));var totals=object(o.get("layer_totals"));for(var e:r.layerTotals().entrySet())assertEquals(((Number)totals.get(e.getKey())).longValue(),e.getValue());for(var e:r.nonzeroEntries().entrySet())assertEquals(((Number)nz.get(e.getKey())).longValue(),e.getValue());Path output=VelocytoCountMatrices.writeMatrixMarket(p,r,root.resolve("matrices"),()->{});
        var expression=new long[r.genes()][r.cells()];var lines=Files.readAllLines(output.resolve("spliced_unspliced_total.mtx"));for(String line:lines.subList(3,lines.size())){String[] f=line.split(" ");expression[Integer.parseInt(f[0])-1][Integer.parseInt(f[1])-1]=Long.parseLong(f[2]);}
        var layers=object(o.get("layers"));var s=list(layers.get("spliced"));var u=list(layers.get("unspliced"));for(int g=0;g<r.genes();g++)for(int cell=0;cell<r.cells();cell++)assertEquals(((Number)list(s.get(g)).get(cell)).longValue()+((Number)list(u.get(g)).get(cell)).longValue(),expression[g][cell]);assertThrows(IOException.class,()->VelocytoCountMatrices.writeMatrixMarket(p,r,output,()->{}));
    }
    @Test void missingZeroPopulationTamperAndTruncationCannotPass()throws Exception {
        var o=oracle("original-cli-standard");var c=contract(o);Path p=copy(text(o.get("sparse_file")));byte[] bytes=Files.readAllBytes(p);Files.write(p,Arrays.copyOf(bytes,bytes.length-1));assertThrows(IOException.class,()->VelocytoCountMatrices.audit(p,c,matrixLimits,()->{}));bytes[8]^=1;Files.write(p,bytes);assertThrows(IOException.class,()->VelocytoCountMatrices.audit(p,c,matrixLimits,()->{}));Files.write(p,Arrays.copyOfRange(bytes,36,bytes.length));assertThrows(IOException.class,()->VelocytoCountMatrices.audit(p,c,matrixLimits,()->{}));
    }
    @Test void completeCapacityCancellationAndImmutableResults()throws Exception {
        var o=oracle("original-cli-standard");var c=contract(o);Path p=copy(text(o.get("sparse_file")));assertThrows(IOException.class,()->VelocytoCountMatrices.audit(p,c,new VelocytoCountMatrices.Limits(1,1,1,1,1),()->{}));assertThrows(java.util.concurrent.CancellationException.class,()->VelocytoCountMatrices.audit(p,c,matrixLimits,()->{throw new java.util.concurrent.CancellationException();}));var r=VelocytoCountMatrices.audit(p,c,matrixLimits,()->{});long[] totals=r.expressionTotalByCell();totals[0]=999;assertNotEquals(999,r.expressionTotalByCell()[0]);
    }
    @Test void changedAuditedSparseFileCannotBeExportedAsComplete()throws Exception {
        var o=oracle("original-cli-standard");var c=contract(o);Path p=copy(text(o.get("sparse_file")));var r=VelocytoCountMatrices.audit(p,c,matrixLimits,()->{});byte[] bytes=Files.readAllBytes(p);bytes[8]^=1;Files.write(p,bytes);assertThrows(IOException.class,()->VelocytoCountMatrices.writeMatrixMarket(p,r,root.resolve("changed"),()->{}));
    }
    private VelocytoBamPreparation.Settings settings(String tag,Set<String> exclude){return new VelocytoBamPreparation.Settings(tag,exclude,false,Set.of("AAAACCCC","CCCCGGGG","GGGGTTTT"),true);}
    private final VelocytoBamPreparation.Limits bamLimits=new VelocytoBamPreparation.Limits(100000,100000000,100,100000);
    @Test void originalCbUbDomainAndSourceMoleculeBoundAreAudited()throws Exception {
        var r=VelocytoBamPreparation.prepare(copy("standard.bam"),root.resolve("prepared.bam"),settings("UB",Set.of()),bamLimits,()->{});assertEquals(1108,r.inputRecords());assertEquals(r.inputRecords(),r.outputRecords());assertEquals(0,r.renamedTags());assertEquals(7,r.distinctMoleculeKeys());assertEquals(3,r.barcodes().get("AAAACCCC").distinctMoleculeKeys());assertEquals(1,r.skippedNonunique());assertTrue(r.taggedForOriginalPeek()>1000);
    }
    @Test void nativeBdRhapsodyPreparationDropsOnlyDeclaredAntibodyAlignments()throws Exception {
        var r=VelocytoBamPreparation.prepare(copy("bd-rhapsody.bam"),root.resolve("prepared.bam"),settings("MA",Set.of("AB_contig")),bamLimits,()->{});assertEquals(1109,r.inputRecords());assertEquals(1108,r.outputRecords());assertEquals(1,r.excludedRecords());assertEquals(1108,r.renamedTags());assertEquals(7,r.distinctMoleculeKeys());
        try(var reader=htsjdk.samtools.SamReaderFactory.makeDefault().open(root.resolve("prepared.bam"))){for(var read:reader){assertFalse(read.hasAttribute("MA"));assertTrue(read.hasAttribute("UB"));assertNotEquals("AB_contig",read.getReferenceName());}}
    }
    @Test void originalUint16OverflowRiskIsExplicitWithoutDiscardingRecords()throws Exception {
        var r=VelocytoBamPreparation.prepare(copy("overflow.bam"),root.resolve("prepared.bam"),settings("UB",Set.of()),bamLimits,()->{});assertEquals(65536,r.inputRecords());assertEquals(65536,r.outputRecords());assertEquals(65536,r.distinctMoleculeKeys());assertTrue(r.warnings().stream().anyMatch(x->x.startsWith("SOURCE_UINT16_OVERFLOW_RISK")));
    }
    @Test void barcodeCollisionsAndUmiConflictsAreRefused()throws Exception {
        Path original=copy("standard.bam");Path conflict=root.resolve("conflict.bam");try(var reader=htsjdk.samtools.SamReaderFactory.makeDefault().open(original);var writer=new htsjdk.samtools.SAMFileWriterFactory().makeBAMWriter(reader.getFileHeader(),true,conflict.toFile())){boolean first=true;for(var r:reader){if(first){r.setAttribute("MA","DIFFERENT");first=false;}writer.addAlignment(r);}}
        assertThrows(IOException.class,()->VelocytoBamPreparation.prepare(conflict,root.resolve("failed.bam"),settings("MA",Set.of()),bamLimits,()->{}));
        Path collision=root.resolve("collision.bam");try(var reader=htsjdk.samtools.SamReaderFactory.makeDefault().open(original);var writer=new htsjdk.samtools.SAMFileWriterFactory().makeBAMWriter(reader.getFileHeader(),true,collision.toFile())){boolean first=true;for(var r:reader){if(first){r.setAttribute("CB","AAAACCCC-2");first=false;}writer.addAlignment(r);}}
        assertThrows(IOException.class,()->VelocytoBamPreparation.prepare(collision,root.resolve("collision-failed.bam"),settings("UB",Set.of()),bamLimits,()->{}));
    }
    @Test void nativePreparationCapacityAndReuseRefuseWholeAttempt()throws Exception {
        Path original=copy("standard.bam"),out=root.resolve("prepared.bam");assertThrows(IOException.class,()->VelocytoBamPreparation.prepare(original,out,settings("UB",Set.of()),new VelocytoBamPreparation.Limits(1,100000000,100,100),()->{}));assertThrows(IOException.class,()->VelocytoBamPreparation.prepare(original,out,settings("UB",Set.of()),bamLimits,()->{}));assertEquals("MT",VelocytoBamPreparation.sourceChromosome("chrM"));assertEquals("KI270",VelocytoBamPreparation.sourceChromosome("chr1_KI270_random"));
    }
}
