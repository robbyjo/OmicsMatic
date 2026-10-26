/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

/** Exact source/runtime boundary for original PRS-CS. Analysis never installs. */
final class PrsCsRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String text, Exception cause) { super(text, cause); }
    }

    private static final String MARKER = ".jlinalg-prscs.json";
    private static final String BOOTSTRAP = "import os,runpy,sys;source=sys.argv.pop(1);sys.path.insert(0,source);sys.argv[0]=os.path.join(source,'PRScs.py');runpy.run_path(sys.argv[0],run_name='__main__')";
    private final Path home, python, source, markerPath, basePrefix;
    private final String markerHash, baseRealPrefix;
    private final Map<String,String> files, baseFiles;
    private final Map<?,?> selected;
    private final Map<String,Object> session;
    private final boolean legacy;

    PrsCsRuntime(Path supplied, String profile, Path work, Map<String,Object> session) throws Exception {
        this.session = session;
        home = supplied.toAbsolutePath().normalize();
        try {
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                    || !Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
                throw new IOException("The admitted original PRS-CS profile requires Windows x64");
            byte[] raw;
            try (var input = PrsCsRuntime.class.getResourceAsStream("/org/jlinalg/omics/prscs-runtime-lock.json")) {
                if (input == null) throw new IOException("Bundled PRS-CS identity missing");
                raw = input.readAllBytes();
            }
            Map<?,?> lock = object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));
            Map<?,?> profiles = object(lock.get("profiles"));
            selected = object(profiles.get(profile));
            legacy = "legacy".equals(selected.get("python"));
            markerPath = home.resolve(MARKER);
            byte[] markerBytes = small(markerPath,8*1048576);
            markerHash = digest(markerBytes);
            Map<?,?> marker = object(SimpleJson.parse(new String(markerBytes,StandardCharsets.UTF_8)));
            if (!digest(raw).equals(marker.get("runtime_lock_sha256")) || !lock.get("profile").equals(marker.get("profile")))
                throw new IOException("Original PRS-CS marker differs from bundled identity");
            if (!(marker.get("profiles") instanceof List<?> installed) || installed.isEmpty()
                    || new HashSet<>(installed).size() != installed.size() || !installed.contains(profile)
                    || !profiles.keySet().containsAll(installed))
                throw new IOException("Missing/unknown/duplicated installed PRS-CS source profile");
            files = hashes(object(marker.get("files")),30000);
            boolean hasCurrent = false;
            for (Object name : installed) {
                Map<?,?> definition = object(profiles.get(name));
                boolean current = "current".equals(definition.get("python"));
                hasCurrent |= current;
                String executable = current ? "current/Scripts/python.exe" : "legacy/python.exe";
                if (!files.containsKey(executable)) throw new IOException("Missing sealed PRS-CS interpreter");
                for (var entry : object(definition.get("files")).entrySet()) {
                    String path = "sources/"+name+"/"+entry.getKey();
                    if (!object(entry.getValue()).get("sha256").equals(files.get(path)))
                        throw new IOException("Original PRS-CS source differs from bundled pin: "+path);
                }
                if (!current && !object(lock.get("legacy_python")).get("derived_pth_sha256").equals(files.get("legacy/python310._pth")))
                    throw new IOException("Legacy Python import-path configuration differs from its pin");
            }
            if (hasCurrent != (marker.get("base_python") instanceof Map<?,?>))
                throw new IOException("External current-Python base identity missing or unexpected");
            if (hasCurrent) {
                Map<?,?> base = object(marker.get("base_python"));
                basePrefix = Path.of(String.valueOf(base.get("prefix"))).toAbsolutePath().normalize();
                baseRealPrefix = String.valueOf(base.get("real_prefix"));
                baseFiles = hashes(object(base.get("files")),10000);
                if (!(base.get("version") instanceof List<?> version) || version.size()!=3
                        || integer(version.get(0))!=3 || integer(version.get(1))!=12 || baseFiles.isEmpty())
                    throw new IOException("Current Python base must be admitted CPython 3.12 x64");
                String config = new String(small(home.resolve("current/pyvenv.cfg"),65536),StandardCharsets.UTF_8);
                var values = new LinkedHashMap<String,String>();
                for (String line : config.lines().toList()) {
                    String[] pair = line.split("=",2);
                    if (pair.length==2) values.put(pair[0].strip(),pair[1].strip());
                }
                if (!"false".equals(values.get("include-system-site-packages"))
                        || !Path.of(values.getOrDefault("home","")).toRealPath().equals(Path.of(baseRealPrefix)))
                    throw new IOException("Current Python venv base/site-package isolation differs");
            } else {
                basePrefix = null; baseRealPrefix = null; baseFiles = Map.of();
            }
            python = home.resolve(legacy ? "legacy/python.exe" : "current/Scripts/python.exe");
            source = home.resolve("sources").resolve(profile);
            session.put("runtime_home",home.toString());
            session.put("runtime_profile",lock.get("profile"));
            session.put("runtime_lock_sha256",digest(raw));
            session.put("runtime_marker_sha256",markerHash);
            session.put("runtime_files",files.size());
            session.put("external_python_base_files",baseFiles.size());
            session.put("selected_source_profile",profile);
            session.put("original_source_commit",selected.get("commit"));
            session.put("legacy_runtime_out_of_support",legacy);
            session.put("python_cache_bypassed",true);
            verify();
            String probe = "import json,sys,struct,numpy,scipy,h5py;print(json.dumps(dict(implementation=sys.implementation.name,version=list(sys.version_info[:3]),bits=8*struct.calcsize('P'),numpy=numpy.__version__,scipy=scipy.__version__,h5py=h5py.__version__)))";
            var command = flags(work,"imports"); command.addAll(List.of("-c",probe));
            execute(command,work,"imports",60,1);
            Map<?,?> versions = object(SimpleJson.parse(Files.readString(work.resolve("imports.stdout"))));
            if (!"cpython".equals(versions.get("implementation")) || integer(versions.get("bits"))!=64
                    || !(versions.get("version") instanceof List<?> version) || version.size()!=3
                    || integer(version.get(0))!=3 || integer(version.get(1))!=(legacy?10:12)
                    || legacy && integer(version.get(2))!=11)
                throw new IOException("Actual Python version differs from admitted profile");
            Object packages = object(lock.get("packages")).get(legacy?"legacy":"current");
            if (!(packages instanceof List<?> list)) throw new IOException("Malformed bundled Python package pins");
            for (Object value : list) {
                Map<?,?> item = object(value);
                if (!item.get("version").equals(versions.get(item.get("name"))))
                    throw new IOException("Actual package version differs: "+item.get("name"));
            }
            session.put("python_versions",versions);
            command = original(work,"usage"); command.add("--help");
            execute(command,work,"usage",60,1);
            if (!Files.readString(work.resolve("usage.stdout")).contains("PRS-CS: a polygenic prediction method"))
                throw new IOException("Original PRS-CS help probe differs");
        } catch (IOException | RuntimeException failure) {
            throw new Unavailable("Original PRS-CS dependency unavailable or changed: "+failure.getMessage(),failure);
        }
    }

    boolean legacy() { return legacy; }
    Path sourceDirectory() { return source; }
    boolean supportsSe() { return Boolean.TRUE.equals(selected.get("supports_se")); }
    boolean supportsPsi() { return Boolean.TRUE.equals(selected.get("supports_psi")); }
    boolean supportsDraws() { return Boolean.TRUE.equals(selected.get("supports_draws")); }
    String bool(boolean value) { return "title".equals(selected.get("boolean_case")) ? value?"True":"False" : value?"TRUE":"FALSE"; }

    ArrayList<String> flags(Path work, String label) throws IOException {
        if (!label.matches("[A-Za-z0-9-]+")) throw new IllegalArgumentException("Invalid PRS-CS process label");
        Path cache = work.resolve(label+"-unused-cache"); Files.createDirectory(cache);
        return new ArrayList<>(List.of(python.toString(),"-I","-B","-X","utf8","-X","pycache_prefix="+cache.toAbsolutePath(),"-W","default"));
    }
    ArrayList<String> original(Path work, String label) throws IOException {
        var command = flags(work,label); command.addAll(List.of("-c",BOOTSTRAP,source.toString())); return command;
    }
    void execute(List<String> command, Path directory, String stage, int timeout, int threads) throws Exception {
        if (!stage.matches("[A-Za-z0-9-]+") || timeout<1 || timeout>86400 || threads<1 || threads>256)
            throw new IllegalArgumentException("Invalid PRS-CS process label/timeout/thread bound");
        verify(); Path stdout=directory.resolve(stage+".stdout"),stderr=directory.resolve(stage+".stderr");
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        for (String name : List.of("PYTHONPATH","PYTHONHOME","PYTHONSTARTUP","PYTHONINSPECT","PYTHONWARNINGS")) builder.environment().remove(name);
        for (String name : List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMEXPR_NUM_THREADS")) builder.environment().put(name,Integer.toString(threads));
        builder.environment().put("HDF5_PLUGIN_PRELOAD","::");builder.environment().put("HDF5_PLUGIN_PATH","");
        session.put(stage+"_command",List.copyOf(command));Process child;
        try { child=builder.start(); } catch (IOException failure) { throw new Unavailable("Cannot launch admitted PRS-CS Python: "+failure.getMessage(),failure); }
        session.put(stage+"_owned_pid",child.pid());boolean timedOut;
        try { timedOut=!child.waitFor(timeout,TimeUnit.SECONDS);if(timedOut)terminate(child); }
        catch (InterruptedException failure) { try {terminate(child);}finally{Thread.currentThread().interrupt();}throw failure; }
        session.put(stage+"_exit_code",child.exitValue());session.put(stage+"_stdout_sha256",hash(stdout));session.put(stage+"_stderr_sha256",hash(stderr));verify();
        if(timedOut){session.put(stage+"_timed_out",true);throw new IOException("Original PRS-CS "+stage+" timed out; owned process terminated");}
        if(child.exitValue()!=0)throw new IOException("Original PRS-CS "+stage+" failed with exit "+child.exitValue()+"; inspect retained original logs");
    }
    private static void terminate(Process child) throws Exception {
        for(var descendant:child.descendants().toList())if(descendant.isAlive())descendant.destroyForcibly();
        child.destroyForcibly();if(!child.waitFor(30,TimeUnit.SECONDS))throw new IOException("Owned PRS-CS process did not terminate");
    }

    void verify() throws Unavailable {
        try {
            if (!home.equals(home.toRealPath()) || !hash(markerPath).equals(markerHash)) throw new IOException("PRS-CS home/marker changed");
            verifyFiles(home,files,false);
            if (basePrefix!=null) {
                if (!basePrefix.toRealPath().equals(Path.of(baseRealPrefix))) throw new IOException("External Python base was retargeted");
                verifyFiles(basePrefix,baseFiles,true);
            }
        } catch (IOException | RuntimeException failure) { throw new Unavailable("Original PRS-CS runtime changed: "+failure.getMessage(),failure); }
    }
    private static void verifyFiles(Path root, Map<String,String> expected, boolean base) throws IOException {
        var observed = new HashSet<String>();
        if(base)try(var paths=Files.list(root)) {
            for(Path p:paths.toList()) {
                String n=p.getFileName().toString().toLowerCase(Locale.ROOT);
                if(Files.isRegularFile(p)&&n.startsWith("python")&&(n.endsWith(".exe")||n.endsWith(".dll")||n.endsWith(".zip")))check(root,p,expected,observed);
            }
        }
        for(Path directory:base?List.of(root.resolve("DLLs"),root.resolve("Lib")):List.of(root)) {
            if(!Files.exists(directory))continue;
            Files.walkFileTree(directory,new SimpleFileVisitor<>() {
                private int visits;
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attributes)throws IOException {
                    if(++visits>50000||!dir.toAbsolutePath().normalize().equals(dir.toRealPath()))throw new IOException("Linked/oversized runtime tree");
                    Path relative=root.relativize(dir);
                    if(base&&(parts(relative,"__pycache__")||parts(relative,"site-packages")))return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path p,BasicFileAttributes attributes)throws IOException {
                    if(++visits>50000||Files.isSymbolicLink(p)||!attributes.isRegularFile())throw new IOException("Linked/oversized/nonregular runtime member");
                    String relative=root.relativize(p).toString().replace('\\','/'),name=p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if(parts(root.relativize(p),"__pycache__")||!base&&relative.equals(MARKER))return FileVisitResult.CONTINUE;
                    if(!base||name.endsWith(".py")||name.endsWith(".pyd")||name.endsWith(".dll")||name.endsWith(".zip"))check(root,p,expected,observed);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if(!observed.equals(expected.keySet()))throw new IOException("Runtime file inventory differs");
    }
    private static void check(Path root,Path p,Map<String,String> expected,Set<String> observed)throws IOException {
        String name=root.relativize(p).toString().replace('\\','/');
        if(!p.toAbsolutePath().normalize().equals(p.toRealPath())||!observed.add(name)||!hash(p).equals(expected.get(name)))throw new IOException("Changed/extra runtime file: "+name);
    }
    private static boolean parts(Path path,String name){for(Path part:path)if(part.toString().equals(name))return true;return false;}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Malformed original runtime identity");return map;}
    private static int integer(Object value)throws IOException {if(!(value instanceof Number number))throw new IOException("Malformed runtime number");return number.intValue();}
    private static Map<String,String> hashes(Map<?,?> value,int maximum)throws IOException {
        if(value.isEmpty()||value.size()>maximum)throw new IOException("Runtime inventory count invalid");var result=new LinkedHashMap<String,String>();
        for(var e:value.entrySet()) {
            String key=String.valueOf(e.getKey()),digest=String.valueOf(e.getValue());Path p=Path.of(key);
            if(p.isAbsolute()||key.contains("\\")||!p.normalize().toString().replace('\\','/').equals(key)||key.startsWith("../")||!digest.matches("[a-f0-9]{64}"))throw new IOException("Escaping/malformed runtime file pin");
            result.put(key,digest);
        }
        return result;
    }
    private static byte[] small(Path path,int maximum)throws IOException {
        if(!Files.isRegularFile(path))throw new IOException("Missing PRS-CS runtime identity/configuration: "+path);
        try(var input=Files.newInputStream(path)){byte[] raw=input.readNBytes(maximum+1);if(raw.length>maximum)throw new IOException("Runtime identity/configuration exceeds byte bound");return raw;}
    }
    private static String digest(byte[] raw)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    static String setup(){return " Where permitted, run python tools/literature/install_prscs.py --profiles SELECTED_PROFILE --python CPYTHON_312_EXECUTABLE --home NEW_PRIVATE_HOME --allow-install; add --allow-download only for permitted missing official artifacts. Supply that sealed home with --home or JLINALG_PRSCS_HOME. Missing, changed or forbidden dependencies skip analysis (exit 3); preserve changed homes and use new paths. Historical 1.0.0/1.1.0 runtimes use unsupported Python 3.10 only for explicit offline reproduction. No reference panel is downloaded.";}
}
