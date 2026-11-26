/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.assembly.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MerquryNativeTest {
    private static final Runnable CHECK=()->{};
    private static MerquryBoundary.Bin bin(int depth,long n){return new MerquryBoundary.Bin(depth,n);}
    private static MerquryFastx.Result fastx(String input,int k)throws IOException{return MerquryFastx.count(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),k,null,new MerquryFastx.Limits(100000,1000,1000,100,10000,1000),CHECK);}
    @Test void encodedCanonicalOrderDiffersFromAscii(){assertEquals("ACT",MerquryAssessment.canonical("AGT"));assertEquals("ACT",MerquryAssessment.canonical("ACT"));assertThrows(IllegalArgumentException.class,()->MerquryAssessment.canonical("ACN"));}
    @Test void sparseHistogramRecordsCurrentDepthMinusOne(){var result=MerquryBoundary.calculate(List.of(bin(1,10),bin(3,3),bin(8,7)),CHECK);assertEquals(7,result.effectiveMinimumDepth());assertTrue(result.firstValleyFound());assertEquals(3,result.completeBins());}
    @Test void plateausDoNotCreateValley(){assertEquals(0,MerquryBoundary.calculate(List.of(bin(1,10),bin(2,4),bin(3,4),bin(4,8)),CHECK).effectiveMinimumDepth());}
    @Test void completeTrailingInputStillValidated(){assertThrows(IllegalArgumentException.class,()->MerquryBoundary.calculate(List.of(bin(1,10),bin(2,3),bin(3,7),bin(2,1)),CHECK));}
    @Test void noCrossingPreservesZero(){assertEquals(0,MerquryBoundary.calculate(List.of(bin(1,3)),CHECK).effectiveMinimumDepth());assertEquals(0,MerquryBoundary.calculate(List.of(),CHECK).effectiveMinimumDepth());}
    @Test void histogramLongToDoublePrecisionMatchesSource(){assertFalse(MerquryBoundary.calculate(List.of(bin(1,9007199254740993L),bin(2,9007199254740992L),bin(3,9007199254740993L)),CHECK).firstValleyFound());}
    @Test void repeatedFastaIdentifiersAreNotDeduplicated()throws Exception{var result=fastx(">same description\nACGTAC\n>same other\nACGTAC\n",3);assertEquals(2,result.sequences().size());assertEquals("same",result.sequences().get(1).id());assertEquals(8,result.counts().values().stream().mapToLong(Long::longValue).sum());}
    @Test void ambiguityAndUracilBreakWindows()throws Exception{var result=fastx(">x\naacgUUacgtNRYacgt\n",3);assertEquals(6,result.sequences().get(0).validWindows());assertEquals(2,result.uracilBases());assertEquals(5,result.ambiguousBases());}
    @Test void wrappedFastqQualitiesAndSequenceAreComplete()throws Exception{var result=fastx("@x\nACG\nTAC\n+x\nIII\nIII\n",3);assertEquals(4,result.sequences().get(0).validWindows());assertThrows(IOException.class,()->fastx("@x\nACG\n+\nII\n",3));}
    @Test void gzipIsDecodedWithoutChangingOriginalRecords()throws Exception{byte[] bytes;try(var raw=new ByteArrayOutputStream()){try(var gzip=new GZIPOutputStream(raw)){gzip.write(">x\nACGT\n".getBytes(StandardCharsets.US_ASCII));}bytes=raw.toByteArray();}var actual=MerquryFastx.count(new ByteArrayInputStream(bytes),3,null,new MerquryFastx.Limits(1000,100,100,10,100,100),CHECK);assertEquals(fastx(">x\nACGT\n",3).counts(),actual.counts());}
    @Test void invalidInputCannotBeSilentlyRepaired(){for(String input:List.of("",">x\nACGT-\n",">\nACGT\n",">x\nACGT\n@y\nACGT\n"))assertThrows(IOException.class,()->fastx(input,3));}
    @Test void capacitiesAndCancellationApplyToCompletePopulation(){assertThrows(IOException.class,()->MerquryFastx.count(new ByteArrayInputStream(">x\nACGTACGT\n".getBytes(StandardCharsets.US_ASCII)),3,null,new MerquryFastx.Limits(100,100,4,10,100,100),CHECK));assertThrows(IllegalStateException.class,()->MerquryBoundary.calculate(List.of(bin(1,2)),()->{throw new IllegalStateException("cancel");}));}
    @Test void differenceAndIntersectionKeepLeftMultiplicity(){var a=Map.of("AAA",7L,"AAC",2L);var b=Map.of("AAA",1L,"ACT",9L);assertEquals(Map.of("AAC",2L),MerquryAssessment.difference(a,b,3,CHECK));assertEquals(Map.of("AAA",7L),MerquryAssessment.intersect(a,b,3,CHECK));assertEquals(8,MerquryAssessment.unionSum(List.of(a,b),3,CHECK).get("AAA"));}
    @Test void qvUsesOccurrencesButCompletenessUsesSolidDistinct(){var result=MerquryAssessment.assess(Map.of("AAA",3L,"AAC",2L),Map.of("AAA",1L,"ACT",4L),3,2,CHECK);assertEquals(4,result.quality().assemblyOnlyOccurrences());assertEquals(5,result.quality().totalOccurrences());assertEquals(1,result.completeness().solidReadDistinct());assertEquals(100,result.completeness().percent());assertEquals(3,result.assemblyOnlyExtraOccurrences());}
    @Test void sourceNonfiniteArithmeticRemainsVisible(){var result=MerquryAssessment.assess(Map.of("AAA",1L),Map.of("AAA",1L),3,1,CHECK);assertEquals(Double.POSITIVE_INFINITY,result.quality().qv());assertTrue(Double.isNaN(result.completeness().percent()));}
    @Test void countOverflowFails(){assertThrows(ArithmeticException.class,()->MerquryAssessment.unionSum(List.of(Map.of("AAA",Long.MAX_VALUE),Map.of("AAA",1L)),3,CHECK));}
    @Test void absentDuplicationClassesAreExplicitlyUndefined(){var result=MerquryAssessment.falseDuplications(List.of(new MerquryAssessment.Spectrum("1",3,7)),CHECK);assertEquals(4,result.cutoff());assertFalse(result.sourceWellDefined());assertEquals(List.of("2","3","4",">4"),result.absentSourceFields());assertTrue(Double.isNaN(result.percentage()));}
    @Test void duplicationModeTiesUseFirstAscendingMultiplicity(){var bins=new ArrayList<MerquryAssessment.Spectrum>();bins.add(new MerquryAssessment.Spectrum("1",5,10));bins.add(new MerquryAssessment.Spectrum("1",3,10));for(String label:List.of("2","3","4",">4"))bins.add(new MerquryAssessment.Spectrum(label,2,1));var result=MerquryAssessment.falseDuplications(bins,CHECK);assertEquals(4,result.cutoff());assertTrue(result.sourceWellDefined());assertEquals(100.0*4/14,result.percentage());}
    @Test void sourceAwkPeakComparisonUsesDoubleCounts(){var bins=new ArrayList<MerquryAssessment.Spectrum>();bins.add(new MerquryAssessment.Spectrum("1",3,9007199254740992L));bins.add(new MerquryAssessment.Spectrum("1",5,9007199254740993L));for(String label:List.of("2","3","4",">4"))bins.add(new MerquryAssessment.Spectrum(label,2,1));assertEquals(4,MerquryAssessment.falseDuplications(bins,CHECK).cutoff());}
    @Test void largeCountReportsMatchSixUnmodifiedOriginalExecutions()throws Exception {
        var bins=new ArrayList<MerquryAssessment.Spectrum>();String base="/org/jlinalg/omics/merqury/";
        try(var in=MerquryNativeTest.class.getResourceAsStream(base+"false-duplication-large.hist")){assertNotNull(in);var lines=new String(in.readAllBytes(),StandardCharsets.US_ASCII).lines().toList();for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t");bins.add(new MerquryAssessment.Spectrum(f[0],Long.parseLong(f[1]),Long.parseLong(f[2])));}}
        var expected=MerquryAssessment.falseDuplications(bins,CHECK);assertEquals(18148148016L,expected.consideredDistinct());
        for(String engine:List.of("current","mawk"))try(var in=MerquryNativeTest.class.getResourceAsStream(base+"false-duplication-large-"+engine+".report.tsv")) {
            assertNotNull(in);var rows=new String(in.readAllBytes(),StandardCharsets.US_ASCII).lines().toList();var audit=MerquryTables.falseDuplicationReport(rows,"asm1",expected);assertEquals("86.3946",audit.get("duplication_percent"));var changed=new ArrayList<>(rows);changed.set(1,rows.get(1).replace("86.3946","0.0001"));assertThrows(IOException.class,()->MerquryTables.falseDuplicationReport(changed,"asm1",expected));
        }
    }
    @Test void examplesAreTypedAndUnsupportedNativeEditionFails()throws Exception {
        for(String mode:List.of("evaluate","count-kmers","build-hapmers")) {
            var example=MerquryToolsCli.example(mode);example.put("runtime",java.nio.file.Path.of("example-binding").toAbsolutePath().normalize().toString());
            var inputs=new ArrayList<Object>();for(Object raw:NextflowFiles.list(example.get("inputs"))){var row=NextflowFiles.object(raw);var replacement=new LinkedHashMap<String,Object>();row.forEach((key,value)->replacement.put(key.toString(),value));replacement.put("path",java.nio.file.Path.of("example-input-"+row.get("role")).toAbsolutePath().normalize().toString());inputs.add(replacement);}example.put("inputs",inputs);
            assertEquals(mode,MerquryRequest.parse(example).mode());
        }
        assertThrows(IOException.class,()->MerquryProfile.selection("1.3",MerquryProfile.NATIVE));
    }
    @Test void windowsExecutionIsProminentlySkipped(){if(System.getProperty("os.name").startsWith("Windows")){var err=new ByteArrayOutputStream();int status=MerquryToolsCli.run(new String[]{"--verify","missing","--scratch","new"},System.out,new PrintStream(err));assertEquals(3,status);assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3 DEPENDENT MERQURY ANALYSIS SKIPPED"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("Required action:"));}}
}
