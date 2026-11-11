/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.TreeMap;

/** Complete native bundle identities and bounded ledger writing, with no producer-path access during verification. */
final class BigWigFiles {
    private BigWigFiles() { }
    static final long FINAL_RESERVE=16777216L;
    static void create(Path root)throws IOException {require(root.isAbsolute()&&root.normalize().equals(root)&&root.getParent()!=null&&root.getParent().toRealPath().equals(root.getParent())&&root.toString().chars().noneMatch(c->c<32||c==127),"Require a canonical existing parent and NEW native bigWig output directory without control characters");if(root.getFileSystem().supportedFileAttributeViews().contains("posix"))Files.createDirectory(root,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));else Files.createDirectory(root);}
    static Map<String,Object> inventory(Path root,BigWigRequest.Spec s,Runnable check)throws IOException {return MemeRuntimeFiles.tree(root,new MemeRuntimeFiles.Limits(s.workspaceBytes(),s.workspaceBytes(),100),check);}
    static Map<String,Object> portable(Object raw)throws IOException {var result=new TreeMap<String,Object>();for(var e:NextflowFiles.object(raw).entrySet()){String name=NextflowFiles.text(e.getKey());if(!name.equals("."))NextflowFiles.member(name);var row=NextflowFiles.object(e.getValue());String kind=NextflowFiles.text(row.get("kind"));NextflowFiles.integer(row.get("mode"),-1,0777);if(kind.equals("file")){NextflowFiles.keys(row,"kind","mode","bytes","sha256");result.put(name,Map.of("kind","file","bytes",NextflowFiles.fingerprint(row,1099511627776L).bytes(),"sha256",NextflowFiles.fingerprint(row,1099511627776L).sha256()));}else{require(kind.equals("directory"),"Unsupported native bundle member kind");NextflowFiles.keys(row,"kind","mode");result.put(name,Map.of("kind","directory"));}}return Map.copyOf(result);}
    static Map<?,?> json(Path file,int maximum)throws IOException {return NextflowFiles.object(NextflowFiles.json(file,maximum));}
    static void json(Path file,Object value)throws IOException {NextflowFiles.writeJson(file,value);}
    static Map<String,Object> pin(NextflowFiles.Pin pin){return Map.of("bytes",pin.bytes(),"sha256",pin.sha256());}
    static void copy(Path source,Path destination,NextflowFiles.Pin pin,Runnable check)throws IOException {long copied=0;try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];for(int n=in.read(buffer);n>=0;n=in.read(buffer)){check.run();copied+=n;require(copied<=pin.bytes(),"Original source grew during unchanged capture");out.write(buffer,0,n);}}require(copied==pin.bytes()&&pin.equals(NextflowFiles.pin(source,pin.bytes(),check))&&pin.equals(NextflowFiles.pin(destination,pin.bytes(),check)),"Original/captured complete input identity differs");}
    static final class Lines implements AutoCloseable {
        private final java.io.OutputStream output;private final java.io.InputStream input;private final long maximum;private long bytes;private final Runnable check;
        Lines(Path file,boolean compare,long maximum,Runnable check)throws IOException {this.maximum=maximum;this.check=check;if(compare){input=new java.io.BufferedInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS),65536);output=null;}else{output=new java.io.BufferedOutputStream(Files.newOutputStream(file,StandardOpenOption.CREATE_NEW),65536);input=null;}}
        long bytes(){return bytes;}
        void line(String text)throws IOException {check.run();byte[] raw=(text+"\n").getBytes(StandardCharsets.UTF_8);require(bytes<=maximum-raw.length,"Complete native ledger exceeds declared workspace/ledger capacity");if(input==null)output.write(raw);else{byte[] actual=input.readNBytes(raw.length);require(java.util.Arrays.equals(raw,actual),"Complete native source/precision/reference ledger differs");}bytes+=raw.length;}
        void end()throws IOException {if(input!=null)require(input.read()==-1,"Late native ledger bytes/rows");}
        @Override public void close()throws IOException {if(output!=null)output.close();else input.close();}
    }
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
