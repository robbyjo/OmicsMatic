/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jlinalg.enrichr.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Complete deterministic protocol and output derivation, with live and offline transports. */
final class EnrichrWorkflowModel {
    private EnrichrWorkflowModel() { }
    interface Recorder { void save(List<EnrichrClient.Exchange> exchanges)throws IOException; int count(); }
    interface Call<T> { T apply(EnrichrClient.Session session)throws IOException,InterruptedException; }
    static final class Access {
        private final EnrichrClient client;
        private final Recorder recorder;
        private final long interval;
        private long previous;
        private EnrichrClient.Reply last;
        Access(EnrichrClient client,Recorder recorder,long interval){this.client=client;this.recorder=recorder;this.interval=interval;}
        <T>T call(Call<T> call)throws IOException{
            if(previous!=0){long remaining=interval-(System.nanoTime()-previous);if(remaining>0)try{TimeUnit.NANOSECONDS.sleep(remaining);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted before next API operation");}}
            var session=client.openSession();
            try{return call.apply(session);}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("API transfer interrupted; no complete result");}
            finally{
                var captures=session.exchanges();
                if(captures.size()>1)throw new IOException("Unexpected multi-request acquisition");
                if(!captures.isEmpty())last=captures.get(0).reply();
                boolean interrupted=Thread.interrupted();
                try{recorder.save(captures);}finally{if(interrupted)Thread.currentThread().interrupt();previous=System.nanoTime();}
            }
        }
        byte[] lastBody()throws IOException{if(last==null)throw new IOException("No complete captured response");return last.body();}
    }
    static final class WireWriter implements Recorder {
        private final EnrichrWorkflowFiles.Outputs files;
        private final String origin;
        private int count;
        WireWriter(EnrichrWorkflowFiles.Outputs files,String origin){this.files=files;this.origin=origin;}
        @Override public int count(){return count;}
        @Override public void save(List<EnrichrClient.Exchange> captures)throws IOException{
            for(var capture:captures){
                if(++count>128)throw new IOException("Protocol request population exceeds capacity");
                String prefix=String.format(Locale.ROOT,"wire/%04d",count);
                var request=capture.request();var reply=capture.reply();byte[] sent=request.body(),received=reply.body();
                files.bytes(prefix+".request",sent);files.bytes(prefix+".response",received);
                var metadata=new LinkedHashMap<String,Object>();
                metadata.put("method",request.method());metadata.put("uri",request.uri().toString());metadata.put("content_type",request.contentType());
                metadata.put("timeout_millis",request.timeout().toMillis());metadata.put("maximum_response_bytes",request.maximumResponseBytes());
                metadata.put("status",reply.status());metadata.put("headers",reply.headers());metadata.put("complete",reply.complete());
                metadata.put("transfer_state",reply.transferState().toString());metadata.put("failure",reply.failure());metadata.put("transport_origin",origin);
                metadata.put("request",EnrichrWorkflowFiles.pin(sent));metadata.put("response",EnrichrWorkflowFiles.pin(received));
                files.json(prefix+".json",metadata);
            }
        }
    }
    static final class WireReplay implements EnrichrClient.Transport,Recorder {
        private final Path root;
        private final int total;
        private final long cap;
        private final String origin;
        private int consumed,recorded;
        WireReplay(Path root,long cap,String origin)throws IOException{
            this.root=root;this.cap=cap;this.origin=origin;
            Path wire=root.resolve("wire");if(!Files.isDirectory(wire,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing complete wire directory");
            List<String> names;
            try(var list=Files.list(wire)){names=list.map(p->p.getFileName().toString()).sorted().toList();}
            if(names.size()%3!=0||names.isEmpty()||names.size()>384)throw new IOException("Invalid wire population");
            total=names.size()/3;var expected=new TreeSet<String>();
            for(int i=1;i<=total;i++)for(String suffix:List.of(".json",".request",".response"))expected.add(String.format(Locale.ROOT,"%04d",i)+suffix);
            if(!new TreeSet<>(names).equals(expected))throw new IOException("Wire sequence/members differ");
        }
        @Override public EnrichrClient.Reply send(EnrichrClient.Request expected)throws IOException{
            if(++consumed>total)throw new IOException("Captured protocol ended before complete derivation");
            String prefix=String.format(Locale.ROOT,"wire/%04d",consumed);
            var m=EnrichrWorkflowFiles.read(root.resolve(prefix+".json"),2_000_000);
            keys(m,Set.of("method","uri","content_type","timeout_millis","maximum_response_bytes","status","headers","complete","transfer_state","failure","transport_origin","request","response"));
            byte[] sent=EnrichrWorkflowFiles.bytes(root.resolve(prefix+".request"),cap),received=EnrichrWorkflowFiles.bytes(root.resolve(prefix+".response"),cap);
            if(!same(m.get("request"),EnrichrWorkflowFiles.pin(sent))||!same(m.get("response"),EnrichrWorkflowFiles.pin(received)))throw new IOException("Wire body pin differs");
            if(!expected.method().equals(m.get("method"))||!expected.uri().toString().equals(m.get("uri"))
                    ||!same(expected.timeout().toMillis(),m.get("timeout_millis"))
                    ||!same(expected.maximumResponseBytes(),m.get("maximum_response_bytes"))||!origin.equals(m.get("transport_origin")))throw new IOException("Captured request differs from exact derived protocol at "+consumed);
            Object content=m.get("content_type");String saved=content==null?null:(String)content;
            if(expected.contentType()!=null&&expected.contentType().startsWith("multipart/form-data; boundary=")){
                if(!multipart(expected.body(),expected.contentType()).equals(multipart(sent,saved)))throw new IOException("Captured multipart gene list/description differs");
            }else if(!Objects.equals(expected.contentType(),saved)||!Arrays.equals(expected.body(),sent))throw new IOException("Captured form/request bytes differ");
            var headers=new LinkedHashMap<String,List<String>>();
            for(var entry:object(m.get("headers"),"headers").entrySet()){
                var values=new ArrayList<String>();for(Object value:list(entry.getValue(),"header values")){if(!(value instanceof String s))throw new IOException("Header value must be a string");values.add(s);}headers.put(entry.getKey(),values);
            }
            var state=EnrichrClient.TransferState.valueOf((String)m.get("transfer_state"));
            if(!Boolean.valueOf(state==EnrichrClient.TransferState.COMPLETE).equals(m.get("complete")))throw new IOException("Transfer state differs");
            Object failure=m.get("failure");if(failure!=null&&!(failure instanceof String))throw new IOException("Invalid failure field");
            Object status=m.get("status");
            if(!(status instanceof Number n)||!same(n,n.longValue())||n.longValue()<0||n.longValue()>599)throw new IOException("Invalid stored HTTP status");
            return new EnrichrClient.Reply(((Number)status).intValue(),headers,received,state,(String)failure);
        }
        @Override public void save(List<EnrichrClient.Exchange> captures)throws IOException{recorded+=captures.size();if(recorded!=consumed)throw new IOException("Protocol capture accounting differs");}
        @Override public int count(){return consumed;}
        void finish()throws IOException{if(consumed!=total||recorded!=total)throw new IOException("Unused/incomplete captured protocol");}
        private static Map<String,String> multipart(byte[] raw,String contentType)throws IOException{
            String prefix="multipart/form-data; boundary=";
            if(contentType==null||!contentType.startsWith(prefix))throw new IOException("Expected multipart content type");
            String boundary=contentType.substring(prefix.length());if(!boundary.matches("[A-Za-z0-9]{1,128}"))throw new IOException("Invalid MIME boundary");
            String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
            String first="--"+boundary+"\r\nContent-Disposition: form-data; name=\"list\"\r\n\r\n";
            String middle="\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"description\"\r\n\r\n";
            String end="\r\n--"+boundary+"--\r\n";
            int at=text.indexOf(middle,first.length());
            if(!text.startsWith(first)||!text.endsWith(end)||at<0||text.indexOf(middle,at+middle.length())>=0)throw new IOException("Invalid exact multipart shape");
            return Map.of("list",text.substring(first.length(),at),"description",text.substring(at+middle.length(),text.length()-end.length()));
        }
    }
    record Calculation(List<String> warnings,int libraries,long terms,long hits,int requests) { }

    static Calculation derive(Path root,Map<String,Object> request,Map<String,Object> execution,EnrichrClient client,
            Recorder recorder,EnrichrWorkflowFiles.Outputs output,boolean offline)throws IOException{
        var warnings=new ArrayList<String>();warnings.add(EnrichrWorkflowContracts.SCOPE);
        boolean enrich=request.get("operation").equals("enrichment");
        var limits=object(request.get("limits"),"limits");long inputCap=EnrichrWorkflowContracts.number(limits,"maximum_input_bytes");
        long maxGenes=EnrichrWorkflowContracts.number(limits,"maximum_genes");
        List<String> query=enrich?EnrichrWorkflowFiles.genes(root.resolve("inputs/query.txt"),inputCap,maxGenes):List.of();
        var background=request.containsKey("background")?object(request.get("background"),"background"):Map.<String,Object>of();
        boolean custom=enrich&&background.get("kind").equals("explicit");
        List<String> bg=custom?EnrichrWorkflowFiles.genes(root.resolve("inputs/background.txt"),inputCap,maxGenes):List.of();
        Set<String> queryIds=new HashSet<>(query),backgroundIds=new HashSet<>(bg);
        long inputBytes=enrich?Files.size(root.resolve("inputs/query.txt")):0;
        if(custom)inputBytes=Math.addExact(inputBytes,Files.size(root.resolve("inputs/background.txt")));
        if(inputBytes>inputCap)throw new IOException("Combined captured input byte capacity exceeded");
        var names=list(request.get("libraries"),"libraries").stream().map(String.class::cast).toList();
        var access=new Access(client,recorder,offline?0:TimeUnit.MILLISECONDS.toNanos(EnrichrWorkflowContracts.number(limits,"request_interval_millis")));
        List<String> available=access.call(EnrichrClient.Session::libraries);
        for(String name:names)if(!available.contains(name))throw new EnrichrWorkflowFiles.Unavailable("Exact requested current library is unavailable: "+name+"; restore the exact resource or retain the skip; no alias/year fallback",null);
        var snapshots=new ArrayList<Map<String,Object>>();long totalTerms=0,totalHits=0,zeroP=0,infiniteScores=0,unknownHitEntries=0;boolean echoIdentityChanged=false;
        if(custom)warnings.add("Custom-background normalization is unobserved: no qualified full query echo/effective background population is available. Explicit acknowledgement is recorded; only returned literal hit identities can be checked.");
        try(var terms=new EnrichrWorkflowFiles.Table(output,"terms.tsv","library_index","library","rank","term","p_value_token","odds_ratio_token","combined_score_token","adjusted_p_value_token","old_p_value_token","old_adjusted_p_value_token","reported_hit_entries");
            var hits=new EnrichrWorkflowFiles.Table(output,"hits.tsv","library_index","library","rank","hit_index","gene");
            var libraryTerms=new EnrichrWorkflowFiles.Table(output,"library-terms.tsv","library_index","library","term_index","term","description","gene_fields","unique_nonempty_genes","empty_fields","duplicate_nonempty_fields");
            var memberships=new EnrichrWorkflowFiles.Table(output,"memberships.tsv","library_index","term_index","gene_field_index","gene")){
            for(int i=0;i<names.size();i++){
                String name=names.get(i);var library=access.call(s->s.library(name));byte[] raw=access.lastBody();
                output.bytes(String.format(Locale.ROOT,"libraries/%03d.gmt",i+1),raw);
                var info=new LinkedHashMap<String,Object>();info.put("library_index",i+1);info.put("library",name);
                info.put("download_wire_index",recorder.count());info.put("download",EnrichrWorkflowFiles.pin(raw));info.put("library_terms",library.terms().size());snapshots.add(info);
                warnings.addAll(library.warnings().stream().map(w->name+": "+w).toList());
                int termIndex=0;
                for(var term:library.terms()){
                    termIndex++;var nonempty=new HashSet<String>();int empty=0;
                    for(String gene:term.genes()){if(gene.isEmpty())empty++;else nonempty.add(gene);}
                    libraryTerms.row(i+1,name,termIndex,term.name(),term.description(),term.genes().size(),nonempty.size(),empty,term.genes().size()-empty-nonempty.size());
                    for(int j=0;j<term.genes().size();j++)memberships.row(i+1,termIndex,j+1,term.genes().get(j));
                }
            }
            List<String> echoed=List.of();EnrichrClient.Submission submitted=null;EnrichrClient.Background submittedBackground=null;
            if(enrich){
                String description=(String)request.get("description");
                submitted=access.call(s->s.submit(custom?EnrichrClient.Channel.CUSTOM_BACKGROUND:EnrichrClient.Channel.STANDARD,query,description));
                var submission=submitted;
                if(custom){
                    submittedBackground=access.call(s->s.background(bg));
                }else{
                    echoed=access.call(s->s.view(submission));
                    echoIdentityChanged=!counts(query).equals(counts(echoed));
                    if(echoIdentityChanged)EnrichrWorkflowContracts.policy(request,"identity_changes","Standard server gene echo differs in literal identity or multiplicity from submitted query",warnings);
                    if(!query.equals(echoed))warnings.add("Server query order/multiplicity differs from input order; both sequences are retained, never substituted.");
                }
                var submittedBg=submittedBackground;
                for(int i=0;i<names.size();i++){
                    String name=names.get(i);var result=access.call(s->custom?s.enrich(submission,submittedBg,name):s.enrich(submission,name));
                    snapshots.get(i).put("results_wire_index",recorder.count());snapshots.get(i).put("reported_terms",result.rows().size());
                    warnings.addAll(result.warnings().stream().map(w->name+": "+w).toList());
                    if(result.rows().isEmpty())EnrichrWorkflowContracts.policy(request,"empty_results",name+": empty returned term family, not evidence of pathway absence",warnings);
                    var rows=new ArrayList<Object>();
                    for(var row:result.rows()){
                        if(row.pValue().decimal().signum()==0)zeroP++;
                        if(!row.oddsRatio().isFinite()||!row.combinedScore().isFinite()){infiniteScores++;EnrichrWorkflowContracts.policy(request,"infinite_scores",name+": positive infinite reported score retained",warnings);}
                        terms.row(i+1,name,row.rank(),row.term(),row.pValue().token(),row.oddsRatio().token(),row.combinedScore().token(),row.adjustedPValue().token(),row.oldPValue().token(),row.oldAdjustedPValue().token(),row.overlappingGenes().size());
                        for(int j=0;j<row.overlappingGenes().size();j++){
                            String hit=row.overlappingGenes().get(j);
                            if(!queryIds.contains(hit)||(custom&&!backgroundIds.contains(hit))){unknownHitEntries++;EnrichrWorkflowContracts.policy(request,"identity_changes",name+": returned hit is outside the literal submitted query/background: "+hit,warnings);}
                            hits.row(i+1,name,row.rank(),j+1,hit);totalHits++;
                        }
                        var r=new LinkedHashMap<String,Object>();r.put("rank",row.rank());r.put("term",row.term());r.put("p_value_token",row.pValue().token());r.put("odds_ratio_token",row.oddsRatio().token());r.put("combined_score_token",row.combinedScore().token());r.put("hit_genes",row.overlappingGenes());r.put("adjusted_p_value_token",row.adjustedPValue().token());r.put("old_p_value_token",row.oldPValue().token());r.put("old_adjusted_p_value_token",row.oldAdjustedPValue().token());rows.add(r);
                        totalTerms++;
                    }
                    output.json(String.format(Locale.ROOT,"results/%03d.json",i+1),Map.of("library",name,"rows",rows,"numeric_encoding","exact source numeric token strings; no binary64 narrowing, recalculation or floor"));
                }
                for(int i=0;i<names.size();i++){
                    String name=names.get(i);access.call(s->s.library(name));byte[] later=access.lastBody();
                    if(!same(snapshots.get(i).get("download"),EnrichrWorkflowFiles.pin(later)))throw new IOException("Library bytes changed between pre/post observations: "+name+"; no complete batch admitted");
                    snapshots.get(i).put("postcheck_wire_index",recorder.count());snapshots.get(i).put("pre_post_observed_identical",true);
                }
                warnings.add("Pre/post library downloads matched byte-for-byte. This checks observed stability, not an authenticated atomic server snapshot during computation or historical author-library recovery.");
            }
            writeGeneLedgers(output,query,bg,echoed,enrich&&!custom);
        }
        if(!enrich)warnings.add("Library-resource acquisition only. No gene upload, enrichment test, gene-set filtering or downstream ranked FGSEA fit was performed.");
        output.json("libraries.json",Map.of("libraries",snapshots));
        var summary=new LinkedHashMap<String,Object>();summary.put("schema_version",1);summary.put("profile",EnrichrWorkflowContracts.PROFILE);summary.put("operation",request.get("operation"));
        summary.put("library_count",names.size());summary.put("query_rows",query.size());summary.put("background_rows",custom?bg.size():null);summary.put("background_kind",enrich?background.get("kind"):null);
        summary.put("reported_terms",totalTerms);summary.put("reported_hit_entries",totalHits);summary.put("protocol_requests",recorder.count());summary.put("execution",execution);
        var flags=Map.<String,Object>of("zero_p_value_terms",zeroP,"positive_infinite_score_terms",infiniteScores,"out_of_literal_input_hit_entries",unknownHitEntries,"server_echo_identity_changed",echoIdentityChanged,"custom_normalization_unobserved",custom);
        summary.put("scientific_flags",flags);summary.put("warnings",warnings);
        output.json("summary.json",summary);
        output.bytes("WARNINGS.md",("# Completed with explicit scope and warnings\n\n"+String.join("\n\n",warnings)+"\n\nRequired actions: "+EnrichrWorkflowContracts.ACTION+"\n").getBytes(StandardCharsets.UTF_8));
        output.json("analysis-status.json",Map.of("schema_version",1,"profile",EnrichrWorkflowContracts.PROFILE,"status","completed-with-warnings","result_admitted",true,"execution",execution,"scientific_flags",flags,"whole_paper_reproduced",false));
        return new Calculation(List.copyOf(warnings),names.size(),totalTerms,totalHits,recorder.count());
    }
    private static Map<String,Integer> counts(List<String> values){var result=new TreeMap<String,Integer>();for(String value:values)result.merge(value,1,Integer::sum);return result;}
    private static void writeGeneLedgers(EnrichrWorkflowFiles.Outputs output,List<String> query,List<String> background,List<String> echoed,boolean fullEcho)throws IOException{
        var observed=counts(echoed);
        try(var q=new EnrichrWorkflowFiles.Table(output,"query-ledger.tsv","input_row","submitted_gene","input_occurrences","server_occurrences","server_echo_available");
            var b=new EnrichrWorkflowFiles.Table(output,"background-ledger.tsv","input_row","submitted_gene","effective_server_population_known");
            var e=new EnrichrWorkflowFiles.Table(output,"server-query.tsv","server_row","gene")){
            var input=counts(query);
            for(int i=0;i<query.size();i++)q.row(i+1,query.get(i),input.get(query.get(i)),fullEcho?observed.getOrDefault(query.get(i),0):"",fullEcho);
            for(int i=0;i<background.size();i++)b.row(i+1,background.get(i),false);
            for(int i=0;i<echoed.size();i++)e.row(i+1,echoed.get(i));
        }
    }
}
