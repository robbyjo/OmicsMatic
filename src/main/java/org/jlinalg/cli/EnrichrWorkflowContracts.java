/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.jlinalg.enrichr.EnrichrClient;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit current-service choices; no historical editions or identifier inference. */
final class EnrichrWorkflowContracts {
    private EnrichrWorkflowContracts() { }
    static final String PROFILE = "enrichr-current-human-mouse-workflow-v1";
    static final String SCOPE = "Current human/mouse Enrichr API and explicit library snapshots. Statistical computation is upstream. No historical server/client parity, implicit identifier mapping, local ORA substitution, original-paper reproduction or biological calibration. Offline verification/replay contacts no server.";
    static final String ACTION = "Read WARNINGS.md and analysis-status.json. SKIP3: restore the exact named permitted prerequisite/service/library or retain the skipped dependent analysis. FAIL2: no complete result; preserve raw inputs, partial replies and errors, correct the cause and retry NEW output. Never silently repair IDs, probabilities, source settings, libraries or algorithms. Hashes provide integrity, not authentication against coordinated fabrication.";
    static Map<String,Object> validate(Map<String,Object> raw) throws IOException {
        keys(raw, Set.of("schema_version","mode","out","operation","service_profile","libraries","query","background",
            "allow_upload","allow_unobserved_custom_normalization","description","policies","limits"));
        if (integer(raw,"schema_version",0,1,1)!=1 || !"RUN".equals(raw.get("mode"))) throw new IOException("Require schema_version 1 and mode RUN");
        if (!PROFILE.equals(raw.get("service_profile"))) throw new IOException("Declare service_profile " + PROFILE);
        String operation=choice(raw,"operation","",Set.of("enrichment","library-download"));
        var result=new LinkedHashMap<String,Object>();
        result.put("schema_version",1);result.put("mode","RUN");result.put("out",text(raw,"out"));
        result.put("service_profile",PROFILE);result.put("operation",operation);
        var libraries=list(raw.get("libraries"),"libraries");
        if(libraries.isEmpty()||libraries.size()>32)throw new IOException("Require 1..32 explicit library names");
        var names=new ArrayList<String>();var seen=new HashSet<String>();
        for(Object value:libraries){if(!(value instanceof String s))throw new IOException("Library name must be a string");literal(s,"library name");if(!seen.add(s))throw new IOException("Duplicate library name");names.add(s);}
        result.put("libraries",names);
        if(!(raw.get("allow_upload") instanceof Boolean upload))throw new IOException("Declare allow_upload explicitly");
        result.put("allow_upload",upload);
        if(operation.equals("enrichment")){
            if(!upload)throw new IOException("Enrichment submits gene symbols externally; explicitly allow_upload or select library-download");
            var query=object(raw.get("query"),"query");keys(query,Set.of("file","format"));
            if(!"lines".equals(query.get("format")))throw new IOException("Declare headerless UTF-8 query format lines; no guessing or stripping");
            result.put("query",Map.of("file",text(query,"file"),"format","lines"));
            var background=object(raw.get("background"),"background");keys(background,Set.of("kind","file","format"));
            String kind=choice(background,"kind","",Set.of("server-default","explicit"));
            if(kind.equals("explicit")){
                if(!"lines".equals(background.get("format")))throw new IOException("Declare explicit-background format lines");
                if(!Boolean.TRUE.equals(raw.get("allow_unobserved_custom_normalization")))throw new IOException("Custom-background API has no qualified full query echo; explicitly allow_unobserved_custom_normalization or use a different declared analysis");
                result.put("background",Map.of("kind",kind,"file",text(background,"file"),"format","lines"));
                result.put("allow_unobserved_custom_normalization",true);
            }else{
                if(background.size()!=1||raw.containsKey("allow_unobserved_custom_normalization"))throw new IOException("Server-default background has only kind; custom normalization acknowledgement is inapplicable");
                result.put("background",Map.of("kind",kind));
            }
            String description=raw.containsKey("description")?textAllowEmpty(raw.get("description")):"";
            if(description.length()>65536||description.codePoints().anyMatch(Character::isISOControl))throw new IOException("Description exceeds capacity or contains controls");
            unicode(description);result.put("description",description);
        }else{
            if(upload||raw.containsKey("query")||raw.containsKey("background")||raw.containsKey("description")||raw.containsKey("allow_unobserved_custom_normalization"))
                throw new IOException("Library-download performs no gene upload and accepts no query/background/description");
        }
        Map<String,Object> policies=raw.containsKey("policies")?object(raw.get("policies"),"policies"):Map.of();
        keys(policies,Set.of("identity_changes","empty_results","infinite_scores"));
        var p=new LinkedHashMap<String,Object>();
        for(String name:List.of("identity_changes","empty_results","infinite_scores"))p.put(name,choice(policies,name,name.equals("identity_changes")?"fail":"warn",Set.of("warn","fail")));
        result.put("policies",p);
        Map<String,Object> limits=raw.containsKey("limits")?object(raw.get("limits"),"limits"):Map.of();
        keys(limits,Set.of("timeout_seconds","maximum_response_bytes","maximum_request_bytes","maximum_input_bytes","maximum_output_bytes","maximum_genes","request_interval_millis"));
        var l=new LinkedHashMap<String,Object>();
        l.put("timeout_seconds",integer(limits,"timeout_seconds",45,1,300));
        l.put("maximum_response_bytes",integer(limits,"maximum_response_bytes",8388608,1,67108864));
        l.put("maximum_request_bytes",integer(limits,"maximum_request_bytes",8388608,1,67108864));
        l.put("maximum_input_bytes",integer(limits,"maximum_input_bytes",16777216,1,134217728));
        l.put("maximum_output_bytes",integer(limits,"maximum_output_bytes",536870912,1,2147483647));
        l.put("maximum_genes",integer(limits,"maximum_genes",200000,1,1000000));
        l.put("request_interval_millis",integer(limits,"request_interval_millis",1000,0,60000));
        result.put("limits",l);return result;
    }
    static Map<String,Object> portable(Map<String,Object> request)throws IOException{
        var r=new LinkedHashMap<>(request);r.put("out",".");
        if(request.containsKey("query"))r.put("query",Map.of("file","inputs/query.txt","format","lines"));
        if(request.containsKey("background")){
            var b=object(request.get("background"),"background");
            r.put("background",b.get("kind").equals("explicit")?Map.of("kind","explicit","file","inputs/background.txt","format","lines"):Map.of("kind","server-default"));
        }
        return r;
    }
    static EnrichrClient.Settings settings(Map<String,Object> request,boolean offline)throws IOException{
        var l=object(request.get("limits"),"limits");
        return new EnrichrClient.Settings(Duration.ofSeconds(number(l,"timeout_seconds")),Math.toIntExact(number(l,"maximum_response_bytes")),
            Math.toIntExact(number(l,"maximum_request_bytes")),Math.toIntExact(number(l,"maximum_genes")),Duration.ofMillis(offline?0:number(l,"request_interval_millis")));
    }
    static long number(Map<String,Object> object,String name){return ((Number)object.get(name)).longValue();}
    static void policy(Map<String,Object> request,String name,String message,List<String> warnings)throws IOException{
        if(object(request.get("policies"),"policies").get(name).equals("fail"))throw new IOException(message+"; policy "+name+"=fail");
        warnings.add(message+"; retained under explicit/default warn policy, without identifier/statistic repair.");
    }
    static void literal(String value,String label)throws IOException{
        if(value.isEmpty()||value.length()>4096||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require literal nonempty "+label+" without controls, at most 4096 units");
        unicode(value);
    }
    static void unicode(String value)throws IOException{
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){if(++i==value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IOException("Malformed Unicode");}
            else if(Character.isLowSurrogate(c))throw new IOException("Malformed Unicode");
        }
    }
    private static String textAllowEmpty(Object value)throws IOException{if(!(value instanceof String))throw new IOException("Description must be a string");return (String)value;}
}
