/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native identity, coordinate and tabular contracts around the original MAGMA application. */
public final class MagmaTables {
    private MagmaTables() { }
    /** Original SNP-major genotype family, with sample count and exact SNP locations. */
    public record Panel(Path prefix,List<Path> files,List<GeneWindowMapping.Variant> variants,int samples) {
        public Panel {files=List.copyOf(files);variants=List.copyOf(variants);}
    }
    /** Inspect BED magic/size, unique FID/IID and variant identities; preserve all original bytes. */
    public static Panel panel(Path prefix,boolean nonhuman)throws IOException{
        prefix=prefix.toAbsolutePath().normalize();Path bed=Path.of(prefix+".bed"),bim=Path.of(prefix+".bim"),fam=Path.of(prefix+".fam");for(var p:List.of(bed,bim,fam))if(!Files.isRegularFile(p))throw new IOException("Missing PLINK family member: "+p);
        var ids=new HashSet<String>();try(var in=Files.newBufferedReader(fam)){String line;while((line=in.readLine())!=null){String[] f=line.trim().split("\\s+");if(f.length!=6||!GeneWindowMapping.token(f[0])||!GeneWindowMapping.token(f[1])||!ids.add(f[0]+"\t"+f[1]))throw new IOException("FAM requires six fields and unique FID/IID pairs");if(!Set.of("0","1","2").contains(f[4]))throw new IOException("FAM sex must be0/1/2");}}
        if(ids.size()<3)throw new IOException("At least three reference samples required");var variants=GeneWindowMapping.variants(bim,true,nonhuman);try(var in=Files.newInputStream(bed)){if(in.read()!=0x6c||in.read()!=0x1b||in.read()!=1)throw new IOException("BED must be PLINK SNP-major");}if(Files.size(bed)!=Math.addExact(3,Math.multiplyExact((ids.size()+3L)/4,variants.size())))throw new IOException("BED length differs from BIM/FAM counts");return new Panel(prefix,List.of(bed,bim,fam),variants,ids.size());
    }
    /** Every source summary-statistic row is retained with its exact join outcome. */
    public record Pvalue(String id,String chromosome,int position,String p,String n,String status) { }
    /** Read canonical variant_id/chromosome/position/p[/n] TSV; duplicate IDs always fail. */
    public static List<Pvalue> pvalues(Path file,Panel panel,boolean nonhuman,Long uniformN,boolean excludeUnmatched)throws IOException{
        var by=new HashMap<String,GeneWindowMapping.Variant>();panel.variants().forEach(v->by.put(v.id(),v));var seen=new HashSet<String>();var rows=new ArrayList<Pvalue>();try(var in=Files.newBufferedReader(file)){String header=in.readLine();boolean per=uniformN==null;if(!(per?"variant_id\tchromosome\tposition\tp\tn":"variant_id\tchromosome\tposition\tp").equals(header))throw new IOException("Summary-statistic header must be variant_id/chromosome/position/p"+(per?"/n":" (uniform sample-size excludes a conflicting n column)"));String line;while((line=in.readLine())!=null){String[] f=line.split("\t",-1);try{
            if(f.length!=(per?5:4)||!GeneWindowMapping.token(f[0])||!seen.add(f[0]))throw new IllegalArgumentException("Duplicate/empty variant or inconsistent columns");String chr=GeneWindowMapping.chromosome(f[1],nonhuman);int pos=Integer.parseInt(f[2]);double p=Double.parseDouble(f[3]);if(pos<1||!Double.isFinite(p)||p<=0||p>1)throw new IllegalArgumentException("Require positive position and finite0<p<=1; repair underflow/missing values explicitly");String n=per?f[4]:uniformN.toString();long count=Long.parseLong(n);if(count<3||count>Integer.MAX_VALUE)throw new IllegalArgumentException("Total GWAS N must be an integer3..2147483647, including case-control total N");var reference=by.get(f[0]);if(reference!=null&&(!reference.chromosome().equals(chr)||reference.position()!=pos))throw new IllegalArgumentException("GWAS/reference coordinate/build mismatch for "+f[0]);if(reference==null&&!excludeUnmatched)throw new IllegalArgumentException("GWAS variant absent from reference panel: "+f[0]+"; choose an appropriate panel or explicitly exclude unmatched variants");rows.add(new Pvalue(f[0],chr,pos,f[3],n,reference==null?"absent-reference":"matched"));
        }catch(IllegalArgumentException e){throw new IOException("GWAS row "+(rows.size()+2)+": "+e.getMessage(),e);}}}if(rows.stream().noneMatch(v->v.status().equals("matched")))throw new IOException("No GWAS variants matched the declared reference panel");return List.copyOf(rows);
    }
    /** A whitespace table with original text values and unique column labels. */
    public record Table(List<String> columns,List<Map<String,String>> rows) {
        public Table {columns=List.copyOf(columns);rows=rows.stream().map(Map::copyOf).toList();}
    }
    /** Read the application's human-readable output without interpreting comments as data. */
    public static Table table(Path file)throws IOException{
        List<String> columns=null;var rows=new ArrayList<Map<String,String>>();try(var in=Files.newBufferedReader(file)){String line;while((line=in.readLine())!=null){if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.trim().split("\\s+");if(columns==null){columns=List.of(f);if(new HashSet<>(columns).size()!=columns.size())throw new IOException("Duplicate result columns");}else{if(f.length!=columns.size())throw new IOException("Inconsistent upstream result field count");var row=new LinkedHashMap<String,String>();for(int i=0;i<f.length;i++)row.put(columns.get(i),f[i]);rows.add(row);}}}if(columns==null||rows.isEmpty())throw new IOException("Upstream output contains no result rows: "+file);return new Table(columns,rows);
    }
    /** Validate original gene identities/expanded coordinates and finite current-model results. */
    public static Table geneResults(Path file,List<GeneWindowMapping.Assignment> annotation)throws IOException{
        var by=new HashMap<String,GeneWindowMapping.Assignment>();annotation.forEach(a->by.put(a.gene().id(),a));Table t=table(file);if(!t.columns().containsAll(List.of("GENE","CHR","START","STOP","NSNPS","NPARAM","N","ZSTAT")))throw new IOException("Missing expected gene result columns");var seen=new HashSet<String>();for(var row:t.rows()){
            var a=by.get(row.get("GENE"));if(a==null||!seen.add(row.get("GENE"))||!row.get("CHR").equals(a.gene().chromosome())||Integer.parseInt(row.get("START"))!=a.windowStart()||Integer.parseInt(row.get("STOP"))!=a.windowEnd())throw new IOException("Gene results changed identity, location or assembly");if(Integer.parseInt(row.get("NSNPS"))<1||Integer.parseInt(row.get("NSNPS"))>a.variants().size()||number(row.get("NPARAM"))<0||number(row.get("N"))<3)throw new IOException("Invalid gene SNP/model/sample count");for(String c:t.columns())if(!Set.of("GENE","CHR").contains(c)){double value=number(row.get(c));if((c.equals("P")||c.startsWith("P_")||c.equals("PERMP"))&&(value<0||value>1))throw new IOException("Invalid gene result probability");}
        }return t;
    }
    /** Finite result value; input NA is handled by explicit upstream covariate policy instead. */
    public static double number(String text)throws IOException{try{double x=Double.parseDouble(text);if(!Double.isFinite(x))throw new NumberFormatException();return x;}catch(NumberFormatException|NullPointerException e){throw new IOException("Expected finite numeric value: "+text,e);}}
    /** Audit current raw gene/correlation rows against the original human-readable gene results. */
    public static void verifyRaw(Path file,Table results)throws IOException{
        var by=new HashMap<String,Map<String,String>>();results.rows().forEach(r->by.put(r.get("GENE"),r));var seen=new HashSet<String>();try(var in=Files.newBufferedReader(file)){if(!"# VERSION = 110".equals(in.readLine()))throw new IOException("Unexpected MAGMA raw version");if(!"# COVAR = NSAMP MAC".equals(in.readLine()))throw new IOException("Unexpected MAGMA raw covariate structure");String line;while((line=in.readLine())!=null){if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.trim().split("\\s+");if(f.length<9)throw new IOException("Incomplete gene/correlation row");var r=by.get(f[0]);if(r==null||!seen.add(f[0])||!f[1].equals(r.get("CHR"))||!f[2].equals(r.get("START"))||!f[3].equals(r.get("STOP"))||!f[4].equals(r.get("NSNPS")))throw new IOException("Raw gene/correlation identity differs from result table");for(int i=5;i<f.length;i++)number(f[i]);if(Math.abs(number(f[8])-number(r.get("ZSTAT")))>0.001)throw new IOException("Raw gene score differs beyond current output rounding");for(int i=9;i<f.length;i++)if(Math.abs(number(f[i]))>1.00001)throw new IOException("Invalid raw gene correlation");}}if(!seen.equals(by.keySet()))throw new IOException("Raw gene universe differs from gene result table");
    }
    /** Exact UTF-8 headered TSV export retaining original model-specific columns/values. */
    public static void write(Path file,Table t)throws IOException{
        try(var out=Files.newBufferedWriter(file)){out.write(String.join("\t",t.columns()));out.write('\n');for(var r:t.rows()){var f=new ArrayList<String>();for(String c:t.columns())f.add(r.get(c));out.write(String.join("\t",f));out.write('\n');}}
    }
}
