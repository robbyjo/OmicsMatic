/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import org.jlinalg.gprofiler.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.GprofilerWorkflowFiles.*;

/** Original calls and lossless reports; native checks are protocol/arithmetic audits, not a refit. */
final class GprofilerWorkflowModel {
    private GprofilerWorkflowModel() { }
    record Result(GprofilerGostClient.Result metadata,GprofilerGostClient.Result enrichment,int calls,boolean cookieHeaders) { }
    static Result invoke(Map<String,Object> request,GprofilerGostClient client,GprofilerWorkflowWire.Capture capture)throws IOException{
        var session=client.openSession();IOException prior=null;
        try{
            var payload=object(request.get("payload"));String organism=text(payload.get("organism"),"organism");
            var metadata=session.versions(service(request),organism);
            require(organism.equals(object(GprofilerJson.read(metadata.response())).get("organism")),"Metadata organism differs; no gene list submitted");
            if(capture!=null)capture.append(session.exchanges());
            if("metadata".equals(request.get("operation")))return new Result(metadata,null,1,cookies(session));
            var result=session.profile(service(request),payload,true);
            return new Result(metadata,result,2,cookies(session));
        }catch(IOException e){prior=e;throw e;}
        finally{
            if(capture!=null)try{capture.append(session.exchanges());}
                catch(IOException save){if(prior!=null)save.addSuppressed(prior);throw save;}
        }
    }
    static Map<String,Object> derive(Map<String,Object> request,Result result,Map<String,Object> execution,Outputs outputs)throws IOException{
        var warnings=new ArrayList<String>();warnings.add(SCOPE);
        if(result.cookieHeaders())warnings.add(PRIVACY);
        outputs.bytes("metadata.json",result.metadata().response());
        var summary=map("schema_version",1,"profile",PROFILE,"operation",request.get("operation"),
            "observed_build",result.metadata().observedBuild(),"original_calls",result.calls(),
            "execution",execution,"cookie_response_headers_recorded",result.cookieHeaders(),"independent_statistical_refit",false,"authentication",false);
        long zeroP=0,sentinels=0,highlighted=0;int terms=0;boolean mappingChanges=false,customUnobserved=false;
        if(result.enrichment()!=null){
            var payload=object(request.get("payload"));byte[] raw=result.enrichment().response();var document=object(GprofilerJson.read(raw));
            var rows=list(document.get("result"));require(rows.size()<=cap(request,"maximum_result_rows"),"Result row capacity exceeded; no admitted result");terms=rows.size();
            var meta=object(document.get("meta"));var echo=object(meta.get("query_metadata"));
            require(payload.get("organism").equals(echo.get("organism")),"Result organism differs from requested organism");
            var serverQueries=object(echo.get("queries"));require(!serverQueries.isEmpty(),"Missing echoed query identities");
            Object query=payload.get("query");Map<String,Object> expectedQueries=query instanceof Map<?,?>?object(query):map("query_1",query);
            var differences=new ArrayList<Object>();
            if(!same(expectedQueries,serverQueries))differences.add(map("field","queries","requested",expectedQueries,"observed",serverQueries));
            if(!new ArrayList<>(expectedQueries.keySet()).equals(new ArrayList<>(serverQueries.keySet())))differences.add(map("field","query_name_order","requested",new ArrayList<>(expectedQueries.keySet()),"observed",new ArrayList<>(serverQueries.keySet())));
            for(String key:List.of("organism","all_results","ordered","no_iea","sources","combined","numeric_ns","domain_scope","measure_underrepresentation","significance_threshold_method","user_threshold","no_evidences","highlight")){
                require(echo.containsKey(key),"Missing echoed scientific option: "+key);
                if(!same(payload.get(key),echo.get(key)))differences.add(map("field",key,"requested",payload.get(key),"observed",echo.get(key)));
            }
            if(!differences.isEmpty())policy(request,"server_normalization","Server normalized echoed identities/options; inspect normalization.json and both original populations. No Java repair was performed",warnings);
            outputs.json("normalization.json",differences);outputs.json("query-metadata.json",echo);
            var genes=object(meta.get("genes_metadata"));var failed=list(genes.get("failed"));var ambiguous=object(genes.get("ambiguous"));var duplicates=list(genes.get("duplicates"));
            object(genes.get("query"));mappingChanges=!failed.isEmpty()||!ambiguous.isEmpty()||!duplicates.isEmpty();
            if(mappingChanges)policy(request,"mapping_changes","Service reports failed, ambiguous or duplicate identifiers; inspect gene-mapping.json and the exact submitted request. No identifiers were replaced or dropped by Java",warnings);
            outputs.json("gene-mapping.json",genes);
            customUnobserved=text(payload.get("domain_scope"),"domain").startsWith("custom");
            if(customUnobserved)warnings.add("Custom background submission is retained exactly, but the service does not echo its complete resolved background mapping. Effective domain sizes are original per-term/source values; complete background population identity was not independently established");
            boolean combined=Boolean.TRUE.equals(payload.get("combined"));int queries=serverQueries.size();
            for(Object value:rows){
                var row=object(value);text(row.get("native"),"term ID");String source=text(row.get("source"),"source");text(row.get("name"),"term name");
                for(Object parent:list(row.get("parents")))text(parent,"parent term");
                long domain=integer(row.get("effective_domain_size"),0,Long.MAX_VALUE),size=integer(row.get("term_size"),0,domain);
                if(combined){
                    var ps=list(row.get("p_values"));var qs=list(row.get("query_sizes"));var hits=list(row.get("intersection_sizes"));
                    require(ps.size()==queries&&qs.size()==queries&&hits.size()==queries,"Combined vectors differ from echoed query order/dimensions");
                    for(int i=0;i<queries;i++){
                        var p=probability(ps.get(i));if(p.signum()==0)zeroP++;
                        long q=integer(qs.get(i),-1,domain),hit=integer(hits.get(i),-1,domain);
                        if(q==-1||hit==-1){require(q==-1&&hit==-1&&p.compareTo(BigDecimal.ONE)==0,"Invalid original combined absence sentinel");sentinels++;}
                        else require(hit<=q&&hit<=size,"Combined intersection exceeds its populations");
                    }
                }else{
                    require(serverQueries.containsKey(text(row.get("query"),"result query")),"Result query absent from server query metadata");
                    long q=integer(row.get("query_size"),0,domain),hit=integer(row.get("intersection_size"),0,Math.min(q,size));
                    if(probability(row.get("p_value")).signum()==0)zeroP++;
                    require(row.get("significant") instanceof Boolean,"Missing original significance flag");
                    ratio(row.get("precision"),hit,q,"precision");ratio(row.get("recall"),hit,size,"recall");
                }
                if(row.containsKey("highlighted")){require(row.get("highlighted") instanceof Boolean,"Invalid original driver flag");if(Boolean.TRUE.equals(row.get("highlighted")))highlighted++;}
                if(source.startsWith("GO:")&&Boolean.TRUE.equals(echo.get("highlight")))require(row.get("highlighted") instanceof Boolean,"Requested/echoed GO highlighting lacks original per-term flags");
            }
            if(terms==0)policy(request,"empty_results","Original service returned a complete empty result; inspect mapping and options before interpreting absence of enriched terms",warnings);
            if(zeroP>0)warnings.add("Original service reports P=0 for "+zeroP+" values; retained without flooring. Exact nonzero tails are unavailable");
            if(sentinels>0)warnings.add("Original combined output contains "+sentinels+" paired -1 count sentinels with P=1; retained as source absence indicators, not converted into zero counts or missing strings");
            outputs.bytes("result.json",raw);outputs.jsonLines("results.jsonl",rows);
        }
        summary.put("reported_terms",terms);summary.put("highlighted_terms",highlighted);summary.put("original_zero_p_values",zeroP);
        summary.put("original_combined_absence_cells",sentinels);summary.put("mapping_changes_reported",mappingChanges);
        summary.put("custom_background_mapping_unobserved",customUnobserved);summary.put("warnings",warnings);
        outputs.json("summary.json",summary);
        outputs.json("analysis-status.json",map("schema_version",1,"status","COMPLETE_REQUIRES_VALID_COMPLETION_MARKER",
            "result_admission","Only a valid COMPLETED.json with matching closed manifest admits this result", "warnings",warnings));
        StringBuilder notice=new StringBuilder("# WARNINGS AND REQUIRED ACTIONS\n\n");
        for(String warning:warnings)notice.append("- ").append(warning).append("\n\n");
        notice.append(ACTION).append("\n");outputs.text("WARNINGS.md",notice.toString());return summary;
    }
    private static void ratio(Object value,long numerator,long denominator,String label)throws IOException{
        if(denominator==0){require(value==null||probability(value).signum()>=0,"Invalid undefined source ratio");return;}
        double actual=probability(value).doubleValue(),expected=(double)numerator/denominator;
        require(Math.abs(actual-expected)<=1e-12+1e-10*Math.abs(expected),"Original "+label+" inconsistent with source counts");
    }
    private static boolean cookies(GprofilerGostClient.Session session){
        return session.exchanges().stream().anyMatch(e->e.reply().headers().keySet().stream().anyMatch(k->k.equalsIgnoreCase("set-cookie")));
    }
}
