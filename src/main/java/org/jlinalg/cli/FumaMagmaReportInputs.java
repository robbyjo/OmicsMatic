/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.FumaMagmaReport;

/** Strict report TSV transport, exact source snapshots and bounded publication. */
final class FumaMagmaReportInputs {
    record Limits(int bytes,int rows,int line,long cells,long snapshots,long output) { }
    private record Source(String role,Path path,Path snapshot,String sha,long bytes) { }
    final Map<Path,String> hashes=new LinkedHashMap<>();
    final Output output;
    private final List<Source> sources=new ArrayList<>();
    private final Map<Path,Source> originals=new LinkedHashMap<>();
    private final Path directory;private final Limits limits;private final Runnable check;
    private long cells,snapshotBytes;private String context="options";
    FumaMagmaReportInputs(Path directory,Limits limits,Runnable check)throws IOException {
        this.directory=directory;this.limits=limits;this.check=check;output=new Output(limits.output,limits.line,check);Files.createDirectory(directory.resolve("source-inputs"));
    }
    static byte[] bounded(Path path,int maximum,String role)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>maximum)throw new IOException("Missing, linked or oversized MAGMA report "+role+" input: "+path);
        try(var stream=Files.newInputStream(path)){byte[] data=stream.readNBytes(maximum+1);if(data.length>maximum)throw new IOException("MAGMA report input grew beyond its byte bound: "+path);return data;}
    }
    byte[] raw(Path supplied,String role,int maximum)throws Exception {
        check.run();Path path=supplied.toAbsolutePath().normalize();context=role+": "+path;byte[] bytes=bounded(path,maximum,role);String digest=AdmixtureInputs.digest(bytes),old=hashes.putIfAbsent(path,digest);if(old!=null&&!old.equals(digest))throw new IOException("MAGMA report input changed during preparation: "+path);
        Source source=originals.get(path);
        if(source==null){if(bytes.length>limits.snapshots-snapshotBytes)throw new IOException("Complete MAGMA report snapshots exceed --max-snapshot-bytes");Path snapshot=directory.resolve("source-inputs/i"+String.format(java.util.Locale.ROOT,"%04d",originals.size()+1)+".raw");output.raw(snapshot,bytes);snapshotBytes+=bytes.length;source=new Source(role,path,snapshot,digest,bytes.length);originals.put(path,source);}
        sources.add(new Source(role,path,source.snapshot,digest,bytes.length));output.table(directory.resolve("resource-ledger.tsv"),List.of("role","source","snapshot","sha256","bytes"),sources.size(),i->{var s=sources.get(i);return new String[]{s.role,s.path.toString(),directory.relativize(s.snapshot).toString().replace('\\','/'),s.sha,""+s.bytes};});check.run();return bytes;
    }
    FumaMagmaReport.Table read(Path path,String role,int skip)throws Exception {
        byte[] bytes=raw(path,role,limits.bytes);String text;
        try{text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}catch(IOException failure){throw new IOException("Invalid UTF-8 in "+context,failure);}
        var lines=text.lines().toList();if(lines.size()<=skip||lines.size()-skip-1>limits.rows)throw new IOException("Empty header or complete MAGMA report --max-rows bound exceeded: "+path);
        for(int i=0;i<skip;i++)if(!lines.get(i).startsWith("#")||lines.get(i).getBytes(StandardCharsets.UTF_8).length>limits.line)throw new IOException("magma-skip3 requires exactly three bounded initial comment lines: "+path);
        var header=List.of(lines.get(skip).split("\t",-1));if(lines.get(skip).getBytes(StandardCharsets.UTF_8).length>limits.line||new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Invalid/duplicate MAGMA report TSV header: "+path);
        count(header.size());var rows=new ArrayList<List<String>>();
        for(int i=skip+1;i<lines.size();i++){check.run();String line=lines.get(i);String[] values=line.split("\t",-1);if(values.length!=header.size()||line.getBytes(StandardCharsets.UTF_8).length>limits.line||Arrays.stream(values).anyMatch(v->v.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed MAGMA report TSV row "+(i+1)+": "+path);count(values.length);rows.add(List.of(values));}
        return new FumaMagmaReport.Table(header,rows);
    }
    private void count(long size)throws IOException{if(size>limits.cells-cells)throw new IOException("Complete MAGMA report input cells exceed --max-cells");cells+=size;}
    void verify()throws Exception {
        check.run();for(var source:originals.values())for(Path p:List.of(source.path,source.snapshot))if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p)||!FollowupSupport.hash(p).equals(source.sha))throw new IOException("MAGMA report source or exact snapshot changed: "+p);check.run();
    }
    String context(){return context;}
    long cells(){return cells;}
    long snapshotBytes(){return snapshotBytes;}
    /** Scientific files/snapshots share a byte budget; diagnostic metadata is separate. */
    static final class Output {
        private final long maximum;private final int line;private final Runnable check;private long used;private final Map<Path,Long> sizes=new LinkedHashMap<>();
        Output(long maximum,int line,Runnable check){this.maximum=maximum;this.line=line;this.check=check;}
        private void begin(Path path){used-=sizes.getOrDefault(path,0L);sizes.put(path,0L);}
        private void reserve(Path path,long bytes)throws IOException {check.run();if(bytes>maximum-used)throw new IOException("MAGMA report scientific files and snapshots exceed --max-output-bytes; retained files are partial diagnostics");used+=bytes;sizes.put(path,sizes.get(path)+bytes);}
        void raw(Path path,byte[] bytes)throws IOException{begin(path);reserve(path,bytes.length);Files.write(path,bytes);}
        @FunctionalInterface interface Row {String[] get(int index)throws IOException;}
        void table(Path path,List<String> header,int rows,Row values)throws IOException {begin(path);try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){write(path,out,header.toArray(String[]::new));for(int i=0;i<rows;i++)write(path,out,values.get(i));}}
        void table(Path path,FumaMagmaReport.Table table)throws IOException{table(path,table.columns(),table.rows().size(),i->table.rows().get(i).toArray(String[]::new));}
        private void write(Path path,java.io.Writer out,String[] fields)throws IOException{long size=0;for(String value:fields){if(value==null||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid emitted MAGMA report field");size+=value.getBytes(StandardCharsets.UTF_8).length+1L;if(size>line)throw new IOException("Complete emitted MAGMA report row exceeds --max-line-bytes");}reserve(path,size);out.write(String.join("\t",fields));out.write('\n');}
        long bytes(){return used;}
    }
}
