/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.assembly.MerquryAssessment;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Complete logical Meryl table and Merqury scalar/spectrum admission, without prefix sampling. */
final class MerquryTables {
    private MerquryTables() { }
    static Map<String,Long> counts(Path path,int k,long bytes,int maximum,Runnable check)throws IOException {
        require(Files.size(path)<=bytes,"Complete Meryl export exceeds byte capacity");var rows=new TreeMap<String,Long>();
        try(var in=Files.newBufferedReader(path,StandardCharsets.US_ASCII)) {
            for(String line=in.readLine();line!=null;line=in.readLine()) {
                check.run();String[] f=line.trim().split("\\s+",-1);require(f.length==2&&f[0].length()==k&&f[0].chars().allMatch(c->c=='A'||c=='C'||c=='G'||c=='T'),"Malformed complete literal Meryl print row");
                long count=positive(f[1]);require(rows.size()<maximum&&rows.putIfAbsent(f[0],count)==null,"Complete Meryl key capacity exceeded or duplicate key");
            }
        }
        return Collections.unmodifiableMap(rows);
    }
    static long positive(String value)throws IOException {require(value.matches("[1-9][0-9]{0,18}"),"Positive canonical int64 required");try{return Long.parseLong(value);}catch(NumberFormatException e){throw new IOException("Complete Meryl value exceeds int64 domain",e);}}
    static void equalCounts(Map<String,Long> expected,Map<String,Long> actual,String role)throws IOException {require(expected.equals(actual),"Complete Meryl counts differ from independent native "+role+"; no cropped, missing or extra population admitted");}
    static void spectrum(Path path,List<MerquryAssessment.Spectrum> expected,Runnable check)throws IOException {
        var rows=new HashMap<String,Long>();for(var row:expected)require(rows.put(row.copies()+"\t"+row.readMultiplicity(),row.distinct())==null,"Duplicate native spectrum bin");
        try(var in=Files.newBufferedReader(path,StandardCharsets.US_ASCII)) {
            require("Copies\tkmer_multiplicity\tCount".equals(in.readLine()),"Different original spectra-cn header");
            for(String line=in.readLine();line!=null;line=in.readLine()) {
                check.run();String[] f=line.split("\t",-1);require(f.length==3,"Malformed spectra-cn row");Long count=rows.remove(f[0]+"\t"+f[1]);require(count!=null&&count==positive(f[2]),"Changed/extra original copy-number/multiplicity spectrum bin");
            }
        }
        require(rows.isEmpty(),"Original copy-number spectrum is missing complete bins");
    }
    static void numbers(double expected,String literal,String name)throws IOException {
        double actual;String s=literal.toLowerCase(Locale.ROOT);
        try{actual=s.equals("inf")||s.equals("+inf")?Double.POSITIVE_INFINITY:s.equals("-inf")?Double.NEGATIVE_INFINITY:s.equals("nan")||s.equals("-nan")||s.equals("+nan")?Double.NaN:Double.parseDouble(literal);}
        catch(NumberFormatException e){throw new IOException("Missing/malformed original numeric result: "+name,e);}
        // gawk's original scripts print six significant digits by default.
        require(Double.isNaN(expected)?Double.isNaN(actual):Double.isInfinite(expected)?actual==expected:Double.isFinite(actual)&&Math.abs(actual-expected)<=6e-6*Math.max(Math.abs(expected),1e-300),"Original "+name+" differs from complete native count equation");
    }
    static void quality(String[] fields,String label,MerquryAssessment.Quality expected)throws IOException {
        require(fields.length==5&&fields[0].equals(label),"Missing/extra/reordered QV row");
        require(Long.parseLong(fields[1])==expected.assemblyOnlyOccurrences()&&Long.parseLong(fields[2])==expected.totalOccurrences(),"QV uses different occurrence totals");
        numbers(expected.qv(),fields[3],label+" QV");numbers(expected.error(),fields[4],label+" error");
    }
    /** Original awk may format large class sums with reduced precision before a second awk computes the rate. */
    static Map<String,Object> falseDuplicationReport(List<String> rows,String name,MerquryAssessment.FalseDuplications expected)throws IOException {
        require(expected.sourceWellDefined(),"Original false-duplication fields are undefined; absent classes must not be repaired");
        require(rows.size()==2&&rows.get(0).equals("hist\tcutoff\t1\t2\t3\t4\t>4\tdup(>1)\tall\tdup%"),"Different original false-duplication report");String[] f=rows.get(1).split("\t",-1);
        require(f.length==10&&f[0].equals("result."+name+".spectra-cn.hist")&&Long.parseLong(f[1])==expected.cutoff(),"Changed original false-duplication cutoff");
        long[] counts=expected.distinctByCopyClass();double duplicated=0,first=0;var literals=new ArrayList<String>();var exact=new ArrayList<Long>();
        for(int i=0;i<5;i++) {
            numbers((double)counts[i],f[i+2],"false-duplication copy class "+i);double value=Double.parseDouble(f[i+2]);require(value>0,"Source copy class is not positive");if(i==0)first=value;else duplicated+=value;literals.add(f[i+2]);exact.add(counts[i]);
        }
        double all=duplicated+first;numbers(duplicated,f[7],"false-duplication rounded-class total");numbers(all,f[8],"false-duplication rounded-class population");numbers(100*duplicated/all,f[9],"source staged-precision false-duplication rate");
        return AllcoolsPosteriorCompute.map("assembly",name,"cutoff",expected.cutoff(),"duplication_percent",f[9],"source_rounded_class_counts",literals,"native_exact_class_counts",exact,"native_exact_count_percentage",expected.percentage(),"source_intermediate_precision_preserved",true);
    }
}
