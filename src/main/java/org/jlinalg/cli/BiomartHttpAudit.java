/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Audits complete original Curl diagnostics without replacing the original response parser. */
final class BiomartHttpAudit {
    private BiomartHttpAudit() { }
    private record Wire(String context,String header,byte[] body) { }
    static Map<String,Object> inspect(Path home,Map<String,Object> report,BiomartRequest.Request r,Map<String,List<String>> plans,List<String> warnings)throws IOException {
        require(text(report.get("trace_error")).isEmpty(),"Incomplete HTTP diagnostic capture");String hostname=URI.create(text(r.endpoint().get("base_url"))).getHost();int port=(int)integer(r.endpoint().get("port"),1,65535);var requests=new ArrayList<Wire>();String header=null,context=null;var body=new ByteArrayOutputStream();int index=0;long total=0;
        for(Object item:list(report.get("events"))){var event=object(item);require(integer(event.get("index"),1,Integer.MAX_VALUE)==++index,"HTTP trace sequence incomplete");int type=(int)integer(event.get("type"),0,4);String name=text(event.get("file"));require(name.equals(String.format(Locale.ROOT,"http/%06d-type%d.bin",index,type)),"HTTP trace filename differs");byte[] raw=NextflowFiles.bytes(home.resolve(name),16777216);require(raw.length==integer(event.get("bytes"),0,Integer.MAX_VALUE),"Raw HTTP event length differs");require(raw.length<=r.httpBytes()-total,"Complete HTTP byte capacity exceeded");total+=raw.length;
            if(type==2){if(header!=null)requests.add(new Wire(context,header,body.toByteArray()));header=NextflowFiles.utf8(raw);context=text(event.get("context"));body.reset();}
            if(type==4){require(header!=null,"HTTP request body has no captured headers");body.writeBytes(raw);}
        }
        if(header!=null)requests.add(new Wire(context,header,body.toByteArray()));require(!requests.isEmpty(),"No complete original HTTP requests captured");var actual=new LinkedHashMap<String,List<String>>();var audit=new ArrayList<Map<String,Object>>();
        for(Wire wire:requests){String[] lines=wire.header().split("\\r?\\n");String[] first=lines[0].split(" ");require(first.length==3,"Malformed original outgoing HTTP start line");String method=first[0],target=first[1];
            if(method.equals("CONNECT")){require(target.equalsIgnoreCase(hostname+":"+port)&&wire.body().length==0,"Off-target HTTP proxy tunnel");continue;}
            require(Set.of("GET","POST").contains(method),"Unexpected original HTTP method");URI uri;try{uri=URI.create(target);}catch(IllegalArgumentException invalid){throw new IOException("Malformed original request target",invalid);}require(uri.getRawPath().equals("/biomart/martservice")&&uri.getRawFragment()==null,"Off-profile original HTTP path");String host=null;for(String line:lines)if(line.regionMatches(true,0,"Host:",0,5)){require(host==null,"Duplicate Host header");host=line.substring(5).strip();}require(host!=null&&(host.equalsIgnoreCase(hostname+":"+port)||port==443&&host.equalsIgnoreCase(hostname)),"Original HTTP request left the declared endpoint: "+host);
            var row=new LinkedHashMap<String,Object>();row.put("context",wire.context());row.put("method",method);row.put("target",target);row.put("body_bytes",wire.body().length);row.put("body_sha256",NextflowFiles.sha(wire.body()));
            if(method.equals("POST")){require(plans.containsKey(wire.context()),"Unexpected metadata/off-case query submission");String form=NextflowFiles.utf8(wire.body());require(form.startsWith("query=")&&form.indexOf('&')<0,"Original POST is not the single getBM query form");String xml;try{xml=URLDecoder.decode(form.substring(6),StandardCharsets.UTF_8);}catch(IllegalArgumentException invalid){throw new IOException("Invalid original query form encoding",invalid);}actual.computeIfAbsent(wire.context(),k->new ArrayList<>()).add(xml);row.put("xml",xml);}
            else require(plans.isEmpty(),"Unexpected metadata/service discovery during the original query phase");audit.add(row);
        }
        int fallbacks=0;
        for(var entry:plans.entrySet()){
            var expected=new ArrayList<>(new LinkedHashSet<>(entry.getValue()));var observed=actual.getOrDefault(entry.getKey(),List.of());int cursor=0;
            for(String xml:expected){require(cursor<observed.size()&&xml.equals(observed.get(cursor++)),"Complete original getBM HTTP query differs from native plan: "+entry.getKey());if(cursor<observed.size()&&observed.get(cursor).equals(xml.replace("TSV","HTML"))){require(r.allowHtmlFallback(),"Original HTML fallback occurred but was not explicitly allowed; inspect original requests and responses");fallbacks++;cursor++;warnings.add("ORIGINAL HTML FALLBACK ["+entry.getKey()+"]: original source globally replaces TSV with HTML in the entire XML, including filter values. Both original requests/responses are retained; no local correction was made.");}}
            require(cursor==observed.size(),"Extra or reordered original HTTP query in case "+entry.getKey());
        }
        NextflowFiles.writeJson(home.resolve("native-http-audit.json"),Map.of("complete_events",index,"complete_diagnostic_bytes",total,"requests",audit,"original_HTML_fallbacks",fallbacks,"redirect_or_endpoint_substitution_permitted",false));return Map.of("complete_events",index,"requests",audit.size(),"original_HTML_fallbacks",fallbacks);
    }
}
