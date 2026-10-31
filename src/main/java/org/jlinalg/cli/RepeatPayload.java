/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact clean upstream payload/provider admission; scientific runtime health is a separate gate. */
final class RepeatPayload {
    private RepeatPayload() { }
    private static final String BASE = "/org/jlinalg/omics/";
    private record Lock(byte[] bytes, Map<?,?> value, byte[] bootstrap) { }
    private static final Lock LOCK = load();
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message,Throwable cause) { super(message,cause); }
    }
    record Limits(long bytes, int members) {
        Limits { if (bytes < 1 || bytes > 1000000000000L || members < 1 || members > 1000000) throw new IllegalArgumentException("Require bounded complete runtime payload capacities"); }
    }
    record Fingerprint(long bytes,String sha256) { }
    record External(Path path,Fingerprint fingerprint) { }
    record Context(Path home,String profile,String version,Path python,Map<String,Fingerprint> files,
            Map<String,External> external,Limits limits,String lockSha256) {
        Context { files=Map.copyOf(files);external=Map.copyOf(external); }
        void verify(Runnable check) throws IOException {
            try { if (!equals(open(home,profile,python,limits,check))) throw new IOException("RepeatMasker clean payload/provider context changed"); }
            catch (Unavailable changed) { throw new IOException("Previously admitted RepeatMasker payload/provider became unavailable",changed); }
        }
        Map<String,String> environment(Path work) throws IOException {
            originalPath(work);
            var values=new TreeMap<String,String>();values.put("PATH",home.resolve("bin")+":/usr/bin:/bin");
            values.put("HOME",work.toString());values.put("TMPDIR",work.toString());values.put("LANG","C.UTF-8");values.put("LC_ALL","C.UTF-8");
            values.put("RMBLAST_DIR",home.resolve("bin").toString());values.put("TRF_PRGM",home.resolve("bin/trf").toString());
            values.put("FAMDB_DIR","");values.put("FAMDB_DATA_DIR","");
            values.put("LD_LIBRARY_PATH",home.resolve("lib").toString());values.put("BLAST_USAGE_REPORT","false");values.put("NCBI_USAGE_REPORT","false");values.put("BLASTDB_LMDB_MAP_SIZE","1000000000");
            values.put("PYTHONDONTWRITEBYTECODE","1");values.put("PYTHONNOUSERSITE","1");values.put("PYTHONSAFEPATH","1");
            values.put("OMP_NUM_THREADS","4");values.put("OPENBLAS_NUM_THREADS","1");values.put("MKL_NUM_THREADS","1");
            return Map.copyOf(values);
        }
    }
    static Context open(Path suppliedHome,String profile,Path suppliedPython,Limits limits,Runnable check) throws IOException {
        try(var scope=RepeatFingerprint.enter()){scope.requireActive();return openPass(suppliedHome,profile,suppliedPython,limits,check);}
    }
    private static Context openPass(Path suppliedHome,String profile,Path suppliedPython,Limits limits,Runnable check) throws IOException {
        String arch=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64","x86_64").contains(arch))
            throw new Unavailable("Original RepeatMasker payloads require Linux x86_64 Java;on Windows use Linux Java inside WSL");
        Map<?,?> profiles=object(LOCK.value().get("profiles"));
        if (!profiles.containsKey(profile)) throw new Unavailable("Requested original RepeatMasker payload profile is unavailable;no edition is substituted");
        Path home=suppliedHome.toAbsolutePath().normalize();originalPath(home);
        if (!Files.exists(home,LinkOption.NOFOLLOW_LINKS)) throw new Unavailable("Missing clean private RepeatMasker payload home");
        if (!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS) || !home.toRealPath().equals(home)) throw new IOException("Require a canonical real RepeatMasker payload home");
        Map<?,?> pin=object(profiles.get(profile));boolean historical=Boolean.TRUE.equals(pin.get("python_required"));
        if (historical && suppliedPython==null) throw new Unavailable("Historical payload requires the exact declared CPython3.12.3 interpreter");
        if (!historical && suppliedPython!=null) throw new IOException("Current custom-library payload does not consume a Python provider;remove the unused interpreter");
        var expected=new TreeMap<String,Fingerprint>();add(expected,"repeatmasker/",object(pin.get("source_members")));
        add(expected,"",object(LOCK.value().get("common_members")));
        expected.put("runtime-lock.json",fingerprint(LOCK.bytes()));expected.put("profile.txt",fingerprint((profile+"\n").getBytes(StandardCharsets.US_ASCII)));
        Path python=null;
        if (historical) {
            python=suppliedPython.toAbsolutePath().normalize();originalPath(python);
            var pythonPin=object(LOCK.value().get("python"));Fingerprint required=fingerprint(object(pythonPin.get("executable")));
            if (!Files.exists(python,LinkOption.NOFOLLOW_LINKS)) throw new Unavailable("Missing pinned CPython interpreter;provide the documented permitted build or skip the dependent analysis");
            verifyFile(python,required,check);
            if (!Files.isExecutable(python)) throw new Unavailable("Pinned CPython execution is forbidden");
            add(expected,"",object(pythonPin.get("scientific_members")));expected.put("python/repeat_python_bootstrap.py",fingerprint(LOCK.bootstrap()));
            expected.put("bin/python3",fingerprint(wrapper(home,python)));
        }
        long total=0;for(var value:expected.values())total=Math.addExact(total,value.bytes());
        if(total>limits.bytes())throw new IOException("Complete pinned RepeatMasker payload exceeds declared byte capacity");
        inventory(home,expected.keySet(),limits,check);
        try { for(var entry:expected.entrySet())verifyFile(home.resolve(entry.getKey()),entry.getValue(),check); }
        catch(AccessDeniedException forbidden){throw new Unavailable("Required payload access is forbidden;skip dependent analysis",forbidden);}
        var external=new TreeMap<String,External>();
        for(String name:List.of("perl","env","sh","sort","rm")) {
            Path path=Path.of(name.equals("sh")?"/bin/sh":"/usr/bin/"+name);
            if(!Files.exists(path)||!Files.isExecutable(path))throw new Unavailable("Missing/forbidden original runtime helper: "+name);
            Path canonical=path.toRealPath();external.put(name,new External(canonical,new Fingerprint(Files.size(canonical),RepeatFingerprint.hash(canonical,1073741824,check))));
        }
        if(python!=null)external.put("python",new External(python,new Fingerprint(Files.size(python),RepeatFingerprint.hash(python,1073741824,check))));
        for(String name:List.of("bin/rmblastn","bin/makeblastdb","bin/blastdbcmd","bin/trf","repeatmasker/RepeatMasker","repeatmasker/ProcessRepeats"))
            if(!Files.isExecutable(home.resolve(name)))throw new Unavailable("Original program execution is forbidden: "+name);
        if(historical&&!Files.isExecutable(home.resolve("bin/python3")))throw new Unavailable("Isolated FamDB bootstrap execution is forbidden");
        inventory(home,expected.keySet(),limits,check);
        return new Context(home,profile,text(pin,"version"),python,expected,external,limits,AdmixtureInputs.digest(LOCK.bytes()));
    }
    static byte[] wrapper(Path home,Path python) {
        return ("#!/bin/sh\nexec "+python+" \"-I\" \"-S\" \"-B\" \""+home.resolve("python/repeat_python_bootstrap.py")+"\" \"$@\"\n").getBytes(StandardCharsets.UTF_8);
    }
    static void originalPath(Path path) throws IOException {
        if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toString().matches("/[A-Za-z0-9_./+-]+"))
            throw new Unavailable("This original runtime profile needs canonical ASCII paths without whitespace/metacharacters;retain Unicode source files and choose an explicit supported NEW runtime/run directory");
    }
    private static void inventory(Path home,Set<String> files,Limits limits,Runnable check) throws IOException {
        var directories=new HashSet<Path>();directories.add(home);
        for(String name:files){Path parent=home.resolve(name).getParent();while(parent!=null&&parent.startsWith(home)){directories.add(parent);parent=parent.getParent();}}
        var actualFiles=new HashSet<String>();var actualDirectories=new HashSet<Path>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(home,new SimpleFileVisitor<>(){
            private void member()throws IOException{check.run();if(++count[0]>limits.members())throw new IOException("Complete RepeatMasker payload member capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{member();if(!path.toRealPath().equals(path)||!directories.contains(path))throw new IOException("Unexpected/linked RepeatMasker payload directory: "+path);actualDirectories.add(path);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{member();String name=home.relativize(path).toString().replace('\\','/');if(!attributes.isRegularFile()||!files.contains(name))throw new IOException("Unexpected/linked/nonregular RepeatMasker payload member: "+path);bytes[0]=Math.addExact(bytes[0],attributes.size());if(bytes[0]>limits.bytes())throw new IOException("Complete RepeatMasker payload byte capacity exceeded");actualFiles.add(name);return FileVisitResult.CONTINUE;}
        });
        if(!actualFiles.equals(files)||!actualDirectories.equals(directories))throw new IOException("Complete RepeatMasker payload file/directory inventory differs");
    }
    private static void add(Map<String,Fingerprint> target,String prefix,Map<?,?> values)throws IOException{
        for(var entry:values.entrySet()){
            if(!(entry.getKey() instanceof String name))throw new IOException("Payload keys must be strings");Path relative=Path.of(prefix+name);
            if(relative.isAbsolute()||!relative.normalize().equals(relative)||relative.toString().isBlank()||relative.startsWith(".."))throw new IOException("Pinned payload path escapes its home");
            if(target.putIfAbsent(relative.toString().replace('\\','/'),fingerprint(object(entry.getValue())))!=null)throw new IOException("Duplicate pinned payload path");
        }
    }
    private static void verifyFile(Path path,Fingerprint expected,Runnable check)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)!=expected.bytes()||!RepeatFingerprint.hash(path,expected.bytes(),check).equals(expected.sha256()))
            throw new IOException("Pinned clean RepeatMasker payload/provider changed: "+path);
    }
    private static Fingerprint fingerprint(Map<?,?> value)throws IOException{
        if(!(value.get("bytes") instanceof Long bytes)||bytes<0||bytes>1000000000000L||!(value.get("sha256") instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid exact pinned payload fingerprint");return new Fingerprint(bytes,hash);
    }
    private static Fingerprint fingerprint(byte[] bytes){return new Fingerprint(bytes.length,AdmixtureInputs.digest(bytes));}
    private static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Expected pinned payload JSON object");return map;}
    private static String text(Map<?,?> map,String key)throws IOException{if(!(map.get(key) instanceof String text)||text.isBlank())throw new IOException("Missing payload text: "+key);return text;}
    private static Lock load(){
        try{
            byte[] raw=resource(BASE+"repeat-runtime-lock.json",2097152);Map<?,?> document=RepeatBundle.object(raw);
            if(!Long.valueOf(1).equals(document.get("schema_version"))||!"RepeatMasker".equals(document.get("tool")))throw new IOException("Invalid pinned RepeatMasker payload lock");
            return new Lock(raw,document,resource(BASE+"repeat-runtime-setup/repeat_python_bootstrap.py",131072));
        }catch(IOException invalid){throw new ExceptionInInitializerError(invalid);}
    }
    private static byte[] resource(String name,int maximum)throws IOException{
        try(var input=RepeatPayload.class.getResourceAsStream(name)){if(input==null)throw new IOException("Missing bundled RepeatMasker payload resource: "+name);byte[] data=input.readNBytes(maximum+1);if(data.length>maximum)throw new IOException("Oversized pinned payload resource");return data;}
    }
}
