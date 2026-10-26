/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.DoubletScores;
import org.jlinalg.sequence.NegativeBinomialResiduals;
import org.jlinalg.sequence.SparseCounts;
import static org.jlinalg.cli.FollowupSupport.*;

/** Explicit single-cell count contracts, original Scrublet and distinct R SCT interfaces. */
final class SingleCellQcCli {
    private SingleCellQcCli() { }
    private static final Set<String> COMMON=Set.of("method","out");
    private static final Set<String> COUNTS=Set.of("matrix","features","barcodes","count-kind","feature-type","sort-records");
    private static final Set<String> EXTERNAL=Set.of("timeout","seed","dataset");
    private static final Set<String> SCRUBLET=Set.of("python","neighbors","components","simulation-ratio","expected-rate","rate-sd","umi-subsampling","approximate","metric","min-counts","min-cells","variability-percentile","log-transform","mean-center","normalize-variance","svd-solver","threshold","write-manifold");
    private static final Set<String> SCT=Set.of("rscript","r-library","flavor","fit-method","allow-nb-fallback","metadata","categorical","latent","regress","fit-genes","fit-cells","min-cells","corrected-counts","residual-type","clip-low","clip-high","variable-features","variable-only","conserve-memory","max-residual-values");
    static final class Unavailable extends IOException { private static final long serialVersionUID=1L;Unavailable(String message){super(message);} }
    static int run(String[] args,PrintStream output,PrintStream error){
        if(Arrays.asList(args).contains("--help")){output.println(help());return 0;}
        try{
            var all=new HashSet<>(COMMON);for(var set:List.of(COUNTS,EXTERNAL,SCRUBLET,SCT,Set.of("input")))all.addAll(set);var o=options(args,all);String method=required(o,"method");
            if(!Set.of("counts","doublet-score","nb-residual","scrublet","sct-vst","sct-seurat").contains(method))throw new IllegalArgumentException("Unknown single-cell-qc method");
            boolean external=method.equals("scrublet")||method.startsWith("sct-");var applicable=new HashSet<>(COMMON);
            if(Set.of("doublet-score","nb-residual").contains(method)){applicable.add("input");if(method.equals("doublet-score"))applicable.add("threshold");required(o,"input");}
            else{applicable.addAll(COUNTS);for(String key:List.of("matrix","features","barcodes"))required(o,key);}
            if(external){applicable.addAll(EXTERNAL);applicable.addAll(method.equals("scrublet")?SCRUBLET:SCT);}
            for(String key:o.keySet())if(!applicable.contains(key))throw new IllegalArgumentException("--"+key+" does not apply to "+method);
            if(external)validate(o,method);if(o.containsKey("threshold"))range(o,"threshold",0,0,1);
            int[] code={0};externalTransaction(o,"single-cell-qc-"+method,(dir,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                try{
                    if(method.equals("doublet-score"))nativeScores(o,dir,manifest);
                    else if(method.equals("nb-residual"))nativeResiduals(o,dir,manifest);
                    else execute(o,method,dir,manifest,session,warnings);
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                    if(!warnings.isEmpty())Files.writeString(dir.resolve("WARNINGS.md"),"# SINGLE-CELL ANALYSIS WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: inspect the named input, model, gene-selection, threshold and count-layer assumptions before downstream cell filtering or expression analysis. Original inputs are preserved.\n");
                }catch(Exception failure){
                    code[0]=failure instanceof Unavailable?3:2;manifest.put("analysis_status",code[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",String.valueOf(failure.getMessage()));RnaToolRuntime.quarantine(dir);
                    Files.writeString(dir.resolve("WARNINGS.md"),"# "+(code[0]==3?"ANALYSIS SKIPPED - SINGLE-CELL RUNTIME UNAVAILABLE":"SINGLE-CELL ANALYSIS FAILED")+"\n\n"+failure.getMessage()+"\n\nRequired action: "+(code[0]==3?setup(method):"Inspect retained native logs and the named count/metadata/parameter/output contract. Correct the cause and rerun to a new output directory. No automatic cell filtering or parameter reduction was performed.")+" Partial artifacts are retained under partial/; no successful results.tsv is published.\n");
                }
                session.put("warnings",warnings);Files.writeString(dir.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));Files.writeString(dir.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");
            });
            if(code[0]!=0||Files.exists(Path.of(o.get("out"),"WARNINGS.md")))error.println((code[0]==3?"SKIPPED ANALYSIS":code[0]==2?"ANALYSIS FAILED":"ANALYSIS WARNINGS")+": see "+Path.of(o.get("out"),"WARNINGS.md"));if(code[0]==0)output.println("Single-cell operation completed: "+o.get("out"));return code[0];
        }catch(Exception failure){error.println("jlinalg: "+failure.getMessage());return 2;}
    }
    private static void validate(Map<String,String> o,String method){
        String dataset=required(o,"dataset");if(!dataset.matches("[A-Za-z0-9_.-]{1,128}"))throw new IllegalArgumentException("dataset must use1..128 letters/digits/underscore/dot/hyphen");
        integer(o,"timeout",3600,1,86400);integer(o,"seed",0,0,Integer.MAX_VALUE);
        if(method.equals("scrublet")){
            required(o,"python");integer(o,"components",30,1,10000);if(o.containsKey("neighbors"))integer(o,"neighbors",1,1,1000000);
            if(number(o,"simulation-ratio",2)<=0)throw new IllegalArgumentException("simulation-ratio must be positive");double rate=number(o,"expected-rate",.1);if(rate<=0||rate>=1)throw new IllegalArgumentException("expected-rate must be strictly between0 and1");if(number(o,"rate-sd",.02)<0)throw new IllegalArgumentException("rate-sd must be nonnegative");
            double subsampling=range(o,"umi-subsampling",1,0,1);if(subsampling==0)throw new IllegalArgumentException("umi-subsampling must be positive");range(o,"variability-percentile",85,0,100);integer(o,"min-counts",3,1,Integer.MAX_VALUE);integer(o,"min-cells",3,1,Integer.MAX_VALUE);
            for(String key:List.of("approximate","log-transform","mean-center","normalize-variance","write-manifold"))bool(o,key,!key.equals("log-transform"));
            if(!Set.of("euclidean","manhattan","cosine").contains(o.getOrDefault("metric","euclidean"))||!Set.of("arpack","randomized","full").contains(o.getOrDefault("svd-solver","arpack")))throw new IllegalArgumentException("Unsupported metric or SVD solver");
            if(!bool(o,"mean-center",true)&&o.getOrDefault("svd-solver","arpack").equals("full"))throw new IllegalArgumentException("Full SVD is a centered PCA option; uncentered TruncatedSVD requires arpack or randomized");
        }else{
            required(o,"rscript");required(o,"r-library");if(!Set.of("v1","v2").contains(o.getOrDefault("flavor","v2")))throw new IllegalArgumentException("flavor must be v1 or v2");
            integer(o,"fit-genes",2000,1,2000000);integer(o,"fit-cells",method.equals("sct-seurat")?5000:2000,2,10000000);integer(o,"min-cells",5,1,10000000);integer(o,"variable-features",3000,1,2000000);
            for(String key:List.of("allow-nb-fallback","corrected-counts","variable-only","conserve-memory"))bool(o,key,Set.of("corrected-counts","variable-only").contains(key));
            if(method.equals("sct-seurat")&&bool(o,"conserve-memory",false)&&!bool(o,"variable-only",true))throw new IllegalArgumentException("Seurat conserve-memory requires variable-only true; no silent output override");
            if(o.containsKey("clip-low")!=o.containsKey("clip-high")||number(o,"clip-low",-1)>number(o,"clip-high",1))throw new IllegalArgumentException("clip-low/high must be supplied together in increasing order");
            if(Long.parseLong(o.getOrDefault("max-residual-values","50000000"))<1)throw new IllegalArgumentException("max-residual-values must be positive");
            if(method.equals("sct-seurat")&&o.keySet().stream().anyMatch(Set.of("fit-method","latent","fit-genes","residual-type")::contains))throw new IllegalArgumentException("Standalone fit-method/latent/fit-genes/residual-type do not apply to Seurat SCTransform");
            if(method.equals("sct-vst")&&o.keySet().stream().anyMatch(Set.of("regress","variable-features","variable-only","conserve-memory")::contains))throw new IllegalArgumentException("Seurat post-fit regression/variable-feature/memory options do not apply to standalone vst");
            if(o.getOrDefault("flavor","v2").equals("v2")&&o.containsKey("fit-method"))throw new IllegalArgumentException("vst flavor v2 chooses its fitting engine upstream; fit-method requires flavor v1");
            if(!Set.of("poisson","qpoisson","nb_fast","nb","glmGamPoi","glmGamPoi_offset","offset","offset_shared_theta_estimate").contains(o.getOrDefault("fit-method","poisson"))||!Set.of("pearson","deviance").contains(o.getOrDefault("residual-type","pearson")))throw new IllegalArgumentException("Unsupported fitting/residual method");
            for(String key:List.of("categorical","latent","regress"))if(o.containsKey(key))names(o.get(key));
            if((o.containsKey("regress")||o.containsKey("categorical"))&&!o.containsKey("metadata"))throw new IllegalArgumentException("Regression/categorical variables require metadata");
        }
    }
    private static double range(Map<String,String> o,String key,double fallback,double low,double high){double x=number(o,key,fallback);if(x<low||x>high)throw new IllegalArgumentException(key+" must be in ["+low+","+high+"]");return x;}
    private static List<String> names(String text){var names=List.of(text.split(",",-1));if(new HashSet<>(names).size()!=names.size()||names.stream().anyMatch(s->!s.matches("[A-Za-z][A-Za-z0-9_.]*")))throw new IllegalArgumentException("Variable names require unique simple R identifiers separated by commas");return names;}
    private static void execute(Map<String,String> o,String method,Path dir,Map<String,Object> manifest,Map<String,Object> session,List<String> warnings)throws Exception{
        boolean scrublet=method.equals("scrublet"),external=scrublet||method.startsWith("sct-");Path program=null;
        if(external){program=Path.of(o.get(scrublet?"python":"rscript")).toAbsolutePath().normalize();if(!Files.isRegularFile(program))throw new Unavailable("Runtime executable does not exist: "+program);}
        if(external&&!scrublet&&!Files.isDirectory(Path.of(o.get("r-library"))))throw new Unavailable("Explicit private R library directory does not exist: "+o.get("r-library"));
        var source=new LinkedHashMap<Path,String>();for(String key:List.of("matrix","features","barcodes","metadata"))if(o.containsKey(key)){Path p=RnaToolRuntime.file(o.get(key));source.put(p,hash(p));}
        String kind=o.getOrDefault("count-kind","raw-umi");if(!Set.of("raw-umi","estimate").contains(kind))throw new IllegalArgumentException("count-kind must be raw-umi or estimate");
        String type=o.getOrDefault("feature-type","Gene Expression");var summary=SparseCounts.canonicalize(RnaToolRuntime.file(o.get("matrix")),RnaToolRuntime.file(o.get("features")),RnaToolRuntime.file(o.get("barcodes")),dir.resolve("counts"),kind.equals("raw-umi")?SparseCounts.Kind.RAW_UMI:SparseCounts.Kind.NONNEGATIVE_ESTIMATE,type.equals("all")?null:type,integer(o,"sort-records",100000,1,10000000));
        var countRows=new ArrayList<String[]>();double[] totals=summary.cellTotals();int[] detected=summary.detectedGenes();for(int i=0;i<summary.cells().size();i++)countRows.add(new String[]{summary.cells().get(i),Double.toString(totals[i]),Integer.toString(detected[i])});table(dir.resolve("cell-counts.tsv"),List.of("cell_id","selected_feature_total","detected_features"),countRows);
        session.put("counts",Map.of("features",summary.features().size(),"cells",summary.cells().size(),"input_entries",summary.inputEntries(),"canonical_nonzero_entries",summary.outputEntries(),"excluded_features",summary.excludedFeatures(),"count_kind",kind,"feature_type",type));
        if(summary.excludedFeatures()>0)warnings.add("FEATURE TYPE SELECTION: "+summary.excludedFeatures()+" features were excluded by the explicit/default feature-type selection. Original matrix and feature table hashes are retained.");
        if(summary.features().stream().anyMatch(f->!f.type().isEmpty()&&!f.type().equals("Gene Expression")))warnings.add("NON-RNA FEATURE TYPES SELECTED: original Scrublet and SCT interfaces do not implement the modified SnapATAC2 doublet algorithm or infer a multi-assay model.");
        if(!external){if(Arrays.stream(totals).anyMatch(x->x==0))warnings.add("ZERO-COUNT CELLS RETAINED: count inspection preserves zero cells; remove or justify them explicitly before a model requiring positive cell totals.");}
        else{
            if(Arrays.stream(totals).anyMatch(x->x<=0))throw new IOException("Model input contains zero-total cells. Inspect cell-counts.tsv and apply explicit QC; no cells were silently removed");
            if(o.containsKey("metadata"))inspectMetadata(RnaToolRuntime.file(o.get("metadata")),summary.cells());
            var cfg=new LinkedHashMap<String,Object>();cfg.put("counts",dir.resolve("counts").toString());cfg.put("out",dir.toString());cfg.put("count_kind",kind);cfg.put("dataset",o.get("dataset"));cfg.put("seed",integer(o,"seed",0,0,Integer.MAX_VALUE));
            if(scrublet)scrubletConfig(o,cfg);else sctConfig(o,method,cfg,summary);
            Path script=dir.resolve(scrublet?"scrublet_qc.py":"sctransform_qc.R");String resource="/org/jlinalg/omics/"+script.getFileName();try(var input=SingleCellQcCli.class.getResourceAsStream(resource)){if(input==null)throw new IOException("Bundled worker resource missing");Files.copy(input,script);}
            Path config=dir.resolve("worker-config.json");Files.writeString(config,RegenieRuntime.json(cfg)+"\n");var protectedFiles=new LinkedHashMap<Path,String>();protectedFiles.put(program,hash(program));protectedFiles.put(config,hash(config));protectedFiles.put(script,hash(script));for(String name:List.of("matrix.mtx","features.tsv","barcodes.tsv"))protectedFiles.put(dir.resolve("counts").resolve(name),hash(dir.resolve("counts").resolve(name)));
            List<String> command=scrublet?List.of(program.toString(),"-I",script.toString(),config.toString()):List.of(program.toString(),"--vanilla",script.toString(),config.toString(),Path.of(o.get("r-library")).toAbsolutePath().normalize().toString(),dir.toString());
            int status=process(command,dir,session,integer(o,"timeout",3600,1,86400));Path info=dir.resolve("worker-status.json");
            Map<?,?> result=Files.exists(info)?RegenieRuntime.object(info):Map.of("error",Files.exists(dir.resolve("dependency-error.txt"))?Files.readString(dir.resolve("dependency-error.txt")):"Upstream worker did not publish status; inspect analysis.log");session.put("upstream",result);
            if(status==3)throw new Unavailable(String.valueOf(result.get("error")));if(status!=0||!Objects.equals(result.get("status"),"completed"))throw new IOException(String.valueOf(result.get("error")));
            if(result.get("warnings") instanceof List<?> list)for(Object warning:list)warnings.add(String.valueOf(warning));else if(result.get("warnings") instanceof String warning&&!warning.isEmpty())warnings.add(warning);
            if(scrublet)validateScrublet(dir,o,summary,result);else validateSct(dir,summary,result);
            RegenieRuntime.verify(protectedFiles);session.put("worker_sha256",hash(script));session.put("runtime_executable_sha256",hash(program));
        }
        RegenieRuntime.verify(source);var inputHashes=new LinkedHashMap<String,String>();source.forEach((p,h)->inputHashes.put(p.toString(),h));manifest.put("source_sha256",inputHashes);
        table(dir.resolve("results.tsv"),List.of("metric","value"),List.<String[]>of(new String[]{"selected_features",Integer.toString(summary.features().size())},new String[]{"cells",Integer.toString(summary.cells().size())},new String[]{"canonical_nonzero_entries",Long.toString(summary.outputEntries())},new String[]{"method",method}));
    }
    private static void scrubletConfig(Map<String,String> o,Map<String,Object> c){
        c.put("simulation_ratio",number(o,"simulation-ratio",2));c.put("neighbors",o.containsKey("neighbors")?integer(o,"neighbors",1,1,1000000):null);c.put("components",integer(o,"components",30,1,10000));c.put("expected_rate",number(o,"expected-rate",.1));c.put("rate_sd",number(o,"rate-sd",.02));c.put("umi_subsampling",number(o,"umi-subsampling",1));c.put("approximate",bool(o,"approximate",true));c.put("metric",o.getOrDefault("metric","euclidean"));c.put("min_counts",integer(o,"min-counts",3,1,Integer.MAX_VALUE));c.put("min_cells",integer(o,"min-cells",3,1,Integer.MAX_VALUE));c.put("variability_percentile",number(o,"variability-percentile",85));c.put("log_transform",bool(o,"log-transform",false));c.put("mean_center",bool(o,"mean-center",true));c.put("normalize_variance",bool(o,"normalize-variance",true));c.put("svd_solver",o.getOrDefault("svd-solver","arpack"));c.put("threshold",o.containsKey("threshold")?number(o,"threshold",0):null);c.put("write_manifold",bool(o,"write-manifold",true));
    }
    private static void sctConfig(Map<String,String> o,String method,Map<String,Object> c,SparseCounts.Summary summary)throws IOException{
        boolean seurat=method.equals("sct-seurat");int rows=seurat&&bool(o,"variable-only",true)?Math.min(summary.features().size(),integer(o,"variable-features",3000,1,2000000)):summary.features().size();long elements=Math.multiplyExact((long)rows,summary.cells().size());long maximum=Long.parseLong(o.getOrDefault("max-residual-values","50000000"));if(elements>maximum)throw new IOException("Requested residual output can contain "+elements+" values, exceeding max-residual-values="+maximum+". Select a scientifically justified feature universe/memory option or explicitly raise this limit on a host with sufficient RAM and disk");
        c.put("engine",seurat?"seurat":"vst");c.put("flavor",o.getOrDefault("flavor","v2"));c.put("fit_method",o.getOrDefault("fit-method","poisson"));c.put("allow_nb_fallback",bool(o,"allow-nb-fallback",false));c.put("metadata",o.containsKey("metadata")?Path.of(o.get("metadata")).toAbsolutePath().normalize().toString():null);c.put("categorical",o.containsKey("categorical")?names(o.get("categorical")):List.of());c.put("latent",o.containsKey("latent")?names(o.get("latent")):List.of("log_umi"));c.put("regress",o.containsKey("regress")?names(o.get("regress")):List.of());c.put("fit_genes",integer(o,"fit-genes",2000,1,2000000));c.put("fit_cells",integer(o,"fit-cells",seurat?5000:2000,2,10000000));c.put("min_cells",integer(o,"min-cells",5,1,10000000));c.put("corrected_counts",bool(o,"corrected-counts",true));c.put("residual_type",o.getOrDefault("residual-type","pearson"));c.put("clip",o.containsKey("clip-low")?List.of(number(o,"clip-low",0),number(o,"clip-high",0)):null);c.put("variable_features",integer(o,"variable-features",3000,1,2000000));c.put("variable_only",bool(o,"variable-only",true));c.put("conserve_memory",bool(o,"conserve-memory",false));
    }
    private static void inspectMetadata(Path file,List<String> cells)throws IOException{
        var ids=new HashSet<String>();try(var in=Files.newBufferedReader(file)){String header=in.readLine();if(header==null)throw new IOException("Metadata is empty");String[] columns=header.split("\t",-1);if(columns.length<2||!columns[0].equals("cell_id")||new HashSet<>(List.of(columns)).size()!=columns.length)throw new IOException("Metadata requires cell_id and unique covariate columns");String line;while((line=in.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=columns.length||!ids.add(f[0]))throw new IOException("Metadata has duplicate cells or inconsistent fields");}}if(!ids.equals(new HashSet<>(cells)))throw new IOException("Metadata must match all selected cells exactly; no positional/missing-cell imputation");
    }
    private static int process(List<String> command,Path dir,Map<String,Object> session,int timeout)throws Exception{
        session.put("command",command);var builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve("analysis.log").toFile());var env=builder.environment();env.put("LC_ALL","C");for(String key:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMBA_NUM_THREADS"))env.put(key,"1");
        Process p;try{p=builder.start();}catch(IOException e){throw new Unavailable("Runtime could not launch: "+e.getMessage());}
        try{if(!p.waitFor(timeout,TimeUnit.SECONDS)){p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();p.waitFor(30,TimeUnit.SECONDS);throw new IOException("Single-cell worker timed out; inspect retained analysis.log and dataset/model size");}session.put("exit_code",p.exitValue());return p.exitValue();}catch(InterruptedException e){p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();Thread.currentThread().interrupt();throw e;}
    }
    private static List<String[]> rows(Path path,String header)throws IOException{
        var rows=new ArrayList<String[]>();try(var in=Files.newBufferedReader(path)){if(!header.equals(in.readLine()))throw new IOException("Unexpected table header: "+path.getFileName());String line;int fields=header.split("\t",-1).length;while((line=in.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=fields)throw new IOException("Unexpected table columns: "+path.getFileName());rows.add(f);}}return rows;
    }
    private static void validateScrublet(Path dir,Map<String,String> o,SparseCounts.Summary summary,Map<?,?> info)throws IOException{
        var observed=rows(dir.resolve("observed.tsv"),"cell_id\tscore\tstandard_error\tsimulated_neighbors\tneighbors\tpredicted_doublet");if(observed.size()!=summary.cells().size())throw new IOException("Scrublet omitted/added observed cells");int simulated=((Number)info.get("simulated_cells")).intValue();double ratio=simulated/(double)observed.size();Double threshold=info.get("threshold") instanceof Number n?n.doubleValue():null;
        for(int i=0;i<observed.size();i++){var f=observed.get(i);if(!f[0].equals(summary.cells().get(i)))throw new IOException("Scrublet changed cell IDs/order");validateScore(f,1,2,3,4,ratio,o);String call=threshold==null?"NA":Boolean.toString(DoubletScores.predicted(Double.parseDouble(f[1]),threshold));if(!call.equals(f[5]))throw new IOException("Scrublet prediction differs from strict reviewed threshold");}
        var synthetic=rows(dir.resolve("simulated.tsv"),"simulated_id\tparent1\tparent2\tscore\tstandard_error\tsimulated_neighbors\tneighbors");if(synthetic.size()!=simulated)throw new IOException("Scrublet changed simulated population");var cells=new HashSet<>(summary.cells());for(int i=0;i<synthetic.size();i++){var f=synthetic.get(i);if(!f[0].equals("SIM"+i)||!cells.contains(f[1])||!cells.contains(f[2]))throw new IOException("Invalid synthetic doublet/parent IDs");validateScore(f,3,4,5,6,ratio,o);}
        var genes=rows(dir.resolve("gene-selection.tsv"),"feature_id\tselected");if(genes.size()!=summary.features().size())throw new IOException("Scrublet omitted feature selection states");for(int i=0;i<genes.size();i++)if(!genes.get(i)[0].equals(summary.features().get(i).id())||!Set.of("true","false").contains(genes.get(i)[1]))throw new IOException("Scrublet changed feature IDs/order or selection states");
        if(bool(o,"write-manifold",true)){
            validateManifold(dir.resolve("manifold-observed.tsv"),summary.cells(),integer(o,"components",30,1,10000));
            var syntheticIds=new ArrayList<String>();for(int i=0;i<simulated;i++)syntheticIds.add("SIM"+i);
            validateManifold(dir.resolve("manifold-simulated.tsv"),syntheticIds,integer(o,"components",30,1,10000));
        }
    }
    private static void validateManifold(Path path,List<String> cells,int components)throws IOException{
        StringBuilder header=new StringBuilder("cell_id");for(int i=1;i<=components;i++)header.append("\tPC").append(i);
        var values=rows(path,header.toString());if(values.size()!=cells.size())throw new IOException("Scrublet manifold changed population size");
        for(int i=0;i<values.size();i++){String[] row=values.get(i);if(!row[0].equals(cells.get(i)))throw new IOException("Scrublet manifold changed cell IDs/order");for(int j=1;j<row.length;j++)if(!Double.isFinite(Double.parseDouble(row[j])))throw new IOException("Scrublet manifold contains nonfinite coordinates");}
    }
    private static void validateScore(String[] f,int score,int error,int nd,int k,double ratio,Map<String,String> o)throws IOException{
        var value=DoubletScores.score(Integer.parseInt(f[nd]),Integer.parseInt(f[k]),ratio,number(o,"expected-rate",.1),number(o,"rate-sd",.02));double s=Double.parseDouble(f[score]),e=Double.parseDouble(f[error]);if(!Double.isFinite(s)||!Double.isFinite(e)||Math.abs(s-value.value())>2e-11||Math.abs(e-value.standardError())>2e-11)throw new IOException("Upstream doublet score/uncertainty differs from native Bayesian neighbor formula");
    }
    private static void validateSct(Path dir,SparseCounts.Summary summary,Map<?,?> info)throws IOException{
        if(!Files.readAllLines(dir.resolve("barcodes.tsv")).equals(summary.cells()))throw new IOException("SCT changed output cell IDs/order");var ids=new HashSet<String>();summary.features().forEach(f->ids.add(f.id()));
        for(String prefix:List.of("residual","corrected","logdata")){
            Path matrix=dir.resolve(prefix.equals("residual")?"residuals.mtx":prefix.equals("corrected")?"corrected-counts.mtx":"sct-logdata.mtx"),features=dir.resolve(prefix+"-features.tsv");if(!Files.exists(matrix)){if(prefix.equals("residual"))throw new IOException("Missing SCT residual matrix");continue;}
            var names=Files.readAllLines(features);if(names.isEmpty()||new HashSet<>(names).size()!=names.size()||!ids.containsAll(names))throw new IOException("SCT introduced/duplicated unknown features");inspectMatrix(matrix,names.size(),summary.cells().size(),!prefix.equals("residual"));
        }
        if(((Number)info.get("cells")).intValue()!=summary.cells().size())throw new IOException("SCT population metadata differs from selected cells");
    }
    private static void inspectMatrix(Path path,int rows,int cols,boolean nonnegative)throws IOException{
        try(var in=Files.newBufferedReader(path)){String header=in.readLine();if(header==null||!header.matches("%%MatrixMarket matrix coordinate (real|integer) general"))throw new IOException("Unexpected output matrix format");String line;do{line=in.readLine();}while(line!=null&&(line.isBlank()||line.startsWith("%")));if(line==null)throw new IOException("Missing output dimensions");String[] d=line.trim().split("\\s+");if(d.length!=3||Integer.parseInt(d[0])!=rows||Integer.parseInt(d[1])!=cols)throw new IOException("Output matrix/feature/cell dimensions differ");long expected=Long.parseLong(d[2]),count=0;while((line=in.readLine())!=null){if(line.isBlank()||line.startsWith("%"))continue;String[] f=line.trim().split("\\s+");if(f.length!=3)throw new IOException("Invalid sparse output row");int row=Integer.parseInt(f[0]),col=Integer.parseInt(f[1]);double value=Double.parseDouble(f[2]);if(row<1||row>rows||col<1||col>cols||!Double.isFinite(value)||nonnegative&&value<0)throw new IOException("Invalid output coordinate or value");count++;}if(count!=expected)throw new IOException("Output entry count differs from its header");}
    }
    private static void nativeScores(Map<String,String> o,Path dir,Map<String,Object> manifest)throws Exception{
        Path file=RnaToolRuntime.file(o.get("input"));String before=hash(file);var input=rows(file,"id\tsimulated_neighbors\tneighbors\tsimulation_ratio\texpected_rate\trate_sd");var output=new ArrayList<String[]>();var seen=new HashSet<String>();for(var f:input){if(f[0].isBlank()||!seen.add(f[0]))throw new IOException("Duplicate/empty score ID");var score=DoubletScores.score(Integer.parseInt(f[1]),Integer.parseInt(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Double.parseDouble(f[5]));output.add(new String[]{f[0],Double.toString(score.value()),Double.toString(score.standardError()),o.containsKey("threshold")?Boolean.toString(DoubletScores.predicted(score.value(),number(o,"threshold",0))):"NA"});}if(!before.equals(hash(file)))throw new IOException("Score input changed during analysis");manifest.put("input_sha256",before);table(dir.resolve("results.tsv"),List.of("id","score","standard_error","predicted_doublet"),output);
    }
    private static double scalar(String value){return value.equals("Inf")?Double.POSITIVE_INFINITY:value.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(value);}
    private static void nativeResiduals(Map<String,String> o,Path dir,Map<String,Object> manifest)throws Exception{
        Path file=RnaToolRuntime.file(o.get("input"));String before=hash(file);var input=rows(file,"id\tcount\tmean\ttheta\tminimum_variance\tclip_low\tclip_high");var output=new ArrayList<String[]>();var seen=new HashSet<String>();for(var f:input){if(f[0].isBlank()||!seen.add(f[0]))throw new IOException("Duplicate/empty residual ID");double value=NegativeBinomialResiduals.pearson(scalar(f[1]),scalar(f[2]),scalar(f[3]),scalar(f[4]),scalar(f[5]),scalar(f[6]));output.add(new String[]{f[0],Double.toString(value)});}if(!before.equals(hash(file)))throw new IOException("Residual input changed during analysis");manifest.put("input_sha256",before);table(dir.resolve("results.tsv"),List.of("id","pearson_residual"),output);
    }
    private static String setup(String method){return method.equals("scrublet")?"Where permitted use Windows x86_64 Python3.12 to run tools/literature/install_scrublet.py --allow-install --allow-source-build and supply its private Python executable. Annoy needs an existing native C++ compiler. If installation is forbidden, skip this analysis until a permitted pinned environment is supplied.":"Where permitted run Rscript --vanilla tools/literature/install_sctransform_dependencies.R build/omics-sctransform-r build/literature-review/sctransform-r-dependency allow seurat-v2 and supply that Rscript/private library. Use vst-v2 for standalone vst only. If dependencies cannot be installed or supplied, skip the affected analysis; inspect the exact package/DLL/mirror cause.";}
    static String help(){return """
        single-cell-qc --method counts|scrublet|sct-vst|sct-seurat|doublet-score|nb-residual --out NEW_DIR
        Count methods: --matrix genes-by-cells.mtx[.gz] --features features.tsv[.gz]
          --barcodes barcodes.tsv[.gz]; standard 10x ID tables WITHOUT headers. Unique feature IDs
          in column1; optional gene name/type. --feature-type 'Gene Expression' (all retains every
          type), --count-kind raw-umi|estimate, --sort-records 100000. Native disk sorting merges
          duplicates before cell totals/detected genes; no automatic cell QC or library pooling.
        scrublet: --python EXE --dataset LOADING_LIBRARY --components 30 --simulation-ratio 2
          --expected-rate .1 --rate-sd .02 [--neighbors INTEGER] --approximate true --metric euclidean
          --min-counts 3 --min-cells 3 --variability-percentile 85 --umi-subsampling 1
          --mean-center true --normalize-variance true --log-transform false --svd-solver arpack
          [--threshold NUMBER] --write-manifold true. One independent loading library per run.
          Original Scrublet0.2.3, pinned NumPy/SciPy/sklearn/Annoy; all observed/simulated scores,
          parents and selected genes retained. Native score/error verification from actual neighbor
          counts. Automatic threshold failure keeps scores with NA calls and actionable warnings.
          No SnapATAC2/Scanpy wrapper or custom diffusion/cluster-median method equivalence.
        sct-vst|sct-seurat: --rscript EXE --r-library LIBRARY --dataset ID --flavor v1|v2
          --fit-cells 2000(vst)|5000(Seurat) --min-cells 5 --corrected-counts true
          [--clip-low NUMBER --clip-high NUMBER] --allow-nb-fallback false
          [--metadata cell_id/covariate TSV --categorical comma-list]. V2 normally requires
          glmGamPoi1.24.0; absence may use explicitly allowed upstream nb_offset fallback.
          vst: --fit-genes 2000 --fit-method poisson --latent log_umi --residual-type pearson.
          Seurat: --regress comma-list --variable-features 3000 --variable-only true
          --conserve-memory false. Post-fit Seurat regression differs from vst latent variables.
          --max-residual-values 50000000 bounds output size; raise explicitly on a suitable host.
          Separate residual/corrected/log matrices, exact feature/cell maps and RDS models retained.
          No integration, RCTD deconvolution, differential expression or old-version parity inferred.
        External: --seed 0 --timeout 3600. Missing runtime/packages skip3; failures exit2; all
          warnings and failed partials retained. Raw inputs are hashed/preserved; no cells removed.
        Native doublet-score: --input TSV header id,simulated_neighbors,neighbors,simulation_ratio,
          expected_rate,rate_sd (TAB separated) [--threshold NUMBER]. Scores from supplied graph
          counts only; does not infer preprocessing, neighbors or the biological expected rate.
        Native nb-residual: --input TSV header id,count,mean,theta,minimum_variance,clip_low,clip_high;
          Inf/-Inf supported for theta/clipping. Supplied model mean/dispersion, not SCT fitting.
        """;}
}
