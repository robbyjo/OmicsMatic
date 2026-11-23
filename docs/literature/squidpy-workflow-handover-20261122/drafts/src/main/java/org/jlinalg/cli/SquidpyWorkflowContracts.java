/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Explicit local inputs, unchanged editions/options and bounded original Python calls. */
final class SquidpyWorkflowContracts {
    private SquidpyWorkflowContracts() { }
    static final String PROFILE="squidpy-original-workflow-v1";
    static final String SCOPE="Original Squidpy 1.1.2/1.2.2/1.8.4 prepared spatial-neighbor, autocorrelation, ligand-receptor and Nanostring workflows. No inferred paper settings, whole-package/paper reproduction, biological calibration or silent native fallback. Python API objects are retained as typed complete exports and original AnnData H5AD where applicable.";
    static final String ACTION="SKIP3: dependent analysis skipped; restore/install the exact named permitted private runtime/input or retain the skip. FAIL2: no complete result admitted; preserve original/raw/partial/failed/undefined evidence, correct the stated cause and retry NEW output. Read WARNINGS.md and worker/report.json; never repair identifiers, editions, source settings or NA/NaN/Inf silently. SHA-256 is integrity, not authentication against coordinated replacement.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static Map<String,Object> validate(Map<String,Object> raw,Path base)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","python","expected_python","expected_squidpy","expected_packages","input","options","interactions_file","nanostring_directory","limits","admission"));
        require(integer(raw.get("schema_version"),1,1)==1&&"RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile")),"Declare schema_version=1,mode=RUN and exact Squidpy workflow profile");
        var result=new LinkedHashMap<>(raw);
        for(String key:List.of("out","python"))result.put(key,path(base,text(raw.get(key),key)).toString());
        String version=text(raw.get("expected_squidpy"),"expected_squidpy");
        var editions=object(SquidpyWorkflowRuntime.profile().get("editions"));require(editions.containsKey(version),"Unsupported exact Squidpy edition");
        require(text(raw.get("expected_python"),"expected_python").matches("[0-9]+\\.[0-9]+\\.[0-9]+"),"Declare the exact Python patch edition");
        String operation=choice(raw,"operation",Set.of("SPATIAL_NEIGHBORS","SPATIAL_AUTOCORR","LIGREC","READ_NANOSTRING"));
        var functions=object(object(editions.get(version)).get("functions"));
        if(!functions.containsKey(operation))throw new Unavailable("Original "+operation+" is absent from Squidpy"+version+"; explicitly choose a supported permitted edition, otherwise keep the skipped operation");
        var options=object(raw.getOrDefault("options",map()));Set<String> parameters=new HashSet<>();
        for(Object value:list(object(functions.get(operation)).get("parameters")))parameters.add(text(value,"original parameter"));
        keys(options,parameters);jsonParameters(options,0,new int[]{0});result.put("options",options);
        var packages=object(raw.get("expected_packages"));require(packages.size()<=512,"Excessive dependency population");
        require(packages.keySet().containsAll(Set.of("anndata","numpy","scipy","pandas","scanpy","numba","scikit-learn","statsmodels","omnipath")),"Declare exact scientific dependency editions");
        for(var entry:packages.entrySet())require(entry.getKey().matches("[A-Za-z0-9_.-]{1,128}")&&text(entry.getValue(),"package edition").matches("[^\\p{Cntrl}]{1,256}"),"Invalid dependency name/version");
        if(packages.containsKey("squidpy"))require(version.equals(packages.get("squidpy")),"Contradictory expected Squidpy edition");
        if(operation.equals("READ_NANOSTRING")){
            require(!raw.containsKey("input")&&!raw.containsKey("interactions_file"),"Nanostring declares its original directory/files only");
            result.put("nanostring_directory",path(base,text(raw.get("nanostring_directory"),"nanostring_directory")).toString());
            for(String key:List.of("counts_file","meta_file"))SquidpyWorkflowFiles.relative(text(options.get(key),key));
            if(options.get("fov_file")!=null)SquidpyWorkflowFiles.relative(text(options.get("fov_file"),"fov_file"));
        }else{
            require(!raw.containsKey("nanostring_directory"),"A prepared operation cannot also declare Nanostring input");
            var input=object(raw.get("input"));String format=choice(input,"format",Set.of("prepared_json","h5ad","matrix_market"));
            var normalized=new LinkedHashMap<>(input);
            if(format.equals("matrix_market")){
                keys(input,Set.of("format","matrix","cells","features","orientation","dtype","obs_columns","var_columns"));
                choice(input,"orientation",Set.of("features_by_cells","cells_by_genes"));choice(input,"dtype",Set.of("float64","float32","int64","int32"));
                for(String key:List.of("matrix","cells","features"))normalized.put(key,path(base,text(input.get(key),key)).toString());
                for(String key:List.of("obs_columns","var_columns"))if(input.containsKey(key))jsonParameters(input.get(key),0,new int[]{0});
            }else{
                keys(input,Set.of("format","file"));normalized.put("file",path(base,text(input.get("file"),"input file")).toString());
            }
            result.put("input",normalized);
            if(operation.equals("LIGREC"))result.put("interactions_file",path(base,text(raw.get("interactions_file"),"captured interaction pairs")).toString());
            else require(!raw.containsKey("interactions_file"),"Interaction pairs belong to LIGREC only");
        }
        var limits=object(raw.getOrDefault("limits",map()));
        keys(limits,Set.of("maximum_cells","maximum_features","maximum_entries","maximum_dense_entries","maximum_working_bytes","maximum_work","threads","timeout_seconds","maximum_input_bytes","maximum_member_bytes","maximum_output_bytes","maximum_files","ack_timeout_seconds"));
        var capacities=map();
        capacities.put("maximum_cells",bound(limits,"maximum_cells",100000,1,1000000));
        capacities.put("maximum_features",bound(limits,"maximum_features",100000,1,1000000));
        capacities.put("maximum_entries",bound(limits,"maximum_entries",20000000,1,200000000));
        capacities.put("maximum_dense_entries",bound(limits,"maximum_dense_entries",20000000,1,200000000));
        capacities.put("maximum_working_bytes",bound(limits,"maximum_working_bytes",2147483648L,1,40000000000L));
        capacities.put("maximum_work",bound(limits,"maximum_work",20000000000L,1,1000000000000000L));
        capacities.put("threads",bound(limits,"threads",1,1,32));
        capacities.put("timeout_seconds",bound(limits,"timeout_seconds",3600,1,86400));
        capacities.put("ack_timeout_seconds",bound(limits,"ack_timeout_seconds",300,1,3600));
        capacities.put("maximum_input_bytes",bound(limits,"maximum_input_bytes",1073741824,1,40000000000L));
        capacities.put("maximum_member_bytes",bound(limits,"maximum_member_bytes",1073741824,1,40000000000L));
        capacities.put("maximum_output_bytes",bound(limits,"maximum_output_bytes",4294967296L,1,80000000000L));
        capacities.put("maximum_files",bound(limits,"maximum_files",10000,64,100000));result.put("limits",capacities);
        if(options.get("n_jobs")!=null)integer(options.get("n_jobs"),1,number(capacities,"threads"));
        if(options.containsKey("backend"))require(Set.of("loky","threading","multiprocessing","sequential").contains(text(options.get("backend"),"backend")),"Declare a built-in bounded backend");
        var admission=object(raw.getOrDefault("admission",map()));keys(admission,Set.of("allow_no_result_rows","allow_identifier_changes","allow_replay_differences"));
        var policy=map();for(String key:List.of("allow_no_result_rows","allow_identifier_changes","allow_replay_differences")){
            Object value=admission.getOrDefault(key,false);require(value instanceof Boolean,"Declare Boolean admission policy");policy.put(key,value);
        }result.put("admission",policy);
        return result;
    }
    static Map<String,Object> recordedRequest(Map<String,Object> raw,String base)throws IOException {
        var result=new LinkedHashMap<>(raw);
        for(String name:List.of("out","python","interactions_file","nanostring_directory"))if(raw.containsKey(name))result.put(name,recordedPath(base,text(raw.get(name),name)));
        if(raw.containsKey("input")){
            var input=new LinkedHashMap<>(object(raw.get("input")));
            for(String name:List.of("file","matrix","cells","features"))if(input.containsKey(name))input.put(name,recordedPath(base,text(input.get(name),name)));
            result.put("input",input);
        }return result;
    }
    static String recordedPath(String base,String value)throws IOException {
        return FusionWorkflowContracts.recordedPath(base,value).replace('\\','/');
    }
    static Map<String,Object> abstractAddresses(Map<String,Object> request)throws IOException {
        var result=new LinkedHashMap<>(request);Path anchor=Path.of(".").toAbsolutePath().getRoot().resolve("__squidpy_recorded_address__");
        for(String name:List.of("out","python","interactions_file","nanostring_directory"))if(request.containsKey(name)){
            text(request.get(name),"original address");result.put(name,anchor.resolve(name).toString());
        }
        if(request.containsKey("input")){
            var input=new LinkedHashMap<>(object(request.get("input")));
            for(String name:List.of("file","matrix","cells","features"))if(input.containsKey(name)){text(input.get(name),"original input address");input.put(name,anchor.resolve("input-"+name).toString());}
            result.put("input",input);
        }return result;
    }
    static Map<String,Object> addressBindings(Map<String,Object> request,String base)throws IOException {
        var values=map();for(String name:List.of("out","python","interactions_file","nanostring_directory"))if(request.containsKey(name))values.put(name,recordedPath(base,text(request.get(name),name)));
        if(request.containsKey("input"))for(var entry:object(request.get("input")).entrySet())if(Set.of("file","matrix","cells","features").contains(entry.getKey()))values.put("input/"+entry.getKey(),recordedPath(base,text(entry.getValue(),"input address")));
        return values;
    }
    static void validateSaved(Map<String,Object> request)throws IOException {
        var normalized=abstractAddresses(request);
        require(SquidpyWorkflowFiles.same(normalized,validate(normalized,Path.of(".").toAbsolutePath())),"Saved canonical fields/defaults/bounds differ from this source contract");
    }
    static Path path(Path base,String value)throws IOException {require(!value.isBlank()&&value.length()<=32768&&value.codePoints().noneMatch(c->c<32||c==127),"Require a literal bounded path without controls");Path p=Path.of(value);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    private static void jsonParameters(Object value,int depth,int[] count)throws IOException {
        require(depth<=16&&++count[0]<=200000,"Excessive parameter nesting/population");
        if(value==null||value instanceof Boolean)return;
        if(value instanceof org.jlinalg.gprofiler.GprofilerJson.Numeric||value instanceof Number){require(Double.isFinite(decimal(value).doubleValue()),"Scientific parameter overflows float64; no conversion to infinity");return;}
        if(value instanceof String s){require(s.length()<=32768&&!s.contains("\u0000"),"Invalid literal parameter text");return;}
        if(value instanceof List<?> list){for(Object v:list)jsonParameters(v,depth+1,count);return;}
        for(var entry:object(value).entrySet()){require(entry.getKey().length()<=4096,"Excessive parameter key");jsonParameters(entry.getValue(),depth+1,count);}
    }
}
