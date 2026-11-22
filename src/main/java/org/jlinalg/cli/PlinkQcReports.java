/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded original QC reports. Integer counts, identities and complete families are checked. */
final class PlinkQcReports {
    private PlinkQcReports() { }
    record Missing(long missing,long opportunities) {double fraction(){return opportunities==0?Double.NaN:missing/(double)opportunities;}}
    record Frequency(String first,String second,long firstCount,long secondCount) {
        long observations(){return Math.addExact(firstCount,secondCount);}long minorCount(){return Math.min(firstCount,secondCount);}
        double minorFrequency(){return observations()==0?Double.NaN:minorCount()/(double)observations();}
    }
    record Hwe(String first,String second,long homFirst,long heterozygotes,long homSecond,String rawP,boolean validP) {
        long called(){return Math.addExact(Math.addExact(homFirst,heterozygotes),homSecond);}
        boolean informative(){return called()>0&&validP;}
        boolean degenerate(){return called()>0&&Math.addExact(2*Math.min(homFirst,homSecond),heterozygotes)<2;}
        boolean unresolvedZero(){return validP&&new BigDecimal(rawP).signum()==0;}
    }
    static List<Map<String,String>> table(Path path,int maximumRows,long maximumBytes,int maximumLine) throws IOException {
        var result=new ArrayList<Map<String,String>>();try(var stream=PlinkGlmInputs.limited(path,maximumBytes)) {
            String header=PlinkGlmInputs.line(stream,maximumLine);if(header==null)throw new IOException("Empty original QC report: "+path);String[] names=header.strip().replaceFirst("^#","").split("\\s+");var unique=new HashSet<String>();for(String name:names)if(!unique.add(name))throw new IOException("Duplicate original QC column");
            for(String line;(line=PlinkGlmInputs.line(stream,maximumLine))!=null;){if(line.isBlank())continue;if(result.size()>=maximumRows)throw new IOException("Original QC report row bound exceeded");String[] values=line.strip().split("\\s+");if(values.length!=names.length)throw new IOException("Original QC report row width differs: "+path);var row=new LinkedHashMap<String,String>();for(int i=0;i<names.length;i++)row.put(names[i],values[i]);result.add(row);}
        }return result;
    }
    static Set<String> snps(Path path,Set<String> allowed,long bytes,int lineBound) throws IOException {
        var result=new LinkedHashSet<String>();try(var stream=PlinkGlmInputs.limited(path,bytes)){for(String line;(line=PlinkGlmInputs.line(stream,lineBound))!=null;){if(line.isBlank())continue;String id=line.strip();if(!allowed.contains(id)||!result.add(id))throw new IOException("Unknown/duplicate original QC variant decision: "+id);}}return result;
    }
    static Map<PlinkQcInputs.Id,Missing> samples(Path path,Set<PlinkQcInputs.Id> allowed,boolean complete,int maximum,long bytes,int lineBound) throws IOException {
        var result=new LinkedHashMap<PlinkQcInputs.Id,Missing>();for(var row:table(path,maximum,bytes,lineBound)){var id=new PlinkQcInputs.Id(required(row,"FID"),required(row,"IID"));if(!allowed.contains(id)||result.putIfAbsent(id,missing(row))!=null)throw new IOException("Unknown/duplicate original QC sample decision");}if(complete&&!result.keySet().equals(allowed))throw new IOException("Original QC sample report omitted declared individuals");return result;
    }
    static Map<String,Missing> variants(Path path,Set<String> expected,int maximum,long bytes,int lineBound) throws IOException {
        var result=new LinkedHashMap<String,Missing>();for(var row:table(path,maximum,bytes,lineBound)){String id=required(row,row.containsKey("SNP")?"SNP":"ID");if(!expected.contains(id)||result.putIfAbsent(id,missing(row))!=null)throw new IOException("Unknown/duplicate variant missingness row");}if(!result.keySet().equals(expected))throw new IOException("Original QC missingness report omitted variants");return result;
    }
    static Map<String,Frequency> frequencies(Path path,Set<String> expected,int maximum,long bytes,int lineBound) throws IOException {
        var result=new LinkedHashMap<String,Frequency>();for(var row:table(path,maximum,bytes,lineBound)){
            boolean legacy=row.containsKey("SNP");String id=required(row,legacy?"SNP":"ID");Frequency value;
            if(legacy)value=new Frequency(required(row,"A1"),required(row,"A2"),count(row,"C1"),count(row,"C2"));
            else {long total=count(row,"OBS_CT");String alt=required(row,"ALT"),literal=required(row,"ALT_CTS");long second=literal.equals(".")&&alt.equals(".")?0:count(row,"ALT_CTS");if(second>total)throw new IOException("ALT count exceeds allele observations");value=new Frequency(required(row,"REF"),alt,total-second,second);}
            if(value.observations()%2!=0)throw new IOException("Autosomal hard-call QC requires even allele observations");if(!expected.contains(id)||result.putIfAbsent(id,value)!=null)throw new IOException("Unknown/duplicate original frequency row");
        }if(!result.keySet().equals(expected))throw new IOException("Original QC frequency report omitted variants");return result;
    }
    static Map<String,Hwe> hwe(Path path,Set<String> expected,int maximum,long bytes,int lineBound) throws IOException {
        return hwe(path,expected,maximum,bytes,lineBound,"exact");
    }
    static Map<String,Hwe> hwe(Path path,Set<String> expected,int maximum,long bytes,int lineBound,String mode) throws IOException {
        if(!Set.of("exact","midp").contains(mode))throw new IllegalArgumentException("Declare exact or midp HWE report mode");
        var result=new LinkedHashMap<String,Hwe>();for(var row:table(path,maximum,bytes,lineBound)){
            boolean legacy=row.containsKey("SNP");String id=required(row,legacy?"SNP":"ID");long first,het,second;
            if(legacy){if(!Set.of("ALL","ALL(NP)").contains(required(row,"TEST")))throw new IOException("QC HWE reference must be the explicit phenotype-free selected population");String[] geno=required(row,"GENO").split("/",-1);if(geno.length!=3)throw new IOException("Malformed original HWE genotype counts");first=number(geno[0]);het=number(geno[1]);second=number(geno[2]);}
            else {first=count(row,"HOM_A1_CT");het=count(row,"HET_A1_CT");second=count(row,"TWO_AX_CT");}
            String p=required(row,legacy||mode.equals("exact")?"P":"MIDP");boolean valid=false;if(!Set.of("NA","nan","NaN",".").contains(p)){try{var probability=new BigDecimal(p);if(probability.signum()<0||probability.compareTo(BigDecimal.ONE)>0)throw new IOException("Original HWE P-value lies outside [0,1]");valid=true;}catch(NumberFormatException failure){throw new IOException("Malformed original HWE P-value",failure);}}
            var value=new Hwe(required(row,"A1"),required(row,legacy?"A2":"AX"),first,het,second,p,valid);value.called();if(!expected.contains(id)||result.putIfAbsent(id,value)!=null)throw new IOException("Unknown/duplicate HWE row");
        }if(!result.keySet().equals(expected))throw new IOException("Original HWE report omitted variants");return result;
    }
    private static Missing missing(Map<String,String> row) throws IOException {
        long missing=count(row,row.containsKey("N_MISS")?"N_MISS":"MISSING_CT"),opportunities=count(row,row.containsKey("N_GENO")?"N_GENO":"OBS_CT");if(missing>opportunities)throw new IOException("Missing genotypes exceed opportunities");return new Missing(missing,opportunities);
    }
    private static long number(String text) throws IOException {try{long value=Long.parseLong(text);if(value<0)throw new NumberFormatException("negative");return value;}catch(NumberFormatException failure){throw new IOException("Invalid original QC integer count: "+text,failure);}}
    private static long count(Map<String,String> row,String name) throws IOException {return number(required(row,name));}
    private static String required(Map<String,String> row,String name) throws IOException {String value=row.get(name);if(value==null)throw new IOException("Missing original QC column: "+name);return value;}
}
