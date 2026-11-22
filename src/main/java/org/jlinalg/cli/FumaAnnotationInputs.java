/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.FumaVariantAnnotations;
import org.jlinalg.genetics.FumaVariantAnnotations.*;

/** Strict supplied-resource SNP annotation inputs and immutable original snapshots. */
final class FumaAnnotationInputs {
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    record Limits(int bytes,int expanded,int rows,int states,int tracks,int line,long cells,long snapshots,long output) { }
    record Data(List<String> header,List<String[]> rows) {
        void require(String... names)throws IOException{for(String name:names)if(!header.contains(name))throw new IOException("Missing annotation TSV column: "+name);}
        String get(String[] row,String name)throws IOException{int index=header.indexOf(name);if(index<0)throw new IOException("Missing annotation TSV column: "+name);return row[index];}
    }
    record Prepared(Data snps,Data consequences,Data independent,List<Variant> variants,List<Gene> genes,
            List<Annotation> annotations,List<String> stateColumns,List<Consequence> consequenceRows,List<Ld> ld,
            List<String> independentIds,List<Reference> references,List<Track> tracks) { }
    private record Source(String role,Path path,String snapshot,String sha,long bytes) { }
    final Map<Path,String> hashes=new LinkedHashMap<>();
    private final Map<Path,Source> snapshots=new LinkedHashMap<>();
    private final List<Source> roles=new ArrayList<>();
    private final Path directory;
    private final Limits limits;
    private final Runnable check;
    private long cells,snapshotBytes;
    private String context="options";
    final Output output;

