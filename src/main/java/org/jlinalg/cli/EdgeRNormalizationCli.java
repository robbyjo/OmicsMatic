/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.jlinalg.differential.EdgeRNormalization;
import org.jlinalg.differential.EdgeRNormalization.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed native count normalization, with saved-input arithmetic verification. */
public final class EdgeRNormalizationCli {
    private EdgeRNormalizationCli() { }
    static final String SCOPE="Native Java edgeR matrix normalization/CPM/RPKM only; no dispersion fitting, differential tests, offsets, S4 objects or automatic upstream fallback. Source-built standalone component; no whole-tool or paper reproduction.";
    static final String ACTION="SKIP3: supply the named permitted exact input/edition or keep dependent analysis skipped. FAIL2: NO COMPLETE RESULT; preserve inputs, partials and diagnostics, resolve the named cause and retry a NEW output. Never repair identifiers or non-finite values silently.";
    private static final Map<String,Edition> EDITIONS=Map.of("3.32.1",Edition.EDGER_3_32_1,"3.36.0",Edition.EDGER_3_36_0,"3.40.2",Edition.EDGER_3_40_2,"3.42.4",Edition.EDGER_3_42_4,"4.10.7",Edition.EDGER_4_10_7);
    private static final List<String> INPUTS=List.of("counts","libraries","gene_lengths");
    private static final List<String> CLASSES=List.of("org/jlinalg/differential/EdgeRNormalization","org/jlinalg/differential/EdgeRNormalization$Edition","org/jlinalg/differential/EdgeRNormalization$Method","org/jlinalg/differential/EdgeRNormalization$Options","org/jlinalg/differential/EdgeRNormalization$Result","org/jlinalg/differential/EdgeRLog2","org/jlinalg/cli/EdgeRNormalizationCli","org/jlinalg/cli/EdgeRNormalizationCli$1","org/jlinalg/cli/EdgeRNormalizationCli$Unavailable","org/jlinalg/cli/EdgeRNormalizationCli$Computed","org/jlinalg/cli/SimpleJson","org/jlinalg/cli/SeuratDifferentialContracts","org/jlinalg/cli/RsemJson","org/jlinalg/cli/RegenieRuntime");
    private static final class Unavailable extends IOException { private static final long serialVersionUID=1L; Unavailable(String message){super(message);} }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){out.println("EdgeRNormalizationCli --request request.json | --verify capture\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]).toAbsolutePath().normalize());out.println("Verified complete native edgeR capture by recomputing its saved inputs. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Use --help, --request JSON, or --verify CAPTURE");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bounded(source,2_000_000);var raw=object(SimpleJson.parse(utf8(original)),"request");
            String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){control(raw,false);verify(resolve(source.getParent(),text(raw,"analysis")));out.println("Verified native edgeR capture from saved inputs. "+SCOPE);return 0;}
            if(mode.equals("REPLAY")) {
                control(raw,true);Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior);Path target=resolve(source.getParent(),text(raw,"out"));outsideParent(prior,target);
                var request=read(prior.resolve("request.json"));for(String key:INPUTS)if(request.containsKey(key))request.put(key,resolve(prior,text(request,key)).toString());
                request.put("out",target.toString());return execute(source.getParent(),request,original,Map.of("analysis",prior.toString(),"seal_sha256",sha(Files.readAllBytes(prior.resolve("capture-files.json")))),out,err);
            }
            return execute(source.getParent(),raw,original,Map.of(),out,err);
        }catch(Exception error){int code=failure(error);err.println((code==3?"SKIP3: ":"FAIL2: NO COMPLETE RESULT: ")+error.getMessage()+"\n"+ACTION);return code;}
    }
    private static void control(Map<String,Object> raw,boolean replay)throws IOException {
        keys(raw,replay?Set.of("schema_version","mode","analysis","out"):Set.of("schema_version","mode","analysis"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");text(raw,"analysis");if(replay)text(raw,"out");
    }
    private static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","edition","out","counts","libraries","gene_lengths","method","reference_sample","logratio_trim","sum_trim","weighting","a_cutoff","quantile","transform","normalized_library_sizes","prior_count","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_samples","maximum_entries","maximum_work","preparation_note"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version 1 and mode RUN");
        if(!EDITIONS.containsKey(text(raw,"edition")))throw new Unavailable("Unsupported exact native edgeR edition "+raw.get("edition")+"; supported: "+new TreeSet<>(EDITIONS.keySet()));
        var r=new LinkedHashMap<String,Object>(raw);text(r,"out");text(r,"counts");for(String key:INPUTS)if(r.containsKey(key))text(r,key);
        r.put("method",choice(r,"method","",Set.of("TMM","TMMwsp","RLE","upperquartile","none")));
        r.put("transform",choice(r,"transform","CPM",Set.of("NONE","CPM","LOG_CPM","RPKM","LOG_RPKM")));
        if(text(r,"transform").contains("RPKM")!=r.containsKey("gene_lengths"))throw new IOException("Supply gene_lengths exactly for an RPKM transform");
        if(r.containsKey("reference_sample"))literal(text(r,"reference_sample"));if(r.containsKey("preparation_note"))literal(text(r,"preparation_note"));
        r.put("logratio_trim",real(r,"logratio_trim",.3,0,Math.nextDown(.5),true));r.put("sum_trim",real(r,"sum_trim",.05,0,Math.nextDown(.5),true));
        r.put("weighting",bool(r,"weighting",true));r.put("a_cutoff",real(r,"a_cutoff",-1e10,-Double.MAX_VALUE,Double.MAX_VALUE,true));r.put("quantile",real(r,"quantile",.75,0,1,true));
        r.put("normalized_library_sizes",bool(r,"normalized_library_sizes",true));r.put("prior_count",real(r,"prior_count",2,0,1e10,true));
        r.put("maximum_input_bytes",integer(r,"maximum_input_bytes",32_000_000,1,256_000_000));r.put("maximum_output_bytes",integer(r,"maximum_output_bytes",128_000_000,1,200_000_000));
        r.put("maximum_features",integer(r,"maximum_features",100_000,1,2_000_000));r.put("maximum_samples",integer(r,"maximum_samples",2000,1,100_000));
        r.put("maximum_entries",integer(r,"maximum_entries",2_000_000,1,20_000_000));r.put("maximum_work",integer(r,"maximum_work",200_000_000,1,2_000_000_000));return r;
    }
    private static int execute(Path base,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws Exception {
        var request=validate(raw);long inputCap=((Number)request.get("maximum_input_bytes")).longValue();Map<String,byte[]> inputs=new LinkedHashMap<>();long total=0;
        for(String key:INPUTS)if(request.containsKey(key)){byte[] bytes=bounded(resolve(base,text(request,key)),inputCap);total+=bytes.length;if(total>inputCap)throw new IOException("Combined inputs exceed maximum_input_bytes");inputs.put(key,bytes);}
        Path root=resolve(base,text(request,"out"));if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output; choose a NEW directory");
        Files.createDirectories(root.getParent());Files.createDirectory(root);
        try {
            Files.write(root.resolve("original-request.json"),original,StandardOpenOption.CREATE_NEW);Files.createDirectory(root.resolve("inputs"));
            for(var e:inputs.entrySet()){String path="inputs/"+e.getKey()+".tsv";Files.write(root.resolve(path),e.getValue(),StandardOpenOption.CREATE_NEW);request.put(e.getKey(),path);}
            request.put("out",".");Files.write(root.resolve("request.json"),RsemJson.bytes(request),StandardOpenOption.CREATE_NEW);
            Files.write(root.resolve("producer.json"),RsemJson.bytes(producer()),StandardOpenOption.CREATE_NEW);
            if(!replay.isEmpty())Files.write(root.resolve("replay-source.json"),RsemJson.bytes(replay),StandardOpenOption.CREATE_NEW);
            Computed result=compute(root,request);for(var e:result.files.entrySet())Files.write(root.resolve(e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);
            seal(root);String message=result.complete?"Completed native edgeR normalization. ":"FAIL2: NO COMPLETE RESULT: original computation returned non-finite output. ";
            (result.complete?out:err).println(message+SCOPE+"\n"+Files.readString(root.resolve("WARNINGS.md")));return result.complete?0:2;
        }catch(Exception error) {
            Files.write(root.resolve("analysis-status.json"),RsemJson.bytes(Map.of("status",failure(error)==3?"SKIP3":"FAIL2","complete_result",false,"error",error.toString(),"required_action",ACTION)));
            Files.writeString(root.resolve("WARNINGS.md"),"# NO COMPLETE RESULT\n\n"+error+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8);seal(root);throw error;
        }
    }
    private record Computed(Map<String,byte[]> files,boolean complete) { }
    private static Computed compute(Path root,Map<String,Object> r)throws IOException {
        var rows=table(root.resolve(text(r,"counts")),((Number)r.get("maximum_input_bytes")).longValue());String[] header=rows.get(0);
        if(header.length<2||!header[0].equals("feature_id"))throw new IOException("Counts header must start feature_id followed by literal sample IDs");
        int samples=header.length-1,features=rows.size()-1;var sampleIds=Arrays.asList(header).subList(1,header.length);unique(sampleIds);
        if(samples>((Number)r.get("maximum_samples")).longValue()||features>((Number)r.get("maximum_features")).longValue()
                ||(long)samples*features>((Number)r.get("maximum_entries")).longValue())throw new IOException("Declared feature/sample/entry capacity exceeded");
        double work=(double)samples*features*(Math.log(Math.max(2,features))/Math.log(2)+4);
        if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("Declared normalization work capacity exceeded");
        List<String> ids=new ArrayList<>();double[][] x=new double[features][samples];
        for(int i=0;i<features;i++){String[] row=rows.get(i+1);if(row.length!=header.length)throw new IOException("Counts row width differs at row "+(i+2));identity(row[0]);ids.add(row[0]);for(int j=0;j<samples;j++)x[i][j]=numeric(row[j+1],false);}
        unique(ids);double[] libraries=r.containsKey("libraries")?vector(root.resolve(text(r,"libraries")),"sample_id","library_size",sampleIds,r):null;
        if(features==0&&libraries==null)throw new IOException("Empty feature matrix requires explicit library sizes to retain sample dimension");
        double[] lengths=r.containsKey("gene_lengths")?vector(root.resolve(text(r,"gene_lengths")),"feature_id","length_bp",ids,r):null;
        Integer reference=null;if(r.containsKey("reference_sample")){reference=sampleIds.indexOf(text(r,"reference_sample"));if(reference<0)throw new IOException("reference_sample is not a literal count header identity");}
        Method method=switch(text(r,"method")){case "TMM"->Method.TMM;case "TMMwsp"->Method.TMMWSP;case "RLE"->Method.RLE;case "upperquartile"->Method.UPPER_QUARTILE;default->Method.NONE;};
        var options=new Options(method,reference,((Number)r.get("logratio_trim")).doubleValue(),((Number)r.get("sum_trim")).doubleValue(),(Boolean)r.get("weighting"),((Number)r.get("a_cutoff")).doubleValue(),((Number)r.get("quantile")).doubleValue());
        Edition edition=EDITIONS.get(text(r,"edition"));var normalized=EdgeRNormalization.normalize(x,libraries,edition,options);var warnings=new ArrayList<>(normalized.warnings());
        boolean complete=normalized.finite();Map<String,byte[]> files=new LinkedHashMap<>();double[] libs=normalized.librarySizes(),factors=normalized.factors(),effective=normalized.effectiveLibrarySizes();
        StringBuilder f=new StringBuilder("sample_id\tlibrary_size\tnorm_factor\teffective_library_size\n");
        for(int j=0;j<samples;j++)f.append(sampleIds.get(j)).append('\t').append(libs[j]).append('\t').append(factors[j]).append('\t').append(effective[j]).append('\n');
        files.put("normalization.tsv",f.toString().getBytes(StandardCharsets.UTF_8));String transform=text(r,"transform");
        if(complete&&!transform.equals("NONE")) {
            double[] used=(Boolean)r.get("normalized_library_sizes")?effective:libs;boolean log=transform.startsWith("LOG_");double prior=((Number)r.get("prior_count")).doubleValue();
            double[][] values;
            try { values=transform.contains("RPKM")?EdgeRNormalization.rpkm(x,used,lengths,edition,log,prior):EdgeRNormalization.cpm(x,used,edition,log,prior); }
            catch(IllegalArgumentException error){values=null;complete=false;warnings.add("NO COMPLETE RESULT: dependent transform refused: "+error.getMessage());}
            if(values!=null) {
            StringBuilder matrix=new StringBuilder(String.join("\t",header)).append('\n');
            for(int i=0;i<features;i++){matrix.append(ids.get(i));for(int j=0;j<samples;j++){matrix.append('\t').append(values[i][j]);if(!Double.isFinite(values[i][j]))complete=false;}matrix.append('\n');}
            files.put("expression.tsv",matrix.toString().getBytes(StandardCharsets.UTF_8));
            if(!complete)warnings.add("NO COMPLETE RESULT: non-finite CPM/RPKM values are retained literally; zero counts with a zero log prior remain NaN");
            }
        }else if(!complete&&!transform.equals("NONE"))warnings.add("Dependent CPM/RPKM calculation skipped because normalization is undefined; no substitute factors were used");
        Map<String,Object> status=new LinkedHashMap<>();status.put("status",complete?"PASS_NATIVE_NORMALIZATION":"FAIL2");status.put("complete_result",complete);status.put("engine","native-Java");status.put("edition",r.get("edition"));status.put("features",features);status.put("samples",samples);status.put("reference_sample",normalized.referenceColumn()==null?null:sampleIds.get(normalized.referenceColumn()));status.put("whole_tool_qualified",false);status.put("scope",SCOPE);status.put("warnings",warnings);status.put("required_action",ACTION);
        files.put("analysis-status.json",RsemJson.bytes(status));files.put("WARNINGS.md",("# Native edgeR warnings and required actions\n\n"+SCOPE+"\n\n"+String.join("\n\n",warnings)+"\n\n"+ACTION+"\n").getBytes(StandardCharsets.UTF_8));
        long size=files.values().stream().mapToLong(a->a.length).sum();if(size>((Number)r.get("maximum_output_bytes")).longValue())throw new IOException("Declared output capacity exceeded; no complete result");return new Computed(files,complete);
    }
    private static double[] vector(Path path,String first,String second,List<String> expected,Map<String,Object> r)throws IOException {
        var rows=table(path,((Number)r.get("maximum_input_bytes")).longValue());if(!Arrays.equals(rows.get(0),new String[]{first,second})||rows.size()!=expected.size()+1)throw new IOException("Require exact "+first+"/"+second+" table dimensions");
        double[] result=new double[expected.size()];for(int i=0;i<result.length;i++){String[] row=rows.get(i+1);if(row.length!=2||!row[0].equals(expected.get(i)))throw new IOException("Literal identity/order mismatch in "+path.getFileName()+"; no mapping or recycling");result[i]=numeric(row[1],true);}return result;
    }
    private static double numeric(String token,boolean positive)throws IOException {
        if(!token.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require literal finite nonnegative decimal data, without whitespace repair");
        double x=Double.parseDouble(token);if(!Double.isFinite(x)||(positive?x<=0:x<0))throw new IOException("Data outside finite "+(positive?"positive":"nonnegative")+" range");return x;
    }
    private static List<String[]> table(Path path,long cap)throws IOException {
        String text=utf8(bounded(path,cap));if(text.indexOf('\r')>=0)text=text.replace("\r\n","\n");
        if(text.indexOf('\r')>=0||text.indexOf('\u0000')>=0)throw new IOException("Invalid tabular control character");String[] lines=text.split("\n",-1);int n=lines.length;if(n>0&&lines[n-1].isEmpty())n--;
        if(n==0)throw new IOException("Require a tabular header");var result=new ArrayList<String[]>();for(int i=0;i<n;i++)result.add(lines[i].split("\t",-1));return result;
    }
    private static void literal(String id)throws IOException {identity(id);if(!StandardCharsets.UTF_8.newEncoder().canEncode(id))throw new IOException("Identity contains invalid Unicode; no replacement is permitted");}
    private static void unique(List<String> ids)throws IOException {var seen=new HashSet<String>();for(String id:ids){literal(id);if(!seen.add(id))throw new IOException("Repeated literal identity: "+id);}}
    private static String utf8(byte[] raw)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    private static byte[] bounded(Path path,long cap)throws IOException {
        if(!Files.exists(path))throw new NoSuchFileException(path.toString());if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Require a bounded regular file: "+path);
        try(var in=Files.newInputStream(path)){byte[] raw=in.readNBytes((int)Math.min(Integer.MAX_VALUE,cap+1));if(raw.length>cap)throw new IOException("Input grew beyond declared capacity");return raw;}
    }
    static String sha(byte[] raw)throws IOException {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}}
    private static Map<String,Object> producer()throws IOException {
        var pins=new TreeMap<String,Object>();for(String name:CLASSES)try(var in=EdgeRNormalizationCli.class.getResourceAsStream("/"+name+".class")){if(in==null)throw new IOException("Required native producer class missing: "+name);byte[] raw=in.readAllBytes();pins.put(name,Map.of("bytes",raw.length,"sha256",sha(raw)));}return pins;
    }
    private static Map<String,Object> inventory(Path root)throws IOException {
        var files=new TreeMap<String,Object>();Path real=root.toRealPath();long[] total={0};int[] directories={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory,java.nio.file.attribute.BasicFileAttributes a)throws IOException {
                if(++directories[0]>8||Files.isSymbolicLink(directory)||!directory.toRealPath().startsWith(real))throw new IOException("Capture directory is linked or exceeds capacity");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path p,java.nio.file.attribute.BasicFileAttributes a)throws IOException {
                if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Capture contains a link or special file");String n=root.relativize(p).toString().replace('\\','/');if(n.equals("capture-files.json"))return FileVisitResult.CONTINUE;
                if(files.size()>=32||(total[0]+=Files.size(p))>512_000_000)throw new IOException("Capture inventory capacity exceeded");
                byte[] bytes=bounded(p,256_000_000);files.put(n,Map.of("bytes",bytes.length,"sha256",sha(bytes)));return FileVisitResult.CONTINUE;
            }
        });return files;
    }
    static void seal(Path root)throws IOException {Files.write(root.resolve("capture-files.json"),RsemJson.bytes(inventory(root)));}
    static void verify(Path root)throws IOException {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require original capture directory");var contents=inventory(root);if(!same(read(root.resolve("capture-files.json"),32_000_000),contents))throw new IOException("Capture inventory changed");
        if(!same(read(root.resolve("producer.json")),producer()))throw new IOException("Exact original native producer differs; preserve this capture and use its qualified source-built JAR");
        var saved=read(root.resolve("request.json"));var request=validate(saved);if(!same(saved,request))throw new IOException("Saved request is not the resolved closed contract");
        if(!text(request,"out").equals("."))throw new IOException("Saved output must be capture-relative");
        for(String key:INPUTS)if(request.containsKey(key)&&!text(request,key).equals("inputs/"+key+".tsv"))throw new IOException("Saved input must be inside its capture");
        var provenance=object(SimpleJson.parse(utf8(bounded(root.resolve("original-request.json"),2_000_000))),"original request");
        if(text(provenance,"mode").equals("RUN")) {
            var a=validate(provenance);var b=new LinkedHashMap<String,Object>(request);for(String key:INPUTS){a.remove(key);b.remove(key);}a.remove("out");b.remove("out");
            if(!same(a,b)||Files.exists(root.resolve("replay-source.json")))throw new IOException("Original and resolved scientific request fields differ");
        }else {control(provenance,true);if(!text(provenance,"mode").equals("REPLAY")||!Files.isRegularFile(root.resolve("replay-source.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Replay provenance is incomplete");}
        Computed expected=compute(root,request);for(var e:expected.files.entrySet())if(!Arrays.equals(bounded(root.resolve(e.getKey()),256_000_000),e.getValue()))throw new IOException("Saved native arithmetic or warnings differ: "+e.getKey());
        var admitted=new HashSet<String>(List.of("original-request.json","request.json","producer.json"));if(provenance.get("mode").equals("REPLAY"))admitted.add("replay-source.json");for(String key:INPUTS)if(request.containsKey(key))admitted.add(text(request,key));admitted.addAll(expected.files.keySet());if(!contents.keySet().equals(admitted))throw new IOException("Capture contains an unreferenced or missing payload");
        if(!expected.complete)throw new IOException("Capture has NO COMPLETE RESULT; original native outcome was non-finite");
    }
    private static void outsideParent(Path prior,Path destination)throws IOException {
        Path original=prior.toRealPath(),existing=destination.toAbsolutePath().normalize();var missing=new ArrayDeque<Path>();
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)){if(existing.getParent()==null)throw new IOException("Cannot resolve replay output");missing.addFirst(existing.getFileName());existing=existing.getParent();}
        Path actual=existing.toRealPath();for(Path part:missing)actual=actual.resolve(part);if(actual.normalize().startsWith(original))throw new IOException("REPLAY output must be outside its original analysis; choose a NEW sibling directory");
    }
    private static int failure(Exception error){return error instanceof NoSuchFileException||error instanceof AccessDeniedException||error instanceof Unavailable?3:2;}
}
