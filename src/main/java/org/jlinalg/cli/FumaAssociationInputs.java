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
import org.jlinalg.genetics.FumaKnownAssociations.*;

/** Streaming strict TSV transport and exact bounded raw snapshots for association review. */
final class FumaAssociationInputs {
    record Limits(int bytes,int rows,int line,long cells,long snapshots,long output,long outputRows) { }
    record Analysis(Source source,String model,String resource,String geneNamespace,String traitNamespace,
            String geneAnnotation,String genomeBuild) { }
    record Prepared(List<Analysis> analyses,List<Source> sources,List<Family> families,
            List<org.jlinalg.genetics.FumaKnownAssociations.Test> tests) { }
    private record Definition(String id,String source,Encoding encoding,Adjustment adjustment,
            Missing missing,ReportedZero zero,Duplicates duplicates,String probabilityDefinition) { }
    private record Captured(String role,Path path,Path snapshot,String sha,long bytes) { }
    final Map<Path,String> hashes=new LinkedHashMap<>();
    final Output output;
    private final Path directory;
    private final Limits limits;
    private final Runnable check;
    private final Map<Path,Captured> originals=new LinkedHashMap<>();
    private final List<Captured> roles=new ArrayList<>();
    private long cells,snapshotBytes,rows;
    private String context="options";

