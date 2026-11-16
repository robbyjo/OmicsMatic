/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.workflow.SnakemakeBenchmarks;

/** Complete captured benchmark inputs, explicit requests and deterministic receipts. */
final class SnakemakeBenchmarkFiles {
    static final String PROFILE="snakemake-benchmark-9.27.1-v1";
    static final Set<String> KEYS=Set.of("profile","manifest","benchmark_directory","max_input_bytes","max_manifest_bytes","max_work_bytes","max_files","max_rows","max_line_characters","wall_ms");
    static final String PIN_HEADER="path\tkind\tbytes\tsha256\n";
    private static final class Unique extends Properties {
        private static final long serialVersionUID=1L;
        @Override public synchronized Object put(Object key,Object value) { if(containsKey(key))throw new IllegalArgumentException("Duplicate request/receipt property: "+key);return super.put(key,value); }
    }
    record Config(Map<String,String> values) {
        Config { values=Map.copyOf(values); }
        String get(String key) { return values.get(key); }
        long n(String key) { return Long.parseLong(get(key)); }
        int i(String key) { return Math.toIntExact(n(key)); }
    }
    private record SeenFile(Path path,Object fileKey) { }
    static final class Deadline {
        private final long start=System.nanoTime(),milliseconds;
        Deadline(long milliseconds) { this.milliseconds=milliseconds; }
        void check() throws IOException {
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native benchmark aggregation interrupted");
            if((System.nanoTime()-start)/1_000_000>milliseconds)throw new IOException("Benchmark wall_ms exceeded; retain provisional output, supply adequate permitted capacity and retry NEW output");
        }
    }
    private SnakemakeBenchmarkFiles() { }
    static Properties load(Path path,long maximum) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximum)throw new IOException("Bounded regular request/receipt required: "+path);
        Unique values=new Unique();values.load(new StringReader(Files.readString(path,StandardCharsets.UTF_8)));return values;
    }
    static Config config(Properties properties) {
        if(!properties.stringPropertyNames().equals(KEYS))throw new IllegalArgumentException("Require exactly these benchmark request fields: "+KEYS);
        var values=new TreeMap<String,String>();
        for(String key:KEYS) { String value=properties.getProperty(key);if(value.isEmpty()||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nonempty single-line request field required: "+key);values.put(key,value); }
        if(!PROFILE.equals(values.get("profile")))throw new IllegalArgumentException("Require explicit "+PROFILE+"; author engine versions are not inferred");
        Config c=new Config(values);
        for(String key:KEYS)if(key.startsWith("max_")||key.equals("wall_ms"))if(c.n(key)<1)throw new IllegalArgumentException("Positive request capacity required: "+key);
        for(String key:List.of("max_files","max_rows","max_line_characters"))c.i(key);
        if(c.n("max_manifest_bytes")>c.n("max_input_bytes")||c.n("max_manifest_bytes")>Integer.MAX_VALUE-8)throw new IllegalArgumentException("Manifest capacity must fit max_input_bytes and a bounded Java string");
        return c;
    }
    static void properties(Path target,Map<String,String> values) throws IOException {
        var text=new StringBuilder();for(var entry:new TreeMap<>(values).entrySet())text.append(escape(entry.getKey())).append('=').append(escape(entry.getValue())).append('\n');write(target,text.toString());
    }
    private static String escape(String text) { return text.replace("\\","\\\\").replace("=","\\=").replace(":","\\:").replace("#","\\#").replace("!","\\!").replace(" ","\\ "); }
    static void write(Path path,String text) throws IOException { Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    static String sha(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);} }
    static String pin(Path path,Deadline deadline) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(var input=Files.newInputStream(path)) { byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1) { deadline.check();size=Math.addExact(size,n);digest.update(block,0,n); } }
            return "F\t"+size+"\t"+HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    static String member(String name) throws IOException {
        if(name.isEmpty()||name.length()>4096||name.contains("\\")||name.chars().anyMatch(Character::isISOControl))throw new IOException("Portable relative member required: "+name);
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.contains(":"))throw new IOException("Absolute, aliased or parent-relative member is forbidden: "+name);
        return name;
    }
    static Map<String,String> tree(Path supplied,long maxBytes,int maxEntries,Deadline deadline) throws IOException {
        Path root=supplied.toRealPath();var result=new TreeMap<String,String>();var fileKeys=new HashSet<Object>();var sameContent=new java.util.HashMap<String,List<SeenFile>>();long bytes=0;int entries=0;
        try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                deadline.check();Path path=iterator.next();if(++entries>maxEntries)throw new IOException("Tree exceeds max_files: "+root);
                if(!path.toRealPath().equals(path.toAbsolutePath().normalize())||Files.isSymbolicLink(path))throw new IOException("Input/work aliases are not qualified: "+path);
                if(path.equals(root))continue;String name=member(root.relativize(path).toString().replace('\\','/'));
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))result.put(name,"D\t0\t-");
                else if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {
                    Object fileKey=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
                    if(fileKey!=null&&!fileKeys.add(fileKey))throw new IOException("Hard-linked duplicate benchmark/work files cannot be counted twice: "+path);
                    bytes=Math.addExact(bytes,Files.size(path));if(bytes>maxBytes)throw new IOException("Tree exceeds byte capacity; no complete result admitted: "+root);
                    String fingerprint=pin(path,deadline);List<SeenFile> matching=sameContent.computeIfAbsent(fingerprint,unused->new ArrayList<>());
                    for(SeenFile previous:matching) { deadline.check();if((fileKey==null||previous.fileKey()==null)&&Files.isSameFile(path,previous.path()))throw new IOException("Hard-linked duplicate benchmark/work files cannot be counted twice: "+path); }
                    matching.add(new SeenFile(path,fileKey));result.put(name,fingerprint);
                }else throw new IOException("Regular files/directories required: "+path);
            }
        }
        return result;
    }
    static String render(Map<String,String> values) { var text=new StringBuilder(PIN_HEADER);for(var entry:new TreeMap<>(values).entrySet())text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');return text.toString(); }
    static List<SnakemakeBenchmarks.Assignment> assignments(Path manifest,Config c) throws IOException {
        if(Files.size(manifest)>c.n("max_manifest_bytes"))throw new IOException("Job manifest exceeds max_manifest_bytes");
        String text=Files.readString(manifest,StandardCharsets.UTF_8);
        if(!text.endsWith("\n"))throw new IOException("Job manifest must have complete newline-terminated rows");
        String[] lines=text.split("\n",-1);if(!lines[0].replaceFirst("\r$","").equals("pipeline\treplicate\tjob\tbenchmark\trow"))throw new IOException("Expected manifest header: pipeline, replicate, job, benchmark, row (TSV)");
        var result=new ArrayList<SnakemakeBenchmarks.Assignment>();
        for(int index=1;index<lines.length-1;index++) {
            if(result.size()>=c.i("max_rows")||lines[index].length()>c.i("max_line_characters"))throw new IOException("Manifest exceeds explicit row/line capacities");
            String[] fields=lines[index].replaceFirst("\r$","").split("\t",-1);if(fields.length!=5)throw new IOException("Malformed benchmark job manifest row "+index);
            result.add(new SnakemakeBenchmarks.Assignment(fields[0],fields[1],fields[2],member(fields[3]),Integer.parseInt(fields[4])));
        }
        if(result.isEmpty())throw new IOException("Complete nonempty job manifest required");return List.copyOf(result);
    }
    static Map<String,String> code() throws IOException {
        var classes=new java.util.TreeSet<String>();var queue=new ArrayList<Class<?>>();queue.add(SnakemakeBenchmarks.class);queue.add(SnakemakeBenchmarkFiles.class);queue.add(SnakemakeBenchmarkCli.class);
        for(int index=0;index<queue.size();index++) { Class<?> type=queue.get(index);classes.add(type.getName().replace('.','/')+".class");queue.addAll(List.of(type.getDeclaredClasses())); }
        var result=new TreeMap<String,String>();
        for(String name:classes)try(var input=SnakemakeBenchmarkFiles.class.getResourceAsStream('/'+name)) { if(input==null)throw new IOException("Missing benchmark implementation class: "+name);byte[] raw=input.readAllBytes();result.put(name,"F\t"+raw.length+"\t"+sha(raw)); }
        return result;
    }
    static void copy(Path source,Path target,long maximum,Deadline deadline) throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>maximum)throw new IOException("Bounded regular capture source required: "+source);
        Files.createDirectories(target.getParent());long size=0;
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] block=new byte[65536];int n;while((n=input.read(block))!=-1) { deadline.check();size=Math.addExact(size,n);if(size>maximum)throw new IOException("Source grew beyond capture capacity: "+source);output.write(block,0,n); }
        }
    }
}
