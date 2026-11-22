/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

/** Verified Linux original-runtime boundary; never installs software during analysis. */
final class CellBenderRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    private final Path home,python,site,marker,stdlib;
    private final String markerHash;
    private final Map<String,String> files,links;
    private final Map<String,Map<?,?>> baseFiles;
    private final Set<String> stdlibMembers,absentPaths;
    private final Map<String,Object> session;

    CellBenderRuntime(Path supplied,Path work,Map<String,Object> session)throws Exception {
        this.session=session;
        try {
            if(!System.getProperty("os.name","").equals("Linux") || !Set.of("amd64","x86_64").contains(System.getProperty("os.arch","")))
                throw new IOException("The admitted CellBender profile requires Linux x86_64;run Linux Java inside WSL on Windows");
            home=supplied.toAbsolutePath().normalize();
            if(!home.equals(home.toRealPath()) || Files.isSymbolicLink(home))throw new IOException("Linked CellBender home/ancestor is unsupported");
            byte[] raw;
            try(var stream=CellBenderRuntime.class.getResourceAsStream("/org/jlinalg/omics/cellbender-runtime-lock.json")) {
                if(stream==null)throw new IOException("Bundled CellBender identity missing");raw=stream.readAllBytes();
            }
            Map<?,?> lock=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));
            marker=home.resolve(".jlinalg-cellbender.json");byte[] markerBytes=small(marker,8*1048576);markerHash=digest(markerBytes);
            Map<?,?> metadata=object(SimpleJson.parse(new String(markerBytes,StandardCharsets.UTF_8)));
            if(!lock.get("profile").equals(metadata.get("profile")) || !digest(raw).equals(metadata.get("runtime_lock_sha256")) || !home.toString().equals(metadata.get("home")))
                throw new IOException("CellBender marker differs from bundled identity/home;never re-seal changed/unmanaged software");
            Map<?,?> wheelPins=object(metadata.get("wheels")),expectedWheels=object(lock.get("wheels"));
            if(!wheelPins.keySet().equals(expectedWheels.keySet()))throw new IOException("CellBender wheel family differs");
            for(var e:expectedWheels.entrySet())if(!object(e.getValue()).get("sha256").equals(wheelPins.get(e.getKey())))throw new IOException("CellBender dependency wheel differs: "+e.getKey());
            if(!digest(small(home.resolve("runtime-lock.json"),1048576)).equals(digest(raw)))throw new IOException("Installed CellBender lock differs");
            var inventory=object(metadata.get("inventory"));files=hashes(object(inventory.get("files")));links=strings(object(inventory.get("links")));
            if(files.size()<100 || files.size()+links.size()>100000)throw new IOException("Invalid private CellBender inventory size");
            python=member(string(metadata.get("python")));site=member(string(metadata.get("site_packages")));
            if(!python.equals(home.resolve("venv/bin/python")) || !site.equals(home.resolve("venv/lib/python3.12/site-packages")))throw new IOException("Unexpected private Python/site path");
            for(String required:List.of("venv/lib/python3.12/site-packages/cellbender/__init__.py","venv/lib/python3.12/site-packages/cellbender/remove_background/run.py","runtime-lock.json"))
                if(!files.containsKey(required))throw new IOException("Required CellBender family member missing: "+required);
            var base=object(metadata.get("base_python"));stdlib=Path.of(string(base.get("stdlib_root"))).toAbsolutePath().normalize();
            if(!stdlib.toString().equals("/usr/lib/python3.12"))throw new IOException("This admitted profile requires the Ubuntu base stdlib path");
            stdlibMembers=stringSet(base.get("stdlib_members"));absentPaths=stringSet(base.get("absent_paths"));
            baseFiles=new LinkedHashMap<>();
            for(var e:object(base.get("files")).entrySet()) {
                String name=string(e.getKey());Path p=Path.of(name);
                if(!p.isAbsolute() || !p.normalize().equals(p) || p.startsWith(home))throw new IOException("Invalid external Python/library path");
                Map<?,?> item=object(e.getValue());if(!string(item.get("sha256")).matches("[a-f0-9]{64}"))throw new IOException("Invalid external file digest");
                baseFiles.put(name,item);
            }
            if(baseFiles.isEmpty() || baseFiles.size()>100000 || !baseFiles.containsKey("/etc/os-release"))throw new IOException("Invalid external runtime inventory");
            for(var e:object(lock.get("critical_package_files")).entrySet()) {
                Path p=member("venv/lib/python3.12/site-packages/"+string(e.getKey()));
                if(!hash(p).equals(string(e.getValue())))throw new IOException("Original CellBender member differs from bundled source identity: "+e.getKey());
            }
            for(var e:object(lock.get("notices")).entrySet()) {
                String name="upstream-notices/"+string(e.getKey());
                if(!object(e.getValue()).get("sha256").equals(files.get(name)))throw new IOException("Required upstream copyright/license notice differs");
            }
            String release=Files.readString(Path.of("/etc/os-release"),StandardCharsets.UTF_8);
            if(!release.matches("(?s).*\nID=ubuntu(?:\r?\n|$).*") || !release.matches("(?s).*\nVERSION_ID=\"24\\.04\"(?:\r?\n|$).*"))throw new IOException("Admitted CellBender profile requires Ubuntu24.04");
            session.put("runtime_home",home.toString());session.put("runtime_profile",lock.get("profile"));session.put("runtime_lock_sha256",digest(raw));session.put("runtime_marker_sha256",markerHash);
            session.put("runtime_files",files.size());session.put("runtime_base_files",baseFiles.size());session.put("python_isolation","-I -S -B -X utf8;verified site only;new empty alternate pycache prefix;empty HOME;no inherited Python/CellBender/LD settings");
            Path probe=work.resolve("cellbender-version-probe.py");
            try(var stream=CellBenderRuntime.class.getResourceAsStream("/org/jlinalg/omics/cellbender_probe.py")) {
                if(stream==null)throw new IOException("Bundled CellBender probe missing");Files.copy(stream,probe);
            }
            execute(probe,List.of(site.toString()),work,"version-probe",120,1,1073741824L);
            Map<?,?> actual=object(SimpleJson.parse(Files.readString(work.resolve("version-probe.stdout"),StandardCharsets.UTF_8)));
            if(!actual.equals(metadata.get("versions")))throw new IOException("Actual isolated Python/CellBender version or empty-state probe differs");
            session.put("runtime_versions",actual);
        }catch(IOException|RuntimeException failure){throw new Unavailable("Original CellBender dependency unavailable or changed: "+failure.getMessage(),failure);}
    }

    Path sitePackages(){return site;}
    Path home(){return home;}

    void verify()throws Unavailable {
        try {
            if(!hash(marker).equals(markerHash))throw new IOException("Private CellBender marker changed");
            var foundFiles=new HashSet<String>();var foundLinks=new LinkedHashMap<String,String>();int count=0;
            try(var walk=Files.walk(home)) {
                for(Path p:walk.toList()) {
                    Path relative=home.relativize(p);if(relative.toString().isEmpty() || ignored(relative) || p.equals(marker))continue;
                    if(++count>150000)throw new IOException("Private runtime path count exceeds bound");
                    String name=relative.toString().replace('\\','/');
                    if(Files.isSymbolicLink(p))foundLinks.put(name,Files.readSymbolicLink(p).toString());
                    else if(Files.isRegularFile(p))foundFiles.add(name);
                    else if(!Files.isDirectory(p))throw new IOException("Nonregular private runtime member");
                }
            }
            if(!foundFiles.equals(files.keySet()) || !foundLinks.equals(links))throw new IOException("Private CellBender file/link inventory changed");
            for(var e:files.entrySet())if(!hash(member(e.getKey())).equals(e.getValue()))throw new IOException("Private CellBender file changed: "+e.getKey());
            for(var e:baseFiles.entrySet()) {
                Path p=Path.of(e.getKey());Map<?,?> value=e.getValue();
                if(!Files.isRegularFile(p) || !p.toRealPath().toString().equals(value.get("real_path")) || !hash(p).equals(value.get("sha256")))throw new IOException("Base interpreter/library changed: "+p);
            }
            for(String name:absentPaths)if(Files.exists(Path.of(name)))throw new IOException("Previously absent Python import path appeared");
            var current=new HashSet<String>();
            try(var walk=Files.walk(stdlib)) {
                for(Path p:walk.toList()) {
                    Path relative=stdlib.relativize(p);
                    if(!ignored(relative) && !hasPart(relative,"site-packages") && !hasPart(relative,"dist-packages") && Files.isRegularFile(p))current.add(relative.toString().replace('\\','/'));
                }
            }
            if(!current.equals(stdlibMembers))throw new IOException("Base Python stdlib inventory changed");
        }catch(IOException|RuntimeException failure){throw new Unavailable(failure.getMessage(),failure);}
    }

    void execute(Path script,List<String> arguments,Path directory,String stage,int timeout,int threads,long maxBytes)throws Exception {
        if(!stage.matches("[A-Za-z0-9-]+") || timeout<1 || timeout>86400 || threads<1 || threads>64 || maxBytes<1)throw new IllegalArgumentException("Invalid CellBender stage/resource bound");
        verify();Path cache=Files.createDirectory(directory.resolve(stage+"-unused-pycache"));Path isolated=Files.createDirectory(directory.resolve(stage+"-empty-home"));
        var command=new ArrayList<String>();command.addAll(List.of(python.toString(),"-I","-S","-B","-X","utf8","-X","pycache_prefix="+cache,script.toString()));command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(stage+".stdout").toFile()).redirectError(directory.resolve(stage+".stderr").toFile());var env=builder.environment();env.clear();env.put("PATH",home.resolve("venv/bin")+":/usr/bin:/bin");env.put("HOME",isolated.toString());env.put("LANG","C.UTF-8");env.put("LC_ALL","C.UTF-8");env.put("PYTHONNOUSERSITE","1");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("PYTHONPYCACHEPREFIX",cache.toString());
        for(String key:List.of("MPLCONFIGDIR","IPYTHONDIR","JUPYTER_CONFIG_DIR","XDG_CACHE_HOME","TORCH_HOME"))env.put(key,isolated.resolve(key.toLowerCase(java.util.Locale.ROOT)).toString());
        for(String key:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMEXPR_NUM_THREADS"))env.put(key,Integer.toString(threads));
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("script_sha256",hash(script));call.put("timeout_seconds",timeout);call.put("maximum_work_bytes",maxBytes);session.put(stage+"_process",call);Process child;
        try{child=builder.start();}catch(IOException failure){throw new Unavailable("Verified original CellBender Python could not launch",failure);}
        call.put("pid",child.pid());long started=System.nanoTime();var descendants=new LinkedHashSet<ProcessHandle>();boolean interrupted=false;
        try {
            for(;;){child.descendants().forEach(descendants::add);boolean done=child.waitFor(100,TimeUnit.MILLISECONDS);call.put("observed_work_bytes",workBytes(directory,maxBytes));if(done){call.put("exit_code",child.exitValue());break;}if(System.nanoTime()-started>timeout*1000000000L){call.put("timed_out",true);throw new IOException("Original CellBender exceeded timeout;incomplete results are not admitted");}}
        }catch(InterruptedException failure){interrupted=true;throw failure;}
        finally {
            call.put("seconds",(System.nanoTime()-started)/1e9);if(interrupted)Thread.interrupted();
            try{child.descendants().forEach(descendants::add);for(ProcessHandle p:descendants)if(p.isAlive())p.destroyForcibly();if(child.isAlive())child.destroyForcibly();if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned CellBender Python did not terminate: "+child.pid());long end=System.nanoTime()+5000000000L;while(descendants.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<end)Thread.sleep(20);boolean live=child.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive);call.put("owned_descendants",descendants.stream().map(ProcessHandle::pid).toList());call.put("alive_after_cleanup",live);if(live)throw new IOException("An owned CellBender descendant remains alive;inspect recorded PIDs");}finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        call.put("stdout_sha256",hash(directory.resolve(stage+".stdout")));call.put("stderr_sha256",hash(directory.resolve(stage+".stderr")));verify();if(child.exitValue()!=0)throw new IOException("Original CellBender stage failed with exit"+child.exitValue()+";inspect retained original logs");
    }
    private static long workBytes(Path directory,long maximum)throws IOException {
        long[] bytes={0};int[] paths={0};
        Files.walkFileTree(directory,new SimpleFileVisitor<>() {
            private void count(Path path,BasicFileAttributes attributes)throws IOException {
                if(++paths[0]>20000)throw new IOException("CellBender work path-count bound exceeded");
                if(attributes.isSymbolicLink())throw new IOException("Linked CellBender work output is not admitted");
                if(attributes.isRegularFile()) {
                    bytes[0]=Math.addExact(bytes[0],attributes.size());
                    if(bytes[0]>maximum)throw new IOException("CellBender staged/output byte bound exceeded;review --max-work-bytes and model/data size");
                }
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException {count(path,attributes);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException {count(path,attributes);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path path,IOException failure)throws IOException {
                // The original report generator atomically moves and deletes temporary files.
                // Absence during this live size scan is normal; all other failures remain fatal.
                if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;
                throw failure;
            }
        });
        return bytes[0];
    }
    private Path member(String name)throws IOException {
        Path p=Path.of(name);if(p.isAbsolute() || name.contains("\\") || !p.normalize().equals(p) || p.getNameCount()==0)throw new IOException("Invalid private runtime member");
        Path result=home.resolve(p).normalize();if(!result.startsWith(home))throw new IOException("Runtime member escapes home");return result;
    }
    private static boolean hasPart(Path p,String text){for(Path part:p)if(part.toString().equals(text))return true;return false;}
    private static boolean ignored(Path p){String name=p.getFileName()==null?"":p.getFileName().toString();return hasPart(p,"__pycache__")||name.endsWith(".pyc")||name.endsWith(".pyo");}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Malformed runtime object");return map;}
    private static String string(Object value)throws IOException {if(!(value instanceof String s)||s.isEmpty()||s.indexOf('\0')>=0)throw new IOException("Malformed runtime text");return s;}
    private static Map<String,String> strings(Map<?,?> map)throws IOException {var out=new LinkedHashMap<String,String>();for(var e:map.entrySet())out.put(string(e.getKey()),string(e.getValue()));return out;}
    private static Map<String,String> hashes(Map<?,?> map)throws IOException {var out=strings(map);for(String value:out.values())if(!value.matches("[a-f0-9]{64}"))throw new IOException("Malformed runtime digest");return out;}
    private static Set<String> stringSet(Object value)throws IOException {if(!(value instanceof List<?> list)||list.size()>100000)throw new IOException("Malformed runtime list");var result=new HashSet<String>();for(Object v:list)if(!result.add(string(v)))throw new IOException("Duplicate runtime list member");return result;}
    private static byte[] small(Path p,int cap)throws IOException {if(!Files.isRegularFile(p)||Files.isSymbolicLink(p)||Files.size(p)>cap)throw new IOException("Missing/linked/oversized runtime metadata: "+p);return Files.readAllBytes(p);}
    private static String digest(byte[] raw)throws IOException {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}}
    static String setup(){return " Required action: use Ubuntu24.04 x86_64 Linux Java/Python (inside WSL on Windows) and an admitted private --home or JLINALG_CELLBENDER_HOME. Where installation is permitted run python3 tools/literature/install_cellbender.py --home NEW_PRIVATE_HOME --cache CACHE --report NEW_REPORT --allow-install;add --allow-download only for permitted missing pinned official wheels. This profile is original0.4.0/PyTorch2.8.0+cpu and cannot run CUDA. Do not use redacted0.3.1 or substitute historical releases. Missing,changed,unsupported or forbidden dependencies SKIP the affected analysis(exit3);preserve partial/changed homes and choose a new path. Analysis never installs or resumes a supplied checkpoint.";}
}
