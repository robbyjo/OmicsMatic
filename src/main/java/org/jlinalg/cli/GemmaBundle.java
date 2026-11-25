/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jlinalg.genetics.GemmaKinship;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete captured-byte and native input/output consistency; no original-host access. */
final class GemmaBundle {
    private GemmaBundle(){ }
    static Pin producer()throws IOException{try{Path p=Path.of(GemmaBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!Files.isRegularFile(p))throw new IOException("Execute GEMMA capture/verification using its exact packaged executable JAR");return pin(p,1000000000L,()->{});}catch(java.net.URISyntaxException e){throw new IOException(e);}}
    static Map<String,Object> files(Path root,GemmaRequest.Spec s,Runnable check)throws IOException{
        long maximum=s.nativeEngine()?Math.addExact(Math.addExact(s.inputBytes(),s.outputBytes()),16777216):s.process().workspaceBytes();int nodes=s.nativeEngine()?100:s.process().fileCount();var result=new TreeMap<String,Object>();long generated=0;
        for(var e:tree(root,nodes,maximum,Math.max(s.inputBytes(),s.outputBytes())+16777216,check).entrySet()){
            require(e.getValue().kind().equals("file"),"GEMMA capture cannot contain symbolic links");if(!s.originals().containsKey(e.getKey())){generated=Math.addExact(generated,e.getValue().pin().bytes());require(generated<=s.outputBytes()+16777216,"Complete generated population exceeds output_bytes plus16MiB metadata reserve");}result.put(e.getKey(),Map.of("bytes",e.getValue().pin().bytes(),"sha256",e.getValue().pin().sha256()));
        }return result;
    }
    static void verify(Path supplied,boolean precommit,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();directory(root);require(!Files.exists(root.resolve("FAILURE.json"))&&Files.exists(root.resolve("INCOMPLETE.json"))==precommit,"Failed/incomplete GEMMA capture is not admitted");var completed=object(json(root.resolve("COMPLETED.json"),1048576));keys(completed,"schema_version","status","manifest","analysis_admitted");integer(completed.get("schema_version"),1,1);require("COMPLETE_GEMMA_CAPTURED_CONTRACT".equals(completed.get("status"))&&Boolean.TRUE.equals(completed.get("analysis_admitted")),"Missing overall GEMMA admission");Pin manifest=pin(root.resolve("manifest.json"),16777216,check);require(manifest.equals(fingerprint(completed.get("manifest"),16777216)),"Manifest bytes changed");var saved=object(json(root.resolve("manifest.json"),16777216));keys(saved,"schema_version","profile","files","producer","analysis_admitted","whole_paper_reproduced","optimizer_convergence_certified");integer(saved.get("schema_version"),1,1);require("gemma-captured-plink-univariate-v1".equals(saved.get("profile"))&&Boolean.TRUE.equals(saved.get("analysis_admitted"))&&Boolean.FALSE.equals(saved.get("whole_paper_reproduced"))&&Boolean.FALSE.equals(saved.get("optimizer_convergence_certified")),"Different/expanded GEMMA capture profile");require(producer().equals(fingerprint(saved.get("producer"),1000000000L)),"Restore the exact producing JAR; never refresh historical expected hashes");
        GemmaRequest.Spec s;try{s=GemmaRequest.read(root.resolve("request.json"),false);}catch(GemmaRuntime.Unavailable e){throw new IOException("Saved GEMMA contract differs; no live runtime was consulted",e);}
        long started=System.nanoTime();Runnable bounded=()->{check.run();if((System.nanoTime()-started)/1000000>s.wallMillis())throw new IllegalStateException("Cooperative GEMMA saved-readback wall capacity exceeded");};
        inventory(root,s,saved.get("files"),precommit,bounded);audit(root,s,true,bounded);require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(GemmaWorkflow.warnings(s)),"Warnings or required actions changed");inventory(root,s,saved.get("files"),precommit,bounded);require(manifest.equals(pin(root.resolve("manifest.json"),16777216,bounded)),"Manifest changed during complete readback");
    }
    private static void inventory(Path root,GemmaRequest.Spec s,Object declared,boolean precommit,Runnable check)throws IOException{
        var actual=files(root,s,check);var names=new HashSet<String>(Set.of("request.json","source-inputs.json","QC.jsonl","samples.tsv","execution.json","OUTPUT-AUDIT.json","WARNINGS.md","manifest.json","COMPLETED.json"));names.addAll(s.originals().keySet());
        if(s.nativeEngine())names.add("outputs/kinship.txt");else{
            names.addAll(Set.of("runtime/marker.json","runtime/setup-completed.json","runtime/health-completed.json","runtime/provenance.json","process/launch.json","process/process.json","process/WARNINGS.md","process/process.stdout","process/process.stderr","outputs/result.log.txt"));names.add(output(s));
        }if(precommit)names.add("INCOMPLETE.json");require(actual.keySet().equals(names),"Complete GEMMA namespace differs; missing/extra files are not admitted");
        var dirs=new HashSet<String>();dirs.add("");for(String name:names){Path p=Path.of(name).getParent();while(p!=null){dirs.add(p.toString().replace('\\','/'));p=p.getParent();}}
        try(var paths=Files.walk(root)){for(Path p:paths.toList())if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))require(dirs.remove(root.relativize(p).toString().replace('\\','/')),"Extra/duplicate capture directory");}require(dirs.isEmpty(),"Missing capture directories");
        actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");require(NextflowJson.json(actual).equals(NextflowJson.json(declared)),"Complete captured bytes differ");
    }
    static Map<String,Object> audit(Path root,GemmaRequest.Spec s,boolean saved,Runnable check)throws IOException{
        check.run();var sources=object(json(root.resolve("source-inputs.json"),1048576));keys(sources,"files","complete_original_bytes","source_bytes_unchanged");var original=object(sources.get("files"));require(original.keySet().equals(s.originals().keySet())&&Boolean.TRUE.equals(sources.get("source_bytes_unchanged")),"Incomplete original input capture");long bytes=0;for(var e:s.originals().entrySet()){var row=object(original.get(e.getKey()));keys(row,"original","pin");require(e.getValue().equals(row.get("original")),"Original path declaration differs");Pin actual=pin(root.resolve(e.getKey()),s.inputBytes(),check);require(actual.equals(fingerprint(row.get("pin"),s.inputBytes())),"Captured original file changed");bytes=Math.addExact(bytes,actual.bytes());}require(bytes==integer(sources.get("complete_original_bytes"),0,s.inputBytes()),"Complete input byte count differs");
        try(var prepared=GemmaInputs.read(root,s,check)){
            try(var rows=new GemmaInputs.Lines(root.resolve("QC.jsonl"),s.outputBytes()+16777216)){for(var p:prepared.screened.predictors()){String line=rows.next();require(line!=null&&line.equals(NextflowJson.json(GemmaWorkflow.predictor(p))),"Complete native QC disposition differs: "+p.id());}require(rows.next()==null,"Extra QC dispositions");}
            require(Files.readString(root.resolve("samples.tsv"),StandardCharsets.UTF_8).equals(GemmaWorkflow.samples(prepared)),"Complete sample identity/mask differs");var execution=object(json(root.resolve("execution.json"),1048576));
            var report=new LinkedHashMap<String,Object>();report.put("schema_version",1);report.put("status","COMPLETE_GEMMA_INPUT_OUTPUT_CONSISTENCY");report.put("engine",s.engine());report.put("edition",s.edition());report.put("mode",s.mode());report.put("full_fam_samples",prepared.data.samples().size());report.put("qc_complete_samples",prepared.screened.qcSamples());report.put("total_variants",prepared.data.variantCount());report.put("used_variants",prepared.screened.predictorsUsed());report.put("all_input_output_populations_checked",true);report.put("optimizer_convergence_certified",false);report.put("whole_paper_reproduced",false);
            var statuses=new TreeMap<String,Integer>();for(var p:prepared.screened.predictors())statuses.merge(p.status().toString(),1,Integer::sum);report.put("qc_dispositions",statuses);
            if(s.nativeEngine()){
                keys(execution,"engine","edition_contract","mode","original_executable_run");require("native".equals(execution.get("engine"))&&s.edition().equals(execution.get("edition_contract"))&&s.mode().equals(execution.get("mode"))&&Boolean.FALSE.equals(execution.get("original_executable_run")),"Expanded/different native execution claim");matrix(root.resolve("outputs/kinship.txt"),prepared,s,true,check);report.put("matrix_entries_checked",(long)prepared.data.samples().size()*prepared.data.samples().size());
            }else{
                original(root,s,execution,prepared,check);report.put("original_near_zero_eigenvalue_warning_retained",Files.readString(root.resolve("process/process.stderr"),StandardCharsets.UTF_8).contains("eigenvalues close to zero"));
                if(s.association())report.putAll(associations(root,prepared,s,check));else{matrix(root.resolve(output(s)),prepared,s,false,check);report.put("matrix_entries_checked",(long)prepared.data.samples().size()*prepared.data.samples().size());}
            }
            if(saved)require(NextflowJson.json(report).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),1048576))),"Saved complete output audit differs");return report;
        }
    }
    private static void original(Path root,GemmaRequest.Spec s,Map<?,?> execution,GemmaInputs input,Runnable check)throws IOException{
        keys(execution,"engine","edition","mode","execution_root","home","command","original_executable_modified");String oldRoot=text(execution.get("execution_root")),home=text(execution.get("home"));require(oldRoot.startsWith("/")&&home.startsWith("/")&&"original".equals(execution.get("engine"))&&s.edition().equals(execution.get("edition"))&&s.mode().equals(execution.get("mode"))&&Boolean.FALSE.equals(execution.get("original_executable_modified")),"Different/expanded original execution claim");var command=GemmaWorkflow.command(oldRoot,home,s);require(command.equals(strings(execution.get("command"))),"Captured original options differ");
        var process=object(json(root.resolve("process/process.json"),16777216));var p=s.process();var wrapped=new ArrayList<String>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+p.addressSpaceBytes(),"--cpu="+p.cpuSeconds(),"--fsize="+p.fileBytes(),"--nofile="+p.openFiles(),"--"));wrapped.addAll(command);require("completed-process-only".equals(process.get("status"))&&integer(process.get("exit_code"),0,0)==0&&list(process.get("owned_live_after_cleanup")).isEmpty()&&wrapped.equals(strings(process.get("command")))&&oldRoot.equals(process.get("working_directory"))&&NextflowJson.json(p).equals(NextflowJson.json(process.get("limits"))),"Original process/options/capacities are incomplete or not terminal");
        var marker=object(json(root.resolve("runtime/marker.json"),1048576));var done=object(json(root.resolve("runtime/setup-completed.json"),1048576));var health=object(json(root.resolve("runtime/health-completed.json"),1048576));GemmaRuntime.savedEvidence(marker,done,health,pin(root.resolve("runtime/marker.json"),1048576,check),pin(root.resolve("runtime/health-completed.json"),1048576,check));var provenance=object(json(root.resolve("runtime/provenance.json"),1048576));require(pin(root.resolve("runtime/marker.json"),1048576,check).equals(fingerprint(provenance.get("runtime_marker"),1048576))&&pin(root.resolve("runtime/setup-completed.json"),1048576,check).equals(fingerprint(provenance.get("setup_completed"),1048576))&&pin(root.resolve("runtime/health-completed.json"),1048576,check).equals(fingerprint(provenance.get("health_completed"),1048576))&&Boolean.FALSE.equals(provenance.get("original_binaries_modified")),"Captured runtime provenance differs");var helpers=object(provenance.get("helpers"));require(helpers.keySet().equals(Set.of("/usr/bin/setsid","/usr/bin/prlimit")),"Missing/extra Linux execution helper population");for(Object raw:helpers.values())fingerprint(raw,16777216);
        for(String path:List.of("process/process.stdout","process/process.stderr","outputs/result.log.txt")){
            String text=utf8(bytes(root.resolve(path),16777216));String diagnostic=String.join("\n",text.lines().filter(line->!line.startsWith("## Command Line Input")&&!line.startsWith("## Build profile")).toList());require(!Pattern.compile("(?i)\\b(error|failed|failure|nan|inf|infinite|nonfinite|aborted)\\b|not converg|maximum.*iteration").matcher(diagnostic).find(),"Original numerical/error diagnostic in "+path+"; inspect preserved bytes; no complete result admitted");
            for(String line:text.lines().toList())if(line.contains("WARNING"))require(line.matches("\\*{4} WARNING: Matrix G has [0-9]+ eigenvalues close to zero"),"Unexpected original warning: "+line);
        }
        String log=utf8(bytes(root.resolve("outputs/result.log.txt"),16777216));require(log.contains("## GEMMA Version    = "+s.edition()+" "),"Original output edition banner differs");
        var counts=Map.of("number of total individuals",input.data.samples().size(),"number of analyzed individuals",input.screened.qcSamples(),"number of covariates",input.design[0].length,"number of phenotypes",1,"number of total SNPs/var",(int)input.data.variantCount(),"number of analyzed SNPs/var",input.screened.predictorsUsed());
        for(var count:counts.entrySet())require(log.lines().filter(line->line.startsWith("## "+count.getKey()+" = ")).toList().equals(List.of("## "+count.getKey()+" = "+count.getValue())),"Original complete input/QC count differs: "+count.getKey());
    }
    private static void matrix(Path path,GemmaInputs input,GemmaRequest.Spec s,boolean exact,Runnable check)throws IOException{
        int n=input.data.samples().size();try(var lines=new GemmaInputs.Lines(path,s.outputBytes())){for(int i=0;i<n;i++){check.run();String row=lines.next();require(row!=null,"Missing complete kinship output row");String[] fields=GemmaInputs.fields(row);require(fields.length==n,"Incomplete kinship output columns");for(int j=0;j<n;j++){double actual=GemmaInputs.finite(fields[j]),expected=input.screened.value(i,j);require(exact?Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected):Math.abs(actual-expected)<=6e-6*Math.max(1,Math.abs(expected)),"Complete source/native kinship entry differs: "+i+","+j);}}require(lines.next()==null,"Extra kinship output rows");}
    }
    private static Map<String,Object> associations(Path root,GemmaInputs input,GemmaRequest.Spec s,Runnable check)throws IOException{
        List<String> header=associationHeader(s);
        long rows=0,boundaries=0;var details=input.screened.predictors();var variants=input.data.selectedVariants();
        try(var lines=new GemmaInputs.Lines(root.resolve(output(s)),s.outputBytes())){
            String first=lines.next();require(first!=null&&Arrays.asList(first.split("\t",-1)).equals(header),"Original association header differs");
            for(int j=0;j<details.size();j++)if(details.get(j).status()==GemmaKinship.Status.USED){
                check.run();var p=details.get(j);var variant=variants.get(j);String row=lines.next();require(row!=null,"Missing source association row");String[] f=row.split("\t",-1);require(f.length==header.size(),"Association field count differs");require(f[0].equals(variant.chromosome())&&f[1].equals(variant.id())&&f[2].equals(Long.toString(variant.position()))&&f[3].equals(Integer.toString(p.qcMissing()))&&f[4].equals(variant.allele1())&&f[5].equals(variant.allele2()),"Association identifier/allele/missingness differs from complete native QC");
                var values=new HashMap<String,Double>();for(int c=6;c<f.length;c++)values.put(header.get(c),GemmaInputs.finite(f[c]));require(Math.abs(values.get("af")-p.qcAllele1Frequency())<=.000501,"Original A1 frequency differs from complete native calls");
                if(s.mode().equals("lmm3")&&!s.edition().equals("0.98.5"))require(values.get("logl_H1")==0,"Original older score-mode likelihood placeholder differs; it is not a fitted likelihood");
                for(var e:values.entrySet()){
                    if(e.getKey().startsWith("p_"))require(e.getValue()>=0&&e.getValue()<=1,"Association probability outside0..1");
                    if(e.getKey().equals("se"))require(e.getValue()>0,"Nonpositive association standard error");
                    if(e.getKey().startsWith("l_")){double l=e.getValue();require(l>=1e-5*(1-1e-6)&&l<=1e5*(1+1e-6),"Lambda outside explicit original bounds");if(l<=1e-5*(1+1e-6)||l>=1e5*(1-1e-6))boundaries++;}
                }
                if(values.containsKey("p_wald")){double z=values.get("beta")/values.get("se"),stat=z*z;require(Double.isFinite(stat),"Nonfinite derived Wald statistic");double expected=jdistlib.F.cumulative(stat,1,input.screened.qcSamples()-input.design[0].length-1,false,false);require(Double.isFinite(expected)&&Math.abs(expected-values.get("p_wald"))<=1e-6,"Wald probability is inconsistent with beta/se and complete-case degrees of freedom");}rows++;
            }require(lines.next()==null,"Extra association rows");
        }
        return Map.of("association_rows_checked",rows,"lambda_boundary_fields",boundaries,"source_logl_H1_kind",likelihoodKind(s));
    }
    static List<String> associationHeader(GemmaRequest.Spec s)throws IOException{
        var header=new ArrayList<String>(List.of("chr","rs","ps","n_miss","allele1","allele0","af"));
        if(!s.mode().equals("lmm2"))header.addAll(List.of("beta","se"));if(!s.mode().equals("lmm3")||!s.edition().equals("0.98.5"))header.add("logl_H1");
        switch(s.mode()){case "lmm1"->header.addAll(List.of("l_remle","p_wald"));case "lmm2"->header.addAll(List.of("l_mle","p_lrt"));case "lmm3"->header.add("p_score");case "lmm4"->header.addAll(List.of("l_remle","l_mle","p_wald","p_lrt","p_score"));default->throw new IOException("Unqualified association mode");}
        return List.copyOf(header);
    }
    static String likelihoodKind(GemmaRequest.Spec s){return s.mode().equals("lmm1")?"REML":s.mode().equals("lmm3")?(s.edition().equals("0.98.5")?"absent":"original_uncomputed_zero_placeholder"):"ML";}
    private static String output(GemmaRequest.Spec s){return s.association()?"outputs/result.assoc.txt":"outputs/result."+(s.mode().equals("gk1")?"cXX":"sXX")+".txt";}
    private static void require(boolean yes,String message)throws IOException{if(!yes)throw new IOException(message);}
}
