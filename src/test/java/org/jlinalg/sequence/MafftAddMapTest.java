/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MafftAddMapTest {
    private static final String DESCRIPTION="# letter, position in the original sequence, position in the reference alignment\n";
    private static final String MAP=">T000000003\n"+DESCRIPTION+"A, 1, 1\nC, 2, 2\nC, 3, 3\nD, 4, 4\nP, 5, -\nQ, 6, -\nR, 7, -\nS, 8, -\nT, 9, -\nE, 10, 5\nF, 11, 6\nG, 12, 7\n";
    private static final Map<String,String> NAMES=Map.of("T000000001","backbone a","T000000002","backbone b","T000000003","added α");
    @TempDir Path temporary;
    private static MafftSequences.Data data(String text,MafftSequences.Symbols symbols,MafftSequences.Layout layout)throws IOException {
        return MafftSequences.readFasta(new StringReader(text),MafftSequences.Alphabet.PROTEIN,symbols,MafftSequences.Headers.WHOLE_HEADER,layout,MafftSequences.Gaps.ALLOW,MafftSequences.Limits.defaults(),()->{});
    }
    private static MafftSequences.Data added()throws IOException {
        return data(">added α\nAC-CDPQRSTEFG\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.SEQUENCES);
    }
    private static MafftSequences.Data aligned()throws IOException {
        return data(">T000000001\nACCDEFGHI-K\n>T000000002\nA--DEFGHI-K\n>T000000003\nACCDEFG----\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
    }
    private static MafftAddMap.Audit read(String text)throws IOException {
        return MafftAddMap.read(new StringReader(text),added(),aligned(),NAMES,MafftAddMap.Limits.defaults(),m->{},()->{});
    }
    @Test void everyOriginalResidueAndDeletionIsPreservedWithBothCoordinateSystems()throws Exception {
        var rows=new ArrayList<MafftAddMap.Mapping>();
        var audit=MafftAddMap.read(new StringReader(MAP),added(),aligned(),NAMES,MafftAddMap.Limits.defaults(),rows::add,()->{});
        assertEquals(1,audit.records());assertEquals(12,audit.residues());assertEquals(7,audit.mappedResidues());assertEquals(5,audit.deletedResidues());
        assertEquals(12,rows.size());assertEquals(3,rows.get(2).originalResiduePosition());assertEquals(4,rows.get(2).inputCharacterColumn());
        assertEquals(-1,rows.get(4).alignedColumn());assertEquals('P',rows.get(4).originalResidue());
        assertEquals(Map.of("added α",5L),audit.deletionsByTaxon());assertThrows(UnsupportedOperationException.class,()->audit.deletionsByTaxon().clear());
        var original=data(">backbone a\nACCDEFGHI-K\n>backbone b\nA--DEFGHI-K\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
        assertTrue(MafftSequences.auditBackbone(original,aligned(),NAMES,MafftSequences.Backbone.KEEP_LENGTH).gapOnlyColumnIdentityInferred());
        var all=MafftSequences.combine(original,added(),MafftSequences.Limits.defaults());
        assertEquals(3,MafftSequences.checkIdentities(all,aligned(),NAMES,MafftSequences.OutputOrder.INPUT).size());
        assertThrows(IllegalArgumentException.class,()->MafftSequences.audit(all,aligned(),NAMES,MafftSequences.OutputOrder.INPUT));
    }
    @Test void lateTruncationAndExtraOrDuplicateCoordinatesNeverReturnAPartialAudit()throws Exception {
        for(String text:List.of(MAP.replace("G, 12, 7\n",""),MAP+"G, 13, 8\n",MAP.replace("F, 11, 6","F, 10, 6"),
                MAP.replace("P, 5, -","P, 5, 0"),MAP.replace("P, 5, -","P, 5, 2147483648"),
                MAP.replace("F, 11, 6","F, 11, 5"),MAP.replace("A, 1, 1","A, 1, 99"),MAP+"garbage\n",MAP+MAP))
            assertThrows(IOException.class,()->read(text),text);
    }
    @Test void mismatchingLettersUnmappedOutputAndMissingFamiliesAreRefused()throws Exception {
        assertThrows(IOException.class,()->read(MAP.replace("C, 3, 3","A, 3, 3")));
        assertThrows(IOException.class,()->read(MAP.replace("E, 10, 5","E, 10, -")));
        assertThrows(IOException.class,()->read(MAP.replace("T000000003","T000000004")));
        assertThrows(IOException.class,()->read(MAP.replace(DESCRIPTION,"")));
        var two=data(">added α\nACCDPQRSTEFG\n>other\nAC\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.SEQUENCES);
        var output=data(">x\nACCDEFG----\n>y\nAC---------\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
        assertThrows(IOException.class,()->MafftAddMap.read(new StringReader(MAP.replace("T000000003","x")),two,output,Map.of("x","added α","y","other"),MafftAddMap.Limits.defaults(),m->{},()->{}));
    }
    @Test void commaAndHashResiduesAreParsedFromTheRightAndCaseChangesRemainCounted()throws Exception {
        var input=data(">original\n,a#\n",MafftSequences.Symbols.ANY_PRINTABLE,MafftSequences.Layout.SEQUENCES);
        var output=data(">opaque\n-,A#\n",MafftSequences.Symbols.ANY_PRINTABLE,MafftSequences.Layout.ALIGNMENT);
        String map=">opaque\n"+DESCRIPTION+",, 1, 2\nA, 2, 3\n#, 3, 4\n";
        var audit=MafftAddMap.read(new StringReader(map),input,output,Map.of("opaque","original"),MafftAddMap.Limits.defaults(),m->{},()->{});
        assertEquals(3,audit.mappedResidues());assertEquals(1,audit.sourcePrintedCaseChanges());
    }
    @Test void everyCompleteMapCapacityRefusesTheWholeFamily()throws Exception {
        for(var limits:List.of(new MafftAddMap.Limits(MAP.length()-1,1000,100,10),new MafftAddMap.Limits(10000,10,100,10),new MafftAddMap.Limits(10000,1000,11,10)))
            assertThrows(IOException.class,()->MafftAddMap.read(new StringReader(MAP),added(),aligned(),NAMES,limits,m->{},()->{}));
    }
    @Test void utf8ReadAndCloseFailuresInvalidateEvenTheLastRow()throws Exception {
        byte[] valid=MAP.getBytes(java.nio.charset.StandardCharsets.UTF_8);Path path=temporary.resolve("map");byte[] broken=Arrays.copyOf(valid,valid.length+1);broken[valid.length]=(byte)0xc3;Files.write(path,broken);
        assertThrows(IOException.class,()->MafftAddMap.read(path,added(),aligned(),NAMES,MafftAddMap.Limits.defaults(),m->{},()->{}));
        var closed=new AtomicBoolean();Reader reader=new FilterReader(new StringReader(MAP)){
            @Override public void close()throws IOException{closed.set(true);throw new IOException("injected close failure");}
        };
        assertThrows(IOException.class,()->MafftAddMap.read(reader,added(),aligned(),NAMES,MafftAddMap.Limits.defaults(),m->{},()->{}));assertTrue(closed.get());
    }
    @Test void callbacksAreExplicitlyProvisionalUntilTheCompleteMapCloses()throws Exception {
        var rows=new ArrayList<MafftAddMap.Mapping>();
        assertThrows(IOException.class,()->MafftAddMap.read(new StringReader(MAP.replace("G, 12, 7\n","")),added(),aligned(),NAMES,MafftAddMap.Limits.defaults(),rows::add,()->{}));
        assertEquals(11,rows.size());
    }
    @Test void aGapOnlyAddedRowHasAnExplicitEmptyResidueFamily()throws Exception {
        var input=data(">new\n--\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.SEQUENCES);
        var output=data(">x\n---\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
        var audit=MafftAddMap.read(new StringReader(">x\n"+DESCRIPTION),input,output,Map.of("x","new"),MafftAddMap.Limits.defaults(),m->{fail("No residue exists");},()->{});
        assertEquals(1,audit.records());assertEquals(0,audit.residues());assertEquals(0,audit.deletedResidues());
    }
    @Test void mappingCallbacksCannotRebindLaterTaxa()throws Exception {
        var input=data(">alpha\nAC\n>beta\nDE\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.SEQUENCES);
        var output=data(">x\nA-C-\n>y\n-D-E\n",MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
        var aliases=new LinkedHashMap<String,String>();aliases.put("x","alpha");aliases.put("y","beta");
        var seen=new ArrayList<String>();
        var audit=MafftAddMap.read(new StringReader(">x\n"+DESCRIPTION+"A, 1, 1\nC, 2, 3\n>y\n"+DESCRIPTION+"D, 1, 2\nE, 2, 4\n"),
            input,output,aliases,MafftAddMap.Limits.defaults(),row->{seen.add(row.originalTaxon());aliases.put("x","beta");aliases.put("y","alpha");},()->{});
        assertEquals(2,audit.records());assertEquals(4,audit.mappedResidues());
        assertEquals(List.of("alpha","alpha","beta","beta"),seen);
    }
    @Test void anInvalidIdentityBindingStillClosesTheOwnedMapReader()throws Exception {
        var closed=new AtomicBoolean();Reader reader=new FilterReader(new StringReader(MAP)){
            @Override public void close()throws IOException{closed.set(true);super.close();}
        };
        assertThrows(IllegalArgumentException.class,()->MafftAddMap.read(reader,added(),aligned(),Map.of("wrong","added α"),MafftAddMap.Limits.defaults(),m->{fail("Invalid identities must not emit rows");},()->{}));
        assertTrue(closed.get());
    }
}
