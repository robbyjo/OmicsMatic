/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Exact managed recipe/effective-original-parser contract, independent of the Python guard. */
final class SnakemakeOriginalManagedPolicy {
    static final Set<String> CONTROLLED=Set.of("snakefile","directory","targets","configfile","executor","cores","scheduler","retries","latency_wait","forceall","keep_incomplete","notemp","benchmark_extended","logger","software_deployment_method","conda_prefix","apptainer_prefix","apptainer_args","conda_frontend");
    record Audit(boolean accepted,boolean upstreamApiCalled,String status,Map<String,Object> effective,Map<?,?> optionSources,String failure) {
        Audit {effective=Collections.unmodifiableMap(new TreeMap<>(effective));}
    }
    private SnakemakeOriginalManagedPolicy() { }
    static Map<String,Object> request(String producer,String working,SnakemakeOriginalRecipe.Request recipe,String conda,String images,String apptainerArgs)throws IOException {
        var result=new TreeMap<String,Object>(SnakemakeOriginalPolicy.request(producer,working,recipe));var expected=new TreeMap<String,Object>();
        for(var entry:object(result.get("expected")).entrySet())expected.put(text(entry.getKey()),entry.getValue());
        expected.put("logger",List.of("omicsmanaged"));expected.put("software_deployment_method",methods(recipe.execution().deployment()));expected.put("conda_prefix",conda);expected.put("apptainer_prefix",images);expected.put("apptainer_args",apptainerArgs);expected.put("conda_frontend","conda");result.put("expected",expected);
        validate(result,recipe.execution().allowInstall(),working,conda,images);return result;
    }
    // The selected parser stores a set; the unchanged recorder sorts enum names.
    static List<String> methods(String deployment)throws IOException {return switch(deployment){case "conda"->List.of("CONDA");case "apptainer"->List.of("APPTAINER");case "conda-apptainer"->List.of("APPTAINER","CONDA");default->throw new IOException("Require an explicit qualified managed deployment mode");};}
    static Map<String,Object> disabled(){var result=SnakemakeOriginalPolicy.disabled();result.remove("software_deployment_method");return result;}
    static Map<String,Object> validate(Object value,boolean allow,String working,String conda,String images)throws IOException {
        Map<?,?> policy=object(value);keys(policy,"schema_version","deployment","allow_install","expected");integer(policy.get("schema_version"),1,1);if(!(policy.get("allow_install") instanceof Boolean permission)||permission!=allow)throw new IOException("Managed effective-parser and installation permissions differ");
        Map<?,?> expected=object(policy.get("expected"));if(!expected.keySet().equals(CONTROLLED))throw new IOException("Incomplete managed original-parser contract");var result=new TreeMap<String,Object>();for(var item:expected.entrySet())result.put(text(item.getKey()),item.getValue());
        equal(methods(text(policy.get("deployment"))),expected.get("software_deployment_method"),"managed deployment methods");
        equal(absolute(working),expected.get("directory"),"managed workflow cwd");String snake=absolute(text(expected.get("snakefile")));if(!snake.startsWith(working+"/"))throw new IOException("Managed snakefile escapes its captured cwd");
        equal(absolute(conda),expected.get("conda_prefix"),"managed Conda prefix");equal(absolute(images),expected.get("apptainer_prefix"),"managed image prefix");
        if(!"local".equals(expected.get("executor"))||!"conda".equals(expected.get("conda_frontend"))||!Boolean.TRUE.equals(expected.get("forceall"))||!Boolean.TRUE.equals(expected.get("keep_incomplete")))throw new IOException("Managed execution requires the qualified local executor, frontend and complete target DAG");
        equal(List.of("omicsmanaged"),expected.get("logger"),"managed parent logger");if(!(expected.get("apptainer_args") instanceof String))throw new IOException("Declare explicit Apptainer arguments");
        integer(expected.get("cores"),1,1024);integer(expected.get("retries"),0,100);integer(expected.get("latency_wait"),0,3600);if(!Set.of("greedy","ilp").contains(text(expected.get("scheduler"))))throw new IOException("Unqualified managed scheduler");
        for(String key:List.of("notemp","benchmark_extended"))if(!(expected.get(key) instanceof Boolean))throw new IOException("Explicit managed boolean required: "+key);
        var targets=strings(expected.get("targets"));if(targets.isEmpty())throw new IOException("Declare the complete managed workflow target family");for(String target:targets)if(target.startsWith("-")||target.indexOf('\0')>=0)throw new IOException("Managed targets cannot become CLI options");
        var configs=strings(expected.get("configfile"));if(configs.isEmpty())throw new IOException("Managed parameters/configuration must be captured explicitly");for(String path:configs)absolute(path);return result;
    }
    static Audit audit(Object requested,Map<?,?> controller,boolean allow,String working,String conda,String images)throws IOException {
        var expected=validate(requested,allow,working,conda,images);equal(requested,controller.get("execution_policy"),"source and Java managed execution policy");
        var actual=new TreeMap<String,Object>();for(var item:object(controller.get("effective_policy")).entrySet())actual.put(text(item.getKey()),item.getValue());var all=disabled();all.putAll(expected);if(!actual.keySet().equals(all.keySet()))throw new IOException("Incomplete actual managed parser setting census");
        Map<?,?> sources=object(controller.get("option_sources"));if(sources.isEmpty())throw new IOException("Missing original managed option provenance");for(var source:sources.entrySet()){text(source.getKey());for(var item:object(source.getValue()).entrySet()){text(item.getKey());Map<?,?> option=object(item.getValue());keys(option,"destination","value");if(option.get("destination")!=null)text(option.get("destination"));}}
        boolean accepted="PASS_EFFECTIVE_ORIGINAL_MANAGED_PARSER_POLICY".equals(controller.get("policy_status")),called=Boolean.TRUE.equals(controller.get("upstream_api_called"));String failure=controller.get("failure")==null?null:text(controller.get("failure"));
        if(accepted){if(!called)throw new IOException("Managed parser pass did not call the original API");equal(all,actual,"effective managed original parser and full-DAG policy");}
        else {if(!"SKIP3_EFFECTIVE_ORIGINAL_MANAGED_PARSER_POLICY".equals(controller.get("policy_status"))||called||!Boolean.FALSE.equals(controller.get("upstream_api_called"))||integer(controller.get("exit_code"),3,3)!=3||failure==null||!failure.contains("NEW output")||Boolean.TRUE.equals(controller.get("upstream_success")))throw new IOException("Malformed managed effective-parser skip");boolean contradiction=false;try{equal(all,actual,"managed rejected setting");}catch(IOException different){contradiction=true;}if(!contradiction)throw new IOException("Managed parser skip has no actual conflicting setting");}
        return new Audit(accepted,called,text(controller.get("policy_status")),actual,sources,failure);
    }
}
