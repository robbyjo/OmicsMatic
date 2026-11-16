/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.alignment.SambambaStreamingBamAudit;

/** Strict explicit streaming capacities, integrity and workspace operations for v2. */
final class SambambaStreamingWorkflowFiles {
    static final String PROFILE="original-sambamba-streaming-linux-v2";
    static final Set<String> KEYS=Set.of("bam","sambamba","edition","action","threads","compression",
        "hash_table_size","overflow_list_size","sort_buffer_mb","io_buffer_mb","wall_ms","address_space_mb","work_mb","max_files","audit_compressed_bytes","audit_decoded_bytes","audit_records","audit_record_bytes","audit_header_bytes","audit_dictionary_bytes");
    static final Map<String,String> BINARIES=Map.of(
        "0.6.6","8b06691f80bc89c733267c523dbbb3a4c36b51cae42ef7b2a171e17c68516dc2",
        "0.8.2","bbccbd914ebf8c2b09a5bb6a922dba99ac24c09d25ddd4925f5b2cc8dc359543");
    static final Map<String,Long> BINARY_BYTES=Map.of("0.6.6",5728376L,"0.8.2",22248744L);
    static final class Prerequisite extends IOException {
        private static final long serialVersionUID=1L;
        Prerequisite(String message){super(message);}
    }
    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID=1L;
        @Override public synchronized Object put(Object key,Object value){
            if(containsKey(key))throw new IllegalArgumentException("Duplicate property: "+key);
            return super.put(key,value);
        }
    }
    record Config(Map<String,String> values) {
        Config {values=Map.copyOf(values);}
        String get(String key){return values.get(key);}
        long number(String key){return Long.parseLong(get(key));}
        SambambaStreamingBamAudit.Limits auditLimits(){return new SambambaStreamingBamAudit.Limits(number("audit_compressed_bytes"),number("audit_decoded_bytes"),number("audit_records"),Math.toIntExact(number("audit_record_bytes")),Math.toIntExact(number("audit_header_bytes")),Math.toIntExact(number("audit_dictionary_bytes")));}
        boolean remove(){return get("action").equals("remove");}
        long workBytes(){return number("work_mb")*(1L<<20);}
        long addressBytes(){return number("address_space_mb")*(1L<<20);}
        Properties properties(){Properties p=new Properties();p.putAll(values);return p;}
    }
    record Inventory(Map<String,String> members,long bytes,int entries) {
        Inventory {members=Map.copyOf(members);}
    }
    private SambambaStreamingWorkflowFiles(){ }

    static Config config(Properties p){
        if(!p.stringPropertyNames().equals(KEYS))throw new IllegalArgumentException("Request must contain exactly these fields: "+new TreeSet<>(KEYS));
        for(String key:KEYS)if(p.getProperty(key).isBlank())throw new IllegalArgumentException("Blank request field: "+key);
        if(!BINARIES.containsKey(p.getProperty("edition")))throw new IllegalArgumentException("Edition must be exactly 0.6.6 or 0.8.2");
        if(!Set.of("mark","remove").contains(p.getProperty("action")))throw new IllegalArgumentException("Action must be mark or remove");
        integer(p,"threads",1,64);integer(p,"compression",0,9);integer(p,"hash_table_size",1,16_777_216);
        integer(p,"overflow_list_size",1,16_777_216);integer(p,"sort_buffer_mb",1,4096);integer(p,"io_buffer_mb",1,256);
        integer(p,"wall_ms",1,604_800_000);integer(p,"address_space_mb",8,65_536);integer(p,"work_mb",1,1L<<40);integer(p,"max_files",1,10_000);
        integer(p,"audit_compressed_bytes",28,1L<<50);integer(p,"audit_decoded_bytes",12,1L<<56);integer(p,"audit_records",0,Long.MAX_VALUE);
        integer(p,"audit_record_bytes",32,1<<30);integer(p,"audit_header_bytes",1,64<<20);integer(p,"audit_dictionary_bytes",4,64<<20);
        Map<String,String> values=new TreeMap<>();for(String key:KEYS)values.put(key,p.getProperty(key));return new Config(values);
    }
    private static void integer(Properties p,String key,long min,long max){
        String raw=p.getProperty(key);long n;
        try{n=Long.parseLong(raw);}catch(NumberFormatException e){throw new IllegalArgumentException("Use a canonical integer for "+key,e);}
        if(!Long.toString(n).equals(raw)||n<min||n>max)throw new IllegalArgumentException(key+" must be a canonical integer in "+min+".."+max);
    }
    static void scientificCoherence(Config original,Config effective){
        for(String key:KEYS)if(!Set.of("bam","sambamba").contains(key)&&!original.get(key).equals(effective.get(key)))
            throw new IllegalArgumentException("Original/effective request contradiction: "+key);
        if(!effective.get("bam").equals("input.bam")||!Path.of(effective.get("sambamba")).isAbsolute())
            throw new IllegalArgumentException("Only the recorded captured input and absolute original binary may be relocated");
    }
    static void linux()throws Prerequisite {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))
            throw new Prerequisite("Run this profile in a permitted Linux x86-64 JVM, including Java inside your selected WSL distribution. The exact Linux releases cannot execute in a native Windows JVM.");
        for(String helper:List.of("/usr/bin/setpriv","/usr/bin/prlimit","/usr/bin/ldd"))
            if(!Files.isExecutable(Path.of(helper)))throw new Prerequisite("Required Linux helper unavailable: "+helper+". Supply a permitted util-linux/glibc runtime and retry a NEW directory; skip dependent analysis.");
    }
    static Path binary(Path path,String edition)throws Exception {
        if(!Files.isRegularFile(path)||!Files.isExecutable(path))throw new Prerequisite("Exact permitted Sambamba"+edition+" executable unavailable: "+path+". Use the pinned download recipe, supply executable permission where allowed, and retry NEW output.");
        Path resolved=path.toRealPath();commandPath(resolved);
        if(Files.size(resolved)!=BINARY_BYTES.get(edition)||!sha(resolved,32L<<20).equals(BINARIES.get(edition)))throw new IOException("Original executable identity differs for Sambamba"+edition+"; no edition substitution or patched binary is admitted");
        return resolved;
    }
    static void commandPath(Path p){
        String text=p.toString();for(char c:new char[]{'\0','\t','\r','\n'})if(text.indexOf(c)>=0)
            throw new IllegalArgumentException("Command/output paths must not contain NUL, tabs or line breaks; choose a NEW plain path");
    }
    static Properties load(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>1_048_576)throw new IOException("Require a regular properties file <=1 MiB: "+path);
        return parse(Files.readString(path,StandardCharsets.UTF_8));
    }
    static Properties parse(String text)throws IOException {Properties p=new UniqueProperties();p.load(new StringReader(text));return p;}
    static byte[] bytes(Properties p)throws IOException {StringWriter w=new StringWriter();p.store(w,null);return w.toString().getBytes(StandardCharsets.UTF_8);}
    static void write(Path path,String text)throws IOException {write(path,text.getBytes(StandardCharsets.UTF_8));}
    static void write(Path path,byte[] raw)throws IOException {Files.write(path,raw,StandardOpenOption.CREATE_NEW);}
    static void store(Path path,Properties p)throws IOException {write(path,bytes(p));}
    static String sha(Path path)throws Exception{return sha(path,Long.MAX_VALUE);}
    static String sha(Path path,long maximum)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(path)){byte[] b=new byte[65536];long size=0;for(int n;(n=in.read(b))>=0;)if(n>0){size=Math.addExact(size,n);if(size>maximum)throw new IOException("File grew beyond its admitted byte bound: "+path);digest.update(b,0,n);}}
        return HexFormat.of().formatHex(digest.digest());
    }
    static Path intendedNew(Path requested)throws IOException {
        if(requested.getParent()==null||Files.exists(requested,LinkOption.NOFOLLOW_LINKS))throw new IOException("Choose a NEW output directory; existing paths are preserved");
        Path ancestor=requested;var missing=new ArrayList<Path>();
        while(!Files.exists(ancestor,LinkOption.NOFOLLOW_LINKS)){missing.add(ancestor.getFileName());ancestor=ancestor.getParent();if(ancestor==null)throw new IOException("Output has no existing absolute ancestor");}
        Path result=ancestor.toRealPath();for(int i=missing.size()-1;i>=0;i--)result=result.resolve(missing.get(i));return result.normalize();
    }
    static String sha(byte[] raw)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    static Inventory inventory(Path root,Config config,boolean hashes)throws Exception {
        Map<String,String> members=new TreeMap<>();long bytes=0;int count=0;
        try(var walk=Files.walk(root)){
            for(var iterator=walk.iterator();iterator.hasNext();){
                Path p=iterator.next();if(p.equals(root))continue;
                java.nio.file.attribute.BasicFileAttributes attributes;
                try{attributes=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
                catch(java.nio.file.NoSuchFileException e){if(!hashes)continue;throw e;}
                if(++count>config.number("max_files"))throw new IOException("Workspace file/directory count exceeds max_files");
                if(attributes.isSymbolicLink())throw new IOException("Workspace contains an unqualified symbolic link: "+p);
                String name=root.relativize(p).toString().replace(java.io.File.separatorChar,'/');
                if(attributes.isDirectory())members.put(name,"DIRECTORY");
                else if(attributes.isRegularFile()){
                    long size=attributes.size();
                    bytes=Math.addExact(bytes,size);if(bytes>config.workBytes())throw new IOException("Workspace bytes exceed work_mb; the analysis is not admitted");
                    members.put(name,hashes?sha(p):"FILE");
                }else throw new IOException("Workspace member is not a regular file/directory: "+name);
            }
        }
        return new Inventory(members,bytes,count);
    }
    static Properties implementation()throws Exception {
        var classes=new TreeSet<Class<?>>(java.util.Comparator.comparing(Class::getName));
        for(Class<?> c:List.of(SambambaStreamingWorkflowCli.class,SambambaStreamingWorkflowFiles.class,SambambaStreamingWorkflowProcess.class,SambambaStreamingWorkflowRuntime.class,SambambaStreamingWorkflowBundle.class,SambambaStreamingBamAudit.class))collect(c,classes);
        Properties p=new Properties();p.setProperty("profile",PROFILE);
        for(Class<?> c:classes)try(var in=c.getResourceAsStream("/"+c.getName().replace('.','/')+".class")){
            if(in==null)throw new IOException("Implementation resource unavailable: "+c.getName());
            p.setProperty("sha256."+c.getName(),sha(in.readAllBytes()));
        }
        return p;
    }
    private static void collect(Class<?> c,Set<Class<?>> into){if(into.add(c))for(Class<?> n:c.getDeclaredClasses())collect(n,into);}
    static List<String> command(Path binary,Config c,Path root,boolean remove){
        Path tmp=root.resolve(remove?"tmp-remove":"tmp-mark"),result=root.resolve(remove?"deduplicated.bam":"marked.bam");
        var args=new ArrayList<>(List.of(binary.toString(),"markdup","-t",c.get("threads"),"-l",c.get("compression"),
            "--hash-table-size="+c.get("hash_table_size"),"--overflow-list-size="+c.get("overflow_list_size"),
            "--sort-buffer-size="+c.get("sort_buffer_mb"),"--io-buffer-size="+c.get("io_buffer_mb"),"--tmpdir="+tmp));
        if(remove)args.add("--remove-duplicates");args.add(root.resolve("input.bam").toString());args.add(result.toString());return List.copyOf(args);
    }
    static String headerCommand(List<String> command){return String.join(" ",command.subList(1,command.size()));}
}
