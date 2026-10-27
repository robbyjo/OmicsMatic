/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Verified Linux original-runtime boundary; never installs software during analysis. */
final class PymolRuntime {
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

    PymolRuntime(Path supplied,Path work,Map<String,Object> session)throws Exception {
        this.session=session;
        try {
            if(!System.getProperty("os.name","").equals("Linux") || !Set.of("amd64","x86_64").contains(System.getProperty("os.arch","")))
                throw new IOException("The admitted PyMOL profile requires Linux x86_64;run Linux Java inside WSL on Windows");
            home=supplied.toAbsolutePath().normalize();
            if(!home.equals(home.toRealPath()) || Files.isSymbolicLink(home))throw new IOException("Linked PyMOL home/ancestor is unsupported");
            byte[] raw;
            try(var stream=PymolRuntime.class.getResourceAsStream("/org/jlinalg/omics/pymol-runtime-lock.json")) {
                if(stream==null)throw new IOException("Bundled PyMOL identity missing");raw=stream.readAllBytes();
            }
            Map<?,?> lock=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));
            marker=home.resolve(".jlinalg-pymol.json");byte[] markerBytes=small(marker,8*1048576);markerHash=digest(markerBytes);
            Map<?,?> metadata=object(SimpleJson.parse(new String(markerBytes,StandardCharsets.UTF_8)));
            if(!lock.get("profile").equals(metadata.get("profile")) || !digest(raw).equals(metadata.get("runtime_lock_sha256")) || !home.toString().equals(metadata.get("home")))
                throw new IOException("PyMOL marker differs from bundled identity/home;never re-seal changed/unmanaged software");
            Map<?,?> wheelPins=object(metadata.get("wheels")),expectedWheels=object(lock.get("wheels"));
            if(!wheelPins.keySet().equals(expectedWheels.keySet()))throw new IOException("PyMOL wheel family differs");
            for(var e:expectedWheels.entrySet())if(!object(e.getValue()).get("sha256").equals(wheelPins.get(e.getKey())))throw new IOException("PyMOL dependency wheel differs: "+e.getKey());
            if(!digest(small(home.resolve("runtime-lock.json"),1048576)).equals(digest(raw)))throw new IOException("Installed PyMOL lock differs");
            var inventory=object(metadata.get("inventory"));files=hashes(object(inventory.get("files")));links=strings(object(inventory.get("links")));
            if(files.size()<100 || files.size()+links.size()>30000)throw new IOException("Invalid private PyMOL inventory size");
            python=member(string(metadata.get("python")));site=member(string(metadata.get("site_packages")));
            if(!python.equals(home.resolve("venv/bin/python")) || !site.equals(home.resolve("venv/lib/python3.12/site-packages")))throw new IOException("Unexpected private Python/site path");
            for(String required:List.of("venv/lib/python3.12/site-packages/pymol/__init__.py","venv/lib/python3.12/site-packages/pymol2/__init__.py","runtime-lock.json"))
                if(!files.containsKey(required))throw new IOException("Required PyMOL family member missing: "+required);
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
            if(baseFiles.isEmpty() || baseFiles.size()>30000 || !baseFiles.containsKey("/etc/os-release"))throw new IOException("Invalid external runtime inventory");
            for(var e:object(lock.get("notices")).entrySet()) {
                String name="upstream-notices/"+string(e.getKey());
                if(!object(e.getValue()).get("sha256").equals(files.get(name)))throw new IOException("Required upstream copyright/license notice differs");
            }
            verify();
            String release=Files.readString(Path.of("/etc/os-release"),StandardCharsets.UTF_8);
            if(!release.matches("(?s).*\nID=ubuntu(?:\r?\n|$).*") || !release.matches("(?s).*\nVERSION_ID=\"24\\.04\"(?:\r?\n|$).*"))throw new IOException("Admitted PyMOL profile requires Ubuntu24.04");
            session.put("runtime_home",home.toString());session.put("runtime_profile",lock.get("profile"));session.put("runtime_lock_sha256",digest(raw));session.put("runtime_marker_sha256",markerHash);
            session.put("runtime_files",files.size());session.put("runtime_base_files",baseFiles.size());session.put("python_isolation","-I -S -B -X utf8;verified site only;new empty alternate pycache prefix;empty HOME;no inherited Python/PyMOL/LD settings");
            Path probe=work.resolve("pymol-version-probe.py");
            try(var stream=PymolRuntime.class.getResourceAsStream("/org/jlinalg/omics/pymol_probe.py")) {
                if(stream==null)throw new IOException("Bundled PyMOL probe missing");Files.copy(stream,probe);
            }
            execute(probe,List.of(site.toString()),work,"version-probe",120,1);
            Map<?,?> actual=object(SimpleJson.parse(Files.readString(work.resolve("version-probe.stdout"),StandardCharsets.UTF_8)));
            if(!actual.equals(metadata.get("versions")))throw new IOException("Actual isolated Python/PyMOL version or empty-state probe differs");
            session.put("runtime_versions",actual);
        }catch(IOException|RuntimeException failure){throw new Unavailable("Original PyMOL dependency unavailable or changed: "+failure.getMessage(),failure);}
    }

    Path sitePackages(){return site;}
    Path home(){return home;}

    void verify()throws Unavailable {
        try {
            if(!hash(marker).equals(markerHash))throw new IOException("Private PyMOL marker changed");
            var foundFiles=new HashSet<String>();var foundLinks=new LinkedHashMap<String,String>();int count=0;
            try(var walk=Files.walk(home)) {
                for(Path p:walk.toList()) {
                    Path relative=home.relativize(p);if(relative.toString().isEmpty() || ignored(relative) || p.equals(marker))continue;
                    if(++count>60000)throw new IOException("Private runtime path count exceeds bound");
                    String name=relative.toString().replace('\\','/');
                    if(Files.isSymbolicLink(p))foundLinks.put(name,Files.readSymbolicLink(p).toString());
                    else if(Files.isRegularFile(p))foundFiles.add(name);
                    else if(!Files.isDirectory(p))throw new IOException("Nonregular private runtime member");
                }
            }
            if(!foundFiles.equals(files.keySet()) || !foundLinks.equals(links))throw new IOException("Private PyMOL file/link inventory changed");
            for(var e:files.entrySet())if(!hash(member(e.getKey())).equals(e.getValue()))throw new IOException("Private PyMOL file changed: "+e.getKey());
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

    void execute(Path script,List<String> arguments,Path directory,String stage,int timeout,int threads)throws Exception {
        if(!stage.matches("[A-Za-z0-9-]+") || timeout<1 || timeout>86400 || threads<1 || threads>64)throw new IllegalArgumentException("Invalid PyMOL stage/resource bound");
        verify();Path cache=Files.createDirectory(directory.resolve(stage+"-unused-pycache"));Path isolated=Files.createDirectory(directory.resolve(stage+"-empty-home"));
        var command=new ArrayList<>(List.of(python.toString(),"-I","-S","-B","-X","utf8","-X","pycache_prefix="+cache.toAbsolutePath(),"-W","default",script.toAbsolutePath().toString()));command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(stage+".stdout").toFile()).redirectError(directory.resolve(stage+".stderr").toFile());
        var env=builder.environment();env.keySet().removeIf(k->k.startsWith("PYTHON")||k.startsWith("PYMOL")||k.startsWith("LD_"));
        env.put("HOME",isolated.toString());env.put("PATH","/usr/bin:/bin");env.put("LC_ALL","C.UTF-8");
        for(String name:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMEXPR_NUM_THREADS"))env.put(name,Integer.toString(threads));
        session.put(stage+"_command",List.copyOf(command));session.put(stage+"_script_sha256",hash(script));Process child;
        try{child=builder.start();}catch(IOException failure){throw new Unavailable("Cannot launch original PyMOL/Python: "+failure.getMessage(),failure);}
        session.put(stage+"_owned_pid",child.pid());boolean timeoutOccurred;
        try{timeoutOccurred=!child.waitFor(timeout,TimeUnit.SECONDS);if(timeoutOccurred)terminate(child);}
        catch(InterruptedException failure){try{terminate(child);}finally{Thread.currentThread().interrupt();}throw failure;}
        session.put(stage+"_exit_code",child.exitValue());session.put(stage+"_stdout_sha256",hash(directory.resolve(stage+".stdout")));session.put(stage+"_stderr_sha256",hash(directory.resolve(stage+".stderr")));verify();
        if(timeoutOccurred){session.put(stage+"_timed_out",true);throw new IOException("Original PyMOL stage exceeded timeout;owned process terminated");}
        if(child.exitValue()!=0)throw new IOException("Original PyMOL stage failed with exit"+child.exitValue()+";inspect retained stdout/stderr");
    }
    private static void terminate(Process child)throws Exception {
        for(var handle:child.descendants().toList())if(handle.isAlive())handle.destroyForcibly();child.destroyForcibly();
        if(!child.waitFor(30,TimeUnit.SECONDS))throw new IOException("Owned PyMOL process did not terminate");
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
    private static Set<String> stringSet(Object value)throws IOException {if(!(value instanceof List<?> list)||list.size()>30000)throw new IOException("Malformed runtime list");var result=new HashSet<String>();for(Object v:list)if(!result.add(string(v)))throw new IOException("Duplicate runtime list member");return result;}
    private static byte[] small(Path p,int cap)throws IOException {if(!Files.isRegularFile(p)||Files.isSymbolicLink(p)||Files.size(p)>cap)throw new IOException("Missing/linked/oversized runtime metadata: "+p);return Files.readAllBytes(p);}
    private static String digest(byte[] raw)throws IOException {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}}
    static String setup(){return " Required action: run Linux Java/Python in Ubuntu24.04 x86_64 (WSL on Windows) and supply an admitted private --home or JLINALG_PYMOL_HOME. Where permitted, use python3 tools/literature/install_pymol.py --home NEW_PRIVATE_HOME --cache CACHE --report NEW_REPORT --reference-wheels VERIFIED_WHEELHOUSE --allow-install;add --allow-download only for permitted missing public dependencies. The pinned local PyMOL build must be available in the reference cache. Missing,changed,unsupported or forbidden dependencies skip analysis(exit3);preserve old homes/logs and choose a new path. Analysis never installs. Native molecular-geometry needs only Java.";}
}
