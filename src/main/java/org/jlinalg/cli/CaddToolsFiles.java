/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Bounded full captures and byte-for-byte saved-ledger comparison. */
final class CaddToolsFiles {
    private CaddToolsFiles(){ }
    static Map<String,Object> pin(Path file,long maximum)throws IOException {var p=NextflowFiles.pin(file.toAbsolutePath().normalize(),maximum,()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("CADD interrupted");});return Map.of("bytes",p.bytes(),"sha256",p.sha256());}
    static Path required(Path path)throws IOException {if(!Files.exists(path)||!Files.isReadable(path))throw new CaddToolsCli.Unavailable("Required permitted CADD input/resource unavailable: "+path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Require a canonical unlinked regular CADD input/resource: "+path);return path;}
    static Path copy(Path source,Path target,long cap)throws IOException {required(source);var before=pin(source,cap);Files.copy(source,target);if(!before.equals(pin(target,cap))||!before.equals(pin(source,cap)))throw new IOException("Complete CADD input changed during capture");return target;}
    static final class Lines implements AutoCloseable {
        private final Reader reader;private final long maximum;private final int lineMaximum;private long characters;
        Lines(Path path,Map<String,Object> request)throws IOException{InputStream in=new BufferedInputStream(Files.newInputStream(path));try{in.mark(2);int a=in.read(),b=in.read();in.reset();if(a==31&&b==139)in=new GZIPInputStream(in,65536);reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}catch(IOException|RuntimeException e){in.close();throw e;}maximum=CaddToolsRequest.number(request,"maximum_decoded_characters");lineMaximum=(int)CaddToolsRequest.number(request,"maximum_line_characters");}
        String next()throws IOException {var result=new StringBuilder();int c;while((c=reader.read())!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("CADD input interrupted");if(++characters>maximum)throw new IOException("Complete decoded CADD character capacity exceeded");if(c=='\n')break;if(result.length()>=lineMaximum)throw new IOException("Complete CADD line capacity exceeded");result.append((char)c);}if(c==-1&&result.isEmpty())return null;if(!result.isEmpty()&&result.charAt(result.length()-1)=='\r')result.setLength(result.length()-1);return result.toString();}
        public void close()throws IOException{reader.close();}
    }
    static final class Output implements AutoCloseable {
        private final Path directory;private final boolean compare;private final long maximum;private long bytes;private final Set<String> names=new TreeSet<>();
        Output(Path directory,Map<String,Object> request,boolean compare)throws IOException{this.directory=directory;this.compare=compare;maximum=CaddToolsRequest.number(request,"maximum_output_bytes");if(!compare)Files.createDirectory(directory);}
        Writer writer(String name)throws IOException {if(!names.add(name))throw new IOException("Duplicate CADD result member");Path path=directory.resolve(name);InputStream expected=compare?Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS):null;OutputStream actual=compare?null:Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
            return new OutputStreamWriter(new OutputStream(){
                public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
                public void write(byte[] raw,int start,int length)throws IOException {if(length>maximum-bytes)throw new IOException("Complete CADD result byte capacity exceeded");bytes+=length;if(compare){byte[] before=expected.readNBytes(length);if(before.length!=length||!Arrays.equals(before,Arrays.copyOfRange(raw,start,start+length)))throw new IOException("Saved CADD native ledger differs: "+name);}else actual.write(raw,start,length);}
                public void close()throws IOException{if(compare){try{if(expected.read()!=-1)throw new IOException("Saved CADD ledger contains extra bytes: "+name);}finally{expected.close();}}else actual.close();}
            },StandardCharsets.UTF_8);
        }
        public void close()throws IOException{if(compare){var actual=new TreeSet<String>();try(var children=Files.list(directory)){for(Path p:children.toList()){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected CADD result member");actual.add(p.getFileName().toString());}}if(!actual.equals(names))throw new IOException("Saved CADD result population differs");}}
    }
    static void row(Writer writer,Object... fields)throws IOException {for(int i=0;i<fields.length;i++){if(i>0)writer.write('\t');String text=String.valueOf(fields[i]);if(text.indexOf('\t')>=0||text.indexOf('\r')>=0||text.indexOf('\n')>=0)throw new IOException("Control delimiter in CADD ledger field");writer.write(text);}writer.write('\n');}
    static Map<String,Object> hashes(Path root)throws IOException {var result=new TreeMap<String,Object>();Files.walkFileTree(root,new SimpleFileVisitor<>(){
        public FileVisitResult preVisitDirectory(Path p,BasicFileAttributes a)throws IOException{if(Files.isSymbolicLink(p)||!p.toRealPath().equals(p))throw new IOException("Unexpected CADD capture directory alias");return FileVisitResult.CONTINUE;}
        public FileVisitResult visitFile(Path p,BasicFileAttributes a)throws IOException{if(!a.isRegularFile()||Files.isSymbolicLink(p))throw new IOException("Unexpected CADD capture nonregular member");result.put(root.relativize(p).toString().replace('\\','/'),pin(p,Long.MAX_VALUE));return FileVisitResult.CONTINUE;}
    });return result;}
    static void seal(Path root)throws IOException{var files=hashes(root);files.remove("capture-files.json");json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));}
}
