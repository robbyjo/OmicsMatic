/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Typed workflow/resource/parameter request; no opaque extra command-line flags. */
final class NextflowRecipe {
    private NextflowRecipe() { }
    record Workflow(Path source,String entrypoint,List<String> configs,List<String> profiles) {Workflow {configs=List.copyOf(configs);profiles=List.copyOf(profiles);}}
    record Tool(String name,Path executable) { }
    record Output(String path,String kind,boolean allowEmpty) { }
    record Request(String profile,Workflow workflow,Map<String,Path> sources,Object parameters,List<Tool> tools,
                   List<Output> outputs,boolean requireTasks,NextflowCapture.Limits capture,NextflowProcess.Limits process,
                   int traceRows,int metadataBytes,long logBytes,int heapMib,int cpus,Map<?,?> declared) {
        Request {sources=Map.copyOf(sources);tools=List.copyOf(tools);outputs=List.copyOf(outputs);}
    }
    static Request read(Path source)throws IOException {
        Map<?,?> raw=object(NextflowParameters.parse(utf8(bytes(source,4194304)),4194304,100000));
        keys(raw,"schema_version","engine_profile","workflow","resources","parameters","required_tools","outputs","require_tasks","limits");integer(raw.get("schema_version"),1,1);
        String profile=text(raw.get("engine_profile"));if(!NextflowRuntime.profiles().contains(profile))throw new NextflowRuntime.Unavailable("Exact requested Nextflow engine profile is unsupported; no edition inference");
        Map<?,?> w=object(raw.get("workflow"));keys(w,"path","entrypoint","configs","profiles");Path workflow=absolute(text(w.get("path")));String entry=member(text(w.get("entrypoint")));List<String> configs=strings(w.get("configs")),profiles=strings(w.get("profiles"));
        for(String config:configs)member(config);for(String value:profiles)if(!value.matches("[A-Za-z0-9_.-]+"))throw new IOException("Declare each complete named Nextflow profile separately");
        var sources=new LinkedHashMap<String,Path>();sources.put("workflow",workflow);Map<?,?> resources=object(raw.get("resources"));
        for(var row:resources.entrySet()){String id=text(row.getKey());if(id.equals("workflow")||!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid/reserved Nextflow resource ID: "+id);Map<?,?> resource=object(row.getValue());keys(resource,"path");sources.put(id,absolute(text(resource.get("path"))));}
        Object parameters=raw.get("parameters");object(parameters);
        var tools=new ArrayList<Tool>();var toolNames=new java.util.HashSet<String>();
        for(Object item:list(raw.get("required_tools"))){Map<?,?> tool=object(item);keys(tool,"name","path");String name=text(tool.get("name"));if(!name.matches("[A-Za-z0-9_.+-]+")||!toolNames.add(name))throw new IOException("Invalid/repeated declared executable name");tools.add(new Tool(name,absolute(text(tool.get("path")))));}
        var outputs=new ArrayList<Output>();var outputNames=new java.util.HashSet<String>();
        for(Object item:list(raw.get("outputs"))){Map<?,?> output=object(item);keys(output,"path","kind","allow_empty");String path=member(text(output.get("path"))),kind=text(output.get("kind"));if(!Set.of("file","directory").contains(kind)||!outputNames.add(path))throw new IOException("Invalid/repeated expected output declaration");outputs.add(new Output(path,kind,bool(output.get("allow_empty"))));}
        if(outputs.isEmpty())throw new IOException("Declare the complete expected output family; a zero engine exit alone admits no result");
        Map<?,?> limits=object(raw.get("limits"));Set<String> supported=Set.of("capture_files","capture_bytes","file_bytes","workspace_bytes","wall_seconds","address_space_bytes","cpu_seconds","output_file_bytes","open_files","processes","threads","sampled_rss_bytes","poll_millis","trace_rows","metadata_bytes","log_bytes","java_heap_mib","java_cpus");
        if(!supported.containsAll(limits.keySet()))throw new IOException("Unknown Nextflow capacity field");
        long file=value(limits,"file_bytes",17179869184L,1,1000000000000L),total=value(limits,"capture_bytes",34359738368L,file,1000000000000L);
        var capture=new NextflowCapture.Limits((int)value(limits,"capture_files",100000,1,1000000),total,file);
        var process=new NextflowProcess.Limits(value(limits,"wall_seconds",3600,1,604800)*1000,value(limits,"address_space_bytes",17179869184L,16777216,1099511627776L),value(limits,"cpu_seconds",3600,1,604800),value(limits,"output_file_bytes",17179869184L,1,1000000000000L),(int)value(limits,"open_files",4096,8,1048576),value(limits,"workspace_bytes",274877906944L,1,1000000000000L),(int)value(limits,"capture_files",100000,4,1000000),(int)value(limits,"processes",128,1,4096),(int)value(limits,"threads",4096,1,1048576),value(limits,"sampled_rss_bytes",17179869184L,1,1099511627776L),(int)value(limits,"poll_millis",100,1,1000));
        return new Request(profile,new Workflow(workflow,entry,configs,profiles),sources,parameters,tools,outputs,bool(raw.get("require_tasks")),capture,process,(int)value(limits,"trace_rows",1000000,1,10000000),(int)value(limits,"metadata_bytes",8388608,1024,67108864),value(limits,"log_bytes",67108864,1024,1073741824),(int)value(limits,"java_heap_mib",512,32,1048576),(int)value(limits,"java_cpus",2,1,1024),raw);
    }
    private static long value(Map<?,?> map,String key,long fallback,long minimum,long maximum)throws IOException{return integer(map.containsKey(key)?map.get(key):fallback,minimum,maximum);}
    private static boolean bool(Object value)throws IOException{if(!(value instanceof Boolean flag))throw new IOException("Require an explicit Nextflow boolean declaration");return flag;}
    private static Path absolute(String source)throws IOException{Path path=Path.of(source);if(!path.isAbsolute())throw new IOException("Declare absolute original workflow/data/tool paths");return path.normalize();}
}
