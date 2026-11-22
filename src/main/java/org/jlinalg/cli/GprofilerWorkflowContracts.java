/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.jlinalg.gprofiler.*;

/** Explicit service, scientific parameters, policies and resource bounds. */
final class GprofilerWorkflowContracts {
    private GprofilerWorkflowContracts() { }
    static final String PROFILE="gprofiler-original-gost-protocol-v1";
    static final String SCOPE="Original g:GOSt API: statistics, identifier mapping and GO highlighting execute upstream. Exact full service builds are required for enrichment. No original R-client plotting, complete package/paper reproduction, independent statistical refit or biological calibration. VERIFY and REPLAY use saved protocol without network.";
    static final String PRIVACY="Recorded HTTP response headers contain Set-Cookie. Keep original wire traces private. For public sharing, make a separate declared copy with cookie values redacted; retain originals and leave scientific payloads unchanged.";
    static final String ACTION="Read WARNINGS.md and analysis-status.json. SKIP3: restore the exact named permitted service/build or retain the skipped dependent analysis. FAIL2: no complete admitted result; preserve inputs, raw/partial replies and errors, fix the named cause and retry NEW output. Never silently repair IDs, NA/NaN/Inf, builds or scientific options. SHA-256 provides integrity, not authentication against coordinated replacement.";
    static final List<String> PAYLOAD=List.of("organism","query","sources","user_threshold","all_results","ordered","combined","measure_underrepresentation","no_iea","domain_scope","numeric_ns","significance_threshold_method","background","output","no_evidences","highlight");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException{
        keys(raw,Set.of("schema_version","mode","out","operation","service","payload","allow_upload","policies","limits"));
        require(integer(raw.get("schema_version"),1,1)==1&&"RUN".equals(raw.get("mode")),"Require schema_version=1 and mode=RUN");
        String operation=choice(raw,"operation",Set.of("enrichment","metadata"));text(raw.get("out"),"out");
        var service=object(raw.get("service"));keys(service,Set.of("archive","required_build"));
        require(service.containsKey("archive")&&service.containsKey("required_build"),"Declare archive and required_build, including explicit null for discovery");
        service(raw);
        require(raw.get("allow_upload") instanceof Boolean,"Declare allow_upload explicitly");
        var payload=object(raw.get("payload"));
        literal(text(payload.get("organism"),"organism"),"organism");
        if(operation.equals("metadata")){
            require(payload.keySet().equals(Set.of("organism"))&&!Boolean.TRUE.equals(raw.get("allow_upload")),"Metadata requires only payload.organism and allow_upload=false");
        }else{
            require(Boolean.TRUE.equals(raw.get("allow_upload")),"Enrichment submits identifiers/query names externally; require explicit allow_upload=true");
            require(service.get("required_build") instanceof String,"Enrichment requires a complete expected build; discover metadata separately first");
            require(payload.keySet().equals(new HashSet<>(PAYLOAD)),"Declare exactly all 16 g:GOSt scientific fields");
            for(String flag:List.of("all_results","ordered","combined","measure_underrepresentation","no_iea","no_evidences","highlight"))require(payload.get(flag) instanceof Boolean,"Require Boolean "+flag);
            require("json".equals(payload.get("output")),"Require payload.output=json");
            require(Set.of("annotated","known","custom","custom_annotated").contains(text(payload.get("domain_scope"),"domain_scope")),"Unknown domain_scope");
            require(Set.of("g_SCS","bonferroni","fdr").contains(text(payload.get("significance_threshold_method"),"correction method")),"Unknown correction method");
            text(payload.get("numeric_ns"),"numeric_ns");probability(payload.get("user_threshold"));
            for(Object value:list(payload.get("sources")))literal(text(value,"source"),"source");
        }
        var result=new LinkedHashMap<>(raw);
        var p=raw.containsKey("policies")?object(raw.get("policies")):new LinkedHashMap<String,Object>();
        keys(p,Set.of("mapping_changes","server_normalization","empty_results"));var policies=map();
        for(String key:List.of("mapping_changes","server_normalization","empty_results")){
            Object value=p.getOrDefault(key,"warn");require(Set.of("warn","fail").contains(text(value,key)),"Unknown "+key+" policy");policies.put(key,value);
        }
        result.put("policies",policies);
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        keys(limits,Set.of("timeout_seconds","maximum_request_bytes","maximum_response_bytes","maximum_identifier_occurrences","request_interval_millis","maximum_output_bytes","maximum_result_rows"));
        var normalized=map();
        normalized.put("timeout_seconds",bound(limits,"timeout_seconds",45,1,300));
        normalized.put("maximum_request_bytes",bound(limits,"maximum_request_bytes",8388608,1,67108864));
        normalized.put("maximum_response_bytes",bound(limits,"maximum_response_bytes",16777216,1,67108864));
        normalized.put("maximum_identifier_occurrences",bound(limits,"maximum_identifier_occurrences",200000,1,1000000));
        normalized.put("request_interval_millis",bound(limits,"request_interval_millis",1000,0,60000));
        normalized.put("maximum_output_bytes",bound(limits,"maximum_output_bytes",268435456,1,2147483647));
        normalized.put("maximum_result_rows",bound(limits,"maximum_result_rows",200000,0,1000000));
        result.put("limits",normalized);
        // Validate full identifier populations before any metadata request.
        if(operation.equals("enrichment")){
            long count=0;Object query=payload.get("query");
            if(query instanceof Map<?,?>){var queries=object(query);require(!queries.isEmpty()&&queries.size()<=256,"Require 1..256 named queries");
                for(var entry:queries.entrySet()){literal(entry.getKey(),"query name");count+=identifiers(entry.getValue(),false);}}
            else count+=identifiers(query,false);
            Object background=payload.get("background");if(background!=null)count+=identifiers(background,true);
            require(count<=number(normalized,"maximum_identifier_occurrences"),"Combined identifier occurrence capacity exceeded");
            if(text(payload.get("domain_scope"),"domain_scope").startsWith("custom"))require(background instanceof List<?> b&&!b.isEmpty(),"Custom domain requires a nonempty explicit background array");
            else require(background==null||list(background).isEmpty(),"A supplied background requires an explicit custom domain; no background is silently ignored");
        }
        GprofilerJson.write(payload,Math.toIntExact(number(normalized,"maximum_request_bytes")));
        return result;
    }
    static GprofilerGostClient.Service service(Map<String,Object> request)throws IOException{
        var s=object(request.get("service"));Object build=s.get("required_build");
        require(build==null||build instanceof String,"required_build must be string or null");
        try{return new GprofilerGostClient.Service(text(s.get("archive"),"archive"),(String)build);}
        catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
    }
    static GprofilerGostClient.Settings settings(Map<String,Object> request,boolean offline)throws IOException{
        var l=object(request.get("limits"));return new GprofilerGostClient.Settings(Duration.ofSeconds(number(l,"timeout_seconds")),
            Math.toIntExact(number(l,"maximum_request_bytes")),Math.toIntExact(number(l,"maximum_response_bytes")),
            Math.toIntExact(number(l,"maximum_identifier_occurrences")),Duration.ofMillis(offline?0:number(l,"request_interval_millis")));
    }
    static Map<String,Object> portable(Map<String,Object> request){var copy=new LinkedHashMap<>(request);copy.put("out",".");return copy;}
    static void policy(Map<String,Object> request,String key,String message,List<String> warnings)throws IOException{
        if("fail".equals(object(request.get("policies")).get(key)))throw new IOException(message+"; policy "+key+"=fail");warnings.add(message);
    }
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static Path resolve(Path parent,String value){Path path=Path.of(value);return (path.isAbsolute()?path:parent.resolve(path)).toAbsolutePath().normalize();}
    static LinkedHashMap<String,Object> map(Object...pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    static Map<String,Object> object(Object value)throws IOException{
        if(!(value instanceof Map<?,?> raw))throw new IOException("Require JSON object");
        var m=new LinkedHashMap<String,Object>();for(var entry:raw.entrySet()){require(entry.getKey() instanceof String,"Require string keys");m.put((String)entry.getKey(),entry.getValue());}return m;
    }
    static List<?> list(Object value)throws IOException{if(!(value instanceof List<?> l))throw new IOException("Require JSON array");return l;}
    static String text(Object value,String label)throws IOException{if(!(value instanceof String s))throw new IOException("Require string "+label);return s;}
    static String choice(Map<String,Object> m,String key,Set<String> options)throws IOException{String s=text(m.get(key),key);require(options.contains(s),"Unknown "+key);return s;}
    static void keys(Map<String,Object> m,Set<String> allowed)throws IOException{require(allowed.containsAll(m.keySet()),"Unknown fields: "+m.keySet());}
    static void require(boolean condition,String detail)throws IOException{if(!condition)throw new IOException(detail);}
    static BigDecimal decimal(Object value)throws IOException{
        String s=value instanceof GprofilerJson.Numeric n?n.token():value instanceof Number n?n.toString():null;
        require(s!=null,"Require numeric value");try{return new BigDecimal(s);}catch(NumberFormatException e){throw new IOException("Invalid finite decimal",e);}
    }
    static BigDecimal probability(Object value)throws IOException{BigDecimal d=decimal(value);require(d.signum()>=0&&d.compareTo(BigDecimal.ONE)<=0,"Probability outside [0,1]");return d;}
    static long integer(Object value,long low,long high)throws IOException{try{long n=decimal(value).longValueExact();require(n>=low&&n<=high,"Integer outside declared bounds");return n;}catch(ArithmeticException e){throw new IOException("Require exactly representable integer",e);}}
    static long number(Map<String,Object> m,String key)throws IOException{return integer(m.get(key),0,Long.MAX_VALUE);}
    static long bound(Map<String,Object> m,String key,long fallback,long low,long high)throws IOException{return integer(m.getOrDefault(key,fallback),low,high);}
    private static long identifiers(Object value,boolean empty)throws IOException{var l=list(value);require(empty||!l.isEmpty(),"Require nonempty query arrays");for(Object id:l)literal(text(id,"identifier"),"identifier");return l.size();}
    static void literal(String value,String label)throws IOException{require(!value.isEmpty()&&value.length()<=4096,"Require nonempty literal "+label+" at most 4096 characters");}
}
