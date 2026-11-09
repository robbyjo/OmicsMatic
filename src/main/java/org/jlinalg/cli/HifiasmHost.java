/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Declared native Linux ELF/helper/loader census, without unrelated Perl or R prerequisites. */
final class HifiasmHost {
    private HifiasmHost(){ }
    private static final List<Path> HELPERS=List.of("/usr/bin/setsid","/usr/bin/prlimit","/usr/bin/ldd","/usr/bin/env","/bin/bash","/bin/sh").stream().map(Path::of).toList();
    private static final List<Path> CONFIG=List.of("/etc/ld.so.cache","/etc/ld.so.conf","/etc/ld.so.preload").stream().map(Path::of).toList();
    private static final Set<Path> SYSTEM=Set.of(Path.of("/usr/lib"),Path.of("/usr/lib64"),Path.of("/lib"),Path.of("/lib64"),Path.of("/usr/bin"),Path.of("/bin"),Path.of("/etc"));
    private static final RepeatHostFiles.Limits CAP=new RepeatHostFiles.Limits(1073741824,8589934592L,10000,64,16777216);
    private static final RsemProcess.Limits PROBE=new RsemProcess.Limits(60000,2147483648L,60,1048576,256,67108864,1024,16,128,1073741824,100);
    static Map<String,String> environment(){return Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","TZ","UTC","OMP_NUM_THREADS","1");}
    static void platform()throws IOException{
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new RsemProcess.UnavailableDependency("hifiasm assembly requires Linux x86_64 Java, including WSL");
        for(Path file:HELPERS)if(!Files.isExecutable(file))throw new RsemProcess.UnavailableDependency("Permitted process/ELF inspection helper is unavailable: "+file);
        Path preload=Path.of("/etc/ld.so.preload");if(Files.exists(preload)&&Files.size(preload)!=0)throw new RsemProcess.UnavailableDependency("Nonempty system ld.so.preload is outside this original-runtime profile; use a compatible permitted host");
    }
    /** Capture NEW probe logs and every declared helper, provider, search candidate and absence state. */
    static Map<String,Object> capture(Path binary,Path work,RsemProcess.Check integrity)throws IOException{
        platform();require(binary.isAbsolute()&&binary.equals(binary.toRealPath())&&Files.isRegularFile(binary,LinkOption.NOFOLLOW_LINKS),"Require canonical original binary");Files.createDirectory(work);
        var targets=new java.util.HashSet<Path>(SYSTEM);targets.add(binary.getParent());var allowed=Set.copyOf(targets);var files=new LinkedHashSet<Path>(HELPERS);files.addAll(CONFIG);files.add(binary);var roots=new LinkedHashSet<Path>();roots.add(Path.of("/etc/ld.so.conf.d"));var nodes=new LinkedHashSet<Path>();
        var initial=RepeatHostFiles.capture(List.of(),new ArrayList<>(files),allowed,allowed,CAP,()->{});var pending=new TreeSet<String>();for(var entry:initial.files().values())if(entry.file()!=null)pending.add(entry.file().realPath());
        var images=new TreeMap<String,RsemElf.Image>();var bindings=new TreeMap<String,RsemElf.Report>();int probes=0;
        while(!pending.isEmpty()){
            integrity.verify();String name=pending.pollFirst();if(images.containsKey(name))continue;Path imagePath=Path.of(name);RsemElf.Image image=RsemElf.image(imagePath,CAP.fileBytes(),()->{});if(image==null)continue;require(images.size()<128,"ELF provider capacity exceeded");images.put(name,image);
            var directories=new LinkedHashSet<Path>(RsemElf.searchDirectories(imagePath,image));for(String standard:List.of("/usr/lib/x86_64-linux-gnu","/usr/lib64","/lib/x86_64-linux-gnu","/lib64","/usr/lib","/lib"))directories.add(Path.of(standard));
            for(Path directory:directories){require(allowed.stream().anyMatch(directory::startsWith),"ELF search directory outside declared providers");nodes.add(directory);roots.add(directory.resolve("glibc-hwcaps"));for(String needed:image.neededNames()){require(needed.matches("[A-Za-z0-9_.+\\-]{1,255}"),"Non-SONAME ELF dependency is outside this profile");files.add(directory.resolve(needed));}}
            if(image.interpreter()!=null)add(Path.of(image.interpreter()),files,pending,allowed);
            var probe=RsemProcess.inspectLibraries(imagePath,environment(),work.resolve("ldd-"+(++probes)),PROBE,()->{integrity.verify();RepeatHostFiles.verify(initial,allowed,allowed,CAP,()->{});},()->{},List.of());var report=RsemElf.bindings(image,probe.exitCode(),NextflowFiles.bytes(probe.stdout(),1048576),NextflowFiles.bytes(probe.stderr(),1048576));bindings.put(name,report);for(var binding:report.bindings())add(Path.of(binding.path()),files,pending,allowed);
        }
        var complete=RepeatHostFiles.capture(roots.stream().sorted().toList(),files.stream().sorted().toList(),nodes.stream().sorted().toList(),allowed,allowed,CAP,()->{});RsemHost.requirePreserved(initial,complete);RepeatHostFiles.verify(complete,allowed,allowed,CAP,()->{});integrity.verify();
        var fileDocument=object(NextflowParameters.parse(NextflowFiles.utf8(RepeatHostFiles.bytes(complete,CAP)),16777216,1000000));fileDocument.remove("physicalHashes");var identity=new LinkedHashMap<String,Object>();identity.put("files",fileDocument);identity.put("images",images);identity.put("bindings",bindings);identity.put("platform",Map.of("os",System.getProperty("os.name"),"architecture",System.getProperty("os.arch"),"release",System.getProperty("os.version")));
        var result=HifiasmJson.document(identity);NextflowFiles.writeJson(work.resolve("host-binding.json"),result);return result;
    }
    private static void add(Path path,Set<Path> files,Set<String> pending,Set<Path> allowed)throws IOException{Path real=path.toRealPath();require(Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS)&&allowed.stream().anyMatch(real::startsWith),"ELF provider outside declared regular-file roots: "+path);files.add(path);pending.add(real.toString());}
    static void verifyFiles(Map<String,Object> host,Path binary)throws IOException{
        var expected=object(host.get("files"));var targets=new java.util.HashSet<Path>(SYSTEM);targets.add(binary.getParent());var allowed=Set.copyOf(targets);
        var actual=RepeatHostFiles.capture(paths(expected.get("requestedRoots")),paths(expected.get("requestedFiles")),paths(expected.get("requestedNodes")),allowed,allowed,CAP,()->{});var document=object(NextflowParameters.parse(NextflowFiles.utf8(RepeatHostFiles.bytes(actual,CAP)),16777216,1000000));document.remove("physicalHashes");require(expected.equals(document),"Bound hifiasm ELF/helper/loader/search population changed; preserve this binding and prepare NEW compatible paths");
    }
    private static List<Path> paths(Object raw)throws IOException{var result=new ArrayList<Path>();for(Object name:list(raw))result.add(Path.of(text(name)));return result;}
}
