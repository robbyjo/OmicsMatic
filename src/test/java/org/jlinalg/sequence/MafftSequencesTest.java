/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MafftSequencesTest {
    private static final MafftSequences.Limits LIMITS=MafftSequences.Limits.defaults();
    @TempDir Path temporary;
    private static MafftSequences.Data read(String text,MafftSequences.Alphabet alphabet,MafftSequences.Symbols symbols,MafftSequences.Layout layout)throws IOException {
        return MafftSequences.readFasta(new StringReader(text),alphabet,symbols,MafftSequences.Headers.WHOLE_HEADER,layout,MafftSequences.Gaps.ALLOW,LIMITS,()->{});
    }
    private static MafftSequences.Data dna(String text,MafftSequences.Layout layout)throws IOException {
        return read(text,MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,layout);
    }
    @Test void unalignedRowsKeepExactUnicodeHeadersCaseGapsAndEveryResidue()throws Exception {
        String text=">sample α's first\nac-GTuN \t\n>sample two\nACGTA\n";
        var data=dna(text,MafftSequences.Layout.SEQUENCES);
        assertEquals(List.of("sample α's first","sample two"),new ArrayList<>(data.taxa()));
        assertEquals("ac-GTuN",data.rows().get(0).sequence());assertEquals(12,data.statistics().cells());
        assertEquals(11,data.statistics().residues());assertEquals(1,data.statistics().gapCells());
        assertEquals(2,data.statistics().sequenceWhitespace());assertEquals(text.length(),data.statistics().decodedCharacters());
        assertThrows(IllegalStateException.class,data::columns);
        assertThrows(UnsupportedOperationException.class,()->data.rows().clear());
        assertThrows(UnsupportedOperationException.class,()->data.byTaxon().clear());
        assertThrows(UnsupportedOperationException.class,()->data.originalToOpaque().clear());
    }
    @Test void firstFieldCollisionsFailAndOriginalDescriptionsRemainAvailable()throws Exception {
        String text=">A\tdescription α\nACGT\n>B other\nACGA\n";
        var data=MafftSequences.readFasta(new StringReader(text),MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.FIRST_FIELD,MafftSequences.Layout.ALIGNMENT,MafftSequences.Gaps.FORBID,LIMITS,()->{});
        assertEquals(Set.of("A","B"),data.taxa());assertEquals("A\tdescription α",data.rows().get(0).originalHeader());
        assertThrows(IOException.class,()->MafftSequences.readFasta(new StringReader(">A one\nACGT\n>A two\nACGA\n"),data.alphabet(),data.symbols(),MafftSequences.Headers.FIRST_FIELD,data.layout(),MafftSequences.Gaps.FORBID,LIMITS,()->{}));
        assertThrows(IOException.class,()->dna(text,MafftSequences.Layout.ALIGNMENT));
    }
    @ParameterizedTest @ValueSource(strings={"", "ACGT", ">A\n", ">A\n \t\n", ">\nACGT", ">A\nACGT\n>A\nACGA", ">A\nACGT\n>B\nACG*",
        ">A\nACGΩ",">A\nACG0",">A\nACG\u0000","> \nACGT",">\ud800\nACGT",";comment\n>A\nACGT"})
    void malformedFastaNeverReturnsAPrefix(String text){
        assertThrows(IOException.class,()->dna(text,MafftSequences.Layout.SEQUENCES));
    }
    @Test void alignmentWidthAndLiteralSymbolPoliciesAreExplicit()throws Exception {
        assertThrows(IOException.class,()->dna(">A\nACGT\n>B\nACG\n",MafftSequences.Layout.ALIGNMENT));
        assertThrows(IOException.class,()->read(">A\nACDEUO*?\n",MafftSequences.Alphabet.PROTEIN,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.SEQUENCES));
        var any=read(">A\nACDEUO*?,@#\n",MafftSequences.Alphabet.PROTEIN,MafftSequences.Symbols.ANY_PRINTABLE,MafftSequences.Layout.SEQUENCES);
        assertEquals("ACDEUO*?,@#",any.rows().get(0).sequence());
        assertThrows(IOException.class,()->MafftSequences.readFasta(new StringReader(">A\nAC-GT\n"),MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.FORBID,LIMITS,()->{}));
    }
    @Test void everyDeclaredInputCapacityRejectsTheWholePopulation()throws Exception {
        String text=">Alpha\nACGT\n>Beta\nACGA\n";
        var capacities=List.of(new MafftSequences.Limits(1,10,100,100,100,20,100000),new MafftSequences.Limits(10,3,100,100,100,20,100000),
            new MafftSequences.Limits(10,10,7,100,100,20,100000),new MafftSequences.Limits(10,10,100,text.length()-1,100,20,100000),
            new MafftSequences.Limits(10,10,100,100,4,4,100000),new MafftSequences.Limits(10,10,100,100,100,4,100000),
            new MafftSequences.Limits(10,10,100,100,100,20,65536));
        for(var limits:capacities)assertThrows(IOException.class,()->MafftSequences.readFasta(new StringReader(text),MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,limits,()->{}));
    }
    @Test void cumulativeHeaderAndBlankLineStorageAreBoundedBeforeWholeInputIsRetained(){
        String text=java.util.stream.IntStream.range(0,64).mapToObj(i->">"+i+"x".repeat(1000)+"\nA\n").collect(java.util.stream.Collectors.joining());
        var consumed=new AtomicInteger();Reader reader=new FilterReader(new StringReader(text)){
            @Override public int read(char[] b,int off,int len)throws IOException {int n=super.read(b,off,len);if(n>0)consumed.addAndGet(n);return n;}
        };
        var limits=new MafftSequences.Limits(100,10,1000,100000,2000,1100,95000);
        assertThrows(IOException.class,()->MafftSequences.readFasta(reader,MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,limits,()->{}));
        assertTrue(consumed.get()<text.length()/2);
        assertThrows(IOException.class,()->MafftSequences.readFasta(new StringReader(">A\n"+" ".repeat(10000)+"\nA\n"),MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,new MafftSequences.Limits(10,10,100,20000,20000,10,70000),()->{}));
    }
    @Test void eofAndSuccessfulCloseAreMandatoryIncludingLateMalformedUtf8()throws Exception {
        Path path=temporary.resolve("bad.fa");Files.write(path,new byte[]{'>','A','\n','A','C','G','T','\n',(byte)0xc3});
        assertThrows(IOException.class,()->MafftSequences.readFasta(path,MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,LIMITS));
        var closed=new AtomicBoolean();Reader reader=new FilterReader(new StringReader(">A\nACGT\n")){
            @Override public void close()throws IOException {closed.set(true);throw new IOException("injected close failure");}
        };
        assertThrows(IOException.class,()->MafftSequences.readFasta(reader,MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,LIMITS,()->{}));assertTrue(closed.get());
    }
    @Test void cancellationDuringALongLineClosesTheOwnedReader(){
        var closed=new AtomicBoolean();var checks=new AtomicInteger();Reader reader=new FilterReader(new StringReader(">A\n"+"A".repeat(50000))){
            @Override public void close()throws IOException {closed.set(true);super.close();}
        };
        assertThrows(java.util.concurrent.CancellationException.class,()->MafftSequences.readFasta(reader,MafftSequences.Alphabet.NUCLEOTIDE,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,MafftSequences.Layout.SEQUENCES,MafftSequences.Gaps.ALLOW,LIMITS,()->{if(checks.incrementAndGet()>3)throw new java.util.concurrent.CancellationException("controlled");}));
        assertTrue(closed.get());
    }
    @Test void outputAuditCountsEveryResidueAndAllowsOnlyCaseChanges()throws Exception {
        var original=dna(">sample α\naC-GT\n>sample β\nACGTT\n",MafftSequences.Layout.SEQUENCES);
        var output=dna(">T000000001\na-cGT-\n>T000000002\nACGTT-\n",MafftSequences.Layout.ALIGNMENT);
        var names=Map.of("T000000001","sample α","T000000002","sample β");
        var audit=MafftSequences.audit(original,output,names,MafftSequences.OutputOrder.INPUT);
        assertEquals(2,audit.taxa());assertEquals(6,audit.columns());assertEquals(9,audit.originalResidues());
        assertEquals(3,audit.alignedGapCells());assertEquals(1,audit.allGapColumns());assertEquals(1,audit.caseChangedResidues());
        for(String bad:List.of(">T000000001\nacGTT-\n>T000000002\nACGTT-\n",">T000000001\nac-T--\n>T000000002\nACGTT-\n"))
            assertThrows(IllegalArgumentException.class,()->MafftSequences.audit(original,dna(bad,MafftSequences.Layout.ALIGNMENT),names,MafftSequences.OutputOrder.INPUT));
        assertThrows(UnsupportedOperationException.class,()->audit.originalToOutputRow().clear());
    }
    @Test void duplicateAliasExtraRowMissingRowAndOrderCannotHideBehindValidSequences()throws Exception {
        var original=dna(">A\nACGT\n>B\nACGA\n",MafftSequences.Layout.SEQUENCES);
        var duplicate=dna(">x\nACGT\n>y\nACGT\n>z\nACGA\n",MafftSequences.Layout.ALIGNMENT);
        assertThrows(IllegalArgumentException.class,()->MafftSequences.audit(original,duplicate,Map.of("x","A","y","A","z","B"),MafftSequences.OutputOrder.SOURCE));
        var reversed=dna(">y\nACGA\n>x\nACGT\n",MafftSequences.Layout.ALIGNMENT);var map=Map.of("x","A","y","B");
        assertThrows(IllegalArgumentException.class,()->MafftSequences.checkIdentities(original,reversed,map,MafftSequences.OutputOrder.INPUT));
        assertEquals(Map.of("A",1,"B",0),MafftSequences.audit(original,reversed,map,MafftSequences.OutputOrder.SOURCE).originalToOutputRow());
        assertThrows(IllegalArgumentException.class,()->MafftSequences.audit(original,dna(">x\nACGT\n",MafftSequences.Layout.ALIGNMENT),Map.of("x","A"),MafftSequences.OutputOrder.SOURCE));
    }
    @Test void uAndTDifferAndUnknownSymbolsAreNeverSilentlyRewritten()throws Exception {
        var original=dna(">A\nACGU\n",MafftSequences.Layout.SEQUENCES);
        assertThrows(IllegalArgumentException.class,()->MafftSequences.audit(original,dna(">x\nACGT\n",MafftSequences.Layout.ALIGNMENT),Map.of("x","A"),MafftSequences.OutputOrder.INPUT));
        assertEquals(4,MafftSequences.audit(original,dna(">x\nacgu\n",MafftSequences.Layout.ALIGNMENT),Map.of("x","A"),MafftSequences.OutputOrder.INPUT).originalResidues());
    }
    @Test void opaqueWritingAndCombiningNeverMutateOriginalRows()throws Exception {
        var first=dna(">a b\nAC-GT\n",MafftSequences.Layout.SEQUENCES);var second=dna(">a_b\nACGT\n",MafftSequences.Layout.SEQUENCES);
        var combined=MafftSequences.combine(first,second,LIMITS);assertEquals(List.of("a b","a_b"),new ArrayList<>(combined.taxa()));
        var writer=new StringWriter();combined.writeFasta(writer,combined.originalToOpaque(),true,2);
        assertEquals(">T000000001\nAC\nGT\n>T000000002\nAC\nGT\n",writer.toString());assertEquals("AC-GT",first.rows().get(0).sequence());
        assertThrows(IllegalArgumentException.class,()->MafftSequences.combine(first,first,LIMITS));
        assertThrows(IOException.class,()->MafftSequences.combine(first,second,new MafftSequences.Limits(1,10,100,100,100,100,100000)));
        Writer failed=new Writer(){@Override public void write(char[] b,int off,int len)throws IOException{throw new IOException("injected");}@Override public void close(){}@Override public void flush(){}};
        assertThrows(IOException.class,()->combined.writeFasta(failed,combined.originalToOpaque(),false,80));
    }
    @Test void completeBackboneColumnsAreCheckedWithoutInventingGapOnlyOwnership()throws Exception {
        var backbone=read(">a\nACCDEFGHI-K\n>b\nA--DEFGHI-K\n",MafftSequences.Alphabet.PROTEIN,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Layout.ALIGNMENT);
        var output=read(">x\nACCD-----EFGHIK\n>y\nA--D-----EFGHIK\n>z\nACCDPQRSTEFG---\n",backbone.alphabet(),backbone.symbols(),MafftSequences.Layout.ALIGNMENT);
        var names=Map.of("x","a","y","b","z","new");
        var audit=MafftSequences.auditBackbone(backbone,output,names,MafftSequences.Backbone.EXPAND);
        assertEquals(10,audit.matchedNonemptyColumns());assertEquals(1,audit.originalGapOnlyColumns());assertEquals(5,audit.outputBackboneGapOnlyColumns());assertFalse(audit.gapOnlyColumnIdentityInferred());
        assertThrows(IllegalArgumentException.class,()->MafftSequences.auditBackbone(backbone,output,names,MafftSequences.Backbone.KEEP_LENGTH));
        var changed=read(">x\nACC------DEFGHIK\n>y\nA--D------EFGHIK\n>z\nACCDPQRSTEFG---\n".replace("ACC------DEFGHIK","ACC-----DEFGHIK").replace("A--D------EFGHIK","A--D-----EFGHIK"),backbone.alphabet(),backbone.symbols(),MafftSequences.Layout.ALIGNMENT);
        assertThrows(IllegalArgumentException.class,()->MafftSequences.auditBackbone(backbone,changed,names,MafftSequences.Backbone.EXPAND));
    }
    @Test void aWriterCallbackCannotRebindLaterTaxaDuringAnExport()throws Exception {
        var input=dna(">A\nAC\n>B\nGT\n",MafftSequences.Layout.SEQUENCES);
        var names=new LinkedHashMap<String,String>();names.put("A","first");names.put("B","second");
        var writer=new StringWriter(){
            @Override public void write(int c){if(c=='>')names.put("B","changed-by-caller");super.write(c);}
        };
        input.writeFasta(writer,names,false,80);
        assertEquals(">first\nAC\n>second\nGT\n",writer.toString());assertEquals("changed-by-caller",names.get("B"));
    }
}
