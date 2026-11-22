package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlinkQcReportsTest {
    @TempDir Path root;private int counter;
    private Path file(String text)throws Exception{Path path=root.resolve("report"+(counter++));Files.writeString(path,text);return path;}
    @Test void bothOriginalMissingFormatsRetainIntegerOpportunities() throws Exception {
        var allowed=Set.of(new PlinkQcInputs.Id("F1","I1"));
        for(String text:new String[]{"FID IID N_MISS N_GENO F_MISS\nF1 I1 2 94 .02128\n","#FID IID MISSING_CT OBS_CT F_MISS\nF1 I1 2 94 .0212766\n"}){var row=PlinkQcReports.samples(file(text),allowed,true,2,10000,1000).values().iterator().next();assertEquals(2,row.missing());assertEquals(94,row.opportunities());assertEquals(2./94,row.fraction());}
    }
    @Test void subsetsAreAllowedOnlyForFilterDecisionsAndUnknownIdsNeverAre() throws Exception {
        var allowed=Set.of(new PlinkQcInputs.Id("F1","I1"),new PlinkQcInputs.Id("F2","I1"));Path report=file("FID IID N_MISS N_GENO\nF1 I1 0 94\n");assertEquals(1,PlinkQcReports.samples(report,allowed,false,2,10000,1000).size());assertThrows(Exception.class,()->PlinkQcReports.samples(report,allowed,true,2,10000,1000));assertThrows(Exception.class,()->PlinkQcReports.snps(file("V1\nUNKNOWN\n"),Set.of("V1"),10000,1000));assertThrows(Exception.class,()->PlinkQcReports.snps(file("V1\nV1\n"),Set.of("V1"),10000,1000));
    }
    @Test void frequencyCountsUseTheirOwnAllelesAndNeverAssumeBimA1() throws Exception {
        for(String text:new String[]{"SNP A1 A2 C1 C2\nV1 C A 126 124\n","#ID REF ALT ALT_CTS OBS_CT\nV1 A C 126 250\n"}){var value=PlinkQcReports.frequencies(file(text),Set.of("V1"),2,10000,1000).get("V1");assertEquals(250,value.observations());assertEquals(124,value.minorCount());assertEquals(.496,value.minorFrequency());}
        assertThrows(Exception.class,()->PlinkQcReports.frequencies(file("ID REF ALT ALT_CTS OBS_CT\nV1 A C 251 250\n"),Set.of("V1"),2,10000,1000));
    }
    @Test void originalP1WithNoCallsIsNotCreditedAsInformativeHwe() throws Exception {
        for(String text:new String[]{"SNP TEST A1 A2 GENO P\nV1 ALL(NP) C A 0/0/0 1\n","#ID A1 AX HOM_A1_CT HET_A1_CT TWO_AX_CT P\nV1 A C 0 0 0 1\n"}){var value=PlinkQcReports.hwe(file(text),Set.of("V1"),2,10000,1000).get("V1");assertEquals("1",value.rawP());assertEquals(0,value.called());assertFalse(value.informative());}
    }
    @Test void reportedZeroAndDegenerateSingletonHaveDistinctHweMeaning() throws Exception {
        var rows=PlinkQcReports.hwe(file("ID A1 AX HOM_A1_CT HET_A1_CT TWO_AX_CT P\nV1 A C 0 100 0 0\nV2 A C 99 1 0 1\n"),Set.of("V1","V2"),3,10000,1000);assertTrue(rows.get("V1").informative());assertTrue(rows.get("V1").unresolvedZero());assertTrue(rows.get("V2").degenerate());assertFalse(rows.get("V2").unresolvedZero());
    }
    @Test void incompleteMalformedAndOversizedReportsFail() throws Exception {
        for(String value: new String[]{"-0.01","1.01","Infinity","bad"})assertThrows(Exception.class,()->PlinkQcReports.hwe(file("ID A1 AX HOM_A1_CT HET_A1_CT TWO_AX_CT P\nV1 A C 1 2 3 "+value+"\n"),Set.of("V1"),2,10000,1000));
        assertThrows(Exception.class,()->PlinkQcReports.variants(file("ID MISSING_CT OBS_CT\nV1 3 2\n"),Set.of("V1"),2,10000,1000));assertThrows(Exception.class,()->PlinkQcReports.hwe(file("SNP TEST A1 A2 GENO P\nV1 UNAFF A C 1/2/3 .1\n"),Set.of("V1"),2,10000,1000));assertThrows(Exception.class,()->PlinkQcReports.variants(file("ID MISSING_CT OBS_CT\n"),Set.of("V1"),2,10000,1000));assertThrows(Exception.class,()->PlinkQcReports.table(file("ID\n"+"a".repeat(1001)),2,10000,1000));
    }
    @Test void originalMidpColumnMustMatchDeclaredReportMode() throws Exception {
        Path modern=file("ID A1 AX HOM_A1_CT HET_A1_CT TWO_AX_CT MIDP\nV1 A C 6 29 28 .782139\n");assertEquals(".782139",PlinkQcReports.hwe(modern,Set.of("V1"),2,10000,1000,"midp").get("V1").rawP());assertThrows(Exception.class,()->PlinkQcReports.hwe(modern,Set.of("V1"),2,10000,1000,"exact"));Path exact=file("ID A1 AX HOM_A1_CT HET_A1_CT TWO_AX_CT P\nV1 A C 6 29 28 1\n");assertThrows(Exception.class,()->PlinkQcReports.hwe(exact,Set.of("V1"),2,10000,1000,"midp"));Path legacy=file("SNP TEST A1 A2 GENO P\nV1 ALL(NP) A C 6/29/28 .7821\n");assertEquals(".7821",PlinkQcReports.hwe(legacy,Set.of("V1"),2,10000,1000,"midp").get("V1").rawP());
    }
}
