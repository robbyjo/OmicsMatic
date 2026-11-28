/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NfcoreRnasplice104PostfitTest {
    @TempDir Path temp;
    private static final String HEADER="Name\tLength\tEffectiveLength\tTPM\tNumReads\n";
    private Path table(String name,String rows)throws IOException{return Files.writeString(temp.resolve(name),HEADER+rows,StandardCharsets.UTF_8);}
    @Test void checksFullQuantifierPopulationAndPrintedRoundingWithoutChangingIdentifiers()throws Exception{
        Path first=table("first.sf","tx b\t100\t75.1\t333333.333333\t3.5\ntx-a\t120\t95.1\t666666.666667\t7.0\n");Map<String,Object> report=NfcoreRnasplice104Postfit.quantificationFile(first,2);assertEquals(2,report.get("targets"));assertEquals("1000000.000000",report.get("TPM_total"));Path reordered=table("reordered.sf","tx-a\t120\t95.1\t666666.666667\t7.0\ntx b\t100\t75.1\t333333.333333\t3.5\n");assertEquals(report.get("target_population_sha256"),NfcoreRnasplice104Postfit.quantificationFile(reordered,2).get("target_population_sha256"));assertTrue(Files.readString(first).contains("tx b\t"));
    }
    @Test void retainsZeroAbundanceAndSignedZeroWithoutInventingReads()throws Exception{
        var report=NfcoreRnasplice104Postfit.quantificationFile(table("zero.sf","x\t100\t-0.0\t0\t-0.0\n"),1);assertEquals(true,report.get("zero_abundance_unassessable"));assertThrows(IOException.class,()->NfcoreRnasplice104Postfit.quantificationFile(table("impossible.sf","x\t100\t0\t0\t1\n"),1));
    }
    @Test void refusesNonfiniteValuesDuplicateTargetsAndTruncatedPopulations()throws Exception{
        for(String value:List.of("NaN","Inf","-1","1e99999"))assertThrows(IOException.class,()->NfcoreRnasplice104Postfit.quantificationFile(table("bad-"+Math.abs(value.hashCode())+".sf","x\t100\t10\t"+value+"\t1\n"),10));assertThrows(IOException.class,()->NfcoreRnasplice104Postfit.quantificationFile(table("duplicate.sf","x\t100\t10\t500000\t1\nx\t100\t10\t500000\t1\n"),2));assertThrows(IOException.class,()->NfcoreRnasplice104Postfit.quantificationFile(table("capacity.sf","x\t100\t10\t500000\t1\ny\t100\t10\t500000\t1\n"),1));assertThrows(IOException.class,()->NfcoreRnasplice104Postfit.quantificationFile(table("total.sf","x\t100\t10\t1.0000\t1\n"),1));
    }
    @Test void verifiesActualPairedOrderWithoutSortingOrModelSubstitution()throws Exception{
        var expected=List.of(new NfcoreRnasplice104Request.Pair("a","c"),new NfcoreRnasplice104Request.Pair("b","d"));assertEquals(List.of(expected.get(1),expected.get(0)),NfcoreRnasplice104Files.pairOrder(List.of("b","a"),List.of("d","c"),expected,"A-B"));assertThrows(IOException.class,()->NfcoreRnasplice104Files.pairOrder(List.of("b","a"),List.of("c","d"),expected,"A-B"));assertThrows(IOException.class,()->NfcoreRnasplice104Files.pairOrder(List.of("a"),List.of("c","d"),expected,"A-B"));
    }
    @Test void routesTypedCommandAndMakesPrerequisiteActionsProminent()throws Exception{
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();assertEquals(0,NfcoreToolsCli.run(new String[]{"RNASPLICE-1.0.4","--help"},new PrintStream(output),new PrintStream(errors)));String help=output.toString(StandardCharsets.UTF_8);for(String required:List.of("--request","--resume","--verify","SKIP3","FAIL2","Hashes establish integrity, not authenticity"))assertTrue(help.contains(required),required);assertEquals(2,NfcoreRnasplice104ToolsCli.run(new String[]{"--request","x","--request","y"},new PrintStream(output),new PrintStream(errors)));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("NO RESULT ADMITTED"));
    }
    @Test void refusesEarlierSuccessWhenANewerRetainedAttemptExists()throws Exception{
        Path root=Files.createDirectory(temp.resolve("session"));Files.createDirectories(root.resolve("attempts/000001"));Files.createDirectories(root.resolve("attempts/000002"));NextflowFiles.writeJson(root.resolve("latest.json"),Map.of("attempt","000001","status","COMPLETED"));IOException failure=assertThrows(IOException.class,()->NfcoreRnasplice104Bundle.verify(root));assertTrue(failure.getMessage().contains("newer retained attempt"));
    }
    @Test void failedHelpDeliveryCannotReportSuccess()throws Exception{
        try(var broken=new PrintStream(new java.io.OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("Output unavailable");}})){assertEquals(2,NfcoreToolsCli.run(new String[]{"RNASPLICE-1.0.4","--help"},broken,broken));}
    }
}
