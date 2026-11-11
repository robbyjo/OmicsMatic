/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.genetics.BioMartQueries;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit getBM inputs; no inferred gene mapping, release, assembly or mask. */
final class BiomartRequest {
    private BiomartRequest() { }
    record Query(String id,BioMartQueries.Request request,Map<String,Object> original) { }
    record Request(String edition,Path runtime,Path out,Map<String,Object> endpoint,List<Query> queries,
                   int timeoutSeconds,int httpTimeoutSeconds,long outputBytes,long httpBytes,boolean allowHtmlFallback) {
        Map<String,Object> worker() {
            return Map.of("edition",edition,"endpoint",endpoint,"queries",queries.stream().map(Query::original).toList(),
                "maximum_http_bytes",httpBytes,"http_timeout_seconds",httpTimeoutSeconds);
        }
    }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","edition","runtime_manifest","out","endpoint","queries","timeout_seconds","http_timeout_seconds","maximum_output_bytes","maximum_http_bytes","allow_original_html_fallback");
        require(integer(m.get("schema_version"),1,1)==1,"Require schema_version1");String version=text(m.get("edition"));require(Set.of("2.60.1","2.68.0").contains(version),"Choose original biomaRt2.60.1 or2.68.0");
        var e=object(m.get("endpoint"));NextflowFiles.keys(e,"base_url","port","mart","registry_version","database","dataset","assembly","virtual_schema");String base=text(e.get("base_url"));URI uri;
        try{uri=URI.create(base);}catch(IllegalArgumentException invalid){throw new IOException("Invalid explicit BioMart base URL",invalid);}
        require(uri.getHost()!=null&&uri.getRawUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&uri.getPort()==-1&&(uri.getRawPath()==null||uri.getRawPath().isEmpty()),"Base URL must contain only scheme and hostname; port is separate");
        require(uri.getScheme().equals("https")||uri.getScheme().equals("http")&&uri.getHost().equals("127.0.0.1"),"HTTPS required; HTTP is reserved for explicitly labelled localhost protocol controls");
        require(!Set.of("www.ensembl.org","useast.ensembl.org","asia.ensembl.org").contains(uri.getHost().toLowerCase(Locale.ROOT)),"Supply a pinned Ensembl archive or GRCh37 endpoint; the original cache hash probes archives for moving main/mirror hosts");integer(e.get("port"),1,65535);
        for(String key:List.of("mart","dataset","virtual_schema"))require(text(e.get(key)).matches("[A-Za-z0-9_]+"),"Require a literal metadata identifier: "+key);
        for(String key:List.of("registry_version","database","assembly"))require(!label(e.get(key)).isBlank(),"Explicit registry version, database and assembly labels required");
        var queries=new ArrayList<Query>();var ids=new HashSet<String>();long values=0;
        for(Object raw:list(m.get("queries"))){
            require(queries.size()<32,"At most32 complete queries per atomic batch");var q=object(raw);NextflowFiles.keys(q,"id","attributes","filters","unique_rows","check_filters");String id=text(q.get("id"));require(id.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")&&ids.add(id),"Unique portable query IDs required");var attrs=strings(q.get("attributes"),1000);require(!attrs.isEmpty(),"Attributes must not be empty");var fs=new ArrayList<BioMartQueries.Filter>();
            for(Object item:list(q.get("filters"))){require(fs.size()<32,"At most32 explicit filters");var f=object(item);NextflowFiles.keys(f,"name","type","values");String name=label(f.get("name"));BioMartQueries.ValueType type;
                try{type=BioMartQueries.ValueType.valueOf(text(f.get("type")));}catch(IllegalArgumentException invalid){throw new IOException("Filter type must be CHARACTER, NUMERIC or LOGICAL",invalid);}
                List<String> tokens=NextflowFiles.strings(f.get("values"));values=Math.addExact(values,tokens.size());require(values<=1000000,"Complete batch filter-value capacity exceeded");fs.add(new BioMartQueries.Filter(name,type,tokens));
            }
            var edition=version.equals("2.60.1")?BioMartQueries.Edition.BIOMART_2_60_1:BioMartQueries.Edition.BIOMART_2_68_0;
            queries.add(new Query(id,new BioMartQueries.Request(edition,text(e.get("dataset")),text(e.get("virtual_schema")),attrs,fs,bool(q.get("unique_rows")),bool(q.get("check_filters"))),Collections.unmodifiableMap(q)));
        }
        require(!queries.isEmpty(),"Require1..32 queries");return new Request(version,resolve(parent,label(m.get("runtime_manifest"))),resolve(parent,label(m.get("out"))),Collections.unmodifiableMap(e),List.copyOf(queries),
            (int)number(m,"timeout_seconds",600,1,86400),(int)number(m,"http_timeout_seconds",60,1,600),number(m,"maximum_output_bytes",536870912,1024,4294967296L),number(m,"maximum_http_bytes",134217728,1024,1073741824),bool(m.get("allow_original_html_fallback")));
    }
}
