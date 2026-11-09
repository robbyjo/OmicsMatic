/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Explicit permitted compilation of pinned original hifiasm sources; never user assembly admission. */
public final class HifiasmSourceBuildCli {
    private HifiasmSourceBuildCli(){ }
    private static final String ACTION="SKIP3 skips dependent assembly when permitted Linux Java, make, GCC/G++, zlib development files or installation permission is unavailable. Supply the exact permitted prerequisites and NEW source/output paths, or retain the skip. FAIL2 admits no runtime or user assembly: preserve original compiler logs, process receipts and partial sources; resolve the named cause and retry NEW paths. Never refresh old scientific hashes. Compilation is setup only; no read assembly or paper reproduction is admitted.";
    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){
        Path out=null;var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_HIFIASM_SOURCE_COMPILATION");state.put("analysis_admitted",false);state.put("implementation_credit",false);state.put("started_at",Instant.now().toString());int exit=2;
        try{
            require(args.length==2&&args[0].equals("--request"),"Use --request explicit-build-request.json");Path requestPath=Path.of(args[1]).toAbsolutePath().normalize();var request=GseapyExecutionRuntime.read(requestPath,1048576);GseapyWorkflowRequest.allowed(request,"schema_version","mode","allow_install","edition","source","out","compiler_threads");integer(request.get("schema_version"),1,1);require(text(request.get("mode")).equals("BUILD_REFERENCE_ONLY"),"This command compiles exact original sources only");Path target=Path.of(text(request.get("out"))).toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(target.getParent(),LinkOption.NOFOLLOW_LINKS)&&target.getParent().equals(target.getParent().toRealPath()),"Require NEW output under an existing canonical parent");out=Files.createDirectory(target);Files.write(out.resolve("request.original.json"),NextflowFiles.bytes(requestPath,1048576),StandardOpenOption.CREATE_NEW);warnings(out,state);
            if(!bool(request.get("allow_install")))throw new RsemProcess.UnavailableDependency("Installation is forbidden or explicit allow_install absent");if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new RsemProcess.UnavailableDependency("Original hifiasm compilation requires Linux x86_64");for(String name:List.of("/usr/bin/make","/usr/bin/gcc","/usr/bin/g++"))if(!Files.isExecutable(Path.of(name)))throw new RsemProcess.UnavailableDependency("Required permitted compiler is unavailable: "+name);
            String edition=text(request.get("edition"));var profile=profile();var editions=object(profile.get("editions"));require(editions.containsKey(edition),"Declare an exact packaged original hifiasm edition");var selected=object(editions.get(edition));var members=object(selected.get("members"));Path source=Path.of(text(request.get("source"))).toAbsolutePath().normalize();require(Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)&&source.equals(source.toRealPath())&&!source.startsWith(out)&&!out.startsWith(source),"Require canonical original source directory separate from outputs");verify(source,members,true);int threads=Math.toIntExact(integer(request.get("compiler_threads"),1,4));var limits=new RsemProcess.Limits(1800000,8589934592L,1800,1073741824,1024,2147483648L,4096,32,128,12884901888L,100);var environment=Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","TZ","UTC","HOME",out.toString(),"TMPDIR",out.toString(),"OMP_NUM_THREADS","1");var command=List.of("/usr/bin/make","-j"+threads,"-C",source.toString());System.out.println("Compiling unchanged original hifiasm "+edition);var result=RsemProcess.executeOwned(command,environment,out.resolve("compile"),limits,()->{},()->{},List.of(source));verify(source,members,false);Path binary=source.resolve("hifiasm");require(Files.isRegularFile(binary,LinkOption.NOFOLLOW_LINKS)&&binary.equals(binary.toRealPath())&&Files.isExecutable(binary),"Original build did not produce a canonical executable");var binaryPin=NextflowFiles.pin(binary,1073741824,()->{});var version=HifiasmVersionProbe.probe(binary,edition,environment,out.resolve("version"),()->{require(binaryPin.equals(NextflowFiles.pin(binary,1073741824,()->{})),"Compiled original binary changed");});require(result.ownedLiveAfterCleanup().isEmpty(),"Owned compiler processes remain alive");state.put("source_version_probe",version);state.put("status","PASS_ORIGINAL_HIFIASM_SOURCE_COMPILATION_ONLY");state.put("edition",edition);state.put("commit",selected.get("commit"));state.put("binary",Map.of("path",binary.toString(),"bytes",binaryPin.bytes(),"sha256",binaryPin.sha256()));state.put("source_members",members);state.put("original_source_members_unchanged",true);state.put("source_namespace_patched",false);state.put("compiler_command",command);state.put("source_profile",profile);state.put("owned_live_after_cleanup",List.of());exit=0;
        }catch(RsemProcess.UnavailableDependency unavailable){state.put("status","SKIP3_HIFIASM_BUILD_REQUIREMENTS_UNAVAILABLE");state.put("error",unavailable.toString());exit=3;System.err.println("HIFIASM SKIP3: "+unavailable+"\n"+ACTION);}
        catch(Exception failed){state.put("error",failed.toString());System.err.println("HIFIASM FAIL2: "+failed+"\n"+ACTION);failed.printStackTrace(System.err);}
        finally{state.put("finished_at",Instant.now().toString());if(out!=null)try{warnings(out,state);NextflowFiles.writeJson(out.resolve("COMPLETED.json"),state);}catch(IOException failed){System.err.println("HIFIASM FAIL2: final receipt unavailable; preserve the unadmitted directory. "+failed);exit=2;}}
        return exit;
    }
    private static Map<String,Object> profile()throws IOException{
        try(var stream=HifiasmSourceBuildCli.class.getResourceAsStream("/org/jlinalg/omics/hifiasm-source/profiles.json")){require(stream!=null,"Missing pinned original source profile");byte[] raw=stream.readNBytes(16777217);require(raw.length<=16777216,"Source profile capacity exceeded");return object(NextflowParameters.parse(NextflowFiles.utf8(raw),16777216,1000000));}
    }
    private static void verify(Path source,Map<String,Object> members,boolean initial)throws IOException{
        var actual=new TreeSet<String>();try(var paths=Files.walk(source)){for(Path file:paths.toList()){require(!Files.isSymbolicLink(file),"Original source/build tree contains a symbolic link");if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))actual.add(source.relativize(file).toString().replace('\\','/'));else require(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS),"Nonregular original source/build member");}}
        if(initial)require(actual.equals(members.keySet()),"Initial source population differs from the exact original archive");for(var e:members.entrySet()){Path file=source.resolve(NextflowFiles.member(e.getKey()));require(NextflowFiles.fingerprint(e.getValue(),33554432).equals(NextflowFiles.pin(file,33554432,()->{})),"Original scientific source changed: "+e.getKey());}
    }
    private static void warnings(Path out,Map<String,Object> state)throws IOException{Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+ACTION+"\n\nActual runtime admission, host/library binding, complete read/graph/output audits and biological validation are separate. The original compiler warning stream remains verbatim. No yak runtime is included.\n");}
}
