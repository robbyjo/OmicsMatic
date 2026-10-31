/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only runtime fingerprint verification; successful inspection is not scientific admission. */
final class FavorRuntimeFingerprint {
    static final String PROFILE="favorannotator-csv-r4.4.3-linux-x86_64";
    private static final Policy POLICY=loadPolicy();
    record Limits(int metadataBytes,int entries,int hostFiles,int loaders,long fileBytes,long totalBytes) {
        Limits {if(metadataBytes<1||entries<1||entries>1000000||hostFiles<1||hostFiles>10000||loaders<1||loaders>10000||fileBytes<1||totalBytes<1)throw new IllegalArgumentException("Invalid complete FAVOR runtime fingerprint capacity");}
    }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Identity(String path,String real,long bytes,String sha256) { }
    record Member(String kind,int mode,long bytes,String sha256,String target,String resolved,String targetKind) { }
    record Root(String path,Map<String,Member> members) {Root {members=Map.copyOf(members);}}
    record Binding(String library,Identity file,String resolution,String standalone,List<String> roles) {Binding {roles=List.copyOf(roles);}}
    record Loader(String context,String object,boolean staticallyLinked,List<Binding> bindings) {Loader {bindings=List.copyOf(bindings);}}
    record Spec(Map<String,Root> roots,List<Identity> host,List<Loader> loaders,Map<String,Set<String>> maps,
            String kernel,List<String> absent,Map<String,Identity> identities) {
        Spec {roots=Map.copyOf(roots);host=List.copyOf(host);loaders=List.copyOf(loaders);maps=Map.copyOf(maps);absent=List.copyOf(absent);identities=Map.copyOf(identities);}
    }
    record Policy(Map<String,Object> value,String sha256,Set<String> packages,Map<String,String> versions) {
        Policy {value=Map.copyOf(value);packages=Set.copyOf(packages);versions=Map.copyOf(versions);}
    }
    static final class Checked {
        private final Path descriptor;private final String digest;private final Spec spec;private final Limits limits;
        private Checked(Path descriptor,String digest,Spec spec,Limits limits){this.descriptor=descriptor;this.digest=digest;this.spec=spec;this.limits=limits;}
        String scope(){return "fingerprint-verified-not-scientifically-admitted";}
        Path rscript(){return Path.of(spec.roots().get("r").path(),"bin/Rscript");}
        Path xsv(){return Path.of(spec.roots().get("r").path(),"bin/xsv");}
        Path javaExecutable(){return Path.of(spec.roots().get("java").path(),"bin/java");}
        String sha256(){return digest;}
        String policySha256(){return POLICY.sha256();}
        int entries(){return spec.roots().values().stream().mapToInt(r->r.members().size()).sum();}
        void verify(Runnable check)throws IOException {verifyDescriptor(descriptor,digest,limits);verifyFiles(spec,limits,check);verifyDescriptor(descriptor,digest,limits);}
    }
    private FavorRuntimeFingerprint() { }

