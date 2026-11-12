/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import static org.jlinalg.cli.Monocle133GraphRequest.*;

/** Original 1.3.3 prepared-embedding graph learning with complete native ordering audit. */
public final class Monocle133GraphCli {
    private Monocle133GraphCli() { }
    static final String RESOURCE="/org/jlinalg/omics/monocle133-graph/";
    static final String SCOPE="Original Monocle3 1.3.3 learn_graph on the complete caller-prepared UMAP and declared cluster/partition factors; optional original order_cells with explicit roots. Other graph controls retain original defaults. Embedding/clustering learning, covariate-aware graph tests, differential models, other editions and biological root direction are separate. The selected official DESCRIPTION-change commit is not a recovered paper commit. Standalone development adapter; the canonical executable is unchanged.";
    static final String ACTION="Required action: inspect analysis-status.json and WARNINGS.md before use. Require graph_admitted=true for the graph and result_admitted=true for pseudotime. Retain complete inputs, source, RDS, RNG, process receipts and failed attempts. Supply the exact separate Linux x86_64 R4.3.3/Monocle3 1.3.3 profile and source; install only where permitted using the documented setup. If runtime, installation or execution is unavailable/forbidden, SKIP3 skips dependent analysis. FAIL2 admits no partial result: resolve the named input, integrity, scientific or capacity cause and retry NEW paths. Never infer roots, recode labels, replace nonfinite values or omit cells to force success. LEARN admits no pseudotime; inspect its graph, declare roots, and rerun RUN with the same inputs/seed plus expected_principal_graph_sha256 to check the refit. Portable --audit-graph checks supplied graph ordering only; it does not authenticate its producer or verify original graph learning/RDS.";
    private static final class Skipped extends IOException {private static final long serialVersionUID=1L;Skipped(String s){super(s);}}
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;boolean owned=false;int code=2;String cause="Input validation";boolean graph=false,result=false;
        try {
            if(args.length==1&&args[0].equals("--help")){output.println("monocle133-graph --request UTF8_JSON\nmonocle133-graph --audit-graph PREPARED_GRAPH_JSON --out NEW_DIRECTORY\nLEARN/RUN require typed JSON schema_version 1, version 1.3.3, count_units raw-integer-counts, embedding_slot UMAP; complete counts/cells/genes TSV; ordered cluster_levels and numeric partition_levels 1..K; seed, use_partition, close_loop, ncenter, minimal_branch_len, require_terminal_roots, execution_policy ALLOW|FORBID, rscript, r_libraries, source_root, and NEW out. RUN requires root_pr_nodes or root_cells.\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==4&&args[0].equals("--audit-graph")&&args[2].equals("--out")){
                Path input=Path.of(args[1]).toAbsolutePath().normalize();out=Path.of(args[3]).toAbsolutePath().normalize();newOutput(out,List.of(input));owned=true;
                var wanted=NextflowFiles.pin(input,268435456,()->{});byte[] raw=NextflowFiles.bytes(input,268435456);require(wanted.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Graph changed during capture");Files.write(out.resolve("prepared-graph.json"),raw,StandardOpenOption.CREATE_NEW);
                var audit=audit(object(NextflowParameters.parse(NextflowFiles.utf8(raw),268435456,20000000)),1000000000);same(input,wanted);
                NextflowFiles.writeJson(out.resolve("native-audit.json"),audit);cause="PASS_NATIVE_SUPPLIED_GRAPH_ORDERING_COMPARISON_ONLY";graph=false;result=false;code=0;
            }else{
                require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON, --audit-graph PATH --out NEW_DIRECTORY or --help");
                Path request=Path.of(args[1]).toAbsolutePath().normalize();var requestPin=NextflowFiles.pin(request,1048576,()->{});byte[] raw=NextflowFiles.bytes(request,1048576);require(requestPin.equals(new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw))),"Request changed during capture");
                var m=object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000));var r=parse(m,request.getParent());out=r.out();
                var protectedPaths=new ArrayList<>(List.of(request,r.counts(),r.cells(),r.genes(),r.rscript(),r.source()));protectedPaths.addAll(r.libraries());newOutput(out,protectedPaths);owned=true;Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
                if(!r.execute())throw new Skipped("Declared execution policy FORBID; no original analysis launched");
                if(!System.getProperty("os.name").equals("Linux"))throw new Skipped("Original graph learning requires Linux Java; use the portable native audit separately on this host");
                if(!Files.isExecutable(r.rscript())||!Files.isRegularFile(r.rscript()))throw new Skipped("Missing permitted executable Rscript: "+r.rscript());
                for(Path p:r.libraries())if(!Files.isDirectory(p))throw new Skipped("Missing declared R library: "+p);
                if(!Files.isDirectory(r.source()))throw new Skipped("Missing exact original 1.3.3 source tree: "+r.source());
                var profile=object(NextflowParameters.parse(NextflowFiles.utf8(resource("source-profile.json")),1048576,100000));
                var source=NextflowFiles.tree(r.source(),4096,67108864,16777216,()->{});var expected=object(profile.get("source_members"));require(source.keySet().equals(expected.keySet()),"Exact complete original source member population differs");
                for(var e:source.entrySet())require(e.getValue().kind().equals("file")&&e.getValue().pin().equals(NextflowFiles.fingerprint(expected.get(e.getKey()),16777216)),"Original source differs: "+e.getKey());
                Path capturedSource=Files.createDirectory(out.resolve("source"));for(String n:source.keySet()){Path destination=capturedSource.resolve(n);Files.createDirectories(destination.getParent());Files.copy(r.source().resolve(n),destination);same(destination,source.get(n).pin());}
                var runtime=NextflowFiles.tree(r.libraries().get(0),4096,268435456,134217728,()->{});var rscriptPin=NextflowFiles.pin(r.rscript(),16777216,()->{});
                var inputs=new LinkedHashMap<Path,NextflowFiles.Pin>();Files.createDirectory(out.resolve("inputs"));long total=0;
                for(var e:Map.of("counts.tsv",r.counts(),"cells.tsv",r.cells(),"genes.tsv",r.genes()).entrySet()){
                    var p=NextflowFiles.pin(e.getValue(),r.bytes(),()->{});total=Math.addExact(total,p.bytes());require(total<=r.bytes(),"Complete input byte capacity exceeded");inputs.put(e.getValue(),p);Files.copy(e.getValue(),out.resolve("inputs").resolve(e.getKey()));same(out.resolve("inputs").resolve(e.getKey()),p);
                }
                NextflowFiles.writeJson(out.resolve("parameters.json"),r.parameters());Files.write(out.resolve("source-profile.json"),resource("source-profile.json"),StandardOpenOption.CREATE_NEW);Files.write(out.resolve("worker.R"),resource("worker.R"),StandardOpenOption.CREATE_NEW);
                NextflowFiles.writeJson(out.resolve("runtime-provenance.json"),Map.of("rscript",r.rscript().toString(),"rscript_pin",rscriptPin,"libraries",r.libraries().stream().map(Path::toString).toList(),"primary_library_members",NextflowFiles.manifest(runtime),"source_commit",profile.get("commit"),"source_members",NextflowFiles.manifest(source),"producer_authenticated",false));
                Path bundle=out;var before=NextflowFiles.tree(out,20000,r.outputBytes(),Math.max(r.bytes(),134217728),()->{});
                NextflowProcess.Check integrity=()->{
                    same(request,requestPin);for(var e:inputs.entrySet())same(e.getKey(),e.getValue());same(r.rscript(),rscriptPin);
                    require(NextflowFiles.tree(r.source(),4096,67108864,16777216,()->{}).equals(source),"Original source changed during analysis");require(NextflowFiles.tree(r.libraries().get(0),4096,268435456,134217728,()->{}).equals(runtime),"Primary original runtime library changed during analysis");
                    for(var e:before.entrySet())if(e.getValue().kind().equals("file"))same(bundle.resolve(e.getKey()),e.getValue().pin());
                };
                var prefix=r.rscript().getParent().getParent();var env=new LinkedHashMap<String,String>();env.put("PATH",prefix.resolve("bin")+":/usr/bin:/bin");env.put("R_HOME",prefix.resolve("lib/R").toString());env.put("LD_LIBRARY_PATH",prefix.resolve("lib").toString());
                env.put("R_ENVIRON_USER","/dev/null");env.put("R_PROFILE_USER","/dev/null");for(String k:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS"))env.put(k,"1");env.put("LANG","C.UTF-8");env.put("LC_ALL","C.UTF-8");
                var command=new ArrayList<>(List.of(r.rscript().toString(),"--vanilla",out.resolve("worker.R").toString(),out.toString(),capturedSource.toString()));command.addAll(r.libraries().stream().map(Path::toString).toList());
                try{NfcoreProcess.executeSession(command,env,out.resolve("process"),out,out,new NextflowProcess.Limits(r.seconds()*1000L,17179869184L,r.seconds(),r.outputBytes(),1024,r.outputBytes(),20000,64,256,8589934592L,200),integrity,()->{});}
                catch(IOException failure){if(Files.isRegularFile(out.resolve("worker-exit.txt"))&&Files.readString(out.resolve("worker-exit.txt")).strip().equals("3"))throw new Skipped("Original runtime unavailable; read worker-error.txt: "+failure);throw failure;}
                integrity.verify();var original=object(NextflowFiles.json(out.resolve("worker-result.json"),16777216));require(text(original.get("status")).equals("PASS_ORIGINAL_MONOCLE133_GRAPH_REQUIRED_MODEL_ROUNDTRIP"),"Original model roundtrip did not pass");
                var learned=NextflowFiles.pin(out.resolve("principal-graph.json"),r.bytes(),()->{});if(m.containsKey("expected_principal_graph_sha256"))require(learned.sha256().equals(text(m.get("expected_principal_graph_sha256"))),"Learned principal graph differs from caller-declared prior graph; no result admitted");
                if(text(r.parameters().get("mode")).equals("RUN")){
                    var audit=audit(object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(out.resolve("prepared-graph.json"),r.bytes())),r.bytes(),20000000)),r.operations());NextflowFiles.writeJson(out.resolve("native-audit.json"),audit);result=true;
                }
                integrity.verify();graph=true;cause=result?"PASS_ORIGINAL_MONOCLE133_GRAPH_AND_NATIVE_ORDERING_AUDIT":"PASS_ORIGINAL_MONOCLE133_GRAPH_LEARN_ONLY_NO_PSEUDOTIME";code=0;
            }
        }catch(Skipped|NextflowProcess.Unavailable unavailable){code=3;cause="SKIP3: "+unavailable;}
        catch(Exception failure){code=2;cause="FAIL2: "+failure;}
        catch(OutOfMemoryError failure){code=2;cause="FAIL2: Java heap exhausted; no partial result admitted";}
        if(code!=0){graph=false;result=false;}
        try{if(owned){NextflowFiles.writeJson(out.resolve("analysis-status.json"),Map.of("status",cause,"exit_code",code,"graph_admitted",graph,"result_admitted",result,"required_action",ACTION,"whole_tool_qualified",false,"whole_goal_complete",false));Files.writeString(out.resolve("WARNINGS.md"),"# "+cause+"\n\n"+SCOPE+"\n\n"+ACTION+"\n\nOriginal warnings, if any, are retained in worker-result.json or worker-error.txt. RDS roundtrip covers the saved required graph/count/coordinate/label/pseudotime state; it does not certify every external nearest-neighbor pointer or every downstream Monocle operation.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}}
        catch(IOException failure){code=2;cause="FAIL2: Cannot write complete status/warnings: "+failure;}
        (code==0?output:error).println(cause+(out==null?"":"\nOutput: "+out));error.println("WARNINGS / "+ACTION);return code;
    }
    static Map<String,Object> audit(Map<String,Object> g,long operations)throws IOException {
        allowed(g,"cell_ids","cell_coordinates","principal_ids","principal_coordinates","roots","graph_vertex_ids","edge_weight_mode","edges","expected_cell_pseudotime","expected_graph_pseudotime","expected_root_indices");
        String mode=text(g.get("edge_weight_mode"));require(Set.of("weighted","unweighted").contains(mode),"Explicit graph weight mode required");var edges=new ArrayList<Monocle3Pseudotime.Edge>();
        for(Object x:list(g.get("edges"))){var e=object(x);if(mode.equals("weighted")){require(e.keySet().equals(Set.of("from","to","weight")),"Weighted edge fields differ");}else require(e.keySet().equals(Set.of("from","to")),"Unweighted edge must not contain weights");double w=mode.equals("weighted")?finite(e.get("weight")):1;require(w>=0,"Negative graph weight");edges.add(new Monocle3Pseudotime.Edge((int)integer(e.get("from"),0,999999),(int)integer(e.get("to"),0,999999),w));}
        var r=Monocle3Pseudotime.order(NextflowFiles.strings(g.get("cell_ids")),matrix(g.get("cell_coordinates")),NextflowFiles.strings(g.get("principal_ids")),matrix(g.get("principal_coordinates")),NextflowFiles.strings(g.get("roots")),NextflowFiles.strings(g.get("graph_vertex_ids")),edges,operations);
        compare(r.pseudotime(),list(g.get("expected_cell_pseudotime")));compare(r.graphPseudotime(),list(g.get("expected_graph_pseudotime")));
        var expected=list(g.get("expected_root_indices"));require(expected.size()==r.selectedRoots().size(),"Selected-root population differs");for(int i=0;i<expected.size();i++)require(integer(expected.get(i),0,999999)==r.selectedRoots().get(i).zeroBasedCellIndex(),"Original first-nearest-cell root index differs");
        var roots=new ArrayList<Map<String,Object>>();for(var root:r.selectedRoots())roots.add(Map.of("principal_point",root.principalPoint(),"zero_based_cell_index",root.zeroBasedCellIndex(),"cell_id",root.cellId(),"euclidean_distance_token",Double.toString(root.euclideanDistance())));
        var report=new LinkedHashMap<String,Object>();report.put("status","PASS_COMPLETE_NATIVE_MONOCLE133_PREPARED_GRAPH_ORDERING_COMPARISON");report.put("cells",r.cellIds().size());report.put("graph_vertices",r.graphVertexIds().size());report.put("edges",edges.size());report.put("roots",roots);report.put("infinite_cells",r.infiniteCellCount());report.put("operations",r.operations());report.put("absolute_tolerance",1e-12);report.put("relative_tolerance",1e-11);report.put("source_profile","1.3.3");report.put("producer_authenticated",false);return report;
    }
    private static double[][] matrix(Object x)throws IOException{var rows=list(x);require(rows.size()<=1000000,"Coordinate row capacity exceeded");double[][] a=new double[rows.size()][];for(int i=0;i<a.length;i++){var row=list(rows.get(i));require(row.size()<=1024,"Coordinate dimension capacity exceeded");a[i]=new double[row.size()];for(int j=0;j<a[i].length;j++)a[i][j]=finite(row.get(j));}return a;}
    private static void compare(double[] actual,List<?> expected)throws IOException{
        require(actual.length==expected.size(),"Complete original/native pseudotime population differs");for(int i=0;i<actual.length;i++){
            String token=text(expected.get(i));double v;try{v=token.equals("Inf")?Double.POSITIVE_INFINITY:token.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(token);}catch(NumberFormatException e){throw new IOException("Unadmitted original nonnumeric pseudotime token: "+token,e);}
            require(!Double.isNaN(v)&&!Double.isNaN(actual[i]),"NA/NaN pseudotime is not admitted");if(!Double.isFinite(v)||!Double.isFinite(actual[i]))require(v==actual[i],"Original/native infinity differs at "+i);else require(Math.abs(v-actual[i])<=1e-12+1e-11*Math.abs(v),"Original/native finite pseudotime differs at "+i);
        }
    }
    private static byte[] resource(String name)throws IOException{try(var input=Monocle133GraphCli.class.getResourceAsStream(RESOURCE+name)){if(input==null)throw new IOException("Missing packaged worker/profile: "+name);return input.readAllBytes();}}
    private static void same(Path path,NextflowFiles.Pin wanted)throws IOException{require(wanted.equals(NextflowFiles.pin(path,wanted.bytes(),()->{})),"Complete captured input/runtime/source changed: "+path);}
    private static void newOutput(Path out,List<Path> protectedPaths)throws IOException{require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"Output must be NEW; retain previous attempts");NextflowFiles.directory(out.getParent());for(Path p:protectedPaths)require(!out.startsWith(p)&&!p.startsWith(out),"Output overlaps protected input/runtime/source path");Files.createDirectory(out);}
}
