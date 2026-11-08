/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.PlinkBedDataset;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Literal populations, binary32 quantization and raw reference-call accounting. */
final class SmrWorkflowInputs {
    private SmrWorkflowInputs() { }
    record Variant(String id,int chr,int bp,String a1,String a2,double frequency) { }
    record Probe(String id,int chr,int bp,String gene,String orientation) { }
    record Gwas(String id,String a1,String a2,double frequency,double beta,double se,Double p,String n) { }
    record Entry(String probe,String snp,float beta,float se,String preparationBranch) {
        Entry(String probe,String snp,float beta,float se){this(probe,snp,beta,se,"original-BESD");}
        boolean missing(){return Math.abs(se+9)<1e-6;}
    }
    record Reference(Variant variant,double frequency,double otherAlleleFrequency,int called,int missing,double variance) { }
    record Qtl(List<Variant> variants,List<Probe> probes,Map<String,List<Entry>> entries,int type,long sampleSize) { }
    record Population(Map<String,Gwas> gwas,Qtl qtl,Map<String,Reference> reference,List<String> samples,Set<String> probes) { }
    static final class Budget {
        final long maximum;long used;
        Budget(long maximum){this.maximum=maximum;}
        void add(long count)throws IOException {require(count>=0&&count<=maximum-used,"Complete SMR native work exceeds maximum_native_operations; retry permitted capacity without dropping variants/probes/samples");used+=count;}
    }
    static Map<String,Gwas> gwas(Path path,SmrWorkflowRequest.Request r,Budget budget)throws IOException {
        var result=new LinkedHashMap<String,Gwas>();var rows=table(path,8,r.variants()+1L,r);String[] gh=rows.get(0);require(Set.of("N","n").contains(gh[7]),"GWAS sample-size header must be N or n");header(rows,"SNP","A1","A2","freq","b","se","p",gh[7]);
        for(int i=1;i<rows.size();i++){budget.add(1);String[] x=rows.get(i);id(x[0]);alleles(x[1],x[2]);double b=number(x[4]),se=number(x[5]);require(se>0,"GWAS SE must be positive; no replacement");Double p=x[6].equals("NA")?null:probability(x[6]);if(!x[7].equals("NA"))require(number(x[7])>0,"GWAS N must be positive or literal NA");require(result.putIfAbsent(x[0],new Gwas(x[0],x[1],x[2],frequency(x[3]),b,se,p,x[7]))==null,"Duplicate literal GWAS SNP: "+x[0]);}
        require(!result.isEmpty(),"Require GWAS observations");return Collections.unmodifiableMap(result);
    }
    static Qtl qfile(Path path,Path ledger,SmrWorkflowRequest.Request r,Budget budget)throws IOException {
        var variants=new LinkedHashMap<String,Variant>();var probes=new LinkedHashMap<String,Probe>();var entries=new LinkedHashMap<String,List<Entry>>();var seen=new HashSet<List<String>>();var rows=table(path,14,r.qtlEntries()+1L,r);String sh=rows.get(0)[12];require(Set.of("SE","se").contains(sh),"Qfile standard-error header must be SE or se");header(rows,"SNP","Chr","BP","A1","A2","Freq","Probe","Probe_Chr","Probe_bp","Gene","Orientation","b",sh,"p");
        try(var out=Files.newBufferedWriter(ledger,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("input_row\tprobe\tsnp\tbeta_input\tse_input\tbeta_binary32\tse_binary32_expected\tp_input\toriginal_preparation_branch\tsupplied_SE_changed\n");
            for(int i=1;i<rows.size();i++){
                budget.add(1);String[] x=rows.get(i);id(x[0]);id(x[6]);alleles(x[3],x[4]);Variant v=new Variant(x[0],chromosome(x[1]),position(x[2]),x[3],x[4],frequency(x[5]));Probe p=new Probe(x[6],chromosome(x[7]),position(x[8]),x[9],orientation(x[10]));require(!x[9].isEmpty(),"Missing gene label");Variant old=variants.putIfAbsent(v.id(),v);require(old==null||old.equals(v),"Inconsistent QTL SNP metadata: "+v.id());Probe prev=probes.putIfAbsent(p.id(),p);require(prev==null||prev.equals(p),"Inconsistent QTL probe metadata: "+p.id());require(variants.size()<=r.variants()&&probes.size()<=r.probesLimit(),"QTL axes exceed declared capacity");require(seen.add(List.of(p.id(),v.id())),"Duplicate QTL probe/SNP observation; no implicit collapse");
                double b=number(x[11]),se=number(x[12]);require(se>0&&Float.isFinite((float)se)&&(float)se>0,"Require positive finite representable supplied QTL SE; no replacement");Double suppliedP=x[13].equals("NA")?null:probability(x[13]);var stored=SmrQfilePreparation.store(r.version(),b,se,suppliedP);entries.computeIfAbsent(p.id(),k->new ArrayList<>()).add(new Entry(p.id(),v.id(),stored.beta(),stored.se(),stored.branch()));out.write(i+"\t"+p.id()+"\t"+v.id()+"\t"+x[11]+"\t"+x[12]+"\t"+Double.toString(stored.beta())+"\t"+Double.toString(stored.se())+"\t"+x[13]+"\t"+stored.branch()+"\t"+stored.suppliedSeChanged()+"\n");
            }
        }
        require(!entries.isEmpty(),"Require QTL observations");return new Qtl(List.copyOf(variants.values()),List.copyOf(probes.values()),Collections.unmodifiableMap(entries),-1,-9);
    }
    static Population population(Path inputs,Qtl qtl,Map<String,Gwas> gwas,SmrWorkflowRequest.Request r,Budget budget)throws IOException {
        var refs=new LinkedHashMap<String,Reference>();var samples=new ArrayList<String>();
        if(r.reference().get("kind").equals("plink-bed")){
            var bim=table(inputs.resolve("reference.bim"),6,r.variants(),r);var ids=new LinkedHashSet<String>();for(String[] x:bim){id(x[1]);chromosome(x[0]);position(x[3]);number(x[2]);alleles(x[4],x[5]);require(ids.add(x[1]),"Duplicate reference SNP ID");}
            var wanted=new HashSet<String>();for(Variant v:qtl.variants())if(gwas.containsKey(v.id())&&ids.contains(v.id()))wanted.add(v.id());
            var limits=new PlinkBedDataset.Limits(r.samples(),r.variants(),r.variants(),r.inputBytes(),Math.max(1048576,r.inputBytes()),32768);
            try(var bed=PlinkBedDataset.open(inputs.resolve("reference"),wanted,limits)){
                var composite=new HashSet<String>();for(var sample:bed.samples()){require(composite.add(sample.familyId()+":"+sample.individualId()),"Reference FID:IID concatenation collision in original SMR namespace");samples.add(sample.familyId()+"\t"+sample.individualId());}
                for(var v:bed.selectedVariants()){
                    budget.add(bed.samples().size());double[] calls=bed.readAllele1Dosages(v);double sum=0,sq=0,weighted=0,otherWeighted=0,weight=0;int called=0,chr=chromosome(v.chromosome());
                    for(int i=0;i<calls.length;i++)if(!Double.isNaN(calls[i])){double x=calls[i],w=chr==23?(bed.samples().get(i).sex()==1?.5:bed.samples().get(i).sex()==2?1:0):chr<=22?1:.5;sum+=x;sq+=x*x;weighted+=w*x;otherWeighted+=w*(2-x);weight+=w;called++;}
                    require(called>0,"Reference SNP has no called genotypes: "+v.id());require(weight>0,"Reference sex-weighted X frequency is undefined: no called genotype has FAM sex1/2; review the original sex metadata or skip dependent analysis: "+v.id());double mean=weighted/weight,otherMean=otherWeighted/weight,variance=sq/called-(sum/called)*(sum/called);refs.put(v.id(),new Reference(new Variant(v.id(),chr,Math.toIntExact(v.position()),v.allele1(),v.allele2(),mean/2),mean/2,otherMean/2,called,calls.length-called,variance));
                }
            }
        }
        var requested=new LinkedHashSet<String>();var known=new HashSet<String>();for(Probe p:qtl.probes())known.add(p.id());if(r.probes().get("kind").equals("all"))requested.addAll(known);else{for(String[] row:table(inputs.resolve("probes.list"),1,r.probesLimit(),r)){id(row[0]);require(known.contains(row[0])&&requested.add(row[0]),"Unknown/duplicate requested probe: "+row[0]);}require(!requested.isEmpty(),"Empty requested probe family");}
        return new Population(gwas,qtl,Collections.unmodifiableMap(refs),List.copyOf(samples),Collections.unmodifiableSet(requested));
    }
    static int preparation(Qtl supplied,Qtl prepared,Path ledger,Budget budget)throws IOException {
        require(supplied.variants().size()==prepared.variants().size()&&supplied.probes().size()==prepared.probes().size(),"Original QTL preparation changed complete axes");var vs=new HashMap<String,Variant>();for(Variant v:supplied.variants())vs.put(v.id(),v);for(Variant v:prepared.variants()){budget.add(1);Variant old=vs.remove(v.id());require(old!=null&&v.chr()==old.chr()&&v.bp()==old.bp()&&v.a1().equals(old.a1())&&v.a2().equals(old.a2()),"Original QTL SNP metadata differs");rounded(v.frequency(),old.frequency(),6,"prepared ESI frequency");}require(vs.isEmpty(),"Original QTL SNP population differs");var ps=new HashMap<String,Probe>();for(Probe p:supplied.probes())ps.put(p.id(),p);for(Probe p:prepared.probes())require(p.equals(ps.remove(p.id())),"Original QTL probe metadata differs");require(ps.isEmpty(),"Original QTL probe family differs");
        long present=0,missing=0;int quantileRoundingDifferences=0;
        try(var out=Files.newBufferedWriter(ledger,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("probe\tsnp\tdisposition\tbeta_binary32\toriginal_SE_binary32\tnative_expected_SE_binary32\tSE_float_ULP_difference\n");
            for(Probe p:prepared.probes()){
                var expected=new HashMap<String,Entry>();for(Entry e:supplied.entries().getOrDefault(p.id(),List.of()))expected.put(e.snp(),e);
                for(Entry e:prepared.entries().getOrDefault(p.id(),List.of())){
                    budget.add(1);Entry old=expected.remove(e.snp());long ulps=old==null?0:Math.abs((long)Float.floatToRawIntBits(e.se())-Float.floatToRawIntBits(old.se()));String disposition="source-dense-missing";
                    if(old==null){require(e.missing(),"Original preparation introduced an observation");missing++;}
                    else{boolean derived=old.preparationBranch().equals("original1.3.1-SE-derived-from-beta-and-P");require(!e.missing()&&Float.floatToRawIntBits(e.beta())==Float.floatToRawIntBits(old.beta())&&(ulps==0||derived&&ulps==1),"Original QTL beta/SE preparation differs beyond the declared exact/legacy-derived1ULP contract");disposition=ulps==0?"source-preparation-exact":"original-derived-SE-native-quantile-1ULP-difference-retained";if(ulps==1)quantileRoundingDifferences++;present++;}
                    out.write(p.id()+"\t"+e.snp()+"\t"+disposition+"\t"+e.beta()+"\t"+e.se()+"\t"+(old==null?"NA":old.se())+"\t"+ulps+"\n");
                }
                require(expected.isEmpty(),"Original preparation lost supplied QTL observations");
            }
        }
        require(present>0&&missing>=0,"No prepared observations");return quantileRoundingDifferences;
    }
    static List<String[]> table(Path path,int width,long maximum,SmrWorkflowRequest.Request r)throws IOException {
        require(Files.size(path)<=r.tableBytes(),"Complete text table exceeds maximum_table_bytes: "+path);var result=new ArrayList<String[]>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;){require(line.length()<=1048576&&!line.isBlank()&&line.indexOf('\0')<0&&!line.startsWith("\ufeff"),"Blank, oversized, BOM or NUL table row");require(result.size()<maximum,"Complete table exceeds row capacity: "+path);String[] row=line.strip().split("\\s+");require(row.length==width,"Require "+width+" whitespace-delimited fields at row "+(result.size()+1)+": "+path);for(String x:row)require(x.length()<=32768&&x.chars().noneMatch(c->c<33||c>126),"Require literal printable ASCII tokens; no identifier normalization");result.add(row);}}
        require(!result.isEmpty(),"Empty table: "+path);return result;
    }
    static void header(List<String[]> rows,String... columns)throws IOException {require(Arrays.equals(rows.get(0),columns),"Require exact source header: "+String.join(" ",columns));}
    static void id(String value)throws IOException {require(!Set.of("NA","NAN","NaN",".").contains(value),"Ambiguous original missing identifier: "+value);}
    static String orientation(String x)throws IOException {require(Set.of("+","-","N").contains(x),"Require literal one-character QTL orientation +, - or N; original source stores only the first character, no silent label shortening");return x;}
    static void alleles(String a,String b)throws IOException {require(a.matches("[A-Z]+")&&b.matches("[A-Z]+")&&!a.equals(b),"Require distinct literal uppercase allele labels; no strand repair");}
    static int chromosome(String x)throws IOException {int n=position(x);require(n<=26,"This source-bound profile uses literal integer chromosomes1..26; no inferred chromosome recoding");return n;}
    static int position(String x)throws IOException {try{int n=Integer.parseInt(x);require(n>0,"Require positive original32-bit integer coordinate");return n;}catch(NumberFormatException e){throw new IOException("Require literal positive original32-bit integer",e);}}
    static double number(String x)throws IOException {try{double n=Double.parseDouble(x);require(Double.isFinite(n),"Nonfinite supplied number; no replacement");return n;}catch(NumberFormatException e){throw new IOException("Invalid literal numeric token: "+x,e);}}
    static double probability(String x)throws IOException {double n=number(x);require(n>=0&&n<=1,"Probability outside[0,1]");return n;}
    static double frequency(String x)throws IOException {if(x.equals("NA")||x.equals("-9"))return -9;return probability(x);}
    static void rounded(double printed,double full,int digits,String field)throws IOException {
        require(Double.isFinite(printed)&&Double.isFinite(full),"Nonfinite required "+field);double quantum=full==0?0:Math.pow(10,Math.floor(Math.log10(Math.abs(full)))-digits+1);double allowance=.50001*quantum+16*Math.ulp(full);require(Math.abs(printed-full)<=allowance,"Original rounded "+field+" differs: printed="+printed+", full="+full);
    }
}