    FumaAnnotationInputs(Path directory,Limits limits,Runnable check)throws IOException {
        this.directory=directory;this.limits=limits;this.check=check;output=new Output(limits.output(),limits.line(),check);Files.createDirectory(directory.resolve("source-inputs"));
    }
    byte[] raw(Path supplied,String role,int maximum)throws Exception {
        check.run();Path path=supplied.toAbsolutePath().normalize();context=role+": "+path;byte[] bytes=bounded(path,maximum,role);String digest=AdmixtureInputs.digest(bytes),old=hashes.putIfAbsent(path,digest);if(old!=null&&!old.equals(digest))throw new IOException("Input changed during SNP annotation: "+path);
        Source previous=snapshots.get(path);String snapshot;
        if(previous==null){if(bytes.length>limits.snapshots()-snapshotBytes)throw new IOException("Complete original snapshots exceed --max-snapshot-bytes");snapshot="source-inputs/"+String.format(java.util.Locale.ROOT,"i%04d.raw",snapshots.size()+1);output.raw(directory.resolve(snapshot),bytes);snapshotBytes+=bytes.length;previous=new Source(role,path,snapshot,digest,bytes.length);snapshots.put(path,previous);}else snapshot=previous.snapshot();
        roles.add(new Source(role,path,snapshot,digest,bytes.length));ledger();check.run();return bytes;
    }
    private void ledger()throws IOException {
        var rows=new ArrayList<String[]>();for(var r:roles)rows.add(new String[]{r.role(),r.path().toString(),r.snapshot(),r.sha(),""+r.bytes()});output.table(directory.resolve("resource-ledger.tsv"),List.of("role","source","snapshot","sha256","bytes"),rows);
    }
    static byte[] bounded(Path path,int maximum,String role)throws IOException {if(maximum<1||maximum>=Integer.MAX_VALUE||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path)||Files.size(path)>maximum)throw new IOException("Missing, linked or oversized SNP annotation "+role+" input: "+path);try(var stream=Files.newInputStream(path)){byte[] bytes=stream.readNBytes(maximum+1);if(bytes.length>maximum)throw new IOException("SNP annotation "+role+" input grew beyond its byte bound: "+path);return bytes;}}
    private String decode(byte[] bytes)throws IOException {String text;try{text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();}catch(IOException failure){throw new IOException("Invalid UTF-8 in "+context+": "+failure.getMessage(),failure);}if(text.indexOf('\0')>=0)throw new IOException("NUL in annotation resource "+context);return text;}
    Data read(Path path,String role)throws Exception {
        var lines=decode(raw(path,role,limits.bytes())).lines().toList();if(lines.isEmpty()||lines.size()-1>limits.rows())throw new IOException("Empty/oversized complete annotation table: "+path);var header=Arrays.asList(lines.get(0).split("\t",-1));if(lines.get(0).getBytes(StandardCharsets.UTF_8).length>limits.line()||header.stream().anyMatch(s->s.isBlank()||s.codePoints().anyMatch(Character::isISOControl))||new HashSet<>(header).size()!=header.size())throw new IOException("Invalid/duplicate annotation TSV headers: "+path);
        var rows=new ArrayList<String[]>();for(int i=1;i<lines.size();i++){check.run();String line=lines.get(i);String[] row=line.split("\t",-1);if(line.getBytes(StandardCharsets.UTF_8).length>limits.line()||row.length!=header.size()||Arrays.stream(row).anyMatch(s->s.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Malformed annotation TSV row "+(i+1)+": "+path);values(row.length);rows.add(row);}return new Data(List.copyOf(header),List.copyOf(rows));
    }
    private void values(long n)throws IOException {if(n>limits.cells()-cells)throw new IOException("Complete retained input cells exceed --max-cells");cells+=n;}
    Prepared prepare(Map<String,String> options)throws Exception {
        String duplicate=FollowupSupport.required(options,"duplicates"),single=FollowupSupport.required(options,"single-state");if(!Set.of("error","source-first").contains(duplicate))throw new IOException("--duplicates must be explicit error or source-first for gene/score resources");if(!Set.of("error","retain-matrix").contains(single))throw new IOException("--single-state must be explicit error or retain-matrix");
        var states=List.of(FollowupSupport.required(options,"states").split(",",-1));if(states.isEmpty()||states.size()>limits.states()||new HashSet<>(states).size()!=states.size()||states.stream().anyMatch(String::isBlank))throw new IOException("Require distinct explicit comma-separated --states within --max-states");if(states.size()==1&&!single.equals("retain-matrix"))throw new IOException("Original single-state apply() loses its matrix dimension; use --single-state retain-matrix only to request the documented native extension");
        var s=read(Path.of(options.get("snps")),"candidates");s.require("uniqID","chr","pos","gwasP");var variants=new ArrayList<Variant>();for(var row:s.rows()){check.run();variants.add(new Variant(id(s.get(row,"uniqID")),id(s.get(row,"chr")),coordinate(s.get(row,"pos"),false),number(s.get(row,"gwasP"),true)));}
        var g=read(Path.of(options.get("genes")),"gene-annotation");g.require("ensembl_gene_id","external_gene_name","chromosome_name","start_position","end_position");var genes=new ArrayList<Gene>();var geneIds=new HashSet<String>();for(var row:g.rows()){check.run();String gene=id(g.get(row,"ensembl_gene_id"));if(!geneIds.add(gene)&&duplicate.equals("error"))throw new IOException("Repeated gene annotation ID; explicit source-first is required: "+gene);genes.add(new Gene(gene,nullable(g.get(row,"external_gene_name")),id(g.get(row,"chromosome_name")),coordinate(g.get(row,"start_position"),false),coordinate(g.get(row,"end_position"),false)));}
        var a=read(Path.of(options.get("annotations")),"CADD-RDB-states");var expected=new ArrayList<String>(List.of("uniqID","CADD","RDB"));expected.addAll(states);if(!a.header().equals(expected))throw new IOException("Annotations require exact uniqID/CADD/RDB plus declared --states in their supplied order");var annotations=new ArrayList<Annotation>();var annotationIds=new HashSet<String>();for(var row:a.rows()){check.run();String uid=id(row[0]);if(!annotationIds.add(uid)&&duplicate.equals("error"))throw new IOException("Repeated score/state UID; explicit source-first is required: "+uid);var values=new ArrayList<Integer>();for(int i=3;i<row.length;i++){double v=number(row[i],true);if(Double.isNaN(v))values.add(null);else{if(v!=Math.rint(v)||v<1||v>15)throw new IOException("Chromatin state must be integer1..15 or exact NA: "+uid);values.add((int)v);}}annotations.add(new Annotation(uid,number(row[1],true),nullable(row[2]),values));}
        var c=read(Path.of(options.get("consequences")),"supplied-consequences");c.require("uniqID","gene","annot");var consequences=new ArrayList<Consequence>();var labels=new LinkedHashMap<String,LinkedHashSet<String>>();var lengths=new LinkedHashMap<String,Long>();for(var row:c.rows()){check.run();String uid=id(c.get(row,"uniqID")),category=nullable(c.get(row,"annot"));consequences.add(new Consequence(uid,nullable(c.get(row,"gene")),category));String label=category==null?"NA":category;if(labels.computeIfAbsent(uid,k->new LinkedHashSet<>()).add(label)){long size=lengths.getOrDefault(uid,-1L)+1+label.getBytes(StandardCharsets.UTF_8).length;if(size>limits.line())throw new IOException("Complete function-label summary exceeds --max-line-bytes for "+uid);lengths.put(uid,size);}}
        var l=read(Path.of(options.get("ld")),"original-LD-rows");l.require("SNP1","SNP2");var ld=new ArrayList<Ld>();for(var row:l.rows()){check.run();ld.add(new Ld(id(l.get(row,"SNP1")),id(l.get(row,"SNP2"))));}
        var independent=read(Path.of(options.get("independent")),"independent-variants");independent.require("uniqID");var independentIds=new ArrayList<String>();for(var row:independent.rows())independentIds.add(id(independent.get(row,"uniqID")));
        var r=read(Path.of(options.get("reference-counts")),"reference-annotation-counts");if(!r.header().equals(List.of("annot","count","prop")))throw new IOException("Reference counts require exact annot/count/prop TSV header");var references=new ArrayList<Reference>();for(var row:r.rows()){check.run();if(!row[1].matches("[0-9]+"))throw new IOException("Require nonnegative integer reference counts");references.add(new Reference(id(row[0]),Long.parseLong(row[1]),number(row[2],false)));}
        var tracks=options.get("bed-manifest").equals("none")?List.<Track>of():tracks(Path.of(options.get("bed-manifest")));return new Prepared(s,c,independent,List.copyOf(variants),List.copyOf(genes),List.copyOf(annotations),states,List.copyOf(consequences),List.copyOf(ld),List.copyOf(independentIds),List.copyOf(references),tracks);
    }
    private List<Track> tracks(Path manifest)throws Exception {
        var data=read(manifest,"BED-track-manifest");if(!data.header().equals(List.of("resource_id","path","format"))||data.rows().size()>limits.tracks())throw new IOException("BED manifest requires resource_id/path/format within --max-tracks");var tracks=new ArrayList<Track>();long totalRows=0;
        for(var row:data.rows()){
            check.run();Path path=manifest.toAbsolutePath().normalize().getParent().resolve(row[1]).normalize();if(row[1].isBlank())throw new IOException("Empty BED resource path");byte[] bytes=raw(path,"BED:"+id(row[0]),limits.bytes());String format=row[2];
            if(format.equals("bed-gzip")){try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes));var expanded=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;long size=0;while((count=input.read(buffer))!=-1){check.run();size+=count;if(size>limits.expanded())throw new IOException("Complete decompressed BED exceeds --max-expanded-bytes: "+row[0]);expanded.write(buffer,0,count);}bytes=expanded.toByteArray();}}
            else if(!format.equals("bed"))throw new IOException("BED format must be explicit bed or bed-gzip");else if(bytes.length>limits.expanded())throw new IOException("Complete BED exceeds --max-expanded-bytes");
            var regions=new ArrayList<Region>();for(String line:decode(bytes).lines().toList()){check.run();var f=line.split("\t",-1);if(f.length<3||line.getBytes(StandardCharsets.UTF_8).length>limits.line()||Arrays.stream(f).anyMatch(v->v.codePoints().anyMatch(Character::isISOControl)))throw new IOException("Require unheadered BED3+ tab-separated rows: "+row[0]);values(f.length);if(++totalRows>limits.rows())throw new IOException("Complete selected BED row population exceeds --max-rows");regions.add(new Region(id(f[0]),coordinate(f[1],true),coordinate(f[2],false)));}tracks.add(new Track(row[0],regions));
        }return List.copyOf(tracks);
    }
    void verify()throws Exception {check.run();for(var entry:snapshots.entrySet()){check.run();var value=entry.getValue();if(Files.isSymbolicLink(entry.getKey())||!FollowupSupport.hash(entry.getKey()).equals(value.sha()))throw new IOException("Input changed during SNP annotation: "+entry.getKey());if(!FollowupSupport.hash(directory.resolve(value.snapshot())).equals(value.sha()))throw new IOException("Original input snapshot changed: "+value.snapshot());}}
    long inputCells(){return cells;}
    String context(){return context;}
    long snapshotBytes(){return snapshotBytes;}
    static String nullable(String value){return value.equals("NA")?null:value;}
    static String id(String value)throws IOException {if(value.isEmpty()||value.equals("NA")||value.codePoints().anyMatch(Character::isWhitespace)||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require exact nonmissing identifier without whitespace/control characters");return value;}
    static int coordinate(String value,boolean zero)throws IOException {if(!value.matches("[0-9]+"))throw new IOException("Require integer genomic coordinates");long v=Long.parseLong(value);if(v<(zero?0:1)||v>Integer.MAX_VALUE)throw new IOException("Genomic coordinate outside supported integer range");return (int)v;}
    static double number(String value,boolean missing)throws IOException {if(missing&&value.equals("NA"))return Double.NaN;if(!NUMBER.matcher(value).matches())throw new IOException("Require finite decimal/scientific numeric value"+(missing?" or exact NA":"")+": "+value);double result=Double.parseDouble(value);if(!Double.isFinite(result))throw new IOException("Nonfinite annotation numeric value");return result;}

    /** Scientific file/snapshot budget; final diagnostic metadata remains writable after exhaustion. */
    static final class Output {
        private final long maximum;private final int line;private final Runnable check;private long used;private final Map<Path,Long> sizes=new LinkedHashMap<>();
        Output(long maximum,int line,Runnable check){this.maximum=maximum;this.line=line;this.check=check;}
        private void begin(Path path){used-=sizes.getOrDefault(path,0L);sizes.put(path,0L);}
        private void reserve(Path path,long bytes)throws IOException {check.run();if(bytes>maximum-used)throw new IOException("Scientific files and snapshots exceed --max-output-bytes; retained files are partial diagnostics");used+=bytes;sizes.put(path,sizes.get(path)+bytes);}
        void raw(Path path,byte[] bytes)throws IOException {begin(path);reserve(path,bytes.length);Files.write(path,bytes);}
        @FunctionalInterface interface Row {String[] get(int index)throws IOException;}
        void table(Path path,List<String> header,List<String[]> rows)throws IOException {table(path,header,rows.size(),rows::get);}
        void table(Path path,List<String> header,int rows,Row values)throws IOException {begin(path);try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){write(path,out,header.toArray(String[]::new));for(int i=0;i<rows;i++)write(path,out,values.get(i));}}
        private void write(Path path,java.io.Writer out,String[] fields)throws IOException {long size=0;for(String field:fields){if(field==null||field.indexOf('\t')>=0||field.indexOf('\n')>=0||field.indexOf('\r')>=0)throw new IOException("Invalid emitted annotation TSV field");size+=field.getBytes(StandardCharsets.UTF_8).length+1L;if(size>line)throw new IOException("Complete emitted annotation row exceeds --max-line-bytes");}reserve(path,size);out.write(String.join("\t",fields));out.write('\n');}
        String joinedNearest(List<Nearest> hits,boolean symbols)throws IOException {var text=new StringBuilder();long bytes=0;boolean first=true;for(var hit:hits){check.run();String value=symbols?(hit.symbol()==null?"NA":hit.symbol()):""+hit.distance();bytes+=value.getBytes(StandardCharsets.UTF_8).length+(first?0:1);if(bytes>line)throw new IOException("Complete nearest-gene summary exceeds --max-line-bytes");if(!first)text.append(':');text.append(value);first=false;}return hits.isEmpty()?"NA":text.toString();}
        long bytes(){return used;}
    }
}
