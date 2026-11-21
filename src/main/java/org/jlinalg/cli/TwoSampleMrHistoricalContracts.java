/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Version-specific methods and original defaults, with explicit overrides only. */
final class TwoSampleMrHistoricalContracts {
    private TwoSampleMrHistoricalContracts() { }
    static final Map<String,String> VERSIONS=Map.of("TwoSampleMR0.4.9-db55b97a-R4.6.1-Windows64","0.4.9","TwoSampleMR0.5.6-8db843c0-R4.6.1-Windows64","0.5.6","TwoSampleMR0.5.7-592ebe05-R4.6.1-Windows64","0.5.7","TwoSampleMR0.6.8-806703f0-R4.6.1-Windows64","0.6.8");
    static final Set<String> PROFILES=VERSIONS.keySet();
    static final List<String> INPUTS=List.of("exposure","outcome","harmonised","data");
    static boolean mr(String op){return op.equals("MR")||op.equals("HARMONISE_AND_MR");}
    static Map<String,Object> reference(String profile)throws IOException {
        String version=VERSIONS.get(profile);if(version==null)throw new IOException("Unqualified historical source profile");
        return object(SimpleJson.parse(new String(TwoSampleMrHistoricalRuntime.resource(version+"/PROFILE.json"),StandardCharsets.UTF_8)),"original reference");
    }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","exposure","outcome","harmonised","data","harmonise_action","methods","parameter_policy","parameters","sensitivity","format_policy","format_options","rscript","r_libraries","seed","preparation_note","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_columns","maximum_entries","maximum_work"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        var r=new LinkedHashMap<String,Object>(raw);String profile=choice(raw,"profile","",PROFILES);r.put("profile",profile);var ref=reference(profile);
        String op=choice(raw,"operation","",Set.of("FORMAT","HARMONISE","MR","HARMONISE_AND_MR","MV_HARMONISE"));
        text(raw,"out");text(raw,"rscript");text(raw,"preparation_note");if(!raw.containsKey("seed"))throw new IOException("Declare seed explicitly, including original generated identifiers and bootstrap RNG");
        Set<String> required=op.equals("FORMAT")?Set.of("data"):op.equals("MR")?Set.of("harmonised"):Set.of("exposure","outcome");
        for(String key:INPUTS)if(required.contains(key))text(raw,key);else if(raw.containsKey(key))throw new IOException("Unused input for original operation: "+key);
        if(op.equals("MR")||op.equals("FORMAT")){if(raw.containsKey("harmonise_action"))throw new IOException("Unused harmonise_action");}
        else {if(!raw.containsKey("harmonise_action"))throw new IOException("Declare harmonise_action explicitly");r.put("harmonise_action",integer(raw,"harmonise_action",0,1,3));}
        if(op.equals("FORMAT")) {
            if(!text(raw,"format_policy").equals("source-defaults-with-overrides"))throw new IOException("Declare format_policy source-defaults-with-overrides");
            var options=object(raw.get("format_options"),"format_options");var names=list(object(ref.get("formals"),"source formals").get("format_data"),"original format_data arguments");
            for(var entry:options.entrySet()) {
                String key=entry.getKey();if(key.equals("dat")||!names.contains(key))throw new IOException("Option is absent from this original format_data signature: "+key);
                if(key.equals("type"))choice(options,key,"",Set.of("exposure","outcome"));
                else if(key.equals("header")||key.equals("log_pval"))bool(options,key,false);
                else if(key.equals("min_pval"))real(options,key,0,0,1,false);
                else if(key.equals("snps")){if(entry.getValue()!=null)strings(entry.getValue(),"explicit snps filter",0,2000000);}
                else identity(text(options,key));
            }
            if(!options.containsKey("type"))throw new IOException("Declare format_options.type exposure/outcome explicitly");r.put("format_options",options);
        }else for(String key:List.of("format_policy","format_options"))if(raw.containsKey(key))throw new IOException("Unused formatter option: "+key);
        if(mr(op)) {
            if(!text(raw,"parameter_policy").equals("source-defaults-with-overrides"))throw new IOException("Declare parameter_policy source-defaults-with-overrides");
            var allowed=new HashSet<String>();for(Object value:list(ref.get("methods"),"source method list"))allowed.add(text(object(value,"source method"),"obj"));
            var methods=strings(raw.get("methods"),"methods",1,allowed.size());if(!allowed.containsAll(methods))throw new IOException("Requested method is absent from this original source method list; no provider or edition substitution");r.put("methods",methods);
            var overrides=object(raw.getOrDefault("parameters",Map.of()),"parameters");var effective=object(ref.get("default_parameters"),"original parameter defaults");
            for(var entry:overrides.entrySet()) {
                String key=entry.getKey();Object original=effective.get(key);if(original==null)throw new IOException("Parameter is absent from original defaults: "+key);
                if(original instanceof Boolean)bool(overrides,key,false);
                else if(key.equals("nboot"))integer(overrides,key,0,2,1000000);
                else if(key.equals("test_dist"))choice(overrides,key,"",Set.of("z","t"));
                else if(key.equals("loss.function"))choice(overrides,key,"",Set.of("l2","huber","tukey"));
                else if(Set.of("alpha","Qthresh").contains(key))real(overrides,key,0,0,1,false);
                else if(Set.of("penk","phi").contains(key))real(overrides,key,0,0,Double.MAX_VALUE,false);
                else real(overrides,key,0,-Double.MAX_VALUE,Double.MAX_VALUE,true);
                effective.put(key,entry.getValue());
            }
            r.put("parameters",overrides);r.put("effective_parameters",effective);
            var sensitivity=strings(raw.getOrDefault("sensitivity",List.of()),"sensitivity",0,4);if(!Set.of("HETEROGENEITY","PLEIOTROPY","SINGLE_SNP","LEAVE_ONE_OUT").containsAll(sensitivity))throw new IOException("Unknown sensitivity operation");r.put("sensitivity",sensitivity);
        }else for(String key:List.of("methods","parameter_policy","parameters","sensitivity"))if(raw.containsKey(key))throw new IOException("Unused MR option: "+key);
        r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));r.put("seed",integer(raw,"seed",0,0,Integer.MAX_VALUE));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,20000000000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000L,1,40000000000L));
        r.put("maximum_rows",integer(raw,"maximum_rows",1000000,1,2000000));r.put("maximum_columns",integer(raw,"maximum_columns",128,1,1024));r.put("maximum_entries",integer(raw,"maximum_entries",20000000,1,200000000));r.put("maximum_work",integer(raw,"maximum_work",10000000000L,1,1000000000000000L));return r;
    }
}
