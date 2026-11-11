/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class MashWorkflowAuditTest {
    @TempDir static Path temporary;
    private static Path cached;
    private static final Runnable CHECK=()->{};
    private static final MashWorkflowAudit.Limits LIMITS=MashWorkflowAudit.Limits.defaults();
    private static Path corpus()throws Exception {
        if(cached!=null)return cached;
        Path root=temporary.resolve("corpus");Files.createDirectory(root);
        try(var input=MashWorkflowAuditTest.class.getResourceAsStream("mashr-workflow/original-controls.zip")){
            assertNotNull(input,"Missing original workflow corpus");byte[] raw=input.readAllBytes();assertEquals(5817297,raw.length);
            assertEquals("28187ad99751081f608473ffe6a24f2a7e61081e9eb5bdddfddc6de361239d42",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            int count=0;long total=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                    assertFalse(entry.isDirectory());String name=entry.getName();assertFalse(name.contains("\\")||name.contains("..")||name.contains(":"));
                    Path path=root.resolve(name).normalize();assertTrue(path.startsWith(root));byte[] value=zip.readNBytes(8_388_609);assertTrue(value.length<=8_388_608);total+=value.length;assertTrue(total<=134_217_728);assertTrue(++count<=5000);
                    Files.createDirectories(path.getParent());assertFalse(Files.exists(path));Files.write(path,value);
                }
            }assertEquals(2650,count);
        }
        cached=root;return root;
    }
    @TestFactory Stream<DynamicTest> completeOriginalWorkflows()throws Exception {
        Path root=corpus();Object parsed=SimpleJson.parse(Files.readString(root.resolve("ORIGINAL-CONTROLS.json")));
        assertInstanceOf(Map.class,parsed);Object value=((Map<?,?>)parsed).get("protocols");assertInstanceOf(List.class,value);var protocols=(List<?>)value;assertEquals(26,protocols.size());
        var tests=new ArrayList<DynamicTest>();
        for(Object item:protocols){assertInstanceOf(Map.class,item);var protocol=(Map<?,?>)item;String name=(String)protocol.get("name");int expected=((Number)protocol.get("expected_exit_code")).intValue();
            tests.add(DynamicTest.dynamicTest(name,()->{
                Path folder=root.resolve(name);if(expected!=0){assertThrows(IOException.class,()->MashWorkflowAudit.audit(folder.resolve("inputs"),folder.resolve("outputs"),LIMITS,CHECK));return;}
                var result=MashWorkflowAudit.audit(folder.resolve("inputs"),folder.resolve("outputs"),LIMITS,CHECK);
                int training=((Number)protocol.get("expected_training_rows")).intValue(),targets=((Number)protocol.get("expected_target_rows")).intValue(),components=((Number)protocol.get("expected_components")).intValue();
                assertEquals(training,result.trainingRows());assertEquals(targets,result.targetRows());assertEquals(3,result.conditions());
                assertEquals(components,result.components());assertEquals((long)training*components+(long)targets*(25+2L*components)+5,result.checkedNumericValues());assertTrue(result.trainingRelativeDualGap()<=1e-6);
                if(name.equals("numeric-feature-identities"))assertEquals("0001",Files.readAllLines(folder.resolve("outputs/training-ids.tsv")).get(0));
                System.out.println(name+"\tchecked="+result.checkedNumericValues()+"\tmaximum_absolute_error="+result.maximumAbsoluteError()+"\tmaximum_scaled_error="+result.maximumScaledError());
            }));
        }return tests.stream();
    }
    private static void copy(Path source,Path target)throws IOException {
        try(var files=Files.walk(source)){for(Path path:files.toList()){Path dest=target.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(dest);else Files.copy(path,dest);}}
    }
    private static Path altered(String name)throws Exception {
        Path result=temporary.resolve(name),original=corpus().resolve("canonical_pca_ed-em-Rcpp");Files.createDirectory(result);
        copy(original.resolve("inputs"),result.resolve("inputs"));copy(original.resolve("outputs"),result.resolve("outputs"));return result;
    }
    private static IOException rejects(Path root){return assertThrows(IOException.class,()->MashWorkflowAudit.audit(root.resolve("inputs"),root.resolve("outputs"),LIMITS,CHECK));}
    private static void alterCell(Path path)throws IOException {
        var lines=new ArrayList<>(Files.readAllLines(path,StandardCharsets.UTF_8));String[] fields=lines.get(0).split("\t");fields[0]=Double.toString(Double.parseDouble(fields[0])+.25);lines.set(0,String.join("\t",fields));Files.write(path,lines,StandardCharsets.UTF_8);
    }
    @Test void alteredPosteriorCannotBeAdmittedByReusingTheOriginalProcessStatus()throws Exception {
        Path root=altered("mean-change");alterCell(root.resolve("outputs/posterior/mean.tsv"));assertTrue(rejects(root).getMessage().contains("NUMERIC FAIL2"));
    }
    @Test void everyTrainingComponentLikelihoodIsAudited()throws Exception {
        Path root=altered("training-change");alterCell(root.resolve("outputs/training-component-loglik.tsv"));assertTrue(rejects(root).getMessage().contains("training"));
    }
    @Test void reorderedTargetsCannotPassACompleteNumericComparison()throws Exception {
        Path root=altered("target-order");Path path=root.resolve("outputs/target-ids.tsv");var lines=new ArrayList<>(Files.readAllLines(path));java.util.Collections.swap(lines,0,1);Files.write(path,lines);assertTrue(rejects(root).getMessage().contains("identities"));
    }
    @Test void trainingAndTargetMembershipMustMatchTheOriginalRequest()throws Exception {
        Path root=altered("population-change");Path path=root.resolve("inputs/populations.tsv");String text=Files.readString(path);assertTrue(text.contains("gene-120\t0\t0\t1"));Files.writeString(path,text.replace("gene-120\t0\t0\t1","gene-120\t1\t0\t0"));assertTrue(rejects(root).getMessage().contains("population"));
    }
    @Test void conditionIdentitiesAreCheckedAgainstTheOriginalMatrix()throws Exception {
        Path root=altered("condition-change");Path path=root.resolve("inputs/effects.tsv");Files.writeString(path,Files.readString(path).replace("cell-A","different-cell"));assertTrue(rejects(root).getMessage().contains("condition identities"));
    }
    @Test void truncatedPosteriorWeightsCannotReturnPrefixSuccess()throws Exception {
        Path root=altered("weights-truncated");Path path=root.resolve("outputs/posterior/weights.tsv");var lines=Files.readAllLines(path);Files.write(path,lines.subList(0,lines.size()-1));assertTrue(rejects(root).getMessage().contains("Truncated complete matrix"));
    }
    @Test void unexplainedExtraCovarianceRowsAreRejected()throws Exception {
        Path root=altered("covariance-extra");Path path=root.resolve("outputs/posterior/covariance-column-major.tsv");String text=Files.readString(path);Files.writeString(path,text+text.lines().findFirst().orElseThrow()+"\n");assertTrue(rejects(root).getMessage().contains("Unexpected rows"));
    }
    @Test void missingModelComponentsAreNotSilentlyDropped()throws Exception {
        Path root=altered("missing-component");Files.delete(root.resolve("outputs/model/component-3.tsv"));assertTrue(rejects(root).getMessage().contains("Missing/nonregular"));
    }
    @Test void repeatedPriorLabelsRemainIndexedRatherThanMergedOrDropped()throws Exception {
        Path root=altered("repeated-prior-labels");Path path=root.resolve("outputs/model/component-names.tsv");int count=Files.readAllLines(path).size();
        Files.write(path,java.util.Collections.nCopies(count,"identity.1"));
        var result=MashWorkflowAudit.audit(root.resolve("inputs"),root.resolve("outputs"),LIMITS,CHECK);assertEquals(count,result.components());assertEquals(140L*count+505,result.checkedNumericValues());
    }
    @Test void aSuccessKeywordInsideAnErrorStatusDoesNotAdmitAnAnalysis()throws Exception {
        Path root=altered("counterfeit-status");Files.writeString(root.resolve("outputs/source-status.tsv"),"FAIL2: previously saw \"ORIGINAL_PROCESS_COMPLETE_UNAUDITED\"\n");assertTrue(rejects(root).getMessage().contains("No complete original-process result"));
    }
    @Test void zeroSeCannotBeHiddenByUnchangedCapturedPosteriorTables()throws Exception {
        Path root=altered("original-zero-se");Path path=root.resolve("inputs/standard-errors.tsv");var lines=new ArrayList<>(Files.readAllLines(path));String[] fields=lines.get(1).split("\t");fields[1]="0";lines.set(1,String.join("\t",fields));Files.write(path,lines);assertTrue(rejects(root).getMessage().contains("invalid original SE"));
    }
    @Test void capacityFailureDoesNotAdmitASelectedSubset()throws Exception {
        Path root=corpus().resolve("canonical-fixed-Rcpp");var limits=new MashWorkflowAudit.Limits(1000,3,100,1,268435456,100000000000L,1e-8,2e-8);
        assertThrows(IOException.class,()->MashWorkflowAudit.audit(root.resolve("inputs"),root.resolve("outputs"),limits,CHECK));
    }
    @Test void cancellationNeverReturnsPartialCoverage()throws Exception {
        Path root=corpus().resolve("canonical-fixed-Rcpp");assertThrows(CancellationException.class,()->MashWorkflowAudit.audit(root.resolve("inputs"),root.resolve("outputs"),LIMITS,()->{throw new CancellationException("stop");}));
    }
}
