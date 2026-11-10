/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.KingRequest.need;
import static org.jlinalg.cli.KingAudit.map;

/** Historical output checks retain discrepancies rather than reproducing erroneous source counts. */
final class KingHistoricalAudit {
    private KingHistoricalAudit() { }
    private static final String WITHIN="FID ID1 ID2 N_SNP Z0 Phi HetHet IBS0 Kinship Error";
    private static final String BETWEEN="FID1 ID1 FID2 ID2 N_SNP HetHet IBS0 Kinship";
    private static final String CONCORD="FID1 ID1 FID2 ID2 N N_IBS0 N_IBS1 N_IBS2 Concord HomConc HetConc";
    private static String[] fields(String line) throws IOException {
        need(!line.isBlank()&&line.chars().noneMatch(c->c==0||c==127),"Invalid/empty original historical row");return line.strip().split("\\s+");
    }
    private interface Row {void accept(String[] values) throws IOException;}
    private static int table(Path file,String header,boolean headerless,int maximum,Row consumer,Runnable check) throws IOException {
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            String line=reader.readLine();need(line!=null,"Missing original table header/first row");
            int width=header.split(" ").length,count=0;
            if(String.join(" ",fields(line)).equals(header))line=reader.readLine();else need(headerless,"Unexpected original historical schema");
            for(;line!=null;line=reader.readLine()) {
                check.run();need(++count<=maximum,"Historical source row capacity exceeded");String[] values=fields(line);
                need(values.length==width,"Original historical field count differs");consumer.accept(values);
            }
            return count;
        }
    }
    private static long whole(String value) throws IOException {
        need(value.matches("0|[1-9][0-9]*"),"Expected exact original nonnegative integer");
        try{return Long.parseLong(value);}catch(NumberFormatException failure){throw new IOException("Source integer capacity exceeded",failure);}
    }
    private static double number(String raw) throws IOException {
        return switch(raw.toLowerCase(java.util.Locale.ROOT)) {
            case "nan","-nan","+nan" -> Double.NaN;
            case "inf","+inf","infinity","+infinity" -> Double.POSITIVE_INFINITY;
            case "-inf","-infinity" -> Double.NEGATIVE_INFINITY;
            default -> {try{yield Double.parseDouble(raw);}catch(NumberFormatException failure){throw new IOException("Invalid original numeric field",failure);}}
        };
    }
    private static int pair(KingAudit.Data data,String[] row,boolean within) throws IOException {
        var a=data.index().get(new KingAudit.Sample(row[0],row[1]));
        var b=data.index().get(new KingAudit.Sample(within?row[0]:row[2],within?row[2]:row[3]));
        need(a!=null&&b!=null&&!a.equals(b),"Unmapped/self historical pair; no identifier repair inferred");
        int hi=Math.max(a,b),lo=Math.min(a,b);return Math.toIntExact((long)hi*(hi-1)/2+lo);
    }
    private static final class Differences {
        final List<Map<String,Object>> rows=new ArrayList<>();
        final int maximum;
        Differences(int maximum){this.maximum=maximum;}
        void exact(String role,String[] id,String raw,long expected) throws IOException {
            if(whole(raw)!=expected)add(role,id,raw,Long.toString(expected),0);
        }
        void rounded(String role,String[] id,String raw,double expected,double tolerance) throws IOException {
            double actual=number(raw);
            boolean equal=Double.isNaN(expected)?Double.isNaN(actual):Double.isInfinite(expected)?actual==expected:
                    Double.isFinite(actual)&&Math.abs(actual-expected)<=tolerance+1e-12;
            if(!equal)add(role,id,raw,Double.toHexString(expected),tolerance);
        }
        private void add(String role,String[] id,String raw,String expected,double tolerance) throws IOException {
            need(rows.size()<maximum,"Complete discrepancy report row capacity exceeded");
            rows.add(map("field",role,"source_pair",List.of(id[0],id[1],id[2],id[3]),"original_value",raw,
                    "native_expected",expected,"rounding_tolerance",tolerance));
        }
    }
    private static Map<String,Object> kinship(Path result,KingAudit.Data data,KingHistoricalRequest.Request request,String platform,Differences differences,Runnable check) throws IOException {
        var within=new HashSet<Integer>();var between=new HashSet<Integer>();
        table(result.resolve("original.kin"),WITHIN,false,request.limits().rows(),r->{
            int pair=pair(data,r,true);need(within.add(pair),"Duplicate within-family source pair");var c=data.counts().estimate(pair).counts();
            String[] id={r[0],r[1],r[0],r[2]};differences.exact("within.N_SNP",id,r[3],c.jointlyCalled());
            differences.rounded("within.HetHet",id,r[6],(double)c.bothHeterozygous()/c.jointlyCalled(),request.version().equals("2.0")?.0005:.00005);
            differences.rounded("within.IBS0",id,r[7],(double)c.oppositeHomozygotes()/c.jointlyCalled(),.00005);
            differences.rounded("within.Kinship",id,r[8],(c.bothHeterozygous()-2.0*c.oppositeHomozygotes())/(c.firstHeterozygotes()+c.secondHeterozygotes()),.00005);
            number(r[4]);number(r[5]);number(r[9]);
        },check);
        need(within.size()==data.within(),"Incomplete original within-family population");
        table(result.resolve("original.kin0"),BETWEEN,platform.equals("windows"),request.limits().rows(),r->{
            need(!r[0].equals(r[2]),"Within-family source pair in between-family report");
            int pair=pair(data,r,false);need(between.add(pair),"Duplicate between-family source pair");var c=data.counts().estimate(pair).counts();
            differences.exact("between.N_SNP",r,r[4],c.jointlyCalled());
            differences.rounded("between.HetHet",r,r[5],(double)c.bothHeterozygous()/c.jointlyCalled(),.00005);
            differences.rounded("between.IBS0",r,r[6],(double)c.oppositeHomozygotes()/c.jointlyCalled(),.00005);
            differences.rounded("between.Kinship",r,r[7],c.smallerHeterozygoteCount()==0?0:data.counts().estimate(pair).coefficient(),.00005);
        },check);
        need((long)between.size()+within.size()==data.counts().pairCount(),"Incomplete original complete pair population");
        return map("within_pairs",within.size(),"between_pairs",between.size(),"within_HetHet_decimal_places",request.version().equals("2.0")?3:4,
                "pedigree_columns_independently_refitted",false);
    }
    private static Map<String,Object> duplicate(Path result,KingAudit.Data data,KingHistoricalRequest.Request request,Differences differences,String stdout,Runnable check) throws IOException {
        Path file=result.resolve("original.con");
        if(!Files.exists(file)) {
            need(stdout.contains("No duplicates are found with heterozygote concordance rate > 80%."),"Absent original concordance table without tested no-duplicate declaration");
            return map("reported_pairs",0,"source_no_duplicates_declaration",true,"screen_completeness_independently_refitted",false);
        }
        var seen=new HashSet<Integer>();int count=table(file,CONCORD,false,request.limits().rows(),r->{
            int pair=pair(data,r,false);need(seen.add(pair),"Duplicate original concordance pair");var c=data.counts().estimate(pair).counts();
            differences.exact("duplicate.N",r,r[4],c.jointlyCalled());differences.exact("duplicate.N_IBS0",r,r[5],c.oppositeHomozygotes());
            differences.exact("duplicate.N_IBS1",r,r[6],c.ibs1());differences.exact("duplicate.N_IBS2",r,r[7],c.ibs2());
            differences.rounded("duplicate.Concord",r,r[8],(double)c.ibs2()/c.jointlyCalled(),.000005);
            differences.rounded("duplicate.HomConc",r,r[9],(double)c.sameHomozygotes()/(c.sameHomozygotes()+c.oppositeHomozygotes()),.000005);
            differences.rounded("duplicate.HetConc",r,r[10],(double)c.bothHeterozygous()/(c.bothHeterozygous()+c.ibs1()),.000005);
        },check);
        return map("reported_pairs",count,"complete_returned_counts_audited",true,"screen_completeness_independently_refitted",false);
    }
    private static void countReport(Path path,KingAudit.Data data,boolean write,Runnable check) throws IOException {
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
    private static String countLine(KingAudit.Data data,int i) {
        var p=data.counts().pair(i);var a=data.samples().get(p.first());var b=data.samples().get(p.second());
        var estimate=data.counts().estimate(i);var c=estimate.counts();
        return String.join("\t",a.fid(),a.iid(),b.fid(),b.iid(),Long.toString(c.jointlyCalled()),Long.toString(c.bothHeterozygous()),
                Long.toString(c.oppositeHomozygotes()),Long.toString(c.firstHetSecondHom()),Long.toString(c.firstHomSecondHet()),
                Double.toHexString(estimate.coefficient()),estimate.disposition().name(),Boolean.toString(a.fid().equals(b.fid())));
    }
    static Map<String,Object> verify(Path root,Path result,KingHistoricalRequest.Request request,String platform,boolean write,Runnable check) throws IOException {
        String stdout=utf8(bytes(result.resolve("analysis.stdout"),16_777_216));
        need(java.util.regex.Pattern.compile("(?m)^KING "+java.util.regex.Pattern.quote(request.version())+"\\b").matcher(stdout).find(),"Original historical version banner differs");
        boolean terminal=request.version().equals("2.0")
                ? java.util.regex.Pattern.compile("(?m)^ +ends at [^\\r\\n]+$").matcher(stdout).find()
                    && (request.mode().equals("kinship")?stdout.contains("Between-family kinship data saved in file "):
                        stdout.contains("with heterozygote concordance rate > 80%"))
                : stdout.contains("KING ends at ");
        need(terminal&&!stdout.contains("FATAL ERROR")&&!stdout.contains("carried out instead")&&!stdout.contains("is replaced with"),"Original historical method failed, was replaced or did not finish");
        KingAudit.Data data=KingAudit.inputs(root,request.base(),check);
        countReport(root.resolve("native-pair-counts.tsv"),data,write,check);
        Differences differences=new Differences(request.limits().rows());
        Map<String,Object> analysis=request.mode().equals("kinship")?kinship(result,data,request,platform,differences,check):duplicate(result,data,request,differences,stdout,check);
        var audit=map("samples",data.samples().size(),"variants",data.variants().size(),"complete_native_pairs",data.counts().pairCount(),
                "zero_smaller_heterozygosity_pairs_unassessable",data.unassessable(),"version",request.version(),"mode",request.mode(),
                "analysis",analysis,"scientific_discrepancies",differences.rows,"all_scientific_fields_passed",differences.rows.isEmpty());
        if(write)writeJson(root.resolve("scientific-audit.json"),audit);
        else need(KingHistoricalToolsCli.stable(audit).equals(KingHistoricalToolsCli.stable(json(root.resolve("scientific-audit.json"),64_000_000))),"Historical scientific audit replay differs");
        need(differences.rows.isEmpty(),"ORIGINAL KING"+request.version()+" SCIENTIFIC DISCREPANCY: "+differences.rows.size()+" fields disagree with the complete native census; no result admitted. Read scientific-audit.json and retained source reports. No repair or version/flag substitution performed.");
        return audit;
    }
}
