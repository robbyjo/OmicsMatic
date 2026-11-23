/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SquidpyWorkflowFiles.*;

/** Offline typed-object closure and explicit result admission; no upstream model refit. */
final class SquidpyWorkflowAudit {
    private SquidpyWorkflowAudit() { }
    static Map<String,Object> producer()throws IOException {
        var classes=new TreeMap<String,Object>();
        for(Class<?> type:List.of(SquidpyWorkflowCli.class,SquidpyWorkflowContracts.class,SquidpyWorkflowFiles.class,
                SquidpyWorkflowRuntime.class,SquidpyWorkflowProcess.class,SquidpyWorkflowFrames.class,
                SquidpyWorkflowAudit.class,OwnedProcesses.class,GprofilerWorkflowContracts.class,
                GprofilerJson.class,PopsWorkflowNpy.class,FusionWorkflowContracts.class,jdistlib.Normal.class))classPin(type,classes);
        return map("selected_class_sha256",classes,"java_version",System.getProperty("java.version"),"os_name",System.getProperty("os.name"),"authentication",false);
    }
    private static void classPin(Class<?> type,Map<String,Object> classes)throws IOException {
        String name=type.getName();if(classes.containsKey(name))return;try(var in=type.getResourceAsStream("/"+name.replace('.','/')+".class")){require(in!=null,"Missing producer class "+name);classes.put(name,sha(in.readAllBytes()));}
        for(Class<?> child:type.getDeclaredClasses())classPin(child,classes);
        for(Class<?> member:type.getNestMembers())if(member!=type)classPin(member,classes);
    }
    private static Set<String> files(Path root)throws IOException {
        var result=new TreeSet<String>();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Missing output directory");
        try(var paths=Files.walk(root)){
            for(Path p:paths.toList()){
                require(!Files.isSymbolicLink(p)&&p.toRealPath().equals(p.toAbsolutePath().normalize()),"Aliased output member");
                if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;
                require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Nonregular output member");result.add(root.relativize(p).toString().replace('\\','/'));
            }
        }return result;
    }
    private static void frame(Path path,Map<String,Object> request)throws IOException {try(var source=new SquidpyWorkflowFrames.Frame(path,request)){while(source.next()!=null){/* All typed values and row positions are checked. */}}}
    private static void expectedFrame(Set<String> expected,String name){expected.add(name+"/schema.json");expected.add(name+"/rows.jsonl");}
    static Map<String,Object> summary(Path root,Map<String,Object> request,boolean materialize)throws IOException {
        var report=read(root.resolve("worker/report.json"));
        require("ORIGINAL_RETURNED_PENDING_JAVA_AUDIT".equals(report.get("status"))&&Boolean.TRUE.equals(report.get("original_call_started"))&&Boolean.TRUE.equals(report.get("original_call_returned"))&&!Boolean.TRUE.equals(report.get("results_admitted")),"Original public call did not return a complete auditable object");
        var invocation=read(root.resolve("worker/invocation.json"));
        require(same(invocation.get("provided_options"),request.get("options")),"Executed original options differ from request");
        require(!text(invocation.get("signature"),"source signature").isBlank(),"Missing actual source signature");
        var output=object(report.get("original_output"));String operation=request.get("operation").toString();require(operation.equals(output.get("operation")),"Original returned operation differs");
        var result=map("operation",operation,"source_squidpy",request.get("expected_squidpy"),"recorded_warning_count",list(report.get("warnings")).size(),
                "finite_result_rows",0,"no_result_rows",0,"withheld_probability_cells",0,"identifier_changes",0);
        result.put("result_unit",operation.equals("LIGREC")?"interaction_cluster_cell":operation.equals("SPATIAL_AUTOCORR")?"feature_point_statistic":"observation");
        Path original=root.resolve("worker/out");var expected=new TreeSet<String>();
        try(var emissions=new Emissions(root,materialize)){
            switch(operation){
                case "SPATIAL_AUTOCORR"->autocorrelation(original,request,emissions,result,expected);
                case "LIGREC"->ligrec(original,request,emissions,result,expected);
                case "SPATIAL_NEIGHBORS","READ_NANOSTRING"->state(original.resolve("state"),request,emissions,result,expected);
                default->throw new IOException("Unknown recorded operation");
            }
            if(operation.equals("SPATIAL_NEIGHBORS")){
                long changes=list(output.get("observation_name_changes")).size();result.put("identifier_changes",changes);
                require(changes==0||Boolean.TRUE.equals(object(request.get("admission")).get("allow_identifier_changes")),"Original neighbor function changed observation identifiers; original output retained. Explicit allow_identifier_changes is required to admit this source change");
                emissions.json("identifier-changes.json",output.get("observation_name_changes"));
            }
            long absent=number(result,"no_result_rows"),finite=number(result,"finite_result_rows");
            require(finite>0,"Original operation contains no finite usable result unit; preserve raw source output and retry justified NEW inputs");
            require(absent+number(result,"withheld_probability_cells")==0||Boolean.TRUE.equals(object(request.get("admission")).get("allow_no_result_rows")),"Original undefined/untested rows or withheld probabilities require explicit allow_no_result_rows; raw values and source conditions are retained");
            result.put("admitted_files",emissions.identities());
        }
        require(files(original).equals(expected),"Original typed export membership differs from the complete declared operation object");
        result.put("original_export_files",new ArrayList<>(expected));
        return result;
    }
    private static void state(Path directory,Map<String,Object> request,Emissions emissions,Map<String,Object> summary,Set<String> expected)throws IOException {
        var state=read(directory.resolve("state.json"));keys(state,Set.of("schema_version","n_obs","n_vars","X","slots","raw","uns"));integer(state.get("schema_version"),1,1);
        long cells=integer(state.get("n_obs"),1,SquidpyWorkflowContracts.cap(request,"maximum_cells")),genes=integer(state.get("n_vars"),1,SquidpyWorkflowContracts.cap(request,"maximum_features"));
        var matrices=map();var local=new TreeSet<String>();local.add("state.json");local.add("original-state.h5ad");
        for(String axis:List.of("obs","var")){
            try(var source=new SquidpyWorkflowFrames.Frame(directory.resolve(axis),request)){
                require(source.rows==(axis.equals("obs")?cells:genes),"Source axis identity population differs");while(source.next()!=null){/* Preserve source identity order, duplicates and typed missing data. */}
            }expectedFrame(local,axis);
        }
        var expression=object(state.get("X"));var shape=list(expression.get("shape"));require(integer(shape.get(0),0,Long.MAX_VALUE)==cells&&integer(shape.get(1),0,Long.MAX_VALUE)==genes,"Source X dimensions differ from identities");
        matrices.put("X",SquidpyWorkflowFrames.matrix(directory,expression,request));local.add(text(expression.get("file"),"source matrix"));
        var slots=object(state.get("slots"));require(slots.keySet().equals(Set.of("obsm","obsp","layers")),"Source matrix slots incomplete");
        for(String slot:slots.keySet()){
            var names=new HashSet<Object>();int index=0;
            for(Object value:list(slots.get(slot))){var item=object(value);require(names.add(item.get("key")),"Duplicate source matrix slot key");
                var dimensions=list(item.get("shape"));require(integer(dimensions.get(0),0,Long.MAX_VALUE)==cells,"Source matrix observation dimension differs");
                if(slot.equals("obsp"))require(integer(dimensions.get(1),0,Long.MAX_VALUE)==cells,"Source graph is not square");
                if(slot.equals("layers"))require(integer(dimensions.get(1),0,Long.MAX_VALUE)==genes,"Source layer feature population differs");
                var facts=SquidpyWorkflowFrames.matrix(directory,item,request);matrices.put(slot+"/"+index++,map("key",item.get("key"),"facts",facts));local.add(text(item.get("file"),"matrix export"));
            }
        }
        if(state.containsKey("raw")){
            var raw=object(state.get("raw"));require(integer(list(raw.get("shape")).get(0),0,Long.MAX_VALUE)==cells,"Raw observation population differs");
            try(var source=new SquidpyWorkflowFrames.Frame(directory.resolve("raw-var"),request)){require(source.rows==integer(list(raw.get("shape")).get(1),0,Long.MAX_VALUE),"Raw feature IDs differ");while(source.next()!=null){/* Full raw feature metadata. */}}
            matrices.put("raw",SquidpyWorkflowFrames.matrix(directory,raw,request));local.add(text(raw.get("file"),"raw matrix"));expectedFrame(local,"raw-var");
        }
        var opaque=new ArrayList<String>();uns(state.get("uns"),directory,request,local,opaque,0);
        try(var stream=Files.newInputStream(directory.resolve("original-state.h5ad"))){require(Arrays.equals(stream.readNBytes(8),new byte[]{(byte)137,72,68,70,13,10,26,10}),"Missing original HDF5 transport object");}
        for(String name:local)expected.add("state/"+name);
        summary.put("cells",cells);summary.put("features",genes);summary.put("matrix_facts",matrices);summary.put("opaque_context_arrays",opaque);
        if(request.get("operation").equals("SPATIAL_NEIGHBORS")){
            String key=object(request.get("options")).getOrDefault("key_added","spatial").toString();var graphNames=new HashSet<String>();
            for(Object value:list(slots.get("obsp"))){var graph=object(value);if(Set.of(key+"_connectivities",key+"_distances").contains(graph.get("key"))){graphNames.add(graph.get("key").toString());require(number(SquidpyWorkflowFrames.matrix(directory,graph,request),"nonfinite_entries")==0,"Original constructed graph contains nonfinite entries");}}
            require(graphNames.equals(Set.of(key+"_connectivities",key+"_distances")),"Original requested graph pair is missing");
        }
        summary.put("finite_result_rows",cells);emissions.json("data-object.json",map("source_state","worker/out/state/state.json","cells",cells,"features",genes,"matrix_facts",matrices,"opaque_context_arrays",opaque));
    }
    private static void uns(Object value,Path directory,Map<String,Object> request,Set<String> names,List<String> opaque,int depth)throws IOException {
        require(depth<=64,"Source metadata nesting exceeds capacity");var item=object(value);String kind=text(item.get("kind"),"source metadata kind");
        switch(kind){
            case "mapping"->{for(Object raw:list(item.get("entries"))){var entry=object(raw);keys(entry,Set.of("key","value"));SquidpyWorkflowFrames.scalar(entry.get("key"),0);uns(entry.get("value"),directory,request,names,opaque,depth+1);}}
            case "list","tuple","object_array"->{for(Object child:list(item.get(kind.equals("object_array")?"values":"values")))uns(child,directory,request,names,opaque,depth+1);}
            case "array"->{String name=relative(text(item.get("file"),"array file"));require(Path.of(name).getNameCount()==1&&names.add(name),"Repeated/unsafe source array file");
                String dtype=text(item.get("dtype"),"source array dtype");Path file=directory.resolve(name);
                if(dtype.matches("(float(32|64)|u?int(8|16|32|64)|bool)")){
                    var array=PopsWorkflowNpy.inspect(file,65536,SquidpyWorkflowContracts.cap(request,"maximum_member_bytes"));var shape=list(item.get("shape"));require(shape.size()==array.dimensions(),"Original array dimension metadata differs");
                    for(int i=0;i<shape.size();i++)require(integer(shape.get(i),0,Integer.MAX_VALUE)==array.dimension(i),"Original array shape metadata differs");
                }else{
                    try(var in=Files.newInputStream(file)){require(Arrays.equals(in.readNBytes(6),new byte[]{(byte)147,78,85,77,80,89}),"Context NPY signature differs");}opaque.add(name+" dtype="+dtype);
                }
            }
            default->SquidpyWorkflowFrames.scalar(value,0);
        }
    }
    private static void autocorrelation(Path original,Map<String,Object> request,Emissions emissions,Map<String,Object> summary,Set<String> expected)throws IOException {
        expectedFrame(expected,"statistics");expected.add("normality-context.json");expected.add("normality-z.jsonl");
        var context=read(original.resolve("normality-context.json"));long n=integer(context.get("n"),2,SquidpyWorkflowContracts.cap(request,"maximum_cells"));
        double s0=SquidpyWorkflowFrames.floating(context.get("s0")),s1=SquidpyWorkflowFrames.floating(context.get("s1")),s2=SquidpyWorkflowFrames.floating(context.get("s2"));
        double epsilon=SquidpyWorkflowFrames.floating(context.get("moment_precision_epsilon"));require(Set.of(Math.scalb(1.,-52),Math.scalb(1.,-23),Math.scalb(1.,-10)).contains(epsilon),"Unrecognized source moment precision");
        String mode=object(request.get("options")).getOrDefault("mode","moran").toString(),statistic=mode.equals("moran")?"I":"C";
        double s02=s0*s0,scale;
        if(mode.equals("geary")&&request.get("expected_squidpy").equals("1.8.4"))scale=(Math.abs((2*s1+s2)*(n-1.))+Math.abs(4*s02))/Math.abs(2*(n+1.)*s02);
        else scale=(Math.abs((double)n*n*s1)+Math.abs(n*s2)+Math.abs(3*s02))/Math.abs((n-1.)*(n+1.)*s02)+1./((n-1.)*(n-1.));
        double guard=64*epsilon*scale;
        var tainted=new HashSet<String>();long finite=0,absent=0,withheld=0;
        for(int pass=0;pass<2;pass++){
            try(var source=new SquidpyWorkflowFrames.Frame(original.resolve("statistics"),request);var zReader=Files.newBufferedReader(original.resolve("normality-z.jsonl"),StandardCharsets.UTF_8);var output=pass==1?emissions.rows("estimates.jsonl"):null){
                int score=source.column(statistic),variance=source.column("var_norm"),norm=source.column("pval_norm");require(score>=0&&variance>=0&&norm>=0,"Incomplete original autocorrelation columns");
                var labels=new ArrayList<String>();for(Object label:list(source.schema.get("column_labels"))){var l=object(label);require("string".equals(l.get("kind")),"Unrecognized scientific column label type");labels.add(text(l.get("value"),"statistic column"));}
                Map<String,Object> row;while((row=source.next())!=null){
                    String line=SquidpyWorkflowFrames.line(zReader,16777216);require(line!=null,"Missing normality diagnostic row");var z=object(GprofilerJson.read(line.getBytes(StandardCharsets.UTF_8)));
                    require(same(z.get("position"),row.get("position"))&&same(z.get("index"),row.get("index")),"Normality reconstruction identity/order differs");
                    var cells=list(row.get("cells"));double point=SquidpyWorkflowFrames.floating(cells.get(score)),v=SquidpyWorkflowFrames.floating(cells.get(variance));boolean pointOk=Double.isFinite(point);
                    boolean varianceOk=Double.isFinite(v)&&v>0&&Double.isFinite(guard)&&v>guard;
                    var probabilities=map();boolean rowComplete=pointOk;
                    for(int i=0;i<labels.size();i++)if(labels.get(i).startsWith("pval_")){
                        String label=labels.get(i),base=label.startsWith("pval_norm")?"pval_norm":label.startsWith("pval_z_sim")?"pval_z_sim":label.startsWith("pval_sim")?"pval_sim":null;
                        require(base!=null,"Unknown original probability column");double p=SquidpyWorkflowFrames.floating(cells.get(i));boolean valid=pointOk&&Double.isFinite(p)&&p>=0&&p<=1;
                        if(base.equals("pval_norm"))valid&=varianceOk;
                        if(base.equals("pval_z_sim")){int at=source.column("var_sim");valid&=at>=0&&Double.isFinite(SquidpyWorkflowFrames.floating(cells.get(at)))&&SquidpyWorkflowFrames.floating(cells.get(at))>0;}
                        boolean adjusted=!label.equals(base);
                        if(!adjusted&&!valid)tainted.add(base);
                        if(adjusted&&tainted.contains(base))valid=false;
                        if(!adjusted&&valid&&base.equals("pval_norm")){
                            double standardized=SquidpyWorkflowFrames.floating(z.get("standardized"));require(!Double.isNaN(standardized),"Finite normality result has undefined reconstruction");
                            double expectedP=standardized>0?1-jdistlib.Normal.cumulative(standardized,0,1,true,false):jdistlib.Normal.cumulative(standardized,0,1,true,false);
                            if(Boolean.TRUE.equals(object(request.get("options")).get("two_tailed")))expectedP*=2;
                            require(Math.abs(p-expectedP)<=3e-12,"Original normality probability differs from its source-precision standardized statistic");
                        }
                        if(pass==1){
                            probabilities.put(label,valid?map("status","ADMITTED","estimate",cells.get(i)):map("status",adjusted&&tainted.contains(base)?"UNDEFINED_ADJUSTMENT_FAMILY":"UNDEFINED_OR_UNRESOLVED","original",cells.get(i)));
                            if(!valid){withheld++;rowComplete=false;}
                        }
                    }
                    if(pass==1){
                        if(pointOk)finite++;else absent++;
                        output.write(map("source_position",row.get("position"),"feature",row.get("index"),"point",pointOk?map("status","ADMITTED","estimate",cells.get(score)):map("status","UNDEFINED","original",cells.get(score)),"variance",cells.get(variance),"probabilities",probabilities));
                    }
                }require(SquidpyWorkflowFrames.line(zReader,16777216)==null,"Excess normality diagnostic rows");
            }
        }
        summary.put("finite_result_rows",finite);summary.put("no_result_rows",absent);summary.put("withheld_probability_cells",withheld);summary.put("tainted_adjustment_families",new ArrayList<>(new TreeSet<>(tainted)));summary.put("moment_variance_guard",Double.isFinite(guard)?Double.toString(guard):"NaN/Inf");
    }
    private static void ligrec(Path original,Map<String,Object> request,Emissions emissions,Map<String,Object> summary,Set<String> expected)throws IOException {
        long finite=0,absent=0;
        for(String name:List.of("means","pvalues","metadata"))expectedFrame(expected,name);
        frame(original.resolve("metadata"),request);
        try(var means=new SquidpyWorkflowFrames.Frame(original.resolve("means"),request);var pvalues=new SquidpyWorkflowFrames.Frame(original.resolve("pvalues"),request);var output=emissions.rows("estimates.jsonl")){
            require(means.rows==pvalues.rows&&means.columns==pvalues.columns&&same(means.schema.get("column_labels"),pvalues.schema.get("column_labels")),"Ligand-receptor mean/probability axes differ");
            Map<String,Object> m,p;while((m=means.next())!=null){p=pvalues.next();require(p!=null&&same(m.get("index"),p.get("index")),"Ligand-receptor interaction order differs");
                var values=new ArrayList<Object>();var a=list(m.get("cells"));var b=list(p.get("cells"));
                for(int i=0;i<a.size();i++){
                    double mean=SquidpyWorkflowFrames.floating(a.get(i)),probability=SquidpyWorkflowFrames.floating(b.get(i));boolean valid=Double.isFinite(mean)&&Double.isFinite(probability)&&probability>=0&&probability<=1;
                    if(valid)finite++;else absent++;
                    values.add(map("column",i,"mean",a.get(i),"probability",valid?map("status","ADMITTED","estimate",b.get(i)):map("status","NOT_TESTED_OR_UNDEFINED","original",b.get(i))));
                }output.write(map("source_position",m.get("position"),"interaction",m.get("index"),"cells",values));
            }require(pvalues.next()==null,"Excess ligand-receptor probability rows");emissions.json("columns.json",means.schema.get("column_labels"));
        }
        summary.put("finite_result_rows",finite);summary.put("no_result_rows",absent);summary.put("withheld_probability_cells",absent);
    }
    private static final class Emissions implements AutoCloseable {
        final Path root;final boolean materialize;final Map<String,Object> identities=new TreeMap<>();final List<Rows> opened=new ArrayList<>();
        Emissions(Path root,boolean materialize){this.root=root;this.materialize=materialize;}
        void json(String name,Object value)throws IOException{try(var rows=rows(name)){rows.write(value);}}
        Rows rows(String name)throws IOException{relative(name);var rows=new Rows(name);opened.add(rows);return rows;}
        Map<String,Object> identities()throws IOException{for(var row:opened)row.close();return new LinkedHashMap<>(identities);}
        @Override public void close()throws IOException{for(var row:opened)row.close();}
        final class Rows implements AutoCloseable {
            final String name;final MessageDigest digest;final OutputStream stream;long size;boolean closed;
            Rows(String name)throws IOException{
                this.name=name;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
                Path file=root.resolve("admitted").resolve(name);if(materialize){Files.createDirectories(file.getParent());stream=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW);}else stream=OutputStream.nullOutputStream();
            }
            void write(Object value)throws IOException{byte[] raw=SquidpyWorkflowFiles.json(value);stream.write(raw);digest.update(raw);size+=raw.length;}
            @Override public void close()throws IOException{
                if(closed)return;closed=true;stream.close();var identity=map("bytes",size,"sha256",HexFormat.of().formatHex(digest.digest()));require(identities.put(name,identity)==null,"Repeated admitted export");
                if(!materialize)require(same(identity,pin(root.resolve("admitted").resolve(name))),"Admitted typed export differs from source-derived admission: "+name);
            }
        }
    }
    static Map<String,Object> verify(Path root)throws IOException {
        require(Files.isRegularFile(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS)&&!Files.exists(root.resolve("FAILURE.json")),"Missing completion or present failure journal");
        var manifest=read(root.resolve("manifest.json"));var request=read(root.resolve("canonical-request.json"));
        SquidpyWorkflowContracts.validateSaved(request);
        require("squidpy-original-workflow-v1".equals(manifest.get("workflow"))&&Boolean.TRUE.equals(manifest.get("results_admitted")),"Unadmitted/wrong saved workflow");
        var admission=read(root.resolve("analysis-status.json"));require("completed-with-warnings".equals(admission.get("status"))&&Boolean.TRUE.equals(admission.get("results_admitted"))&&integer(admission.get("exit_code"),0,0)==0,"Final analysis status does not admit a complete result");
        require(same(read(root.resolve("COMPLETED.json")).get("manifest_sha256"),pin(root.resolve("manifest.json")).get("sha256")),"Completion seal differs");
        require(same(manifest.get("inventory"),inventory(root,request)),"Saved capture file/directory closure differs");
        var recordedProducer=read(root.resolve("producer.json"));require(same(recordedProducer.get("selected_class_sha256"),producer().get("selected_class_sha256")),"Saved producer classes differ from this verifier; retain its original qualified producer/context");
        for(String name:SquidpyWorkflowRuntime.SOURCE_FILES)require(Arrays.equals(SquidpyWorkflowRuntime.resource(name),bytes(root.resolve("source/"+name),67108864)),"Saved Python/profile source differs from producer");
        require(same(read(root.resolve("worker/request.json")),SquidpyWorkflowRuntime.workerRequest(request)),"Worker scientific request differs");
        var binding=read(root.resolve("request-binding.json"));var raw=object(GprofilerJson.read(bytes(root.resolve("source-request.json"),67108864)));
        require(same(raw,binding.get("original"))&&same(request,binding.get("canonical")),"Original/canonical request binding differs");
        String base=text(binding.get("source_base"),"recorded request base");
        require(Objects.equals(manifest.get("mode"),raw.get("mode")),"Recorded mode differs from original request");
        if("RUN".equals(raw.get("mode"))){
            var abstracted=SquidpyWorkflowContracts.abstractAddresses(raw);
            require(same(SquidpyWorkflowContracts.validate(abstracted,Path.of(".").toAbsolutePath()),SquidpyWorkflowContracts.abstractAddresses(request)),"Raw/canonical scientific declarations differ");
            require(same(SquidpyWorkflowContracts.addressBindings(raw,base),SquidpyWorkflowContracts.addressBindings(request,base)),"Original/canonical literal addresses differ");
        }else{
            keys(raw,Set.of("schema_version","mode","analysis","out"));require("REPLAY".equals(raw.get("mode")),"Unknown saved request mode");integer(raw.get("schema_version"),1,1);
            var prior=object(binding.get("replay_source_canonical"));SquidpyWorkflowContracts.validateSaved(prior);var changed=new LinkedHashMap<>(prior);changed.put("out",request.get("out"));
            require(same(changed,request),"Replay changed scientific/runtime/input declarations");
            require(SquidpyWorkflowContracts.recordedPath(base,text(raw.get("out"),"out")).equals(SquidpyWorkflowContracts.recordedPath(base,text(request.get("out"),"canonical out"))),"Replay output binding differs");
            require(SquidpyWorkflowContracts.recordedPath(base,text(raw.get("analysis"),"analysis")).equals(SquidpyWorkflowContracts.recordedPath(base,text(binding.get("replay_analysis_address"),"parent address"))),"Replay parent binding differs");
            var parent=read(root.resolve("replay-parent-manifest.json"));require(same(pin(root.resolve("replay-parent-manifest.json")),manifest.get("replay_parent_manifest")),"Retained parent manifest differs");
            var parentFiles=object(object(parent.get("inventory")).get("files"));
            for(String name:List.of("original-inventory.json","input-inventory.json"))require(same(parentFiles.get(name),pin(root.resolve("replay-parent-"+name))),"Retained replay-parent metadata differs");
            require(same(read(root.resolve("replay-parent-input-inventory.json")),read(root.resolve("input-inventory.json"))),"Replay input bytes differ from parent");
        }
        for(var input:read(root.resolve("input-inventory.json")).entrySet()){relative(input.getKey());require(same(input.getValue(),pin(root.resolve(input.getKey()))),"Captured original input differs");}
        require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Recorded runtime closure changed during execution");
        runtimeClosure(root,request);
        require(Arrays.equals(bytes(root.resolve("worker/READY"),128),"SQUIDPY_PREFLIGHT_V1\n".getBytes(StandardCharsets.US_ASCII))&&Arrays.equals(bytes(root.resolve("worker/ACK"),128),"SQUIDPY_ACK_V1\n".getBytes(StandardCharsets.US_ASCII)),"Original pre-call handshake differs");
        require(integer(manifest.get("process_exit_code"),0,0)==0,"Original process exit was not successful");
        for(Object value:list(manifest.get("owned_processes")))require(Boolean.FALSE.equals(object(value).get("same_identity_alive")),"An observed owned process remains live");
        var summary=summary(root,request,false);require(same(summary,read(root.resolve("summary.json"))),"Source-derived scientific admission summary differs");
        var original=object(read(root.resolve("original-inventory.json")));var names=files(root.resolve("worker/out"));require(original.keySet().equals(names),"Original object export closure differs");
        for(String name:names)require(same(original.get(name),pin(root.resolve("worker/out").resolve(name))),"Preserved original API export differs: "+name);
        require(files(root.resolve("admitted")).equals(object(summary.get("admitted_files")).keySet()),"Admitted output population differs");
        return summary;
    }
    private static void runtimeClosure(Path root,Map<String,Object> request)throws IOException {
        var before=read(root.resolve("runtime-before.json"));keys(before,Set.of("files","addresses"));
        var identities=object(before.get("files"));var addresses=object(before.get("addresses"));require(!identities.isEmpty(),"Empty original runtime closure");
        var files=new TreeMap<String,Object>();var aliases=new TreeMap<String,String>();
        for(var entry:identities.entrySet()){
            var identity=object(entry.getValue());require(identity.keySet().equals(Set.of("bytes","sha256")),"Invalid runtime file identity");integer(identity.get("bytes"),0,Long.MAX_VALUE);require(text(identity.get("sha256"),"runtime SHA256").matches("[0-9a-f]{64}"),"Invalid runtime checksum");
            require(files.put(address(entry.getKey()),identity)==null,"Repeated normalized runtime file address");
        }
        for(var entry:addresses.entrySet())require(aliases.put(address(entry.getKey()),address(text(entry.getValue(),"runtime physical address")))==null,"Repeated runtime alias address");
        for(String name:List.of("runtime.json","runtime-after-info.json")){
            var info=read(root.resolve("worker/"+name));require(request.get("expected_python").equals(info.get("python"))&&request.get("expected_squidpy").equals(info.get("squidpy")),"Recorded runtime editions differ");
            require("0".equals(info.get("python_hash_seed")),"Recorded Python hash seed differs");
            var flags=object(info.get("python_flags"));require(integer(flags.get("no_user_site"),1,1)==1&&Boolean.TRUE.equals(flags.get("dont_write_bytecode")),"Recorded Python source-isolation flags differ");
            require(files.containsKey(address(text(info.get("python_executable"),"actual interpreter"))),"Recorded interpreter is outside byte closure");
            var packages=new TreeMap<String,Object>();for(Object value:list(info.get("packages"))){var p=object(value);String key=packageKey(text(p.get("name"),"dependency name"));Object old=packages.putIfAbsent(key,p.get("version"));require(old==null||old.equals(p.get("version")),"Conflicting installed dependency aliases");}
            for(var entry:object(request.get("expected_packages")).entrySet())require(entry.getValue().equals(packages.get(packageKey(entry.getKey()))),"Recorded dependency edition differs: "+entry.getKey());
            var sourceFiles=Set.of(SquidpyWorkflowContracts.recordedPath(text(request.get("out"),"source analysis address"),"source/worker.py"),SquidpyWorkflowContracts.recordedPath(text(request.get("out"),"source analysis address"),"source/operations.py"));
            for(Object value:list(info.get("loaded_module_files"))){String file=address(text(value,"loaded module"));require(sourceFiles.contains(file)||files.containsKey(file),"Recorded loaded module is outside original closure: "+file);}
            var edition=object(object(SquidpyWorkflowRuntime.profile().get("editions")).get(request.get("expected_squidpy")));var members=object(edition.get("members"));
            require(same(members,info.get("original_source_files")),"Recorded installed original wheel closure differs");
            String sourceRoot=text(info.get("squidpy_source_root"),"original source root");
            for(var entry:members.entrySet()){
                relative(entry.getKey());String file=SquidpyWorkflowContracts.recordedPath(sourceRoot,entry.getKey());String real=aliases.getOrDefault(file,file);
                require(same(entry.getValue(),files.get(real)),"Original wheel member lacks its recorded runtime bytes: "+entry.getKey());
            }
        }
    }
    static String packageKey(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[-_.]+","-");}
    private static String address(String value)throws IOException {
        String base=value.matches("[A-Za-z]:[/\\\\].*")?value.substring(0,3):value.startsWith("\\\\")?value:"/";
        require(value.startsWith("/")||value.matches("[A-Za-z]:[/\\\\].*")||value.startsWith("\\\\"),"Runtime address is not absolute");return SquidpyWorkflowContracts.recordedPath(base,value);
    }
}
