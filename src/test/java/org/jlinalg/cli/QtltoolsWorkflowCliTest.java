/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

class QtltoolsWorkflowCliTest {
    @TempDir Path temp;
    private int sequence;
    private record Invocation(int code,String stdout,String stderr) { }

    @Test void nativeRunVerifiesCompleteCountsMetadataAndProminentOrderWarning()throws Exception {
        var request=nativeRequest("native",true);Path root=Path.of(request.get("out").toString());Invocation result=invoke(request);
        assertEquals(0,result.code,result.stderr);assertDoesNotThrow(()->QtltoolsWorkflowCli.verify(root));
        assertEquals("-20 -11 0\n-10 -1 0\n0 9 0\n10 19 0\n",Files.readString(root.resolve("outputs/result")));
        assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("FDENSITY SOURCE-ORDER WARNING: 1 QTL rows"));
        assertFalse(Files.exists(root.resolve("worker/owned.json")));assertFalse(Files.exists(root.resolve("worker/runtime-capture.json")));
        var audit=read(root.resolve("output-audit.json"));assertEquals(1L,((Number)object(audit.get("fdensity_metadata"),"metadata").get("source_order_guarded_qtl_rows")).longValue());
    }
    @Test void nativeReplayRerunsInNewOutputAndPreservesParentExactly()throws Exception {
        var request=nativeRequest("replay",false);assertEquals(0,invoke(request).code);Path parent=Path.of(request.get("out").toString());String seal=FollowupSupport.hash(parent.resolve("capture-files.json"));
        Path destination=temp.resolve("replayed");var replay=Map.<String,Object>of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",destination.toString());Invocation result=invoke(replay);
        assertEquals(0,result.code,result.stderr);assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));
        assertArrayEquals(Files.readAllBytes(parent.resolve("outputs/result")),Files.readAllBytes(destination.resolve("outputs/result")));
        assertDoesNotThrow(()->QtltoolsWorkflowCli.verify(parent));assertDoesNotThrow(()->QtltoolsWorkflowCli.verify(destination));assertTrue(Files.exists(destination.resolve("replay-source.json")));
    }
    @Test void globalCommandExecutesAndVerifiesTheRealNativeWorkflow()throws Exception {
        var request=nativeRequest("global",false);Path path=temp.resolve("global-request.json");json(path,request);var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"qtltools-tools","--request",path.toString()},new PrintStream(out),new PrintStream(err)),err.toString(StandardCharsets.UTF_8));
        assertEquals(0,JLinAlgCli.run(new String[]{"qtltools-tools","--verify",request.get("out").toString()},new PrintStream(out),new PrintStream(err)),err.toString(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Verified saved QTLtools capture"));
    }
    @TestFactory Stream<DynamicTest> resealedCorruptionsAreRefusedByScientificAndSourceChecks() {
        Map<String,Consumer<Path>> changes=new LinkedHashMap<>();
        changes.put("native-count",root->unchecked(()->Files.writeString(root.resolve("outputs/result"),"-20 -11 0\n-10 -1 0\n0 9 999\n10 19 0\n")));
        changes.put("native-metadata",root->unchecked(()->{var m=read(root.resolve("manifest.json"));var n=object(m.get("native_metadata"),"metadata");n.put("candidate_overlaps",999);m.put("native_metadata",n);json(root.resolve("manifest.json"),m);}));
        changes.put("undeclared-output",root->unchecked(()->Files.writeString(root.resolve("outputs/extra"),"unrelated\n")));
        changes.put("warnings",root->unchecked(()->Files.writeString(root.resolve("WARNINGS.md"),"No warnings\n")));
        changes.put("executing-class-profile",root->unchecked(()->json(root.resolve("source/java-class-profile.json"),Map.of())));
        changes.put("source-worker",root->unchecked(()->Files.writeString(root.resolve("source/owned_worker.py"),"# modified worker\n")));
        changes.put("request-operation",root->unchecked(()->{var m=read(root.resolve("effective-request.json"));m.put("operation","MBV");json(root.resolve("effective-request.json"),m);}));
        return changes.entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey(),()->{
            var request=nativeRequest("tamper-"+entry.getKey(),false);assertEquals(0,invoke(request).code);Path root=Path.of(request.get("out").toString());entry.getValue().accept(root);QtltoolsWorkflowFiles.seal(root);assertThrows(Exception.class,()->QtltoolsWorkflowCli.verify(root));
        }));
    }
    @Test void missingInputSkipsWithExactRemedyAndNoAdmittedResult()throws Exception {
        var request=nativeRequest("missing",false);Path missing=temp.resolve("not-provided.bed");var inputs=object(request.get("inputs"),"inputs");inputs.put("qtl",Map.of("path",missing.toString(),"format","BED"));request.put("inputs",inputs);Invocation result=invoke(request);
        assertEquals(3,result.code);Path root=Path.of(request.get("out").toString());var status=read(root.resolve("analysis-status.json"));assertEquals(false,status.get("results_admitted"));String warning=Files.readString(root.resolve("WARNINGS.md"));assertTrue(warning.contains(missing.toString()));assertTrue(warning.contains("SKIP3"));assertTrue(warning.contains("installation is prohibited"));assertThrows(Exception.class,()->QtltoolsWorkflowCli.verify(root));
    }
    @Test void invalidCoordinatesRetainFailureAndRawInputs()throws Exception {
        var request=nativeRequest("coordinates",false);var inputs=object(request.get("inputs"),"inputs");Path qtl=Path.of(text(object(inputs.get("qtl"),"qtl"),"path"));Files.writeString(qtl,"1\t5\t5\tq\tg\t+\n");byte[] raw=Files.readAllBytes(qtl);Invocation result=invoke(request);
        assertEquals(2,result.code);Path root=Path.of(request.get("out").toString());assertArrayEquals(raw,Files.readAllBytes(root.resolve("inputs/qtl.bed")));assertEquals(false,read(root.resolve("manifest.json")).get("results_admitted"));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("FAIL2"));
    }
    @Test void existingAndNestedOutputAreRefusedWithoutChangingParent()throws Exception {
        var request=nativeRequest("existing",false);assertEquals(0,invoke(request).code);Path root=Path.of(request.get("out").toString());String seal=FollowupSupport.hash(root.resolve("capture-files.json"));assertEquals(2,invoke(request).code);assertEquals(seal,FollowupSupport.hash(root.resolve("capture-files.json")));
        var nested=new LinkedHashMap<>(request);nested.put("out",root.resolve("nested/output").toString());assertEquals(2,invoke(nested).code);assertFalse(Files.exists(root.resolve("nested")));assertDoesNotThrow(()->QtltoolsWorkflowCli.verify(root));
    }
    @TestFactory Stream<DynamicTest> explicitScientificAndEditionRestrictionsAreEnforced() {
        Map<String,Consumer<Map<String,Object>>> changes=new LinkedHashMap<>();
        changes.put("unknown-edition",r->r.put("edition","1.2.27"));
        changes.put("native-cis-forbidden",r->r.put("operation","CIS_NOMINAL"));
        changes.put("acknowledgement-required",r->r.put("acknowledge_original_handling",false));
        changes.put("unused-native-runtime",r->r.put("runtime",Map.of("executable","/tmp/tool")));
        changes.put("unknown-flags",r->r.put("flags",List.of("--repair")));
        changes.put("zero-window",r->r.put("parameters",Map.of("window",0,"bin",10)));
        changes.put("zero-bin",r->r.put("parameters",Map.of("window",20,"bin",0)));
        changes.put("bin-capacity",r->r.put("limits",Map.of("maximum_bins",1)));
        changes.put("negative-capacity",r->r.put("limits",Map.of("maximum_output_bytes",-1)));
        return changes.entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey(),()->{var request=nativeRequest("contract-"+entry.getKey(),false);entry.getValue().accept(request);assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));}));
    }
    @Test void legacyMismatchesAreNotRenamedIntoSupportedAnalysisOptions()throws Exception {
        var request=mapping("CIS_PERMUTE","1.1");request.put("parameters",Map.of("permutations",List.of(1000,10000)));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));
        request.put("parameters",Map.of("permutations",100,"grouping","grp"));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));
        request.put("parameters",Map.of("permutations",100,"standard_error",true));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));
        var sample=mapping("TRANS_SAMPLE","1.3.1");sample.put("parameters",Map.of("sample",100,"chunk",List.of(1,2)));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(sample));
    }
    @Test void oldQuanTpmAndHashFlagsAreRefused()throws Exception {
        var request=mapping("QUAN","1.1");request.put("inputs",Map.of("gtf",Map.of("path","annotation.gtf","format","GTF"),"alignments",List.of(Map.of("path","reads.bam","format","BAM"))));request.put("parameters",Map.of("sample_names",List.of("s"),"tpm",true));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));
        request.put("parameters",Map.of("sample_names",List.of("s"),"filename_hash","none"));assertThrows(IOException.class,()->QtltoolsWorkflowContracts.validate(request));
    }
    @Test void sourceIntegerDivisionAndNaSentinelsRemainEditionSpecific()throws Exception {
        Path root=temp.resolve("audit");Files.createDirectories(root.resolve("outputs"));var request=mapping("MBV","1.1");request.put("inputs",Map.of("genotypes",Map.of("path","g.vcf","format","VCF"),"alignment",Map.of("path","a.bam","format","BAM")));request=QtltoolsWorkflowContracts.validate(request);
        String header="SampleID n_geno_missing n_het_total n_hom_total n_het_covered n_hom_covered n_het_consistent n_hom_consistent perc_het_consistent perc_hom_consistent n_het_in_ase\n";
        Files.writeString(root.resolve("outputs/result"),header+"s 0 18 22 18 22 5 13 0 0 0\n");QtltoolsWorkflowAudit.outputs(root,request);
        Files.writeString(root.resolve("outputs/result"),header+"s 0 18 22 18 22 5 13 0.277778 0.590909 0\n");var legacy=request;assertThrows(IOException.class,()->QtltoolsWorkflowAudit.outputs(root,legacy));
        request.put("edition","1.2");assertDoesNotThrow(()->QtltoolsWorkflowAudit.outputs(root,legacy));
        Files.writeString(root.resolve("outputs/result"),header+"s 0 0 22 0 22 0 13 -nan 0.590909 0\n");var modern=QtltoolsWorkflowAudit.outputs(root,request);assertTrue(modern.toString().contains("undefined_source_percentage_cells=1"));
    }
    @Test void declaredCompressionIsNotSilentlyChanged()throws Exception {
        var request=nativeRequest("compression",false);var inputs=object(request.get("inputs"),"inputs");Path path=temp.resolve("compressed.bed.gz");try(var gzip=new java.util.zip.GZIPOutputStream(Files.newOutputStream(path))){gzip.write("1\t0\t1\tq\tg\t+\n".getBytes(StandardCharsets.UTF_8));}inputs.put("qtl",Map.of("path",path.toString(),"format","BED"));request.put("inputs",inputs);Invocation result=invoke(request);assertEquals(2,result.code);assertTrue(result.stderr.contains("Compression differs from declared format"));
        request.put("out",temp.resolve("compression-correct").toString());inputs.put("qtl",Map.of("path",path.toString(),"format","BED_GZ"));request.put("inputs",inputs);result=invoke(request);assertEquals(0,result.code,result.stderr);
    }
    @Test void zeroVariantPermutationRowsAreClassifiedAsNullWithoutInventedPvalues()throws Exception {
        Path root=temp.resolve("null-cis");Files.createDirectories(root.resolve("outputs"));Files.writeString(root.resolve("outputs/result"),"p 1 1 1 + 0"+" NA".repeat(14)+"\n");var request=QtltoolsWorkflowContracts.validate(mapping("CIS_PERMUTE","1.3.1"));
        assertEquals("null-no-cis-variants",QtltoolsWorkflowAudit.outputs(root,request).get("result_kind"));
        Files.writeString(root.resolve("outputs/result"),"p 1 1 1 + 0"+" NA".repeat(13)+" 1\n");assertThrows(IOException.class,()->QtltoolsWorkflowAudit.outputs(root,request));
    }
    @Test void noCoveredGenotypesProduceExplicitNullConcordance()throws Exception {
        Path root=temp.resolve("null-mbv");Files.createDirectories(root.resolve("outputs"));String header="SampleID n_geno_missing n_het_total n_hom_total n_het_covered n_hom_covered n_het_consistent n_hom_consistent perc_het_consistent perc_hom_consistent n_het_in_ase\n";
        Files.writeString(root.resolve("outputs/result"),header+"s 0 18 22 0 0 0 0 -nan -nan 0\n");var request=mapping("MBV","1.2");request.put("inputs",Map.of("genotypes",Map.of("path","g.vcf","format","VCF"),"alignment",Map.of("path","a.bam","format","BAM")));var effective=QtltoolsWorkflowContracts.validate(request);
        var audited=QtltoolsWorkflowAudit.outputs(root,effective);assertEquals("null-no-covered-genotype-concordance",audited.get("result_kind"));assertTrue(audited.toString().contains("undefined_source_percentage_cells=2"));
    }
    private Map<String,Object> nativeRequest(String name,boolean guard)throws IOException {
        Path folder=temp.resolve(name);Files.createDirectories(folder);Path qtl=folder.resolve("qtl.bed"),annotation=folder.resolve("annotations.bed");Files.writeString(qtl,"1\t0\t1\tq\tg\t+\n");Files.writeString(annotation,guard?"1\t1000\t1001\n1\t0\t2\n":"1\t0\t2\n");var request=mapping("FDENSITY","1.3.1");request.put("engine","native");request.remove("runtime");request.put("out",folder.resolve("capture").toString());request.put("inputs",Map.of("qtl",Map.of("path",qtl.toString(),"format","BED"),"annotations",Map.of("path",annotation.toString(),"format","BED")));request.put("parameters",Map.of("window",20,"bin",10));return request;
    }
    private Map<String,Object> mapping(String operation,String edition){var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","RUN");request.put("edition",edition);request.put("engine","original");request.put("operation",operation);request.put("out",temp.resolve("unused").toString());request.put("runtime",Map.of("executable","/exact/permitted/QTLtools"));request.put("acknowledge_original_handling",true);request.put("inputs",Map.of("genotypes",Map.of("path","genotypes.vcf.gz","format","VCF_GZ"),"phenotypes",Map.of("path","phenotypes.bed.gz","format","BED_GZ")));request.put("parameters",Map.of());return request;}
    private Invocation invoke(Map<String,Object> request)throws IOException {Path path=temp.resolve("request-"+(sequence++)+".json");json(path,request);var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=QtltoolsWorkflowCli.run(new String[]{"--request",path.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Invocation(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @FunctionalInterface private interface IO { void run()throws Exception; }
    private static void unchecked(IO operation){try{operation.run();}catch(Exception error){throw new IllegalStateException(error);}}
}
