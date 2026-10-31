/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.FumaLocusNovelty.Coverage;
import org.jlinalg.genetics.FumaLocusNovelty.Known;
import org.jlinalg.genetics.FumaLocusNovelty.Lead;

/** Streaming strict TSV transport and exact bounded raw snapshots for novelty review. */
final class FumaNoveltyInputs {
    record Limits(int bytes,int rows,int line,long cells,long snapshots,long output,long outputRows) { }
    record Prepared(List<Lead> leads,List<Known> known,List<Coverage> coverage) { }
    private record Source(String role,Path path,Path snapshot,String sha,long bytes) { }
    final Map<Path,String> hashes=new LinkedHashMap<>();
    final Output output;
    private final Path directory;
    private final Limits limits;
    private final Runnable check;
    private final Map<Path,Source> originals=new LinkedHashMap<>();
    private final List<Source> roles=new ArrayList<>();
    private long cells,snapshotBytes,rows;
    private String context="options";

    FumaNoveltyInputs(Path directory,Limits limits,Runnable check)throws IOException {
        this.directory=directory;this.limits=limits;this.check=check;
        output=new Output(limits.output(),limits.line(),limits.outputRows(),check);
        Files.createDirectory(directory.resolve("source-inputs"));
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static byte[] carrier(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>1048576)throw new IOException("Missing, linked or oversized novelty options carrier: "+path);
        try(var stream=Files.newInputStream(path)){byte[] raw=stream.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Novelty options carrier grew beyond 1048576 bytes");return raw;}
    }
    Path capture(Path supplied,String role,int maximum)throws Exception {
        check.run();Path path=supplied.toAbsolutePath().normalize();context=role+": "+path;
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>maximum)throw new IOException("Missing, linked or oversized novelty "+role+" input: "+path);
        Source source=originals.get(path);
        if(source==null){
            Path snapshot=directory.resolve("source-inputs/i"+String.format(java.util.Locale.ROOT,"%04d",originals.size()+1)+".raw");
            var digest=digest();long size=0;
            try(var input=Files.newInputStream(path);var target=Files.newOutputStream(snapshot)){
                output.begin(snapshot);byte[] buffer=new byte[8192];int length;
                while((length=input.read(buffer))!=-1){check.run();size+=length;if(size>maximum)throw new IOException("Novelty "+role+" input grew beyond --max-bytes");if(size>limits.snapshots()-snapshotBytes)throw new IOException("Complete novelty snapshots exceed --max-snapshot-bytes");output.reserve(snapshot,length);target.write(buffer,0,length);digest.update(buffer,0,length);}
            }
            snapshotBytes+=size;source=new Source(role,path,snapshot,HexFormat.of().formatHex(digest.digest()),size);originals.put(path,source);hashes.put(path,source.sha());
        }else verify(source);
        roles.add(new Source(role,path,source.snapshot(),source.sha(),source.bytes()));
        output.table(directory.resolve("resource-ledger.tsv"),List.of("role","source","snapshot","sha256","bytes"),roles.size(),i->{var s=roles.get(i);return new String[]{s.role(),s.path().toString(),directory.relativize(s.snapshot()).toString().replace('\\','/'),s.sha(),""+s.bytes()};},false);
        return source.snapshot();
    }
    @FunctionalInterface private interface Row {void read(String[] fields,List<String> header,int sourceRow)throws Exception;}
    private void read(Path supplied,String role,List<String> required,Row consumer)throws Exception {
        Path snapshot=capture(supplied,role,limits.bytes());
        try(var stream=new BufferedInputStream(Files.newInputStream(snapshot),8192)){
            String first=line(stream);if(first==null)throw new IOException("Missing novelty TSV header: "+context);
            var header=List.of(first.split("\t",257));
            if(header.size()>256||new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Invalid/duplicate novelty TSV header (maximum256 columns): "+context);
            for(String name:required)if(!header.contains(name))throw new IOException("Missing novelty TSV column "+name+": "+context);
            count(header.size());String line;int sourceRow=0;
            while((line=line(stream))!=null){
                check.run();context=role+": "+supplied+", line "+(sourceRow+2);
                var fields=line.split("\t",257);
                if(fields.length!=header.size()||Arrays.stream(fields).anyMatch(s->s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed novelty TSV row: "+context);
                if(++rows>limits.rows())throw new IOException("Combined novelty input --max-rows exceeded: "+context);count(fields.length);consumer.read(fields,header,++sourceRow);
            }
        }
    }
    private String line(BufferedInputStream stream)throws IOException {
        var bytes=new ByteArrayOutputStream();int value;boolean any=false;
        while((value=stream.read())!=-1){any=true;if(value=='\n')break;if(bytes.size()>=limits.line())throw new IOException("Novelty input line exceeds --max-line-bytes: "+context);bytes.write(value);if((bytes.size()&8191)==0)check.run();}
        if(!any)return null;byte[] raw=bytes.toByteArray();int length=raw.length;if(length>0&&raw[length-1]=='\r')length--;
        try{return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,length)).toString();}catch(IOException invalid){throw new IOException("Invalid UTF-8 in novelty "+context,invalid);}
    }
    private void count(long size)throws IOException{if(size>limits.cells()-cells)throw new IOException("Complete novelty input --max-cells exceeded: "+context);cells+=size;}
    private static String field(String[] row,List<String> header,String name){return row[header.indexOf(name)];}
    private static int coordinate(String value)throws IOException {
        if(!value.matches("[0-9]+"))throw new IOException("Require positive one-based integer novelty coordinates");
        long number;try{number=Long.parseLong(value);}catch(NumberFormatException invalid){throw new IOException("Novelty coordinate exceeds supported integer range",invalid);}
        if(number<1||number>Integer.MAX_VALUE)throw new IOException("Novelty coordinate outside1..2147483647");return (int)number;
    }
    Prepared prepare(Map<String,String> options)throws Exception {
        var leads=new ArrayList<Lead>();var known=new ArrayList<Known>();var coverage=new ArrayList<Coverage>();
        var positions=List.of("trait","chromosome","position");var leadHeader=new ArrayList<>(positions);leadHeader.add("locus");var knownHeader=new ArrayList<>(positions);knownHeader.add("variant");
        read(Path.of(options.get("leads")),"lead-variants",leadHeader,(row,header,n)->leads.add(new Lead(field(row,header,"locus"),field(row,header,"trait"),field(row,header,"chromosome"),coordinate(field(row,header,"position")))));
        read(Path.of(options.get("known")),"known-associations",knownHeader,(row,header,n)->known.add(new Known(field(row,header,"variant"),field(row,header,"trait"),field(row,header,"chromosome"),coordinate(field(row,header,"position")))));
        if(!options.get("coverage").equals("none"))read(Path.of(options.get("coverage")),"reference-coverage",List.of("trait","chromosome","start","end"),(row,header,n)->coverage.add(new Coverage(field(row,header,"trait"),field(row,header,"chromosome"),coordinate(field(row,header,"start")),coordinate(field(row,header,"end")))));
        return new Prepared(List.copyOf(leads),List.copyOf(known),List.copyOf(coverage));
    }
    private void verify(Source source)throws Exception {
        for(Path path:List.of(source.path(),source.snapshot())){
            check.run();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)!=source.bytes())throw new IOException("Novelty source or exact snapshot changed: "+path);
            var digest=digest();long size=0;try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1){check.run();size+=length;if(size>source.bytes())throw new IOException("Novelty protected input grew during verification: "+path);digest.update(buffer,0,length);}}
            if(size!=source.bytes()||!HexFormat.of().formatHex(digest.digest()).equals(source.sha()))throw new IOException("Novelty source or exact snapshot changed: "+path);
        }
    }
    void verify()throws Exception{for(var source:originals.values())verify(source);check.run();}
    String context(){return context;}
    long cells(){return cells;}
    long snapshotBytes(){return snapshotBytes;}
    /** Complete scientific files and snapshots share one byte budget; diagnostics remain writable. */
    static final class Output {
        private final long maximum,maximumRows;
        private final int line;
        private final Runnable check;
        private final Map<Path,Long> sizes=new LinkedHashMap<>();
        private long used,rows;
        Output(long maximum,int line,long maximumRows,Runnable check){this.maximum=maximum;this.line=line;this.maximumRows=maximumRows;this.check=check;}
        private void begin(Path path){used-=sizes.getOrDefault(path,0L);sizes.put(path,0L);}
        private void reserve(Path path,long size)throws IOException{check.run();if(size>maximum-used)throw new IOException("Novelty scientific files and snapshots exceed --max-output-bytes; retained files are partial diagnostics");used+=size;sizes.put(path,sizes.get(path)+size);}
        @FunctionalInterface interface Values {String[] get(int index);}
        void table(Path path,List<String> header,int count,Values values,boolean countRows)throws IOException {
            if(countRows&&count>maximumRows-rows)throw new IOException("Complete novelty output --max-output-rows exceeded; no rows were truncated");if(countRows)rows+=count;
            begin(path);try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){write(path,writer,header.toArray(String[]::new));for(int i=0;i<count;i++)write(path,writer,values.get(i));}
        }
        private void write(Path path,java.io.Writer writer,String[] fields)throws IOException {
            long size=0;for(String field:fields){if(field==null||field.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid emitted novelty TSV field");size+=field.getBytes(StandardCharsets.UTF_8).length+1L;if(size>line)throw new IOException("Complete emitted novelty row exceeds --max-line-bytes");}reserve(path,size);writer.write(String.join("\t",fields));writer.write('\n');
        }
        long bytes(){return used;}
        long rows(){return rows;}
    }
}
