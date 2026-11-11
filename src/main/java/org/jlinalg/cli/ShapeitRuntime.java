/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Exact controlled common-stage runtime integrity. Every analysis still needs scientific admission. */
final class ShapeitRuntime {
    private ShapeitRuntime() { }
    static final String MARKER=".omicsmatic-shapeit.json";
    static final String PROFILE="shapeit5-c34d-common-linux-x86_64";
    enum Program { COMMON("phase_common"), JOIN("joined-wire");final String file;Program(String file){this.file=file;} }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record External(String requestedPath,String realPath,ShapeitRawIndex.Identity identity) { }
    record Context(Path home,String profile,String version,ShapeitRawIndex.Identity marker,
                   Map<String,ShapeitRawIndex.Identity> files,Map<String,External> helpers) {
        Context {files=Map.copyOf(files);helpers=Map.copyOf(helpers);}
        void verify() throws IOException {
            try{if(!equals(open(home)))throw new IOException("SHAPEIT runtime admission changed during analysis");}
            catch(Unavailable missing){throw new IOException("Admitted SHAPEIT runtime became unavailable; no result is admitted",missing);}
        }
        List<String> command(Program program){
            // Disable the cache and each locked object's RPATH/RUNPATH. The loader itself is private and pinned.
            var inhibit=new ArrayList<String>();inhibit.add(""); // The loader represents the main executable by an empty object name.
            for(String path:files.keySet())if(path.startsWith("bin/")||path.startsWith("lib/")){inhibit.add(home.resolve(path).toString());inhibit.add(Path.of(path).getFileName().toString());}
            Collections.sort(inhibit);
            return List.of(home.resolve("lib/ld-linux-x86-64.so.2").toString(),"--inhibit-cache","--glibc-hwcaps-mask","",
                    "--inhibit-rpath",String.join(":",inhibit),"--library-path",home.resolve("lib").toString(),home.resolve("bin/"+program.file).toString());
        }
        List<String> listCommand(Program program){var result=new ArrayList<>(command(program));result.add(result.size()-1,"--list");return List.copyOf(result);}
        void checkClosure(Program program,Path stdout)throws IOException{
            var expected=new HashSet<String>();Object raw=object(LOCK.value().get("bindings")).get(program.file);
            if(!(raw instanceof List<?> names))throw new IOException("Missing locked SHAPEIT loader bindings");for(Object name:names)expected.add(name.toString());
            var seen=new HashSet<String>();String text=utf8(bounded(stdout,1048576));
            var pattern=java.util.regex.Pattern.compile("\\s*(?:\\S+\\s+=>\\s+)?(/[^\\s]+)\\s+\\(0x[0-9a-fA-F]+\\)\\s*");
            for(String line:text.split("\n")){
                if(line.isBlank()||line.trim().matches("linux-vdso\\.so\\.1 \\(0x[0-9a-fA-F]+\\)"))continue;
                var match=pattern.matcher(line);if(!match.matches())throw new IOException("Unrecognized SHAPEIT loader dependency result: "+line);
                Path path=Path.of(match.group(1));String name=path.getFileName().toString();
                if(!path.equals(home.resolve("lib/"+name))||!seen.add(name)||!expected.contains(name))throw new IOException("SHAPEIT dependency escaped or differs from the private locked closure: "+line);
            }
            if(!seen.equals(expected))throw new IOException("Incomplete SHAPEIT private loader closure");
        }
        Map<String,String> environment(){return Map.ofEntries(Map.entry("PATH","/usr/bin:/bin"),Map.entry("LANG","C"),Map.entry("LC_ALL","C"),
                Map.entry("LD_LIBRARY_PATH",home.resolve("lib").toString()),Map.entry("LD_BIND_NOW","1"),Map.entry("HTS_PATH",home.resolve("share").toString()),
                Map.entry("OMP_NUM_THREADS","1"),Map.entry("OPENBLAS_NUM_THREADS","1"),Map.entry("MKL_NUM_THREADS","1"));}
    }
    private record Lock(Map<?,?> value,String sha256,String installerSha256) { }
    private static final Lock LOCK=load();
    private static Lock load(){
        try{
            byte[] raw=resource("/org/jlinalg/omics/shapeit-runtime-lock.json");
            return new Lock(object(SimpleJson.parse(utf8(raw))),digest(raw),digest(resource("/org/jlinalg/omics/shapeit-runtime-setup/install_shapeit.py")));
        }catch(IOException invalid){throw new ExceptionInInitializerError(invalid);}
    }
    static Context open(Path supplied) throws IOException {
        String arch=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(arch))throw new Unavailable("SHAPEIT source c34d requires Linux x86_64 Java; on Windows use Linux Java inside WSL");
        host();Path home=supplied.toAbsolutePath().normalize();
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Missing private SHAPEIT runtime; install the exact permitted profile before dependent analysis");
        canonical(home);if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new IOException("SHAPEIT runtime home is not a private directory");
        if(home.toString().codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||c==':'))throw new IOException("SHAPEIT managed runtime path cannot contain loader-list whitespace, control characters or colons");
        Path marker=home.resolve(MARKER);if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Unmanaged SHAPEIT home; preserve it and install to a fresh private directory");
        try{
            var markerIdentity=ShapeitRawIndex.identity(marker,1048576,()->{});Map<?,?> value=object(SimpleJson.parse(utf8(bounded(marker,1048576))));
            for(var entry:Map.of("status","installed-exact-runtime-only","tool","SHAPEIT","profile",PROFILE,"lock_sha256",LOCK.sha256(),"installer_sha256",LOCK.installerSha256(),"home",home.toString()).entrySet())
                if(!entry.getValue().equals(value.get(entry.getKey())))throw new IOException("SHAPEIT managed profile/installer/identity differs: "+entry.getKey());
            if(!System.getProperty("os.version").equals(value.get("kernel")))throw new Unavailable("SHAPEIT admitted kernel changed; preserve this home and adopt a fresh private home on the current host");
            if(!Boolean.FALSE.equals(value.get("scientific_analysis_admitted")))throw new IOException("Installation cannot claim scientific analysis admission");
            if(!PROFILE.equals(LOCK.value().get("profile")))throw new IOException("Unsupported SHAPEIT lock profile");
            Map<?,?> expected=object(LOCK.value().get("files")),recorded=object(value.get("files"));
            if(!recorded.equals(expected)||expected.size()!=32)throw new IOException("SHAPEIT complete locked file inventory differs");
            var files=new TreeMap<String,ShapeitRawIndex.Identity>();
            for(var entry:expected.entrySet()){
                String name=entry.getKey().toString();if(!name.matches("(?:bin|lib|share)/[A-Za-z0-9._+-]+"))throw new IOException("Unsafe SHAPEIT locked member");
                var identity=identity(object(entry.getValue()));verifyFile(home.resolve(name),identity);files.put(name,identity);
            }
            inventory(home,files.keySet());
            for(String executable:List.of("bin/phase_common","bin/joined-wire","lib/ld-linux-x86-64.so.2"))if(!Files.isExecutable(home.resolve(executable)))throw new Unavailable("SHAPEIT execution is forbidden: "+executable);
            var helpers=new TreeMap<String,External>();Map<?,?> recordedHelpers=object(value.get("helpers"));
            if(!recordedHelpers.keySet().equals(Set.of("/usr/bin/setsid","/usr/bin/prlimit")))throw new IOException("SHAPEIT owned-process helper inventory differs");
            for(var entry:recordedHelpers.entrySet()){
                String requested=entry.getKey().toString();Map<?,?> detail=object(entry.getValue());Path path=Path.of(requested),real=path.toRealPath();
                if(!real.toString().equals(detail.get("real_path")))throw new IOException("SHAPEIT system helper target changed");
                var identity=identity(detail);verifyFile(real,identity);if(!Files.isExecutable(real))throw new Unavailable("SHAPEIT owned-process helper execution is forbidden: "+requested);
                helpers.put(requested,new External(requested,real.toString(),identity));
            }
            if(!markerIdentity.equals(ShapeitRawIndex.identity(marker,1048576,()->{})))throw new IOException("SHAPEIT marker changed during integrity admission");
            return new Context(home,PROFILE,LOCK.value().get("version").toString(),markerIdentity,files,helpers);
        }catch(AccessDeniedException forbidden){throw new Unavailable("SHAPEIT runtime access is forbidden; skip dependent analysis",forbidden);}
        catch(IllegalArgumentException invalid){throw new IOException("Invalid SHAPEIT runtime metadata",invalid);}
    }
    private static void host() throws IOException {
        if(Files.exists(Path.of("/etc/ld.so.preload"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("System dynamic-loader preload configuration is outside this profile; use a separately admitted isolated environment");
        String cpu=new String(bounded(Path.of("/proc/cpuinfo"),4194304),StandardCharsets.US_ASCII);int processors=0;
        for(String line:cpu.split("\n"))if(line.startsWith("flags")&&line.contains(":")){
            var flags=new HashSet<>(List.of(line.substring(line.indexOf(':')+1).trim().split("\\s+")));if(!flags.containsAll(List.of("avx2","fma")))throw new Unavailable("SHAPEIT controlled build requires AVX2 and FMA on every admitted processor");processors++;
        }
        if(processors==0)throw new Unavailable("Cannot establish required SHAPEIT CPU features");
    }
    private static ShapeitRawIndex.Identity identity(Map<?,?> value) throws IOException {
        Object size=value.get("bytes"),sha=value.get("sha256");
        if(!(size instanceof Number n)||!Double.isFinite(n.doubleValue())||n.longValue()!=n.doubleValue()||n.longValue()<1||n.longValue()>100000000
                ||!(sha instanceof String hash)||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid SHAPEIT runtime member fingerprint");
        return new ShapeitRawIndex.Identity(n.longValue(),(String)sha);
    }
    private static void verifyFile(Path path,ShapeitRawIndex.Identity expected) throws IOException {
        canonical(path);if(!expected.equals(ShapeitRawIndex.identity(path,expected.bytes(),()->{})))throw new IOException("SHAPEIT runtime member changed: "+path);
    }
    private static void inventory(Path home,Set<String> files) throws IOException {
        var expected=new HashSet<>(files);expected.add(MARKER);var seen=new HashSet<String>();int[] count={0};
        Files.walkFileTree(home,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{
                if(++count[0]>64||!Set.of("","bin","lib","share").contains(home.relativize(path).toString()))throw new IOException("Unmanaged SHAPEIT runtime directory");canonical(path);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                String name=home.relativize(path).toString();if(++count[0]>64||!attrs.isRegularFile()||!expected.contains(name))throw new IOException("Unmanaged/linked SHAPEIT runtime member");canonical(path);seen.add(name);return FileVisitResult.CONTINUE;
            }
        });if(!seen.equals(expected))throw new IOException("Incomplete SHAPEIT runtime inventory");
    }
    private static void canonical(Path path)throws IOException{if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toRealPath().equals(path))throw new IOException("Linked or unnormalized SHAPEIT runtime path: "+path);}
    private static byte[] bounded(Path path,int maximum)throws IOException{
        try(var in=Files.newInputStream(path)){byte[] raw=in.readNBytes(maximum+1);if(raw.length>maximum)throw new IOException("SHAPEIT metadata exceeds byte capacity");return raw;}
    }
    private static byte[] resource(String name)throws IOException{
        try(var in=ShapeitRuntime.class.getResourceAsStream(name)){if(in==null)throw new IOException("Missing bundled SHAPEIT runtime setup resource: "+name);byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized SHAPEIT setup resource");return raw;}
    }
    private static String utf8(byte[] raw)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    private static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Require SHAPEIT runtime JSON object");return map;}
    private static String digest(byte[] raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
