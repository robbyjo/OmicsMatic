/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Edition contracts, meaningful refusals and offline replay of 41 actual original-package fits. */
final class SusieWorkflowTest {
    private static final String FIXTURE="/org/jlinalg/omics/susie-workflow/reference-20261119.zip";
    private static final String FIXTURE_SHA="be7ca436a6d7478503e25b42f583b52c5ff0d1dbc3ce6a92d92100b234924235";
    private static final Map<String,byte[]> PAYLOAD=fixture();
    private static final Map<String,Object> INDEX=index();
    private static Map<String,Object> map(Object... values){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)r.put((String)values[i],values[i+1]);return r;}
    private static Path home(String name)throws IOException {
        Path base=Path.of(System.getProperty("omicsmatic.susie.workflow.test_root","build/susie-workflow-tests")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,name+"-");
    }
    private static Map<String,Object> request(Path home)throws IOException {
        Files.writeString(home.resolve("variants.tsv"),"variant_id\tz\n a \t6\na\t2\n",StandardCharsets.UTF_8);
        Files.writeString(home.resolve("matrix.tsv"),"variant_id\t a \ta\n a \t1\t0.1\na\t0.1\t1\n",StandardCharsets.UTF_8);
        return map("schema_version",1,"mode","RUN","profile","SuSiE-0.14.2-R4.6.1","model","RSS_Z","sample_size",60,
            "ld_origin","reference","variants","variants.tsv","matrix","matrix.tsv","out",home.resolve("analysis").toString(),
            "rscript",home.resolve("missing-Rscript").toString(),"r_libraries",List.of(home.toString()));
    }
    private static int run(Path home,Map<String,Object> request,ByteArrayOutputStream error)throws IOException {
        Path path=home.resolve("request-"+UUID.randomUUID()+".json");json(path,request);
        try(var out=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8)){
            return SusieWorkflowCli.run(new String[]{"--request",path.toString()},out,err);
        }
    }
    private static int run(Path home,Map<String,Object> request)throws IOException{return run(home,request,new ByteArrayOutputStream());}
    @TestFactory Stream<DynamicTest> originalPackageCapturesAcrossThreeEditions()throws IOException {
        return list(INDEX.get("cases"),"cases").stream().map(raw->objectUnchecked(raw)).map(row->DynamicTest.dynamicTest(row.get("name").toString(),()->{
            Path root=restore(row);int code=((Number)row.get("project_exit")).intValue();
            if(code==0)assertDoesNotThrow(()->SusieWorkflowCli.verify(root));
            else {assertEquals(2,code);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));}
        }));
    }
    @Test void legacyRssDoesNotInventTheModernSampleSizeInterface()throws IOException {
        Path h=home("legacy");var r=request(h);r.put("profile","SuSiE-0.11.92-R4.6.1");
        assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));r.remove("sample_size");
        var e=SusieWorkflowContracts.validate(r);assertEquals(50.0,e.get("prior_variance"));assertFalse(e.containsKey("standardize"));assertFalse(e.containsKey("estimate_residual_variance"));
    }
    @Test void modernRssRequiresExplicitNOrAnExplicitMissingNChoice()throws IOException {
        Path h=home("modern-n");var r=request(h);r.remove("sample_size");assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
        r.put("sample_size","not-provided");var e=SusieWorkflowContracts.validate(r);assertTrue(SusieWorkflowContracts.noSampleRss(e));
        r.put("standardize",false);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
    }
    @Test void betaInterfacesAreEditionSpecific()throws IOException {
        Path h=home("beta");var r=request(h);r.put("model","SUFFICIENT_BETA_SE");r.put("variance_y",1);
        assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));r.put("profile","SuSiE-0.11.92-R4.6.1");r.put("estimate_residual_variance",false);
        assertDoesNotThrow(()->SusieWorkflowContracts.validate(r));r.put("model","RSS_BETA_SE");assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
    }
    @Test void historical01143IsSkippedWithoutAnAdjacentVersionSubstitute()throws IOException {
        Path h=home("01143");var r=request(h);r.put("profile","SuSiE-0.11.43-R4.6.1");var error=new ByteArrayOutputStream();
        assertEquals(3,run(h,r,error));assertTrue(error.toString(StandardCharsets.UTF_8).contains("0.11.43"));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void referenceLdDoesNotSilentlyEnableResidualVarianceEstimation()throws IOException {
        Path h=home("reference");var r=request(h);r.put("estimate_residual_variance",true);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
        r.put("ld_origin","in-sample");assertDoesNotThrow(()->SusieWorkflowContracts.validate(r));
    }
    @Test void sourceOptionsThatWouldBeIgnoredOrDuplicatedAreRefused()throws IOException {
        Path h=home("ignored");var r=request(h);r.put("prior_variance",50);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
        r.remove("prior_variance");r.put("intercept",true);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
    }
    @Test void unknownFieldsDoNotRunUserRCode()throws IOException {
        Path h=home("unknown");var r=request(h);r.put("r_expression","system('never')");assertEquals(2,run(h,r));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void fractionalIterationsAndZeroCapacitiesAreRefused()throws IOException {
        Path h=home("capacity-schema");var r=request(h);r.put("maximum_iterations",1.5);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
        r.remove("maximum_iterations");r.put("maximum_matrix_entries",0);assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
    }
    @Test void sufficientStatisticsRequireExplicitYtyAndN()throws IOException {
        Path h=home("suff-required");var r=request(h);r.put("model","SUFFICIENT");r.remove("ld_origin");assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
        r.put("yty",1);r.remove("sample_size");assertThrows(IOException.class,()->SusieWorkflowContracts.validate(r));
    }
    @Test void exactSpacesArePreservedBeforeMissingRuntimeSkip()throws IOException {
        Path h=home("identity");var r=request(h);assertEquals(3,run(h,r));
        String prepared=Files.readString(h.resolve("analysis/worker/variants.tsv"));assertTrue(prepared.contains("\n a \t6.0\na\t2.0\n"));
        assertTrue(Files.readString(h.resolve("analysis/WARNINGS.md")).contains("SKIP3"));assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("results_admitted"));
    }
    @Test void missingInputHasProminentSkipAndNoResult()throws IOException {
        Path h=home("missing-input");var r=request(h);r.put("variants","unavailable.tsv");var error=new ByteArrayOutputStream();
        assertEquals(3,run(h,r,error));assertTrue(error.toString(StandardCharsets.UTF_8).contains("Required permitted input"));assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("results_admitted"));
    }
    @Test void existingOutputIsPreserved()throws IOException {
        Path h=home("existing");var r=request(h);Path output=Files.createDirectory(h.resolve("analysis"));Files.writeString(output.resolve("KEEP.txt"),"preserve");
        assertEquals(2,run(h,r));assertEquals("preserve",Files.readString(output.resolve("KEEP.txt")));try(var files=Files.list(output)){assertEquals(1,files.count());}
    }
    @Test void duplicateVariantIdsAreRefused()throws IOException {
        Path h=home("duplicate");var r=request(h);Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\na\t6\na\t2\n");assertEquals(2,run(h,r));
    }
    @Test void differentlyOrderedLdIdsAreNotSilentlyReordered()throws IOException {
        Path h=home("matrix-order");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"variant_id\ta\t a \na\t1\t0.1\n a \t0.1\t1\n");assertEquals(2,run(h,r));
    }
    @Test void asymmetricLdIsNotAveraged()throws IOException {
        Path h=home("asymmetry");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"variant_id\t a \ta\n a \t1\t0.1\na\t0.2\t1\n");assertEquals(2,run(h,r));
    }
    @Test void indefiniteLdIsRefusedBeforeAWorkerStarts()throws IOException {
        Path h=home("indefinite");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"variant_id\t a \ta\n a \t1\t2\na\t2\t1\n");assertEquals(2,run(h,r));assertFalse(read(h.resolve("analysis/manifest.json")).containsKey("owned_worker_pid"));
    }
    @Test void singularPositiveSemidefiniteLdIsAccepted()throws IOException {
        Path h=home("singular");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"variant_id\t a \ta\n a \t1\t1\na\t1\t1\n");assertEquals(3,run(h,r));
    }
    @Test void nonunitLdDiagonalIsNotRescaled()throws IOException {
        Path h=home("diagonal");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"variant_id\t a \ta\n a \t2\t0.1\na\t0.1\t1\n");assertEquals(2,run(h,r));
    }
    @Test void nonfiniteAndUnderflowedDecimalInputsAreRefused()throws IOException {
        for(String value:List.of("NaN","Inf","1e9999","1e-9999"," 6 ","0x1p2")){
            Path h=home("numeric");var r=request(h);Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\n a \t"+value+"\na\t2\n");assertEquals(2,run(h,r),value);
        }
    }
    @Test void pveOverflowIsNotTurnedIntoAZeroZScore()throws IOException {
        Path h=home("pve");var r=request(h);Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\n a \t1e200\na\t2\n");assertEquals(2,run(h,r));
    }
    @Test void positivePriorTermsLostByNormalizationAreRefused()throws IOException {
        Path h=home("prior-loss");var r=request(h);Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\tprior_weight\n a \t6\t1e-320\na\t2\t1e300\n");assertEquals(2,run(h,r));
    }
    @Test void explicitZeroPriorWeightsRemainAllowed()throws IOException {
        Path h=home("zero-prior");var r=request(h);Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\tprior_weight\n a \t6\t0\na\t2\t1\n");assertEquals(3,run(h,r));
    }
    @Test void impossibleJointCrossProductsAreRefused()throws IOException {
        Path h=home("joint");var r=request(h);r.put("model","SUFFICIENT");r.remove("ld_origin");r.put("yty",1);
        Files.writeString(h.resolve("variants.tsv"),"variant_id\txty\n a \t2\na\t2\n");assertEquals(2,run(h,r));
    }
    @Test void workAndInputByteLimitsRefuseWithoutSubsampling()throws IOException {
        Path h=home("work");var r=request(h);r.put("maximum_work",1);assertEquals(2,run(h,r));
        Path b=home("bytes");var s=request(b);s.put("maximum_input_bytes",1);assertEquals(2,run(b,s));
    }
    @Test void interruptionDoesNotAdmitAPartialInputCapture()throws IOException {
        Path h=home("interrupt");var r=request(h);Thread.currentThread().interrupt();
        try{assertEquals(2,run(h,r));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
    }
    @Test void malformedUtf8RequestIsRefused()throws IOException {
        Path h=home("utf8");Path path=h.resolve("bad.json");Files.write(path,new byte[]{'{','"','x','"',':','"',(byte)0xc3,'(','"','}'});
        assertEquals(2,SusieWorkflowCli.run(new String[]{"--request",path.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
    }
    @Test void forgedAuthenticationClaimIsRefused()throws IOException {
        Path root=restore(caseNamed("0.14.2-individual-base"));var seal=read(root.resolve("capture-files.json"),16_000_000);seal.put("authentication",true);json(root.resolve("capture-files.json"),seal);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));
    }
    @Test void changedPackagedSourceIsRefusedEvenAfterResealing()throws IOException {
        Path root=restore(caseNamed("0.14.2-individual-base"));Files.writeString(root.resolve("source/reference/0.14.2/R/susie.R"),"# changed\n");SusieWorkflowCli.seal(root);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));
    }
    @Test void invalidPipIsRefusedEvenAfterResealing()throws IOException {
        Path root=restore(caseNamed("0.14.2-individual-base"));Path path=root.resolve("worker/variant-results.tsv");List<String> lines=Files.readAllLines(path);String[] fields=lines.get(1).split("\t",-1);fields[2]="1.5";lines.set(1,String.join("\t",fields));Files.write(path,lines,StandardCharsets.UTF_8);SusieWorkflowCli.seal(root);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));
    }
    @Test void incompleteCredibleSetIsRefusedEvenAfterResealing()throws IOException {
        Path root=restore(caseNamed("0.14.2-individual-base"));Path path=root.resolve("worker/credible-sets.tsv");List<String> lines=Files.readAllLines(path);assertTrue(lines.size()>1);lines.remove(lines.size()-1);Files.write(path,lines,StandardCharsets.UTF_8);SusieWorkflowCli.seal(root);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));
    }
    @Test void changedPreparedIdentitiesAreRefusedEvenAfterResealing()throws IOException {
        Path root=restore(caseNamed("0.14.2-individual-base"));Path path=root.resolve("worker/variants.tsv");Files.writeString(path,Files.readString(path).replace("\n a \n","\nchanged\n"));SusieWorkflowCli.seal(root);assertThrows(IOException.class,()->SusieWorkflowCli.verify(root));
    }
    @Test void replayRequiresItsOriginalPermittedRuntime()throws IOException {
        Path h=home("replay");Path prior=restore(caseNamed("0.14.2-individual-base"));var r=map("schema_version",1,"mode","REPLAY","analysis",prior.toString(),"out",h.resolve("replay").toString(),"rscript",h.resolve("missing-rscript").toString(),"r_libraries",List.of(h.toString()));
        assertEquals(3,run(h,r));assertEquals(false,read(h.resolve("replay/analysis-status.json")).get("results_admitted"));assertTrue(Files.exists(h.resolve("replay/replay-source.json")));
    }
    private static Map<String,Object> caseNamed(String name)throws IOException {for(Object row:list(INDEX.get("cases"),"cases")){var c=object(row,"case");if(name.equals(c.get("name")))return c;}throw new IOException("Fixture case missing");}
    private static Path restore(Map<String,Object> row)throws IOException {
        Path root=home("capture-"+row.get("name"));for(var entry:object(row.get("files"),"files").entrySet()){
            Path relative=Path.of(entry.getKey());if(relative.isAbsolute()||!relative.normalize().equals(relative)||entry.getKey().contains("\\")||entry.getKey().contains(":"))throw new IOException("Unsafe fixture capture member");
            var wanted=object(entry.getValue(),"pin");byte[] raw=PAYLOAD.get("blobs/"+wanted.get("sha256"));if(raw==null||raw.length!=((Number)wanted.get("bytes")).intValue()||!SusieWorkflowRuntime.sha(raw).equals(wanted.get("sha256")))throw new IOException("Fixture blob changed");
            Path target=root.resolve(relative);if(!target.normalize().startsWith(root))throw new IOException("Fixture path escapes output");Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);
        }return root;
    }
    private static Map<String,Object> objectUnchecked(Object value){try{return object(value,"case");}catch(IOException error){throw new UncheckedIOException(error);}}
    private static Map<String,Object> index(){try{return object(SimpleJson.parse(new String(PAYLOAD.get("INDEX.json"),StandardCharsets.UTF_8)),"fixture index");}catch(IOException error){throw new UncheckedIOException(error);}}
    private static Map<String,byte[]> fixture(){
        try(InputStream input=SusieWorkflowTest.class.getResourceAsStream(FIXTURE)){
            if(input==null)throw new IOException("Required original SuSiE fixture absent");byte[] archive=input.readAllBytes();if(!SusieWorkflowRuntime.sha(archive).equals(FIXTURE_SHA))throw new IOException("Original SuSiE fixture hash changed");
            var result=new LinkedHashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(archive),StandardCharsets.UTF_8)){java.util.zip.ZipEntry e;long size=0;while((e=zip.getNextEntry())!=null){if(e.isDirectory()||result.containsKey(e.getName())||!e.getName().equals("INDEX.json")&&!e.getName().matches("blobs/[0-9a-f]{64}"))throw new IOException("Invalid fixture archive member");byte[] raw=zip.readNBytes(16_000_001);size+=raw.length;if(raw.length>16_000_000||size>128_000_000)throw new IOException("Fixture capacity exceeded");result.put(e.getName(),raw);}}
            return Collections.unmodifiableMap(result);
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
}
