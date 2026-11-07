/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Closed requests, source profiles and bounded complete file identities for HTSeq counting. */
final class HtseqFileContract {
    private HtseqFileContract() { }
    static final String RESOURCE="/org/jlinalg/omics/htseq-file-count/";
    static final String ACTION="SKIP3 means a required permitted Linux Java/Python/HTSeq/dependency/helper is missing or access is forbidden: skip dependent analysis. Where installation is allowed, provision the exact declared private profile and retry into NEW paths; otherwise retain the skip. FAIL2 means invalid/changed input, unsupported edition/options, process/table/integrity/capacity failure or cancellation: retain WARNINGS.md, status, INCOMPLETE and process logs, correct the named cause and retry into NEW paths. No partial result is admitted, no analysis installs software, and no version or scientific method is silently substituted.";
    static final Runnable CHECK=()->{if(Thread.currentThread().isInterrupted())throw new IllegalStateException("HTSeq operation interrupted; no result admitted");};
    static final Set<String> COUNT=Set.of("schema_version","operation","version","runtime_home","alignment","annotation","format","order","stranded","overlap","nonunique","secondary","supplementary","minimum_mapq","feature_type","id_attribute","genome_label","annotation_label","counting_unit","out","max_input_bytes","max_output_bytes","max_count_rows","timeout_seconds","memory_bytes");
    record Limits(long input,long output,int rows,long seconds,long memory) {
        Map<String,Object> document(){return Map.of("max_input_bytes",input,"max_output_bytes",output,"max_count_rows",rows,"timeout_seconds",seconds,"memory_bytes",memory);}
        RsemProcess.Limits process(){return new RsemProcess.Limits(seconds*1000,memory,seconds,output,256,Math.addExact(Math.multiplyExact(input,2),Math.addExact(output*4,67108864)),512,8,32,memory,25);}
    }
    record Request(Path source,Map<String,Object> values,String operation,Path out,Limits limits) {
        String text(String name)throws IOException{return HtseqFileContract.text(values.get(name));}
        Path path(String name)throws IOException{return source.getParent().resolve(text(name)).toAbsolutePath().normalize();}
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> object(Object value)throws IOException{
        if(!(value instanceof Map<?,?> map)||map.keySet().stream().anyMatch(k->!(k instanceof String)))throw new IOException("Expected HTSeq JSON object");
        return (Map<String,Object>)map;
    }
    static String text(Object value)throws IOException{
        if(!(value instanceof String s)||s.isBlank()||s.length()>32768||s.codePoints().anyMatch(c->c<32||c==127))throw new IOException("Expected nonempty HTSeq text without control characters");return s;
    }
    static long integer(Object value,long min,long max)throws IOException{
        if(!(value instanceof Long n)||n<min||n>max)throw new IOException("Expected exact HTSeq integer in ["+min+","+max+"]");return n;
    }
    static byte[] resource(String name)throws IOException{
        try(var in=HtseqFileContract.class.getResourceAsStream(RESOURCE+name)){
            if(in==null)throw new IOException("Missing packaged HTSeq resource: "+name);
            byte[] data=in.readNBytes(1048577);if(data.length>1048576)throw new IOException("HTSeq resource exceeds1MiB");return data;
        }
    }
    static Map<String,Object> profile(String version)throws IOException{
        var all=object(SimpleJson.parse(new String(resource("PROFILES.json"),StandardCharsets.UTF_8)));
        var profiles=object(all.get("profiles"));if(!profiles.containsKey(version))throw new IOException("Unsupported exact HTSeq edition: "+version);
        return object(profiles.get(version));
    }
    static Request request(Path source)throws IOException{
        source=source.toAbsolutePath().normalize();var raw=object(SimpleJson.parse(utf8(bytes(source,1048576))));
        integer(raw.get("schema_version"),1,1);String operation=text(raw.get("operation"));
        Set<String> keys=operation.equals("COUNT")?COUNT:operation.equals("VERIFY")?Set.of("schema_version","operation","run","out"):Set.of();
        if(keys.isEmpty())throw new IOException("HTSeq operation must be COUNT or VERIFY");
        for(String key:raw.keySet())if(!keys.contains(key))throw new IOException("Unknown HTSeq request field: "+key);
        Path out=source.getParent().resolve(text(raw.get("out"))).toAbsolutePath().normalize();
        Limits limits=new Limits(1073741824,67108864,2000000,3600,2147483648L);
        if(operation.equals("COUNT")){
            for(String key:COUNT)if(!key.startsWith("max_")&&!Set.of("timeout_seconds","memory_bytes").contains(key)&&!raw.containsKey(key))throw new IOException("Missing explicit HTSeq field: "+key);
            var p=profile(text(raw.get("version")));
            choice(raw,"format",Set.of("sam","bam"));choice(raw,"order",Set.of("name","pos"));choice(raw,"stranded",Set.of("yes","no","reverse"));
            choice(raw,"overlap",Set.of("union","intersection-strict","intersection-nonempty"));
            choice(raw,"secondary",Set.of("ignore","score"));choice(raw,"supplementary",Set.of("ignore","score"));
            choice(raw,"counting_unit",Set.of("original-auto"));
            if(!(p.get("nonunique") instanceof List<?> allowed)||!allowed.contains(text(raw.get("nonunique"))))throw new IOException("Unsupported nonunique policy for selected edition; RANDOM is not admitted");
            if(Boolean.TRUE.equals(p.get("flags_always_scored"))&&(!raw.get("secondary").equals("score")||!raw.get("supplementary").equals("score")))throw new IOException("HTSeq0.5.3/0.6.0 always score secondary/supplementary records; ignore cannot be substituted");
            if(Boolean.TRUE.equals(p.get("sam_name_only"))&&(!raw.get("format").equals("sam")||!raw.get("order").equals("name")))throw new IOException("HTSeq0.5.3 command supports name-ordered SAM only in this adapter");
            integer(raw.get("minimum_mapq"),0,255);
            for(String key:List.of("feature_type","id_attribute"))if(!text(raw.get(key)).matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}"))throw new IOException("Invalid explicit GTF field: "+key);
            for(String key:List.of("genome_label","annotation_label"))if(text(raw.get(key)).length()>1024)throw new IOException("HTSeq declared identity label exceeds1024characters");
            for(String key:List.of("runtime_home","alignment","annotation"))text(raw.get(key));
            limits=new Limits(number(raw,"max_input_bytes",1073741824,1,107374182400L),number(raw,"max_output_bytes",67108864,1024,268435456),
                (int)number(raw,"max_count_rows",2000000,5,2000000),number(raw,"timeout_seconds",3600,1,604800),number(raw,"memory_bytes",2147483648L,268435456,68719476736L));
        }else text(raw.get("run"));
        return new Request(source,Map.copyOf(raw),operation,out,limits);
    }
    private static void choice(Map<String,Object> raw,String name,Set<String> values)throws IOException{
        if(!values.contains(text(raw.get(name))))throw new IOException("Unsupported explicit HTSeq "+name+": "+raw.get(name));
    }
    private static long number(Map<String,Object> raw,String name,long fallback,long min,long max)throws IOException{return raw.containsKey(name)?integer(raw.get(name),min,max):fallback;}
    static String utf8(byte[] raw)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();}
    static byte[] bytes(Path path,long maximum)throws IOException{
        if(maximum<0||maximum>=Integer.MAX_VALUE)throw new IOException("Invalid bounded HTSeq metadata capacity");
        var wanted=pin(path,maximum);byte[] raw;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){raw=in.readNBytes((int)maximum+1);}
        if(!wanted.equals(bytePin(raw)))throw new IOException("HTSeq metadata changed/exceeded capacity while reading: "+path);return raw;
    }
    static void copy(Path source,Path target,Map<String,Object> expected,long maximum)throws IOException{
        long required=(Long)expected.get("bytes");if(required>maximum)throw new IOException("HTSeq complete input exceeds declared copy capacity");long count=0;
        try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;){CHECK.run();count=Math.addExact(count,n);if(count>required||count>maximum)throw new IOException("HTSeq input grew beyond its complete prepared-copy capacity");out.write(buffer,0,n);}
        }
        if(count!=required||!expected.equals(pin(target,maximum)))throw new IOException("HTSeq complete prepared input copy differs from original bytes");
    }
    static void formats(Path alignment,String format,Path annotation)throws IOException{
        for(Path file:List.of(alignment,annotation)){
            byte[] prefix;try(var in=Files.newInputStream(file)){prefix=in.readNBytes(6);}
            boolean gzip=prefix.length>=2&&(prefix[0]&255)==31&&(prefix[1]&255)==139;
            if(file.equals(alignment)&&format.equals("bam")){
                if(!gzip)throw new IOException("Declared BAM lacks its compressed BAM header; CRAM and plain SAM are not BAM inputs");
                byte[] preview;try(var in=Files.newInputStream(file)){preview=in.readNBytes(131072);}
                try(var in=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(preview))){if(!java.util.Arrays.equals(in.readNBytes(4),new byte[]{'B','A','M',1}))throw new IOException("Declared BAM has no BAM magic within the first128KiB compressed bytes; no CRAM/SAM format substitution is admitted");}
            }else if(gzip||starts(prefix,new byte[]{'B','A','M',1})||file.equals(alignment)&&starts(prefix,new byte[]{'C','R','A','M'})&&prefix.length>=5&&prefix[4]>=1&&prefix[4]<=8)throw new IOException("Require explicit plain SAM/GTF or declared BAM; compressed SAM/GTF and CRAM are not admitted");
        }
    }
    private static boolean starts(byte[] value,byte[] prefix){return value.length>=prefix.length&&java.util.Arrays.equals(java.util.Arrays.copyOf(value,prefix.length),prefix);}
    static Map<String,Object> bytePin(byte[] raw){return Map.of("bytes",(long)raw.length,"sha256",HexFormat.of().formatHex(digest().digest(raw)));}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static Map<String,Object> pin(Path path,long maximum)throws IOException{
        CHECK.run();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toAbsolutePath().normalize().equals(path.toRealPath()))throw new IOException("Require canonical regular HTSeq file: "+path);
        var before=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()>maximum)throw new IOException("HTSeq complete file capacity exceeded: "+path);
        MessageDigest hash=digest();long n=0;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[65536];for(int read;(read=in.read(buffer))!=-1;){CHECK.run();n+=read;if(n>maximum||n>before.size())throw new IOException("HTSeq file grew during capture");hash.update(buffer,0,read);}}
        var after=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(n!=after.size()||n!=before.size()||!java.util.Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||!path.toRealPath().equals(path))throw new IOException("HTSeq file identity changed: "+path);
        return Map.of("bytes",n,"sha256",HexFormat.of().formatHex(hash.digest()));
    }
    static void write(Path path,Object value)throws IOException{Files.write(path,RsemJson.bytes(value),StandardOpenOption.CREATE_NEW);}
    static Map<String,Object> json(Path path,long maximum)throws IOException{return object(SimpleJson.parse(utf8(bytes(path,maximum))));}
    static Map<String,Object> tree(Path root,long maximum,int members)throws IOException{
        if(!root.toRealPath().equals(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require canonical HTSeq capture directory");
        var result=new TreeMap<String,Object>();long total=0;int nodes=0;
        try(var paths=Files.walk(root)){
            for(Path path:(Iterable<Path>)paths::iterator){
                CHECK.run();if(path.equals(root))continue;if(++nodes>members)throw new IOException("HTSeq complete tree member capacity exceeded");
                if(Files.isSymbolicLink(path))throw new IOException("HTSeq capture/package links are not admitted: "+path);
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                var value=pin(path,maximum);total=Math.addExact(total,(Long)value.get("bytes"));if(total>maximum)throw new IOException("HTSeq complete tree byte capacity exceeded");
                result.put(root.relativize(path).toString().replace('\\','/'),value);
            }
        }return result;
    }
    static List<String> arguments(Request r,Path alignment,Path annotation)throws IOException{
        return arguments(r,alignment.toString(),annotation.toString());
    }
    static List<String> arguments(Request r,String alignment,String annotation)throws IOException{
        var args=new ArrayList<>(List.of("-s",r.text("stranded"),"-a",r.values.get("minimum_mapq").toString(),"-m",r.text("overlap"),"-t",r.text("feature_type"),"-i",r.text("id_attribute")));
        if(!r.text("version").equals("0.5.3"))args.addAll(List.of("-f",r.text("format"),"-r",r.text("order")));
        if(!Set.of("0.5.3","0.6.0").contains(r.text("version")))args.addAll(List.of("--nonunique",r.text("nonunique"),"--secondary-alignments",r.text("secondary"),"--supplementary-alignments",r.text("supplementary")));
        args.add(alignment);args.add(annotation);return List.copyOf(args);
    }
}
