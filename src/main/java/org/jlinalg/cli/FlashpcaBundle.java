/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.FlashpcaPcaAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Exact original PCA plus complete native matrix/output checks and fresh replay. */
final class FlashpcaBundle {
    private FlashpcaBundle() { }
    static Runnable bounded(long millis,Runnable check){long start=System.nanoTime();return()->{check.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>millis)throw new IllegalStateException("Complete FlashPCA wall/cancellation capacity exceeded");};}
    private static Map<Path,String> roles(FlashpcaRequest.Spec s){var result=new LinkedHashMap<Path,String>();for(String tail:List.of("bed","bim","fam"))result.put(Path.of(s.prefix()+"."+tail),"data."+tail);return result;}
    private static void prepare(Path root,byte[] raw,Object inputs,Object source,Object binding)throws IOException {
        Files.write(root.resolve("request.json"),raw,StandardOpenOption.CREATE_NEW);writeJson(root.resolve("captured-inputs.json"),inputs);writeJson(root.resolve("source-inputs.json"),source);writeJson(root.resolve("runtime-binding.json"),binding);
        Files.writeString(root.resolve("WARNINGS.md"),FlashpcaRuntime.ACTION+"\n\n"+warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Files.createDirectory(root.resolve("engine"));Files.createDirectory(root.resolve("engine/inputs"));
    }
    private static Map<Path,Pin> anchors(Path root,Runnable check)throws IOException {
        var anchors=new HashMap<Path,Pin>();anchors.put(FlashpcaRuntime.producerPath(),FlashpcaRuntime.producer());
        for(var e:tree(root,200,16777216,16777216,check).entrySet()){require(e.getValue().kind().equals("file"),"Unexpected link in owned metadata");anchors.put(root.resolve(e.getKey()),e.getValue().pin());}
        return anchors;
    }
    static Path capture(Path request,Path supplied,Runnable cancellation)throws IOException {
        FlashpcaRuntime.platform();if(!Files.isReadable(request))throw new FlashpcaRuntime.Unavailable("Required analysis request unavailable: "+request);
        byte[] raw=bytes(request,16777216);var s=FlashpcaRequest.parse(SimpleJson.parse(utf8(raw)));Runnable check=bounded(s.wallMillis(),cancellation);Path root=FlashpcaRuntime.newRoot(supplied);
        var roles=roles(s);for(Path p:roles.keySet())require(!p.startsWith(root)&&!root.startsWith(p),"Output overlaps original genotype input");
        if(!s.execute()){Files.createDirectory(root);Files.write(root.resolve("request.json"),raw,StandardOpenOption.CREATE_NEW);failure(root,new FlashpcaRuntime.Unavailable("Explicit native/upstream execution FORBID"));throw new FlashpcaRuntime.Unavailable("Execution FORBID; inspect "+root);}
        var runtime=FlashpcaRuntime.open(s.runtime(),s.profile(),check);runtime.separate(root);
        var inputs=new TreeMap<String,Object>();var source=new ArrayList<Object>();long total=0;
        for(var e:roles.entrySet()) {
            if(!Files.isReadable(e.getKey()))throw new FlashpcaRuntime.Unavailable("Complete permitted genotype input unavailable: "+e.getKey());
            var pin=pin(e.getKey(),s.inputBytes(),check);total=Math.addExact(total,pin.bytes());require(total<=s.inputBytes(),"Complete input byte capacity exceeded");
            inputs.put(e.getValue(),map("kind","file","mode",mode(e.getKey()),"bytes",pin.bytes(),"sha256",pin.sha256()));source.add(map("original",e.getKey().toString(),"captured",e.getValue(),"bytes",pin.bytes(),"sha256",pin.sha256()));
        }
        Files.createDirectory(root);
        try {
            prepare(root,raw,inputs,source,map("path",runtime.root().toString(),"seal",runtime.seal()));runtime=runtime.anchored(anchors(root,check));
            for(var e:roles.entrySet()){Path target=root.resolve("engine/inputs").resolve(e.getValue());Files.copy(e.getKey(),target,StandardCopyOption.COPY_ATTRIBUTES);require(pin(target,s.inputBytes(),check).equals(fingerprint(inputs.get(e.getValue()),s.inputBytes())),"Captured input changed");}
            execute(root,s,runtime,inputs,check);
            for(var e:roles.entrySet())require(pin(e.getKey(),s.inputBytes(),check).equals(fingerprint(inputs.get(e.getValue()),s.inputBytes())),"Original genotype input changed during PCA");
            require(Arrays.equals(raw,bytes(request,16777216)),"Original request changed during PCA");finish(root,s,runtime,check);return root;
        }catch(IOException|RuntimeException|Error e){failure(root,e);throw e;}
    }
    private static void immutable(Path root,FlashpcaRequest.Spec s,FlashpcaRuntime.Context runtime,Object inputs,Runnable check)throws IOException {
        runtime.verify(check);require(NextflowJson.json(inputs).equals(NextflowJson.json(manifest(tree(root.resolve("engine/inputs"),8,s.inputBytes(),s.inputBytes(),check)))),"Complete captured BED/BIM/FAM population changed");
    }
    private static void execute(Path root,FlashpcaRequest.Spec s,FlashpcaRuntime.Context runtime,Object inputs,Runnable check)throws IOException {
        immutable(root,s,runtime,inputs,check);Path prefix=root.resolve("engine/inputs/data"),engine=root.resolve("engine");var in=FlashpcaPcaAudit.inspect(prefix,s.options(),s.audit(),check);
        if(s.options().edition()==FlashpcaPcaAudit.Edition.V1_2_6){long extra=integer(s.settings().get("legacy_extra_dimensions"),0,10000);if(extra==0)extra=Math.min(Math.min(in.samples(),in.variants())-s.options().components(),190);require(s.options().components()+extra<=Math.min(in.samples(),in.variants()),"Complete legacy randomized subspace dimensions exceed data; no hidden reduction");}
        var inputReport=input(in);writeJson(root.resolve("INPUT-AUDIT.json"),inputReport);var anchored=runtime.anchored(Map.of(root.resolve("INPUT-AUDIT.json"),pin(root.resolve("INPUT-AUDIT.json"),16777216,check)));
        var command=new ArrayList<>(List.of(text(runtime.value().get("binary")),"--bfile",prefix.toString(),"--ndim",Integer.toString(s.options().components()),"--seed",textNumber(s.settings().get("seed")),"--maxiter",textNumber(s.settings().get("iterations")),"--tol",textNumber(s.settings().get("tolerance")),"--numthreads","1","--outpc","pcs.txt","--outvec","eigenvectors.txt","--outval","eigenvalues.txt","--outpve","pve.txt","--outload","loadings.txt","--outmeansd","meansd.txt"));
        if(s.options().edition()==FlashpcaPcaAudit.Edition.V1_2_6){command.addAll(List.of("--stand","binom","--kernel","linear","--method",text(s.settings().get("legacy_method")).toLowerCase(Locale.ROOT),"--mem",text(s.settings().get("legacy_memory")).toLowerCase(Locale.ROOT),"--orth","yes"));if(integer(s.settings().get("legacy_extra_dimensions"),0,10000)>0)command.addAll(List.of("--nextra",textNumber(s.settings().get("legacy_extra_dimensions"))));if(s.options().forceTranspose())command.add("--transpose");}
        else command.addAll(List.of("--standx",s.options().standardization()==FlashpcaPcaAudit.Standardization.BINOM?"binom":"binom2","--div",s.options().divisor()==FlashpcaPcaAudit.Divisor.N1?"n1":s.options().divisor()==FlashpcaPcaAudit.Divisor.P?"p":"none","--blocksize",textNumber(s.settings().get("block_snps")),"--precision","15"));
        writeJson(root.resolve("original-command.json"),command);var context=anchored.anchored(Map.of(root.resolve("original-command.json"),pin(root.resolve("original-command.json"),16777216,check)));
        NextflowProcess.executeSession(command,runtime.environment(),root.resolve("original-process"),engine,root,s.process(),()->immutable(root,s,context,inputs,check),check);
        var actual=tree(engine,s.files(),s.outputBytes(),s.outputBytes(),check);require(actual.keySet().equals(Set.of("inputs/data.bed","inputs/data.bim","inputs/data.fam","pcs.txt","eigenvectors.txt","eigenvalues.txt","pve.txt","loadings.txt","meansd.txt")),"Complete original PCA output population differs");
        var report=FlashpcaPcaAudit.audit(prefix,engine,s.options(),s.audit(),check);var outputReport=report(report);
        writeJson(root.resolve("OUTPUT-AUDIT.json"),map("status","COMPLETE_SCOPED_ORIGINAL_FLASHPCA_PCA","native",outputReport,"warnings",List.of(warnings()),"whole_paper_reproduced",false));
        var tables=new TreeMap<String,Object>();for(String name:List.of("pcs.txt","eigenvectors.txt","eigenvalues.txt","pve.txt","loadings.txt","meansd.txt"))tables.put(name,pin(engine.resolve(name),s.outputBytes(),check));
        writeJson(root.resolve("canonical.json"),map("input",inputReport,"native",outputReport,"all_original_numeric_tables",tables,"whole_paper_reproduced",false));immutable(root,s,context,inputs,check);
    }
    private static Map<String,Object> input(FlashpcaPcaAudit.Input r){return map("samples",r.samples(),"variants",r.variants(),"called_genotypes",r.calledGenotypes(),"missing_genotypes",r.missingGenotypes(),"monomorphic_variants",r.monomorphicVariants(),"legacy_transposed",r.legacyTransposed(),"divisor",r.divisor());}
    private static Object report(FlashpcaPcaAudit.Report r){return map("input",input(r.input()),"components",r.components(),"covariance_trace",r.covarianceTrace(),"maximum_orthogonality_error",r.maximumOrthogonalityError(),"maximum_relative_eigen_residual",r.maximumRelativeEigenResidual(),"maximum_pc_scaling_error",r.maximumPcScalingError(),"maximum_loading_error",r.maximumLoadingError(),"maximum_meansd_error",r.maximumMeanSdError(),"maximum_pve_error",r.maximumPveError(),"retained_variance_fraction",r.retainedVarianceFraction(),"complete_genotypes_audited",r.completeGenotypesAudited(),"partial_pca_refit",r.partialPcaRefit(),"global_largest_eigenvalue_order_certified",r.globalLargestEigenvalueOrderCertified());}
    private static String textNumber(Object value)throws IOException{return value instanceof Number?value.toString():value instanceof NextflowParameters.NumberToken n?n.text():text(value);}
    private static void finish(Path root,FlashpcaRequest.Spec s,FlashpcaRuntime.Context runtime,Runnable check)throws IOException {
        runtime.verify(check);var members=tree(root,s.files(),s.outputBytes(),s.outputBytes(),check);writeJson(root.resolve("manifest.json"),map("schema_version",1,"profile",s.profile(),"producer",FlashpcaRuntime.producer(),"files",manifest(members),"producer_authenticated",false));
        writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_SCOPED_ORIGINAL_FLASHPCA_PCA","manifest",pin(root.resolve("manifest.json"),16777216,check),"result_admitted",true,"whole_paper_reproduced",false));
    }
    private static FlashpcaRequest.Spec saved(Path root,Runnable check)throws IOException {
        directory(root);if(!Files.isRegularFile(root.resolve("COMPLETED.json")))throw new FlashpcaRuntime.Unavailable("Completed permitted saved PCA unavailable: "+root);
        require(!Files.exists(root.resolve("FAILURE.json")),"Failed original attempt cannot be admitted");var done=object(json(root.resolve("COMPLETED.json"),1048576));require(done.get("status").equals("COMPLETE_SCOPED_ORIGINAL_FLASHPCA_PCA")&&Boolean.TRUE.equals(done.get("result_admitted")),"Invalid saved admission scope");
        require(pin(root.resolve("manifest.json"),16777216,check).equals(fingerprint(done.get("manifest"),16777216)),"Saved manifest changed");var m=object(json(root.resolve("manifest.json"),16777216));require(FlashpcaRuntime.producer().equals(fingerprint(m.get("producer"),1000000000)),"Use exact original producing JAR; no seal refresh");
        var s=FlashpcaRequest.parse(json(root.resolve("request.json"),16777216));require(s.profile().equals(m.get("profile"))&&s.execute(),"Saved source edition or execution policy differs");var members=new TreeMap<>(manifest(tree(root,s.files(),s.outputBytes(),s.outputBytes(),check)));members.remove("manifest.json");members.remove("COMPLETED.json");require(NextflowJson.json(members).equals(NextflowJson.json(m.get("files"))),"Complete saved input/output/evidence population changed");
        var runtime=FlashpcaRuntime.open(s.runtime(),s.profile(),check);var bound=object(json(root.resolve("runtime-binding.json"),1048576));require(runtime.root().toString().equals(bound.get("path"))&&runtime.seal().equals(fingerprint(bound.get("seal"),1048576)),"Saved runtime binding differs");return s;
    }
    static void verify(Path original,Path supplied,Runnable cancellation)throws IOException {
        FlashpcaRuntime.platform();original=original.toAbsolutePath().normalize();var s=saved(original,cancellation);Runnable check=bounded(s.wallMillis(),cancellation);var runtime=FlashpcaRuntime.open(s.runtime(),s.profile(),check);Path root=FlashpcaRuntime.newRoot(supplied);runtime.separate(root);require(!root.startsWith(original)&&!original.startsWith(root),"Replay overlaps saved evidence");Files.createDirectory(root);
        try {
            var inputs=json(original.resolve("captured-inputs.json"),16777216);prepare(root,bytes(original.resolve("request.json"),16777216),inputs,json(original.resolve("source-inputs.json"),16777216),json(original.resolve("runtime-binding.json"),1048576));runtime=runtime.anchored(anchors(root,check));
            require(object(inputs).keySet().equals(Set.of("data.bed","data.bim","data.fam")),"Complete saved input-role population differs");
            for(Object name:object(inputs).keySet())Files.copy(original.resolve("engine/inputs").resolve(member(text(name))),root.resolve("engine/inputs").resolve(member(text(name))),StandardCopyOption.COPY_ATTRIBUTES);
            execute(root,s,runtime,inputs,check);require(NextflowJson.json(json(root.resolve("canonical.json"),16777216)).equals(NextflowJson.json(json(original.resolve("canonical.json"),16777216))),"Fresh original PCA differs in complete numeric tables, dimensions or native audits");saved(original,check);
            writeJson(root.resolve("REPLAY.json"),map("status","PASS_COMPLETE_FRESH_ORIGINAL_FLASHPCA_REPLAY","original_completion",pin(original.resolve("COMPLETED.json"),1048576,check),"all_source_tables_equal",true));finish(root,s,runtime,check);saved(original,check);
        }catch(IOException|RuntimeException|Error e){failure(root,e);throw e;}
    }
    private static void failure(Path root,Throwable e)throws IOException {Files.deleteIfExists(root.resolve("COMPLETED.json"));writeJson(root.resolve("FAILURE.json"),map("status",e instanceof FlashpcaRuntime.Unavailable||e instanceof NextflowProcess.Unavailable?"SKIP3_DEPENDENT_FLASHPCA_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_FLASHPCA_PCA","error",e.toString(),"required_action",FlashpcaRuntime.ACTION,"result_admitted",false));}
    static String warnings(){return "Original linear PCA starts from the exact supplied PLINK BED/BIM/FAM population; every genotype, ordered sample/SNP/A1 output and six complete numeric tables are checked. Missing calls use the original SNP mean. Legacy BINOM/N-1 scaling and orientation-dependent PC scores differ from modern BINOM2/P defaults; source scores remain authoritative. v1.2.6 leaves monomorphic constant columns uncentered and prints six significant digits; v2 zeroes their lookup and prints the explicitly selected fifteen digits. All-missing SNPs and nonfinite/inadequate-rank outputs admit no result. No silent sample, variant, allele, dimension or missing-value repair occurs. The source v2 seed argument is retained even though the original online solver initializes independently of that argument.\n\nNative full covariance/eigenvector residuals, orthogonality, moments, loadings, score scaling and total-trace variance checks do not refit partial PCA or formally certify global top-k order; independent complete SVD controls qualify the tested adapter. No MAF/HWE/LD/region filtering, rare-variant random sampling, ancestry labeling or downstream association model is performed. P240's source MAF <15% wording is preserved. Cited method families do not recover an unreported author patch. Legacy PCA materializes its genotype matrix, while original modern online PCA uses declared SNP blocks. Address-space/RSS, whole-work and output capacities must cover the actual complete workload; no biobank deployment or paper cohort is certified. Preserve the producing JAR, source/runtime bindings, inputs, outputs and failed attempts.\n";}
}
