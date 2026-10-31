/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScviMatrixTest {
    @TempDir Path root;
    private int serial;
    private static final ScviMatrix.Limits LIMITS=new ScviMatrix.Limits(1_048_576,4_194_304,16_777_216,16_777_216,128,1000,1000,10000,2,4096);
    private ScviMatrix.Request request(String rows,ScviMatrix.Kind kind,ScviMatrix.Precision precision,ScviMatrix.Duplicates duplicates)throws Exception {
        Path dir=Files.createDirectory(root.resolve("source-"+serial++));Files.writeString(dir.resolve("matrix"),"%%MatrixMarket matrix coordinate real general\n"+rows);Files.writeString(dir.resolve("genes"),"β_GENE\ng2\ng3\n");Files.writeString(dir.resolve("cells"),"cell_日本\nc2\n");
        return new ScviMatrix.Request(new ScviText.Source(dir.resolve("matrix"),ScviText.Encoding.PLAIN_UTF8),new ScviText.Source(dir.resolve("genes"),ScviText.Encoding.PLAIN_UTF8),new ScviText.Source(dir.resolve("cells"),ScviText.Encoding.PLAIN_UTF8),kind,precision,duplicates,LIMITS);
    }
    private ScviMatrix.Prepared prepare(ScviMatrix.Request r)throws Exception{return ScviMatrix.prepare(r,root.resolve("prepared-"+serial++),()->{});}
    private ScviMatrix.Request counts(String rows)throws Exception{return request(rows,ScviMatrix.Kind.RAW_COUNTS,ScviMatrix.Precision.EXACT_FLOAT32,ScviMatrix.Duplicates.SUM_SOURCE_ORDER);}
    private ScviMatrix.Request withLimits(ScviMatrix.Request r,ScviMatrix.Limits l){return new ScviMatrix.Request(r.matrix(),r.genes(),r.cells(),r.kind(),r.precision(),r.duplicates(),l);}
    @Test void diskSpillingSumsRawDuplicatesBeforeFloat32AndPreservesAllIds()throws Exception {
        var r=counts("3 2 5\n3 2 7\n1 1 2\n1 1 3\n2 1 0\n1 2 1\n");var p=prepare(r);
        assertEquals(List.of("β_GENE","g2","g3"),p.genes());assertEquals(List.of("cell_日本","c2"),p.cells());assertEquals(5,p.summary().inputEntries());assertEquals(3,p.summary().canonicalEntries());assertEquals(1,p.summary().duplicateEntries());assertEquals(1,p.summary().zeroInputEntries());
        assertTrue(Files.readString(p.directory().resolve("matrix.mtx")).endsWith("1 1 5\n1 2 1\n3 2 7\n"));p.verify(()->{});
        assertTrue(Files.readString(p.directory().resolve("admission.json")).contains("PREPARED_MATRIX_ONLY"));assertFalse(Files.exists(p.directory().resolve("COMPLETED.json")));
    }
    @Test void sourceOrderSignedSummationSurvivesSortingAndKeepsZeroCells()throws Exception {
        var r=request("3 2 3\n1 1 1e16\n1 1 -1e16\n1 1 1\n",ScviMatrix.Kind.SIGNED_EXPRESSION,ScviMatrix.Precision.EXACT_FLOAT32,ScviMatrix.Duplicates.SUM_SOURCE_ORDER);var p=prepare(r);
        assertEquals(2,p.summary().duplicateEntries());assertEquals(1,p.summary().canonicalEntries());assertTrue(Files.readString(p.directory().resolve("matrix.mtx")).endsWith("1 1 1.0\n"));assertEquals(2,p.cells().size());
        assertTrue(Files.readString(p.directory().resolve("cell-summary.tsv")).contains("c2\t0.0\t0.0\t0"));
    }
    @Test void reviewedFloat32RoundingAndUnderflowAreCompleteNamedCoordinateAudits()throws Exception {
        var r=request("3 2 3\n1 1 0.1\n2 1 -0.2\n3 2 1e-50\n",ScviMatrix.Kind.SIGNED_EXPRESSION,ScviMatrix.Precision.REVIEWED_FLOAT32_ROUNDING,ScviMatrix.Duplicates.REJECT);var p=prepare(r);
        assertEquals(3,p.summary().roundedCoordinates());assertEquals(1,p.summary().underflowCoordinates());assertEquals(2,p.summary().canonicalEntries());var audit=Files.readAllLines(p.directory().resolve("float32-conversion.tsv"));assertEquals(4,audit.size());assertTrue(audit.get(3).startsWith("2\t3\t"));assertTrue(audit.get(3).endsWith("UNDERFLOW_TO_ZERO"));
        assertThrows(IOException.class,()->prepare(new ScviMatrix.Request(r.matrix(),r.genes(),r.cells(),r.kind(),ScviMatrix.Precision.EXACT_FLOAT32,r.duplicates(),r.limits())));
    }
    @Test void rawCountsRejectFractionalLexemesAndUnrepresentableFinalValues()throws Exception {
        for(String value:List.of("1.0000000000000001","-1","NaN","Infinity","9007199254740992","16777217"))assertThrows(IOException.class,()->prepare(counts("3 2 2\n1 1 "+value+"\n1 2 1\n")),value);
        var p=prepare(counts("3 2 3\n1 1 16777217\n1 1 1\n1 2 1\n"));assertTrue(Files.readString(p.directory().resolve("matrix.mtx")).contains("1 1 16777218"));
    }
    @Test void duplicateRejectionIncludesExplicitZeroCoordinates()throws Exception {
        var r=counts("3 2 3\n1 1 0\n1 1 1\n1 2 1\n");assertThrows(IOException.class,()->prepare(new ScviMatrix.Request(r.matrix(),r.genes(),r.cells(),r.kind(),r.precision(),ScviMatrix.Duplicates.REJECT,r.limits())));
    }
    @Test void noCellOrGeneIsFilteredToRepairRawZeroLibrariesOrShapeErrors()throws Exception {
        var failure=assertThrows(IOException.class,()->prepare(counts("3 2 1\n1 1 1\n")));assertTrue(failure.getMessage().contains("c2"));
        for(String rows:List.of("2 2 2\n1 1 1\n1 2 1\n","3 2 1\n1 1 1\n1 2 1\n","3 2 3\n1 1 1\n1 2 1\n","3 2 2\n4 1 1\n1 2 1\n"))assertThrows(IOException.class,()->prepare(counts(rows)));
    }
    @Test void strictGzipConsumesEveryMemberCrcAndPhysicalTrailingByte()throws Exception {
        var r=counts("3 2 2\n1 1 1\n1 2 1\n");byte[] raw=Files.readAllBytes(r.matrix().path());var zipped=new ByteArrayOutputStream();int split=raw.length/2;
        try(var out=new GZIPOutputStream(zipped)){out.write(raw,0,split);}try(var out=new GZIPOutputStream(zipped)){out.write(raw,split,raw.length-split);}Files.write(r.matrix().path(),zipped.toByteArray());
        var gz=new ScviMatrix.Request(new ScviText.Source(r.matrix().path(),ScviText.Encoding.GZIP_UTF8),r.genes(),r.cells(),r.kind(),r.precision(),r.duplicates(),r.limits());assertEquals(2,prepare(gz).summary().canonicalEntries());
        Files.write(r.matrix().path(),new byte[]{1},StandardOpenOption.APPEND);assertThrows(IOException.class,()->prepare(gz));byte[] corrupt=zipped.toByteArray();corrupt[corrupt.length-8]^=1;Files.write(r.matrix().path(),corrupt);assertThrows(IOException.class,()->prepare(gz));
    }
    @Test void invalidUtf8DuplicateIdsAndOversizedLinesFailBeforeModelUse()throws Exception {
        var r=counts("3 2 2\n1 1 1\n1 2 1\n");Files.write(r.genes().path(),new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->prepare(r));Files.writeString(r.genes().path(),"g\ng\ng3\n");assertThrows(IOException.class,()->prepare(r));Files.writeString(r.genes().path(),"g".repeat(5000)+"\ng2\ng3\n");assertThrows(IOException.class,()->prepare(r));
    }
    @Test void completeCapacityAndCancellationNeverBecomeSuccessfulInputAdmission()throws Exception {
        var r=counts("3 2 2\n1 1 1\n1 2 1\n");var l=r.limits();
        assertThrows(IOException.class,()->prepare(withLimits(r,new ScviMatrix.Limits(l.inputBytes(),l.decodedBytes(),1000,l.retainedBytes(),l.files(),l.genes(),l.cells(),l.entries(),l.sortRecords(),l.lineCharacters()))));
        assertThrows(IOException.class,()->prepare(withLimits(r,new ScviMatrix.Limits(l.inputBytes(),10,l.workspaceBytes(),l.retainedBytes(),l.files(),l.genes(),l.cells(),l.entries(),l.sortRecords(),l.lineCharacters()))));
        assertThrows(CancellationException.class,()->ScviMatrix.prepare(r,root.resolve("cancelled"),()->{throw new CancellationException("stop");}));assertFalse(Files.exists(root.resolve("cancelled/admission.json")));
    }
    @Test void byteMutationsRestoredMtimeAndExtraEmptyDirectoriesCannotRetainAdmission()throws Exception {
        var p=prepare(counts("3 2 2\n1 1 1\n1 2 1\n"));Path file=p.directory().resolve("genes.tsv");var time=Files.getLastModifiedTime(file);byte[] raw=Files.readAllBytes(file);raw[raw.length-2]='x';Files.write(file,raw);Files.setLastModifiedTime(file,time);assertThrows(IOException.class,()->p.verify(()->{}));
        var q=prepare(counts("3 2 2\n1 1 1\n1 2 1\n"));Files.createDirectory(q.directory().resolve("unexpected"));assertThrows(IOException.class,()->q.verify(()->{}));
        var s=prepare(counts("3 2 2\n1 1 1\n1 2 1\n"));Files.writeString(s.request().genes().path(),"changed\ng2\ng3\n");assertThrows(IOException.class,()->s.verify(()->{}));
    }
}
