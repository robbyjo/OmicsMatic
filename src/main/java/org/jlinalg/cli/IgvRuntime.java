/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.hash;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Exact private original IGV/Java21 source-worker boundary;analysis never installs. */
final class IgvRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Throwable cause){super(message,cause);}
        Unavailable(String message){super(message);}
    }
    private final Path home,javaExecutable,worker;
    private final Map<String,String> files;
    private final Map<Path,String> javaFiles;
    private final String markerHash;
    private final List<String> jvmArguments;
    private final Map<String,Object> session;
    IgvRuntime(Path supplied,Map<String,Object> session)throws Exception {
        this.session=session;
        try {
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new IOException("Admitted IGV profile requires Windows x86_64 with a usable Java graphics environment");
            home=supplied.toAbsolutePath().normalize();if(!home.toString().chars().allMatch(c->c<128)||!Files.isDirectory(home)||!home.toRealPath().equals(home))throw new IOException("Require an existing real ASCII-named private IGV home");
            byte[] raw;try(var stream=IgvRuntime.class.getResourceAsStream("/org/jlinalg/omics/igv-runtime-lock.json")){if(stream==null)throw new IOException("Bundled IGV profile missing");raw=stream.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized IGV lock");}
            Map<?,?> lock=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)));Path marker=home.resolve(".jlinalg-igv.json");if(!Files.isRegularFile(marker)||Files.size(marker)>1048576)throw new IOException("IGV admission marker missing/oversized");markerHash=hash(marker);Map<?,?> admitted=object(SimpleJson.parse(Files.readString(marker,StandardCharsets.UTF_8)));String lockHash=digest(raw);
            if(!lockHash.equals(admitted.get("runtime_lock_sha256"))||!lock.get("profile").equals(admitted.get("profile"))||!home.toString().equals(admitted.get("home")))throw new IOException("IGV marker profile/home differs from bundled identity");
            files=hashes(object(admitted.get("files")));var expected=new LinkedHashMap<>(hashes(object(lock.get("files"))));expected.put("runtime-lock.json",lockHash);if(!files.equals(expected))throw new IOException("IGV original file family differs from bundled identity");
            javaExecutable=Path.of(String.valueOf(admitted.get("java_executable"))).toAbsolutePath().normalize();javaFiles=new LinkedHashMap<>();for(var entry:object(admitted.get("java_files")).entrySet()){Path file=Path.of(String.valueOf(entry.getKey())).toAbsolutePath().normalize();String value=String.valueOf(entry.getValue());if(!value.matches("[a-f0-9]{64}"))throw new IOException("Invalid admitted Java hash");javaFiles.put(file,value);}if(javaFiles.size()<10||javaFiles.size()>300||!javaFiles.containsKey(javaExecutable))throw new IOException("Incomplete admitted Java21 core identity");
            jvmArguments=new ArrayList<>();for(Object value:list(lock.get("jvm_arguments"))){if(!(value instanceof String text)||text.contains("\n"))throw new IOException("Invalid pinned JVM option");jvmArguments.add(text);}worker=home.resolve("jlinalg-worker/IgvWorker.java");verify();session.put("runtime_profile",lock.get("profile"));session.put("runtime_home",home.toString());session.put("runtime_lock_sha256",lockHash);session.put("runtime_marker_sha256",markerHash);session.put("runtime_original_and_helper_files",files.size());session.put("runtime_java_core_files",javaFiles.size());session.put("runtime_scope",lock.get("scope"));
        }catch(IOException|RuntimeException failure){throw new Unavailable("Original IGV dependency unavailable or changed:"+failure.getMessage(),failure);}
    }
    void verify()throws Unavailable {
        try {
            if(!home.toRealPath().equals(home)||!hash(home.resolve(".jlinalg-igv.json")).equals(markerHash))throw new IOException("IGV home/marker changed");var observed=new HashSet<String>();
            try(var paths=Files.walk(home)){var iterator=paths.iterator();int count=0;while(iterator.hasNext()){Path path=iterator.next();if(++count>3000||Files.isSymbolicLink(path)||!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new IOException("Linked/aliased/excessive IGV inventory");if(Files.isRegularFile(path)&&!path.getFileName().toString().equals(".jlinalg-igv.json"))observed.add(home.relativize(path).toString().replace('\\','/'));}}
            if(!observed.equals(files.keySet()))throw new IOException("IGV private file inventory changed");for(var entry:files.entrySet()){String name=entry.getKey();if(name.startsWith("/")||name.contains("\\")||name.contains(":")||java.util.Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Unsafe IGV runtime member");Path path=home.resolve(name);if(!Files.isRegularFile(path)||!hash(path).equals(entry.getValue()))throw new IOException("IGV original/helper member changed:"+name);}
            for(var entry:javaFiles.entrySet())if(!Files.isRegularFile(entry.getKey())||Files.isSymbolicLink(entry.getKey())||!entry.getKey().toRealPath().equals(entry.getKey())||!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Admitted Java21 core file changed:"+entry.getKey());
        }catch(IOException|RuntimeException failure){throw new Unavailable("IGV runtime integrity failed:"+failure.getMessage(),failure);}
    }
    void probe(Path directory)throws Exception {
        try {
            int code=execute(null,directory,"igv-probe",90);if(code!=0)throw new Unavailable("Original IGV/Java21/graphics probe failed;inspect igv-probe.stderr");Map<?,?> result=object(SimpleJson.parse(Files.readString(directory.resolve("igv-probe.stdout"),StandardCharsets.UTF_8)));if(!"2.19.8".equals(result.get("igv"))||!"14 (14)".equals(result.get("build"))||!(result.get("java_feature") instanceof Number n)||n.intValue()!=21||!Boolean.FALSE.equals(result.get("headless"))||!Set.of("amd64","x86_64").contains(result.get("os_arch"))||!Path.of(String.valueOf(result.get("java_home"))).toAbsolutePath().normalize().equals(javaExecutable.getParent().getParent()))throw new Unavailable("Actual original IGV/Java21/graphics identity differs");session.put("runtime_actual_probe",result);
        }catch(Unavailable failure){throw failure;}catch(Exception failure){throw new Unavailable("Original IGV/Java21/graphics probe unavailable;inspect retained probe logs:"+failure.getMessage(),failure);}
    }
    int execute(Path recipe,Path directory,String stage,int timeout)throws Exception {
        if(!stage.matches("[A-Za-z0-9-]+")||timeout<1||timeout>3600)throw new IllegalArgumentException("Invalid IGV stage/timeout");verify();Path temporary=Files.createTempDirectory("jlinalg-igv-").toAbsolutePath().normalize();Path real=temporary.toRealPath();
        try {
            if(!temporary.equals(real)||!temporary.toString().chars().allMatch(c->c<128))throw new Unavailable("IGV source launcher needs an ASCII-named private system temp directory;configure java.io.tmpdir accordingly");
            Path user=Files.createDirectory(temporary.resolve("user")),scratch=Files.createDirectory(temporary.resolve("scratch"));var command=new ArrayList<>(List.of(javaExecutable.toString(),"-Xmx1024m","-Dfile.encoding=UTF-8","-Duser.language=en","-Duser.country=US","-Duser.home="+user,"-Djava.io.tmpdir="+scratch,"-Djava.net.useSystemProxies=false","-Dsun.java2d.uiScale=1","-Dsamjdk.snappy.disable=true"));command.addAll(jvmArguments);command.addAll(List.of("--module-path",home.resolve("lib").toString(),"--add-modules","org.igv",worker.toString()));
            if(recipe==null)command.add("--probe");else {Path carrier=temporary.resolve("request.json");Files.copy(recipe,carrier);if(!hash(recipe).equals(hash(carrier)))throw new IOException("Worker request changed during ASCII carrier staging");command.add(carrier.toString());session.put(stage+"_recipe_sha256",hash(carrier));}
            var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(stage+".stdout").toFile()).redirectError(directory.resolve(stage+".stderr").toFile());var env=builder.environment();for(String name:new ArrayList<>(env.keySet())){String upper=name.toUpperCase(Locale.ROOT);if(Set.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","CLASSPATH").contains(upper)||upper.startsWith("IGV_")||upper.startsWith("AWS_")||upper.startsWith("GOOGLE_")||upper.startsWith("LD_")||upper.startsWith("DYLD_"))env.remove(name);}String windows=System.getenv("SystemRoot");if(windows==null)windows=System.getenv("windir");if(windows==null)throw new Unavailable("Windows host SystemRoot is unavailable");env.put("PATH",javaExecutable.getParent()+";"+Path.of(windows).resolve("System32"));env.put("HOME",user.toString());env.put("USERPROFILE",user.toString());env.put("TEMP",scratch.toString());env.put("TMP",scratch.toString());session.put(stage+"_command",command);Process child;try{child=builder.start();}catch(IOException failure){throw new Unavailable("Cannot launch admitted IGV Java21 process",failure);}session.put(stage+"_owned_pid",child.pid());boolean timedOut=false;
            try{if(!child.waitFor(timeout,TimeUnit.SECONDS)){timedOut=true;session.put(stage+"_timed_out",true);throw new IOException("Original IGV exceeded timeout;owned process will be terminated");}session.put(stage+"_exit_code",child.exitValue());return child.exitValue();}
            finally {if(child.isAlive()){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned IGV process could not terminate;inspect PID"+child.pid());}session.put(stage+"_alive_after_cleanup",child.isAlive());session.put(stage+"_stdout_sha256",hash(directory.resolve(stage+".stdout")));session.put(stage+"_stderr_sha256",hash(directory.resolve(stage+".stderr")));if(timedOut)session.put(stage+"_status","failed-owned-timeout");verify();}
        }finally {
            // Only the exact fresh temp root created above is removed. Scientific
            // data,recipes,logs and failed outputs remain in the analysis bundle.
            if(!temporary.equals(real)||!temporary.getFileName().toString().startsWith("jlinalg-igv-"))throw new IOException("Refuse unexpected IGV temporary cleanup target");
            try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()){if(!path.normalize().startsWith(temporary))throw new IOException("Temporary path escaped owned root");Files.delete(path);}}
        }
    }
    static String recovery(){return "Required action: on Windows x86_64,where permitted run python tools/literature/install_igv.py --home NEW_ASCII_PRIVATE_HOME --cache OFFICIAL_ARCHIVE_CACHE --java FULL_JAVA21_EXECUTABLE --allow-install;add --allow-download only when permitted. Supply the admitted home to igv-tools. A full Java21 JDK and usable Windows graphics/fonts are required;no interactive window is shown. Use an ASCII java.io.tmpdir for source-launcher recipe carriers;data/output paths remain inside UTF8 JSON. If dependencies or installation are forbidden/unavailable,skip this analysis(exit3) until an administrator supplies an admitted runtime. Preserve changed homes/caches and never re-seal modified software. Input/process/timeout/audit failures return2;read retained WARNINGS.md,session.json and original logs,correct the cause and rerun to a new output directory.";}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Expected runtime metadata object");return map;}
    private static List<?> list(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Expected runtime option array");return list;}
    private static Map<String,String> hashes(Map<?,?> values)throws IOException {if(values.size()<1||values.size()>3000)throw new IOException("Invalid runtime inventory size");var result=new LinkedHashMap<String,String>();for(var entry:values.entrySet()){String key=String.valueOf(entry.getKey()),value=String.valueOf(entry.getValue());if(!value.matches("[a-f0-9]{64}"))throw new IOException("Invalid runtime member digest");result.put(key,value);}return result;}
    private static String digest(byte[] raw)throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));}
}
