/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit original Hail operations, expression trees and topologically declared Batch resources. No executable expression source. */
final class HailWorkflowRequest {
    private HailWorkflowRequest() { }
    record Capture(String id,Path source,boolean directory) { }
    record Request(String edition,Path runtime,Path out,List<Capture> captures,List<Path> executables,
                   List<Map<String,Object>> cases,Map<String,Object> limits,long inputBytes,long outputBytes,int maximumFiles,int timeout) {
        Map<String,Object> worker(String expectedVersion){return Map.of("schema_version",1,"expected_hail",expectedVersion,"limits",limits,"cases",cases);}
        boolean needsService()throws IOException{for(var c:cases)if(text(c.get("kind")).equals("batch-local")&&text(c.get("backend")).equals("SERVICE"))return true;return false;}
    }
    static final class Binder {
        final Path parent,out;final List<Capture> captures=new ArrayList<>();final Map<String,String> names=new LinkedHashMap<>();
        Binder(Path parent,Path out){this.parent=parent;this.out=out;}
        String capture(Object value,boolean directory)throws IOException {
            Path source=resolve(parent,label(value));String key=directory+":"+source;String name=names.get(key);
            if(name==null){require(captures.size()<1024,"At most1024 distinct complete inputs per batch");name="input"+String.format(Locale.ROOT,"%04d",captures.size()+1);names.put(key,name);captures.add(new Capture(name,source,directory));}
            require(source.getFileName()!=null,"An input must not be a filesystem root");return out.resolve("inputs").resolve(name).resolve(source.getFileName()).toString();
        }
    }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        NextflowFiles.keys(m,"schema_version","edition","runtime_manifest","out","limits","cases","maximum_input_bytes","maximum_output_bytes","maximum_files","timeout_seconds");integer(m.get("schema_version"),1,1);String edition=text(m.get("edition"));require(Set.of("0.2.139","0.2.130").contains(edition),"Declare exact Hail0.2.139 or0.2.130; never substitute a different release");Path out=resolve(parent,label(m.get("out")));var binder=new Binder(parent,out);var limits=object(m.get("limits"));NextflowFiles.keys(limits,"maximum_rows","maximum_columns","maximum_entries","maximum_dense_ld_cells");for(Object value:limits.values())integer(value,1,1000000000);var seen=new LinkedHashMap<String,String>();var folded=new HashSet<String>();var cases=new ArrayList<Map<String,Object>>();var executables=new LinkedHashSet<Path>();
        for(Object value:list(m.get("cases"))){
            require(cases.size()<128,"At most128 complete cases per atomic batch");var c=object(value);String id=id(c.get("id"));require(folded.add(id.toLowerCase(Locale.ROOT)),"Case IDs must be unique including case folding");String kind=text(c.get("kind"));
            switch(kind){
                case "matrix-pipeline" -> {
                    NextflowFiles.keys(c,"id","kind","input","steps");var input=object(c.get("input"));String format=text(input.get("kind"));
                    if(format.equals("vcf")){
                        NextflowFiles.keys(input,"kind","path","reference_genome","force_bgz","contig_recoding");require(Set.of("GRCh37","GRCh38").contains(text(input.get("reference_genome"))),"Declare original VCF reference genome");bool(input.get("force_bgz"));var recoding=object(input.get("contig_recoding"));require(recoding.size()<=100000,"Contig recoding capacity exceeded");for(var e:recoding.entrySet()){label(e.getKey());label(e.getValue());}input.put("path",binder.capture(input.get("path"),false));
                    }else if(format.equals("matrix-table")){
                        require(input.containsKey("path")^input.containsKey("from_case"),"MatrixTable input must declare exactly one path or earlier producer case");
                        if(input.containsKey("path")){NextflowFiles.keys(input,"kind","path");input.put("path",binder.capture(input.get("path"),true));}
                        else{NextflowFiles.keys(input,"kind","from_case");String producer=text(input.remove("from_case"));require("matrix-pipeline".equals(seen.get(producer)),"MatrixTable producer must be an earlier matrix case");input.put("path",out.resolve("worker").resolve(producer).resolve("result.mt").toString());}
                    }else throw new IOException("Input kind must be vcf or matrix-table; no implicit conversion");
                    c.put("input",input);var steps=new ArrayList<Map<String,Object>>();var stepIds=new HashSet<String>();
                    for(Object raw:list(c.get("steps"))){require(steps.size()<256,"At most256 declared operations per pipeline");var step=object(raw);require(stepIds.add(id(step.get("id")).toLowerCase(Locale.ROOT)),"Operation IDs must be unique including case folding");step(step);steps.add(step);}c.put("steps",steps);
                }
                case "liftover" -> {
                    NextflowFiles.keys(c,"id","kind","input","chain","source_reference","destination_reference","min_match","unmapped_policy","allele_policy");require(text(c.get("source_reference")).equals("GRCh37")&&text(c.get("destination_reference")).equals("GRCh38"),"This profile requires explicit GRCh37 to GRCh38 liftover");finite(c.get("min_match"),0,1);require(Set.of("KEEP_MISSING","FAIL_ATOMIC").contains(text(c.get("unmapped_policy"))),"Declare unmapped policy");require(text(c.get("allele_policy")).equals("KEEP_SOURCE_REPORT_STRAND"),"Keep original alleles and report target strand; no implicit reference-allele repair or reverse complementation");c.put("input",binder.capture(c.get("input"),false));c.put("chain",binder.capture(c.get("chain"),false));
                }
                case "batch-local" -> {
                    NextflowFiles.keys(c,"id","kind","backend","inputs","jobs");require(Set.of("LOCAL","SERVICE").contains(text(c.get("backend"))),"Declare LOCAL or SERVICE. SERVICE currently skips before execution: no configured/validated cloud profile");var inputs=new ArrayList<Map<String,Object>>();var inputIds=new HashSet<String>();
                    for(Object raw:list(c.get("inputs"))){require(inputs.size()<1024,"Batch input capacity exceeded");var input=object(raw);NextflowFiles.keys(input,"id","path");require(inputIds.add(id(input.get("id"))),"Duplicate Batch input ID");Path original=resolve(parent,label(input.get("path")));require(original.getFileName()!=null&&original.getFileName().toString().matches("[A-Za-z0-9_.-]+"),"Original Batch shell resource paths require portable ASCII input basenames; provide an explicitly named safe copy, without changing data or identifiers");input.put("path",binder.capture(input.get("path"),false));inputs.add(input);}c.put("inputs",inputs);var jobs=new ArrayList<Map<String,Object>>();var resources=new LinkedHashMap<String,Set<String>>();
                    for(Object raw:list(c.get("jobs"))){
                        require(jobs.size()<128,"At most128 explicit Batch jobs");var job=object(raw);NextflowFiles.keys(job,"id","argv","outputs","depends_on");String name=id(job.get("id"));require(!resources.containsKey(name),"Duplicate Batch job ID");var outputs=new LinkedHashSet<String>();for(Object output:list(job.get("outputs")))require(outputs.add(id(output)),"Duplicate job output");require(outputs.size()<=1024,"Batch output capacity exceeded");var dependencies=strings(job.get("depends_on"),128);require(new HashSet<>(dependencies).size()==dependencies.size(),"Duplicate explicit Batch dependency");for(String dependency:dependencies)require(resources.containsKey(dependency),"Jobs must be topologically declared: unresolved/cyclic dependency "+dependency);
                        var argv=list(job.get("argv"));require(!argv.isEmpty()&&argv.size()<=10000&&argv.get(0) instanceof String,"Each job needs a literal executable path and1..10000 arguments");Path executable=resolve(parent,label(argv.get(0)));executables.add(executable);var bound=new ArrayList<Object>();bound.add(executable.toString());
                        for(int i=1;i<argv.size();i++){
                            Object token=argv.get(i);
                            if(token instanceof String s){require(s.indexOf('\0')<0&&!s.contains("__RESOURCE_FILE__")&&!s.contains("__RESOURCE_GROUP__")&&!s.contains("__PYTHON_RESULT__"),"Literal argument contains NUL or a reserved original Batch resource token; use a typed binding");bound.add(s);}
                            else{
                                var binding=object(token);
                                if(binding.keySet().equals(Set.of("input")))require(inputIds.contains(text(binding.get("input"))),"Unknown Batch input binding");
                                else if(binding.keySet().equals(Set.of("output")))require(outputs.contains(text(binding.get("output"))),"Unknown current job output binding");
                                else if(binding.keySet().equals(Set.of("job","output"))){String producer=text(binding.get("job"));require(resources.containsKey(producer)&&resources.get(producer).contains(text(binding.get("output"))),"Resource dependency must refer to an earlier producer/output");}
                                else throw new IOException("Argument must be a literal or typed input/output/job binding");bound.add(binding);
                            }
                        }
                        job.put("argv",bound);jobs.add(job);resources.put(name,outputs);
                    }
                    require(!jobs.isEmpty(),"Require at least one Batch job");c.put("jobs",jobs);
                }
                default -> throw new IOException("Unsupported typed Hail case: "+kind);
            }
            cases.add(c);seen.put(id,kind);
        }
        require(!cases.isEmpty(),"Require1..128 complete cases");return new Request(edition,resolve(parent,label(m.get("runtime_manifest"))),out,List.copyOf(binder.captures),List.copyOf(executables),List.copyOf(cases),Collections.unmodifiableMap(limits),integer(m.get("maximum_input_bytes"),1,1099511627776L),integer(m.get("maximum_output_bytes"),1024,1099511627776L),(int)integer(m.get("maximum_files"),1,1000000),(int)integer(m.get("timeout_seconds"),1,86400));
    }
    static void step(Map<String,Object> step)throws IOException {
        String op=text(step.get("operation"));
        switch(op){
            case "sample_qc","variant_qc" -> {NextflowFiles.keys(step,"id","operation","name");id(step.get("name"));}
            case "filter_rows","filter_cols","filter_entries" -> {NextflowFiles.keys(step,"id","operation","predicate","keep");expression(step.get("predicate"),0,new int[]{0});bool(step.get("keep"));}
            case "annotate" -> {NextflowFiles.keys(step,"id","operation","axis","fields");require(Set.of("rows","cols","entries","globals").contains(text(step.get("axis"))),"Declare annotation axis");var fields=object(step.get("fields"));require(fields.size()<=1000,"Annotation capacity exceeded");for(var field:fields.entrySet()){id(field.getKey());expression(field.getValue(),0,new int[]{0});}}
            case "select_rows","select_cols","select_entries","select_globals","key_rows_by","key_cols_by" -> {NextflowFiles.keys(step,"id","operation","fields");var fields=strings(step.get("fields"),1000);require(new HashSet<>(fields).size()==fields.size(),"Duplicate selected/key fields");}
            case "ld_prune" -> {NextflowFiles.keys(step,"id","operation","r2","bp_window_size","memory_per_core","keep_higher_maf","block_size");finite(step.get("r2"),0,1);integer(step.get("bp_window_size"),1,1000000000);integer(step.get("memory_per_core"),1,16384);bool(step.get("keep_higher_maf"));integer(step.get("block_size"),1,4096);}
            case "ld_matrix" -> {NextflowFiles.keys(step,"id","operation","radius","block_size");integer(step.get("radius"),0,1000000000);integer(step.get("block_size"),1,4096);}
            case "hwe_normalized_pca" -> {NextflowFiles.keys(step,"id","operation","k","compute_loadings");integer(step.get("k"),1,100);bool(step.get("compute_loadings"));}
            default -> throw new IOException("Unsupported original Hail operation: "+op);
        }
    }
    static void expression(Object value,int depth,int[] nodes)throws IOException {
        require(depth<=40&&++nodes[0]<=10000,"Complete typed expression capacity exceeded");var node=object(value);
        if(node.keySet().equals(Set.of("literal"))){require(node.get("literal")!=null,"Missing literals require explicit dtype");literal(node.get("literal"),depth,nodes);}
        else if(node.keySet().equals(Set.of("missing")))require(Set.of("int32","int64","float32","float64","str","bool","call").contains(text(node.get("missing"))),"Unsupported typed missing literal");
        else if(node.keySet().equals(Set.of("field"))){var field=object(node.get("field"));NextflowFiles.keys(field,"axis","path");require(Set.of("entry","row","col","global").contains(text(field.get("axis"))),"Declare field axis");var path=strings(field.get("path"),40);require(!path.isEmpty(),"Require nonempty field path");}
        else if(node.keySet().equals(Set.of("index","at"))){integer(node.get("at"),0,1000000);expression(node.get("index"),depth+1,nodes);}
        else if(node.keySet().equals(Set.of("operator","left","right"))){require(Set.of("add","subtract","multiply","divide","lt","le","gt","ge","eq","ne","and","or").contains(text(node.get("operator"))),"Unsupported typed binary operator");expression(node.get("left"),depth+1,nodes);expression(node.get("right"),depth+1,nodes);}
        else if(node.keySet().equals(Set.of("operator","arg"))){require(text(node.get("operator")).equals("not"),"Unsupported unary operator");expression(node.get("arg"),depth+1,nodes);}
        else if(node.keySet().equals(Set.of("function","args"))){String fn=text(node.get("function"));require(Set.of("is_defined","is_missing","abs","sqrt","len","min","max","float64","int32","str").contains(fn),"Unsupported original function; executable Python expressions are not accepted");var args=list(node.get("args"));require(!args.isEmpty()&&args.size()<=64&&(Set.of("min","max").contains(fn)||args.size()==1),"Original function arity differs");for(Object arg:args)expression(arg,depth+1,nodes);}
        else if(node.keySet().equals(Set.of("call_method","arg"))){require(Set.of("is_het","is_hom_ref","is_hom_var","is_non_ref","n_alt_alleles").contains(text(node.get("call_method"))),"Unsupported original call method");expression(node.get("arg"),depth+1,nodes);}
        else throw new IOException("Unsupported expression node; no executable expression source: "+node.keySet());
    }
    private static void literal(Object value,int depth,int[] nodes)throws IOException {
        require(depth<=40&&++nodes[0]<=10000,"Literal expression capacity exceeded");
        if(value instanceof String s)require(s.indexOf('\0')<0&&s.length()<=1000000,"Literal string capacity/NUL");
        else if(value instanceof Boolean||value instanceof NextflowParameters.NumberToken||value instanceof Number){/* Original Hail infers the literal type and validates it. */}
        else if(value instanceof List<?> a)for(Object item:a)literal(item,depth+1,nodes);
        else if(value instanceof Map<?,?> m)for(var e:m.entrySet()){label(e.getKey());literal(e.getValue(),depth+1,nodes);}
        else throw new IOException("Unsupported/null untyped literal; declare missing dtype explicitly");
    }
    static String id(Object value)throws IOException{String id=text(value);require(id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"),"Require a portable literal identifier");return id;}
}
