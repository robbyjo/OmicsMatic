/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Complete byte capture and closed JSON/source contracts for original neighbor execution. */
final class Monocle100NeighborsFiles {
    private Monocle100NeighborsFiles() { }
    static final String PROFILE="monocle100-original-neighbors-r43-v1";
    static final String ENGINE="omicsmatic-monocle100-neighbors-v1";
    static final String RESOURCE="/org/jlinalg/omics/monocle100-neighbors/";
    record Captured(Path original,Path copy,NextflowFiles.Pin pin,long capacity) {
        void verify() throws IOException { check(original,pin,capacity);check(copy,pin,capacity); }
    }
    static void require(boolean ok,String message) throws IOException { if(!ok)throw new IOException(message); }
    static Map<String,Object> object(Object value) throws IOException {
        var raw=NextflowFiles.object(value);var result=new LinkedHashMap<String,Object>();
        for(var row:raw.entrySet())result.put(NextflowFiles.text(row.getKey()),row.getValue());return result;
    }
    static Map<String,Object> read(Path path,int maximum) throws IOException { return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(path,maximum)),maximum,40_000_000)); }
    static List<?> list(Object value) throws IOException { return NextflowFiles.list(value); }
    static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    static String label(Object value) throws IOException { String result=text(value);require(!result.isBlank()&&result.length()<=32768&&result.chars().noneMatch(c->c<32||c==127),"Use nonblank literal labels without control characters");return result; }
    static long integer(Object value,long low,long high) throws IOException { return NextflowFiles.integer(value,low,high); }
    static double finite(Object value) throws IOException {
        String token=value instanceof String?(String)value:value instanceof NextflowParameters.NumberToken number?number.text():value instanceof Number?value.toString():"";
        require(token.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require an explicit finite numerical value");
        double result=Double.parseDouble(token);require(Double.isFinite(result),"Nonfinite numerical field");return result;
    }
    static void keys(Map<String,Object> value,Set<String> allowed,Set<String> required) throws IOException { require(allowed.containsAll(value.keySet())&&value.keySet().containsAll(required),"Unknown or missing closed-contract field: "+value.keySet()); }
    static Path resolve(Path directory,Object value) throws IOException { Path path=Path.of(label(value));return(path.isAbsolute()?path:directory.resolve(path)).toAbsolutePath().normalize(); }
    static Path regular(Path path) throws IOException {
        path=path.toAbsolutePath().normalize();if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(path.toString());
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require a regular unlinked file: "+path);return path.toRealPath();
    }
    static Path destination(Path path,List<Path> protectedPaths) throws IOException {
        require(!Files.exists(path,LinkOption.NOFOLLOW_LINKS),"Preserve existing output; select NEW output");Path parent=path.getParent();require(parent!=null,"Output requires a parent directory");
        while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();Path result=parent.toRealPath().resolve(parent.relativize(path)).normalize();
        for(Path input:protectedPaths)require(!result.startsWith(input)&&!input.startsWith(result),"Output overlaps a protected input or capture");return result;
    }
    static Captured capture(Path source,Path target,long capacity) throws IOException {
        var pin=NextflowFiles.pin(source,capacity,()->{});Files.copy(source,target);Captured result=new Captured(source,target,pin,capacity);result.verify();return result;
    }
    static void check(Path path,NextflowFiles.Pin wanted,long maximum) throws IOException { require(NextflowFiles.pin(path,maximum,()->{}).equals(wanted),"Captured/original file changed: "+path); }
    static Map<String,Object> pin(NextflowFiles.Pin value) { return Map.of("bytes",value.bytes(),"sha256",value.sha256()); }
    static NextflowFiles.Pin pin(Object value,long maximum) throws IOException {
        var map=object(value);keys(map,Set.of("bytes","sha256"),Set.of("bytes","sha256"));String sha=text(map.get("sha256"));require(sha.matches("[0-9a-f]{64}"),"Invalid saved SHA256");return new NextflowFiles.Pin(integer(map.get("bytes"),0,maximum),sha);
    }
    static void json(Path path,Object value) throws IOException { NextflowFiles.writeJson(path,value); }
    static void write(Path path,String value) throws IOException { Files.writeString(path,value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
    static byte[] resource(String name) throws IOException {
        try(var input=Monocle100NeighborsFiles.class.getResourceAsStream(RESOURCE+name)) { require(input!=null,"Missing packaged original neighbor source: "+name);return input.readNBytes(8*1024*1024); }
    }
    static void sources(Path root,boolean compare) throws IOException {
        byte[] raw=resource("PROFILE.json");var profile=object(NextflowParameters.parse(NextflowFiles.utf8(raw),1_048_576,100_000));require(PROFILE.equals(profile.get("profile")),"Different packaged original profile");
        var members=new LinkedHashMap<String,byte[]>();members.put("PROFILE.json",raw);
        for(var row:object(profile.get("files")).entrySet()) {
            NextflowFiles.member(row.getKey());byte[] value=resource(row.getKey());var wanted=pin(row.getValue(),8*1024*1024);
            require(wanted.bytes()==value.length&&wanted.sha256().equals(NextflowFiles.sha(value)),"Changed packaged original reference: "+row.getKey());members.put(row.getKey(),value);
        }
        for(String name:List.of("worker.R","supervisor.py","NOTICE.txt"))members.put(name,resource(name));
        for(var row:members.entrySet()) {
            Path path=root.resolve("source").resolve(row.getKey());
            if(compare)require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&java.util.Arrays.equals(NextflowFiles.bytes(path,8*1024*1024),row.getValue()),"Changed captured worker/reference source: "+row.getKey());
            else {Files.createDirectories(path.getParent());Files.write(path,row.getValue(),StandardOpenOption.CREATE_NEW);}
        }
    }
    static Map<String,Object> snapshot(Path root,long maximumFile,int maximumFiles) throws IOException {
        var result=new TreeMap<String,Object>();
        try(var paths=Files.walk(root)) {
            for(Path path:paths.toList()) {
                if(path.equals(root))continue;require(!Files.isSymbolicLink(path),"Capture contains a symlink");if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Capture contains a special file");String name=root.relativize(path).toString().replace('\\','/');
                if(name.equals("manifest.json")||name.equals("COMPLETED.json"))continue;
                require(result.size()<maximumFiles,"Complete capture exceeds file capacity");result.put(name,pin(NextflowFiles.pin(path,maximumFile,()->{})));
            }
        }
        return new LinkedHashMap<>(result);
    }
    static void manifest(Path root,long maximumFile,int maximumFiles) throws IOException { json(root.resolve("manifest.json"),Map.of("schema_version",1,"engine",ENGINE,"files",snapshot(root,maximumFile,maximumFiles))); }
    static void verifyManifest(Path root,long maximumFile,int maximumFiles) throws IOException {
        var saved=read(root.resolve("manifest.json"),64*1024*1024);keys(saved,Set.of("schema_version","engine","files"),Set.of("schema_version","engine","files"));integer(saved.get("schema_version"),1,1);require(ENGINE.equals(saved.get("engine")),"Foreign capture engine");
        var wanted=object(saved.get("files"));var actual=snapshot(root,maximumFile,maximumFiles);require(wanted.keySet().equals(actual.keySet()),"Capture file population differs");
        for(var row:wanted.entrySet())require(pin(row.getValue(),maximumFile).equals(pin(actual.get(row.getKey()),maximumFile)),"Changed complete capture member: "+row.getKey());
    }
}
