/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Genuine original-program captures plus semantic changes after deliberate resealing. */
final class TwoSampleMrClumpTest {
    @TempDir Path temp;
    private Path fixture(String name)throws IOException {
        Path root=temp.resolve(name);if(Files.exists(root))return root;
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/cli/twosamplemr-clump-0.7.14.zip")) {
            assertNotNull(raw);try(var zip=new ZipInputStream(raw)) {
                int entries=0,selected=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    assertTrue(++entries<3000);String path=entry.getName();if(!path.startsWith(name+"/"))continue;
                    Path target=temp.resolve(path).normalize();assertTrue(target.startsWith(root));assertFalse(entry.isDirectory());
                    byte[] bytes=zip.readAllBytes();total+=bytes.length;assertTrue(total<16000000);Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);selected++;
                }
                assertTrue(selected>30);
            }
        }
        return root;
    }
    @ParameterizedTest @ValueSource(strings={"basic","high-p-linked","single-absent-above-p","single-present-above-p","empty-threshold","multiple-groups","both-p-columns","outcome-p-fallback","default-p","generated-id","duplicate-input","tied-p-reversed-input","missing-p","all-missing-p","monomorphic-missing-genotypes","index-boundary","r2-zero","p-format-near-tie","numeric-looking-ids","p-format-created-tie","empty-input"})
    void genuineOriginalCaptureVerifiesWithoutR(String name)throws IOException {TwoSampleMrClumpCli.verify(fixture(name));}
    @Test void emptySelectionRemainsCompleteWithoutAnEstimate()throws IOException {
        Path root=fixture("empty-threshold");TwoSampleMrClumpCli.verify(root);var audit=read(root.resolve("audit.json"));assertEquals(0,((Number)audit.get("retained_rows")).intValue());assertEquals(true,audit.get("no_statistical_estimate_produced"));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("COMPLETE_EMPTY_SELECTION"));
    }
    @Test void singleAbsentAboveThresholdStillBypassesPlink()throws IOException {
        Path root=fixture("single-absent-above-p");TwoSampleMrClumpCli.verify(root);var audit=read(root.resolve("audit.json"));assertEquals(1,((Number)audit.get("retained_rows")).intValue());assertEquals(0,((Number)audit.get("real_PLINK_calls")).intValue());assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("SOURCE_SINGLE_VARIANT_BYPASS"));
    }
    @Test void sourceNumericIdentifierCoercionIsProminent()throws IOException {
        Path root=fixture("numeric-looking-ids");TwoSampleMrClumpCli.verify(root);assertEquals(true,read(root.resolve("audit.json")).get("source_index_coercion_observed"));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("SOURCE_INDEX_COERCION"));
        var report=read(root.resolve("worker/report.json"));var calls=new ArrayList<Object>(list(report.get("ld_calls"),"calls"));var local=object(calls.get(1),"local");local.put("source_parsed_index_keys",List.of("001","003"));calls.set(1,local);report.put("ld_calls",calls);json(root.resolve("worker/report.json"),report);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void formattingCreatedTieIsRetainedAndWarned()throws IOException {
        Path root=fixture("p-format-created-tie");TwoSampleMrClumpCli.verify(root);assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("SOURCE_FORMAT_CREATED_TIE"));
    }
    @Test void resealedDecisionCannotDropAnOriginalReturnedRow()throws IOException {
        Path root=fixture("basic");change(root.resolve("worker/decisions.tsv"),1,"kept","FALSE");Files.copy(root.resolve("worker/decisions.tsv"),root.resolve("results/decisions.tsv"),StandardCopyOption.REPLACE_EXISTING);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void duplicateInputRowsCannotBeSilentlyCollapsed()throws IOException {
        Path root=fixture("duplicate-input");TwoSampleMrClumpCli.verify(root);Path output=root.resolve("worker/clumped.tsv");var lines=new ArrayList<>(Files.readAllLines(output));assertTrue(lines.size()>2);lines.remove(lines.size()-1);Files.write(output,lines);Files.copy(output,root.resolve("results/clumped.tsv"),StandardCopyOption.REPLACE_EXISTING);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void changedNativeReferenceModeIsInvalid()throws IOException {
        Path root=fixture("basic");byte[] bed=Files.readAllBytes(root.resolve("inputs/reference.bed"));bed[2]=0;Files.write(root.resolve("inputs/reference.bed"),bed);assertThrows(IOException.class,()->TwoSampleMrClumpFiles.prepare(root,read(root.resolve("effective-request.json")),true));
    }
    @Test void unpairedReferenceDimensionsAreInvalid()throws IOException {
        Path root=fixture("basic");Files.writeString(root.resolve("inputs/reference.fam"),"EXTRA EXTRA 0 0 1 -9\n",StandardOpenOption.APPEND);assertThrows(IOException.class,()->TwoSampleMrClumpFiles.prepare(root,read(root.resolve("effective-request.json")),true));
    }
    @ParameterizedTest @ValueSource(strings={"default-p","generated-id","missing-p"})
    void sourceDefaultsRequireExplicitConsent(String name)throws IOException {
        Path root=fixture(name);var request=read(root.resolve("effective-request.json"));request.put("allow_source_defaults",Map.of("generated_ids",false,"pvalues",false));var validated=TwoSampleMrClumpContracts.validate(request);assertThrows(IOException.class,()->TwoSampleMrClumpFiles.prepare(root,validated,true));
    }
    @Test void alteredRawPlinkReportCannotBeResealed()throws IOException {
        Path root=fixture("basic");Files.writeString(root.resolve("worker/ld-calls/002-ld_clump_local/artifacts-001/plink.clumped"),"changed\n",StandardOpenOption.APPEND);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void noExtraResourceFlagsAreSmuggledIntoOriginalCall()throws IOException {
        Path root=fixture("basic");var report=read(root.resolve("worker/report.json"));var calls=new ArrayList<Object>(list(report.get("ld_calls"),"calls"));var local=object(calls.get(1),"local");local.put("plink_resource_flags"," --threads 1");calls.set(1,local);report.put("ld_calls",calls);json(root.resolve("worker/report.json"),report);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void missingOriginalReturnAndUnrestoredBindingAreRefused()throws IOException {
        Path root=fixture("basic");Files.delete(root.resolve("worker/objects/001-clump_data/original-return.rds"));TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
        Path other=fixture("empty-input");var report=read(other.resolve("worker/report.json"));report.put("ieugwasr_bindings_restored",false);json(other.resolve("worker/report.json"),report);TwoSampleMrClumpCli.seal(other);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(other));
    }
    @Test void changedRuntimeEditionIsRefused()throws IOException {
        Path root=fixture("basic");var profile=read(root.resolve("worker/runtime-profile.json"));profile.put("R","4.6.2");json(root.resolve("worker/runtime-profile.json"),profile);TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void requiredRecoveryWarningsCannotBeHidden()throws IOException {
        Path root=fixture("numeric-looking-ids");Files.writeString(root.resolve("WARNINGS.md"),"Everything is fine\n");TwoSampleMrClumpCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrClumpCli.verify(root));
    }
    @Test void historicalProfileSkipsWithoutSubstitution()throws IOException {
        Path root=fixture("basic");var request=read(root.resolve("request.json"));request.put("profile","TwoSampleMR0.5.7");request.put("out",temp.resolve("unknown").toString());Path path=temp.resolve("unknown.json");json(path,request);assertEquals(3,run("--request",path.toString()));assertFalse(Files.exists(temp.resolve("unknown")));
    }
    @Test void missingSoftwareSkipsDependentAnalysisWithRecovery()throws IOException {
        Path root=fixture("basic");var request=read(root.resolve("request.json"));for(String key:TwoSampleMrClumpContracts.INPUTS)request.put(key,root.resolve(text(request,key)).toString());request.put("rscript",temp.resolve("missing-Rscript").toString());Path out=temp.resolve("missing-runtime");request.put("out",out.toString());Path path=temp.resolve("missing.json");json(path,request);assertEquals(3,run("--request",path.toString()));assertEquals(false,read(out.resolve("analysis-status.json")).get("results_admitted"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("SKIP3"));assertFalse(Files.exists(out.resolve("results/clumped.tsv")));
    }
    @Test void replayCannotWriteWithinParentCapture()throws IOException {
        Path root=fixture("basic");String before=FollowupSupport.hash(root.resolve("capture-files.json"));Path path=temp.resolve("nested.json");json(path,Map.of("schema_version",1,"mode","REPLAY","analysis",root.toString(),"out",root.resolve("child").toString()));assertEquals(2,run("--request",path.toString()));assertFalse(Files.exists(root.resolve("child")));assertEquals(before,FollowupSupport.hash(root.resolve("capture-files.json")));TwoSampleMrClumpCli.verify(root);
    }
    private static int run(String... args) {return TwoSampleMrClumpCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private static void change(Path path,int row,String column,String value)throws IOException {var lines=new ArrayList<>(Files.readAllLines(path));var columns=List.of(lines.get(0).split("\t",-1));var cells=lines.get(row).split("\t",-1);cells[columns.indexOf(column)]=value;lines.set(row,String.join("\t",cells));Files.write(path,lines,StandardCharsets.UTF_8);}
}
