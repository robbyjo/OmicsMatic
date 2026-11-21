/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Exact original mtCOJO declarations; no inferred scales, populations or causal estimator. */
final class GctaMtcojoContracts {
    private GctaMtcojoContracts() { }
    static final Set<String> FIELDS=Set.of("traits","reference_ld","weight_ld","causal_effect_mode","causal_effects",
        "effect_scale","gwas_p","diff_frequency","minimum_instruments","clump_kb","clump_r2","heidi_p","ld_fdr");
    private static final Set<String> GSMR=Set.of("minimum_instruments","clump_kb","clump_r2","heidi_p","ld_fdr");
    static void validate(Map<String,Object> raw,Map<String,Object> r) throws IOException {
        if(!"as_supplied".equals(text(raw,"effect_scale")))throw new IOException("MTCOJO effect_scale must be as_supplied. This source profile does not claim a liability-scale conversion");
        if(((Number)r.get("autosome_number")).longValue()!=22)throw new IOException("GCTA1.94.1 MTCOJO reads LD-score chromosomes 1..22; require autosome_number=22");
        String mode=choice(raw,"causal_effect_mode","",Set.of("GSMR","supplied"));
        var traits=new ArrayList<Map<String,Object>>();var names=new LinkedHashSet<String>();
        for(Object item:list(raw.get("traits"),"traits")) {
            var t=object(item,"trait");keys(t,Set.of("name","summary","compression","sample_prevalence","population_prevalence"));
            String name=text(t,"name");GctaWorkflowFiles.literal(name);text(t,"summary");
            if(!names.add(name))throw new IOException("Duplicate literal mtCOJO trait name");
            t.put("compression",choice(t,"compression","none",Set.of("none","gzip")));
            boolean sample=t.containsKey("sample_prevalence"),population=t.containsKey("population_prevalence");
            if(sample!=population)throw new IOException("Declare both sample and population prevalences, or omit both; no prevalence imputation");
            if(sample)for(String key:List.of("sample_prevalence","population_prevalence")) {
                double p=real(t,key,-1,0,1,false);if(p>=1)throw new IOException("Require prevalence strictly between 0 and 1");t.put(key,p);
            }
            traits.add(t);
        }
        if(traits.size()<2||traits.size()>17)throw new IOException("MTCOJO requires one target followed by 1..16 ordered covariate traits");
        r.put("traits",traits);r.put("reference_ld",shards(raw,"reference_ld",true));r.put("weight_ld",shards(raw,"weight_ld",false));
        r.put("gwas_p",real(raw,"gwas_p",5e-8,0,1,true));r.put("diff_frequency",real(raw,"diff_frequency",.2,0,1,true));
        if(mode.equals("supplied")) {
            for(String key:GSMR)if(raw.containsKey(key))throw new IOException("Unused GSMR setting in supplied-causal-effect mode: "+key);
            var effects=object(raw.get("causal_effects"),"causal_effects");var expected=new HashSet<>(names);expected.remove(text(traits.get(0),"name"));
            if(!effects.keySet().equals(expected))throw new IOException("Supply exactly one causal effect for each literal covariate trait");
            var values=new LinkedHashMap<String,Object>();
            for(String name:expected) {
                double b=real(effects,name,Double.NaN,-100,100,true);
                if(Math.abs(b)>=100)throw new IOException("Original GCTA1.94.1 treats supplied abs(bxy)>=100 as unset and invokes GSMR. Refuse that estimator change; use a separately qualified method");values.put(name,b);
            }
            r.put("causal_effects",values);
        }else {
            if(raw.containsKey("causal_effects"))throw new IOException("GSMR mode has no supplied causal effects");
            r.put("minimum_instruments",integer(raw,"minimum_instruments",10,0,1000000));
            r.put("clump_kb",integer(raw,"clump_kb",10000,0,1000000));
            r.put("clump_r2",real(raw,"clump_r2",.05,0,1,true));
            r.put("heidi_p",real(raw,"heidi_p",.01,0,1,true));double fdr=real(raw,"ld_fdr",.05,0,1,true);
            if(fdr!=.05&&fdr!=0&&fdr!=1)throw new IOException("Original GCTA1.94.1 parses --gsmr-ld-fdr using integer conversion. This profile supports its 0.05 default, or explicit 0/1; other fractions cannot be honored without a separately qualified edition");r.put("ld_fdr",fdr);
        }
    }
    private static List<Map<String,Object>> shards(Map<String,Object> raw,String key,boolean reference) throws IOException {
        var values=new ArrayList<Map<String,Object>>();var chromosomes=new HashSet<Long>();
        for(Object item:list(raw.get(key),key)) {
            var value=object(item,"LD-score shard");keys(value,reference?Set.of("chromosome","scores","markers","compression"):Set.of("chromosome","scores","compression"));
            long chromosome=integer(value,"chromosome",0,1,22);if(!chromosomes.add(chromosome))throw new IOException("Duplicate LD-score chromosome");
            value.put("chromosome",chromosome);text(value,"scores");if(reference)text(value,"markers");
            value.put("compression",choice(value,"compression","none",Set.of("none","gzip")));values.add(value);
        }
        if(chromosomes.size()!=22)throw new IOException("Declare all 22 LD-score shards, including header-only empty chromosomes; no reference-panel substitution");return values;
    }
    static String traitPath(int index,Map<String,Object> trait) throws IOException {return "inputs/mt-trait"+index+".ma"+suffix(trait);}
    static String scorePath(String kind,Map<String,Object> shard) throws IOException {return "inputs/mt-"+kind+"/"+integer(shard,"chromosome",0,1,22)+".l2.ldscore"+suffix(shard);}
    static String markerPath(Map<String,Object> shard) throws IOException {return "inputs/mt-ref/"+integer(shard,"chromosome",0,1,22)+".l2.M_5_50";}
    static String suffix(Map<String,Object> item) throws IOException {return choice(item,"compression","none",Set.of("none","gzip")).equals("gzip")?".gz":"";}
    static void bindings(Map<String,Object> request,List<GctaWorkflowContracts.Binding> bindings) throws IOException {
        int i=0;for(Object item:list(request.get("traits"),"traits")){var t=object(item,"trait");bindings.add(new GctaWorkflowContracts.Binding(traitPath(i++,t),text(t,"summary")));}
        for(String key:List.of("reference_ld","weight_ld"))for(Object item:list(request.get(key),key)) {
            var s=object(item,"LD shard");bindings.add(new GctaWorkflowContracts.Binding(scorePath(key.equals("reference_ld")?"ref":"weight",s),text(s,"scores")));
            if(key.equals("reference_ld"))bindings.add(new GctaWorkflowContracts.Binding(markerPath(s),text(s,"markers")));
        }
    }
    static void paths(Map<String,Object> result,Path root) throws IOException {
        var traits=new ArrayList<Map<String,Object>>();int i=0;
        for(Object item:list(result.get("traits"),"traits")) {
            var t=object(item,"trait");t.put("summary",root==null?traitPath(i,t):resolve(root,text(t,"summary")).toString());traits.add(t);i++;
        }result.put("traits",traits);
        for(String key:List.of("reference_ld","weight_ld")) {
            var values=new ArrayList<Map<String,Object>>();
            for(Object item:list(result.get(key),key)) {
                var s=object(item,"LD shard");s.put("scores",root==null?scorePath(key.equals("reference_ld")?"ref":"weight",s):resolve(root,text(s,"scores")).toString());
                if(key.equals("reference_ld"))s.put("markers",root==null?markerPath(s):resolve(root,text(s,"markers")).toString());values.add(s);
            }result.put(key,values);
        }
    }
    static byte[] traitList(Map<String,Object> r) throws IOException {
        var text=new StringBuilder();int i=0;
        for(Object item:list(r.get("traits"),"traits")) {
            var t=object(item,"trait");text.append(text(t,"name")).append('\t').append("../").append(traitPath(i++,t));
            if(t.containsKey("sample_prevalence"))text.append('\t').append(t.get("sample_prevalence")).append('\t').append(t.get("population_prevalence"));text.append('\n');
        }return text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    static byte[] effectList(Map<String,Object> r) throws IOException {
        var text=new StringBuilder();var effects=object(r.get("causal_effects"),"causal effects");var traits=list(r.get("traits"),"traits");
        for(int i=1;i<traits.size();i++){String name=text(object(traits.get(i),"trait"),"name");text.append(name).append('\t').append(effects.get(name)).append('\n');}
        return text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
