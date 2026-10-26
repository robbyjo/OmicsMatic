/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemeDnaMotifsTest {
    @TempDir Path root;
    private static final String HEADER="MEME version 4\nALPHABET= ACGT\nstrands: +\nBackground letter frequencies\nA .1 C .2 G .3 T .4\n\n";
    private Path file(String text) throws IOException { Path p=root.resolve("motifs.meme");Files.writeString(p,text);return p; }
    private static String motif(String id,String rows) { return "MOTIF "+id+" alt\nletter-probability matrix: alength= 4 w= 1\n"+rows+"\n"; }

    @Test void orderedMotifsDefaultsAndBackgroundAreDefensiveAndDiscoveryStrandsAreMetadata() throws Exception {
        var doc=MemeDnaMotifs.read(file(HEADER+motif("M1",".7 .1 .1 .1")+"URL https://example.org/M1\n"+motif("M2",".1 .2 .3 .4")),false);
        assertEquals(2,doc.motifs().size());assertEquals("+",doc.declaredStrands());assertEquals(20,doc.motifs().get(0).sites());assertEquals("alt",doc.motifs().get(0).alternateId());
        doc.background()[0]=0;doc.motifs().get(0).frequencies()[0][0]=0;assertEquals(.1,doc.background()[0]);assertEquals(.7,doc.motifs().get(0).frequencies()[0][0]);assertThrows(UnsupportedOperationException.class,()->doc.motifs().clear());
    }

    @Test void roundedRowsRequireAnExplicitNormalizationChoiceAndChangesAreCounted() throws Exception {
        Path p=file(HEADER+motif("rounded",".333333 .333333 .333333 0"));assertThrows(IOException.class,()->MemeDnaMotifs.read(p,false));
        var doc=MemeDnaMotifs.read(p,true);assertEquals(1,doc.motifs().get(0).normalizedRows());assertEquals(1.0/3,doc.motifs().get(0).frequencies()[0][0]);
    }

    @Test void duplicateMotifsCountsLogOddsCustomAlphabetAndMissingWidthAreRejected() throws Exception {
        String valid=HEADER+motif("ok",".7 .1 .1 .1");
        for(String text:new String[]{valid+motif("ok",".7 .1 .1 .1"),valid.replace("ACGT","ACGU"),valid.replace("w= 1",""),valid.replace(".7 .1 .1 .1","7 1 1 1"),valid.replace("letter-probability","log-odds"),valid.replace(".7 .1 .1 .1","NaN .1 .1 .1"),valid.replace("w= 1","w= 513"),"<html>"+valid})
            assertThrows(IOException.class,()->MemeDnaMotifs.read(file(text),true));
    }

    @Test void zeroBackgroundEmptyMatrixInvalidSitesAndExtraSectionsFail() throws Exception {
        for(String text:new String[]{HEADER+motif("ok","0 0 0 0"),HEADER.replace("A .1","A 0")+motif("ok",".7 .1 .1 .1"),HEADER+motif("ok",".7 .1 .1 .1").replace("w= 1","w= 1 nsites= 0"),HEADER+motif("ok",".7 .1 .1 .1")+"arbitrary extra section\n"})
            assertThrows(IOException.class,()->MemeDnaMotifs.read(file(text),true));
    }

    @Test void duplicateAndUnknownMatrixFieldsAreNeverSilentlyResolved() throws Exception {
        String text=HEADER+motif("ok",".7 .1 .1 .1");
        for(String extra:new String[]{"w= 3","alength= 20","nsites= 2 nsites= 20","unknown= yes","unkeyed text","E= NaN","E= -1"})
            assertThrows(IOException.class,()->MemeDnaMotifs.read(file(text.replace("w= 1","w= 1 "+extra)),false));
    }
}