    FumaAssociationInputs(Path directory,Limits limits,Runnable check)throws IOException {
        this.directory=directory;this.limits=limits;this.check=check;
        output=new Output(limits.output(),limits.line(),limits.outputRows(),check);
        Files.createDirectory(directory.resolve("source-inputs"));
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    static byte[] carrier(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>1048576)throw new IOException("Missing, linked or oversized association options carrier: "+path);
        try(var stream=Files.newInputStream(path)){byte[] raw=stream.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Association options carrier grew beyond 1048576 bytes");return raw;}
    }
    Path capture(Path supplied,String role,int maximum)throws Exception {
        check.run();Path path=supplied.toAbsolutePath().normalize();context=role+": "+path;
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>maximum)throw new IOException("Missing, linked or oversized association "+role+" input: "+path);
        Captured source=originals.get(path);
        if(source==null){
            Path snapshot=directory.resolve("source-inputs/i"+String.format(java.util.Locale.ROOT,"%04d",originals.size()+1)+".raw");
            var digest=digest();long size=0;
            try(var input=Files.newInputStream(path);var target=Files.newOutputStream(snapshot)){
                output.begin(snapshot);byte[] buffer=new byte[8192];int length;
                while((length=input.read(buffer))!=-1){check.run();size+=length;if(size>maximum)throw new IOException("Association "+role+" input grew beyond --max-bytes");if(size>limits.snapshots()-snapshotBytes)throw new IOException("Complete association snapshots exceed --max-snapshot-bytes");output.reserve(snapshot,length);target.write(buffer,0,length);digest.update(buffer,0,length);}
            }
            snapshotBytes+=size;source=new Captured(role,path,snapshot,HexFormat.of().formatHex(digest.digest()),size);originals.put(path,source);hashes.put(path,source.sha());
        }else verify(source);
        roles.add(new Captured(role,path,source.snapshot(),source.sha(),source.bytes()));
        output.table(directory.resolve("resource-ledger.tsv"),List.of("role","source","snapshot","sha256","bytes"),roles.size(),i->{var s=roles.get(i);return new String[]{s.role(),s.path().toString(),directory.relativize(s.snapshot()).toString().replace('\\','/'),s.sha(),""+s.bytes()};},false);
        return source.snapshot();
    }
    @FunctionalInterface private interface Row {void read(String[] fields,List<String> header,int sourceRow)throws Exception;}
    private void read(Path supplied,String role,List<String> required,Row consumer)throws Exception {
        Path snapshot=capture(supplied,role,limits.bytes());
        try(var stream=new BufferedInputStream(Files.newInputStream(snapshot),8192)){
            String first=line(stream);if(first==null)throw new IOException("Missing association TSV header: "+context);
            var header=List.of(first.split("\t",257));
            if(header.size()>256||new HashSet<>(header).size()!=header.size()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Invalid/duplicate association TSV header (maximum256 columns): "+context);
            for(String name:required)if(!header.contains(name))throw new IOException("Missing association TSV column "+name+": "+context);
            count(header.size());String line;int sourceRow=0;
            while((line=line(stream))!=null){
                check.run();context=role+": "+supplied+", line "+(sourceRow+2);
                var fields=line.split("\t",257);
                if(fields.length!=header.size()||Arrays.stream(fields).anyMatch(s->s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed association TSV row: "+context);
                if(++rows>limits.rows())throw new IOException("Combined association input --max-rows exceeded: "+context);count(fields.length);consumer.read(fields,header,++sourceRow);
            }
        }
    }
    private String line(BufferedInputStream stream)throws IOException {
        var bytes=new ByteArrayOutputStream();int value;boolean any=false;
        while((value=stream.read())!=-1){any=true;if(value=='\n')break;if(bytes.size()>=limits.line())throw new IOException("Association input line exceeds --max-line-bytes: "+context);bytes.write(value);if((bytes.size()&8191)==0)check.run();}
        if(!any)return null;byte[] raw=bytes.toByteArray();int length=raw.length;if(length>0&&raw[length-1]=='\r')length--;
        try{return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,length)).toString();}catch(IOException invalid){throw new IOException("Invalid UTF-8 in association "+context,invalid);}
    }
    private void count(long size)throws IOException{if(size>limits.cells()-cells)throw new IOException("Complete association input --max-cells exceeded: "+context);cells+=size;}
    private static String field(String[] row,List<String> header,String name){return row[header.indexOf(name)];}
    private static String identity(String value)throws IOException {
        if(value.isBlank()||value.length()>1024||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require nonblank bounded association identity/metadata");return value;
    }
    private static <T extends Enum<T>> T policy(Class<T> type,String value)throws IOException {
        try{return Enum.valueOf(type,value);}catch(IllegalArgumentException invalid){throw new IOException("Unknown explicit association "+type.getSimpleName()+" policy: "+value,invalid);}
    }
    private static int expected(String value)throws IOException {
        if(!value.matches("[0-9]+"))throw new IOException("Require nonnegative integer expected_rows including missing tests");
        try{return Integer.parseInt(value);}catch(NumberFormatException invalid){throw new IOException("Association expected_rows exceeds supported integer range",invalid);}
    }
    private static Double probability(String raw)throws IOException {
        if(raw.equals("NA"))return null;if(raw.equals("-Inf"))return Double.NEGATIVE_INFINITY;
        if(!raw.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require decimal association value, NA, or explicit natural-log -Inf");
        double value=Double.parseDouble(raw);if(!Double.isFinite(value))throw new IOException("Association decimal exceeds binary64 range; retain original precision in a supported representation");
        if(value==0&&new java.math.BigDecimal(raw).signum()!=0)throw new IOException("Nonzero association decimal underflows binary64; supply a finite natural-log probability instead of inventing zero");return value;
    }
    Prepared prepare(Map<String,String> options)throws Exception {
        var analyses=new ArrayList<Analysis>();var sources=new ArrayList<Source>();
        read(Path.of(options.get("sources")),"analysis-sources",List.of("source","role","model","resource","gene_namespace","trait_namespace","gene_annotation","genome_build"),(row,header,n)->{
            var source=new Source(field(row,header,"source"),policy(Role.class,field(row,header,"role")));
            var analysis=new Analysis(source,identity(field(row,header,"model")),identity(field(row,header,"resource")),identity(field(row,header,"gene_namespace")),identity(field(row,header,"trait_namespace")),identity(field(row,header,"gene_annotation")),identity(field(row,header,"genome_build")));
            if(!analysis.geneNamespace().equals(options.get("gene-namespace")))throw new IOException("Association gene namespaces differ; harmonize and document gene IDs before matching");
            if(options.get("matching").equals("gene-trait")&&!analysis.traitNamespace().equals(options.get("trait-namespace")))throw new IOException("Association trait namespaces differ; provide documented exact trait IDs before gene-trait matching");
            analyses.add(analysis);sources.add(source);
        });
        var definitions=new LinkedHashMap<String,Definition>();var members=new LinkedHashMap<String,List<Member>>();
        read(Path.of(options.get("families")),"correction-families",List.of("family","source","encoding","adjustment","missing","reported_zero","duplicates","probability_definition"),(row,header,n)->{
            String id=identity(field(row,header,"family"));var value=new Definition(id,identity(field(row,header,"source")),policy(Encoding.class,field(row,header,"encoding")),policy(Adjustment.class,field(row,header,"adjustment")),policy(Missing.class,field(row,header,"missing")),policy(ReportedZero.class,field(row,header,"reported_zero")),policy(Duplicates.class,field(row,header,"duplicates")),identity(field(row,header,"probability_definition")));
            if(definitions.putIfAbsent(id,value)!=null)throw new IOException("Duplicate association correction family ID");members.put(id,new ArrayList<>());
        });
        read(Path.of(options.get("members")),"family-members",List.of("family","trait","context","expected_rows"),(row,header,n)->{
            String id=field(row,header,"family");if(!members.containsKey(id))throw new IOException("Unknown association member family: "+id);members.get(id).add(new Member(field(row,header,"trait"),field(row,header,"context"),expected(field(row,header,"expected_rows"))));
        });
        var families=new ArrayList<Family>();for(var d:definitions.values()){check.run();families.add(new Family(d.id(),d.source(),members.get(d.id()),d.encoding(),d.adjustment(),d.missing(),d.zero(),d.duplicates(),d.probabilityDefinition()));}
        var tests=new ArrayList<org.jlinalg.genetics.FumaKnownAssociations.Test>();
        read(Path.of(options.get("tests")),"association-tests",List.of("family","test_id","gene","trait","context","value"),(row,header,n)->tests.add(new org.jlinalg.genetics.FumaKnownAssociations.Test(field(row,header,"family"),field(row,header,"test_id"),field(row,header,"gene"),field(row,header,"trait"),field(row,header,"context"),probability(field(row,header,"value")))));
        return new Prepared(List.copyOf(analyses),List.copyOf(sources),List.copyOf(families),List.copyOf(tests));
    }
    private void verify(Captured source)throws Exception {
        for(Path path:List.of(source.path(),source.snapshot())){
            check.run();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)!=source.bytes())throw new IOException("Association source or exact snapshot changed: "+path);
            var digest=digest();long size=0;try(var input=Files.newInputStream(path)){byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1){check.run();size+=length;if(size>source.bytes())throw new IOException("Association protected input grew during verification: "+path);digest.update(buffer,0,length);}}
            if(size!=source.bytes()||!HexFormat.of().formatHex(digest.digest()).equals(source.sha()))throw new IOException("Association source or exact snapshot changed: "+path);
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
        private void reserve(Path path,long size)throws IOException{check.run();if(size>maximum-used)throw new IOException("Association scientific files and snapshots exceed --max-output-bytes; retained files are partial diagnostics");used+=size;sizes.put(path,sizes.get(path)+size);}
        @FunctionalInterface interface Values {String[] get(int index);}
        void table(Path path,List<String> header,int count,Values values,boolean countRows)throws IOException {
            if(countRows&&count>maximumRows-rows)throw new IOException("Complete association output --max-output-rows exceeded; no rows were truncated");if(countRows)rows+=count;
            begin(path);try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){write(path,writer,header.toArray(String[]::new));for(int i=0;i<count;i++)write(path,writer,values.get(i));}
        }
        private void write(Path path,java.io.Writer writer,String[] fields)throws IOException {
            long size=0;for(String field:fields){if(field==null||field.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid emitted association TSV field");size+=field.getBytes(StandardCharsets.UTF_8).length+1L;if(size>line)throw new IOException("Complete emitted association row exceeds --max-line-bytes");}reserve(path,size);writer.write(String.join("\t",fields));writer.write('\n');
        }
        long bytes(){return used;}
        long rows(){return rows;}
    }
}
