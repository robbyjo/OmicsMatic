/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Explicit captured workflow, configuration, software, targets and output contracts. */
final class SnakemakeOriginalRecipe {
    record Workflow(String source,String snakefile,List<String> configfiles,List<String> targets) { Workflow { configfiles=List.copyOf(configfiles);targets=List.copyOf(targets); } }
    record Tool(String name,String executable) { }
    record Output(String root,String path,String kind,boolean allowEmpty,String comparison) { }
    record Execution(int cores,String scheduler,int retries,int latencyWait,boolean notemp,boolean extended,String deployment,boolean allowInstall,boolean requireJobs) { }
    record Managed(String providers,String environments,String images) { }
    record Request(String profile,Workflow workflow,Map<String,String> sources,Object parameters,Map<String,String> environment,List<Tool> tools,List<Output> outputs,Execution execution,SnakemakeOriginalCapture.Limits capture,SnakemakeOriginalProcess.Limits process,int metadataBytes,int eventRows,long logBytes,Managed managed,Map<?,?> declared) {
        Request { sources=Map.copyOf(sources);environment=Map.copyOf(environment);tools=List.copyOf(tools);outputs=List.copyOf(outputs); }
    }
    private SnakemakeOriginalRecipe() { }
    static Request read(Path file) throws IOException {
        Map<?,?> raw=object(SnakemakeOriginalParameters.parse(utf8(bytes(file,4_194_304)),4_194_304,100000));
        return parse(raw,false);
    }
    /** Validate Linux producer paths as text without accessing them on the reader's host. */
    static Request recorded(Map<?,?> raw)throws IOException {return parse(raw,true);}
    private static Request parse(Map<?,?> raw,boolean recorded)throws IOException {
        long schema=integer(raw.get("schema_version"),1,2);if(schema==1)keys(raw,"schema_version","engine_profile","workflow","resources","parameters","environment","required_tools","outputs","execution","limits");else keys(raw,"schema_version","engine_profile","workflow","resources","parameters","environment","required_tools","outputs","execution","limits","managed_software");
        String profile=text(raw.get("engine_profile"));if(!profile.equals(SnakemakeOriginalRuntime.PROFILE))throw new SnakemakeOriginalRuntime.Unavailable("Exact requested Snakemake profile is unavailable; do not infer author versions or substitute editions");
        Map<?,?> w=object(raw.get("workflow"));keys(w,"path","snakefile","configfiles","targets");String source=absolute(text(w.get("path")),recorded);String snakefile=member(text(w.get("snakefile")));List<String> configs=strings(w.get("configfiles")),targets=strings(w.get("targets"));
        for(String value:configs)member(value);for(String value:targets) { member(value);if(value.startsWith("-"))throw new IOException("Targets must be literal rule names/relative paths, not command-line flags"); }
        if(targets.isEmpty())throw new IOException("Declare explicit target rules/files; do not infer a first rule");
        var sources=new LinkedHashMap<String,String>();sources.put("workflow",source);
        for(var entry:object(raw.get("resources")).entrySet()) { String id=text(entry.getKey());if(Set.of("workflow","published").contains(id)||!id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Invalid/reserved captured resource ID");Map<?,?> value=object(entry.getValue());keys(value,"path");sources.put(id,absolute(text(value.get("path")),recorded)); }
        var environment=new LinkedHashMap<String,String>();
        for(var entry:object(raw.get("environment")).entrySet()) {
            String key=text(entry.getKey()),value=text(entry.getValue());
            if(!key.matches("[A-Za-z_][A-Za-z0-9_]*")||key.startsWith("PYTHON")||key.startsWith("LD_")||key.startsWith("SNAKEMAKE_")||key.startsWith("OMICS_SNAKEMAKE_")||Set.of("PATH","HOME","TMPDIR","XDG_CACHE_HOME","BASH_ENV","ENV","BASHOPTS","SHELLOPTS","CDPATH").contains(key)||value.indexOf('\0')>=0)throw new IOException("Environment key conflicts with protected interpreter/launch bindings: "+key);
            environment.put(key,value);
        }
        object(raw.get("parameters"));var tools=new ArrayList<Tool>();var toolNames=new HashSet<String>();
        for(Object item:list(raw.get("required_tools"))) { Map<?,?> tool=object(item);keys(tool,"name","path");String name=text(tool.get("name"));if(!name.matches("[A-Za-z0-9_.+-]+")||name.equals(".")||name.equals("..")||!toolNames.add(name))throw new IOException("Declare unique literal executable names");tools.add(new Tool(name,absolute(text(tool.get("path")),recorded))); }
        var outputs=new ArrayList<Output>();var outputNames=new HashSet<String>();
        for(Object item:list(raw.get("outputs"))) {
            Map<?,?> output=object(item);keys(output,"root","path","kind","allow_empty","comparison");String root=text(output.get("root")),path=text(output.get("path")),kind=text(output.get("kind")),comparison=text(output.get("comparison"));
            if(!sources.containsKey(root)&&!root.equals("published")||!path.isEmpty()&&!member(path).equals(path)||!Set.of("file","directory").contains(kind)||!Set.of("exact","benchmark-measurement").contains(comparison)||!outputNames.add(root+'\t'+path))throw new IOException("Invalid/repeated declared complete output");
            if(comparison.equals("benchmark-measurement")&&!kind.equals("file"))throw new IOException("A benchmark measurement declaration must be a complete selected-format file");
            outputs.add(new Output(root,path,kind,bool(output.get("allow_empty")),comparison));
        }
        if(outputs.isEmpty())throw new IOException("Declare the complete expected output family; a zero engine exit is insufficient");
        Map<?,?> x=object(raw.get("execution"));keys(x,"cores","scheduler","retries","latency_wait_seconds","notemp","benchmark_extended","deployment","allow_install","require_jobs");
        String scheduler=text(x.get("scheduler")),deployment=text(x.get("deployment"));
        if(!Set.of("greedy","ilp").contains(scheduler))throw new SnakemakeOriginalRuntime.Unavailable("Requested scheduler plugin is not yet qualified; no silent scheduler substitution");
        if(!Set.of("preinstalled","conda","apptainer","conda-apptainer").contains(deployment))throw new SnakemakeOriginalRuntime.Unavailable("Requested software deployment mode is unavailable; no PATH/deployer fallback");
        Execution execution=new Execution((int)integer(x.get("cores"),1,1024),scheduler,(int)integer(x.get("retries"),0,100),(int)integer(x.get("latency_wait_seconds"),0,3600),bool(x.get("notemp")),bool(x.get("benchmark_extended")),deployment,bool(x.get("allow_install")),bool(x.get("require_jobs")));
        Managed managed=null;
        if(schema==2){
            if(deployment.equals("preinstalled"))throw new IOException("Schema-2 managed software requires explicit Conda/Apptainer deployment");Map<?,?> m=object(raw.get("managed_software"));keys(m,"providers","conda_environments","container_images");String providers=absolute(text(m.get("providers")),recorded),environments=m.get("conda_environments")==null?null:absolute(text(m.get("conda_environments")),recorded),images=m.get("container_images")==null?null:absolute(text(m.get("container_images")),recorded);
            if(execution.allowInstall()&&(environments!=null||images!=null))throw new IOException("Permitted installation must use new owned cache prefixes (null declarations); borrowed prefixes are immutable");
            for(String key:environment.keySet())if(key.startsWith("CONDA")||key.startsWith("MAMBA")||key.startsWith("APPTAINER")||key.startsWith("SINGULARITY")||key.startsWith("XDG_"))throw new IOException("Managed provider/cache environment is owned by the adapter: "+key);managed=new Managed(providers,environments,images);
            for(Tool tool:tools)if(Set.of("conda","mamba","micromamba","apptainer","singularity").contains(tool.name()))throw new IOException("Declared tools cannot replace selected managed deployment providers: "+tool.name());
        }
        Map<?,?> l=object(raw.get("limits"));keys(l,"capture_files","capture_bytes","file_bytes","workspace_bytes","wall_seconds","address_space_bytes","cpu_seconds","output_file_bytes","open_files","processes","threads","sampled_rss_bytes","poll_millis","metadata_bytes","event_rows","log_bytes");
        long fileBytes=integer(l.get("file_bytes"),1,1_000_000_000_000L),total=integer(l.get("capture_bytes"),fileBytes,1_000_000_000_000L);int captureFiles=(int)integer(l.get("capture_files"),4,1_000_000);
        var capture=new SnakemakeOriginalCapture.Limits(captureFiles,total,fileBytes);
        var process=new SnakemakeOriginalProcess.Limits(integer(l.get("wall_seconds"),1,604800)*1000,integer(l.get("address_space_bytes"),16_777_216,1_099_511_627_776L),integer(l.get("cpu_seconds"),1,604800),integer(l.get("output_file_bytes"),1,1_000_000_000_000L),(int)integer(l.get("open_files"),8,1_048_576),integer(l.get("workspace_bytes"),1,1_000_000_000_000L),captureFiles,(int)integer(l.get("processes"),1,4096),(int)integer(l.get("threads"),1,1_048_576),integer(l.get("sampled_rss_bytes"),1,1_099_511_627_776L),(int)integer(l.get("poll_millis"),1,1000));
        return new Request(profile,new Workflow(source,snakefile,configs,targets),sources,raw.get("parameters"),environment,tools,outputs,execution,capture,process,(int)integer(l.get("metadata_bytes"),1024,67_108_864),(int)integer(l.get("event_rows"),1,10_000_000),integer(l.get("log_bytes"),1024,1_073_741_824),managed,raw);
    }
    private static boolean bool(Object value) throws IOException { if(!(value instanceof Boolean flag))throw new IOException("Require explicit boolean execution/output declarations");return flag; }
    private static String absolute(String value,boolean recorded) throws IOException {
        if(recorded){if(!value.startsWith("/")||value.length()<2)throw new IOException("Recorded producer paths must be canonical absolute Linux text");member(value.substring(1));}
        else {Path path=Path.of(value);if(!path.isAbsolute()||!path.normalize().equals(path))throw new IOException("Declare a canonical absolute original workflow/data/tool path");}
        return value;
    }
}
