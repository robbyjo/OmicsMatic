/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalManagedPolicyTest {
    private static final String WORK="/captured/workflow",CONDA="/qualified/environments",IMAGES="/qualified/images";
    private static Map<String,Object> policy(String deployment)throws IOException {
        var x=new TreeMap<String,Object>();x.put("snakefile",WORK+"/Snakefile");x.put("directory",WORK);x.put("targets",List.of("all"));x.put("configfile",List.of("/captured/parameters.json"));x.put("executor","local");x.put("cores",2);x.put("scheduler","greedy");x.put("retries",0);x.put("latency_wait",1);x.put("forceall",true);x.put("keep_incomplete",true);x.put("notemp",false);x.put("benchmark_extended",false);x.put("logger",List.of("omicsmanaged"));x.put("software_deployment_method",SnakemakeOriginalManagedPolicy.methods(deployment));x.put("conda_prefix",CONDA);x.put("apptainer_prefix",IMAGES);x.put("apptainer_args","");x.put("conda_frontend","conda");return new TreeMap<>(Map.of("schema_version",1,"deployment",deployment,"allow_install",false,"expected",x));
    }
    private static Map<String,Object> receipt(Map<String,Object> policy)throws IOException {
        var effective=SnakemakeOriginalManagedPolicy.disabled();for(var entry:SnakemakeOriginalFiles.object(policy.get("expected")).entrySet())effective.put((String)entry.getKey(),entry.getValue());
        var r=new TreeMap<String,Object>();r.put("execution_policy",policy);r.put("effective_policy",effective);r.put("option_sources",Map.of("command_line",Map.of("",Map.of("destination","targets","value",List.of("all")))));r.put("policy_status","PASS_EFFECTIVE_ORIGINAL_MANAGED_PARSER_POLICY");r.put("upstream_api_called",true);r.put("exit_code",0);r.put("upstream_success",true);r.put("failure",null);return r;
    }
    private static SnakemakeOriginalManagedPolicy.Audit audit(Map<String,Object> policy,Map<String,Object> receipt)throws IOException{return SnakemakeOriginalManagedPolicy.audit(policy,receipt,false,WORK,CONDA,IMAGES);}
    @Test void allThreeOriginalEnumMethodFamiliesAreExplicitAndPortable()throws Exception {
        assertEquals(List.of("APPTAINER","CONDA"),SnakemakeOriginalManagedPolicy.methods("conda-apptainer"));
        for(String mode:List.of("conda","apptainer","conda-apptainer")){var p=policy(mode);var a=audit(p,receipt(p));assertTrue(a.accepted());assertTrue(a.upstreamApiCalled());assertEquals(19,SnakemakeOriginalManagedPolicy.CONTROLLED.size());assertThrows(UnsupportedOperationException.class,()->a.effective().put("until",List.of("partial")));}
    }
    @Test void changedActualDeploymentPartialDagAndLegacyFlagsCannotPass()throws Exception {
        var p=policy("conda");var r=receipt(p);var original=SnakemakeOriginalFiles.object(r.get("effective_policy"));
        for(var mutation:List.of(Map.entry("software_deployment_method",List.of("APPTAINER")),Map.entry("until",List.of("partial")),Map.entry("use_conda",true),Map.entry("use_envmodules",true),Map.entry("dryrun",true),Map.entry("conda_prefix","/other"),Map.entry("apptainer_args","--writable"),Map.entry("conda_frontend","mamba"),Map.entry("mode","SUBPROCESS"))){var actual=new TreeMap<String,Object>();for(var entry:original.entrySet())actual.put((String)entry.getKey(),entry.getValue());actual.put(mutation.getKey(),mutation.getValue());r.put("effective_policy",actual);assertThrows(IOException.class,()->audit(p,r),mutation.getKey());}
    }
    @Test void actualConflictingProfileMaySkipOnlyBeforeTheApi()throws Exception {
        var p=policy("conda");var r=receipt(p);var actual=SnakemakeOriginalManagedPolicy.disabled();for(var entry:SnakemakeOriginalFiles.object(p.get("expected")).entrySet())actual.put((String)entry.getKey(),entry.getValue());actual.put("until",List.of("partial"));r.put("effective_policy",actual);r.put("policy_status","SKIP3_EFFECTIVE_ORIGINAL_MANAGED_PARSER_POLICY");r.put("upstream_api_called",false);r.put("upstream_success",false);r.put("exit_code",3);r.put("failure","Conflicting actual until setting. Resolve the profile and retry NEW output.");assertFalse(audit(p,r).accepted());r.put("upstream_api_called",true);assertThrows(IOException.class,()->audit(p,r));
    }
    @Test void skipClaimWithoutActualContradictionCannotHideAFailure()throws Exception {
        var p=policy("conda");var r=receipt(p);r.put("policy_status","SKIP3_EFFECTIVE_ORIGINAL_MANAGED_PARSER_POLICY");r.put("upstream_api_called",false);r.put("upstream_success",false);r.put("exit_code",3);r.put("failure","Retain this skip and retry NEW output.");assertThrows(IOException.class,()->audit(p,r));
    }
    @Test void missingEffectiveSettingOrOptionProvenanceCannotBeAccepted()throws Exception {
        var p=policy("conda");var noProvenance=receipt(p);noProvenance.put("option_sources",Map.of());assertThrows(IOException.class,()->audit(p,noProvenance));var r=receipt(p);var actual=new TreeMap<String,Object>();for(var entry:SnakemakeOriginalFiles.object(r.get("effective_policy")).entrySet())actual.put((String)entry.getKey(),entry.getValue());actual.remove("use_envmodules");r.put("effective_policy",actual);assertThrows(IOException.class,()->audit(p,r));
    }
    @Test void permissionAndCompleteMethodContractCannotBeSilentlyChanged()throws Exception {
        var p=policy("conda");assertThrows(IOException.class,()->SnakemakeOriginalManagedPolicy.validate(p,true,WORK,CONDA,IMAGES));var changed=new TreeMap<String,Object>();for(var entry:SnakemakeOriginalFiles.object(p.get("expected")).entrySet())changed.put((String)entry.getKey(),entry.getValue());changed.put("software_deployment_method",List.of("CONDA","APPTAINER"));p.put("expected",changed);assertThrows(IOException.class,()->SnakemakeOriginalManagedPolicy.validate(p,false,WORK,CONDA,IMAGES));
    }
}
