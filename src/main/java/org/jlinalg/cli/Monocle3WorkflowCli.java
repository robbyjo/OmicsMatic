/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Original current Monocle3 with native prepared-graph auditing and atomic admission. */
public final class Monocle3WorkflowCli {
    private Monocle3WorkflowCli() { }
    static final String ACTION="Preserve this attempt. Read WARNINGS.md, analysis-status.json, worker/worker-report.json, worker/cases/ID/source-status.json and stdout/stderr. SKIP3: supply exact R/platform/private dependencies from source/SOURCE-PROFILE.json where installation is permitted; otherwise skip the dependent analysis. This command installs nothing and substitutes no edition. FAIL2: no result in this atomic batch is admitted, including completed/partial R tables. Correct the named input, original model error, source or capacity cause and retry into NEW paths. Do not drop genes, reorder IDs, replace Inf/NA, or use TPM/activity as counts. Development CLI only; no portable VERIFY command is provided.";
    static final String SCOPE="Original Monocle3 1.4.27 raw integer counts: optional PCA/LSI, UMAP, clustering, graph learning/order, additive or natural-spline gene models, and stock Moran graph_test. Full original objects/tables and warnings are retained. Native Java audits fitted prepared-graph pseudotime only; it does not refit graphs or gene models. Exact versions and selected R bodies/formals are checked; native DLLs and complete dependency source are not authenticated. Historical editions, Monocle2 differentialGeneTest, Cicero, py-monocle, activity inputs, batch alignment and custom covariate graph_test are outside this interface. Logical capacities/polling are not OS heap or disk quotas. No whole-paper reproduction or biological certification is claimed.";
    record Population(List<String> genes,List<String> cells) { }
    record Captured(Path original,Path copy,NextflowFiles.Pin pin) {
        void verify()throws IOException {require(pin.equals(NextflowFiles.pin(original,pin.bytes(),()->{}))&&pin.equals(NextflowFiles.pin(copy,pin.bytes(),()->{})),"Original or captured input changed: "+original);}
    }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        try {
            if(args.length==1&&args[0].equals("--help")){
                emit(output,"Monocle3WorkflowCli --request ASCII_PATH.json\nUTF-8 JSON paths resolve against the request. Require schema_version 1, mode RUN, exact version, count_units raw-integer-counts, counts/cell_metadata/gene_metadata TSV, metadata_types, rscript/r_libraries, analyses and NEW out.\nCounts: gene_id then literal ordered cell IDs. Metadata: cell_id then declared covariates; gene metadata: gene_id<TAB>gene_short_name. TSV inputs are unquoted literal fields; empty/control/duplicate IDs, implicit NA, reordering and imputation are refused.\nEach analysis declares id, seed and trajectory and/or fit_models; graph_test requires trajectory. All options in the development guide are explicit. Any failed analysis/gene status rejects the batch.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();var sourcePin=NextflowFiles.pin(source,1048576,()->{});byte[] raw=NextflowFiles.bytes(source,1048576);
            require(sourcePin.bytes()==raw.length&&sourcePin.sha256().equals(NextflowFiles.sha(raw)),"Request changed during intake");
            Request request=parse(object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000)),source.getParent());
            for(Path library:request.libraries())require(!request.out().startsWith(library)&&!library.startsWith(request.out()),"Output cannot overlap a declared R library");
            int[] exit={0};FollowupSupport.externalTransaction(Map.of("out",request.out().toString()),"original-monocle3-workflow-development",(stage,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("result_admitted",false);
                try {
                    verifyRequest(source,sourcePin);Files.write(stage.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(stage.resolve("effective-request.json"),request.effective());
                    Path inputs=Files.createDirectory(stage.resolve("inputs"));var captured=new ArrayList<Captured>();long remaining=request.inputBytes();
                    for(var item:List.of(Map.entry(request.counts(),"counts.tsv"),Map.entry(request.cells(),"cell-metadata.tsv"),Map.entry(request.genes(),"gene-metadata.tsv"))){
                        var c=capture(item.getKey(),inputs.resolve(item.getValue()),remaining);captured.add(c);remaining-=c.pin().bytes();
                    }
                    Population population=population(inputs,request);manifest.put("source_request_sha256",sourcePin.sha256());
                    var inputPins=new LinkedHashMap<String,Object>();for(var c:captured)inputPins.put(c.copy().getFileName().toString(),Map.of("bytes",c.pin().bytes(),"sha256",c.pin().sha256()));manifest.put("captured_inputs",inputPins);
                    Path worker=Files.createDirectory(stage.resolve("worker"));NextflowFiles.writeJson(worker.resolve("parameters.json"),request.parameters());
                    Monocle3WorkflowRuntime.sources(stage,"1.4.27",false);Monocle3WorkflowRuntime.run(stage,request,manifest);
                    var wr=read(worker.resolve("worker-report.json"),request.jsonBytes());appendDiagnostics(wr,warnings);
                    for(Captured c:captured)c.verify();verifyRequest(source,sourcePin);Monocle3WorkflowRuntime.sources(stage,"1.4.27",true);
                    var ledger=read(worker.resolve("input-ledger.json"),request.jsonBytes());require(strings(ledger.get("cell_ids"),1000000).equals(population.cells())&&strings(ledger.get("gene_ids"),1000000).equals(population.genes()),"Original constructor input axes differ from captured counts");
                    var reports=new ArrayList<Map<String,Object>>();
                    for(var a:request.analyses())reports.add(audit(worker.resolve("cases").resolve(text(a.get("id"))),a,population,request,warnings));
                    Path results=Files.createDirectory(stage.resolve("results"));
                    for(var a:request.analyses())copyTree(worker.resolve("cases").resolve(text(a.get("id"))),results.resolve(text(a.get("id"))));
                    for(Captured c:captured)c.verify();verifyRequest(source,sourcePin);Monocle3WorkflowRuntime.sources(stage,"1.4.27",true);
                    NextflowFiles.writeJson(stage.resolve("report.json"),Map.of("version",request.version(),"genes",population.genes().size(),"cells",population.cells().size(),"analyses",reports,"scope",SCOPE,"analysis_installs_software",false));
                    state.put("status","completed-with-warnings");state.put("result_admitted",true);state.put("analyses",reports.size());
                } catch(Monocle3WorkflowRuntime.Unavailable failure){exit[0]=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("cause",failure.toString());warnings.add("SKIP3: "+failure);}
                catch(Exception failure){exit[0]=2;state.put("cause",failure.toString());warnings.add("FAIL2: "+failure);}
                catch(OutOfMemoryError failure){exit[0]=2;state.put("cause","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted; partial results unadmitted. Retry suitable limits/heap into NEW paths.");}
                if(exit[0]!=0){
                    if(Files.exists(stage.resolve("results")))Files.move(stage.resolve("results"),stage.resolve("unadmitted-partial-results"));
                    if(Files.exists(stage.resolve("report.json")))Files.move(stage.resolve("report.json"),stage.resolve("unadmitted-report.json"));
                    Path wr=stage.resolve("worker/worker-report.json");if(Files.isRegularFile(wr))try{appendDiagnostics(read(wr,request.jsonBytes()),warnings);}catch(IOException failure){warnings.add("Worker diagnostic read failed: "+failure);}
                }
                state.put("required_action",ACTION);state.put("version",request.version());state.put("atomic_batch",true);
                NextflowFiles.writeJson(stage.resolve("analysis-status.json"),state);
                Files.writeString(stage.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                manifest.put("analysis_status",state.get("status"));manifest.put("result_admitted",state.get("result_admitted"));manifest.put("version",request.version());manifest.put("scope",SCOPE);
            });
            emit(exit[0]==0?output:error,(exit[0]==0?"Original Monocle3 completed: ":exit[0]==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 NO RESULT ADMITTED: ")+request.out());
            emit(error,"WARNINGS / REQUIRED ACTIONS: "+request.out().resolve("WARNINGS.md")+"\n"+ACTION);return exit[0];
        } catch(Exception failure){emit(error,"FAIL2 NO RESULT ADMITTED: "+failure+"\n"+ACTION);return 2;}
        catch(OutOfMemoryError failure){emit(error,"FAIL2 NO RESULT ADMITTED: Java heap exhausted. "+ACTION);return 2;}
    }
    static Population population(Path inputs,Request r)throws IOException {
        var counts=tsv(inputs.resolve("counts.tsv"),r);String[] header=counts.get(0);require(header.length>=3&&header[0].equals("gene_id"),"Counts header: gene_id then at least two ordered cell IDs");
        var cells=unique(Arrays.asList(header).subList(1,header.length));var genes=new ArrayList<String>();var geneSet=new HashSet<String>();
        require((long)(counts.size()-1)*cells.size()<=r.maximumEntries(),"Count matrix exceeds maximum_entries");double[] totals=new double[cells.size()];
        for(int i=1;i<counts.size();i++){
            String[] row=counts.get(i);require(row.length==header.length,"Ragged count row "+i);String id=label(row[0]);require(geneSet.add(id),"Duplicate gene ID: "+id);genes.add(id);
            for(int j=1;j<row.length;j++){
                require(row[j].matches("0|[1-9][0-9]*"),"Count must be an unquoted nonnegative raw integer at row "+i+", column "+j);
                long count;try{count=Long.parseLong(row[j]);}catch(NumberFormatException e){throw new IOException("Count exceeds exact integer domain",e);}
                require(count<=9007199254740991L,"Count exceeds binary64 exact integer range");totals[j-1]+=count;
            }
        }
        require(!genes.isEmpty(),"No genes");for(double total:totals)require(total>0,"Zero-total cells are refused; no implicit deletion or imputation");
        var cm=tsv(inputs.resolve("cell-metadata.tsv"),r);var ch=cm.get(0);require(ch[0].equals("cell_id")&&ch.length==r.types().size()+1,"Cell metadata header requires cell_id and every declared typed covariate");
        var names=unique(Arrays.asList(ch).subList(1,ch.length));require(new HashSet<>(names).equals(r.types().keySet()),"Metadata header and metadata_types differ");
        require(cm.size()==cells.size()+1,"Cell metadata population differs");
        for(int i=1;i<cm.size();i++){
            String[] row=cm.get(i);require(row.length==ch.length&&row[0].equals(cells.get(i-1)),"Cell metadata must match literal count cell order; no reordering");
            for(int j=1;j<ch.length;j++){
                var spec=object(r.types().get(ch[j]));if(text(spec.get("type")).equals("factor"))require(strings(spec.get("levels"),1024).contains(label(row[j])),"Undeclared literal factor level: "+row[j]);
                else {require(row[j].matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Numeric metadata must be finite and explicit");double d=Double.parseDouble(row[j]);require(Double.isFinite(d),"Nonfinite numeric metadata");}
            }
        }
        var gm=tsv(inputs.resolve("gene-metadata.tsv"),r);require(Arrays.equals(gm.get(0),new String[]{"gene_id","gene_short_name"})&&gm.size()==genes.size()+1,"Gene metadata header/population differs");
        for(int i=1;i<gm.size();i++){String[] row=gm.get(i);require(row.length==2&&row[0].equals(genes.get(i-1)),"Gene metadata must match literal count gene order");label(row[1]);}
        return new Population(List.copyOf(genes),cells);
    }
    private static List<String[]> tsv(Path path,Request r)throws IOException {
        String text=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)r.inputBytes())).replace("\r\n","\n");require(!text.contains("\r"),"TSV requires LF or CRLF");
        var out=new ArrayList<String[]>();var lines=text.lines().iterator();while(lines.hasNext()){
            String line=lines.next();require(!line.isEmpty(),"Blank TSV row");String[] row=line.split("\t",-1);for(String field:row)label(field);out.add(row);
            require(out.size()<=r.maximumEntries()+1,"TSV record capacity exceeded");
        }require(!out.isEmpty(),"Empty TSV");return out;
    }
    private static List<String> unique(List<String> values)throws IOException {var out=new ArrayList<String>();var seen=new HashSet<String>();for(String x:values){label(x);require(seen.add(x),"Duplicate literal identifier/header: "+x);out.add(x);}return List.copyOf(out);}
    private static Map<String,Object> audit(Path dir,Map<String,Object> a,Population p,Request r,List<String> warnings)throws Exception {
        var source=read(dir.resolve("source-status.json"),r.jsonBytes());require(text(source.get("status")).equals("original-returned"),"Original case did not return");
        var pop=object(source.get("population"));require(integer(pop.get("genes"),1,10000000)==p.genes().size()&&integer(pop.get("cells"),1,10000000)==p.cells().size()&&Boolean.TRUE.equals(pop.get("full_save_reload_identical")),"Original full-save/reload axes or counts differ");
        var report=new LinkedHashMap<String,Object>();report.put("id",a.get("id"));report.put("full_save_reload_identical",true);
        if(a.containsKey("trajectory")){
            var cells=table(dir.resolve("cells.json"),r);require(tokens(cells,"cell_id").equals(p.cells()),"Original cell table order differs");
            var g=read(dir.resolve("prepared-graph.json"),r.jsonBytes());require(strings(g.get("cell_ids"),1000000).equals(p.cells()),"Prepared graph cell order differs");
            var edges=new ArrayList<Monocle3Pseudotime.Edge>();for(Object raw:list(g.get("edges"))){var e=object(raw);edges.add(new Monocle3Pseudotime.Edge((int)integer(e.get("from"),0,999999),(int)integer(e.get("to"),0,999999),finite(e.get("weight"),0,Double.MAX_VALUE)));}
            var nativeResult=Monocle3Pseudotime.order(p.cells(),matrix(g.get("cell_coordinates")),strings(g.get("principal_ids"),9999),matrix(g.get("principal_coordinates")),strings(g.get("roots"),9999),strings(g.get("graph_vertex_ids"),1000000),edges,r.nativeOperations());
            double cellError=compare(strings(g.get("expected_cell_pseudotime"),1000000),nativeResult.pseudotime());
            double graphError=compare(strings(g.get("expected_graph_pseudotime"),1000000),nativeResult.graphPseudotime());compare(tokens(cells,"pseudotime"),nativeResult.pseudotime());
            var roots=list(g.get("expected_root_indices"));require(roots.size()==nativeResult.selectedRoots().size(),"Original/native root count differs");for(int i=0;i<roots.size();i++)require(integer(roots.get(i),0,999999)==nativeResult.selectedRoots().get(i).zeroBasedCellIndex(),"Original/native nearest-cell root differs");
            warnings.addAll(nativeResult.warnings());report.put("native_pseudotime_audit",Map.of("cell_max_absolute_error",cellError,"graph_max_absolute_error",graphError,"operations",nativeResult.operations(),"infinite_cells",nativeResult.infiniteCellCount(),"graph_refitted",false));
        }
        if(a.containsKey("fit_models")){
            var models=table(dir.resolve("models.json"),r);require(tokens(models,"gene_id").equals(p.genes()),"Original gene model order differs");require(tokens(models,"status").stream().allMatch("OK"::equals),"Original non-OK gene model status");
            var coefficients=table(dir.resolve("coefficients.json"),r);require(new HashSet<>(tokens(coefficients,"gene_id")).equals(new HashSet<>(p.genes())),"Coefficient population differs");report.put("coefficient_rows",tokens(coefficients,"gene_id").size());
        }
        if(a.containsKey("graph_test")){
            var tests=table(dir.resolve("graph-test.json"),r);require(tokens(tests,"source_row_name").equals(p.genes()),"Original graph-test gene order differs");require(tokens(tests,"status").stream().allMatch("OK"::equals),"Original non-OK graph-test status");report.put("graph_test_rows",p.genes().size());
        }
        NextflowFiles.writeJson(dir.resolve("java-audit.json"),report);return report;
    }
    private static Map<String,Object> table(Path path,Request r)throws IOException {
        var m=read(path,r.jsonBytes());int rows=(int)integer(m.get("rows"),0,10000000);var names=new HashSet<String>();
        for(Object raw:list(m.get("columns"))){var c=object(raw);require(names.add(label(c.get("name"))),"Duplicate original output column");var values=literalTokens(c.get("tokens"));var kinds=strings(c.get("kinds"),10000000);require(values.size()==rows&&kinds.size()==rows,"Ragged original output table");for(String k:kinds)require(Set.of("value","NA","NaN","positive-infinity","negative-infinity").contains(k),"Unknown original value kind");}
        return m;
    }
    private static List<String> tokens(Map<String,Object> table,String name)throws IOException {for(Object raw:list(table.get("columns"))){var c=object(raw);if(text(c.get("name")).equals(name))return literalTokens(c.get("tokens"));}throw new IOException("Missing original output column: "+name);}
    private static List<String> literalTokens(Object raw)throws IOException {var out=new ArrayList<String>();for(Object x:list(raw)){require(out.size()<10000000,"Excessive original output population");out.add(text(x));}return out;}
    private static double[][] matrix(Object raw)throws IOException {var rows=list(raw);double[][] out=new double[rows.size()][];for(int i=0;i<rows.size();i++){var row=list(rows.get(i));out[i]=new double[row.size()];for(int j=0;j<row.size();j++)out[i][j]=finite(row.get(j),-Double.MAX_VALUE,Double.MAX_VALUE);}return out;}
    private static double compare(List<String> expected,double[] actual)throws IOException {require(expected.size()==actual.length,"Original/native pseudotime population differs");double max=0;for(int i=0;i<actual.length;i++){double e=expected.get(i).equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(expected.get(i));if(Double.isInfinite(e))require(e==actual[i],"Original/native unreachable pseudotime differs");else{double d=Math.abs(e-actual[i]);require(Double.isFinite(e)&&Double.isFinite(actual[i])&&d<=5e-12*Math.max(1,Math.abs(e)),"Original/native fitted graph distance differs at "+i);max=Math.max(max,d);}}return max;}
    private static void appendDiagnostics(Map<String,Object> report,List<String> warnings)throws IOException {
        for(Object w:list(report.get("warnings")))warnings.add("ORIGINAL R WARNING: "+text(w));
        if(report.containsKey("detail")&&!text(report.get("detail")).isEmpty())warnings.add("ORIGINAL R DETAIL: "+text(report.get("detail")));
        for(Object raw:list(report.get("cases"))){var c=object(raw);for(Object w:list(c.get("warnings")))warnings.add("ORIGINAL R WARNING ["+text(c.get("id"))+"]: "+text(w));if(c.get("original_error")!=null)warnings.add("ORIGINAL ERROR ["+text(c.get("id"))+"]: "+object(c.get("original_error")));}
    }
    private static Captured capture(Path original,Path copy,long maximum)throws IOException {
        require(maximum>=0,"Complete input exceeds maximum_input_bytes");var pin=NextflowFiles.pin(original,maximum,()->{});long count=0;
        try(var in=Files.newInputStream(original,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture cancelled");require(n<=maximum-count,"Input grew beyond capacity");out.write(buffer,0,n);count+=n;}
        }require(count==pin.bytes(),"Input changed during capture");var c=new Captured(original,copy,pin);c.verify();return c;
    }
    private static void verifyRequest(Path source,NextflowFiles.Pin pin)throws IOException {require(pin.equals(NextflowFiles.pin(source,1048576,()->{})),"Original request changed");}
    private static void copyTree(Path from,Path to)throws IOException {Files.createDirectory(to);try(var paths=Files.walk(from)){for(Path path:paths.toList()){if(path.equals(from))continue;Path target=to.resolve(from.relativize(path));require(!Files.isSymbolicLink(path),"Refuse result symlink");if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(target);else{require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Refuse nonregular result");Files.copy(path,target);}}}}
    private static void emit(PrintStream stream,String value){byte[] bytes=(value+"\n").getBytes(StandardCharsets.UTF_8);stream.write(bytes,0,bytes.length);stream.flush();}
    static Map<String,Object> read(Path path,int maximum)throws IOException {return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,maximum)),maximum,10000000));}
}
