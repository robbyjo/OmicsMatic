/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jlinalg.genetics.KingRobustKinship;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.KingRequest.need;

/** Complete transport checks; original segment placement and selection are not independently refitted. */
final class KingAudit {
    private KingAudit() { }
    private static final String WITHIN = "FID ID1 ID2 N_SNP Z0 Phi HetHet IBS0 Kinship Error";
    private static final String BETWEEN = "FID1 ID1 FID2 ID2 N_SNP HetHet IBS0 Kinship";
    private static final String SEG = "FID1 ID1 FID2 ID2 IBD1Seg IBD2Seg PropIBD InfType";
    private static final String COORD = "FID1 ID1 FID2 ID2 IBDType Chr StartMB StopMB StartSNP StopSNP N_SNP Length";
    private static final String USABLE = "Segment Chr StartMB StopMB Length N_SNP StartSNP StopSNP";
    private static final String CONCORD = "FID1 ID1 FID2 ID2 N N_IBS0 N_IBS1 N_IBS2 Concord HomConc HetConc";
    record Sample(String fid, String iid) { }
    record Variant(int index, String chr, long bp) { }
    record Data(List<Sample> samples, Map<Sample,Integer> index, Map<String,Variant> variants,
                KingRobustKinship.Accumulator counts, long within, long unassessable) { }
    static Map<String,Object> map(Object... values) {
        var result = new LinkedHashMap<String,Object>();
        for (int i=0; i<values.length; i+=2) result.put((String)values[i], values[i+1]);
        return result;
    }
    private static String[] fields(String line) throws IOException {
        need(!line.isBlank() && line.chars().noneMatch(c -> c==0 || c==127), "Empty/invalid KING text row");
        return line.strip().split("\\s+");
    }
    static Data inputs(Path root, KingRequest.Request request, Runnable check) throws IOException {
        var limits = request.limits();
        var samples = new ArrayList<Sample>();
        var index = new HashMap<Sample,Integer>();
        var families = new HashMap<String,Integer>();
        try (var reader = Files.newBufferedReader(root.resolve("inputs/cohort.fam"), StandardCharsets.UTF_8)) {
            for (String line; (line=reader.readLine())!=null;) {
                check.run(); String[] f = fields(line);
                need(f.length==6 && samples.size()<limits.samples(), "FAM shape/sample capacity exceeded");
                Sample id = new Sample(f[0],f[1]);
                need(index.putIfAbsent(id, samples.size())==null, "Duplicate composite FID/IID; preserve IDs and correct inputs explicitly");
                samples.add(id); families.merge(f[0],1,Integer::sum);
            }
        }
        need(samples.size()>=2, "KING requires at least two genotyped participants");
        var variants = new LinkedHashMap<String,Variant>();
        int previousChr=0; long previousBp=0;
        try (var reader = Files.newBufferedReader(root.resolve("inputs/cohort.bim"), StandardCharsets.UTF_8)) {
            for (String line; (line=reader.readLine())!=null;) {
                check.run(); String[] f = fields(line);
                need(f.length==6 && variants.size()<limits.variants(), "BIM shape/variant capacity exceeded");
                need(f[0].matches("[1-9]|1[0-9]|2[0-2]"), "Require caller-selected numeric diploid autosomes1..22; no automatic chromosome selection");
                int chr = Integer.parseInt(f[0]); long bp = whole(f[3]);
                need(bp>0 && (chr>previousChr || chr==previousChr && bp>previousBp), "Require explicitly prepared strictly coordinate-sorted unique loci; no sorting/repair performed");
                need(Set.of("A","C","G","T").contains(f[4]) && Set.of("A","C","G","T").contains(f[5]) && !f[4].equals(f[5]),
                        "Require caller-selected biallelic A/C/G/T SNPs; no hidden filtering or allele recoding");
                need(variants.putIfAbsent(f[1],new Variant(variants.size(),f[0],bp))==null, "Duplicate variant ID; no identifier repair");
                previousChr=chr; previousBp=bp;
            }
        }
        need(!variants.isEmpty(), "No explicitly selected autosomal SNPs");
        int n=samples.size(), m=variants.size(); long pairCount=(long)n*(n-1)/2;
        need(pairCount<Integer.MAX_VALUE && pairCount<=limits.rows(), "Complete pair population exceeds pair/row capacity");
        need(pairCount<=limits.pairVariantOperations()/m, "Complete native pair-by-variant operation capacity exceeded");
        long estimate=Math.addExact(Math.multiplyExact(pairCount,512),Math.multiplyExact((long)m+n,512));
        need(estimate<=limits.workingBytes(), "Complete native census working-byte capacity exceeded (conservative logical estimate)");
        var pairs=new ArrayList<KingRobustKinship.Pair>();
        for(int first=1;first<n;first++)for(int second=0;second<first;second++)pairs.add(new KingRobustKinship.Pair(first,second));
        var counts=new KingRobustKinship.Accumulator(n,pairs,m,limits.workingBytes());
        int width=(n+3)/4;
        need(Files.size(root.resolve("inputs/cohort.bed"))==3L+(long)width*m, "Exact complete BED size differs from FAM/BIM");
        try(var input=Files.newInputStream(root.resolve("inputs/cohort.bed"))) {
            need(input.read()==0x6c && input.read()==0x1b && input.read()==1,"Require original variant-major PLINK BED");
            for(int i=0;i<m;i++){check.run();byte[] row=input.readNBytes(width);need(row.length==width,"Truncated BED");counts.acceptPackedBed(row);}
            need(input.read()==-1,"Unexpected extra BED bytes");
        }
        counts.finish(); long within=0,unassessable=0;
        for(int count:families.values())within+=(long)count*(count-1)/2;
        for(int i=0;i<pairs.size();i++)if(counts.estimate(i).counts().smallerHeterozygoteCount()==0)unassessable++;
        return new Data(List.copyOf(samples),Map.copyOf(index),Map.copyOf(variants),counts,within,unassessable);
    }
    private static long whole(String value) throws IOException {
        need(value.matches("0|[1-9][0-9]*"), "Expected exact nonnegative source integer");
        try{return Long.parseLong(value);}catch(NumberFormatException failure){throw new IOException("Integer capacity exceeded",failure);}
    }
    private static double number(String value) throws IOException {
        return switch(value.toLowerCase(java.util.Locale.ROOT)) {
            case "nan","-nan","+nan" -> Double.NaN;
            case "inf","+inf","infinity","+infinity" -> Double.POSITIVE_INFINITY;
            case "-inf","-infinity" -> Double.NEGATIVE_INFINITY;
            default -> {try{yield Double.parseDouble(value);}catch(NumberFormatException failure){throw new IOException("Invalid original numeric field",failure);}}
        };
    }
    private static void close(String raw,double expected,double tolerance,String role) throws IOException {
        double actual=number(raw);
        need(Double.isNaN(expected) ? Double.isNaN(actual) : Double.isInfinite(expected) ? actual==expected
                : Double.isFinite(actual) && Math.abs(actual-expected)<=tolerance+1e-12,
                "Original "+role+" differs from native complete count/coordinate arithmetic: "+raw+" versus "+expected);
    }
    private static int participant(Data data,String fid,String iid) throws IOException {
        Integer i=data.index().get(new Sample(fid,iid));
        need(i!=null,"Source identifier absent from captured original FAM; no mapping/repair inferred: "+fid+"/"+iid);return i;
    }
    private static int pair(Data data,String[] row,boolean within) throws IOException {
        int a=participant(data,row[0],row[1]);int b=participant(data,within?row[0]:row[2],within?row[2]:row[3]);
        need(a!=b,"Self-pair in original report");int hi=Math.max(a,b),lo=Math.min(a,b);
        return Math.toIntExact((long)hi*(hi-1)/2+lo);
    }
    private interface Row {void accept(String[] fields) throws IOException;}
    private static int table(Path file,String header,boolean headerless,int maximum,Row row,Runnable check) throws IOException {
        try(var input=Files.newInputStream(file);
            var decoded=file.toString().endsWith(".gz")?new GZIPInputStream(input):input;
            var reader=new BufferedReader(new InputStreamReader(decoded,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)))) {
            String line=reader.readLine();need(line!=null,"Missing source table header/first row: "+file.getFileName());
            int width=header.split(" ").length,count=0;
            if(String.join(" ",fields(line)).equals(header))line=reader.readLine();
            else need(headerless,"Unexpected source table schema: "+file.getFileName());
            for(;line!=null;line=reader.readLine()) {
                check.run();need(++count<=maximum,"Complete report row capacity exceeded");String[] f=fields(line);
                need(f.length==width,"Original table field count differs: "+file.getFileName());row.accept(f);
            }
            return count;
        }
    }
    private static Map<String,Object> kinship(Path result,Data data,KingRequest.Request request,String platform,Runnable check) throws IOException {
        var withinSeen=new HashSet<Integer>();var betweenSeen=new HashSet<Integer>();
        table(result.resolve("original.kin"),WITHIN,false,request.limits().rows(),r -> {
            int i=pair(data,r,true);need(withinSeen.add(i),"Duplicate within-family pair");
            var c=data.counts().estimate(i).counts();need(whole(r[3])==c.jointlyCalled(),"Within-family joint-call count differs");
            close(r[6],(double)c.bothHeterozygous()/c.jointlyCalled(),0.00005,"within-family HetHet");
            close(r[7],(double)c.oppositeHomozygotes()/c.jointlyCalled(),0.00005,"within-family IBS0");
            close(r[8],(c.bothHeterozygous()-2.0*c.oppositeHomozygotes())/(c.firstHeterozygotes()+c.secondHeterozygotes()),0.00005,"within-family KING-robust");
            number(r[4]);number(r[5]);number(r[9]); // Original pedigree fields retained, not independently refitted.
        },check);
        need(withinSeen.size()==data.within(),"Incomplete within-family pair population");
        boolean headerless=platform.equals("windows")&&request.degree()==0;
        int between=table(result.resolve("original.kin0"),BETWEEN,headerless,request.limits().rows(),r -> {
            need(!r[0].equals(r[2]),"Within-family pair incorrectly in between-family report");
            int i=pair(data,r,false);need(betweenSeen.add(i),"Duplicate between-family pair");var c=data.counts().estimate(i).counts();
            need(whole(r[4])==c.jointlyCalled(),"Between-family joint-call count differs");
            close(r[5],(double)c.bothHeterozygous()/c.jointlyCalled(),0.00005,"between-family HetHet");
            close(r[6],(double)c.oppositeHomozygotes()/c.jointlyCalled(),0.00005,"between-family IBS0");
            double expected=c.smallerHeterozygoteCount()==0?0:data.counts().estimate(i).coefficient();
            close(r[7],expected,0.00005,"between-family KING-robust");
        },check);
        int expected=0;
        for(int i=0;i<data.counts().pairCount();i++) {
            check.run();var p=data.counts().pair(i);
            if(data.samples().get(p.first()).fid().equals(data.samples().get(p.second()).fid()))continue;
            var c=data.counts().estimate(i).counts();double k=c.smallerHeterozygoteCount()==0?0:data.counts().estimate(i).coefficient();
            boolean selected=request.degree()==0 || k>=Math.pow(2,-request.degree()-1.5);
            // Count-based original degree filtering is checked over the entire input population.
            need(betweenSeen.contains(i)==selected,"Original degree-filtered pair population differs from complete native census");
            if(selected)expected++;
        }
        need(between==expected,"Between-family population count differs");
        return map("within_pairs",withinSeen.size(),"between_pairs",between,"windows_unfiltered_header_may_be_absent",headerless,
                "pedigree_columns_independently_refitted",false);
    }
    private static Map<String,Object> unrelated(Path result,Data data,KingRequest.Request request,Runnable check) throws IOException {
        var kept=new HashSet<Integer>();var removed=new HashSet<Integer>();
        ids(result.resolve("originalunrelated.txt"),data,kept,request.limits().rows(),check);
        ids(result.resolve("originalunrelated_toberemoved.txt"),data,removed,request.limits().rows(),check);
        need(java.util.Collections.disjoint(kept,removed)&&kept.size()+removed.size()==data.samples().size(),
                "Source keep/remove sets do not form an exact disjoint partition of original genotyped participants");
        return map("kept",kept.size(),"removed",removed.size(),"complete_original_sample_partition",true,
                "selection_independently_refitted",false,"unreported_pairs_prove_unrelatedness",false);
    }
    private static void ids(Path path,Data data,Set<Integer> values,int maximum,Runnable check) throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            for(String line;(line=reader.readLine())!=null;) {check.run();String[] f=fields(line);
                need(f.length==2&&values.size()<maximum,"Invalid source selection row/capacity");
                need(values.add(participant(data,f[0],f[1])),"Duplicate source selection participant");}
        }
    }
    private static Variant endpoint(Data data,String id,String chr,String mb) throws IOException {
        Variant v=data.variants().get(id);need(v!=null&&v.chr().equals(chr),"Original segment endpoint ID/chromosome absent from captured BIM");
        close(mb,v.bp()/1e6,0.0005,"segment endpoint Mb");return v;
    }
    private static long usable(Path result,Data data,KingRequest.Request request,Runnable check) throws IOException {
        long[] total={0};var seen=new HashSet<String>();
        table(result.resolve("originalallsegs.txt"),USABLE,false,request.limits().rows(),r -> {
            need(seen.add(r[0]),"Duplicate usable segment index");whole(r[0]);
            Variant a=endpoint(data,r[6],r[1],r[2]),b=endpoint(data,r[7],r[1],r[3]);
            need(b.index()>=a.index()&&whole(r[5])==b.index()-a.index()+1,"Usable segment SNP span differs");
            close(r[4],(b.bp()-a.bp())/1e6,0.0005,"usable segment length");
            total[0]=Math.addExact(total[0],b.bp()-a.bp());
        },check);
        need(total[0]>0,"No usable genome span for original IBD analysis");return total[0];
    }
    private static Map<String,Object> segments(Path result,Data data,KingRequest.Request request,Runnable check) throws IOException {
        long total=usable(result,data,request,check);var lengths=new HashMap<Integer,long[]>();var seen=new HashSet<String>();
        int coordinates=table(result.resolve("original.segments.gz"),COORD,false,request.limits().rows(),r -> {
            int pair=pair(data,r,false);need(Set.of("IBD1","IBD2").contains(r[4]),"Unknown original IBD segment type");
            Variant a=endpoint(data,r[8],r[5],r[6]),b=endpoint(data,r[9],r[5],r[7]);
            need(b.index()>=a.index()&&whole(r[10])==b.index()-a.index()+1,"IBD coordinate SNP span differs");
            close(r[11],(b.bp()-a.bp())/1e6,0.05,"IBD segment length");
            need(b.bp()-a.bp()>=3_000_000,"Unexpected short original IBD segment");
            need(seen.add(pair+"/"+r[4]+"/"+r[8]+"/"+r[9]),"Duplicate original IBD coordinate record");
            long[] sums=lengths.computeIfAbsent(pair,k->new long[3]);int type=r[4].equals("IBD1")?0:1;
            sums[type]=Math.addExact(sums[type],b.bp()-a.bp());sums[2]=Math.max(sums[2],b.bp()-a.bp());
        },check);
        var reported=new HashSet<Integer>();
        int pairs=table(result.resolve("original.seg"),SEG,false,request.limits().rows(),r -> {
            int pair=pair(data,r,false);need(reported.add(pair),"Duplicate original IBD pair summary");long[] sums=lengths.get(pair);
            need(sums!=null&&sums[2]>10_000_000,"IBD pair lacks a source-required long segment (>10Mb)");
            close(r[4],(double)sums[0]/total,0.00005,"IBD1 genome proportion");
            close(r[5],(double)sums[1]/total,0.00005,"IBD2 genome proportion");
            // The original computes PropIBD from its already quantized four-decimal IBD1/2 fractions.
            close(r[6],0.5*number(r[4])+number(r[5]),0.00005,"source quantized PropIBD");
            need(Set.of("Dup/MZ","PO","FS","2nd","3rd","4th","UN").contains(r[7]),"Unknown original inference label");
        },check);
        need(reported.equals(lengths.keySet()),"IBD summary/coordinate pair populations differ");
        return map("summary_pairs",pairs,"coordinate_records",coordinates,"usable_span_bp",total,
                "segment_placement_independently_refitted",false,"PropIBD_uses_quantized_source_fractions",true);
    }
    private static Map<String,Object> duplicate(Path result,Data data,KingRequest.Request request,Runnable check) throws IOException {
        Path file=result.resolve("original.con");
        if(!Files.exists(file)) {
            String stdout=utf8(bytes(result.resolve("analysis.stdout"),16_777_216));
            need(stdout.contains("No duplicates are found with heterozygote concordance rate > 80%."),"Absent concordance table without original no-duplicate declaration");
            return map("reported_pairs",0,"source_no_duplicates_declaration",true,"absence_independently_refitted",false);
        }
        var seen=new HashSet<Integer>();
        int rows=table(file,CONCORD,false,request.limits().rows(),r -> {
            int pair=pair(data,r,false);need(seen.add(pair),"Duplicate original concordance pair");var c=data.counts().estimate(pair).counts();
            need(whole(r[4])==c.jointlyCalled()&&whole(r[5])==c.oppositeHomozygotes()&&whole(r[6])==c.ibs1()&&whole(r[7])==c.ibs2(),"Original concordance hard-call counts differ");
            close(r[8],(double)c.ibs2()/c.jointlyCalled(),0.000005,"duplicate genotype concordance");
            close(r[9],(double)c.sameHomozygotes()/(c.sameHomozygotes()+c.oppositeHomozygotes()),0.000005,"duplicate homozygote concordance");
            close(r[10],(double)c.bothHeterozygous()/(c.bothHeterozygous()+c.ibs1()),0.000005,"duplicate heterozygote concordance");
        },check);
        return map("reported_pairs",rows,"complete_returned_pair_counts_verified",true,"candidate_screen_independently_refitted",false);
    }
    private static void countReport(Path path,Data data,boolean write,Runnable check) throws IOException {
        String header="FID1\tIID1\tFID2\tIID2\tN_SNP\tHET_HET\tIBS0\tHET1_HOM2\tHET2_HOM1\tNATIVE_BETWEEN_MIN_HET_KINSHIP_HEX\tNATIVE_BETWEEN_MIN_HET_DISPOSITION\tWITHIN_FAMILY";
        if(write) {
            try(var output=Files.newBufferedWriter(path,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW)) {
                output.write(header);output.newLine();
                for(int i=0;i<data.counts().pairCount();i++){check.run();output.write(countLine(data,i));output.newLine();}
            }
        } else {
            try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
                need(header.equals(reader.readLine()),"Native count/disposition schema differs");
                for(int i=0;i<data.counts().pairCount();i++){check.run();need(countLine(data,i).equals(reader.readLine()),"Complete native count/disposition row differs");}
                need(reader.readLine()==null,"Extra native count/disposition row");
            }
        }
    }
    private static String countLine(Data data,int i) {
        var p=data.counts().pair(i);var a=data.samples().get(p.first());var b=data.samples().get(p.second());
        var estimate=data.counts().estimate(i);var c=estimate.counts();
        return String.join("\t",a.fid(),a.iid(),b.fid(),b.iid(),Long.toString(c.jointlyCalled()),Long.toString(c.bothHeterozygous()),
                Long.toString(c.oppositeHomozygotes()),Long.toString(c.firstHetSecondHom()),Long.toString(c.firstHomSecondHet()),
                Double.toHexString(estimate.coefficient()),estimate.disposition().name(),Boolean.toString(a.fid().equals(b.fid())));
    }
    static Map<String,Object> verify(Path root,Path result,KingRequest.Request request,String platform,boolean writeCounts,Runnable check) throws IOException {
        String stdout=utf8(bytes(result.resolve("analysis.stdout"),16_777_216));
        need(java.util.regex.Pattern.compile("(?m)^KING 2\\.3\\.2\\b").matcher(stdout).find(),"Original KING banner differs");
        need(stdout.contains("KING ends at "),"Original KING terminal report absent");
        need(!stdout.contains("carried out instead")&&!stdout.contains("is replaced with")&&!stdout.contains("FATAL ERROR")&&!stdout.contains(" is skipped"),
                "Requested KING method was replaced, skipped or failed; original outputs remain unadmitted");
        Data data=inputs(root,request,check);
        Map<String,Object> analysis=switch(request.mode()) {
            case "kinship" -> kinship(result,data,request,platform,check);
            case "unrelated" -> unrelated(result,data,request,check);
            case "ibdseg" -> segments(result,data,request,check);
            case "duplicate" -> duplicate(result,data,request,check);
            default -> throw new IOException("Unsupported KING analysis");
        };
        countReport(root.resolve("native-pair-counts.tsv"),data,writeCounts,check);
        return map("samples",data.samples().size(),"variants",data.variants().size(),"complete_native_pairs",data.counts().pairCount(),
                "zero_smaller_heterozygosity_pairs_unassessable",data.unassessable(),"mode",request.mode(),"degree",request.degree(),"analysis",analysis);
    }
}
