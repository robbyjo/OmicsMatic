/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.metagenomics.HopsAssessmentAudit;

/** Isolated public R provider; installation receipts are integrity evidence, not signatures. */
final class HopsPostprocessingProvider {
    private HopsPostprocessingProvider() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message) {super(message);}
    }
    record Snapshot(NextflowFiles.Pin receipt,Map<String,NextflowFiles.Entry> members) { }
    static byte[] resource(String name)throws IOException {
        try(var in=HopsPostprocessingProvider.class.getResourceAsStream("/org/jlinalg/metagenomics/hops035/"+name)) {
            if(in==null)throw new Unavailable("Missing packaged HOPS resource; rebuild with tools/literature/hops035_workflow/build.py");
            byte[] raw=in.readNBytes(2097153);if(raw.length>2097152)throw new IOException("Packaged HOPS resource exceeds capacity");return raw;
        }
    }
    static Snapshot inspect(Path root)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))
            throw new Unavailable("Original HOPS postprocessing needs Linux x86_64 Java and the permitted pinned public R provider");
        if(!Files.isRegularFile(root.resolve("RUNTIME_READY.json")))throw new Unavailable("Missing public R provider; use hops035_workflow/setup.py where installation is permitted");
        NextflowFiles.directory(root);byte[] lockRaw=resource("r-runtime-lock.json");
        if(lockRaw.length!=56918||!NextflowFiles.sha(lockRaw).equals("3a29cc6e204aece7ca6cb6068732c53498a0a5beb6f341f396b45ed4540fa495"))throw new IOException("Packaged public R package lock differs");
        var lock=NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(lockRaw)));
        Path receipt=root.resolve("RUNTIME_READY.json");var ready=NextflowFiles.object(NextflowFiles.json(receipt,8388608));
        if(!"READY_PINNED_PUBLIC_R405_HOPS035_PROVIDER_ANALYSIS_NOT_YET_QUALIFIED".equals(ready.get("status")))throw new IOException("Unsupported public R setup state");
        if(!NextflowFiles.fingerprint(ready.get("lock"),2097152).equals(new NextflowFiles.Pin(lockRaw.length,NextflowFiles.sha(lockRaw))))throw new IOException("Provider package lock differs");
        same(Map.of("R","4.0.5","packages",HopsAssessmentAudit.PACKAGES),ready.get("observed"),"Provider R/package versions differ");
        var records=new ArrayList<Map<String,Object>>();
        for(Object value:NextflowFiles.list(lock.get("packages"))) {
            var row=NextflowFiles.object(value);String name=NextflowFiles.text(row.get("name")),file=NextflowFiles.member(NextflowFiles.text(row.get("fn")));
            if(file.indexOf('/')>=0)throw new IOException("Public package archive name differs");
            var expected=new NextflowFiles.Pin(NextflowFiles.integer(row.get("size"),1,1000000000),NextflowFiles.text(row.get("sha256")));
            if(!NextflowFiles.pin(root.resolve("packages").resolve(file),1000000000,()->{}).equals(expected))throw new IOException("Pinned public package archive differs: "+file);
            records.add(Map.of("name",name,"file",file,"pin",expected,"url",NextflowFiles.text(row.get("url"))));
        }
        if(records.size()!=102)throw new IOException("Incomplete locked public R dependency closure");
        same(records,ready.get("packages"),"Public R package installation records differ");
        var expected=new TreeMap<String,NextflowFiles.Entry>();
        for(var entry:NextflowFiles.object(ready.get("members")).entrySet()) {
            String name=NextflowFiles.member(NextflowFiles.text(entry.getKey()));
            if(!name.startsWith("r/")&&!name.startsWith("program/"))throw new IOException("Unexpected public runtime inventory root");
            var row=NextflowFiles.object(entry.getValue());String kind=NextflowFiles.text(row.get("kind"));
            if(kind.equals("file")) {
                NextflowFiles.keys(row,"kind","bytes","sha256","mode");String mode=NextflowFiles.text(row.get("mode"));
                if(!mode.matches("0o[0-7]{3}"))throw new IOException("Invalid public runtime mode");
                expected.put(name,new NextflowFiles.Entry("file",NextflowFiles.fingerprint(row,1000000000),Integer.parseInt(mode.substring(2),8),null));
            }else if(kind.equals("symlink")) {
                NextflowFiles.keys(row,"kind","target");expected.put(name,new NextflowFiles.Entry("symlink",null,-1,NextflowFiles.text(row.get("target"))));
            }else throw new IOException("Unexpected public runtime member kind");
        }
        var actual=new TreeMap<String,NextflowFiles.Entry>();
        for(String folder:List.of("r","program"))for(var entry:installedTree(root.resolve(folder),folder.equals("r")).entrySet())actual.put(folder+"/"+entry.getKey(),entry.getValue());
        if(!actual.equals(expected))throw new IOException("Complete installed public R/program bytes, modes or links differ from the setup inventory");
        var script=actual.get("program/postprocessing.AMPS.r");
        if(script==null||!script.pin().equals(new NextflowFiles.Pin(15666,"7912dfcede0084134d1d677368f851a4d55dadeb0b7eaa591fcfe2e9c6cbdf62")))throw new IOException("Original AMPS script differs");
        for(Path executable:List.of(root.resolve("r/bin/Rscript"),Path.of("/usr/bin/setsid"),Path.of("/usr/bin/prlimit")))if(!Files.isExecutable(executable))throw new Unavailable("Required R/supervisor executable is unavailable: "+executable);
        return new Snapshot(NextflowFiles.pin(receipt,8388608,()->{}),Collections.unmodifiableMap(actual));
    }
    static void unchanged(Path root,Snapshot expected)throws IOException {
        if(!inspect(root).equals(expected))throw new IOException("Public provider changed during supervised processing");
    }
    private static Map<String,NextflowFiles.Entry> installedTree(Path root,boolean r)throws IOException {
        NextflowFiles.directory(root);var entries=new TreeMap<String,NextflowFiles.Entry>();long[] total={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes a)throws IOException {
                if(++count[0]>100000||a.isSymbolicLink())throw new IOException("Complete public runtime directory capacity/type differs");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes a)throws IOException {
                if(++count[0]>100000)throw new IOException("Complete public runtime file capacity exceeded");String name=NextflowFiles.member(root.relativize(path).toString());
                if(a.isSymbolicLink()) {
                    Path target=Files.readSymbolicLink(path);
                    if(target.isAbsolute()||!path.getParent().resolve(target).normalize().startsWith(root))throw new IOException("Public runtime link escapes its prefix");
                    // The exact pinned gcc_impl_linux-64 9.4.0 h03d3576_15
                    // archive contains this compatibility link. libstdcxx-ng11
                    // supplies a newer SONAME, so this unused GCC9 alias dangles.
                    // Retain its literal target; do not repair it or qualify GCC.
                    boolean gccAlias=r&&name.equals("x86_64-conda-linux-gnu/lib/libstdc++.so.6.0.28")&&target.toString().equals("../../lib/libstdc++.so.6.0.28");
                    if(Files.exists(path)) {if(!path.toRealPath().startsWith(root))throw new IOException("Public runtime link resolves outside its prefix");}
                    else if(!gccAlias)throw new IOException("Unqualified dangling public runtime link: "+name);
                    entries.put(name,new NextflowFiles.Entry("symlink",null,-1,target.toString()));
                }else if(a.isRegularFile()) {
                    var pin=NextflowFiles.pin(path,1000000000,()->{});total[0]=Math.addExact(total[0],pin.bytes());if(total[0]>4000000000L)throw new IOException("Complete public runtime byte capacity exceeded");entries.put(name,new NextflowFiles.Entry("file",pin,NextflowFiles.mode(path),null));
                }else throw new IOException("Nonregular public runtime member: "+name);
                return FileVisitResult.CONTINUE;
            }
        });return entries;
    }
    private static void same(Object expected,Object actual,String cause)throws IOException {
        if(!NextflowJson.json(expected).equals(NextflowJson.json(actual)))throw new IOException(cause);
    }
}
