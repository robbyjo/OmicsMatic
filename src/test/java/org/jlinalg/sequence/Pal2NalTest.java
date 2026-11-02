/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class Pal2NalTest {
    private static final Pal2Nal.Limits LIMITS = Pal2Nal.Limits.defaults();
    private static Pal2Nal.Options options() {
        return new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.ALLOW_V14_RELAXED,
                Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,0,0);
    }
    private static List<Pal2Nal.Sequence> rows(String... strings) {
        var result=new ArrayList<Pal2Nal.Sequence>();
        for(int i=0;i<strings.length;i++)result.add(new Pal2Nal.Sequence("p"+(i+1),strings[i]));
        return result;
    }
    private static Pal2Nal.Result fit(String peptide,String dna,Pal2Nal.Options opts)throws IOException {
        return Pal2Nal.align(rows(peptide),rows(dna),opts,LIMITS,()->{});
    }
    private static List<Pal2Nal.Sequence> parseFasta(String text) {
        var values=new ArrayList<Pal2Nal.Sequence>();String id=null;var sequence=new StringBuilder();
        for(String line:text.split("\\R")) {
            if(line.startsWith(">")) {
                if(id!=null)values.add(new Pal2Nal.Sequence(id,sequence.toString()));
                id=line.substring(1);sequence.setLength(0);
            } else if(!line.isBlank()) { assertNotNull(id,"Expected complete original FASTA output");sequence.append(line); }
        }
        if(id!=null)values.add(new Pal2Nal.Sequence(id,sequence.toString()));
        return values;
    }
    private static String decoded(String text) { return new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8); }

    @TestFactory Stream<DynamicTest> everyFreshOriginalProtocolAndDeclaredDeparture()throws Exception {
        byte[] raw;
        try(var in=getClass().getResourceAsStream("pal2nal-original/controls.zip")) { assertNotNull(in);raw=in.readAllBytes(); }
        assertEquals("3841d0fe4c0f8dc099dd76b9343fc84ff2952839f2303b65eb749d1b8fdfa686",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null)assertNull(files.put(entry.getName(),zip.readAllBytes()));
        }
        // The fixture is generated once from actual original processes; full byte pin is in its sidecar.
        assertEquals(429,files.size());
        String[] lines=new String(files.get("index.tsv"),StandardCharsets.UTF_8).strip().split("\n");
        assertEquals(85,lines.length);
        return Stream.of(lines).map(line -> {
            String[] f=line.split("\t",-1);
            assertEquals(8,f.length);
            return DynamicTest.dynamicTest(f[0],()-> {
                var peptide=parseFasta(decoded(f[6]));var dna=parseFasta(decoded(f[7]));
                var flags=Set.of(f[3].split(",",-1));String mask=decoded(f[4]);
                var opts=new Pal2Nal.Options(Integer.parseInt(f[1]),Pal2Nal.Pairing.valueOf(f[2]),Pal2Nal.Matching.ALLOW_V14_RELAXED,
                        Pal2Nal.Placement.SOURCE_LEFTMOST,flags.contains("-nogap")?Pal2Nal.GapStopFilter.V14_NOGAP:Pal2Nal.GapStopFilter.KEEP,
                        flags.contains("-nomismatch"),mask.isEmpty()?null:mask,f[5].equals("documented-normalization-departure"),f[0].startsWith("tfastx-"),0,0);
                if(f[5].equals("native-refusal")) { assertThrows(IOException.class,()->Pal2Nal.align(peptide,dna,opts,LIMITS,()->{}));return; }
                var actual=Pal2Nal.align(peptide,dna,opts,LIMITS,()->{});
                var expected=parseFasta(new String(files.get(f[0]+"/stdout"),StandardCharsets.UTF_8));
                var expectedMap=new LinkedHashMap<String,String>();for(var row:expected)expectedMap.put(row.id(),row.sequence());
                if(f[5].equals("exact-rows"))assertEquals(expectedMap,actual.alignedSequences());
                else {
                    assertNotEquals(expectedMap,actual.alignedSequences());
                    assertTrue(actual.warnings().stream().anyMatch(s->s.startsWith("NATIVE_DOCUMENTED_SYMBOL_NORMALIZATION")));
                    assertEquals(f[0].startsWith("underscore")?"ATGGCTTAAAAA":"ATG---GCTAAA",actual.rows().get(0).alignment());
                }
                assertEquals(peptide.size(),actual.rows().size());
                for(var row:actual.rows()) {
                    assertEquals(actual.outputColumns(),row.alignment().length());
                    assertEquals(actual.expandedColumns(),row.expandedAlignment().length());
                    assertEquals(actual.proteinColumns(),row.cells().size());
                    var consumed=new StringBuilder();int position=row.dnaStart();
                    for(var cell:row.cells()) {
                        if(cell.state()==Pal2Nal.State.GAP) { assertEquals(0,cell.dnaStart());assertTrue(cell.bases().isEmpty()); }
                        else { assertEquals(position,cell.dnaStart());consumed.append(cell.bases());position+=cell.bases().length(); }
                    }
                    assertEquals(row.originalDna().substring(row.dnaStart()-1,row.dnaEnd()),consumed.toString());
                    var retained=new StringBuilder();
                    for(var triplet:actual.triplets())if(triplet.drops().isEmpty())retained.append(row.expandedAlignment(),triplet.expandedStart()-1,triplet.expandedStart()+2);
                    assertEquals(row.alignment(),retained.toString());
                }
            });
        });
    }

    @Test void sourceTable10DefectAndSourceAmbiguityRemainExplicit()throws Exception {
        assertEquals(Set.of(1,2,3,4,5,6,9,10,11,12,13,14,15,16,21,22,23),Pal2Nal.supportedCodonTables());
        assertTrue(Pal2NalCodes.matches(10,'*',"TGA"));assertFalse(Pal2NalCodes.matches(10,'C',"TGA"));
        assertTrue(Pal2NalCodes.matches(6,'Q',"TAA"));assertFalse(Pal2NalCodes.matches(6,'*',"TAA"));
        assertTrue(Pal2NalCodes.matches(1,'A',"GCN"));assertFalse(Pal2NalCodes.matches(1,'I',"ATN"));
        var opts=new Pal2Nal.Options(10,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,1,1);
        assertTrue(fit("MA","ATGGCT",opts).warnings().get(0).startsWith("SOURCE_V14_TABLE_10"));
        for(int table:List.of(3,11,13)) {
            var original=new Pal2Nal.Options(table,opts.pairing(),opts.matching(),opts.placement(),opts.gapStopFilter(),false,null,false,false,1,1);
            assertTrue(fit("MA","ATGGCT",original).warnings().get(0).startsWith("SOURCE_V14_INITIATORS"));
        }
        assertThrows(IllegalArgumentException.class,()->Pal2NalCodes.requireTable(14_1));
    }
    @Test void independentNcbiReferenceChecksEveryConcreteCodonAndInitiator()throws Exception {
        byte[] raw;
        try(var in=getClass().getResourceAsStream("pal2nal-original/ncbi-codes.zip")) { assertNotNull(in);raw=in.readAllBytes(); }
        assertEquals("15134f83c2dae767dac2c074754e1ca14d923e009130254c04cf28bee91af48e",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)assertNull(files.put(entry.getName(),zip.readAllBytes()));
        }
        assertEquals("2aecbdd09ecc5e0d233c105090a593737298ef93d88f6bc0c1bef26df6051a04",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(files.get("gc.prt"))));
        var disagreements=new java.util.TreeSet<String>();var missingStarts=new java.util.TreeSet<String>();var extraStarts=new java.util.TreeSet<String>();
        int codons=0;
        for(String line:new String(files.get("tables.tsv"),StandardCharsets.US_ASCII).strip().split("\n")) {
            String[] fields=line.split("\t",-1);int table=Integer.parseInt(fields[0]);int position=0;
            assertEquals(64,fields[1].length());assertEquals(64,fields[2].length());
            for(char a:"TCAG".toCharArray())for(char b:"TCAG".toCharArray())for(char c:"TCAG".toCharArray()) {
                String codon=""+a+b+c;char actual='?';int matches=0;
                for(char aa:"ACDEFGHIKLMNPQRSTVWY*".toCharArray())if(Pal2NalCodes.matches(table,aa,codon)) { actual=aa;matches++; }
                assertEquals(1,matches);char expected=fields[1].charAt(position);
                if(actual!=expected)disagreements.add(table+":"+codon+":"+expected+":"+actual);
                boolean sourceStart=Pal2NalCodes.matches(table,'B',codon);boolean ncbiStart=fields[2].charAt(position)=='M';
                if(ncbiStart&&!sourceStart)missingStarts.add(table+":"+codon);
                if(sourceStart&&!ncbiStart)extraStarts.add(table+":"+codon);
                position++;codons++;
            }
        }
        assertEquals(1088,codons);assertEquals(Set.of("10:TGA:C:*"),disagreements);
        assertEquals(Set.of("3:GTG","11:ATT","11:ATC","11:ATA","13:ATA"),missingStarts);
        assertTrue(extraStarts.isEmpty(),extraStarts.toString());
    }
    @Test void explicitPairingNeverSilentlyFallsBackToInputOrder()throws Exception {
        var a=List.of(new Pal2Nal.Sequence("α","MA"));var b=List.of(new Pal2Nal.Sequence("β","ATGGCT"));
        assertThrows(IOException.class,()->Pal2Nal.align(a,b,options(),LIMITS,()->{}));
        var opts=new Pal2Nal.Options(1,Pal2Nal.Pairing.INPUT_ORDER,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,1,1);
        var out=Pal2Nal.align(a,b,opts,LIMITS,()->{});
        assertEquals("α",out.rows().get(0).proteinId());assertEquals("β",out.rows().get(0).dnaId());
        assertTrue(out.warnings().get(0).startsWith("EXPLICIT_POSITIONAL_PAIRING"));
    }
    @Test void multiplePlacementsRequireAnExplicitSourceLeftmostChoice()throws Exception {
        var permissive=fit("MA","ATGGCTATGGCT",options());assertEquals(1,permissive.rows().get(0).dnaStart());assertEquals(2,permissive.rows().get(0).placementsAtLeast());
        var strict=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.REQUIRE_UNIQUE,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,1,1);
        assertThrows(IOException.class,()->fit("MA","ATGGCTATGGCT",strict));
    }
    @Test void relaxedExitSuccessCannotSatisfyVerificationThresholds()throws Exception {
        var weak=fit("M".repeat(12),"GCT".repeat(12),options());
        assertTrue(weak.rows().get(0).relaxed());assertEquals(0,weak.rows().get(0).verifiedResidues());assertEquals(12,weak.rows().get(0).mismatchedResidues());
        assertTrue(weak.warnings().stream().anyMatch(s->s.startsWith("NO_VERIFIED_RESIDUES")));
        assertTrue(weak.warnings().stream().anyMatch(s->s.startsWith("MISMATCHED_CODONS")));
        var count=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.ALLOW_V14_RELAXED,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,1,0);
        assertThrows(IOException.class,()->fit("M".repeat(12),"GCT".repeat(12),count));
        var fraction=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.ALLOW_V14_RELAXED,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,0,.1);
        assertThrows(IOException.class,()->fit("M".repeat(12),"GCT".repeat(12),fraction));
        var exact=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,0,0);
        assertThrows(IOException.class,()->fit("M".repeat(12),"GCT".repeat(12),exact));
    }
    @Test void sourceAndCorrectedStopFilteringAreDistinctPolicies()throws Exception {
        var source=new Pal2Nal.Options(6,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.V14_NOGAP,false,null,false,false,1,1);
        var corrected=new Pal2Nal.Options(6,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.SELECTED_V14_TABLE_NOGAP,false,null,false,false,1,1);
        assertEquals("ATGGCT",fit("MAQ","ATGGCTTAA",source).rows().get(0).alignment());
        assertEquals("ATGGCTTAA",fit("MAQ","ATGGCTTAA",corrected).rows().get(0).alignment());
        var source1=new Pal2Nal.Options(1,source.pairing(),source.matching(),source.placement(),source.gapStopFilter(),false,null,false,false,1,1);
        var corrected1=new Pal2Nal.Options(1,source.pairing(),source.matching(),source.placement(),corrected.gapStopFilter(),false,null,false,false,1,1);
        assertEquals("atggcttaa",fit("MA*","atggcttaa",source1).rows().get(0).alignment());
        assertEquals("atggct",fit("MA*","atggcttaa",corrected1).rows().get(0).alignment());
    }
    @Test void unplacedDnaTailsAndFilteredCellsRemainRecoverable()throws Exception {
        var opts=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,"# #",false,false,1,1);
        var row=fit("MAK","CCCatgGCTaaaTTT",opts).rows().get(0);
        assertEquals(4,row.dnaStart());assertEquals(12,row.dnaEnd());assertEquals("atgaaa",row.alignment());
        assertEquals("CCCatgGCTaaaTTT",row.originalDna());assertEquals("GCT",row.cells().get(1).bases());
        assertTrue(fit("MAK","CCCatgGCTaaaTTT",opts).warnings().stream().anyMatch(s->s.startsWith("UNPLACED_DNA_FLANKS")));
    }
    @Test void everyPopulationCapacityFailsWithoutReturningAPrefix() {
        var caps=List.of(new Pal2Nal.Limits(1,100,100,1000,1000,10000,1000000),new Pal2Nal.Limits(10,2,100,1000,1000,10000,1000000),
                new Pal2Nal.Limits(10,100,8,1000,1000,10000,1000000),new Pal2Nal.Limits(10,100,100,1,1000,10000,1000000),
                new Pal2Nal.Limits(10,100,100,1000,1,10000,1000000),new Pal2Nal.Limits(10,100,100,1000,1000,1,1000000),
                new Pal2Nal.Limits(10,100,100,1000,1000,10000,65536));
        for(var cap:caps)assertThrows(IOException.class,()->Pal2Nal.align(rows("MAK","MAK"),rows("ATGGCTAAA","ATGGCTAAA"),options(),cap,()->{}));
    }
    @Test void cancellationIsCheckedDuringLongInputValidation() {
        var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->Pal2Nal.align(rows("A".repeat(2000)),rows("GCT".repeat(2000)),options(),LIMITS,()->{if(calls.incrementAndGet()==2)throw new CancellationException();}));
        assertEquals(2,calls.get());
    }
    @Test void noSymbolOrLateRowIsSilentlyDiscarded() {
        for(String bad:List.of("ATG-GCT","ATG1GCT","ATG GCT","ATGΩGCT","ATG\u0000GCT","ATGZZZ"))
            assertThrows(IOException.class,()->Pal2Nal.align(rows("MA","MA"),rows("ATGGCT",bad),options(),LIMITS,()->{}));
        for(String bad:List.of("MU","MZ","M_","M.","M0","M?","MΩ"))assertThrows(IOException.class,()->fit(bad,"ATGGCT",options()));
        assertThrows(IOException.class,()->Pal2Nal.align(rows("MA","MAK"),rows("ATGGCT","ATGGCTAAA"),options(),LIMITS,()->{}));
    }
    @Test void identifiersSupportUnicodeButRejectMalformedUtf16AndControls()throws Exception {
        String id="sample α \uD83D\uDE00";
        var result=Pal2Nal.align(List.of(new Pal2Nal.Sequence(id,"MA")),List.of(new Pal2Nal.Sequence(id,"ATGGCT")),options(),LIMITS,()->{});
        assertEquals(id,result.rows().get(0).proteinId());
        for(String bad:List.of("", " ","x\u0000","\uD800","\uDC00"))assertThrows(IOException.class,()->Pal2Nal.align(List.of(new Pal2Nal.Sequence(bad,"MA")),rows("ATGGCT"),options(),LIMITS,()->{}));
    }
    @Test void selectionsAndThresholdsCannotBeImplicitOrNonfinite() {
        for(String mask:List.of("", "   ","##","### ","#.#")) {
            var opts=new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,mask,false,false,0,0);
            assertThrows(IOException.class,()->fit("MAK","ATGGCTAAA",opts));
        }
        for(double fraction:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})assertThrows(IllegalArgumentException.class,()->new Pal2Nal.Options(1,Pal2Nal.Pairing.EXACT_ID,Pal2Nal.Matching.EXACT_ONLY,Pal2Nal.Placement.SOURCE_LEFTMOST,Pal2Nal.GapStopFilter.KEEP,false,null,false,false,0,fraction));
    }
    @Test void completeResultCollectionsCannotBeMutated()throws Exception {
        var out=fit("MA","ATGGCT",options());
        assertThrows(UnsupportedOperationException.class,()->out.rows().clear());
        assertThrows(UnsupportedOperationException.class,()->out.rows().get(0).cells().clear());
        assertThrows(UnsupportedOperationException.class,()->out.triplets().clear());
        assertThrows(UnsupportedOperationException.class,()->out.triplets().get(0).drops().clear());
        assertThrows(UnsupportedOperationException.class,()->out.warnings().clear());
        assertThrows(UnsupportedOperationException.class,()->out.alignedSequences().clear());
    }
}
