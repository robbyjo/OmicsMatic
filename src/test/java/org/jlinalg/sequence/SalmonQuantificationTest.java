/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonQuantificationTest {
    private static final String HEADER="Name\tLength\tEffectiveLength\tTPM\tNumReads\n";
    private static final String FIXTURE="/org/jlinalg/sequence/salmon-quantification/original-quantification.zip";
    private static final String FIXTURE_SHA="72487ad43cdb5bb6921c75a8e4d317287980298d89e21448abd8b660121f1904";
    private static final List<SalmonQuantification.Target> TWO=List.of(new SalmonQuantification.Target("a.1",1000),new SalmonQuantification.Target("b.2",500));
    private static final String EQUAL=HEADER+"a.1\t1000\t100.000\t500000.000000\t50.000\nb.2\t500\t100.000\t500000.000000\t50.000\n";

    private static Map<String,byte[]> fixtures() throws Exception {
        byte[] raw;try(var input=SalmonQuantificationTest.class.getResourceAsStream(FIXTURE)){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals(FIXTURE_SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var entries=new HashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                assertFalse(entry.isDirectory());assertFalse(entry.getName().contains(".."));byte[] data=zip.readNBytes(1048577);assertTrue(data.length<=1048576);assertNull(entries.put(entry.getName(),data));
            }
        }
        assertEquals(82,entries.size());return entries;
    }
    private static List<SalmonQuantification.Target> targets(byte[] raw) {
        return new String(raw,StandardCharsets.UTF_8).lines().skip(1).map(line->{String[] cells=line.split("\t");return new SalmonQuantification.Target(cells[0],Long.parseLong(cells[1]));}).toList();
    }
    static Stream<Arguments> originalCases() throws Exception {
        return new String(fixtures().get("cases.tsv"),StandardCharsets.UTF_8).lines().skip(1).map(line->{String[] c=line.split("\t");assertEquals("0",c[6]);return Arguments.of(c[0],Long.parseLong(c[3]),Integer.parseInt(c[4]),Integer.parseInt(c[5]));});
    }
    private static SalmonQuantification.Table parse(String text,List<SalmonQuantification.Target> targets,int precision) throws IOException {
        return SalmonQuantification.read(new StringReader(text),targets,precision,1048576,16384,()->{});
    }
    private static SalmonQuantification.Audit audit(String text,long assigned) throws IOException {
        return SalmonQuantification.audit(parse(text,TWO,3),assigned,()->{});
    }

    @ParameterizedTest @MethodSource("originalCases")
    void allOriginalTablesPreserveEveryFieldAndMeetRoundingBounds(String path,long assigned,int precision,int expectedRows) throws Exception {
        var source=fixtures();String text=new String(source.get(path+"/quant.sf"),StandardCharsets.UTF_8);
        var table=parse(text,targets(source.get("targets.tsv")),precision);assertEquals(expectedRows,table.rows().size());assertEquals(text.length(),table.characters());
        String rebuilt=HEADER+table.rows().stream().map(r->r.target().name()+"\t"+r.target().length()+"\t"+r.effectiveLength().text()+"\t"+r.tpm().text()+"\t"+r.estimatedFragments().text()+"\n").collect(java.util.stream.Collectors.joining());
        assertEquals(text,rebuilt);var receipt=SalmonQuantification.audit(table,assigned,()->{});assertEquals(expectedRows,receipt.rows().size());
        assertTrue(receipt.minimumCountSum().compareTo(BigDecimal.valueOf(assigned))<=0);assertTrue(receipt.maximumCountSum().compareTo(BigDecimal.valueOf(assigned))>=0);
        assertTrue(receipt.floatingPointTpmAllowance().compareTo(new BigDecimal("0.000001"))<0);
        for(int i=0;i<expectedRows;i++) {
            var row=table.rows().get(i);var checked=receipt.rows().get(i);assertEquals(row.target().name(),checked.target());
            assertTrue(checked.minimumCompatibleTpm().compareTo(row.tpm().value().add(new BigDecimal("0.0000005")))<=0);
            assertTrue(checked.maximumCompatibleTpm().compareTo(row.tpm().value().subtract(new BigDecimal("0.0000005")))>=0);
        }
    }
    @Test void effectiveLengthsRatherThanRawLengthsDetermineTpm() throws Exception {
        String text=HEADER+"a.1\t1000\t100.000\t888888.888889\t80.000\nb.2\t500\t200.000\t111111.111111\t20.000\n";
        var receipt=audit(text,100);assertEquals(0,receipt.rows().get(0).printedPointTpm().compareTo(new BigDecimal(800000000).divide(new BigDecimal(900),new java.math.MathContext(80,java.math.RoundingMode.FLOOR))));
        assertEquals(new BigDecimal("100.000"),receipt.printedCountSum());
    }
    @Test void printedCountTotalsNeedNotEqualTheIntegerMassExactly() throws Exception {
        String text=HEADER+"a.1\t1000\t100.000\t499996.000000\t49.999\nb.2\t500\t100.000\t500004.000000\t50.000\n";
        var result=audit(text,100);assertEquals(new BigDecimal("99.999"),result.printedCountSum());assertTrue(result.rowsRequiringRounding()>0);
    }
    @Test void nonzeroTpmCanCoexistWithACountThatRoundsToZero() throws Exception {
        String text=HEADER+"a.1\t1000\t1.000\t40.000000\t0.000\nb.2\t500\t1.000\t999960.000000\t1.000\n";
        var table=parse(text,TWO,3);var result=SalmonQuantification.audit(table,1,()->{});assertEquals("0.000",table.rows().get(0).estimatedFragments().text());
        assertTrue(result.rows().get(0).roundingRequired());assertEquals(0,result.rows().get(0).printedPointTpm().signum());
    }
    @Test void coarsePrecisionDoesNotInventPointCountsWhenEveryCountRoundsToZero() throws Exception {
        var names=List.of(new SalmonQuantification.Target("a",100),new SalmonQuantification.Target("b",100),new SalmonQuantification.Target("c",100));
        String text=HEADER+"a\t100\t1\t333333.333333\t0\nb\t100\t1\t333333.333333\t0\nc\t100\t1\t333333.333333\t0\n";
        var result=SalmonQuantification.audit(parse(text,names,0),1,()->{});assertEquals(3,result.rowsRequiringRounding());assertTrue(result.rows().stream().allMatch(r->r.printedPointTpm()==null));
    }
    @Test void originalNegativeZeroRemainsVisible() throws Exception {
        String text=HEADER+"a.1\t1000\t100.000\t-0.000000\t-0.000\nb.2\t500\t100.000\t1000000.000000\t1.000\n";
        var table=parse(text,TWO,3);assertEquals("-0.000",table.rows().get(0).estimatedFragments().text());assertEquals("-0.000000",table.rows().get(0).tpm().text());
        assertEquals(2,SalmonQuantification.audit(table,1,()->{}).rows().size());
    }
    @Test void assignedMassIsDistinctFromProcessedInputCount() throws Exception {
        assertNotNull(audit(EQUAL,100));assertThrows(IOException.class,()->audit(EQUAL,150));
    }
    @Test void totalTpmCannotHideAnAlteredIndividualNormalization() {
        assertThrows(IOException.class,()->audit(EQUAL.replaceFirst("500000.000000","501000.000000").replace("b.2\t500\t100.000\t500000.000000","b.2\t500\t100.000\t499000.000000"),100));
    }
    @Test void wrongGlobalTpmSumFails() {
        assertThrows(IOException.class,()->audit(EQUAL.replace("500000.000000","400000.000000"),100));
    }
    @Test void highPrecisionDoesNotGetAnArbitraryAbsoluteTolerance() throws Exception {
        String text=EQUAL.replace("100.000\t","100.000000000000\t").replace("50.000\n","50.000000000000\n");
        var table=parse(text,TWO,12);assertNotNull(SalmonQuantification.audit(table,100,()->{}));
        String changed=text.replaceFirst("500000.000000","500000.000100").replace("b.2\t500\t100.000000000000\t500000.000000","b.2\t500\t100.000000000000\t499999.999900");
        assertThrows(IOException.class,()->SalmonQuantification.audit(parse(changed,TWO,12),100,()->{}));
    }
    @Test void insufficientEffectiveLengthPrecisionIsUnreportableAndPreserved() throws Exception {
        var table=parse(EQUAL.replace("100.000\t500000","0.000\t500000"),TWO,3);assertEquals("0.000",table.rows().get(0).effectiveLength().text());
        var error=assertThrows(IOException.class,()->SalmonQuantification.audit(table,100,()->{}));assertTrue(error.getMessage().contains("sigDigits"));
    }
    @Test void subnormalNormalizationDenominatorIsNotCertified() throws Exception {
        String length="1"+"0".repeat(308)+".000";String text=EQUAL.replace("100.000\t",length+"\t");
        var error=assertThrows(IOException.class,()->audit(text,100));assertTrue(error.getMessage().contains("subnormal"));
    }
    @Test void zeroAssignedMassIsUnreportableRatherThanAConfirmedNegativeFinding() {
        var error=assertThrows(IOException.class,()->audit(EQUAL.replace("500000.000000","0.000000").replace("50.000\n","0.000\n"),0));
        assertTrue(error.getMessage().contains("not biological absence"));
    }
    @Test void completeArithmeticDoesNotOverrideAnOriginalProcessFailure() throws Exception {
        var source=fixtures();var status=new Properties();status.load(new ByteArrayInputStream(source.get("failed-control/status.properties")));
        assertEquals("-11",status.getProperty("original_exit_code"));assertEquals("false",status.getProperty("complete_quantification_admitted"));
        var table=parse(new String(source.get("failed-control/quant.sf"),StandardCharsets.UTF_8),targets(source.get("failed-control/targets.tsv")),3);
        assertEquals(4,SalmonQuantification.audit(table,6000,()->{}).rows().size()); // Necessary arithmetic checks are not run admission.
    }
    @ParameterizedTest @ValueSource(strings={"51.000","-1.000","NaN","Infinity","5e1","50.00","50.0000","+50.000","050.000"," 50.000"})
    void changedOrMalformedCountsAreNotAdmitted(String count) {
        String text=EQUAL.replace("50.000\n",count+"\n");assertThrows(IOException.class,()->audit(text,100));
    }
    @ParameterizedTest @ValueSource(strings={"a.1\t999","wrong\t1000","b.2\t1000","a.1\t01000"})
    void exactProtectedTargetIdentityAndLengthAreRequired(String changed) {
        assertThrows(IOException.class,()->parse(EQUAL.replace("a.1\t1000",changed),TWO,3));
    }
    @Test void completeOrderedFamilyCannotBeReorderedTruncatedOrExtended() {
        List<String> lines=EQUAL.lines().toList();String swapped=lines.get(0)+"\n"+lines.get(2)+"\n"+lines.get(1)+"\n";
        for(String text:List.of(swapped,HEADER+lines.get(1)+"\n",EQUAL+"extra\t1\t1.000\t0.000000\t0.000\n",EQUAL+"\n"))
            assertThrows(IOException.class,()->parse(text,TWO,3));
    }
    @Test void originalHeaderAndTerminalNewlinesAreRequired() {
        for(String text:List.of("\ufeff"+EQUAL,EQUAL.replaceFirst("NumReads","Counts"),EQUAL.substring(0,EQUAL.length()-1),EQUAL.replace("\n","\r\n")))
            assertThrows(IOException.class,()->parse(text,TWO,3));
    }
    @Test void exactCharacterCapacityCannotAdmitAPrefix() throws Exception {
        var input=new TrackedReader(EQUAL);assertThrows(IOException.class,()->SalmonQuantification.read(input,TWO,3,EQUAL.length()-1,200,()->{}));assertTrue(input.closed);
        assertEquals(EQUAL.length(),SalmonQuantification.read(new StringReader(EQUAL),TWO,3,EQUAL.length(),200,()->{}).characters());
    }
    @Test void oversizedLineCannotBypassCompleteFileCapacity() {
        String text=EQUAL.replace("50.000\n","5"+"0".repeat(150)+".000\n");
        assertThrows(IOException.class,()->SalmonQuantification.read(new StringReader(text),TWO,3,10000,100,()->{}));
    }
    @Test void cancellationCannotReturnAPartialTable() {
        var input=new TrackedReader(EQUAL);var checks=new AtomicInteger();assertThrows(CancellationException.class,()->SalmonQuantification.read(input,TWO,3,1000,200,()->{if(checks.incrementAndGet()==2)throw new CancellationException();}));assertTrue(input.closed);
    }
    @Test void arithmeticCancellationCannotReturnAPartialReceipt() throws Exception {
        var table=parse(EQUAL,TWO,3);var checks=new AtomicInteger();assertThrows(CancellationException.class,()->SalmonQuantification.audit(table,100,()->{if(checks.incrementAndGet()==3)throw new CancellationException();}));
    }
    @Test void completePopulationAndPrecisionAreDefensivelyCopiedAndValidated() throws Exception {
        var table=parse(EQUAL,TWO,3);assertThrows(UnsupportedOperationException.class,()->table.rows().clear());
        var receipt=SalmonQuantification.audit(table,100,()->{});assertThrows(UnsupportedOperationException.class,()->receipt.rows().clear());
        var rows=new ArrayList<>(table.rows());var copy=new SalmonQuantification.Table(rows,3,table.characters());rows.clear();assertEquals(2,copy.rows().size());
        assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Table(List.of(table.rows().get(0),table.rows().get(0)),3,1));
        assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Table(table.rows(),2,1));
        assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Printed("1.000",BigDecimal.ONE));
    }
    @Test void invalidDeclaredNamesAndLengthsCannotBecomeAProtectedReference() {
        for(String name:List.of("","a\tb","a\nb","\ud800"))assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Target(name,1));
        assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Target("a",0));assertThrows(IllegalArgumentException.class,()->new SalmonQuantification.Target("a",0x100000000L));
        assertThrows(IOException.class,()->parse(EQUAL,List.of(TWO.get(0),TWO.get(0)),3));
    }
    private static final class TrackedReader extends StringReader {
        boolean closed;TrackedReader(String text){super(text);}@Override public void close(){closed=true;super.close();}
    }
}
