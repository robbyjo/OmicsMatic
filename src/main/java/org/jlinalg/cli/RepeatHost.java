/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Declared Linux host/provider capture for runtime admission; no original scientific health is certified here. */
final class RepeatHost {
    private RepeatHost() { }
    record Limits(RepeatHostFiles.Limits dependencies, RepeatProcess.Limits process, int probes) {
        Limits {
            java.util.Objects.requireNonNull(dependencies); java.util.Objects.requireNonNull(process);
            if (probes < 2 || probes > 2048 || process.workspaceBytes() < 1048576 || process.fileCount() < 64)
                throw new IllegalArgumentException("Require explicit bounded host-probe and retained-diagnostic capacities");
        }
    }
    record PythonDiscovery(String stdlib, List<String> paths, String version) {
        PythonDiscovery { paths = List.copyOf(paths); }
    }
    record Snapshot(String profile, String payloadHome, String payloadLockSha256, List<String> perlRoots,
            List<String> pythonPaths, String pythonVersion, Map<String,String> platform,
            RepeatHostFiles.Snapshot files, Map<String,RepeatHostElf.Image> images,
            Map<String,RepeatHostElf.Report> bindings) {
        Snapshot {
            perlRoots = List.copyOf(perlRoots); pythonPaths = List.copyOf(pythonPaths);
            platform = ordered(platform); images = ordered(images); bindings = ordered(bindings);
        }
        boolean sameContents(Snapshot other) {
            return profile.equals(other.profile) && payloadHome.equals(other.payloadHome)
                && payloadLockSha256.equals(other.payloadLockSha256) && perlRoots.equals(other.perlRoots)
                && pythonPaths.equals(other.pythonPaths) && pythonVersion.equals(other.pythonVersion)
                && platform.equals(other.platform) && files.sameContents(other.files)
                && images.equals(other.images) && bindings.equals(other.bindings);
        }
    }
    record Result(Path work, Snapshot snapshot, List<RepeatProcess.Result> probes, String snapshotSha256) {
        Result { probes = List.copyOf(probes); }
    }
    private static final String PERL_ROOTS = "for my $p (@INC) { die 'Unsupported reference in default INC' if ref($p); print $p,qq(\\n); }";
    private static final String PYTHON_ROOTS = "import json,sys,sysconfig;print(json.dumps({'stdlib':sysconfig.get_path('stdlib'),'paths':sys.path,'version':sys.version.split()[0]},ensure_ascii=False))";
    private static final List<String> REQUIRED = List.of("/usr/bin/perl", "/usr/bin/env", "/bin/sh", "/usr/bin/sort", "/usr/bin/rm",
        "/usr/bin/setsid", "/usr/bin/prlimit", "/usr/bin/ldd", "/bin/bash");
    private static final List<String> SUPPLEMENTAL = List.of("cp", "cat", "gzip", "gunzip", "grep", "head", "tail", "wc", "mv", "which", "date", "df", "cut", "ln", "readlink", "touch", "pod2text");
    private static final List<String> CONFIG_FILES = List.of("/etc/ld.so.cache", "/etc/ld.so.conf", "/etc/ld.so.preload",
        "/etc/nsswitch.conf", "/etc/hosts", "/etc/resolv.conf", "/etc/gai.conf", "/etc/host.conf", "/etc/localtime",
        "/etc/passwd", "/etc/group", "/etc/os-release", "/etc/ssl/openssl.cnf", "/usr/lib/ssl/openssl.cnf", "/usr/lib/locale/locale-archive", "/usr/share/locale/locale.alias", "/etc/ncbi.ini", "/etc/.ncbirc", "/etc/ncbirc",
        "/usr/etc/.ncbirc", "/usr/local/etc/.ncbirc", "/etc/makeblastdb.ini", "/etc/rmblastn.ini", "/etc/toolkitrc", "/etc/selinux/config", "/is.lib",
        "/lib/ld-linux.so.2", "/lib64/ld-linux-x86-64.so.2", "/libx32/ld-linux-x32.so.2");
    private static final List<String> EXTRA_TREES = List.of("/etc/ld.so.conf.d", "/etc/ssl/openssl.cnf.d",
        "/usr/lib/locale/C.utf8", "/usr/lib/locale/C.UTF-8", "/usr/lib/x86_64-linux-gnu/ossl-modules",
        "/usr/lib/x86_64-linux-gnu/glibc-hwcaps", "/usr/lib64/glibc-hwcaps", "/usr/lib/x86_64-linux-gnu/gconv");
    private static final Set<Path> TREE_TARGETS = paths(List.of("/etc/perl", "/etc/python3.12", "/usr/lib", "/usr/lib64",
        "/usr/share", "/usr/local/lib", "/usr/local/lib64", "/usr/local/share", "/usr/bin/lib", "/etc/ld.so.conf.d", "/etc/ssl/openssl.cnf.d"));
    private static final Set<Path> SYSTEM_FILE_TARGETS = paths(List.of("/usr/lib", "/usr/lib64", "/usr/share", "/usr/local/lib",
        "/usr/local/lib64", "/usr/local/share", "/usr/bin", "/etc", "/usr/etc", "/usr/local/etc", "/lib", "/lib64", "/libx32",
        "/run/systemd/resolve", "/run/resolvconf", "/mnt/wsl", "/is.lib", "/log", "/var/log"));

