/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnprelateRequestTest {
    @TempDir Path directory;
    private Map<String,Object> base() {
        return SnprelateAudit.map("schema_version",1,"version","1.46.0","mode","ld-pruning","bfile","cohort","runtime_profile","private/runtime-profile.json","out","attempt",
            "layout","snp-first","autosomes_only",true,"remove_monomorphic",true,"maf",0.005,"missing_rate",0.01,"threads",1,
            "options",SnprelateAudit.map("method","composite","start","random.f500","seed",20261110,"window_bp",500000,"window_variants",null,"ld_threshold",0.2),
            "limits",SnprelateAudit.map("input_bytes",1000000,"output_bytes",10000000,"output_files",150,"samples",100,"variants",1000,"rows",10000,
                "pair_variant_operations",1000000,"working_bytes",10000000,"wall_seconds",60));
    }
    private SnprelateRequest.Request parse(Map<String,Object> raw)throws IOException {
        return SnprelateRequest.parse(NextflowParameters.parse(NextflowJson.json(raw),1000000,10000),directory);
    }
    private Map<String,Object> option(Map<String,Object> raw) {
        Map<?,?> source=(Map<?,?>)raw.get("options");var result=new LinkedHashMap<String,Object>();
        for(var e:source.entrySet())result.put((String)e.getKey(),e.getValue());raw.put("options",result);return result;
    }
    private Map<String,Object> limits(Map<String,Object> raw) {
        Map<?,?> source=(Map<?,?>)raw.get("limits");var result=new LinkedHashMap<String,Object>();
        for(var e:source.entrySet())result.put((String)e.getKey(),e.getValue());raw.put("limits",result);return result;
    }
    private Path inputs(byte[][] calls,String[] identities,String[] variants)throws IOException {
        Path root=directory.resolve("bundle"),input=root.resolve("inputs");Files.createDirectories(input);
        Files.writeString(input.resolve("cohort.fam"),String.join("\n",identities)+"\n",StandardCharsets.UTF_8);
        Files.writeString(input.resolve("cohort.bim"),String.join("\n",variants)+"\n",StandardCharsets.UTF_8);
        var bytes=new ByteArrayOutputStream();bytes.writeBytes(new byte[]{0x6c,0x1b,1});int n=identities.length;
        for(byte[] row:calls) {
            assertEquals(n,row.length);
            for(int start=0;start<n;start+=4) {
                int packed=0;for(int j=0;j<4&&start+j<n;j++)packed|=(switch(row[start+j]){case 0->3;case 1->2;case 2->0;case -1->1;default->throw new IllegalArgumentException();})<<(2*j);
                bytes.write(packed);
            }
        }
        Files.write(input.resolve("cohort.bed"),bytes.toByteArray());return root;
    }
    private static String[] fam(){return new String[]{"家族 shared 0 0 1 -9","F1 shared 0 0 2 -9","家族 NA 0 0 1 -9","F3 NaN 0 0 2 -9"};}
    private static String[] bim(){return new String[]{"1 NA 0 10 A G","1 repeat 0 20 A G","1 repeat 0 30 A G","1 v3 0 40 A G"};}
    private static byte[][] complete(){return new byte[][]{{0,1,2,0},{0,1,2,0},{0,1,2,0},{0,1,2,0}};}

    @Test void bindsExplicitOriginalMethodsParametersAndPaths()throws Exception {
        var r=parse(base());assertEquals("ld-pruning",r.mode());assertEquals("composite",r.method());assertEquals("random.f500",r.start());
        assertEquals(20261110,r.seed());assertEquals(0.2,r.threshold());assertEquals(directory.resolve("cohort"),r.bfile());
        assertNull(r.windowVariants());assertEquals("1",SnprelateRequest.parameters(r).get("threads"));
    }
    @Test void preservesExplicitNonfiniteQcAndPhysicalLayout()throws Exception {
        var m=base();m.put("layout","sample-first");m.put("maf","Inf");m.put("missing_rate","-Inf");m.put("remove_monomorphic",false);
        var r=parse(m);assertEquals(Double.POSITIVE_INFINITY,r.maf());assertEquals(Double.NEGATIVE_INFINITY,r.missing());
        assertFalse(SnprelateAudit.filters(r).active());assertEquals("sample-first",r.layout());
    }
    @Test void rejectsImplicitVersionModeLayoutOrThreadSubstitution() {
        for(var pair:List.of(new Object[]{"version","1.30.1"},new Object[]{"mode","unreported-function"},new Object[]{"layout","auto"},new Object[]{"threads",2})) {
            var raw=base();raw.put((String)pair[0],pair[1]);assertThrows(IOException.class,()->parse(raw));
        }
        var raw=base();raw.remove("version");assertThrows(IOException.class,()->parse(raw));
    }
    @Test void rejectsUnknownFieldsAndDuplicateJsonKeys()throws Exception {
        var raw=base();raw.put("extra_flags","--repair");assertThrows(IOException.class,()->parse(raw));
        String json=NextflowJson.json(base());String duplicate="{\"version\":\"1.46.0\","+json.substring(1);
        assertThrows(IOException.class,()->NextflowParameters.parse(duplicate,1000000,10000));
    }
    @Test void rejectsLdMeasureStartSeedWindowAndThresholdMistakes() {
        for(var pair:List.of(new Object[]{"method","r2"},new Object[]{"start","automatic"},new Object[]{"seed",-1},new Object[]{"seed",1.5},
                new Object[]{"window_bp",-1},new Object[]{"window_variants",-1},new Object[]{"ld_threshold","NaN"},new Object[]{"ld_threshold",1.01})) {
            var raw=base();option(raw).put((String)pair[0],pair[1]);assertThrows(IOException.class,()->parse(raw));
        }
    }
    @Test void supportsSourceZeroWindowsAndIntegerSeedBoundary()throws Exception {
        var raw=base();var opts=option(raw);opts.put("window_bp",0);opts.put("window_variants",0);opts.put("seed",Integer.MAX_VALUE);opts.put("ld_threshold",1);
        var r=parse(raw);assertEquals(0,r.windowBp());assertEquals(0,r.windowVariants());assertEquals(Integer.MAX_VALUE,r.seed());assertEquals(1,r.threshold());
    }
    @Test void rejectsNumericTypeMistakesAndFiniteOverflow()throws Exception {
        var raw=base();raw.put("maf","0.005");assertThrows(IOException.class,()->parse(raw));
        String json=NextflowJson.json(base()).replace("0.005","1e999");
        assertThrows(IOException.class,()->SnprelateRequest.parse(NextflowParameters.parse(json,1000000,10000),directory));
    }
    @Test void requiresModeSpecificOptionsWithoutFreeCodeOrFlags()throws Exception {
        var grm=base();grm.put("mode","grm");grm.put("options",Map.of("method","Weighted"));assertEquals("Weighted",parse(grm).method());
        var king=base();king.put("mode","king-robust");king.put("options",Map.of("family_mode","original-fid"));assertEquals("original-fid",parse(king).family());
        king.put("options",Map.of("family_mode","auto"));assertThrows(IOException.class,()->parse(king));
        var raw=base();option(raw).put("method","corr;system('anything')");assertThrows(IOException.class,()->parse(raw));
    }
    @Test void rejectsInvalidCapacitiesAndControlCharacters() {
        for(var pair:List.of(new Object[]{"samples",5001},new Object[]{"variants",0},new Object[]{"working_bytes",0},new Object[]{"wall_seconds",0})) {
            var raw=base();limits(raw).put((String)pair[0],pair[1]);assertThrows(IOException.class,()->parse(raw));
        }
        var raw=base();raw.put("bfile","cohort\nchanged");assertThrows(IOException.class,()->parse(raw));
    }
    @Test void nativeInputRetainsUnicodeRepeatedIidsAndLiteralIdentifiers()throws Exception {
        Path root=inputs(complete(),fam(),bim());var r=parse(base());var d=SnprelateAudit.inputs(root,r,()->{});
        assertEquals("家族",d.fam().get(0)[0]);assertEquals("shared",d.fam().get(1)[1]);assertEquals("NA",d.fam().get(2)[1]);
        assertEquals("NA",d.bim().get(0)[1]);assertEquals(d.bim().get(1)[1],d.bim().get(2)[1]);assertEquals(List.of(0,1,2,3),d.qc());
        assertArrayEquals(new byte[]{0,1,2,0},d.calls(0));
    }
    @Test void nativeInputRejectsDuplicateParticipantPairsAndUnsortedCoordinates()throws Exception {
        String[] f=fam();f[1]=f[0];Path root=inputs(complete(),f,bim());assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(base()),()->{}));
        // A separate supplied coordinate-order error remains visible; no sorting is applied.
        Files.writeString(root.resolve("inputs/cohort.fam"),String.join("\n",fam())+"\n",StandardCharsets.UTF_8);
        var v=new ArrayList<>(List.of(bim()));java.util.Collections.swap(v,0,1);Files.writeString(root.resolve("inputs/cohort.bim"),String.join("\n",v)+"\n",StandardCharsets.UTF_8);
        assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(base()),()->{}));
    }
    @Test void completeQcAndMissingTransportUseOriginalCallPopulation()throws Exception {
        byte[][] calls={{-1,-1,-1,-1},{0,0,0,0},{0,1,2,0},{0,1,2,-1}};
        Path root=inputs(calls,fam(),bim());var r=parse(base());var d=SnprelateAudit.inputs(root,r,()->{});assertEquals(List.of(2),d.qc());
        SnprelateAudit.writeInputs(root,d,r,()->{});String raw=Files.readString(root.resolve("inputs/native-calls.tsv"),StandardCharsets.UTF_8);
        assertTrue(raw.contains("1\t3\t3\t3\t3\n"));assertTrue(raw.contains("4\t0\t1\t2\t3\n"));assertArrayEquals(calls[3],d.calls(3));
    }
    @Test void windowBoundsUseInclusivePhysicalAndQcIndexDistances()throws Exception {
        Path root=inputs(complete(),fam(),bim());var raw=base();option(raw).put("window_bp",20);
        assertEquals(5,SnprelateAudit.inputs(root,parse(raw),()->{}).candidateEdges());
        option(raw).put("window_variants",1);assertEquals(3,SnprelateAudit.inputs(root,parse(raw),()->{}).candidateEdges());
        option(raw).put("window_variants",0);assertEquals(0,SnprelateAudit.inputs(root,parse(raw),()->{}).candidateEdges());
    }
    @Test void nativeOperationAndStorageBoundsFailBeforeSourceExecution()throws Exception {
        Path root=inputs(complete(),fam(),bim());var raw=base();limits(raw).put("pair_variant_operations",1);
        assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(raw),()->{}));
        var small=base();limits(small).put("working_bytes",1);assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(small),()->{}));
    }
    @Test void emptyQcCannotBePromotedToAnAdmittedOutput()throws Exception {
        Path root=inputs(new byte[][]{{-1,-1,-1,-1}},fam(),new String[]{bim()[0]});var r=parse(base());var d=SnprelateAudit.inputs(root,r,()->{});assertTrue(d.qc().isEmpty());
        var failure=assertThrows(IOException.class,()->SnprelateAudit.outputs(root,root.resolve("worker"),r,d,()->{}));assertTrue(failure.getMessage().contains("empty QC population"));
    }
    @Test void missingRuntimeReturnsProminentSkipWithoutLaunchingOrInventingResults()throws Exception {
        Path file=directory.resolve("request.json");Files.writeString(file,NextflowJson.json(base()),StandardCharsets.UTF_8);
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        assertEquals(3,SnprelateToolsCli.run(new String[]{"--request",file.toString()},new PrintStream(stdout),new PrintStream(stderr)));
        Path bundle=directory.resolve("attempt");var done=NextflowFiles.object(NextflowFiles.json(bundle.resolve("COMPLETED.json"),1000000));
        assertEquals(Boolean.FALSE,done.get("result_admitted"));assertEquals(Boolean.FALSE,done.get("original_analysis_executed"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("SKIP3_EXACT_SNPRELATE_RUNTIME_UNAVAILABLE"));
        assertTrue(Files.readString(bundle.resolve("WARNINGS.md")).contains("If downloads/installation are unavailable or forbidden, keep that analysis skipped"));
        assertFalse(Files.exists(bundle.resolve("worker")));assertFalse(Files.exists(bundle.resolve("scientific-audit.json")));
    }
    @Test void helpAndSavedVerificationKeepScopeAndRecoveryVisible() {
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        assertEquals(0,SnprelateToolsCli.run(new String[]{"--help"},new PrintStream(stdout),new PrintStream(stderr)));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("GRM estimators execute upstream and are not independently refitted"));
        assertEquals(2,SnprelateToolsCli.run(new String[]{"--verify",directory.resolve("missing-bundle").toString()},new PrintStream(stdout),new PrintStream(stderr)));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("FAIL2_NO_SNPRELATE_RESULT_ADMITTED"));
    }
    private static Map<String,Object> exactPca() {
        return SnprelateAudit.map("algorithm","exact","eigen_count",3,"bayesian",false,"eigen_method","DSPEVX","aux_dim",6,"iterations",3,"seed",20261110,"need_genmat",true);
    }
    @Test void pcaRequiresExplicitSolverComponentsNormalizationAndSeed()throws Exception {
        var raw=base();raw.put("mode","pca");raw.put("options",exactPca());var r=parse(raw);
        assertEquals(3,r.pca().eigenCount());assertEquals("DSPEVX",r.pca().solver());assertTrue(r.pca().genmat());
        assertEquals("20261110",SnprelateRequest.parameters(r).get("seed"));assertEquals("false",SnprelateRequest.parameters(r).get("pca_bayesian"));
        option(raw).put("eigen_count",0);assertEquals(0,parse(raw).pca().eigenCount());
    }
    @Test void randomizedPcaDoesNotSilentlyIgnoreRequestedBayesianOrMatrixFlags()throws Exception {
        var raw=base();raw.put("mode","pca");raw.put("options",exactPca());var opts=option(raw);opts.put("algorithm","randomized");
        assertThrows(IOException.class,()->parse(raw));opts.put("need_genmat",false);assertEquals("randomized",parse(raw).pca().algorithm());
        opts.put("bayesian",true);assertThrows(IOException.class,()->parse(raw));opts.put("bayesian",false);
        opts.put("eigen_method","DSPEV");assertThrows(IOException.class,()->parse(raw));
    }
    @Test void pcaPopulationAndProjectionCapacitiesFailBeforeAnalysis()throws Exception {
        Path root=inputs(complete(),fam(),bim());var raw=base();raw.put("mode","pca");raw.put("options",exactPca());
        option(raw).put("eigen_count",5);assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(raw),()->{}));
        var opts=option(raw);opts.put("eigen_count",3);opts.put("algorithm","randomized");opts.put("need_genmat",false);
        assertThrows(IOException.class,()->SnprelateAudit.inputs(root,parse(raw),()->{}));
    }
}
