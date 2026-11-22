/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/** Bounded disk sorting of exact SAM half-unit tag populations, independent of C++ row order. */
final class HomerTagLedger {
    private static final String HEADER="role\tcontig\tposition1\tstrand\tstored_length\tweight\n";
    record Key(String role,String contig,int position1,char strand,int length) implements Comparable<Key> {
        Key {if(role==null||!role.matches("[A-Za-z0-9_-]{1,64}")||contig==null||!contig.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")||position1<1||position1>1_000_000_000||strand!='+'&&strand!='-'||length<1||length>1_000_000_000)throw new IllegalArgumentException("Unsafe or invalid original full tag key");}
        @Override public int compareTo(Key other){int n=role.compareTo(other.role);if(n==0)n=contig.compareTo(other.contig);if(n==0)n=Integer.compare(position1,other.position1);if(n==0)n=Character.compare(strand,other.strand);if(n==0)n=Integer.compare(length,other.length);return n;}
        String text(){return role+"\t"+contig+"\t"+position1+"\t"+strand+"\t"+length;}
    }
    record Summary(long contributions,long fullKeys,int chunks,Map<String,Long> roleHalfUnits,String sha256) {
        Summary {roleHalfUnits=Map.copyOf(roleHalfUnits);}
    }
    @FunctionalInterface interface Check {void run()throws IOException;}
    private record Chunk(Path path,String hash) { }
    private record Entry(Key key,long halfUnits,int reader) { }
    private final Path directory;
    private final int chunkKeys,maximumChunks;
    private final long maximumContributions,maximumBytes;
    private final Check check;
    private final TreeMap<Key,Long> buffer=new TreeMap<>();
    private final List<Chunk> chunks=new ArrayList<>();
    private long contributions,bytes;
    private boolean finished,failed;
    HomerTagLedger(Path directory,int chunkKeys,int maximumChunks,long maximumContributions,long maximumBytes,Check check)throws IOException {
        if(chunkKeys<1||chunkKeys>1_000_000||maximumChunks<1||maximumChunks>256||maximumContributions<1||maximumContributions>200_000_000||maximumBytes<1||check==null)throw new IllegalArgumentException("Invalid exact-tag disk-sort capacities");
        this.directory=directory;this.chunkKeys=chunkKeys;this.maximumChunks=maximumChunks;this.maximumContributions=maximumContributions;this.maximumBytes=maximumBytes;this.check=check;Files.createDirectory(directory);
    }
    void add(Key key,long halfUnits)throws IOException {
        try {
        if(finished||failed||key==null||halfUnits<1||halfUnits>(1L<<52))throw new IOException("Invalid or closed half-unit tag ledger");
        if(contributions>=maximumContributions)throw new IOException("Tag ledger contribution capacity exceeded");contributions++;if((contributions&4095)==0)check.run();
        try{buffer.merge(key,halfUnits,Math::addExact);}catch(ArithmeticException failure){throw new IOException("Tag ledger half-unit arithmetic overflow",failure);}if(buffer.size()>=chunkKeys)flush();
        }catch(IOException|RuntimeException failure){failed=true;throw failure;}
    }
    private void flush()throws IOException {
        if(buffer.isEmpty())return;if(chunks.size()>=maximumChunks)throw new IOException("Tag ledger disk-chunk capacity exceeded; raise chunk key capacity with sufficient Java heap");check.run();Path path=directory.resolve(String.format(java.util.Locale.ROOT,"chunk-%04d.tsv",chunks.size()));
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){for(var entry:buffer.entrySet()){out.write(entry.getKey().text());out.write('\t');out.write(Long.toString(entry.getValue()));out.write('\n');}}
        bytes=Math.addExact(bytes,Files.size(path));if(bytes>maximumBytes)throw new IOException("Tag ledger intermediate-byte capacity exceeded");chunks.add(new Chunk(path,FollowupSupport.hash(path)));buffer.clear();check.run();
    }
    Summary finish(Path output)throws IOException {
        if(finished||failed)throw new IOException("Tag ledger already finalized or failed");finished=true;flush();long rows=0;var totals=new TreeMap<String,Long>();var queue=new PriorityQueue<Entry>(Comparator.comparing(Entry::key).thenComparingInt(Entry::reader));
        try(var inputs=new Readers();var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            out.write(HEADER);
            for(int i=0;i<chunks.size();i++){var chunk=chunks.get(i);HomerFasta.regular(chunk.path(),maximumBytes);if(!FollowupSupport.hash(chunk.path()).equals(chunk.hash()))throw new IOException("Native tag chunk changed before merge");inputs.rows.add(Files.newBufferedReader(chunk.path(),StandardCharsets.UTF_8));var row=read(inputs.rows.get(i),i);if(row!=null)queue.add(row);}
            Key current=null;long mass=0,operations=0;
            while(!queue.isEmpty()) {
                if((++operations&4095)==0){out.flush();if(Files.size(output)>maximumBytes-bytes)throw new IOException("Tag ledger result-byte capacity exceeded");check.run();}var entry=queue.remove();
                if(current!=null&&!current.equals(entry.key())){write(out,current,mass);rows++;totals.merge(current.role(),mass,Math::addExact);mass=0;}current=entry.key();mass=Math.addExact(mass,entry.halfUnits());var next=read(inputs.rows.get(entry.reader()),entry.reader());if(next!=null){if(next.key().compareTo(entry.key())<=0)throw new IOException("Native tag chunk lost strict full-key order");queue.add(next);}
            }
            if(current!=null){write(out,current,mass);rows++;totals.merge(current.role(),mass,Math::addExact);}
        }catch(ArithmeticException failure){throw new IOException("Tag ledger aggregate half-unit arithmetic overflow",failure);}
        if(Files.size(output)>maximumBytes-bytes)throw new IOException("Tag ledger result-byte capacity exceeded");for(var chunk:chunks)if(!FollowupSupport.hash(chunk.path()).equals(chunk.hash()))throw new IOException("Native tag chunk changed during merge");check.run();return new Summary(contributions,rows,chunks.size(),totals,FollowupSupport.hash(output));
    }
    private static void write(BufferedWriter out,Key key,long mass)throws IOException{out.write(key.text());out.write('\t');out.write(Long.toString(mass/2));out.write(mass%2==0?".0":".5");out.write('\n');}
    private static Entry read(BufferedReader input,int reader)throws IOException {
        var line=new StringBuilder();int c;while((c=input.read())!=-1&&c!='\n'){if(line.length()>=4096||c<32&&c!='\t')throw new IOException("Malformed native tag chunk line");line.append((char)c);}if(c<0&&line.isEmpty())return null;String[] f=line.toString().split("\t",-1);
        try{if(f.length!=6||f[3].length()!=1)throw new IllegalArgumentException();long half=Long.parseLong(f[5]);if(half<1)throw new IllegalArgumentException();return new Entry(new Key(f[0],f[1],Integer.parseInt(f[2]),f[3].charAt(0),Integer.parseInt(f[4])),half,reader);}catch(IllegalArgumentException failure){throw new IOException("Malformed native tag chunk row",failure);}
    }
    private static final class Readers implements AutoCloseable {
        final List<BufferedReader> rows=new ArrayList<>();
        @Override public void close()throws IOException{IOException primary=null;for(var row:rows)try{row.close();}catch(IOException failure){if(primary==null)primary=failure;else primary.addSuppressed(failure);}if(primary!=null)throw primary;}
    }
}