    static Result capture(RepeatPayload.Context payload, Path suppliedWork, Limits limits, Runnable check) throws IOException {
        requirePlatform(); Path work = suppliedWork.toAbsolutePath().normalize(); RepeatPayload.originalPath(work);
        if (work.getParent() == null || !work.getParent().toRealPath().equals(work.getParent())
                || work.startsWith(payload.home()) || payload.home().startsWith(work))
            throw new IOException("Host capture requires a canonical NEW directory disjoint from the clean payload");
        check.run(); payload.verify(check);
        for (String helper : REQUIRED)
            if (!Files.isRegularFile(Path.of(helper)) || !Files.isExecutable(Path.of(helper)))
                throw new RepeatProcess.UnavailableDependency("Missing or forbidden declared host helper: " + helper);
        Files.createDirectory(work);
        var run = new Capture(payload, work, limits, check);
        try { return run.execute(); }
        catch (IOException | RuntimeException failure) {
            String message = failure.toString(); if (message.length() > 8192) message = message.substring(0,8192);
            try {
                Files.writeString(work.resolve("HOST_FAILED.txt"), "HOST CAPTURE FAILED: no scientific runtime admitted.\n" + message
                    + "\nRequired action: retain probe stdout/stderr,process ledgers and this directory. Correct the named host,provider,permission,integrity or capacity problem and retry to a NEW directory. Where installation is forbidden,skip the affected analysis;never substitute a provider or edition. No installation is performed by host capture.\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
    static void verify(Snapshot expected, RepeatPayload.Context payload, Path newWork, Limits limits, Runnable check) throws IOException {
        Result actual = capture(payload,newWork,limits,check);
        if (!expected.sameContents(actual.snapshot())) throw new IOException("Re-derived host/provider module paths,files,platform or library bindings differ");
    }
    private static final class Capture {
        final RepeatPayload.Context payload;
        final Path work, metadata, probeRoot;
        final Limits limits;
        final Runnable check;
        final Set<Path> fileTargets;
        final List<RepeatProcess.Result> probes = new ArrayList<>();
        final List<Path> accounted = new ArrayList<>();
        final LinkedHashSet<Path> rootPaths = new LinkedHashSet<>(), filePaths = new LinkedHashSet<>(),nodePaths=new LinkedHashSet<>();
        RepeatHostFiles.Snapshot launchers;
        Capture(RepeatPayload.Context payload, Path work, Limits limits, Runnable check) throws IOException {
            this.payload=payload;this.work=work;this.limits=limits;this.check=check;
            metadata=Files.createDirectory(work.resolve("metadata"));probeRoot=Files.createDirectory(work.resolve("probes"));accounted.add(metadata);
            var targets=new java.util.HashSet<>(SYSTEM_FILE_TARGETS);targets.add(payload.home());
            if(payload.python()!=null)targets.add(payload.python().getParent().getParent().resolve("pyvenv.cfg"));
            fileTargets=Set.copyOf(targets);
        }
        Result execute() throws IOException {
            launchers=RepeatHostFiles.capture(List.of(),REQUIRED.stream().map(Path::of).toList(),TREE_TARGETS,fileTargets,limits.dependencies(),check);
            filePaths.addAll(REQUIRED.stream().map(Path::of).toList());
            for(String name:SUPPLEMENTAL)filePaths.add(Path.of("/usr/bin/"+name));
            CONFIG_FILES.forEach(name->filePaths.add(Path.of(name)));EXTRA_TREES.forEach(name->rootPaths.add(Path.of(name)));
            nodePaths.add(Path.of("/log/fallback"));nodePaths.add(Path.of("/log/srv"));
            payload.external().values().forEach(value->filePaths.add(value.path()));
            for(String name:payload.files().keySet())if(name.startsWith("bin/")||name.contains(".so"))filePaths.add(payload.home().resolve(name));
            // Record all installed NSS providers, including providers that are loaded lazily rather than by DT_NEEDED.
            for(String directory:List.of("/usr/lib/x86_64-linux-gnu","/usr/lib64")){
                Path path=Path.of(directory);
                if(Files.isDirectory(path))try(var members=Files.newDirectoryStream(path,"libnss_*.so*")){for(Path member:members){check.run();filePaths.add(member);}}
            }
            var perl=process("perl-includes",List.of("/usr/bin/perl","-e",PERL_ROOTS),false,null);
            List<String> perlRoots=perlRoots(read(perl.stdout()));perlRoots.forEach(name->rootPaths.add(Path.of(name)));
            List<String> pythonPaths=List.of();String pythonVersion="";
            if(payload.python()!=null){
                Path parent=payload.python().getParent();
                for(String name:List.of("pyvenv.cfg","pybuilddir.txt","Modules/Setup.local","lib/python312.zip"))filePaths.add(parent.resolve(name));
                filePaths.add(payload.python().resolveSibling(payload.python().getFileName()+"._pth"));filePaths.add(parent.getParent().resolve("pyvenv.cfg"));
                rootPaths.add(parent.resolve("lib/python3.12"));
                var python=process("python-paths",List.of(payload.python().toString(),"-I","-S","-B","-c",PYTHON_ROOTS),false,null);
                PythonDiscovery discovery=pythonPaths(read(python.stdout()));pythonPaths=discovery.paths();pythonVersion=discovery.version();
                rootPaths.add(Path.of(discovery.stdlib()));
                for(String name:pythonPaths){Path path=Path.of(name);if(name.endsWith(".zip"))filePaths.add(path);else rootPaths.add(path);}
            }
            var initial=files();write("before-files.json",RepeatHostFiles.bytes(initial,limits.dependencies()));
            var pending=new TreeSet<String>();var images=new TreeMap<String,RepeatHostElf.Image>();var bindings=new TreeMap<String,RepeatHostElf.Report>();var seen=new java.util.HashSet<String>();
            for(var entry:initial.files().values())if(entry.file()!=null)pending.add(entry.file().realPath());
            for(var tree:initial.trees().values())for(var entry:tree.values())if(entry.file()!=null&&Path.of(entry.file().realPath()).getFileName().toString().contains(".so"))pending.add(entry.file().realPath());
            while(!pending.isEmpty()){
                check.run();String name=pending.pollFirst();if(!seen.add(name))continue;Path binary=Path.of(name);
                RepeatHostElf.Image image=RepeatHostElf.image(binary,limits.dependencies().fileBytes(),check);if(image==null)continue;
                images.put(name,image);
                if(image.interpreter()!=null)addProvider(Path.of(image.interpreter()),pending);
                var process=process("ldd",List.of(),true,binary);
                var report=RepeatHostElf.bindings(image,process.exitCode(),read(process.stdout()),read(process.stderr()));bindings.put(name,report);
                for(var binding:report.bindings())addProvider(Path.of(binding.path()),pending);
            }
            var after=files();requirePreserved(initial,after);payload.verify(check);
            RepeatHostFiles.verify(launchers,TREE_TARGETS,fileTargets,limits.dependencies(),check);
            var snapshot=new Snapshot(payload.profile(),payload.home().toString(),payload.lockSha256(),perlRoots,pythonPaths,pythonVersion,platform(),after,images,bindings);
            byte[] raw=RepeatBundle.json(snapshot);if(raw.length>limits.dependencies().metadataBytes())throw new IOException("Complete host binding snapshot exceeds its metadata byte capacity");
            write("snapshot.json",raw);write("capture.json",RepeatBundle.json(Map.of("status","DECLARED_HOST_CAPTURE_ONLY","scientific_runtime_admission_complete",false,
                "public_original_SEARCH_available",false,"probe_count",probes.size(),"profile",payload.profile(),"snapshot_sha256",AdmixtureInputs.digest(raw),
                "scope","Default Perl/Python import paths and complete declared module trees,helpers,configuration/absence states,ELF providers and every parsed ldd row. File hashes/metadata only;configuration contents are not copied. Original scientific health is a separate gate.",
                "wall_policy","Process capacities apply per probe;file fingerprinting and the whole capture have no inferred global wall deadline.",
                "capacity_policy","Declared read-only dependency logical bytes and retained owned probe/metadata workspace have separate explicit limits. Full file-alias bytes count;the workspace reserves32KiB/16members for diagnostics.")));
            budget();check.run();return new Result(work,snapshot,probes,AdmixtureInputs.digest(raw));
        }
        private RepeatHostFiles.Snapshot files() throws IOException {
            return RepeatHostFiles.capture(List.copyOf(rootPaths),List.copyOf(filePaths),List.copyOf(nodePaths),TREE_TARGETS,fileTargets,limits.dependencies(),check);
        }
        private void addProvider(Path path, Set<String> pending) throws IOException {
            // Resolve the original spelling before normalization: symlinks make lexical '..' collapsing unsafe.
            Path real=path.toRealPath();if(!Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS))throw new IOException("Library provider is not a regular file: "+path);
            if(fileTargets.stream().noneMatch(real::startsWith))throw new IOException("Library provider resolves outside the declared host/payload roots: "+path);
            filePaths.add(path.normalize().equals(path)?path:real);
            pending.add(real.toString());
        }
        private RepeatProcess.Result process(String label,List<String> command,boolean library,Path binary) throws IOException {
            if(probes.size()>=limits.probes())throw new IOException("Complete host probe capacity exceeded");
            Path directory=probeRoot.resolve(String.format(Locale.ROOT,"%04d-%s",probes.size(),label));
            RepeatProcess.Check integrity=()->RepeatHostFiles.verify(launchers,TREE_TARGETS,fileTargets,limits.dependencies(),check);
            RepeatProcess.Limits p=limits.process();var reduced=new RepeatProcess.Limits(p.wallMillis(),p.addressSpaceBytes(),p.cpuSeconds(),p.fileBytes(),p.openFiles(),
                p.workspaceBytes()-32768,p.fileCount()-16,p.processes(),p.threads(),p.sampledRssBytes(),p.monitorMillis());
            var result=library?RepeatProcess.inspectLibraries(binary,payload.environment(directory),directory,reduced,integrity,check,List.copyOf(accounted))
                :RepeatProcess.executeOwned(command,payload.environment(directory),directory,reduced,integrity,check,List.copyOf(accounted));
            probes.add(result);accounted.add(directory);budget();return result;
        }
        private byte[] read(Path path) throws IOException {return AdmixtureInputs.raw(path,1048576);}
        private void write(String name,byte[] raw) throws IOException {
            var used=budget();
            if(raw.length>limits.dependencies().metadataBytes()||Math.addExact(used.bytes(),raw.length+32768L)>limits.process().workspaceBytes()
                    ||used.members()+17>limits.process().fileCount())throw new IOException("Complete retained host workspace/metadata capacity exceeded");
            Files.write(metadata.resolve(name),raw,StandardOpenOption.CREATE_NEW);budget();
        }
        private RepeatBundle.Inventory budget() throws IOException {
            return RepeatBundle.inventory(work,new RepeatBundle.Limits(limits.process().workspaceBytes(),limits.process().fileCount()),false,check);
        }
    }
    private static void requirePreserved(RepeatHostFiles.Snapshot before,RepeatHostFiles.Snapshot after) throws IOException {
        if(!before.requestedRoots().equals(after.requestedRoots())||!before.roots().equals(after.roots())||!before.trees().equals(after.trees())||!before.nodes().equals(after.nodes()))
            throw new IOException("Complete declared module-root population changed during binding discovery");
        for(var entry:before.files().entrySet())if(!entry.getValue().equals(after.files().get(entry.getKey())))throw new IOException("Host/provider file changed during binding discovery: "+entry.getKey());
    }
    static List<String> perlRoots(byte[] raw) throws IOException {
        String text=utf8(raw);if(!text.endsWith("\n"))throw new IOException("Perl default-module output is not complete");
        String[] lines=text.substring(0,text.length()-1).split("\n",-1);
        if(lines.length<1||lines.length>64)throw new IOException("Perl default-module root capacity exceeded");
        for(String line:lines)linuxPath(line);return List.of(lines);
    }
    static PythonDiscovery pythonPaths(byte[] raw) throws IOException {
        if(raw.length>1048576)throw new IOException("Complete Python import discovery exceeds its byte capacity");
        Map<?,?> document=RepeatBundle.object(raw);
        if(!document.keySet().equals(Set.of("stdlib","paths","version"))||!(document.get("stdlib") instanceof String stdlib)
                ||!(document.get("paths") instanceof List<?> paths)||!(document.get("version") instanceof String version)
                ||!version.equals("3.12.3")||paths.isEmpty()||paths.size()>64)throw new IOException("Unsupported pinned Python default import-path discovery");
        linuxPath(stdlib);var result=new ArrayList<String>();
        for(Object item:paths){if(!(item instanceof String text))throw new IOException("Python import paths must be explicit strings");linuxPath(text);result.add(text);}
        return new PythonDiscovery(stdlib,result,version);
    }
    private static void linuxPath(String value) throws IOException {
        if(!value.startsWith("/")||value.length()<2||value.length()>4096||value.endsWith("/")||value.chars().anyMatch(c->c<32||c==127)
                ||java.util.Arrays.stream(value.substring(1).split("/",-1)).anyMatch(c->c.isEmpty()||c.equals(".")||c.equals("..")))throw new IOException("Require complete normalized absolute Linux import paths");
    }
    private static String utf8(byte[] raw) throws IOException {
        if(raw.length>1048576)throw new IOException("Complete host discovery text exceeds its capacity");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
    }
    private static Map<String,String> platform() throws IOException {
        var result=new TreeMap<String,String>();result.put("os","Linux");result.put("architecture","x86_64");result.put("kernel",System.getProperty("os.version"));
        int count=0;
        try(var reader=Files.newBufferedReader(Path.of("/proc/self/status"),StandardCharsets.UTF_8)){
            String line;while((line=reader.readLine())!=null){if((count+=line.length())>65536)throw new IOException("Complete process identity exceeds its capacity");
                for(String field:List.of("Uid:","Gid:","Groups:","Cpus_allowed_list:"))if(line.startsWith(field)){
                    String value=line.substring(field.length()).strip().replaceAll("[ \\t]+"," ");
                    boolean valid=field.equals("Cpus_allowed_list:")?value.matches("[0-9]+(?:[-,][0-9]+)*")
                        :field.equals("Groups:")?(value.isEmpty()||value.matches("[0-9]+(?: [0-9]+)*")):value.matches("[0-9]+(?: [0-9]+){3}");
                    if(!valid)throw new IOException("Invalid process identity field");
                    if(result.putIfAbsent(field,value)!=null)throw new IOException("Duplicate process identity field");
                }
            }
        }
        if(!result.keySet().containsAll(List.of("Uid:","Gid:","Groups:","Cpus_allowed_list:")))throw new IOException("Missing complete readable /proc process identity");
        var cpu=new TreeSet<String>();count=0;
        try(var reader=Files.newBufferedReader(Path.of("/proc/cpuinfo"),StandardCharsets.UTF_8)){
            String line;while((line=reader.readLine())!=null){if((count+=line.length())>4194304)throw new IOException("CPU identity exceeds its complete text capacity");
                int colon=line.indexOf(':');if(colon<0)continue;String field=line.substring(0,colon).strip();
                if(Set.of("vendor_id","cpu family","model","model name","stepping","microcode","flags","bugs","address sizes").contains(field))
                    cpu.add(field+"="+line.substring(colon+1).strip().replaceAll("[ \\t]+"," "));
            }
        }
        if(cpu.stream().noneMatch(value->value.startsWith("flags="))||cpu.stream().noneMatch(value->value.startsWith("model name=")))throw new IOException("Missing complete x86 CPU identity/features");
        result.put("cpu_profile_sha256",AdmixtureInputs.digest(String.join("\n",cpu).getBytes(StandardCharsets.UTF_8)));return result;
    }
    private static void requirePlatform() throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
            throw new RepeatProcess.UnavailableDependency("Original host/provider admission requires Linux x86_64 Java;use Linux Java inside WSL on Windows");
    }
    private static Set<Path> paths(List<String> values){var result=new java.util.HashSet<Path>();values.forEach(value->result.add(Path.of(value)));return Set.copyOf(result);}
    private static <T> Map<String,T> ordered(Map<String,T> values){return Collections.unmodifiableMap(new TreeMap<>(values));}
}
