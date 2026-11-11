/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.io.FilterReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhylogeneticAlignmentTest {
    private static final PhylogeneticAlignment.Limits LIMITS=PhylogeneticAlignment.Limits.defaults();
    @TempDir Path temporary;
    private static PhylogeneticAlignment fasta(String text,PhylogeneticAlignment.Kind kind) throws IOException {
        return PhylogeneticAlignment.readFasta(new StringReader(text),kind,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,LIMITS);
    }
    private static PhylogeneticAlignment phylip(String text,PhylogeneticAlignment.Layout layout,PhylogeneticAlignment.NameField names,boolean named) throws IOException {
        return PhylogeneticAlignment.readPhylip(new StringReader(text),PhylogeneticAlignment.Kind.DNA,layout,names,named,LIMITS);
    }
    @Test void fastaKeepsAllTaxaColumnsRawCaseAndWholeHeadersInSourceOrder() throws Exception {
        var value=fasta(" \n>  α's sample (one)  \r\naC gu\r\nTA\n>second|id\nACGUTA\n",PhylogeneticAlignment.Kind.DNA);
        assertEquals(6,value.columns());assertEquals(6,value.modelSites());assertEquals("aCguTA",value.sequences().get("α's sample (one)"));
        assertEquals("  α's sample (one)  ",value.rows().get(0).originalHeader());assertEquals("α's sample (one)",value.rows().get(0).taxon());assertEquals(2,value.rows().size());
    }
    @Test void firstFieldIsExplicitAndCollidingShortNamesNeverMerge() throws Exception {
        String text=">sample one\nACGT\n>sample two\nACGA\n";
        assertEquals(Set.of("sample one","sample two"),fasta(text,PhylogeneticAlignment.Kind.DNA).taxa());
        assertThrows(IllegalArgumentException.class,()->PhylogeneticAlignment.readFasta(new StringReader(text),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.FIRST_FIELD,LIMITS));
        var value=PhylogeneticAlignment.readFasta(new StringReader(">A\tdescription\nACGT\n>B other\nACGA\n"),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.FIRST_FIELD,LIMITS);
        assertEquals(Set.of("A","B"),value.taxa());assertEquals("A\tdescription",value.rows().get(0).originalHeader());
        assertThrows(IllegalArgumentException.class,()->fasta(">A\tdescription\nACGT\n",PhylogeneticAlignment.Kind.DNA));
    }
    @Test void dnaSourceAliasesHaveSeparateRawCountsAndAnExplicitJc69View() throws Exception {
        var value=fasta(">A\naUrNX-.~?!\n>B\nACGTACGTAC\n",PhylogeneticAlignment.Kind.DNA);
        assertEquals(20,value.statistics().rawCells());assertEquals(2,value.statistics().lowercaseCells());assertEquals(7,value.statistics().missingCells());assertEquals(1,value.statistics().ambiguousCells());
        assertEquals("aUrNX-.~?!",value.sequences().get("A"));assertEquals("ATRNNNNNNN",value.jc69Sequences().get("A"));
        for(String version:new String[]{"1.6.12","2.1.2","2.1.4-beta","2.2.0.3"})assertThrows(IllegalArgumentException.class,()->value.requireIqtreeVersion(version));
        for(String version:new String[]{"2.3.1","2.3.5","2.3.6","2.4.0"})value.requireIqtreeVersion(version);
        assertThrows(IllegalArgumentException.class,()->value.requireIqtreeVersion("1.6.619"));assertThrows(IllegalArgumentException.class,()->value.requireIqtreeVersion("3.0.1"));
    }
    @Test void proteinAmbiguitiesAreDistinctFromSourceUnknownUOAndStopMarkers() throws Exception {
        var value=fasta(">protein\nABZJXUO*?-~.!\n",PhylogeneticAlignment.Kind.AMINO_ACID);
        assertEquals(3,value.statistics().ambiguousCells());assertEquals(9,value.statistics().missingCells());assertEquals(13,value.columns());
        assertThrows(IllegalStateException.class,value::jc69Sequences);
    }
    @Test void codonAmbiguityMakesTheWholeSourceCodonUnknownInsteadOfSummingPossibilities() throws Exception {
        var value=fasta(">coding\nATGAARNTNNNN---\n",PhylogeneticAlignment.Kind.CODON_STANDARD);
        assertEquals(15,value.columns());assertEquals(5,value.modelSites());assertEquals(15,value.statistics().rawCells());assertEquals(4,value.statistics().missingCells());assertEquals(2,value.statistics().partialCodonCellsAsUnknown());assertEquals(2,value.statistics().ambiguousCells());
        assertThrows(IllegalStateException.class,value::jc69Sequences);
    }
    @ParameterizedTest @ValueSource(strings={"TAA","TAG","TGA","UAA","ATGA",""})
    void standardCodeStopsAndIncompleteCodonsAreRefusedWithoutTrimming(String sequence) {
        assertThrows(IllegalArgumentException.class,()->fasta(">coding\n"+sequence+"\n",PhylogeneticAlignment.Kind.CODON_STANDARD));
    }
    @Test void relaxedAndFixedTenColumnSequentialPhylipKeepEveryContinuation() throws Exception {
        var relaxed=phylip("2 6\nAlpha ACG\nTAC\nBeta ACT\nTTC\n",PhylogeneticAlignment.Layout.SEQUENTIAL,PhylogeneticAlignment.NameField.RELAXED,false);
        var fixed=phylip("2 6\r\nAlpha     ACG\rTAC\r\nBeta      ACT\nTTC\n",PhylogeneticAlignment.Layout.SEQUENTIAL,PhylogeneticAlignment.NameField.FIXED_TEN_COLUMNS,false);
        assertEquals(Map.of("Alpha","ACGTAC","Beta","ACTTTC"),fixed.sequences());assertEquals(relaxed.sequences(),fixed.sequences());
        assertEquals("Alpha     ",fixed.rows().get(0).originalHeader());
    }
    @Test void namedAndUnnamedInterleavedPhylipRequireTheDeclaredOrderAndFullBlocks() throws Exception {
        var unnamed=phylip("2 6\nAlpha ACG\nBeta ACT\n\nTAC\nTTC\n",PhylogeneticAlignment.Layout.INTERLEAVED,PhylogeneticAlignment.NameField.RELAXED,false);
        var named=phylip("2 6\nAlpha ACG\nBeta ACT\n\nAlpha TAC\nBeta TTC\n",PhylogeneticAlignment.Layout.INTERLEAVED,PhylogeneticAlignment.NameField.RELAXED,true);
        assertEquals(unnamed.sequences(),named.sequences());assertEquals(6,named.columns());
        assertThrows(IllegalArgumentException.class,()->phylip("2 6\nAlpha ACG\nBeta ACT\nBeta TTC\nAlpha TAC\n",PhylogeneticAlignment.Layout.INTERLEAVED,PhylogeneticAlignment.NameField.RELAXED,true));
        assertThrows(IllegalArgumentException.class,()->phylip("2 6\nAlpha ACG\nBeta ACT\nAlpha TAC\nBeta TTC\n",PhylogeneticAlignment.Layout.INTERLEAVED,PhylogeneticAlignment.NameField.RELAXED,false));
    }
    @ParameterizedTest @ValueSource(strings={"","2 0","0 6","2 6 extra","2.0 6","2 6\nA ACG\nB ACT\nTAC\n","2 6\nA ACG\nB AC\nTAC\nTTC\n","2 6\nA ACG\nB ACT\nTAC\nTTCC\n","2 3\nA ACG\nB ACT\nextra\n","2 3\nA ACG\nA ACT\n","999999999999 3\n"})
    void invalidOrTruncatedPhylipNeverReturnsAPartialMatrix(String text) {
        assertThrows(IllegalArgumentException.class,()->phylip(text,PhylogeneticAlignment.Layout.INTERLEAVED,PhylogeneticAlignment.NameField.RELAXED,false));
    }
    @ParameterizedTest @ValueSource(strings={"ACGT",">\nACGT",">A\n\n>B\nACGT",">A\nACGT\n>B\nACG",">A\nACGT\n>A\nACGT",">A\nACGO",">A\nACG0",">A\nACG*",">A\nACG{AT}",">A\nACGé",">A\nACG\u0000",">\ud800\nACGT"})
    void invalidFastaNeverDropsStatesRecordsOrDuplicateIdentities(String text) {
        assertThrows(IllegalArgumentException.class,()->fasta(text,PhylogeneticAlignment.Kind.DNA));
    }
    @Test void allCapacitiesRefuseTheWholeInputRatherThanReturningSampledRowsOrSites() throws Exception {
        String text=">Alpha\nACGT\n>Beta\nACGA\n";
        for(var limits:new PhylogeneticAlignment.Limits[]{new PhylogeneticAlignment.Limits(1,100,100,100,100,100),new PhylogeneticAlignment.Limits(10,3,100,100,100,100),new PhylogeneticAlignment.Limits(10,100,7,100,100,100),new PhylogeneticAlignment.Limits(10,100,100,10,100,100),new PhylogeneticAlignment.Limits(10,100,100,100,4,100),new PhylogeneticAlignment.Limits(10,100,100,100,100,3)})
            assertThrows(IllegalArgumentException.class,()->PhylogeneticAlignment.readFasta(new StringReader(text),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,limits));
        var exact=new PhylogeneticAlignment.Limits(2,4,8,text.length(),6,5);
        assertEquals(8,PhylogeneticAlignment.readFasta(new StringReader(text),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,exact).statistics().rawCells());
        assertThrows(IllegalArgumentException.class,()->PhylogeneticAlignment.readFasta(new StringReader(text+"\n"),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,exact));
    }
    @Test void malformedTrailingUtf8InvalidatesOtherwiseCompleteRows() throws Exception {
        Path path=temporary.resolve("alignment.fa");Files.write(path,new byte[]{'>','A','\n','A','C','G','T','\n',(byte)0xc3});
        assertThrows(IOException.class,()->PhylogeneticAlignment.readFasta(path,PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,LIMITS));
    }
    @Test void readAndCloseFailuresPreventAnyMatrixFromBeingReturned() throws Exception {
        var closed=new AtomicBoolean();
        var source=new FilterReader(new StringReader(">A\nACGT\n")) { @Override public void close() throws IOException { closed.set(true);throw new IOException("injected close failure"); } };
        assertThrows(IOException.class,()->PhylogeneticAlignment.readFasta(source,PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,LIMITS));assertTrue(closed.get());
        var bad=new FilterReader(new StringReader(">A\nACGT\n")) { @Override public int read(char[] data,int offset,int count) throws IOException { throw new IOException("injected read failure"); } @Override public void close() throws IOException { closed.set(false);super.close(); } };
        assertThrows(IOException.class,()->PhylogeneticAlignment.readFasta(bad,PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,LIMITS));assertFalse(closed.get());
    }
    @Test void opaqueFastaAndExactNewickRestorationAvoidAllSourceNameCollisions() throws Exception {
        Map<String,String> input=new LinkedHashMap<>();input.put("a b","ACGT");input.put("a_b","ACGA");input.put("α's (taxon)","TCGT");input.put("T000000001","ACGG");
        var value=PhylogeneticAlignment.of(input,PhylogeneticAlignment.Kind.DNA,LIMITS);var writer=new StringWriter();value.writeFasta(writer,true,2);
        var prepared=fasta(writer.toString(),PhylogeneticAlignment.Kind.DNA);assertEquals(Set.of("T000000001","T000000002","T000000003","T000000004"),prepared.taxa());
        var tree=NewickTree.parse("((T000000001:.1,T000000002:.2):.3,T000000003:.4,T000000004:.5);");
        var restored=NewickTree.parse(tree.relabelLeaves(value.opaqueToOriginal()));assertEquals(input.keySet(),restored.taxa());assertEquals(.3,restored.distance("a b","a_b"),1e-15);
        assertThrows(UnsupportedOperationException.class,()->value.sequences().clear());assertThrows(UnsupportedOperationException.class,()->value.rows().clear());assertThrows(UnsupportedOperationException.class,()->value.opaqueToOriginal().clear());assertThrows(UnsupportedOperationException.class,()->value.statistics().symbols().clear());
    }
    @Test void matrixCopiesInputOwnershipAndWriterFailuresRemainFailures() throws Exception {
        Map<String,String> input=new LinkedHashMap<>();input.put("A","ACGT");input.put("B","ACGA");var value=PhylogeneticAlignment.of(input,PhylogeneticAlignment.Kind.DNA,LIMITS);input.clear();assertEquals(2,value.rows().size());
        Writer writer=new Writer() { @Override public void write(char[] data,int offset,int length) throws IOException { throw new IOException("injected write failure"); } @Override public void flush() { } @Override public void close() { } };
        assertThrows(IOException.class,()->value.writeFasta(writer,true,2));assertThrows(IllegalArgumentException.class,()->value.writeFasta(new StringWriter(),true,0));
    }
}
