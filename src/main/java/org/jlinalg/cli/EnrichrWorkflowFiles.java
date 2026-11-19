/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.enrichr.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Immutable inputs, bounded data writers and complete portable file inventories. */
final class EnrichrWorkflowFiles {
    private EnrichrWorkflowFiles() { }
    @SuppressWarnings("serial") static final class Unavailable extends IOException { Unavailable(String message,Exception cause){super(message,cause);} }
    static byte[] bytes(Path path,long cap)throws IOException{
        try{
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){
                if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(path.toString());
                throw new IOException("Require regular unlinked file: "+path);
            }
            if(Files.size(path)>cap)throw new IOException("File byte capacity exceeded: "+path);
            try(var stream=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] raw=stream.readNBytes(Math.toIntExact(Math.min(cap+1,Integer.MAX_VALUE)));
                if(raw.length>cap)throw new IOException("File grew past byte capacity: "+path);return raw;
            }
        }catch(NoSuchFileException|AccessDeniedException e){throw new Unavailable("Missing/unreadable exact prerequisite: "+path,e);}
    }
    static Map<String,Object> read(Path path,long cap)throws IOException{
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes(path,cap))).toString();
        return object(SimpleJson.parse(text),"Enrichr JSON");
    }
    static byte[] json(Object value)throws IOException{return (NextflowJson.json(value)+"\n").getBytes(StandardCharsets.UTF_8);}
    static void json(Path path,Object value)throws IOException{Files.write(path,json(value),StandardOpenOption.CREATE_NEW);}
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static Map<String,Object> pin(byte[] raw){return Map.of("bytes",raw.length,"sha256",sha(raw));}
    static Map<String,Object> pin(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing/nonregular bundle file: "+path);
        MessageDigest digest=digest();long total=0;
        try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
            byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){checkInterrupted();digest.update(block,0,n);total+=n;}
        }
        if(total!=Files.size(path))throw new IOException("File changed during hashing: "+path);
        return Map.of("bytes",total,"sha256",HexFormat.of().formatHex(digest.digest()));
    }
    static long capture(Path source,Path target,long remaining)throws IOException{
        try{
            if(!Files.exists(source,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException(source.toString());
            if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular unlinked input: "+source);
            if(Files.size(source)>remaining)throw new IOException("Combined input byte capacity exceeded: "+source);
            var before=pin(source);long total=0;
            try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
                byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){checkInterrupted();if(n>remaining-total)throw new IOException("Input grew past byte capacity");out.write(block,0,n);total+=n;}
            }
            if(!same(before,pin(source))||!same(before,pin(target)))throw new IOException("Input changed during exact capture: "+source);
            return total;
        }catch(NoSuchFileException|AccessDeniedException e){throw new Unavailable("Missing/unreadable gene input: "+source,e);}
    }
    static List<String> genes(Path path,long cap,long maximum)throws IOException{
        String raw=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes(path,cap))).toString();
        if(raw.startsWith("\ufeff"))throw new IOException("BOM is not a gene symbol; provide explicit UTF-8 without BOM");
        String[] lines=raw.split("\\r?\\n",-1);int count=lines.length;
        if(count>0&&lines[count-1].isEmpty())count--;
        if(count==0||count>maximum)throw new IOException("Require 1..maximum_genes explicit input rows");
        var result=new ArrayList<String>(count);
        for(int i=0;i<count;i++){
            String gene=lines[i];EnrichrWorkflowContracts.literal(gene,"gene at row "+(i+1));
            if(gene.length()>1024||!gene.equals(gene.strip())||gene.indexOf(',')>=0)throw new IOException("Input row "+(i+1)+": require prepared literal unweighted symbols; no header guessing, stripping or fuzzy-score interpretation");
            result.add(gene);
        }
        return List.copyOf(result);
    }
    record Tree(Map<String,Object> files,List<String> directories,long bytes) { }
    static Tree inventory(Path root)throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require a regular bundle directory");
        var files=new TreeMap<String,Object>();var dirs=new TreeSet<String>();long total=0;int count=0;
        try(var walk=Files.walk(root)){
            var it=walk.iterator();while(it.hasNext()){
                Path path=it.next();if(++count>4096)throw new IOException("Bundle entry capacity exceeded");
                if(Files.isSymbolicLink(path))throw new IOException("Bundle symlinks are refused");
                String name=root.relativize(path).toString().replace('\\','/');
                if(name.isEmpty())continue;
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){dirs.add(name);continue;}
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular bundle entry: "+name);
                if(!Set.of("manifest.json","COMPLETED.json").contains(name)){var p=pin(path);files.put(name,p);total=Math.addExact(total,((Number)p.get("bytes")).longValue());}
            }
        }
        return new Tree(files,List.copyOf(dirs),total);
    }
    interface Outputs {
        OutputStream open(String name)throws IOException;
        Map<String,Object> pins();
        default void bytes(String name,byte[] raw)throws IOException{try(var out=open(name)){out.write(raw);}}
        default void json(String name,Object value)throws IOException{bytes(name,EnrichrWorkflowFiles.json(value));}
    }
    static final class Budget {
        private final long maximum;
        private long total;
        Budget(long maximum,long initial)throws IOException{this.maximum=maximum;reserve(initial);}
        void reserve(long bytes)throws IOException{
            if(bytes<0||bytes>maximum-total)throw new IOException("Combined output byte capacity exceeded; partials retained");
            total+=bytes;
        }
        long remaining(){return maximum-total;}
    }
    static final class Generated implements Outputs {
        private final Path root;
        private final Budget budget;
        private final Map<String,Object> files=new TreeMap<>();
        private final Set<String> opened=new HashSet<>();
        Generated(Path root,Budget budget){this.root=root;this.budget=budget;}
        @Override public Map<String,Object> pins(){return Map.copyOf(files);}
        @Override public OutputStream open(String name)throws IOException{
            if(!name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")||name.contains("..")||!opened.add(name))throw new IOException("Invalid/duplicate generated file");
            OutputStream destination;
            if(root==null)destination=OutputStream.nullOutputStream();
            else{Path path=root.resolve(name);Files.createDirectories(path.getParent());destination=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);}
            return new RecordedOutput(destination,name);
        }
        private final class RecordedOutput extends FilterOutputStream {
            private final String name;
            private final MessageDigest digest=digest();
            private long count;
            private boolean closed;
            RecordedOutput(OutputStream stream,String name){super(stream);this.name=name;}
            @Override public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
            @Override public void write(byte[] block,int offset,int length)throws IOException{
                checkInterrupted();int allowed=(int)Math.min(length,budget.remaining());
                if(allowed>0){budget.reserve(allowed);out.write(block,offset,allowed);digest.update(block,offset,allowed);count+=allowed;}
                if(allowed!=length)throw new IOException("Combined output byte capacity exceeded; captured/generated prefix retained");
            }
            @Override public void close()throws IOException{
                if(closed)return;closed=true;super.close();files.put(name,Map.of("bytes",count,"sha256",HexFormat.of().formatHex(digest.digest())));
            }
        }
    }
    static final class Table implements AutoCloseable {
        private final Writer writer;
        Table(Outputs files,String name,String...header)throws IOException{writer=new BufferedWriter(new OutputStreamWriter(files.open(name),StandardCharsets.UTF_8));row((Object[])header);}
        void row(Object...fields)throws IOException{
            for(int i=0;i<fields.length;i++){
                if(i>0)writer.write('\t');String value=String.valueOf(fields[i]);EnrichrWorkflowContracts.unicode(value);
                for(int c=0;c<value.length();c++)if(Character.isISOControl(value.charAt(c))&&"\t\r\n".indexOf(value.charAt(c))<0)throw new IOException("Unsupported table control character; raw source retained");
                boolean quote=value.indexOf('\t')>=0||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('"')>=0;
                if(quote){writer.write('"');writer.write(value.replace("\"","\"\""));writer.write('"');}else writer.write(value);
            }
            writer.write('\n');
        }
        @Override public void close()throws IOException{writer.close();}
    }
    static Map<String,Object> producer()throws IOException{
        var classes=new TreeMap<String,Object>();
        for(Class<?> type:List.of(EnrichrClient.class,EnrichrResponse.class,EnrichrWorkflowContracts.class,EnrichrWorkflowFiles.class,
            EnrichrWorkflowModel.class,EnrichrWorkflowCli.class,SimpleJson.class,NextflowJson.class,NextflowParameters.class,SeuratDifferentialContracts.class))classPins(type,classes);
        try{classPins(Class.forName("org.jlinalg.enrichr.EnrichrJson"),classes);}catch(ClassNotFoundException e){throw new Unavailable("Restore Enrichr JSON producer class",e);}
        return Map.of("selected_class_sha256",classes,"java_version",System.getProperty("java.version"),"os_name",System.getProperty("os.name"),"authentication",false);
    }
    private static void classPins(Class<?> type,Map<String,Object> result)throws IOException{
        String name=type.getName();try(var in=type.getResourceAsStream("/"+name.replace('.','/')+".class")){
            if(in==null)throw new Unavailable("Restore exact producer class "+name,null);result.put(name,sha(in.readAllBytes()));
        }
        for(Class<?> child:type.getDeclaredClasses())classPins(child,result);
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Enrichr operation interrupted; partials retained");}
}
