/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignacMatrixIOTest {
    @TempDir Path directory;
    private SignacMatrixIO.Intake read(String text)throws IOException{
        Path m=directory.resolve("matrix.mtx"),f=directory.resolve("features.tsv"),c=directory.resolve("cells.tsv");
        Files.writeString(m,text);Files.writeString(f,"peak-A\npeak-B\npeak-C\n");Files.writeString(c,"cell-A\ncell-B\n");
        return SignacMatrixIO.readCounts(m,f,c,SignacMatrixIO.Limits.defaults(),()->false);
    }
    @Test void unsortedDuplicatesSumExactlyAndStoredZerosRemainStored()throws Exception{
        var intake=read("%%MatrixMarket matrix coordinate integer general\n% comment\n3 2 5\n3 2 0\n1 1 2\n3 1 4\n1 1 3\n2 2 1\n");
        assertEquals(5,intake.inputEntries());assertEquals(1,intake.duplicateCoordinates());assertEquals(1,intake.explicitZeroEntries());
        assertArrayEquals(new int[]{0,2,4},intake.counts().columnPointers());assertArrayEquals(new int[]{0,2,1,2},intake.counts().rowIndices());assertArrayEquals(new double[]{5,4,1,0},intake.counts().values());
        assertArrayEquals(new long[]{5,1,4},intake.counts().featureTotals());assertArrayEquals(new long[]{9,1},intake.counts().cellTotals());
    }
    @Test void selectedReorderingPreservesPairedSparseValuesAndExactIdentities()throws Exception{
        var input=read("%%MatrixMarket matrix coordinate integer general\n3 2 3\n1 1 1\n3 1 4\n2 2 2\n").counts();
        var normalized=SignacNormalization.runTfidf(input,SignacNormalization.Method.RAW_COUNT_IDF,1,null);
        var selected=SignacMatrixIO.select(normalized.matrix(),List.of("peak-C","peak-A"),()->false);
        assertEquals(List.of("peak-C","peak-A"),selected.features());assertArrayEquals(new int[]{0,2,2},selected.pointers());assertArrayEquals(new int[]{0,1},selected.rows());assertArrayEquals(new double[]{2,2},selected.values());
        Path m=directory.resolve("new.mtx"),f=directory.resolve("new.features"),c=directory.resolve("new.cells");SignacMatrixIO.write(selected,m,f,c,()->false);
        assertTrue(Files.readString(m).endsWith("1 1 2.0\n2 1 2.0\n"));assertEquals("peak-C\npeak-A\n",Files.readString(f));assertEquals("cell-A\ncell-B\n",Files.readString(c));
        selected.pointers()[0]=90;selected.rows()[0]=90;selected.values()[0]=90;assertArrayEquals(new int[]{0,2,2},selected.pointers());assertArrayEquals(new int[]{0,1},selected.rows());assertArrayEquals(new double[]{2,2},selected.values());
        assertThrows(IOException.class,()->SignacMatrixIO.write(selected,m,directory.resolve("unused-f"),directory.resolve("unused-c"),()->false));assertFalse(Files.exists(directory.resolve("unused-f")));
    }
    @ParameterizedTest @ValueSource(strings={"0 1 1","1 0 1","4 1 1","1 3 1","1 1 -1","1 1 0.5","1 1 1e3","1 1 NaN","1 1 9007199254740992","1 1","1 1 1 extra"})void invalidCoordinateOrCountFails(String entry){
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 1\n"+entry+"\n"));
    }
    @Test void realHeaderAndDeclaredEntryDisagreementsFail(){
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate real general\n3 2 1\n1 1 1\n"));
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 2\n1 1 1\n"));
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 0\n1 1 1\n"));
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n2 2 0\n"));
    }
    @Test void exactDomainIncludesDuplicateFeatureAndCellTotals(){
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 2\n1 1 9007199254740991\n1 1 1\n"));
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 2\n1 1 9007199254740991\n2 1 1\n"));
        assertThrows(IOException.class,()->read("%%MatrixMarket matrix coordinate integer general\n3 2 2\n1 1 9007199254740991\n1 2 1\n"));
    }
    @Test void invalidUtf8IdentityDuplicatesLineAndByteBoundsFail()throws Exception{
        read("%%MatrixMarket matrix coordinate integer general\n3 2 0\n");Path m=directory.resolve("matrix.mtx"),f=directory.resolve("features.tsv"),c=directory.resolve("cells.tsv");
        Files.write(f,new byte[]{(byte)0xff});assertThrows(IOException.class,()->SignacMatrixIO.readCounts(m,f,c,SignacMatrixIO.Limits.defaults(),()->false));
        Files.writeString(f,"peak-A\npeak-A\npeak-C\n");assertThrows(IOException.class,()->SignacMatrixIO.readCounts(m,f,c,SignacMatrixIO.Limits.defaults(),()->false));
        Files.writeString(f,"x".repeat(4097)+"\npeak-B\npeak-C\n");assertThrows(IOException.class,()->SignacMatrixIO.readCounts(m,f,c,SignacMatrixIO.Limits.defaults(),()->false));
        Files.writeString(f,"peak-A\npeak-B\npeak-C\n");assertThrows(IOException.class,()->SignacMatrixIO.readCounts(m,f,c,new SignacMatrixIO.Limits(3,2,5,10,128),()->false));
        assertThrows(IOException.class,()->SignacMatrixIO.readCounts(m,f,c,new SignacMatrixIO.Limits(3,2,5,1000,32),()->false));
    }
    @Test void emptySelectionUnknownSelectionAndNonfiniteSparseOutputAreRejected()throws Exception{
        var normalized=SignacNormalization.runTfidf(read("%%MatrixMarket matrix coordinate integer general\n3 2 1\n1 1 1\n").counts(),SignacNormalization.Method.RAW_COUNT_IDF,1,null);
        assertThrows(IOException.class,()->SignacMatrixIO.select(normalized.matrix(),List.of(),()->false));
        assertThrows(IOException.class,()->SignacMatrixIO.select(normalized.matrix(),List.of("unknown"),()->false));
        assertThrows(IOException.class,()->SignacMatrixIO.select(normalized.matrix(),List.of("peak-A","peak-A"),()->false));
        assertThrows(IllegalArgumentException.class,()->new SignacMatrixIO.Selected(List.of("f"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{Double.POSITIVE_INFINITY}));
        assertThrows(IllegalArgumentException.class,()->new SignacMatrixIO.Selected(List.of("f"),List.of("c"),new int[]{0,1},new int[]{1},new double[]{1}));
    }
    @Test void immediateCancellationDoesNotReadOrWriteCallerFiles(){
        assertThrows(CancellationException.class,()->SignacMatrixIO.readCounts(directory.resolve("missing"),directory.resolve("f"),directory.resolve("c"),SignacMatrixIO.Limits.defaults(),()->true));
        assertFalse(Files.exists(directory.resolve("missing")));
    }
}