    static Checked inspect(Path supplied,String expected,Limits limits,Runnable check)throws IOException {
        if(expected==null||!expected.matches("[a-f0-9]{64}"))throw new IOException("Require explicit protected FAVOR runtime inventory SHA256");
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("FAVOR original runtime fingerprint requires Linux x86_64. Required action: use Linux Java inside a permitted Linux x86_64 environment, including WSL on Windows;skip dependent analysis until runtime admission succeeds.",null);
        try {
            check.run();Path file=supplied.toAbsolutePath().normalize();byte[] bytes=AdmixtureInputs.raw(file,limits.metadataBytes());
            if(!AdmixtureInputs.digest(bytes).equals(expected))throw new IOException("FAVOR runtime inventory SHA256 differs");Spec spec=parse(bytes,limits);verifyFiles(spec,limits,check);verifyDescriptor(file,expected,limits);return new Checked(file,expected,spec,limits);
        }catch(IOException failure){throw new Unavailable("FAVOR runtime fingerprint unavailable,changed,forbidden or unsupported: "+failure.getMessage()+". Required action: preserve the named inventory/runtime;provide the pinned permitted software and create a NEW verified inventory plus scientific admission. Skip dependent analysis until admission succeeds.",failure);}
    }
    static Spec parse(byte[] bytes,Limits limits)throws IOException {
        if(bytes.length>limits.metadataBytes())throw new IOException("Complete runtime metadata byte capacity exceeded");Map<?,?> m=AdmixtureInputs.decode(bytes);
        keys(m,Set.of("schema_version","status","profile","roots","trees","host_files","loader_bindings","required_absent_paths","unexpanded_external_directories","declared_broken_symlinks","R_probe","java_probes","conda_packages","conda_packages_count","conda_artifacts_match_pinned_build_lock","build_lock_sha256","GenomeInfoDbData_archive_sha256"));
        if(whole(m,"schema_version",1,1)!=1||!"inventory-only-not-admitted".equals(text(m,"status",false))||!PROFILE.equals(text(m,"profile",false))||!POLICY.value().get("build_lock_sha256").equals(hash(m,"build_lock_sha256"))||!POLICY.value().get("GenomeInfoDbData_archive_sha256").equals(hash(m,"GenomeInfoDbData_archive_sha256")))throw new IOException("Unsupported runtime inventory/profile/source build policy");
        if(!list(m.get("unexpanded_external_directories")).isEmpty()||!strings(m.get("required_absent_paths")).equals(List.of("/etc/ld.so.preload")))throw new IOException("Incomplete runtime directory or host preloader inventory");
        List<?> packages=list(m.get("conda_packages"));if(whole(m,"conda_packages_count",1,1000)!=POLICY.packages().size()||!Boolean.TRUE.equals(m.get("conda_artifacts_match_pinned_build_lock"))||!packageSet(packages).equals(POLICY.packages()))throw new IOException("Conda package artifact inventory differs from pinned source policy");
        Map<?,?> rawRoots=object(m.get("roots")),trees=object(m.get("trees"));if(rawRoots.size()<2||rawRoots.size()>32||!rawRoots.keySet().containsAll(Set.of("r","java"))||!rawRoots.keySet().equals(trees.keySet()))throw new IOException("Incomplete private runtime roots");
        var roots=new LinkedHashMap<String,Root>();var identities=new LinkedHashMap<String,Identity>();var broken=new HashSet<String>();long total=0;int count=0;
        for(var entry:rawRoots.entrySet()) {
            if(!(entry.getKey() instanceof String label)||!label.matches("[a-z][a-z0-9-]{0,63}"))throw new IOException("Invalid runtime root role");String path=absolute(entry.getValue());Map<?,?> members=object(trees.get(label));if(members.isEmpty())throw new IOException("Empty runtime tree");var parsed=new LinkedHashMap<String,Member>();
            for(var item:members.entrySet()) {
                if(++count>limits.entries())throw new IOException("Complete runtime entry capacity exceeded");String name=relative(item.getKey());Map<?,?> v=object(item.getValue());String kind=text(v,"kind",false);int mode=(int)whole(v,"mode",0,4095);long size=0;String sha=null,target=null,resolved=null,targetKind=null;
                if(kind.equals("directory"))keys(v,Set.of("kind","mode"));
                else if(kind.equals("file")){keys(v,Set.of("kind","mode","bytes","sha256"));size=whole(v,"bytes",0,limits.fileBytes());sha=hash(v,"sha256");register(identities,new Identity(path+"/"+name,path+"/"+name,size,sha));}
                else if(kind.equals("symlink")) {
                    target=text(v,"target",false);resolved=absolute(v.get("resolved"));targetKind=text(v,"target_kind",false);
                    if(targetKind.equals("file")){keys(v,Set.of("kind","mode","target","resolved","target_kind","bytes","sha256"));size=whole(v,"bytes",0,limits.fileBytes());sha=hash(v,"sha256");register(identities,new Identity(path+"/"+name,resolved,size,sha));}
                    else {keys(v,Set.of("kind","mode","target","resolved","target_kind"));if(targetKind.equals("missing")){if(!label.equals("java")||!name.equals("lib/src.zip"))throw new IOException("Unsupported missing runtime link");broken.add(label+"\n"+name+"\n"+target);}else if(!targetKind.equals("directory"))throw new IOException("Invalid runtime link target kind");}
                }else throw new IOException("Unknown runtime entry kind");
                if(size>limits.totalBytes()-total)throw new IOException("Complete runtime byte capacity exceeded");total+=size;parsed.put(name,new Member(kind,mode,size,sha,target,resolved,targetKind));
            }
            for(String name:parsed.keySet()){int slash=name.lastIndexOf('/');if(slash>=0){Member parent=parsed.get(name.substring(0,slash));if(parent==null||!parent.kind().equals("directory"))throw new IOException("Runtime tree omits a real parent directory");}}
            roots.put(label,new Root(path,parsed));
        }
        var declared=new HashSet<String>();for(Object value:list(m.get("declared_broken_symlinks"))){Map<?,?> d=object(value);keys(d,Set.of("root","path","target"));if(!declared.add(text(d,"root",false)+"\n"+relative(d.get("path"))+"\n"+text(d,"target",false)))throw new IOException("Repeated declared runtime link");}if(!declared.equals(broken))throw new IOException("Missing runtime links differ from their exact declaration");
        Map<?,?> rawHost=object(m.get("host_files"));if(rawHost.isEmpty()||rawHost.size()>limits.hostFiles())throw new IOException("Incomplete or oversized host runtime closure");var host=new ArrayList<Identity>();
        for(var entry:rawHost.entrySet()){Map<?,?> v=object(entry.getValue());keys(v,Set.of("real_path","bytes","sha256"));Identity f=new Identity(absolute(entry.getKey()),absolute(v.get("real_path")),whole(v,"bytes",0,limits.fileBytes()),hash(v,"sha256"));if(f.bytes()>limits.totalBytes()-total)throw new IOException("Complete runtime host byte capacity exceeded");total+=f.bytes();register(identities,f);host.add(f);}
        for(String needed:List.of("/bin/sh","/bin/bash","/usr/bin/mkdir","/usr/bin/ldd","/etc/ld.so.cache","/etc/ld.so.conf"))if(!identities.containsKey(needed))throw new IOException("Missing required host command/loader identity: "+needed);
        for(String needed:List.of(roots.get("r").path()+"/bin/Rscript",roots.get("r").path()+"/bin/xsv",roots.get("r").path()+"/lib/R/bin/exec/R",roots.get("java").path()+"/bin/java"))if(!identities.containsKey(needed))throw new IOException("Missing required private runtime program identity: "+needed);
        if(!FavorXsvSupervisor.XSV_SHA256.equals(identities.get(roots.get("r").path()+"/bin/xsv").sha256()))throw new IOException("Original xsv runtime fingerprint differs from pinned binary");
        Map<?,?> probe=object(m.get("R_probe"));keys(probe,Set.of("status","R","R_home","loader_path","tools","library_paths","packages","dll_paths","mapped_paths","host"));if(!"loaded-and-small-IO-smoke-only".equals(text(probe,"status",false))||!text(probe,"R",false).startsWith("R version 4.4.3 ")||!absolute(probe.get("R_home")).equals(roots.get("r").path()+"/lib/R"))throw new IOException("Original R version or private library home differs");
        Map<?,?> tools=object(probe.get("tools"));keys(tools,Set.of("mkdir","sh"));for(String tool:List.of("mkdir","sh"))if(!identities.containsKey(absolute(tools.get(tool))))throw new IOException("Unrecorded R child command binding");
        var actualVersions=new LinkedHashMap<String,String>();for(Object value:list(probe.get("packages"))){Map<?,?> p=object(value);keys(p,Set.of("name","version","path"));String name=text(p,"name",false);if(actualVersions.putIfAbsent(name,text(p,"version",false))!=null||!absolute(p.get("path")).equals(roots.get("r").path()+"/lib/R/library/"+name))throw new IOException("R package identity or private location differs");}if(!actualVersions.equals(POLICY.versions()))throw new IOException("R package versions differ from pinned policy");
        List<String> libraries=strings(probe.get("library_paths"));if(libraries.size()<1||libraries.size()>2||!libraries.contains(roots.get("r").path()+"/lib/R/library"))throw new IOException("R library isolation is incomplete");for(String path:libraries)if(!path.equals(roots.get("r").path()+"/lib/R/library")&&!absolute(path).endsWith("/empty-library"))throw new IOException("Unadmitted external R library");
        String loaderPath=text(probe,"loader_path",true);if(!loaderPath.isEmpty())for(String path:loaderPath.split(":",-1))if(!absolute(path).startsWith(roots.get("r").path()+"/"))throw new IOException("Unadmitted R child loader search directory");
        Map<?,?> hostInfo=object(probe.get("host"));keys(hostInfo,Set.of("sysname","release","machine"));if(!"Linux".equals(text(hostInfo,"sysname",false))||!"x86_64".equals(text(hostInfo,"machine",false)))throw new IOException("Unsupported recorded runtime host");String kernel=text(hostInfo,"release",false);var maps=new LinkedHashMap<String,Set<String>>();maps.put("R",mapped(probe.get("mapped_paths")));for(String path:strings(probe.get("dll_paths")))if(!path.equals("base")&&!identities.containsKey(absolute(path)))throw new IOException("Unrecorded loaded R DLL");
        Map<?,?> javaProbes=object(m.get("java_probes"));keys(javaProbes,Set.of("clean","R-child"));for(String context:List.of("clean","R-child")){Map<?,?> p=object(javaProbes.get(context));keys(p,Set.of("fields","mapped_paths"));Map<?,?> fields=object(p.get("fields"));keys(fields,Set.of("version","home","kernel"));if(!text(fields,"version",false).startsWith("21.")||!absolute(fields.get("home")).equals(roots.get("java").path())||!kernel.equals(text(fields,"kernel",false)))throw new IOException("Recorded supervisor Java21 home/version/host differs");maps.put("java-"+context,mapped(p.get("mapped_paths")));}
        for(Set<String> mapped:maps.values())if(mapped.isEmpty())throw new IOException("Empty observed runtime process mapping");
        Map<?,?> rawLoaders=object(m.get("loader_bindings"));if(rawLoaders.isEmpty()||rawLoaders.size()>limits.loaders())throw new IOException("Incomplete or oversized runtime loader inventory");var loaders=new ArrayList<Loader>();
        for(var entry:rawLoaders.entrySet()) {
            Map<?,?> v=object(entry.getValue());keys(v,Set.of("context","object","static","bindings","trace"));String context=text(v,"context",false),obj=absolute(v.get("object"));if(!Set.of("clean","R-child").contains(context)||!entry.getKey().equals(context+"\n"+obj)||!identities.containsKey(obj)||!(v.get("static") instanceof Boolean staticallyLinked)||!hash(v,"trace").equals(AdmixtureInputs.digest((context+"\n"+obj).getBytes(java.nio.charset.StandardCharsets.UTF_8))))throw new IOException("Invalid runtime loader object/context identity");
            List<?> values=list(v.get("bindings"));if(staticallyLinked?!values.isEmpty():values.isEmpty())throw new IOException("Incomplete actual runtime loader bindings");var bindings=new ArrayList<Binding>();var names=new HashMap<String,Identity>();
            for(Object value:values){Map<?,?> b=object(value);keys(b,Set.of("library","path","real_path","bytes","sha256","resolution","standalone_ldd_path","observed_process_roles"));String library=text(b,"library",false),resolution=text(b,"resolution",false),standalone=text(b,"standalone_ldd_path",false);Identity f=new Identity(absoluteAlias(b.get("path")),absolute(b.get("real_path")),whole(b,"bytes",0,limits.fileBytes()),hash(b,"sha256"));known(identities,f);Identity previous=names.putIfAbsent(library,f);if(previous!=null&&(!previous.real().equals(f.real())||previous.bytes()!=f.bytes()||!previous.sha256().equals(f.sha256())))throw new IOException("Conflicting repeated needed runtime library");List<String> roles=strings(b.get("observed_process_roles"));
                if(!Set.of("ldd","observed-already-loaded").contains(resolution)||resolution.equals("observed-already-loaded")&&roles.isEmpty())throw new IOException("Unknown or unsupported runtime loader resolution");
                for(String role:roles){if(!maps.containsKey(role)||!role.equals("R")&&!role.equals("java-"+context))throw new IOException("Wrong observed process role for runtime loader context");String real=identities.get(obj).real();if(!maps.get(role).contains(real))throw new IOException("Loader object was not mapped by its claimed observed process;fresh execs cannot inherit R loaded libraries");if(resolution.equals("observed-already-loaded")&&!maps.get(role).contains(f.real()))throw new IOException("Needed library was not mapped by the same observed process");}
                if(resolution.equals("ldd")&&!absoluteAlias(standalone).equals(f.path()))throw new IOException("Unresolved or inconsistent standalone runtime dependency");bindings.add(new Binding(library,f,resolution,standalone,roles));
            }
            loaders.add(new Loader(context,obj,staticallyLinked,bindings));
        }
        return new Spec(roots,host,loaders,maps,kernel,strings(m.get("required_absent_paths")),identities);
    }
    private static void verifyFiles(Spec spec,Limits limits,Runnable check)throws IOException {
        check.run();if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch"))||!System.getProperty("os.version").equals(spec.kernel()))throw new IOException("Actual runtime host/kernel differs from recorded fingerprint");
        for(String name:spec.absent())if(Files.exists(Path.of(name),LinkOption.NOFOLLOW_LINKS))throw new IOException("Unadmitted host preloader exists: "+name);
        var seen=new HashMap<String,Identity>();
        for(Root root:spec.roots().values()) {
            Path base=Path.of(root.path());if(!Files.isDirectory(base,LinkOption.NOFOLLOW_LINKS)||!base.toRealPath().equals(base))throw new IOException("Missing or linked runtime root: "+base);var found=new HashSet<String>();
            Files.walkFileTree(base,new SimpleFileVisitor<Path>() {
                private void member(Path file)throws IOException {check.run();if(file.equals(base))return;String relative=base.relativize(file).toString().replace('\\','/');Member expected=root.members().get(relative);if(expected==null||!found.add(relative))throw new IOException("Unexpected runtime tree entry: "+file);int mode=((Number)Files.getAttribute(file,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;if(mode!=expected.mode())throw new IOException("Runtime entry permissions changed: "+file);
                    if(expected.kind().equals("directory")){if(!Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new IOException("Runtime directory kind changed: "+file);}
                    else if(expected.kind().equals("file")){if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file))throw new IOException("Runtime file kind changed: "+file);verifyIdentity(new Identity(file.toString(),file.toString(),expected.bytes(),expected.sha256()),seen,check);}
                    else {if(!Files.isSymbolicLink(file)||!Files.readSymbolicLink(file).toString().equals(expected.target()))throw new IOException("Runtime symbolic binding changed: "+file);Path resolved=canonical(file,0);if(!resolved.toString().equals(expected.resolved()))throw new IOException("Runtime symbolic target changed: "+file);switch(expected.targetKind()){case "file"->verifyIdentity(new Identity(file.toString(),expected.resolved(),expected.bytes(),expected.sha256()),seen,check);case "directory"->{if(!Files.isDirectory(resolved)||spec.roots().values().stream().noneMatch(r->resolved.startsWith(r.path())))throw new IOException("Uninventoried runtime directory link");}case "missing"->{if(Files.exists(resolved,LinkOption.NOFOLLOW_LINKS))throw new IOException("Declared documentation link state changed");}default->throw new IOException("Unknown runtime target kind");}}
                }
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attributes)throws IOException {member(dir);return FileVisitResult.CONTINUE;}
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException {member(file);return FileVisitResult.CONTINUE;}
            });if(!found.equals(root.members().keySet()))throw new IOException("Missing runtime tree entries");
        }
        for(Identity file:spec.host())verifyIdentity(file,seen,check);
        var sonames=new HashMap<String,String>();for(Loader loader:spec.loaders())for(Binding binding:loader.bindings()){verifyIdentity(binding.file(),seen,check);if(binding.resolution().equals("observed-already-loaded")){String name=sonames.get(binding.file().real());if(name==null){name=soname(Path.of(binding.file().real()));sonames.put(binding.file().real(),name);}if(!name.equals(binding.library()))throw new IOException("Runtime observed library SONAME differs: "+binding.library());}}
        Path r=Path.of(spec.roots().get("r").path());for(Path file:List.of(r.resolve("bin/Rscript"),r.resolve("bin/xsv"),Path.of(spec.roots().get("java").path(),"bin/java"),Path.of("/bin/sh"),Path.of("/usr/bin/mkdir")))if(!Files.isExecutable(file))throw new IOException("Required runtime program is not executable: "+file);
        if(!FavorXsvSupervisor.XSV_SHA256.equals(spec.identities().get(r.resolve("bin/xsv").toString()).sha256()))throw new IOException("Original xsv runtime fingerprint differs from pinned binary");
        var actual=new HashSet<String>();Path metadata=r.resolve("conda-meta");try(var listing=Files.newDirectoryStream(metadata,"*.json")){for(Path file:listing){check.run();Map<?,?> m=AdmixtureInputs.json(file,16777216);var row=new LinkedHashMap<String,Object>();for(String key:List.of("name","version","build","subdir","sha256","url"))row.put(key,m.get(key));if(!actual.add(packageKey(row)))throw new IOException("Repeated installed Conda artifact");}}if(!actual.equals(POLICY.packages()))throw new IOException("Actual installed Conda artifact metadata differs from pinned policy");
    }
    private static void verifyIdentity(Identity expected,Map<String,Identity> seen,Runnable check)throws IOException {
        Path file=Path.of(expected.path());if(!Files.isRegularFile(file)||!file.toRealPath().toString().equals(expected.real())||Files.size(file)!=expected.bytes())throw new IOException("Missing or changed runtime file binding: "+file);Identity old=seen.get(expected.real());if(old!=null){if(old.bytes()!=expected.bytes()||!old.sha256().equals(expected.sha256()))throw new IOException("Conflicting runtime file identity");return;}
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException absent){throw new AssertionError(absent);}long size=0;try(var stream=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=stream.read(buffer))!=-1){check.run();if(n>expected.bytes()-size)throw new IOException("Runtime file grew during hashing");size+=n;digest.update(buffer,0,n);}}if(size!=expected.bytes()||!HexFormat.of().formatHex(digest.digest()).equals(expected.sha256())||Files.size(file)!=size)throw new IOException("Runtime file bytes changed: "+file);seen.put(expected.real(),expected);
    }
    private static Path canonical(Path path,int links)throws IOException {if(links>64)throw new IOException("Cyclic runtime symbolic binding");path=path.toAbsolutePath();if(Files.exists(path))return path.toRealPath();if(Files.isSymbolicLink(path)){Path target=Files.readSymbolicLink(path);return canonical(target.isAbsolute()?target:path.getParent().resolve(target),links+1);}Path parent=path.getParent();return parent==null?path:canonical(parent,links+1).resolve(path.getFileName()).normalize();}
    private static String soname(Path path)throws IOException {
        try(FileChannel file=FileChannel.open(path,StandardOpenOption.READ)){ByteBuffer h=read(file,0,64);if(h.getInt(0)!=0x464c457f||h.get(4)!=2||h.get(5)!=1)throw new IOException("Unsupported runtime ELF format");long offset=h.getLong(32);int size=Short.toUnsignedInt(h.getShort(54)),count=Short.toUnsignedInt(h.getShort(56));if(size<56||count>1024)throw new IOException("Invalid runtime ELF program header");var loads=new ArrayList<long[]>();long dynamic=-1,dynamicBytes=0;for(int i=0;i<count;i++){ByteBuffer p=read(file,offset+(long)i*size,56);if(p.getInt(0)==1)loads.add(new long[]{p.getLong(16),p.getLong(8),p.getLong(32)});if(p.getInt(0)==2){dynamic=p.getLong(8);dynamicBytes=p.getLong(32);}}if(dynamic<0)return path.getFileName().toString();if(dynamicBytes<0||dynamicBytes>1048576)throw new IOException("Oversized runtime ELF dynamic table");ByteBuffer d=read(file,dynamic,(int)dynamicBytes);long table=-1,name=-1;while(d.remaining()>=16){long tag=d.getLong(),value=d.getLong();if(tag==0)break;if(tag==5)table=value;if(tag==14)name=value;}if(table<0||name<0)return path.getFileName().toString();for(long[] p:loads)if(table>=p[0]&&table-p[0]<p[2]){long start=p[1]+table-p[0]+name;ByteBuffer text=read(file,start,(int)Math.min(4096,file.size()-start));var out=new java.io.ByteArrayOutputStream();while(text.hasRemaining()){byte b=text.get();if(b==0)return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(out.toByteArray())).toString();out.write(b);}throw new IOException("Unterminated runtime ELF SONAME");}throw new IOException("Unmapped runtime ELF string table");}
    }
    private static ByteBuffer read(FileChannel file,long offset,int size)throws IOException {if(offset<0||size<0||offset>file.size()-size)throw new IOException("Invalid runtime ELF range");ByteBuffer buffer=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);file.position(offset);while(buffer.hasRemaining())if(file.read(buffer)<0)throw new IOException("Truncated runtime ELF");buffer.flip();return buffer;}
    private static void verifyDescriptor(Path file,String expected,Limits limits)throws IOException {if(!file.toRealPath().equals(file)||!AdmixtureInputs.digest(AdmixtureInputs.raw(file,limits.metadataBytes())).equals(expected))throw new IOException("Runtime descriptor changed after inspection");}
    private static void register(Map<String,Identity> index,Identity file)throws IOException {for(String path:List.of(file.path(),file.real())){Identity old=index.putIfAbsent(path,file);if(old!=null&&(old.bytes()!=file.bytes()||!old.sha256().equals(file.sha256())||!old.real().equals(file.real())))throw new IOException("Conflicting runtime identity records");}}
    private static void known(Map<String,Identity> index,Identity file)throws IOException {Identity old=index.get(file.path());if(old==null)old=index.get(file.real());if(old==null||!old.real().equals(file.real())||old.bytes()!=file.bytes()||!old.sha256().equals(file.sha256()))throw new IOException("Loader references an unrecorded runtime file");}
    private static Policy loadPolicy(){try(var in=FavorRuntimeFingerprint.class.getResourceAsStream("/org/jlinalg/omics/favor-runtime-policy.json")){if(in==null)throw new IOException("Missing bundled FAVOR runtime policy");byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Oversized runtime policy");Map<?,?> m=AdmixtureInputs.decode(bytes);var value=new LinkedHashMap<String,Object>();for(var e:m.entrySet())value.put((String)e.getKey(),e.getValue());Map<?,?> versions=object(m.get("R_packages"));var v=new LinkedHashMap<String,String>();for(var e:versions.entrySet())v.put((String)e.getKey(),(String)e.getValue());return new Policy(value,AdmixtureInputs.digest(bytes),packageSet(list(m.get("conda_packages"))),v);}catch(IOException invalid){throw new ExceptionInInitializerError(invalid);}}
    private static Set<String> packageSet(List<?> values)throws IOException {var out=new HashSet<String>();for(Object value:values)if(!out.add(packageKey(object(value))))throw new IOException("Repeated runtime package artifact");return Set.copyOf(out);}
    private static String packageKey(Map<?,?> m)throws IOException {keys(m,Set.of("name","version","build","subdir","sha256","url"));var parts=new ArrayList<String>();for(String key:List.of("name","version","build","subdir","sha256","url"))parts.add(text(m,key,false));hash(m,"sha256");return String.join("\n",parts);}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require runtime JSON object");return map;}
    private static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require runtime JSON array");return list;}
    private static List<String> strings(Object value)throws IOException {var out=new ArrayList<String>();for(Object v:list(value)){if(!(v instanceof String s)||s.length()>8192||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid runtime string array");out.add(s);}return List.copyOf(out);}
    private static Set<String> mapped(Object value)throws IOException {var out=new HashSet<String>();for(String s:strings(value)){absolute(s);if(s.endsWith(" (deleted)"))throw new IOException("Deleted loaded runtime mapping");out.add(s);}return Set.copyOf(out);}
    private static void keys(Map<?,?> m,Set<String> keys)throws IOException {if(!m.keySet().equals(keys))throw new IOException("Unknown or missing runtime inventory fields");}
    private static String text(Map<?,?> m,String key,boolean empty)throws IOException {if(!(m.get(key) instanceof String s)||!empty&&s.isEmpty()||s.length()>8192||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid runtime text: "+key);return s;}
    private static String relative(Object value)throws IOException {if(!(value instanceof String s)||s.isEmpty()||s.length()>8192||s.startsWith("/")||s.contains("\\")||s.codePoints().anyMatch(Character::isISOControl)||List.of(s.split("/",-1)).stream().anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Invalid exact relative runtime path");return s;}
    private static String absolute(Object value)throws IOException {if(!(value instanceof String s)||!s.startsWith("/")||s.length()>8192||s.contains("\\")||s.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||List.of(s.substring(1).split("/",-1)).stream().anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Require exact absolute ASCII-stage-compatible runtime path");return s;}
    private static String absoluteAlias(Object value)throws IOException {if(!(value instanceof String s)||!s.startsWith("/")||s.length()>8192||s.contains("\\")||s.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IOException("Require exact absolute loader alias");return s;}
    private static String hash(Map<?,?> m,String key)throws IOException {String s=text(m,key,false);if(!s.matches("[a-f0-9]{64}"))throw new IOException("Invalid runtime SHA256: "+key);return s;}
    private static long whole(Map<?,?> m,String key,long min,long max)throws IOException {Object value=m.get(key);if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Invalid complete runtime count: "+key);return (long)n.doubleValue();}
}
