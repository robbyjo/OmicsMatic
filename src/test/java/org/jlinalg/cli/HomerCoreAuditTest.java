package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.sequence.HomerEnrichment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerCoreAuditTest {
    @TempDir Path root;
    @Test void knownRetainsOriginalCoarseStatisticsAndSeparateNativeLogTail()throws Exception{
        var data=HomerCoreFixture.data(root,"known");Path work=root.resolve("work");Files.writeString(work.resolve("original.stdout"),HomerCoreFixture.knownHeader()+HomerCoreFixture.knownRow());
        assertEquals(1,HomerCoreAudit.known(work,data,HomerEnrichment.Statistic.HYPERGEOMETRIC));String result=Files.readString(work.resolve("results.tsv"));
        assertTrue(result.contains("1e0\t-0.182\t0.000\t1\t50.00%\t1\t50.00%"));assertTrue(result.contains("model α"));assertTrue(result.contains("native_log_p"));
    }
    @Test void knownChecksBinomialRatherThanReusingHypergeometricValue()throws Exception{
        var data=HomerCoreFixture.data(root,"known");Path work=root.resolve("work");Files.writeString(work.resolve("original.stdout"),HomerCoreFixture.knownHeader()+HomerCoreFixture.knownRow().replace("-0.182","-0.288"));
        assertEquals(1,HomerCoreAudit.known(work,data,HomerEnrichment.Statistic.BINOMIAL));
    }
    @Test void missingKnownMotifsCannotBeAcceptedAsACompleteResult()throws Exception{
        var data=HomerCoreFixture.data(root,"known");Path work=root.resolve("work");Files.writeString(work.resolve("original.stdout"),HomerCoreFixture.knownHeader());assertThrows(IOException.class,()->HomerCoreAudit.known(work,data,HomerEnrichment.Statistic.HYPERGEOMETRIC));
    }
    @Test void knownRejectsWrongPopulationsCountsPercentsPAndDuplicateRows()throws Exception{
        String full=HomerCoreFixture.knownHeader()+HomerCoreFixture.knownRow();int index=0;
        for(String text:new String[]{full.replace("total=2","total=3"),full.replace("\t1\t50.00%","\t0\t50.00%"),full.replace("50.00%","60.00%"),full.replace("1e0","0.833333"),full+HomerCoreFixture.knownRow(),full.replace("-0.182","NaN"),full.replace("-0.182","-1.000")}){
            Path dir=root.resolve("case"+index++);Files.createDirectory(dir);var data=HomerCoreFixture.data(dir,"known");Path work=dir.resolve("work");Files.writeString(work.resolve("original.stdout"),text);assertThrows(IOException.class,()->HomerCoreAudit.known(work,data,HomerEnrichment.Statistic.HYPERGEOMETRIC));
        }
    }
    @Test void findChecksCompleteUniqueSitesAndKeepsForwardReverseText()throws Exception{
        var data=HomerCoreFixture.data(root,"find");Path work=root.resolve("work");Files.writeString(work.resolve("original.stdout"),HomerCoreFixture.findRows());
        assertEquals(2,HomerCoreAudit.find(work,data));String result=Files.readString(work.resolve("results.tsv"));assertTrue(result.contains("\t4\t6\t-\t5\tTT\t2.711670\t"));assertTrue(result.contains("α"));
    }
    @Test void findRejectsOmittedDuplicateUnknownAndAlteredSites()throws Exception{
        String full=HomerCoreFixture.findRows();int index=0;
        for(String text:new String[]{full.lines().findFirst().orElseThrow()+"\n",full+full,full.replace("M000000001","M999999999"),full.replace("\tTT\t","\tAA\t"),full.replace("\t5\t","\t4\t"),full.replace("2.711670","2.800000"),full.replace("\t-\t","\t?\t")}){
            Path dir=root.resolve("case"+index++);Files.createDirectory(dir);var data=HomerCoreFixture.data(dir,"find");Path work=dir.resolve("work");Files.writeString(work.resolve("original.stdout"),text);assertThrows(IOException.class,()->HomerCoreAudit.find(work,data));
        }
    }
    @Test void validEmptyOriginalFindRetainsAllZeroHitInputRecords()throws Exception{
        var options=HomerCoreFixture.options(root.resolve("source"),"find");Files.writeString(Path.of(options.get("targets")),">only-N\nNNNN\n>short\nA\n");Path work=root.resolve("work");Files.createDirectory(work);
        var data=HomerCoreData.stage(options,"find",work,HomerCoreFixture.LIMITS);Files.writeString(work.resolve("original.stdout"),"");assertEquals(0,HomerCoreAudit.find(work,data));assertEquals(2,data.targetCount());assertEquals(1,Files.readAllLines(work.resolve("results.tsv")).size());
    }
}
