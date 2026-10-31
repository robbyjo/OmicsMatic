/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fresh owned original source/library copies; generated caches never enter the sealed payload. */
final class RepeatResources {
    private RepeatResources() { }
    private static final long DIAGNOSTIC_RESERVE=65536;
    record Member(String payloadMember,String method,RepeatPayload.Fingerprint fingerprint) { }
    record Prepared(RepeatPayload.Context payload,Path work,Path sourceRoot,Map<String,Member> members,
            String ledgerSha256,RepeatPayload.Limits limits) {
        Prepared { members=Map.copyOf(members); }
        Map<String,String> environment(Path processWork) throws IOException {
            var values=new TreeMap<>(payload.environment(processWork));
            values.put("LIBDIR",sourceRoot.resolve("Libraries").toString());return Map.copyOf(values);
        }
        void verify(Runnable check) throws IOException {
            try(var scope=RepeatFingerprint.enter()){scope.requireActive();payload.verify(check);verifyProtected(work,members,ledgerSha256,limits,check);}
        }
        Map<String,RepeatPayload.Fingerprint> generatedCache(Runnable check) throws IOException {
            try(var scope=RepeatFingerprint.enter()){
                scope.requireActive();verify(check);var cache=new TreeMap<String,RepeatPayload.Fingerprint>();
                for(var entry:inventory(work,limits,check).entrySet())if(!members.containsKey(entry.getKey())&&!entry.getKey().equals("resources.json"))cache.put(entry.getKey(),entry.getValue());
                return Map.copyOf(cache);
            }
        }
    }
    static Prepared prepare(RepeatPayload.Context payload,Path suppliedWork,RepeatPayload.Limits limits,Runnable check) throws IOException {
        try(var scope=RepeatFingerprint.enter()){scope.requireActive();return preparePass(payload,suppliedWork,limits,check);}
    }
    private static Prepared preparePass(RepeatPayload.Context payload,Path suppliedWork,RepeatPayload.Limits limits,Runnable check) throws IOException {
        Path work=suppliedWork.toAbsolutePath().normalize();RepeatPayload.originalPath(work);
        if(work.getParent()==null||!work.getParent().toRealPath().equals(work.getParent())||work.startsWith(payload.home())||payload.home().startsWith(work))
            throw new IOException("Original resource preparation requires a canonical NEW directory disjoint from its sealed payload");
        payload.verify(check);
        var targets=new TreeMap<String,Member>();var directories=new HashSet<Path>();directories.add(work);long bytes=0;
        for(var entry:payload.files().entrySet())if(entry.getKey().startsWith("repeatmasker/")){
            String target=entry.getKey();
            if(payload.profile().equals(RepeatRecipe.HISTORICAL)&&target.equals("repeatmasker/Libraries/Dfam.h5"))target="repeatmasker/Libraries/RepeatMaskerLib.h5";
            targets.put(target,new Member(entry.getKey(),target.endsWith("/RepeatMaskerLib.h5")?"HARDLINK_OR_COPY":"COPY",entry.getValue()));
            bytes=Math.addExact(bytes,entry.getValue().bytes());Path parent=work.resolve(target).getParent();while(parent!=null&&parent.startsWith(work)){directories.add(parent);parent=parent.getParent();}
        }
        if(Math.addExact(bytes,DIAGNOSTIC_RESERVE)>limits.bytes()||targets.size()+directories.size()+2>limits.members())
            throw new IOException("Complete original source/library resources exceed their logical-byte/member capacity;increase reviewed capacities and use a NEW directory");
        Files.createDirectory(work);
        try{
            var ordered=new ArrayList<>(directories);ordered.sort(java.util.Comparator.comparingInt(Path::getNameCount));
            for(Path directory:ordered)if(!directory.equals(work)){check.run();Files.createDirectory(directory);}
            var completed=new TreeMap<String,Member>();long used=0;
            for(var entry:targets.entrySet()){
                check.run();Member member=entry.getValue();Path source=payload.home().resolve(member.payloadMember()),target=work.resolve(entry.getKey());String method="COPY";
                if(member.method().equals("HARDLINK_OR_COPY")){
                    try{Files.createLink(target,source);method="HARDLINK";}
                    catch(IOException|UnsupportedOperationException unavailable){if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw unavailable;copy(source,target,member.fingerprint().bytes(),check);}
                }else copy(source,target,member.fingerprint().bytes(),check);
                if(Files.size(target)!=member.fingerprint().bytes()||!RepeatFingerprint.hash(target,member.fingerprint().bytes(),check).equals(member.fingerprint().sha256()))throw new IOException("Complete copied original resource differs: "+entry.getKey());
                used=Math.addExact(used,Files.size(target));completed.put(entry.getKey(),new Member(member.payloadMember(),method,member.fingerprint()));
            }
            Map<String,Object> configurationDelta=Map.of();
            if(payload.profile().equals(RepeatRecipe.HISTORICAL)){
                String name="repeatmasker/RepeatMaskerConfig.pm";Path path=work.resolve(name);byte[] original=Files.readAllBytes(path);
                byte[] configured=configureLibraryDirectory(original,work.resolve("repeatmasker/Libraries"));
                long added=configured.length-(long)original.length;if(Math.addExact(used,Math.addExact(added,DIAGNOSTIC_RESERVE))>limits.bytes())throw new IOException("Owned original configuration exceeds resource capacity");
                Files.write(path,configured,StandardOpenOption.TRUNCATE_EXISTING);used=Math.addExact(used,added);
                Member before=completed.get(name);var after=new RepeatPayload.Fingerprint(configured.length,AdmixtureInputs.digest(configured));completed.put(name,new Member(before.payloadMember(),"CONFIGURED_LIBDIR_VALUE",after));
                configurationDelta=Map.of("path",name,"original_sha256",before.fingerprint().sha256(),"configured_sha256",after.sha256(),"value",work.resolve("repeatmasker/Libraries").toString(),"scope","Only the original LIBDIR configuration value;algorithm and all other configuration bytes unchanged");
            }
            var ledger=new LinkedHashMap<String,Object>();ledger.put("schema_version",1);ledger.put("scope","owned-original-source-and-library-resources-only");
            ledger.put("profile",payload.profile());ledger.put("lock_sha256",payload.lockSha256());ledger.put("payload_home",payload.home());ledger.put("members",completed);
            ledger.put("working_configuration_delta",configurationDelta);
            ledger.put("generated_cache_policy","Fresh analysis-owned Libraries only;sealed payload/source members stay unchanged. Record complete generated cache contents and resource provenance after original execution;directory names alone never establish library identity.");
            ledger.put("upstream_search_completed",false);
            byte[] raw=RepeatBundle.json(ledger);
            if(Math.addExact(used,Math.addExact(raw.length,DIAGNOSTIC_RESERVE))>limits.bytes())throw new IOException("Complete original resource metadata exceeds capacity;diagnostic reserve retained");
            Path path=work.resolve("resources.json");Files.write(path,raw,StandardOpenOption.CREATE_NEW);
            Prepared result=new Prepared(payload,work,work.resolve("repeatmasker"),completed,AdmixtureInputs.digest(raw),limits);result.verify(check);return result;
        }catch(IOException|RuntimeException failure){
            String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);
            try{Files.writeString(work.resolve("RESOURCES_FAILED.txt"),"RepeatMasker FAIL2: original resource preparation incomplete;no search result admitted.\n"+message+"\nRequired action: retain these partials and source provenance,correct the cause/capacity,and retry to a NEW directory. Never mutate or reuse the sealed payload as a writable cache.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostics){failure.addSuppressed(diagnostics);}throw failure;
        }
    }
    static byte[] configureLibraryDirectory(byte[] original,Path library)throws IOException{
        RepeatPayload.originalPath(library);
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(original)).toString();
        var section=java.util.regex.Pattern.compile("(?s)'LIBDIR'\\s*=>\\s*\\{.*?\\n\\s*\\}").matcher(text);
        if(!section.find())throw new IOException("Missing exact original LIBDIR configuration block");int start=section.start(),end=section.end();if(section.find())throw new IOException("Duplicate original LIBDIR configuration block");
        String block=text.substring(start,end);var value=java.util.regex.Pattern.compile("'value'\\s*=>\\s*'([^']*)'").matcher(block);
        if(!value.find()||!value.group(1).isEmpty())throw new IOException("Expected the pinned original empty LIBDIR value");int a=start+value.start(1),b=start+value.end(1);if(value.find())throw new IOException("Duplicate original LIBDIR value");
        String configured=text.substring(0,a)+library+text.substring(b);
        if(!configured.substring(0,a).concat(configured.substring(a+library.toString().length())).equals(text))throw new IOException("Working LIBDIR transformation altered other source bytes");
        return configured.getBytes(StandardCharsets.UTF_8);
    }
    private static void copy(Path source,Path target,long maximum,Runnable check)throws IOException{
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];int count;long total=0;while((count=input.read(buffer))>=0){check.run();total=Math.addExact(total,count);if(total>maximum)throw new IOException("Protected original resource grew beyond its complete byte capacity");output.write(buffer,0,count);}
        }
        if(Files.isExecutable(source)&&!target.toFile().setExecutable(true,false))throw new IOException("Cannot preserve executable permission for original program: "+target);
    }
    private static void verifyProtected(Path work,Map<String,Member> members,String ledgerHash,RepeatPayload.Limits limits,Runnable check)throws IOException{
        var directories=new HashSet<Path>();directories.add(work);Path cacheRoot=work.resolve("repeatmasker/Libraries");
        for(String name:members.keySet()){Path parent=work.resolve(name).getParent();while(parent!=null&&parent.startsWith(work)){directories.add(parent);parent=parent.getParent();}}
        Files.walkFileTree(work,new SimpleFileVisitor<>(){
            private int count;
            private void member()throws IOException{check.run();if(++count>limits.members())throw new IOException("Original resource/cache member capacity exceeded during directory validation");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{member();if(!path.toRealPath().equals(path)||!directories.contains(path)&&!path.startsWith(cacheRoot))throw new IOException("Unexpected/linked original resource directory outside owned cache scope");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{member();return FileVisitResult.CONTINUE;}
        });
        Map<String,RepeatPayload.Fingerprint> actual=inventory(work,limits,check);
        for(var entry:members.entrySet())if(!entry.getValue().fingerprint().equals(actual.get(entry.getKey())))throw new IOException("Protected original source/library resource changed: "+entry.getKey());
        var ledger=actual.get("resources.json");if(ledger==null||!ledger.sha256().equals(ledgerHash))throw new IOException("Original resource provenance ledger changed");
        for(String name:actual.keySet())if(!members.containsKey(name)&&!name.equals("resources.json")&&!name.startsWith("repeatmasker/Libraries/"))throw new IOException("Unexpected original resource outside owned cache scope: "+name);
    }
    private static Map<String,RepeatPayload.Fingerprint> inventory(Path work,RepeatPayload.Limits limits,Runnable check)throws IOException{
        if(!Files.isDirectory(work,LinkOption.NOFOLLOW_LINKS)||!work.toRealPath().equals(work))throw new IOException("Original resource root is linked or noncanonical");
        var files=new TreeMap<String,RepeatPayload.Fingerprint>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(work,new SimpleFileVisitor<>(){
            private void member()throws IOException{check.run();if(++count[0]>limits.members())throw new IOException("Original resource/cache member capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{member();if(!path.toRealPath().equals(path))throw new IOException("Linked original resource/cache directory");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{member();if(!attributes.isRegularFile())throw new IOException("Linked/nonregular original resource/cache member");bytes[0]=Math.addExact(bytes[0],attributes.size());if(bytes[0]>limits.bytes())throw new IOException("Original resource/cache logical byte capacity exceeded");files.put(work.relativize(path).toString().replace('\\','/'),new RepeatPayload.Fingerprint(attributes.size(),RepeatFingerprint.hash(path,limits.bytes(),check)));return FileVisitResult.CONTINUE;}
        });return Map.copyOf(files);
    }
}
