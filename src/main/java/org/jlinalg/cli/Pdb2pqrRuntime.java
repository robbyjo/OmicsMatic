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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Admitted original PDB2PQR/PROPKA private Windows runtime. No analysis-time installation. */
final class Pdb2pqrRuntime {
    private static final String MARKER=".jlinalg-pdb2pqr.json";
    private static final String ENTRY="import sys,runpy;sys.path.insert(0,sys.argv.pop(1));sys.argv[0]='pdb2pqr';runpy.run_module('pdb2pqr',run_name='__main__')";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
        Unavailable(String message){super(message);}
    }
    private final Path home,python,site,basePrefix;
    private final String markerHash;
    private final Map<String,String> files,baseFiles;
    private final Set<String> absent;
    private final Map<String,Object> session;
    Pdb2pqrRuntime(Path supplied,Map<String,Object> session)throws Unavailable {
        this.session=session;
        try {
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new IOException("The admitted original PDB2PQR profile requires Windows x86_64 and CPython3.12 x64");
            home=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(home)||Files.isSymbolicLink(home)||!home.toRealPath().equals(home))throw new IOException("PDB2PQR home is missing,linked or aliased");
            byte[] raw;try(var input=Pdb2pqrRuntime.class.getResourceAsStream("/org/jlinalg/omics/pdb2pqr-runtime-lock.json")){if(input==null)throw new IOException("Bundled PDB2PQR identity missing");raw=input.readNBytes(2097153);if(raw.length>2097152)throw new IOException("Oversized PDB2PQR lock");}
            Map<?,?> lock=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));byte[] markerBytes=small(home.resolve(MARKER),8388608);markerHash=digest(markerBytes);Map<?,?> marker=object(SimpleJson.parse(new String(markerBytes,StandardCharsets.UTF_8)));
            if(!digest(raw).equals(marker.get("runtime_lock_sha256"))||!lock.get("profile").equals(marker.get("profile"))||!home.toString().equals(marker.get("home")))throw new IOException("PDB2PQR marker differs from bundled profile/home");
            Map<?,?> pins=object(marker.get("wheels")),wanted=object(lock.get("wheels"));if(!pins.keySet().equals(wanted.keySet()))throw new IOException("PDB2PQR dependency wheel family differs");for(var entry:wanted.entrySet())if(!object(entry.getValue()).get("sha256").equals(pins.get(entry.getKey())))throw new IOException("PDB2PQR dependency wheel differs:"+entry.getKey());
            files=hashes(object(marker.get("files")),30000);baseFiles=hashes(object(object(marker.get("base_python")).get("files")),10000);
            for(var entry:object(lock.get("source_members")).entrySet())if(!entry.getValue().equals(files.get(entry.getKey())))throw new IOException("Original package/data member differs:"+entry.getKey());
            if(!digest(raw).equals(files.get("runtime-lock.json")))throw new IOException("Installed PDB2PQR lock differs");for(var entry:object(lock.get("notices")).entrySet())if(!entry.getValue().equals(files.get("upstream-notices/"+entry.getKey())))throw new IOException("Required PDB2PQR license notice differs");
            Map<?,?> base=object(marker.get("base_python"));basePrefix=Path.of(String.valueOf(base.get("prefix"))).toAbsolutePath().normalize();if(!basePrefix.toRealPath().toString().equals(base.get("real_prefix")))throw new IOException("PDB2PQR shared CPython base moved/changed");
            if(!(base.get("version") instanceof List<?> version)||version.size()!=3||number(version.get(0))!=3||number(version.get(1))!=12)throw new IOException("PDB2PQR base is not admitted CPython3.12");
            absent=new HashSet<>();if(!(base.get("absent_paths") instanceof List<?> absentPaths)||absentPaths.size()>32)throw new IOException("Invalid absent CPython import-path metadata");for(Object path:absentPaths)absent.add(String.valueOf(path));
            python=home.resolve("venv/Scripts/python.exe");site=home.resolve("venv/Lib/site-packages");if(!files.containsKey("venv/Scripts/python.exe"))throw new IOException("Missing sealed private PDB2PQR interpreter");
            var values=new LinkedHashMap<String,String>();for(String line:Files.readAllLines(home.resolve("venv/pyvenv.cfg"),StandardCharsets.UTF_8)){String[] pair=line.split("=",2);if(pair.length==2)values.put(pair[0].trim(),pair[1].trim());}
            if(!"false".equals(values.get("include-system-site-packages"))||!Path.of(values.getOrDefault("home","")).toRealPath().equals(basePrefix))throw new IOException("PDB2PQR venv base/site isolation changed");verify();
            session.put("runtime_profile",lock.get("profile"));session.put("runtime_home",home.toString());session.put("runtime_lock_sha256",digest(raw));session.put("runtime_marker_sha256",markerHash);session.put("runtime_files",files.size());session.put("runtime_base_files",baseFiles.size());session.put("runtime_versions",marker.get("versions"));session.put("runtime_scope",lock.get("scope"));session.put("python_isolation","-I -S -B -X utf8;verified site only;fresh alternate pycache prefix;empty HOME/USERPROFILE;no inherited Python/PROPKA/PDB2PQR/LD settings;single-thread CPU");
        }catch(IOException|RuntimeException failure){throw new Unavailable("Original PDB2PQR dependency unavailable or changed:"+failure.getMessage(),failure);}
    }
    void verify()throws Unavailable {
        try {
            if(!hash(home.resolve(MARKER)).equals(markerHash))throw new IOException("PDB2PQR admission marker changed");
            if(!inventory(home,false).equals(files.keySet()))throw new IOException("Private PDB2PQR file inventory changed");for(var entry:files.entrySet())if(!hash(member(home,entry.getKey())).equals(entry.getValue()))throw new IOException("Private PDB2PQR member changed:"+entry.getKey());
            if(!inventory(basePrefix,true).equals(baseFiles.keySet()))throw new IOException("Shared CPython base inventory changed");for(var entry:baseFiles.entrySet())if(!hash(member(basePrefix,entry.getKey())).equals(entry.getValue()))throw new IOException("Shared CPython base file changed:"+entry.getKey());
            for(String name:absent)if(Files.exists(Path.of(name)))throw new IOException("Previously absent CPython import path appeared:"+name);
        }catch(IOException|RuntimeException failure){throw new Unavailable("PDB2PQR runtime integrity failed:"+failure.getMessage(),failure);}
    }
    private static Set<String> inventory(Path root,boolean base)throws IOException {
        var names=new HashSet<String>();int[] count={0};
        Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                Path relative=root.relativize(path);if(relative.toString().isEmpty())return java.nio.file.FileVisitResult.CONTINUE;
                if(ignored(relative)||base&&(hasPart(relative,"site-packages")||!Set.of("DLLs","Lib").contains(relative.getName(0).toString())))return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                if(++count[0]>60000||Files.isSymbolicLink(path)||!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Linked,aliased or excessive runtime directory");return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                Path relative=root.relativize(path);String name=relative.toString().replace('\\','/');if(ignored(relative)||!base&&name.equals(MARKER))return java.nio.file.FileVisitResult.CONTINUE;
                boolean chosen=!base||relative.getNameCount()==1&&suffix(name,".exe",".dll",".zip","._pth")||relative.getNameCount()>1&&Set.of("DLLs","Lib").contains(relative.getName(0).toString())&&suffix(name,".py",".pyd",".dll",".zip");
                if(chosen){if(++count[0]>60000||!attributes.isRegularFile()||Files.isSymbolicLink(path)||!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Linked,aliased,nonregular or excessive runtime member");names.add(name);}return java.nio.file.FileVisitResult.CONTINUE;
            }
        });return names;
    }
    private static boolean suffix(String name,String... endings){String lower=name.toLowerCase(Locale.ROOT);for(String ending:endings)if(lower.endsWith(ending))return true;return false;}
    private static boolean hasPart(Path path,String name){for(Path part:path)if(part.toString().equals(name))return true;return false;}
    private static boolean ignored(Path path){return hasPart(path,"__pycache__")||suffix(path.toString(),".pyc",".pyo");}
    private static Path member(Path root,String name)throws IOException {if(name.startsWith("/")||name.contains("\\")||name.contains(":")||java.util.Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Unsafe runtime member path");Path path=root.resolve(name).normalize();if(!Files.isRegularFile(path)||Files.isSymbolicLink(path)||!path.toRealPath().equals(path))throw new IOException("Missing/linked runtime member:"+name);return path;}
    private static byte[] small(Path path,int maximum)throws IOException {if(!Files.isRegularFile(path)||Files.isSymbolicLink(path)||Files.size(path)>maximum)throw new IOException("Missing/linked/oversized runtime metadata");return Files.readAllBytes(path);}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require runtime metadata object");return map;}
    private static int number(Object value)throws IOException {if(!(value instanceof Number number))throw new IOException("Invalid runtime numeric identity");return number.intValue();}
    private static Map<String,String> hashes(Map<?,?> source,int maximum)throws IOException {if(source.isEmpty()||source.size()>maximum)throw new IOException("Invalid runtime member count");var result=new LinkedHashMap<String,String>();for(var entry:source.entrySet()){if(!(entry.getKey() instanceof String name)||!(entry.getValue() instanceof String value)||!value.matches("[a-f0-9]{64}"))throw new IOException("Invalid runtime hash member");result.put(name,value);}return result;}
    private static String digest(byte[] bytes)throws IOException {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}}
    int execute(List<String> arguments,Path directory,String label,int timeout)throws Exception {
        if(!label.matches("[A-Za-z0-9-]+")||timeout<1||timeout>3600)throw new IllegalArgumentException("Invalid PDB2PQR stage/timeout bound");verify();Path cache=Files.createDirectory(directory.resolve(label+"-unused-pycache")),empty=Files.createDirectory(directory.resolve(label+"-empty-home"));
        var command=new ArrayList<>(List.of(python.toString(),"-I","-S","-B","-X","utf8","-X","pycache_prefix="+cache.toAbsolutePath(),"-c",ENTRY,site.toString()));command.addAll(arguments);var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(label+".stdout").toFile()).redirectError(directory.resolve(label+".stderr").toFile());
        String windows=System.getenv("SystemRoot");if(windows==null)windows=System.getenv("windir");if(windows==null||!Files.isRegularFile(Path.of(windows).resolve("System32/kernel32.dll")))throw new Unavailable("Windows System32 host dependency is unavailable");
        var env=builder.environment();for(String key:new ArrayList<>(env.keySet())){String upper=key.toUpperCase(Locale.ROOT);if(upper.startsWith("PYTHON")||upper.startsWith("PDB2PQR")||upper.startsWith("PROPKA")||upper.startsWith("APBS")||upper.startsWith("LD_")||upper.startsWith("DYLD_"))env.remove(key);}env.put("PATH",python.getParent()+";"+Path.of(windows).resolve("System32")+";"+windows);env.put("HOME",empty.toString());env.put("USERPROFILE",empty.toString());env.put("LC_ALL","C");for(String name:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMEXPR_NUM_THREADS"))env.put(name,"1");
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("timeout_seconds",timeout);var calls=new ArrayList<Object>();if(session.get("processes") instanceof List<?> list)calls.addAll(list);calls.add(call);session.put("processes",calls);Process child;
        try{child=builder.start();}catch(IOException failure){throw new Unavailable("Cannot launch admitted original PDB2PQR interpreter",failure);}call.put("pid",child.pid());long start=System.nanoTime();
        try{if(!child.waitFor(timeout,TimeUnit.SECONDS)){call.put("timed_out",true);throw new IOException("Original PDB2PQR exceeded --timeout;no completed preparation admitted");}int exit=child.exitValue();call.put("exit_code",exit);return exit;}
        finally{if(child.isAlive()){call.put("termination_requested",true);child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned PDB2PQR process could not terminate;inspect PID"+child.pid());}call.put("alive_after_cleanup",child.isAlive());call.put("seconds",(System.nanoTime()-start)/1e9);verify();}
    }
    void version(Path directory)throws Exception {if(execute(List.of("--version"),directory,"version",60)!=0||!Files.readString(directory.resolve("version.stdout"),StandardCharsets.UTF_8).strip().equals("pdb2pqr 3.8.0"))throw new Unavailable("Original PDB2PQR CLI version probe differs");}
    static String recovery(){return "Required action: on Windows x86_64,where permitted run python tools/literature/install_pdb2pqr.py --python CPYTHON312_EXECUTABLE --home NEW_PRIVATE_HOME --cache WHEEL_CACHE --allow-install;add --allow-download only when public-wheel downloads are permitted. Supply the admitted --home. Missing,changed,unsupported or forbidden dependencies SKIP preparation(exit3). Preserve old/changed homes and failed bundles. Correct the named input,force-field,protonation,repair,missing-parameter or atom-lineage issue and rerun to a new output directory. Analysis never installs software or downloads molecular structures. Prepared PQR charges/radii describe the chosen model,not a measured protonation state or an electrostatic solution.";}
}
