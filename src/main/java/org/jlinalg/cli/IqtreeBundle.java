/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Fresh owned bundles and portable byte integrity; SHA-256 is not authentication. */
final class IqtreeBundle {
    private IqtreeBundle() { }
    record Fingerprint(long bytes,String sha256) { }
    record Inventory(Map<String,IqtreeFiles.Identity> files,Set<String> directories) {
        Inventory {files=Map.copyOf(files);directories=Set.copyOf(directories);}
    }
    static void create(Path root)throws IOException {
        if(root.getParent()==null||!root.isAbsolute()||!root.normalize().equals(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Require a canonical existing parent and a NEW IQ-TREE output directory");
        Files.createDirectory(root);
        if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            var permissions=PosixFilePermissions.fromString("rwx------");Files.setPosixFilePermissions(root,permissions);
            if(!Files.getPosixFilePermissions(root).equals(permissions))throw new IOException("IQ-TREE output filesystem cannot preserve private directory modes; use a fresh Linux-filesystem output path");
        }
    }
    static void write(Path path,Object value)throws IOException {Files.writeString(path,IqtreeJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static Inventory inventory(Path root,long maximumBytes,int maximumFiles,Runnable cancellation)throws IOException {
        var result=new TreeMap<String,IqtreeFiles.Identity>();var directories=new TreeSet<String>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {
                cancellation.run();if(++count[0]>maximumFiles||!path.toRealPath().equals(path))throw new IOException("IQ-TREE bundle directory/link capacity exceeded");directories.add(root.relativize(path).toString().replace('\\','/'));return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                cancellation.run();if(++count[0]>maximumFiles||!attrs.isRegularFile())throw new IOException("IQ-TREE bundle has a nonregular/linked output or exceeds file capacity");
                bytes[0]=Math.addExact(bytes[0],attrs.size());if(bytes[0]>maximumBytes)throw new IOException("Complete IQ-TREE bundle byte capacity exceeded");
                result.put(root.relativize(path).toString().replace('\\','/'),IqtreeFiles.identity(path,Math.max(1,maximumBytes),cancellation));return FileVisitResult.CONTINUE;
            }
        });return new Inventory(result,directories);
    }
    static Map<String,Fingerprint> portable(Inventory inventory){var result=new TreeMap<String,Fingerprint>();for(var e:inventory.files().entrySet())result.put(e.getKey(),new Fingerprint(e.getValue().bytes(),e.getValue().sha256()));return result;}
    static Fingerprint verify(Path supplied)throws IOException {
        return verify(supplied,"COMPLETED.json");
    }
    static Fingerprint verify(Path supplied,String completionName)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();if(!root.toRealPath().equals(root))throw new IOException("Require a canonical unlinked IQ-TREE bundle");
        Path manifest=root.resolve("manifest.json"),completion=root.resolve(completionName);
        var completionIdentity=IqtreeFiles.identity(completion,262144,()->{});Map<?,?> completed=object(SimpleJson.parse(Files.readString(completion,StandardCharsets.UTF_8)));
        if(!Boolean.TRUE.equals(completed.get("scientific_result_admitted"))||!"IQ-TREE".equals(completed.get("tool")))throw new IOException("No completed IQ-TREE scientific bundle is admitted");
        var identity=IqtreeFiles.identity(manifest,16777216,()->{});
        if(!identity.sha256().equals(completed.get("manifest_sha256")))throw new IOException("IQ-TREE completion/manifest hash mismatch");
        Map<?,?> document=object(SimpleJson.parse(Files.readString(manifest,StandardCharsets.UTF_8)));
        if(!Long.valueOf(1).equals(document.get("schema_version"))||!"IQ-TREE".equals(document.get("tool"))||!"audited-provisional-awaiting-finalization".equals(document.get("status")))throw new IOException("Unknown or failed IQ-TREE bundle schema/tool/status");
        long bytes=integer(document.get("maximum_bundle_bytes"),1,1099511627776L);int files=(int)integer(document.get("maximum_bundle_files"),1,1000000);
        Inventory inventory=inventory(root,bytes,files,()->{});
        if(!(document.get("directories") instanceof List<?> dirs)||!new HashSet<>(dirs).equals(inventory.directories())||dirs.size()!=inventory.directories().size())throw new IOException("IQ-TREE bundle has missing/extra directories");
        Map<?,?> expected=object(document.get("files"));var actual=portable(inventory);actual.remove("manifest.json");actual.remove(completionName);
        if(!actual.keySet().equals(expected.keySet()))throw new IOException("IQ-TREE bundle has missing/extra files");
        for(var entry:actual.entrySet()) {
            Map<?,?> pin=object(expected.get(entry.getKey()));
            if(entry.getValue().bytes()!=integer(pin.get("bytes"),0,1099511627776L)||!entry.getValue().sha256().equals(pin.get("sha256")))throw new IOException("IQ-TREE bundle member changed: "+entry.getKey());
        }
        if(!identity.equals(IqtreeFiles.identity(manifest,16777216,()->{}))||!completionIdentity.equals(IqtreeFiles.identity(completion,262144,()->{})))throw new IOException("IQ-TREE bundle admission metadata changed while verifying");
        return new Fingerprint(identity.bytes(),identity.sha256());
    }
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require an IQ-TREE bundle JSON object");return map;}
    private static long integer(Object value,long low,long high)throws IOException {if(!(value instanceof Long n)||n<low||n>high)throw new IOException("Invalid IQ-TREE bundle integer");return n;}
}
