/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.SignacLsi;
import org.jlinalg.sequence.SignacMatrixIO;
import org.jlinalg.sequence.SignacNormalization;
import static org.jlinalg.cli.FollowupSupport.*;

/** Development typed sparse-count to original Signac LSI workflow; standalone entry point. */
public final class SignacLsiToolsCli {
    private SignacLsiToolsCli() { }
    private static final Set<String> OPTIONS=Set.of("counts","features","cells","out","rscript","r-library","r-base-library","profile","components","scale-embeddings","clip","tolerance","seed","irlba-work","tfidf-method","tfidf-scale","cutoff-count","cutoff-percentile","timeout");
    private static final Set<String> PROFILES=Set.of("1.17.1-package-rspectra","1.6.0-source-irlba","1.7.0-source-irlba","1.9.0-source-irlba","1.14.0-source-irlba");
    private static final List<String> VERSIONS=List.of("1.6.0","1.7.0","1.9.0","1.14.0","1.17.1");
    private static final String ACTION="Review WARNINGS.md, manifest.json, worker logs and effective-settings.tsv. Missing or forbidden dependencies mean SKIP3: provide a permitted exact private Signac1.17.1 / Matrix1.7.5 / SeuratObject5.4.0 / RSpectra0.16.2 / irlba2.4.1 runtime with working DLLs; where permitted run tools/literature/setup_signac_reference.py --out NEW_PATH --allow-install, otherwise skip LSI. Preserve all attempts and rerun to NEW paths after correcting the named cause. Historical source profiles are unmodified extracted functions on current dependencies, not historical package installations or full paper reproduction.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]).toAbsolutePath().normalize());emit(out,"Verified captured Signac LSI numeric integrity: "+args[1]);emit(err,"WARNING: integrity is not a signature or independent proof of historical package execution. "+ACTION);return 0;}
            Map<String,String> options=options(args,OPTIONS);
            for(String key:List.of("counts","features","cells","out","rscript","r-library","r-base-library","profile"))required(options,key);
            validateOptions(options);int[] exit={0};
            externalTransaction(options,"signac-lsi-development",(directory,manifest)->{
                List<String> warnings=new ArrayList<>();
                try{
                    Path input=Files.createDirectory(directory.resolve("input"));
                    for(String key:List.of("counts","features","cells")){
                        Path original=Path.of(options.get(key)).toAbsolutePath().normalize(),captured=input.resolve(key.equals("counts")?"counts.mtx":key+".tsv");
                        if(!Files.isRegularFile(original))throw new IOException("Input unavailable: "+key);
                        String before=hash(original);Files.copy(original,captured);if(!before.equals(hash(captured))||!before.equals(hash(original)))throw new IOException("Input changed while capturing: "+key);
                    }
                    var prepared=prepare(input,options,warnings);Path exchange=Files.createDirectory(directory.resolve("exchange"));
                    SignacMatrixIO.write(prepared.matrix(),exchange.resolve("normalized.mtx"),exchange.resolve("features.tsv"),exchange.resolve("cells.tsv"),()->false);
                    writeMetadata(directory,prepared.features());
                    Path worker=Files.createDirectory(directory.resolve("worker")),source=Files.createDirectory(worker.resolve("source"));
                    copyResource("signac_lsi.R",source.resolve("signac_lsi.R"));Path methods=Files.createDirectory(source.resolve("signac-lsi"));
                    for(String version:VERSIONS)copyResource("signac-lsi/RunSVD-"+version+".R",methods.resolve("RunSVD-"+version+".R"));
                    copyResource("signac-lsi/SOURCE-PROFILES.json",methods.resolve("SOURCE-PROFILES.json"));copyResource("signac-lsi/MIT-LICENSE.txt",methods.resolve("MIT-LICENSE.txt"));
                    Path rscript=Path.of(options.get("rscript")).toAbsolutePath().normalize();
                    if(!Files.isRegularFile(rscript))throw new DependencyUnavailable("Rscript is unavailable: "+rscript);
                    manifest.put("rscript_sha256",hash(rscript));manifest.put("runtime_admission_scope","development exact-package/function guards; not portable complete DLL/host admission");
                    List<String> command=new ArrayList<>(List.of(rscript.toString(),"--vanilla",source.resolve("signac_lsi.R").toString(),exchange.resolve("normalized.mtx").toString(),exchange.resolve("features.tsv").toString(),exchange.resolve("cells.tsv").toString(),worker.toString(),
                            Path.of(options.get("r-library")).toAbsolutePath().normalize().toString(),Path.of(options.get("r-base-library")).toAbsolutePath().normalize().toString(),options.get("profile"),
                            options.getOrDefault("components","50"),options.getOrDefault("scale-embeddings","true"),options.getOrDefault("clip","none"),options.getOrDefault("tolerance","1.0E-5"),options.getOrDefault("seed","1"),Integer.toString(options.containsKey("irlba-work")?work(options):Math.min(integer(options,"components",50,1,10000),prepared.matrix().cells().size()-1)*3)));
                    List<String> workerArguments=new ArrayList<>(command.subList(3,command.size()));
                    workerArguments.set(0,"../exchange/normalized.mtx");workerArguments.set(1,"../exchange/features.tsv");workerArguments.set(2,"../exchange/cells.tsv");workerArguments.set(3,".");
                    workerArguments=List.copyOf(workerArguments);
                    for(String value:workerArguments)if(value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IOException("R exchange paths/options cannot contain line breaks");
                    Files.writeString(worker.resolve("worker-request.txt"),String.join("\n",workerArguments)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                    // Windows R can also lose Unicode trailing argv before R code runs.
                    // Transfer values in UTF-8, using only ASCII relative child arguments.
                    command=new ArrayList<>(List.of(rscript.toString(),"--vanilla","source/signac_lsi.R","--request-file","worker-request.txt"));
                    manifest.put("command",command);manifest.put("worker_arguments",workerArguments);manifest.put("requested_profile",options.get("profile"));
                    ProcessBuilder builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve("stdout.log").toFile()).redirectError(worker.resolve("stderr.log").toFile());
                    // This Windows R runtime crashes during shutdown under a nearly empty
                    // environment, even for library(Signac) alone. Preserve host OS variables;
                    // disable R/profile/library injection and set deterministic locale/threads.
                    Set<String> removed=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS");
                    builder.environment().keySet().removeIf(key->key.startsWith("LC_")||removed.contains(key));
                    builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
                    Process process;
                    try{process=builder.start();}catch(IOException unavailable){throw new DependencyUnavailable("Could not start the supplied Rscript",unavailable);}
                    boolean finished;
                    try{finished=process.waitFor(integer(options,"timeout",3600,1,86400),TimeUnit.SECONDS);}
                    catch(InterruptedException interrupted){stop(process);Thread.currentThread().interrupt();throw interrupted;}
                    if(!finished){stop(process);throw new IOException("Signac LSI timed out; owned process was terminated. Retain logs and retry a justified timeout in a NEW directory");}
                    manifest.put("process_exit_code",process.exitValue());
                    var state=table(worker.resolve("worker-status.tsv"),List.of("status","detail","required_action"),1).get(0);
                    if(process.exitValue()!=0){if(state[0].equals("skipped-dependency-unavailable")&&process.exitValue()==3)throw new DependencyUnavailable(state[1]);throw new IOException("Original SVD failed: "+state[1]);}
                    if(!Set.of("completed","completed-with-warnings").contains(state[0]))throw new IOException("Worker status/exit disagreement");
                    List<String> workerWarnings=Files.readAllLines(worker.resolve("worker-warnings.tsv"),StandardCharsets.UTF_8);
                    if(workerWarnings.isEmpty()||!workerWarnings.get(0).equals("warning"))throw new IOException("Missing worker warning protocol");warnings.addAll(workerWarnings.subList(1,workerWarnings.size()));
                    if(!options.get("profile").equals("1.17.1-package-rspectra"))warnings.add("HISTORICAL_SOURCE_PROFILE: historical source function executed with current dependencies; full historical package and complete paper reproduction are not claimed.");
                    Map<String,Object> diagnostics=validateNumerics(directory,prepared.matrix(),options,warnings);
                    for(String name:List.of("effective-settings.tsv","installed-packages.tsv","session-info.txt","fit.rds"))if(!Files.isRegularFile(worker.resolve(name))||Files.size(worker.resolve(name))==0)throw new IOException("Worker omitted provenance: "+name);
                    manifest.put("diagnostics",diagnostics);manifest.put("selected_features",prepared.matrix().features().size());manifest.put("cells",prepared.matrix().cells().size());
                    manifest.put("historical_packages_installed",false);manifest.put("whole_Signac_workflow_certified",false);manifest.put("biological_accuracy_certified",false);
                    for(String name:List.of("embeddings.tsv","loadings.tsv","singular-values.tsv"))Files.copy(worker.resolve(name),directory.resolve("result-"+name));
                    manifest.put("analysis_status",warnings.isEmpty()?"completed":"completed-with-warnings");
                }catch(Exception failure){
                    exit[0]=failure instanceof DependencyUnavailable?3:2;manifest.put("analysis_status",exit[0]==3?"skipped-dependency-unavailable":"failed");manifest.put("error",failure.toString());
                    warnings.add((exit[0]==3?"SKIP3: DEPENDENT LSI ANALYSIS SKIPPED: ":"FAIL2: NO LSI RESULT ADMITTED: ")+failure);
                    for(String name:List.of("embeddings.tsv","loadings.tsv","singular-values.tsv"))if(Files.exists(directory.resolve("result-"+name)))Files.move(directory.resolve("result-"+name),directory.resolve("retained-partial-"+name));
                }
                warnings.add("DEVELOPMENT_SCOPE: sparse count normalization, feature selection and LSI only. No fragment counting, QC, motifs, gene activity, links, WNN, clustering or full Signac integration is admitted.");
                Files.writeString(directory.resolve("WARNINGS.md"),"# "+(exit[0]==3?"SKIP3 — SIGNAC LSI ANALYSIS SKIPPED":exit[0]==2?"FAIL2 — NO SIGNAC LSI RESULT ADMITTED":"SIGNAC LSI WARNINGS AND SCOPE")+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                manifest.put("required_action",ACTION);manifest.put("warnings",warnings);Files.writeString(directory.resolve("analysis-status.json"),NextflowJson.json(Map.of("status",manifest.get("analysis_status"))),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Files.writeString(directory.resolve("manifest.json"),NextflowJson.json(manifest),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            });
            Path destination=Path.of(options.get("out")).toAbsolutePath().normalize();seal(destination);
            if(exit[0]==0){verify(destination);emit(out,"Completed development Signac LSI: "+destination);}else emit(err,(exit[0]==3?"SKIP3: ANALYSIS SKIPPED":"FAIL2: NO RESULT ADMITTED")+"; see "+destination.resolve("WARNINGS.md"));
            emit(err,"SIGNAC LSI WARNINGS / REQUIRED ACTION: "+destination.resolve("WARNINGS.md")+"\n"+ACTION);return exit[0];
        }catch(Exception failure){emit(err,"FAIL2: Signac LSI request/verification failed: "+failure+"\n"+ACTION);return 2;}
    }
    private record Prepared(SignacMatrixIO.Selected matrix,SignacNormalization.TopFeatures features){ }
    private static Prepared prepare(Path input,Map<String,String> options,List<String> warnings)throws IOException{
        var intake=SignacMatrixIO.readCounts(input.resolve("counts.mtx"),input.resolve("features.tsv"),input.resolve("cells.tsv"),SignacMatrixIO.Limits.defaults(),()->false);
        if(intake.duplicateCoordinates()>0)warnings.add("DUPLICATE_COORDINATES_SUMMED: "+intake.duplicateCoordinates()+" duplicate entries were summed exactly; explicit zeros retained.");
        var normalized=SignacNormalization.runTfidf(intake.counts(),SignacNormalization.Method.values()[integer(options,"tfidf-method",1,1,4)-1],number(options,"tfidf-scale",1e4),null);
        for(var warning:normalized.warnings())warnings.add(warning.code()+": "+warning.message()+" Required action: "+warning.requiredAction());
        var cutoff=options.containsKey("cutoff-count")?SignacNormalization.Cutoff.count(number(options,"cutoff-count",0)):SignacNormalization.Cutoff.percentile(number(options,"cutoff-percentile",5));
        var features=SignacNormalization.findTopFeatures(intake.counts(),cutoff,null);var selected=SignacMatrixIO.select(normalized.matrix(),features.selected(),()->false);
        int effective=Math.min(integer(options,"components",50,1,10000),selected.cells().size()-1);
        if(effective<1||effective>=Math.min(selected.features().size(),selected.cells().size()))throw new IOException("Requested SVD rank is not below both selected matrix dimensions after upstream cell-based clamping; choose explicit justified components/features, never silently lower the rank");
        if(((long)selected.features().size()+selected.cells().size())*effective>50_000_000L)throw new IOException("Singular-triplet output exceeds 50 million values; choose justified dimensions or a separately admitted larger workflow");
        return new Prepared(selected,features);
    }
    private static void validateOptions(Map<String,String> options){
        if(!PROFILES.contains(required(options,"profile")))throw new IllegalArgumentException("Select an explicit supported package or source-function profile");
        if(options.containsKey("cutoff-count")&&options.containsKey("cutoff-percentile"))throw new IllegalArgumentException("Choose one feature cutoff kind");
        integer(options,"components",50,1,10000);integer(options,"tfidf-method",1,1,4);integer(options,"seed",1,0,Integer.MAX_VALUE);integer(options,"timeout",3600,1,86400);work(options);bool(options,"scale-embeddings",true);
        double tol=number(options,"tolerance",1e-5);if(tol<=0||tol>0.01)throw new IllegalArgumentException("Tolerance must be in (0,0.01]");
        if(number(options,"tfidf-scale",1e4)<=0)throw new IllegalArgumentException("TF-IDF scale must be positive");
        if(options.containsKey("clip")&&number(options,"clip",1)<=0)throw new IllegalArgumentException("Clipping must be positive");
        if(options.containsKey("cutoff-count"))SignacNormalization.Cutoff.count(number(options,"cutoff-count",0));
        if(options.containsKey("cutoff-percentile"))SignacNormalization.Cutoff.percentile(number(options,"cutoff-percentile",5));
        if(options.get("profile").equals("1.17.1-package-rspectra")&&options.containsKey("irlba-work"))throw new IllegalArgumentException("irlba-work does not apply to RSpectra");
    }
    private static int work(Map<String,String> options){return integer(options,"irlba-work",integer(options,"components",50,1,10000)*3,1,100000);}
    private static Map<String,Object> validateNumerics(Path directory,SignacMatrixIO.Selected matrix,Map<String,String> options,List<String> warnings)throws IOException{
        Path worker=directory.resolve("worker");int n=Math.min(integer(options,"components",50,1,10000),matrix.cells().size()-1);
        double[][] u=matrix(worker.resolve("raw-u.tsv"),matrix.cells(),n),v=matrix(worker.resolve("loadings.tsv"),matrix.features(),n),embeddings=matrix(worker.resolve("embeddings.tsv"),matrix.cells(),n);
        var singular=table(worker.resolve("singular-values.tsv"),List.of("component","singular_value","stdev"),n);double[] d=new double[n],sd=new double[n];
        for(int i=0;i<n;i++){if(!singular.get(i)[0].equals(Integer.toString(i+1)))throw new IOException("Unexpected singular-component order");d[i]=finite(singular.get(i)[1],"singular value");sd[i]=finite(singular.get(i)[2],"standard deviation");}
        var post=SignacLsi.postprocess(u,d,matrix.features().size(),bool(options,"scale-embeddings",true),options.containsKey("clip")?number(options,"clip",0):null,()->false);
        warnings.addAll(post.warnings());double[][] expected=post.values();double[] expectedSd=post.standardDeviations();
        for(int k=0;k<n;k++){same(expectedSd[k],sd[k]);for(int row=0;row<embeddings.length;row++)same(expected[row][k],embeddings[row][k]);}
        var diagnostics=SignacLsi.diagnose(matrix.features().size(),matrix.cells().size(),matrix.pointers(),matrix.rows(),matrix.values(),u,v,d,()->false);
        // Historical1.6/1.7 do not forward tol; their irlba default is still1e-5.
        double tolerance=options.get("profile").startsWith("1.6.0-")||options.get("profile").startsWith("1.7.0-")?1e-5:number(options,"tolerance",1e-5);
        double residualLimit=Math.max(1e-7,50*tolerance);
        if(!Double.isFinite(diagnostics.maximumRelativeLeftResidual())||!Double.isFinite(diagnostics.maximumRelativeRightResidual())||diagnostics.maximumRelativeLeftResidual()>residualLimit||diagnostics.maximumRelativeRightResidual()>residualLimit
                ||diagnostics.maximumUOrthogonalityError()>1e-6||diagnostics.maximumVOrthogonalityError()>1e-6)throw new IOException("Original singular triplets failed independent sparse residual/orthogonality checks: "+diagnostics);
        return Map.of("maximum_relative_left_residual",diagnostics.maximumRelativeLeftResidual(),"maximum_relative_right_residual",diagnostics.maximumRelativeRightResidual(),"maximum_U_orthogonality_error",diagnostics.maximumUOrthogonalityError(),"maximum_V_orthogonality_error",diagnostics.maximumVOrthogonalityError(),"residual_limit",residualLimit,"orthogonality_limit",1e-6);
    }
    private static void same(double expected,double actual)throws IOException{if(!Double.isFinite(expected)||!Double.isFinite(actual)||Math.abs(expected-actual)>Math.max(32*Math.ulp(expected),Math.max(1,Math.abs(expected))*3e-12))throw new IOException("Nonfinite or source-inconsistent LSI postprocessing");}
    private static double[][] matrix(Path path,List<String> identities,int components)throws IOException{
        List<String> header=new ArrayList<>(List.of("id"));for(int i=1;i<=components;i++)header.add("LSI_"+i);
        var rows=table(path,header,identities.size());double[][] values=new double[rows.size()][components];
        for(int row=0;row<rows.size();row++){if(!rows.get(row)[0].equals(identities.get(row)))throw new IOException("LSI changed identities/order");for(int col=0;col<components;col++)values[row][col]=finite(rows.get(row)[col+1],"LSI matrix value");}return values;
    }
    private static List<String[]> table(Path path,List<String> header,int rows)throws IOException{
        if(!Files.isRegularFile(path)||Files.size(path)>2_000_000_000L)throw new IOException("Missing/excessive worker table: "+path.getFileName());
        List<String[]> result=new ArrayList<>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String first=reader.readLine();if(first==null||!Arrays.asList(first.split("\t",-1)).equals(header))throw new IOException("Worker table header mismatch: "+path.getFileName());
            String line;while((line=reader.readLine())!=null){if(result.size()>=rows||line.length()>1_000_000)throw new IOException("Excessive worker table rows/line");String[] fields=line.split("\t",-1);if(fields.length!=header.size())throw new IOException("Ragged worker table");result.add(fields);}
        }if(result.size()!=rows)throw new IOException("Worker table row count mismatch: "+path.getFileName());return result;
    }
    private static void writeMetadata(Path dir,SignacNormalization.TopFeatures top)throws IOException{
        try(var out=Files.newBufferedWriter(dir.resolve("feature-metadata.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("feature\tcount\tpercentile\tselected\n");Set<String> selected=Set.copyOf(top.selected());for(var f:top.metadata())out.write(f.id()+"\t"+f.count()+"\t"+f.percentile()+"\t"+selected.contains(f.id())+"\n");
        }
    }
    private static void copyResource(String resource,Path target)throws IOException{try(var in=SignacLsiToolsCli.class.getResourceAsStream("/org/jlinalg/omics/"+resource)){if(in==null)throw new IOException("Missing bundled Signac source: "+resource);Files.copy(in,target);}}
    private static void stop(Process p)throws InterruptedException{p.descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();if(!p.waitFor(10,TimeUnit.SECONDS))throw new IllegalStateException("Owned Signac process did not stop; inspect its PID "+p.pid());}
    private static void seal(Path root)throws IOException{
        try(var files=Files.walk(root);var out=Files.newBufferedWriter(root.resolve("artifact-checksums.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("sha256\tpath\n");for(Path path:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("artifact-checksums.tsv")).sorted().toList())out.write(hash(path)+"\t"+root.relativize(path).toString().replace('\\','/')+"\n");
        }
    }
    private static void verify(Path root)throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Captured analysis is not a plain directory");
        Set<String> seen=new java.util.HashSet<>();List<String> lines=Files.readAllLines(root.resolve("artifact-checksums.tsv"),StandardCharsets.UTF_8);if(lines.isEmpty()||!lines.get(0).equals("sha256\tpath"))throw new IOException("Missing captured checksum protocol");
        for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t",-1);if(f.length!=2||!f[0].matches("[0-9a-f]{64}"))throw new IOException("Malformed checksum row");Path relative=Path.of(f[1]);if(relative.isAbsolute()||f[1].contains("\\")||f[1].contains(":")||Arrays.stream(f[1].split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IOException("Unsafe captured path");Path file=root.resolve(relative).normalize();if(!file.startsWith(root)||!seen.add(f[1])||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!hash(file).equals(f[0]))throw new IOException("Captured file checksum mismatch: "+f[1]);}
        try(var files=Files.walk(root)){for(Path path:files.toList()){if(Files.isSymbolicLink(path))throw new IOException("Captured symlinks forbidden");if(Files.isRegularFile(path)&&!path.getFileName().toString().equals("artifact-checksums.tsv")&&!seen.contains(root.relativize(path).toString().replace('\\','/')))throw new IOException("Uninventoried captured artifact");}}
        Object parsed=SimpleJson.parse(Files.readString(root.resolve("manifest.json"),StandardCharsets.UTF_8));if(!(parsed instanceof Map<?,?> manifest)||!Set.of("completed","completed-with-warnings").contains(manifest.get("analysis_status")))throw new IOException("Captured analysis did not complete");
        if(!(manifest.get("options") instanceof Map<?,?> raw))throw new IOException("Missing typed options");Map<String,String> options=new LinkedHashMap<>();for(var entry:raw.entrySet()){if(!(entry.getKey() instanceof String key)||!(entry.getValue() instanceof String value)||!OPTIONS.contains(key))throw new IOException("Invalid captured option");options.put(key,value);}validateOptions(options);
        for(String resource:List.of("signac_lsi.R","signac-lsi/SOURCE-PROFILES.json","signac-lsi/MIT-LICENSE.txt"))verifyResource(root,resource);
        for(String version:VERSIONS)verifyResource(root,"signac-lsi/RunSVD-"+version+".R");
        List<String> warnings=new ArrayList<>();var prepared=prepare(root.resolve("input"),options,warnings);validateExchange(root,prepared);
        Map<String,Object> diagnostics=validateNumerics(root,prepared.matrix(),options,warnings);
        if(!(manifest.get("diagnostics") instanceof Map<?,?> capturedDiagnostics))throw new IOException("Missing independent diagnostics");
        for(var entry:diagnostics.entrySet()){Object captured=capturedDiagnostics.get(entry.getKey());if(!(captured instanceof Number n))throw new IOException("Missing diagnostic value");same(((Number)entry.getValue()).doubleValue(),n.doubleValue());}
        Object status=SimpleJson.parse(Files.readString(root.resolve("analysis-status.json"),StandardCharsets.UTF_8));if(!(status instanceof Map<?,?> m)||!manifest.get("analysis_status").equals(m.get("status")))throw new IOException("Captured analysis-status disagreement");
        if(!(manifest.get("process_exit_code") instanceof Number code)||code.intValue()!=0)throw new IOException("Original process did not exit successfully");
        var workerStatus=table(root.resolve("worker/worker-status.tsv"),List.of("status","detail","required_action"),1).get(0);if(!Set.of("completed","completed-with-warnings").contains(workerStatus[0]))throw new IOException("Captured worker did not complete");
        validateSettings(root.resolve("worker/effective-settings.tsv"),options,prepared.matrix());
        for(String name:List.of("embeddings.tsv","loadings.tsv","singular-values.tsv"))if(Files.mismatch(root.resolve("worker").resolve(name),root.resolve("result-"+name))!=-1L)throw new IOException("Published LSI table differs from captured original");
    }
    private static void validateExchange(Path root,Prepared prepared)throws IOException{
        var matrix=prepared.matrix();Path exchange=root.resolve("exchange");
        if(!Files.readAllLines(exchange.resolve("features.tsv"),StandardCharsets.UTF_8).equals(matrix.features())||!Files.readAllLines(exchange.resolve("cells.tsv"),StandardCharsets.UTF_8).equals(matrix.cells()))throw new IOException("Captured exchange changed identities/order");
        int[] p=matrix.pointers(),rows=matrix.rows();double[] values=matrix.values();
        try(var reader=Files.newBufferedReader(exchange.resolve("normalized.mtx"),StandardCharsets.UTF_8)){
            requireLine(reader,"%%MatrixMarket matrix coordinate real general");requireLine(reader,"% Native Signac normalization; explicit zeros retained");requireLine(reader,matrix.features().size()+" "+matrix.cells().size()+" "+values.length);
            for(int cell=0;cell<matrix.cells().size();cell++)for(int k=p[cell];k<p[cell+1];k++)requireLine(reader,(rows[k]+1)+" "+(cell+1)+" "+Double.toString(values[k]));
            if(reader.readLine()!=null)throw new IOException("Extra normalized exchange rows");
        }
        try(var reader=Files.newBufferedReader(root.resolve("feature-metadata.tsv"),StandardCharsets.UTF_8)){
            requireLine(reader,"feature\tcount\tpercentile\tselected");Set<String> selected=Set.copyOf(prepared.features().selected());
            for(var f:prepared.features().metadata())requireLine(reader,f.id()+"\t"+f.count()+"\t"+f.percentile()+"\t"+selected.contains(f.id()));
            if(reader.readLine()!=null)throw new IOException("Extra feature-metadata rows");
        }
    }
    private static void requireLine(java.io.BufferedReader reader,String expected)throws IOException{if(!expected.equals(reader.readLine()))throw new IOException("Captured exchange/metadata differs from replayed native normalization");}
    private static void validateSettings(Path path,Map<String,String> options,SignacMatrixIO.Selected matrix)throws IOException{
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);if(lines.isEmpty()||!lines.get(0).equals("setting\tvalue"))throw new IOException("Missing effective settings");
        Map<String,String> settings=new HashMap<>();for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t",-1);if(f.length!=2||settings.putIfAbsent(f[0],f[1])!=null)throw new IOException("Invalid effective setting row");}
        String profile=options.get("profile");boolean current=profile.equals("1.17.1-package-rspectra");int n=integer(options,"components",50,1,10000),effective=Math.min(n,matrix.cells().size()-1);
        Map<String,String> expected=Map.of("profile",profile,"full_package","1.17.1","solver",current?"RSpectra::svds":"irlba::irlba","scale_embeddings",bool(options,"scale-embeddings",true)?"TRUE":"FALSE","historical_packages_installed","false","tolerance_forwarded",profile.startsWith("1.6.0-")||profile.startsWith("1.7.0-")?"false (irlba default)":"true");
        for(var e:expected.entrySet())if(!e.getValue().equals(settings.get(e.getKey())))throw new IOException("Source-profile effective setting mismatch: "+e.getKey());
        Map<String,Double> numeric=Map.of("requested_components",(double)n,"effective_components",(double)effective,"tolerance_argument",number(options,"tolerance",1e-5),"seed",(double)integer(options,"seed",1,0,Integer.MAX_VALUE),"irlba_work_argument",(double)(options.containsKey("irlba-work")?work(options):effective*3),"standard_deviation_denominator_features",(double)Math.max(1,matrix.features().size()-1));
        for(var e:numeric.entrySet()){String value=settings.get(e.getKey());if(value==null)throw new IOException("Missing effective numeric setting");same(e.getValue(),finite(value,e.getKey()));}
        if(options.containsKey("clip"))same(number(options,"clip",1),finite(settings.get("clip"),"clip"));else if(!"none".equals(settings.get("clip")))throw new IOException("Unexpected clipping");
    }
    private static void verifyResource(Path root,String resource)throws IOException{try(var in=SignacLsiToolsCli.class.getResourceAsStream("/org/jlinalg/omics/"+resource)){if(in==null||!Arrays.equals(in.readAllBytes(),Files.readAllBytes(root.resolve("worker/source").resolve(resource))))throw new IOException("Captured worker/source profile differs from this verifier");}}
    private static final class DependencyUnavailable extends IOException{private static final long serialVersionUID=1L;DependencyUnavailable(String m){super(m);}DependencyUnavailable(String m,Throwable c){super(m,c);}}
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        SignacLsiToolsCli --counts COUNTS.mtx --features FEATURE_IDS.tsv --cells CELL_IDS.tsv
          --rscript EXE --r-library PRIVATE_LIB --r-base-library READ_ONLY_LIB
          --profile 1.17.1-package-rspectra|1.6.0-source-irlba|1.7.0-source-irlba|1.9.0-source-irlba|1.14.0-source-irlba --out NEW_DIR
          --components 50 --tfidf-method 1 --tfidf-scale 10000
          --cutoff-percentile 5 | --cutoff-count N
          --scale-embeddings true --clip POSITIVE_BOUND --tolerance 0.00001 --seed 1
          --irlba-work POSITIVE_INTEGER (historical source profiles only) --timeout 3600
        SignacLsiToolsCli --verify CAPTURED_DIR | --help
        Input: uncompressed integer Matrix Market, features by cells; one exact ID per line.
        Duplicate coordinates are summed exactly; stored zeros retained. No automatic QC/filtering.
        Native normalization/selection + original SVD; only the LSI operation is admitted.
        Standalone development command; canonical executable/root dispatch and catalog are unchanged.
        """+ACTION;}
}
