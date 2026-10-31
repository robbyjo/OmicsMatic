/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original fixed-health and host admission, separate from a completed study search. */
final class RepeatRuntime {
    private RepeatRuntime() { }
    static final String TOOL="RepeatMasker-original-runtime";
    static final String PRESET="bounded-repeat-runtime-v1";
    static final long METADATA=67108864;
    static final RepeatPayload.Limits PAYLOAD=new RepeatPayload.Limits(17179869184L,8192);
    static final RepeatBundle.Limits WORK=new RepeatBundle.Limits(34359738368L,100000);
    static final RepeatHost.Limits HOST=new RepeatHost.Limits(new RepeatHostFiles.Limits(536870912,2147483648L,50000,128,METADATA),
            new RepeatProcess.Limits(60000,1073741824,60,4194304,256,1073741824,32768,8,64,536870912,25),1024);
    private static final long FINAL_BYTES=2*METADATA;
    private static final String STATUS="ADMITTED_FIXED_ORIGINAL_HEALTH_AND_HOST";
    private record Request(String profile,Path payloadHome,Path python,Path out,String preset) { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Context(Path home,RepeatPayload.Context payload,String manifestSha256,String markerSha256,byte[] hostIdentity) {
        Context {hostIdentity=hostIdentity.clone();}
        @Override public byte[] hostIdentity(){return hostIdentity.clone();}
        /** Fresh external verification directories retain probes without changing the admitted home. */
        void verify(Path newHostVerification,Runnable check)throws IOException{
            try{
                Context actual=open(home,payload.profile(),newHostVerification,check);
                if(!manifestSha256.equals(actual.manifestSha256())||!markerSha256.equals(actual.markerSha256())
                        ||!payload.equals(actual.payload())||!Arrays.equals(hostIdentity,actual.hostIdentity()))throw new IOException("Previously admitted original runtime changed during analysis");
            }catch(Unavailable unavailable){throw new IOException("Previously admitted original runtime/provider became unavailable during analysis",unavailable);}
        }
    }
    static Map<?,?> settings()throws IOException{
        return RepeatBundle.object(RepeatBundle.json(Map.of("preset",PRESET,"payload",PAYLOAD,"host",HOST,"workspace",WORK)));
    }
    static Context admit(Path payloadHome,String profile,Path python,Path suppliedHome,Runnable external)throws IOException{
        return admit(payloadHome,profile,python,suppliedHome,external,()->{});
    }
    static Context admit(Path payloadHome,String profile,Path python,Path suppliedHome,Runnable external,RepeatProcess.Check requestIntegrity)throws IOException{
        platform(profile);Path home=suppliedHome.toAbsolutePath().normalize();RepeatPayload.originalPath(home);
        canonicalParent(home);
        Path source=payloadHome.toAbsolutePath().normalize();
        if(home.startsWith(source)||source.startsWith(home))throw new IOException("Scientific runtime admission must use a NEW home disjoint from its sealed payload");
        var payload=payload(source,profile,python,external);Files.createDirectory(home);
        try(var guard=new Guard(home)){
            Runnable check=()->{guard.check();external.run();};
            write(home,"admission-request.json",RepeatBundle.json(new Request(profile,payload.home(),payload.python(),home,PRESET)),FINAL_BYTES,check);
            var before=RepeatHost.capture(payload,home.resolve("host-before"),HOST,check);
            var fixtures=RepeatHealth.prepare(home.resolve("fixtures"),check);
            var resources=RepeatResources.prepare(payload,home.resolve("resources"),PAYLOAD,check);
            var used=RepeatBundle.inventory(home,WORK,false,check);
            var fixtureUse=RepeatBundle.inventory(fixtures.work(),WORK,false,check);
            var resourceUse=RepeatBundle.inventory(resources.work(),WORK,false,check);
            long unrelatedBytes=used.bytes()-fixtureUse.bytes()-resourceUse.bytes();
            int unrelatedMembers=used.members()-fixtureUse.members()-resourceUse.members();
            long available=Math.min(PAYLOAD.bytes(),WORK.bytes()-unrelatedBytes-HOST.process().workspaceBytes()-FINAL_BYTES);
            int members=WORK.members()-unrelatedMembers-HOST.process().fileCount()-16;
            if(available<1048576||members<1024)throw new IOException("Complete admission cannot reserve both host captures,health and final evidence");
            RepeatHealth.execute(fixtures,resources,home.resolve("health"),new RepeatBundle.Limits(available,members),check);
            var after=RepeatHost.capture(payload,home.resolve("host-after"),HOST,check);
            if(!before.snapshot().sameContents(after.snapshot()))throw new IOException("Declared host/provider identity changed across fixed original scientific health");
            byte[] identity=RepeatBundle.json(RepeatHost.identity(after.snapshot()));
            var controls=RepeatHealth.verifyCompleted(fixtures,resources,home.resolve("health"),check);
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("tool",TOOL);record.put("status",STATUS);
            record.put("profile",profile);record.put("version",payload.version());record.put("runtime_home",home);record.put("payload_home",payload.home());record.put("python",payload.python());
            record.put("payload_lock_sha256",payload.lockSha256());record.put("health_lock_sha256",RepeatHealth.LOCK_SHA256);record.put("settings",settings());
            record.put("host_identity_sha256",AdmixtureInputs.digest(identity));record.put("controls",controls);
            record.put("scientific_runtime_admitted",true);record.put("study_search_completed",false);record.put("biological_accuracy_certified",false);
            record.put("scope","Pinned original payload,complete declared host/providers and all fixed synthetic scientific controls. Before/after host capture and independently re-read commands,zero exits,cleanup,raw query census,annotation fields and masked populations. Not an operating-system sandbox,authenticity signature,whole-tool/paper equivalence or biological calibration. Installation and study SEARCH are separate operations.");
            write(home,"runtime.json",RepeatBundle.json(record),FINAL_BYTES,check);
            write(home,"WARNINGS.md",("# Original scientific runtime admitted; no study search completed\n\nFixed original controls and declared host/provider checks passed. A study needs its own typed SEARCH,complete output audit and completion verifier.\n\n"+recovery()+"\n").getBytes(StandardCharsets.UTF_8),FINAL_BYTES,check);
            check.run();requestIntegrity.verify();seal(home,profile,check);requestIntegrity.verify();Context admitted=inspect(home,profile,check);requestIntegrity.verify();return admitted;
        }catch(IOException|RuntimeException failure){
            try{String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);
                Files.writeString(home.resolve("RUNTIME_FAILED.txt"),"ORIGINAL RUNTIME ADMISSION FAILED: no runtime or study result admitted.\n"+message+"\n"+recovery()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    static Context open(Path suppliedHome,String profile,Path newHostVerification,Runnable check)throws IOException{
        platform(profile);Context context=inspect(suppliedHome.toAbsolutePath().normalize(),profile,check);
        Path destination=newHostVerification.toAbsolutePath().normalize();
        for(Path protectedRoot:List.of(context.home(),context.payload().home()))
            if(destination.startsWith(protectedRoot)||protectedRoot.startsWith(destination))throw new IOException("Fresh host verification must be NEW and disjoint from admitted runtime/payload");
        try{
            var actual=RepeatHost.capture(context.payload(),destination,HOST,check);
            if(!Arrays.equals(context.hostIdentity(),RepeatBundle.json(RepeatHost.identity(actual.snapshot()))))throw new Unavailable("Admitted original host/provider identity differs;admit a NEW scientific runtime on this host");
        }catch(RepeatProcess.UnavailableDependency missing){throw new Unavailable("Original runtime host dependency is missing or forbidden;skip dependent analysis",missing);}
        requireMetadataUnchanged(context,check);return context;
    }
    /** Validates retained admission evidence only; it does not re-admit the current host or run probes. */
    static Context evidence(Path suppliedHome,String profile,Runnable check)throws IOException{
        platform(profile);return inspect(suppliedHome.toAbsolutePath().normalize(),profile,check);
    }
    private static Context inspect(Path home,String profile,Runnable check)throws IOException{
        RepeatPayload.originalPath(home);
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Missing original scientific runtime home;payload installation alone does not admit SEARCH");
        if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("Scientific runtime home must be a canonical real directory");
        if(!Files.exists(home.resolve("ADMITTED.json"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Original runtime has no admission marker;retain any failed attempt and admit a NEW home");
        byte[] markerRaw=document(home.resolve("ADMITTED.json"),65536),manifestRaw=document(home.resolve("manifest.json"),METADATA),runtimeRaw=document(home.resolve("runtime.json"),4194304);
        var marker=RepeatBundle.object(markerRaw);var manifest=RepeatBundle.object(manifestRaw);var record=RepeatBundle.object(runtimeRaw);
        header(home,profile,record,manifest,marker,manifestRaw);
        Path payloadHome=Path.of(text(record,"payload_home"));Path python=record.get("python")==null?null:Path.of(text(record,"python"));
        var payload=payload(payloadHome,profile,python,check);
        if(!payload.version().equals(record.get("version"))||!payload.lockSha256().equals(record.get("payload_lock_sha256")))throw new IOException("Admitted original payload version/lock differs");
        var actualTree=managedTree(home,check);
        if(!actualTree.equals(manifest.get("managed_tree")))throw new IOException("Admitted original runtime has changed,missing,linked or unexpected files/directories/modes");
        for(Object name:actualTree.keySet())if(name.toString().endsWith("FAILED.txt")||name.toString().endsWith("INTERRUPTED.md")||name.toString().endsWith("RUNTIME_INTERRUPTED.txt"))throw new IOException("Admitted runtime retains a failure/interruption diagnostic");
        var before=RepeatHost.identity(RepeatBundle.object(document(home.resolve("host-before/metadata/snapshot.json"),METADATA)));
        var after=RepeatHost.identity(RepeatBundle.object(document(home.resolve("host-after/metadata/snapshot.json"),METADATA)));
        byte[] identity=RepeatBundle.json(after);
        if(!before.equals(after)||!profile.equals(after.get("profile"))||!payload.home().toString().equals(after.get("payloadHome"))||!payload.lockSha256().equals(after.get("payloadLockSha256"))
                ||!AdmixtureInputs.digest(identity).equals(record.get("host_identity_sha256")))throw new IOException("Admitted before/after host identities or payload binding differ");
        var fixtures=RepeatHealth.open(home.resolve("fixtures"),check);var resources=RepeatResources.open(payload,home.resolve("resources"),PAYLOAD,check);
        var controls=RepeatHealth.verifyCompleted(fixtures,resources,home.resolve("health"),check);
        if(!RepeatBundle.object(RepeatBundle.json(Map.of("controls",controls))).get("controls").equals(record.get("controls")))throw new IOException("Admission control population differs from independently verified original outputs");
        Context context=new Context(home,payload,AdmixtureInputs.digest(manifestRaw),AdmixtureInputs.digest(markerRaw),identity);
        requireMetadataUnchanged(context,check);return context;
    }
    static void header(Path home,String profile,Map<?,?> record,Map<?,?> manifest,Map<?,?> marker,byte[] manifestRaw)throws IOException{
        if(!record.keySet().equals(Set.of("schema_version","tool","status","profile","version","runtime_home","payload_home","python","payload_lock_sha256","health_lock_sha256","settings","host_identity_sha256","controls","scientific_runtime_admitted","study_search_completed","biological_accuracy_certified","scope")))throw new IOException("Invalid original runtime admission fields");
        if(!marker.keySet().equals(Set.of("schema_version","tool","profile","manifest_sha256"))||!manifest.keySet().equals(Set.of("schema_version","tool","profile","managed_tree")))throw new IOException("Invalid original runtime marker/manifest fields");
        for(Map<?,?> value:List.of(record,manifest,marker))if(!Long.valueOf(1).equals(value.get("schema_version"))||!TOOL.equals(value.get("tool"))||!profile.equals(value.get("profile")))throw new IOException("Original runtime schema/tool/profile differs");
        if(!STATUS.equals(record.get("status"))||!home.toString().equals(record.get("runtime_home"))||!RepeatHealth.LOCK_SHA256.equals(record.get("health_lock_sha256"))||!settings().equals(record.get("settings"))
                ||!Boolean.TRUE.equals(record.get("scientific_runtime_admitted"))||!Boolean.FALSE.equals(record.get("study_search_completed"))||!Boolean.FALSE.equals(record.get("biological_accuracy_certified"))
                ||!AdmixtureInputs.digest(manifestRaw).equals(marker.get("manifest_sha256")))throw new IOException("Original scientific runtime scope/settings/manifest binding differs");
    }
    /** Full owned tree including empty directories and modes; only the two self-referential seal files are excluded. */
    static Map<?,?> managedTree(Path home,Runnable check)throws IOException{
        RepeatBundle.inventory(home,WORK,false,check); // Owned trees never admit symbolic links or nonregular files.
        var limits=new RepeatHostFiles.Limits(WORK.bytes(),WORK.bytes(),WORK.members(),1,METADATA);
        var snapshot=RepeatHostFiles.capture(List.of(home),List.of(),Set.of(home),Set.of(home),limits,check);
        var tree=new TreeMap<>(snapshot.trees().get(home.toString()));
        for(String name:List.of("manifest.json","ADMITTED.json")){
            var entry=tree.remove(name);if(entry!=null&&!entry.kind().equals("REGULAR"))throw new IOException("Runtime seal metadata must be regular files");
        }
        return RepeatBundle.object(RepeatBundle.json(tree));
    }
    private static void seal(Path home,String profile,Runnable check)throws IOException{
        var manifest=Map.of("schema_version",1,"tool",TOOL,"profile",profile,"managed_tree",managedTree(home,check));byte[] raw=RepeatBundle.json(manifest);
        write(home,"manifest.json",raw,METADATA,check);check.run();
        write(home,"ADMITTED.json",RepeatBundle.json(Map.of("schema_version",1,"tool",TOOL,"profile",profile,"manifest_sha256",AdmixtureInputs.digest(raw))),0,check);
    }
    private static void requireMetadataUnchanged(Context context,Runnable check)throws IOException{
        check.run();if(!RepeatInputs.hash(context.home().resolve("manifest.json"),METADATA,check).equals(context.manifestSha256())
                ||!RepeatInputs.hash(context.home().resolve("ADMITTED.json"),65536,check).equals(context.markerSha256()))throw new IOException("Original runtime admission metadata changed during verification");
    }
    private static RepeatPayload.Context payload(Path home,String profile,Path python,Runnable check)throws IOException{
        try{return RepeatPayload.open(home,profile,python,PAYLOAD,check);}
        catch(RepeatPayload.Unavailable unavailable){throw new Unavailable("Required original payload/provider is unavailable;skip dependent analysis",unavailable);}
    }
    private static byte[] document(Path path,long maximum)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Missing,linked or noncanonical original admission metadata: "+path);
        return AdmixtureInputs.raw(path,maximum);
    }
    private static String text(Map<?,?> record,String key)throws IOException{if(!(record.get(key) instanceof String value)||value.isBlank())throw new IOException("Missing original runtime text: "+key);return value;}
    private static void write(Path home,String name,byte[] raw,long reserve,Runnable check)throws IOException{
        if(raw.length>METADATA)throw new IOException("Complete original admission metadata exceeds its explicit capacity");
        RepeatBundle.write(home,name,raw,WORK,reserve,8,check);
    }
    private static void canonicalParent(Path home)throws IOException{if(home.getParent()==null||!home.getParent().toRealPath().equals(home.getParent()))throw new IOException("Original admission requires a canonical existing parent and NEW directory");}
    private static void platform(String profile)throws IOException{
        if(!Set.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL).contains(profile))throw new Unavailable("Requested original runtime profile is unsupported;no edition is substituted");
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("Original scientific runtime admission requires Linux x86_64 Java;on Windows use Linux Java inside WSL");
    }
    static String recovery(){return "Required action: inspect WARNINGS.md,all host probes,both original process ledgers and retained diagnostics. Missing/forbidden/unsupported prerequisites mean SKIP3 and skipped dependent analysis;provide the exact permitted Linux x86_64 payload/provider/resources and admit a NEW runtime. Input,integrity,execution,capacity or interruption means FAIL2;retain partials,correct the named cause and use NEW paths. Never repair a sealed payload/runtime in place. Analysis/admission performs no installation or download. A payload,admission flag,parent exit0 or native mask is not a completed study search. No biological absence,authenticity or whole-paper equivalence is certified. Cell Ranger is excluded.";}
    private static final class Guard implements AutoCloseable{
        final AtomicBoolean interrupted=new AtomicBoolean();final Thread hook;
        Guard(Path home){hook=new Thread(()->{interrupted.set(true);try{Files.writeString(home.resolve("RUNTIME_INTERRUPTED.txt"),"Original runtime admission interrupted;no runtime/result admitted.\n"+recovery()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){System.err.writeBytes(("Original runtime interruption diagnostic failed;retain "+home+"\n").getBytes(StandardCharsets.UTF_8));}},"repeat-runtime-shutdown");Runtime.getRuntime().addShutdownHook(hook);}
        void check(){if(interrupted.get()||Thread.currentThread().isInterrupted())throw new CancellationException("Original runtime admission interrupted");}
        @Override public void close(){try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){/* retained interruption remains authoritative */}}
    }
}
