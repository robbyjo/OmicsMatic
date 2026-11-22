package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.genetics.PlinkAssociations.*;

class PlinkAssociationsTest {
    @TempDir Path directory;private int serial;
    private static final String LINEAR="ID A1 OMITTED TEST OBS_CT BETA SE T_STAT P ERRCODE\n";
    private static final String LOGISTIC="ID A1 OMITTED TEST OBS_CT OR LOG(OR)_SE Z_STAT P ERRCODE\n";
    private static final Map<String,Alleles> ONE=Map.of("rs1",new Alleles("C","A"));
    private Path file(String data)throws IOException { Path path=directory.resolve("input"+(serial++)+".tsv");Files.writeString(path,data);return path; }
    private Row row(String text,Model model)throws IOException {
        var rows=new ArrayList<Row>();Counts counts=scan(file(text),Engine.PLINK2,model,ONE,100,Limits.defaults(),rows::add);assertEquals(1,counts.rows());return rows.get(0);
    }
    @Test void actualReportedEffectPairControlsTheCoefficientMeaning()throws Exception {
        Row value=row(LINEAR+"rs1 A C ADD 80 -0.5 0.25 -2 0.045 .\n",Model.LINEAR);
        assertEquals("A",value.effectAllele());assertEquals("C",value.otherAllele());assertFalse(value.effectIsBimA1());assertEquals(-.5,value.beta());assertEquals(.25,value.standardError());assertEquals(Status.VALID,value.status());assertTrue(value.pReportable());
        assertThrows(UnsupportedOperationException.class,()->value.rawFields().put("BETA","changed"));
    }
    @Test void finiteUnfinishedFirthFieldsAreUnreportableAndRawValuesSurvive()throws Exception {
        Row value=row(LOGISTIC.replace("TEST","FIRTH? TEST")+"rs1 C A Y ADD 100 2731.98 1.44154 5.48912 4.03944e-8 UNFINISHED\n",Model.FIRTH_FALLBACK);
        assertEquals(Status.UNREPORTABLE_UPSTREAM_ERROR,value.status());assertFalse(value.effectReportable());assertFalse(value.pReportable());assertTrue(value.firth());
        for(double number:new double[]{value.beta(),value.oddsRatio(),value.standardError(),value.statistic(),value.pValue(),value.negativeLog10PValue()})assertTrue(Double.isNaN(number));
        assertEquals("2731.98",value.rawFields().get("OR"));assertEquals("UNFINISHED",value.upstreamError());
    }
    @Test void unresolvedRawZeroPDoesNotBecomeAnExactZeroOrArbitraryFloor()throws Exception {
        Row value=row(LINEAR+"rs1 C A ADD 100 1 0.02 50 0 .\n",Model.LINEAR);
        assertEquals(Status.P_VALUE_OUT_OF_RESOLUTION,value.status());assertTrue(value.effectReportable());assertFalse(value.pReportable());assertTrue(Double.isNaN(value.pValue()));assertTrue(Double.isNaN(value.negativeLog10PValue()));assertEquals("0",value.rawFields().get("P"));
    }
    @Test void originalLogPRemainsUsableBelowTheDoubleProbabilityRange()throws Exception {
        Row value=row(LINEAR.replace(" P "," NEG_LOG10_P ")+"rs1 C A ADD 100 1 .02 50 400 .\n",Model.LINEAR);
        assertEquals(Status.VALID_LOG_P_ONLY,value.status());assertEquals(400,value.negativeLog10PValue());assertTrue(Double.isNaN(value.pValue()));assertTrue(value.pReportable());
        Row tiny=row(LINEAR+"rs1 C A ADD 100 1 .02 50 1e-300 .\n",Model.LINEAR);assertEquals(1e-300,tiny.pValue());assertEquals(300,tiny.negativeLog10PValue());
    }
    @Test void positivePrintedDecimalBelowDoubleRangeKeepsItsLogAndInvalidSignsAreRejected()throws Exception {
        Row value=row(LINEAR+"rs1 C A ADD 100 1 .02 50 1.88901e-465 .\n",Model.LINEAR);
        assertEquals(Status.VALID_LOG_P_ONLY,value.status());assertTrue(value.pReportable());assertTrue(Double.isNaN(value.pValue()));
        assertEquals(465-Math.log10(1.88901),value.negativeLog10PValue(),2*Math.ulp(465.0));assertEquals("1.88901e-465",value.rawFields().get("P"));
        Row subnormal=row(LINEAR+"rs1 C A ADD 100 1 .02 50 1e-323 .\n",Model.LINEAR);assertTrue(subnormal.pValue()>0);assertEquals(323,subnormal.negativeLog10PValue());
        for(String probability:new String[]{"-1e-465","1.0000000000000000000001"})assertEquals(Status.UNREPORTABLE_MISSING_FIELDS,row(LINEAR+"rs1 C A ADD 100 1 .02 50 "+probability+" .\n",Model.LINEAR).status());
        assertEquals(Status.UNREPORTABLE_MISSING_FIELDS,row(LINEAR.replace(" P "," NEG_LOG10_P ")+"rs1 C A ADD 100 1 .02 50 -1e-465 .\n",Model.LINEAR).status());
    }
    @Test void oldRoundedColumnsDoNotInventAnUnreportedStandardError()throws Exception {
        Path path=file("CHR SNP BP A1 TEST NMISS BETA STAT P\n1 rs1 100 C ADD 90 .3823 1.81 .07271\n");var rows=new ArrayList<Row>();
        scan(path,Engine.PLINK1,Model.LINEAR,ONE,100,Limits.defaults(),rows::add);Row value=rows.get(0);assertEquals(Status.VALID_WITHOUT_REPORTED_SE,value.status());assertTrue(Double.isNaN(value.standardError()));assertEquals(.3823,value.beta());
    }
    @Test void originalMissingEstimatesAndZeroObservationsRemainUnreportable()throws Exception {
        var rows=new ArrayList<Row>();scan(file("SNP A1 TEST NMISS OR STAT P\nrs1 C ADD 0 NA NA NA\n"),Engine.PLINK1,Model.LOGISTIC,ONE,100,Limits.defaults(),rows::add);
        assertEquals(Status.UNREPORTABLE_MISSING_FIELDS,rows.get(0).status());assertFalse(rows.get(0).pReportable());
    }
    @Test void oddsRatiosKeepTheirOriginalAlleleAndBecomeLogCoefficients()throws Exception {
        Row value=row(LOGISTIC+"rs1 A C ADD 80 2 .2 3.4657 .0005 .\n",Model.LOGISTIC);
        assertEquals(Math.log(2),value.beta());assertEquals(2,value.oddsRatio());assertFalse(value.effectIsBimA1());
    }
    @Test void unknownDuplicateAndOmittedVariantsRejectAnIncompleteFamily()throws Exception {
        String data=LINEAR+"rs1 C A ADD 80 .5 .25 2 .045 .\n";var rows=new ArrayList<Row>();
        assertThrows(IOException.class,()->scan(file(data),Engine.PLINK2,Model.LINEAR,Map.of("rs1",ONE.get("rs1"),"rs2",ONE.get("rs1")),100,Limits.defaults(),rows::add));assertEquals(1,rows.size());
        assertThrows(IOException.class,()->row(data+data.substring(LINEAR.length()),Model.LINEAR));assertThrows(IOException.class,()->row(data.replace("rs1","unknown"),Model.LINEAR));
    }
    @Test void effectAndOtherAllelesMustMatchTheDeclaredBimPair() {
        assertThrows(IOException.class,()->row(LINEAR+"rs1 G A ADD 80 .5 .25 2 .045 .\n",Model.LINEAR));
        assertThrows(IOException.class,()->row(LINEAR+"rs1 C G ADD 80 .5 .25 2 .045 .\n",Model.LINEAR));
        assertThrows(IllegalArgumentException.class,()->new Alleles("A","a"));
    }
    @Test void sampleCountsAndTestModelCannotBeSilentlyChanged() {
        assertThrows(IOException.class,()->row(LINEAR+"rs1 C A ADD 101 .5 .25 2 .045 .\n",Model.LINEAR));
        assertThrows(IOException.class,()->row(LINEAR+"rs1 C A DOM 80 .5 .25 2 .045 .\n",Model.LINEAR));
        assertThrows(IOException.class,()->row(LOGISTIC.replace("TEST","FIRTH? TEST")+"rs1 C A N ADD 80 2 .2 3 .01 .\n",Model.LOGISTIC));
        assertThrows(IOException.class,()->row(LOGISTIC+"rs1 C A ADD 80 2 .2 3 .01 .\n",Model.FIRTH_FALLBACK));
    }
    @Test void invalidNumbersAreNotPublishedAsValidInference()throws Exception {
        for(String row:new String[]{"rs1 C A ADD 80 Infinity .25 2 .045 .\n","rs1 C A ADD 80 .5 -.25 2 .045 .\n","rs1 C A ADD 80 .5 .25 2 1.1 .\n"})
            assertEquals(Status.UNREPORTABLE_MISSING_FIELDS,row(LINEAR+row,Model.LINEAR).status());
        assertThrows(IOException.class,()->row(LINEAR+"rs1 C A ADD 80 .5 .25 2 not-a-number .\n",Model.LINEAR));
    }
    @Test void malformedHeadersWidthsAndUtf8FailExplicitly()throws Exception {
        assertThrows(IOException.class,()->row(LINEAR.replace("BETA","P")+"rs1 C A ADD 80 .5 .25 2 .045 .\n",Model.LINEAR));
        assertThrows(IOException.class,()->row(LINEAR+"rs1 C A ADD 80 .5 .25 2 .045\n",Model.LINEAR));
        Path bad=file("");Files.write(bad,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->scan(bad,Engine.PLINK2,Model.LINEAR,ONE,100,Limits.defaults(),r->{ }));
    }
    @Test void limitsAndUnsupportedLegacyFirthDoNotReadUnboundedInput()throws Exception {
        Path path=file(LINEAR+"rs1 C A ADD 80 .5 .25 2 .045 .\n");
        assertThrows(IOException.class,()->scan(path,Engine.PLINK2,Model.LINEAR,ONE,100,new Limits(1,1,64),r->{ }));
        Path longLine=file(LINEAR+"X".repeat(1000));assertThrows(IOException.class,()->scan(longLine,Engine.PLINK2,Model.LINEAR,ONE,100,new Limits(2,2000,64),r->{ }));
        assertThrows(IllegalArgumentException.class,()->scan(path,Engine.PLINK1,Model.FIRTH,ONE,100,Limits.defaults(),r->{ }));
    }
}
