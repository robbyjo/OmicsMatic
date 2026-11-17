/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.equal;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.join;

/** Independent recipe-to-effective-parser policy checks, including captured workflow profiles. */
final class SnakemakeOriginalPolicy {
    private static final Set<String> CONTROLLED=Set.of("snakefile","directory","targets","configfile","executor","cores","scheduler","retries","latency_wait","forceall","keep_incomplete","notemp","benchmark_extended","logger");
    private SnakemakeOriginalPolicy() { }
    static Map<String,Object> request(String producer,String working,SnakemakeOriginalRecipe.Request request)throws IOException {
        var x=request.execution();var expected=new TreeMap<String,Object>();expected.put("snakefile",join(working,request.workflow().snakefile()));expected.put("directory",working);expected.put("targets",request.workflow().targets());
        var configs=new ArrayList<String>();for(String config:request.workflow().configfiles())configs.add(join(working,config));configs.add(producer+"/parameters.json");expected.put("configfile",configs);
        expected.put("executor","local");expected.put("cores",x.cores());expected.put("scheduler",x.scheduler());expected.put("retries",x.retries());expected.put("latency_wait",x.latencyWait());expected.put("forceall",true);expected.put("keep_incomplete",true);expected.put("notemp",x.notemp());expected.put("benchmark_extended",x.extended());expected.put("logger",List.of("omicsmatic"));
        return Map.of("schema_version",1,"deployment",x.deployment(),"allow_install",x.allowInstall(),"expected",expected);
    }
    static Map<String,Object> disabled() {
        var result=new TreeMap<String,Object>();
        for(String key:List.of("dryrun","touch","list_target_rules","list_rules","print_compilation","filegraph","d3dag","unlock","conda_cleanup_envs","conda_create_envs_only","list_conda_envs","cleanup_shadow","container_cleanup_images","list_input_changes","list_params_changes","list_untracked","summary","detailed_summary","delete_all_output","delete_temp_output","use_conda","use_apptainer","use_envmodules","immediate_submit","all_temp"))result.put(key,false);
        for(String key:List.of("lint","dag","rulegraph","deploy_sources","containerize","report","reporter","generate_unit_tests","cleanup_metadata","list_changes","archive","batch","allowed_rules","cache","default_storage_provider","draft_notebook","edit_notebook","runtime_profile","precommand"))result.put(key,null);
        for(String key:List.of("until","omit_from","target_jobs","omit_flags","software_deployment_method"))result.put(key,List.of());result.put("mode","DEFAULT");result.put("default_storage_prefix","");return result;
    }
    static void audit(Map<?,?> driver)throws IOException {
        if(!"PASS_EFFECTIVE_ORIGINAL_PARSER_POLICY".equals(driver.get("policy_status"))||!Boolean.TRUE.equals(driver.get("upstream_api_called")))throw new IOException("Original API was not called under a successful effective-parser policy");
        Map<?,?> policy=object(driver.get("policy"));keys(policy,"schema_version","deployment","allow_install","expected");integer(policy.get("schema_version"),1,1);
        if(!"preinstalled".equals(policy.get("deployment"))||!(policy.get("allow_install") instanceof Boolean))throw new IOException("Unqualified effective deployment policy");
        Map<?,?> expected=object(policy.get("expected"));if(!expected.keySet().equals(CONTROLLED))throw new IOException("Incomplete effective parser/recipe contract");var settings=disabled();for(var entry:expected.entrySet())settings.put(text(entry.getKey()),entry.getValue());equal(settings,driver.get("effective_policy"),"effective original parser settings and deployment/DAG policy");
        Map<?,?> sources=object(driver.get("option_sources"));if(sources.isEmpty())throw new IOException("Missing original parser option provenance");
        for(var source:sources.entrySet()) {text(source.getKey());for(var row:object(source.getValue()).entrySet()){text(row.getKey());Map<?,?> option=object(row.getValue());keys(option,"destination","value");if(option.get("destination")!=null)text(option.get("destination"));}}
    }
}
