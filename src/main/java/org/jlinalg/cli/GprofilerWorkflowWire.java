/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerGostClient;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.GprofilerWorkflowFiles.*;

/** Complete request/response protocol; saved replay cannot construct a network transport. */
final class GprofilerWorkflowWire {
    private GprofilerWorkflowWire() { }
    static String prefix(int index){return "wire/"+String.format(Locale.ROOT,"%03d",index);}
    static final class Capture {
        private final Outputs outputs;
        private final String origin;
        private int attempted;
        Capture(Outputs outputs,String origin){this.outputs=outputs;this.origin=origin;}
        void append(List<GprofilerGostClient.Exchange> exchanges)throws IOException{
            while(attempted<exchanges.size()){
                var exchange=exchanges.get(attempted++);String stem=prefix(attempted);
                var request=exchange.request();var reply=exchange.reply();
                outputs.bytes(stem+"-request.body",request.body());outputs.bytes(stem+"-response.body",reply.body());
                outputs.json(stem+"-exchange.json",map("method","POST","url",request.uri().toString(),
                    "timeout_nanos",request.timeout().toNanos(),"maximum_response_bytes",request.maximumResponseBytes(),
                    "status",reply.status(),"state",reply.state().toString(),"failure",reply.failure(),
                    "headers",new TreeMap<>(reply.headers()),"transport_origin",origin));
            }
        }
    }
    static final class Replay implements GprofilerGostClient.Transport {
        private final Path root;
        private final Map<String,Object> request;
        private final String origin;
        private int count;
        Replay(Path root,Map<String,Object> request,String origin){this.root=root;this.request=request;this.origin=origin;}
        @Override public GprofilerGostClient.Reply send(GprofilerGostClient.Request sent)throws IOException{
            String stem=prefix(++count);
            var wire=read(root.resolve(stem+"-exchange.json"),1048576);
            require(wire.keySet().equals(Set.of("method","url","timeout_nanos","maximum_response_bytes","status","state","failure","headers","transport_origin")),"Changed wire schema");
            require("POST".equals(wire.get("method"))&&sent.uri().toString().equals(wire.get("url")),"Saved method/URL differs from reconstructed request");
            require(number(wire,"timeout_nanos")==sent.timeout().toNanos()&&number(wire,"maximum_response_bytes")==sent.maximumResponseBytes(),"Saved wire limits differ");
            require(origin.equals(wire.get("transport_origin")),"Saved transport-origin chain differs");
            require(Arrays.equals(bytes(root.resolve(stem+"-request.body"),cap(request,"maximum_request_bytes")),sent.body()),"Saved request bytes differ from scientific request");
            var headers=new TreeMap<String,List<String>>();
            for(var entry:object(wire.get("headers")).entrySet()){
                var values=new ArrayList<String>();for(Object value:list(entry.getValue()))values.add(text(value,"header"));headers.put(entry.getKey(),values);
            }
            Object reason=wire.get("failure");require(reason==null||reason instanceof String,"Invalid wire failure field");
            GprofilerGostClient.State state;
            try{state=GprofilerGostClient.State.valueOf(text(wire.get("state"),"wire state"));}
            catch(IllegalArgumentException e){throw new IOException("Unknown wire transfer state",e);}
            return new GprofilerGostClient.Reply((int)integer(wire.get("status"),0,599),headers,
                bytes(root.resolve(stem+"-response.body"),cap(request,"maximum_response_bytes")),state,(String)reason);
        }
        void finish(int expected)throws IOException{
            require(count==expected,"Unexpected saved exchange count");
            var names=new TreeSet<String>();
            for(int i=1;i<=count;i++)for(String suffix:List.of("-request.body","-response.body","-exchange.json"))names.add(Path.of(prefix(i)+suffix).getFileName().toString());
            try(var entries=Files.list(root.resolve("wire"))){require(new TreeSet<>(entries.map(p->p.getFileName().toString()).toList()).equals(names),"Unexpected saved wire file family");}
        }
    }
}
