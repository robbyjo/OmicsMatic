/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static java.nio.file.StandardOpenOption.CREATE_NEW;

/** Complete public MELODI Presto API responses, without substituting the unrelated R package. */
public final class MelodiPrestoCli {
    private MelodiPrestoCli() { }
    static final String ACTION="SKIP3 skips dependent analysis when permitted networking, the service, access, inputs or output prerequisites are unavailable. "
        +"Supply the named permitted requirement and retry NEW paths. FAIL2 admits no complete result, including server errors, invalid responses, "
        +"truncation or recording failure after a server return. Read WARNINGS.md, FAILURE.json, HTTP.json and complete response.bin; preserve all originals and partials. "
        +"Only final COMPLETED.json admits a recorded operation. The live service does not identify its deployed source or database snapshot. "
        +"A current response does not reproduce the paper's 3 April 2024 analysis or prove biological novelty, absence of evidence or causal links. "
        +"This is an upstream service adapter written in Java, not native Wilcoxon/AUC or local SemMedDB analysis. No software is installed. "
        +"Queries go to the explicitly recorded server. Use only inputs you intend to send there; --allow-network explicitly enables transmission.";
    private static final String DEFAULT_ENDPOINT="https://melodi-presto.mrcieu.ac.uk";
    private static final long MAX_REQUEST=1_048_576;
    private static final Set<String> BASE=Set.of("schema","operation","payload","server_snapshot");
    private static final Set<String> OPTIONAL=Set.of("endpoint","timeout_seconds","max_response_bytes");
    private static final Set<String> COMPLETE_FILES=Set.of("WARNINGS.md","source-request.json","request-payload.json","HTTP.json","response.bin","runtime.json","response-validation.json");
    private static final Set<String> ENRICH=Set.of("query","triple","subject_name","subject_type","subject_id","predicate",
        "object_name","object_type","object_id","localCount","localTotal","globalCount","globalTotal","odds","pval","pmids");
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String s){super(s);}
    }
    record Request(String operation,Map<String,Object> payload,URI uri,int seconds,int maximum) { }

    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){
        Path out=null;boolean owned=false,received=false;String phase="REQUEST";
        try{
            if(args.length==0||args[0].equals("--help")){
                System.out.println("run --request JSON --out NEW --allow-network\nverify --bundle SAVED --out NEW\n"+ACTION);return 0;
            }
            String operation=args[0];Map<String,String> options=options(args);
            Set<String> expected=operation.equals("run")?Set.of("request","out","allow-network"):operation.equals("verify")?Set.of("bundle","out"):Set.of();
            if(expected.isEmpty()||!expected.containsAll(options.keySet())||!options.containsKey("out")||!options.containsKey(operation.equals("run")?"request":"bundle"))throw new IllegalArgumentException("Use the documented operation and arguments");
            Path input=Path.of(options.get(operation.equals("run")?"request":"bundle")).toAbsolutePath().normalize();
            out=Path.of(options.get("out")).toAbsolutePath().normalize();
            if(out.startsWith(input)||input.startsWith(out))throw new IllegalArgumentException("Output overlaps protected input");
            Path parent=out.getParent();Files.createDirectories(parent);
            if(!parent.toRealPath().equals(parent))throw new IllegalArgumentException("Use a canonical unlinked output parent");
            Files.createDirectory(out);owned=true;write(out,"WARNINGS.md",ACTION+"\n");
            if(operation.equals("verify")){verify(input,out);System.out.println("PASS_MELODI_PRESTO_SAVED_TRANSPORT_AND_RESPONSE_READBACK_NOT_HISTORICAL_REEXECUTION");return 0;}
            if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)||Files.size(input)>MAX_REQUEST)throw new Unavailable("Supply a readable regular trusted request <=1MiB");
            byte[] original=Files.readAllBytes(input);write(out,"source-request.json",original);
            Request request=request(object(parse(original,1_000_000)));write(out,"request-payload.json",NextflowJson.parameters(request.payload()));
            if(!options.containsKey("allow-network"))throw new Unavailable("Networking is forbidden without --allow-network; no query was transmitted");
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(30,request.seconds())))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var http=HttpRequest.newBuilder(request.uri()).timeout(Duration.ofSeconds(request.seconds()))
                .header("Accept","application/json").header("Content-Type","application/json")
                .header("Accept-Encoding","identity").header("User-Agent","OmicsMatic-MELODI-Presto/1.0")
                .POST(HttpRequest.BodyPublishers.ofFile(out.resolve("request-payload.json"))).build();
            phase="HTTP_REQUEST";Instant began=Instant.now();
            HttpResponse<InputStream> response=client.send(http,HttpResponse.BodyHandlers.ofInputStream());received=true;phase="HTTP_RESPONSE_RECORDING";
            ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"melodi-response-deadline");t.setDaemon(true);return t;});
            AtomicBoolean timedOut=new AtomicBoolean();
            try(InputStream body=response.body()){
                long remaining=Math.max(1,request.seconds()*1000L-Duration.between(began,Instant.now()).toMillis());
                timer.schedule(()->cancelResponse(body,timedOut),remaining,TimeUnit.MILLISECONDS);
                record(out,"HTTP.json",Map.of("url",request.uri().toString(),"method","POST","status",response.statusCode(),
                    "headers",response.headers().map(),"request_utc",began.toString(),"response_utc",Instant.now().toString(),
                    "redirects_followed",false,"server_source_commit_established",false,"server_database_snapshot_established",false));
                try(OutputStream stream=Files.newOutputStream(out.resolve("response.bin"),CREATE_NEW)){
                    byte[] chunk=new byte[8192];long total=0;int n;
                    while((n=body.read(chunk))!=-1){
                        total+=n;
                        if(total>request.maximum())throw new IOException("Response exceeds declared maximum; retained partial response admits no complete result. Retry NEW paths with sufficient explicit capacity");
                        stream.write(chunk,0,n);
                        if(Duration.between(began,Instant.now()).toSeconds()>request.seconds())throw new Unavailable("Complete response body exceeded declared wall time; partial response retained");
                    }
                }
                if(timedOut.get())throw new Unavailable("Complete response body reached declared time limit; partial records retained");
            }catch(IOException e){if(timedOut.get())throw new Unavailable("Complete response body reached declared time limit; partial records retained");throw e;}
            finally{timer.shutdownNow();}
            if(!Arrays.equals(Files.readAllBytes(input),original))throw new IOException("Original request changed during transmission");
            int code=response.statusCode();
            if(code==401||code==403||code==429||code>=500)throw new Unavailable("Upstream service/access requirement unavailable: HTTP"+code+"; complete response retained");
            if(code!=200)throw new IOException("Upstream HTTP"+code+"; read complete response.bin and resolve the named server/request error");
            String type=response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if(!type.startsWith("application/json"))throw new IOException("Upstream Content-Type is not application/json: "+type);
            phase="RESPONSE_VALIDATION";byte[] raw=Files.readAllBytes(out.resolve("response.bin"));long rows=validate(request.operation(),parse(raw,2_000_000));
            phase="COMPLETE_RECORDING";record(out,"runtime.json",Map.of("java_runtime",System.getProperty("java.runtime.version"),"os",System.getProperty("os.name"),"implementation",implementation()));
            record(out,"response-validation.json",Map.of("operation",request.operation(),"complete_records",rows,
                "complete_response_sha256",sha(raw),"all_original_response_bytes_retained",true,"fields_or_numbers_rewritten",false,
                "source_or_database_snapshot_inferred",false,"local_scientific_analysis_performed",false,"paper_reproduced",false));
            manifest(out);
            complete(out,Map.of("status","PASS_RECORDED_LIVE_MELODI_PRESTO_COMPLETE_RESPONSE", "records",rows,
                "manifest_sha256",sha(Files.readAllBytes(out.resolve("MANIFEST.json"))),"result_admitted",true,"server_snapshot","live-unversioned",
                "biological_accuracy_established",false,"paper_reproduced",false,"whole_goal_complete",false));
            System.out.println("PASS_RECORDED_LIVE_MELODI_PRESTO_COMPLETE_RESPONSE records="+rows+"\nWARNING: "+ACTION);return 0;
        }catch(Exception e){
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            int code=e instanceof Unavailable||(!received&&e instanceof IOException&&!owned&&!(e instanceof FileAlreadyExistsException))?3:2;
            if(!received&&phase.equals("HTTP_REQUEST")&&e instanceof IOException)code=3;
            if(!received&&(e instanceof HttpTimeoutException||e instanceof ConnectException||e instanceof UnknownHostException||e instanceof javax.net.ssl.SSLException))code=3;
            String label=code==3?"SKIP3_MELODI_PRESTO_PREREQUISITE_UNAVAILABLE":"FAIL2_MELODI_PRESTO_RESULT_UNADMITTED";
            System.err.println(label+": "+e+"\n"+ACTION);
            if(owned)try{
                if(Files.exists(out.resolve("COMPLETED.json")))Files.move(out.resolve("COMPLETED.json"),out.resolve("COMPLETED-UNADMITTED.json"));
                record(out,"FAILURE.json",Map.of("status",label,"phase",phase,"cause",e.toString(),"server_response_headers_received",received,
                    "complete_result_admitted",false,"required_action",ACTION));
                Files.writeString(out.resolve("WARNINGS.md"),label+"\n"+e+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8);
            }catch(Exception recording){System.err.println("FAIL2_RESULT_RECORDING_UNAVAILABLE "+recording);code=2;}
            return code;
        }
    }

    static Request request(Map<String,Object> value)throws IOException{
        Set<String> allowed=new HashSet<>(BASE);allowed.addAll(OPTIONAL);
        if(!value.keySet().containsAll(BASE)||!allowed.containsAll(value.keySet())
            ||integer(value.get("schema"))!=1||!"live-unversioned".equals(value.get("server_snapshot")))throw new IOException("Explicit schema1 and server_snapshot live-unversioned required; historical snapshots are unavailable");
        String operation=String.valueOf(value.get("operation"));Map<String,Object> payload=object(value.get("payload"));
        switch(operation){
            case "overlap"->{if(!payload.keySet().equals(Set.of("x","y")))throw new IOException("overlap requires exact x/y lists");terms(payload.get("x"));terms(payload.get("y"));}
            case "enrich"->{if(!payload.keySet().equals(Set.of("query")))throw new IOException("enrich requires query");term(payload.get("query"));}
            case "sentence"->{if(!payload.keySet().equals(Set.of("pmid")))throw new IOException("sentence requires pmid");if(!(payload.get("pmid") instanceof String text)||!text.matches("[0-9]+"))throw new IOException("Supply an explicit PubMed identifier string");}
            default->throw new IOException("Use overlap, enrich or sentence; unrelated Presto packages are not supported");
        }
        String endpoint=String.valueOf(value.getOrDefault("endpoint",DEFAULT_ENDPOINT));URI base=URI.create(endpoint);
        if(base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!Set.of("","/").contains(base.getPath()))throw new IOException("Endpoint must be an explicit credential-free service origin");
        boolean local=Set.of("127.0.0.1","localhost","[::1]").contains(base.getHost());
        if(!"https".equals(base.getScheme())&&!("http".equals(base.getScheme())&&local))throw new IOException("HTTPS required except explicit loopback test services");
        int seconds=value.containsKey("timeout_seconds")?integer(value.get("timeout_seconds")):300;
        int maximum=value.containsKey("max_response_bytes")?integer(value.get("max_response_bytes")):64*1024*1024;
        if(seconds<1||seconds>3600||maximum<1||maximum>256*1024*1024)throw new IOException("Use timeout1..3600seconds and response capacity1..268435456bytes");
        return new Request(operation,payload,base.resolve("/api/"+operation+"/"),seconds,maximum);
    }
    static long validate(String operation,Object value)throws IOException{
        if(operation.equals("enrich")){
            if(!(value instanceof List<?> list))throw new IOException("Complete original enrich response must be a list");
            for(Object row:list)if(!object(row).keySet().containsAll(ENRICH))throw new IOException("Original enrich record population/fields incomplete");return list.size();
        }
        Map<String,Object> result=object(value);
        if(result.keySet().stream().anyMatch(k->k.equalsIgnoreCase("error")))throw new IOException("Upstream returned an Error record; no success is inferred: "+NextflowJson.parameters(result.get(result.keySet().stream().filter(k->k.equalsIgnoreCase("error")).findFirst().orElseThrow())));
        if(!(result.get("data") instanceof List<?> list)||!result.containsKey("count")||count(result.get("count"),operation)<0||count(result.get("count"),operation)!=list.size())throw new IOException("Complete count/data population differs or is unavailable; a server-capped or lower-bound population is not admitted");
        for(Object row:list){Map<String,Object> r=object(row);if(operation.equals("overlap"))for(String suffix:List.of("_x","_y")){
            for(String field:ENRICH)if(!field.equals("query")&&!r.containsKey(field+suffix))throw new IOException("Incomplete original overlap field: "+field+suffix);
            if(!r.containsKey("set"+suffix))throw new IOException("Missing original query-set field");
        }}
        if(operation.equals("sentence")&&!result.containsKey("title"))throw new IOException("Original sentence title record absent");return list.size();
    }
    private static void verify(Path bundle,Path out)throws Exception{
        if(!Files.isDirectory(bundle,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Supply the complete saved response bundle");
        Map<String,Object> done=object(parse(Files.readAllBytes(bundle.resolve("COMPLETED.json")),1000));
        byte[] manifest=Files.readAllBytes(bundle.resolve("MANIFEST.json"));
        if(!"PASS_RECORDED_LIVE_MELODI_PRESTO_COMPLETE_RESPONSE".equals(done.get("status"))||!sha(manifest).equals(done.get("manifest_sha256")))throw new IOException("Complete response admission/manifest differs");
        Map<String,Object> pins=object(parse(manifest,10000));
        if(!pins.keySet().equals(COMPLETE_FILES))throw new IOException("Complete required record family differs");
        Set<String> names=new HashSet<>(pins.keySet());names.addAll(Set.of("MANIFEST.json","COMPLETED.json"));
        try(var stream=Files.list(bundle)){var files=stream.toList();if(files.size()!=names.size())throw new IOException("Extra or missing saved files");for(Path p:files)if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!names.contains(p.getFileName().toString()))throw new IOException("Unexpected saved entry");}
        for(var row:pins.entrySet()){
            if(row.getKey().contains("/")||row.getKey().contains("\\")||row.getKey().equals(".."))throw new IOException("Unsafe manifest member");
            Map<String,Object> id=object(row.getValue());Path p=bundle.resolve(row.getKey());
            if(Files.size(p)!=Long.parseLong(numberText(id.get("bytes")))||!sha(Files.readAllBytes(p)).equals(id.get("sha256")))throw new IOException("Saved original bytes differ: "+row.getKey());
        }
        Request request=request(object(parse(Files.readAllBytes(bundle.resolve("source-request.json")),1000000)));
        if(!Arrays.equals(Files.readAllBytes(bundle.resolve("request-payload.json")),NextflowJson.parameters(request.payload()).getBytes(StandardCharsets.UTF_8)))throw new IOException("Complete transmitted query differs");
        Map<String,Object> http=object(parse(Files.readAllBytes(bundle.resolve("HTTP.json")),100000));
        if(integer(http.get("status"))!=200||!request.uri().toString().equals(http.get("url")))throw new IOException("Successful recorded HTTP status/URL differs");
        long rows=validate(request.operation(),parse(Files.readAllBytes(bundle.resolve("response.bin")),2_000_000));
        if(rows!=integer(done.get("records")))throw new IOException("Saved response record population differs");
        Map<String,Object> validation=object(parse(Files.readAllBytes(bundle.resolve("response-validation.json")),10000));
        if(rows!=integer(validation.get("complete_records"))||!request.operation().equals(validation.get("operation"))||!sha(Files.readAllBytes(bundle.resolve("response.bin"))).equals(validation.get("complete_response_sha256"))
            ||!Boolean.TRUE.equals(validation.get("all_original_response_bytes_retained"))||!Boolean.FALSE.equals(validation.get("fields_or_numbers_rewritten"))
            ||!Files.readString(bundle.resolve("WARNINGS.md")).equals(ACTION+"\n"))throw new IOException("Complete saved validation or warning semantics differ");
        Map<String,Object> runtime=object(parse(Files.readAllBytes(bundle.resolve("runtime.json")),10000));
        if(!object(runtime.get("implementation")).equals(implementation()))throw new IOException("Exact recorded client bytecode differs from this verifier");
        complete(out,Map.of("status","PASS_MELODI_PRESTO_SAVED_TRANSPORT_AND_RESPONSE_READBACK_NOT_HISTORICAL_REEXECUTION", "complete_records",rows,
            "source_bundle_manifest_sha256",sha(manifest),"fresh_service_query",false,"historical_snapshot_recovered",false,"cryptographic_authorship_established",false));
    }
    private static void terms(Object value)throws IOException{if(!(value instanceof List<?> list)||list.isEmpty()||list.size()>10000)throw new IOException("Supply1..10000 explicit query terms");for(Object term:list)term(term);}
    private static int count(Object value,String operation)throws IOException{
        if(operation.equals("sentence")&&value instanceof Map<?,?>){
            Map<String,Object> metadata=object(value);
            if(!metadata.containsKey("value")||!"eq".equals(metadata.get("relation")))throw new IOException("Original sentence count is not exact; lower-bound or unknown Elasticsearch totals cannot establish a complete result");
            return integer(metadata.get("value"));
        }
        return integer(value);
    }
    private static void term(Object value)throws IOException{if(!(value instanceof String s)||s.strip().codePointCount(0,s.strip().length())<2||s.length()>10000)throw new IOException("Each explicit query needs2..10000characters; no identifier repair is performed");}
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object v)throws IOException{if(!(v instanceof Map<?,?>))throw new IOException("Expected a JSON object");return (Map<String,Object>)v;}
    private static String numberText(Object v)throws IOException{if(!(v instanceof NextflowParameters.NumberToken n))throw new IOException("Expected an original JSON number");return n.text();}
    private static int integer(Object v)throws IOException{try{return new BigDecimal(numberText(v)).intValueExact();}catch(ArithmeticException e){throw new IOException("Expected a bounded integer",e);}}
    private static Object parse(byte[] raw,int nodes)throws IOException{return NextflowParameters.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(),256*1024*1024,nodes);}
    private static Map<String,String> options(String[] args){Map<String,String> result=new LinkedHashMap<>();for(int i=1;i<args.length;i++){
        if(!args[i].startsWith("--"))throw new IllegalArgumentException("Expected --option");String key=args[i].substring(2),value="true";
        if(!key.equals("allow-network")){if(++i>=args.length)throw new IllegalArgumentException("Missing --"+key+" value");value=args[i];}
        if(result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Repeated --"+key);
    }return result;}
    private static void write(Path out,String name,String value)throws IOException{write(out,name,value.getBytes(StandardCharsets.UTF_8));}
    private static void write(Path out,String name,byte[] raw)throws IOException{Files.write(out.resolve(name),raw,CREATE_NEW);}
    private static void record(Path out,String name,Object value)throws IOException{write(out,name,NextflowJson.parameters(value));}
    private static void complete(Path out,Object value)throws IOException{record(out,"COMPLETED.pending.json",value);Files.move(out.resolve("COMPLETED.pending.json"),out.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);}
    private static void cancelResponse(InputStream body,AtomicBoolean timedOut){timedOut.set(true);try{body.close();}catch(IOException ignored){/* The recording path reports the timeout and retains partial bytes. */}}
    private static String sha(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static Map<String,String> implementation()throws IOException{
        Map<String,String> result=new TreeMap<>();Set<Class<?>> seen=new HashSet<>();Deque<Class<?>> queue=new ArrayDeque<>(List.of(MelodiPrestoCli.class,NextflowParameters.class,NextflowJson.class));
        while(!queue.isEmpty()){
            Class<?> c=queue.remove();if(!seen.add(c))continue;Collections.addAll(queue,c.getDeclaredClasses());
            String name=c.getName().replace('.','/')+".class";try(InputStream stream=c.getClassLoader().getResourceAsStream(name)){if(stream==null)throw new IOException("Complete implementation bytecode unavailable: "+name);result.put(name,sha(stream.readAllBytes()));}
        }return result;
    }
    private static void manifest(Path out)throws IOException{
        Map<String,Object> result=new TreeMap<>();try(var list=Files.list(out)){for(Path p:list.toList())if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){byte[] raw=Files.readAllBytes(p);result.put(p.getFileName().toString(),Map.of("bytes",raw.length,"sha256",sha(raw)));}}
        record(out,"MANIFEST.json",result);
    }
}
