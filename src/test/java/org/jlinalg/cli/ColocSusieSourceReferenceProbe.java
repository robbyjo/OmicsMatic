/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */

package org.jlinalg.cli;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.jlinalg.coloc.*;
public final class ColocSusieSourceReferenceProbe {
 record Data(ColocSusieInput input,double[] weights){}
 static Data read(Path p)throws Exception {
  var table=DelimitedData.read(p);int signal=table.column("signal"),snp=table.column("snp"),bf=table.column("log_bf"),weight=table.column("weight");
  var rows=new LinkedHashMap<Integer,LinkedHashMap<String,Double>>();var weights=new LinkedHashMap<String,String>();
  for(var row:table.rows()){
   int idx=Integer.parseInt(row[signal]);var values=rows.computeIfAbsent(idx,k->new LinkedHashMap<>());
   if(values.put(row[snp],row[bf].equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(row[bf]))!=null)throw new IllegalArgumentException("duplicate row");
   String prior=weights.putIfAbsent(row[snp],row[weight]);if(prior!=null&&!prior.equals(row[weight]))throw new IllegalArgumentException("inconsistent weights");
  }
  var ids=List.copyOf(weights.keySet());double[] lbf=new double[rows.size()*ids.size()];int[] indices=new int[rows.size()];int k=0;
  for(var entry:rows.entrySet()){
   if(!new ArrayList<>(entry.getValue().keySet()).equals(ids))throw new IllegalArgumentException("inconsistent matrix population/order");
   indices[k]=entry.getKey()-1;for(int j=0;j<ids.size();j++)lbf[k*ids.size()+j]=entry.getValue().get(ids.get(j));k++;
  }
  double[] w=weights.values().stream().allMatch("."::equals)?null:new double[ids.size()];if(w!=null)for(int j=0;j<ids.size();j++)w[j]=Double.parseDouble(weights.get(ids.get(j)));
  return new Data(new ColocSusieInput(ids,lbf,indices),w);
 }
 public static void main(String[] args)throws Exception {
  Path input=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);
  for(String name:Files.readAllLines(input.resolve("cases.tsv"),StandardCharsets.UTF_8).subList(1,Files.readAllLines(input.resolve("cases.tsv"),StandardCharsets.UTF_8).size())){
   Path home=input.resolve(name),dest=out.resolve(name);Files.createDirectory(dest);
   try{
    var s=new HashMap<String,String>();for(var row:DelimitedData.readResult(home.resolve("settings.tsv")).rows())s.put(row[0],row[1]);
    var a=read(home.resolve("trait1.tsv"));var b=read(home.resolve("trait2.tsv"));
    var options=new ColocOptions(Double.parseDouble(s.getOrDefault("p1","1e-4")),Double.parseDouble(s.getOrDefault("p2","1e-4")),Double.parseDouble(s.getOrDefault("p12","5e-6")),Double.parseDouble(s.getOrDefault("overlap","0.5")),Boolean.parseBoolean(s.getOrDefault("trim","True")),a.weights(),b.weights());
    var result=ColocSusie.analyze(a.input(),b.input(),options);
    var pairs=new StringBuilder("nsnps\thit1\thit2\tPP.H0.abf\tPP.H1.abf\tPP.H2.abf\tPP.H3.abf\tPP.H4.abf\tidx1\tidx2\n");
    for(var pair:result.signalPairs()){
     pairs.append(pair.variants()).append('\t').append(pair.trait1LeadVariant()).append('\t').append(pair.trait2LeadVariant());
     for(double pp:pair.hypothesisPosteriors())pairs.append('\t').append(pp);pairs.append('\t').append(pair.trait1EffectIndex()+1).append('\t').append(pair.trait2EffectIndex()+1).append('\n');
    }
    Files.writeString(dest.resolve("pairs.tsv"),pairs,StandardCharsets.UTF_8);
    var variants=new StringBuilder("snp");for(int k=0;k<result.signalPairs().size();k++)variants.append('\t').append(result.signalPairs().size()==1?"SNP.PP.H4.abf":"SNP.PP.H4.row"+(k+1));variants.append('\n');
    for(int j=0;j<result.commonVariants().size();j++){
     variants.append(result.commonVariants().get(j));for(int k=0;k<result.signalPairs().size();k++)variants.append('\t').append(result.sharedVariantPosterior(k)[j]);variants.append('\n');
    }
    Files.writeString(dest.resolve("variants.tsv"),variants,StandardCharsets.UTF_8);Files.write(dest.resolve("warnings.txt"),result.warnings(),StandardCharsets.UTF_8);Files.writeString(dest.resolve("STATUS.txt"),"0\n");
   }catch(Exception e){Files.writeString(dest.resolve("ERROR.txt"),e.toString()+"\n");Files.writeString(dest.resolve("STATUS.txt"),"2\n");}
  }
 }
}
