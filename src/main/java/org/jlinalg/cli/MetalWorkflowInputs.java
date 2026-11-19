/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.MetalWorkflowFiles.*;
import org.jlinalg.cli.MetalWorkflowRequest.Config;

/** Literal prepared summaries, bounded complete-population preflight, and exact capture. */
final class MetalWorkflowInputs {
    static final String HEADER="MARKER\tALLELE1\tALLELE2\tEFFECT\tPVALUE\tN\tSTDERR\tFREQ\tSTRAND";
    record Study(String id,Path file,String gc){ }
    static final class Marker {
        final String a1,a2;final BitSet studies=new BitSet();double totalN,inverseVariance;
        boolean seen;String het="-\t-\t-\t-\t-",freq="-\t-\t-\t-",primaryWeight="-",primaryOverlapN="-",primaryAllele1="",primaryAllele2="";
        Marker(String a1,String a2){this.a1=a1;this.a2=a2;}
    }
    record Population(List<Study> studies,Map<String,Marker> markers,long rows,long estimatedBytes){ }
    private MetalWorkflowInputs(){ }
    static List<Study> studies(Path manifest,Config c)throws IOException {
        var studies=new ArrayList<Study>();var ids=new HashSet<String>();var paths=new HashSet<Path>();
        try(var in=new Lines(manifest,c.integer("max_line_bytes"))){
            require("study\tfile\tgenomic_control".equals(in.next()),"Require study/file/genomic_control TSV header");
            for(String line;(line=in.next())!=null;){String[] f=line.split("\t",-1);require(f.length==3&&f[0].matches("[A-Za-z0-9_.-]{1,128}")&&ids.add(f[0]),"Invalid/duplicate study identity");require(studies.size()<c.number("max_studies"),"Complete study population exceeds declared capacity");MetalWorkflowRequest.gc(f[2]);Path p=input(manifest.getParent(),f[1],false);require(paths.add(p),"Duplicate physical summary file; no implicit double counting");studies.add(new Study(f[0],p,f[2]));}
        }
        require(!studies.isEmpty(),"Nonempty complete study list required");return List.copyOf(studies);
    }
    static Population inspect(Path request,Config c)throws IOException {
        var studies=studies(input(request.getParent(),c.get("studies"),false),c);var markers=new HashMap<String,Marker>();long rows=0,bytes=8192;
        for(int s=0;s<studies.size();s++)try(var in=new Lines(studies.get(s).file(),c.integer("max_line_bytes"))){
            require(HEADER.equals(in.next()),"Require exact nine-column prepared summary header in "+studies.get(s).id());
            long studyRows=0;
            for(String line;(line=in.next())!=null;){
                require(++rows<=c.number("max_rows"),"Complete input-row population exceeds declared capacity; no truncation");studyRows++;
                String[] f=line.split("\t",-1);require(f.length==9,"Require nine literal tab-separated fields; no omitted/extra columns");require(!f[0].isBlank()&&!f[0].matches(".*\\s.*"),"Marker identity cannot be blank or contain whitespace");
                if(c.get("allele_mode").equals("paired"))require(f[1].matches("[ACGT]")&&f[2].matches("[ACGT]")&&!f[1].equals(f[2]),"Paired mode needs two distinct uppercase single-base DNA alleles");else require(f[1].equals("-")&&f[2].equals("-"),"Non-allelic units require literal '-' allele fields");
                double n=number(f[5],"effective N");require(n>0,"Positive finite declared effective N required; no default weight");double iv=0;
                if(c.stderr()){
                    number(f[3],"effect");double se=number(f[6],"standard error");require(se>0,"Positive finite standard error required");iv=1/(se*se);require(Double.isFinite(iv)&&iv>0,"Unrepresentable source inverse variance; no cohort removed");require(f[4].equals("-"),"Unused STDERR P column must be '-'; no ignored conflicting P values");
                }else{
                    double p=number(f[4],"two-sided P");require(p>0&&p<=1,"Original sample-size input needs P in (0,1]; no zero floor or log-input inference");
                    if(!Set.of("+","-").contains(f[3]))require(number(f[3],"direction/effect")!=0||p==1,"Ambiguous zero effect with P<1; supply explicit direction");require(f[6].equals("-"),"Unused sample-size STDERR field must be '-'");
                }
                if(c.on("frequency")){double freq=number(f[7],"effect-allele frequency");require(freq>=0&&freq<=1,"Frequency must be in [0,1]");}else require(f[7].equals("-"),"Unused frequency field must be '-'");
                require(c.on("strand")?Set.of("+","-").contains(f[8]):f[8].equals("+"),"Explicit +/- strand required when enabled; otherwise declare literal '+'");
                Marker m=markers.get(f[0]);if(m==null){bytes=Math.addExact(bytes,1024L+4L*f[0].length()+8L*((studies.size()+63)/64));require(bytes<=c.number("max_metadata_bytes"),"Complete marker metadata exceeds declared estimated-byte capacity; no truncated analysis");m=new Marker(f[1],f[2]);markers.put(f[0],m);}
                require(!m.studies.get(s),"Duplicate marker within study: "+f[0]);require(c.get("allele_mode").equals("none")||compatible(f[1],f[2],m.a1,m.a2),"Incompatible literal allele pairs across studies: "+f[0]);m.studies.set(s);m.totalN+=n;m.inverseVariance+=iv;require(Double.isFinite(m.totalN)&&Double.isFinite(m.inverseVariance),"Unrepresentable complete-marker totals; no dropped studies");
            }
            require(studyRows>0,"Empty study table: "+studies.get(s).id());
        }
        return new Population(studies,markers,rows,bytes);
    }
    static boolean compatible(String a,String b,String x,String y){return (a.equals(x)&&b.equals(y))||(a.equals(y)&&b.equals(x))||(a.equals(complement(x))&&b.equals(complement(y)))||(a.equals(complement(y))&&b.equals(complement(x)));}
    private static String complement(String a){return switch(a){case "A"->"T";case "T"->"A";case "C"->"G";case "G"->"C";default->"";};}
    static Config capture(Path request,Config c,Path root)throws IOException {
        Path executable=input(request.getParent(),c.get("executable"),true),notice=input(request.getParent(),c.get("notice"),true),manifest=input(request.getParent(),c.get("studies"),false);Pin manifestBefore=pin(manifest);var studies=studies(manifest,c);require(manifestBefore.equals(pin(manifest)),"Study list changed while parsing; retain sources and retry NEW output");
        var paths=new ArrayList<Path>(List.of(request.toRealPath(),executable,notice,manifest));for(var study:studies)paths.add(study.file());
        for(Path p:paths)require(!root.startsWith(p)&&!p.startsWith(root),"Output overlaps a required source");
        long total=0;for(Path p:paths){total=Math.addExact(total,Files.size(p));require(total<=c.number("max_bytes"),"Complete captured inputs/runtime exceed declared byte budget");}
        Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("runtime"));Files.createDirectory(root.resolve("stages"));Files.createDirectory(root.resolve("native"));
        copy(request,root.resolve("REQUEST-ORIGINAL.tsv"));require(MetalWorkflowRequest.read(root.resolve("REQUEST-ORIGINAL.tsv")).values().equals(c.values()),"Request changed after parsing; no mixed declaration/input capture");copy(manifest,root.resolve("STUDIES-ORIGINAL.tsv"));require(pin(root.resolve("STUDIES-ORIGINAL.tsv")).equals(manifestBefore),"Study list changed between parsing and capture");copy(executable,root.resolve("runtime/metal"));copy(notice,root.resolve("runtime/NOTICE.txt"));require(pin(root.resolve("runtime/metal")).sha256().equals(c.get("executable_sha256")),"Original executable SHA differs from the explicit request");
        var list=new StringBuilder("study\tfile\tgenomic_control\n");for(int i=0;i<studies.size();i++){String name=String.format(Locale.ROOT,"%04d.tsv",i);copy(studies.get(i).file(),root.resolve("inputs/"+name));list.append(studies.get(i).id()).append('\t').append(name).append('\t').append(studies.get(i).gc()).append('\n');}
        Files.writeString(root.resolve("inputs/STUDIES.tsv"),list,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var values=new TreeMap<>(c.values());values.put("executable","runtime/metal");values.put("notice","runtime/NOTICE.txt");values.put("studies","inputs/STUDIES.tsv");map(root.resolve("REQUEST.tsv"),values);return MetalWorkflowRequest.parse(values);
    }
    static void originalDeclarations(Path root,Config c,Population p)throws IOException {
        var raw=MetalWorkflowRequest.read(root.resolve("REQUEST-ORIGINAL.tsv"));for(String key:MetalWorkflowRequest.KEYS)if(!Set.of("executable","notice","studies").contains(key))require(raw.get(key).equals(c.get(key)),"Original/canonical request declarations differ: "+key);
        try(var in=new Lines(root.resolve("STUDIES-ORIGINAL.tsv"),c.integer("max_line_bytes"))){require("study\tfile\tgenomic_control".equals(in.next()),"Original study-list header differs");for(var study:p.studies()){String line=in.next();require(line!=null,"Incomplete original study list");String[] f=line.split("\t",-1);require(f.length==3&&!f[1].isBlank()&&f[0].equals(study.id())&&f[2].equals(study.gc()),"Original/canonical study ordering or genomic-control declaration differs");}require(in.next()==null,"Extra original study declarations");}
    }
}
