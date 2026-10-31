/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/** Complete component workspace accounting, including retained external preparation trees. */
final class ScviWorkspace {
    final Path root;
    private final long maximumBytes, externalBytes;
    private final int maximumMembers, externalMembers;
    private final Runnable check;
    private long bytes; private int members;
    ScviWorkspace(Path root,long maximumBytes,int maximumMembers,long externalBytes,int externalMembers,Runnable check)throws IOException {
        this.root=root.toAbsolutePath().normalize();this.maximumBytes=maximumBytes;this.maximumMembers=maximumMembers;this.externalBytes=externalBytes;this.externalMembers=externalMembers;this.check=check;
        if(maximumBytes<1||maximumMembers<1||externalBytes<0||externalMembers<0)throw new IllegalArgumentException("Invalid scVI workspace capacity");
        if(this.root.getParent()==null||!Files.isDirectory(this.root.getParent(),LinkOption.NOFOLLOW_LINKS)||!this.root.getParent().toRealPath().equals(this.root.getParent()))throw new IOException("Require a NEW scVI component directory under a canonical existing parent");
        if(externalBytes>maximumBytes)throw new IOException("Retained parent preparation trees exceed the complete scVI workspace capacity");
        bytes=externalBytes;members=externalMembers;member();Files.createDirectory(this.root);
    }
    private void member()throws IOException{check.run();if(++members>maximumMembers)throw new IOException("Complete scVI component member capacity exceeded");}
    private void add(long n)throws IOException{check.run();if(n<0||Math.addExact(bytes,n)>maximumBytes)throw new IOException("Complete scVI component workspace capacity exceeded");bytes+=n;}
    Path directory(String name)throws IOException{Path p=contained(name);member();Files.createDirectory(p);return p;}
    Path contained(String name)throws IOException{Path p=root.resolve(name).normalize();if(!p.startsWith(root)||p.equals(root))throw new IOException("scVI owned path escapes component root");return p;}
    OutputStream output(String name)throws IOException{
        Path p=contained(name);member();OutputStream out=Files.newOutputStream(p,StandardOpenOption.CREATE_NEW);
        return new FilterOutputStream(out){
            @Override public void write(int value)throws IOException{add(1);out.write(value);}
            @Override public void write(byte[] values,int offset,int length)throws IOException{java.util.Objects.checkFromIndexSize(offset,length,values.length);add(length);out.write(values,offset,length);}
        };
    }
    Writer writer(String name)throws IOException{return new BufferedWriter(new OutputStreamWriter(output(name),StandardCharsets.UTF_8));}
    void text(String name,String value)throws IOException{try(var out=output(name)){out.write(value.getBytes(StandardCharsets.UTF_8));}}
    ScviText.Snapshot copy(ScviText.Source source,String name,long maximumInput)throws IOException{
        member();var result=ScviText.copy(source,contained(name),Math.min(maximumInput,remainingBytes()),check);refresh();return result;
    }
    void delete(String name)throws IOException{Files.delete(contained(name));refresh();}
    long remainingBytes(){return maximumBytes-bytes;}
    int remainingMembers(){return maximumMembers-members;}
    void refresh()throws IOException{
        long actual=externalBytes;int count=externalMembers;
        try(var paths=Files.walk(root)){for(var it=paths.iterator();it.hasNext();){Path p=it.next();check.run();if(++count>maximumMembers||Files.isSymbolicLink(p)||!p.toRealPath().equals(p.toAbsolutePath().normalize()))throw new IOException("scVI workspace population or canonical path changed");if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))actual=Math.addExact(actual,Files.size(p));else if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonordinary scVI workspace member");if(actual>maximumBytes)throw new IOException("Complete scVI component workspace capacity exceeded");}}
        bytes=actual;members=count;
    }
    Map<String,String> inventory(ScviMatrix.Limits shape)throws IOException{refresh();var limits=new ScviMatrix.Limits(shape.inputBytes(),shape.decodedBytes(),maximumBytes,shape.retainedBytes(),maximumMembers,shape.genes(),shape.cells(),shape.entries(),shape.sortRecords(),shape.lineCharacters());return ScviMatrix.inventory(root,limits,check);}
}
