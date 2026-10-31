/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.sequence.*;
public final class HomerGenomeNativeProbe {
 public static void main(String[] args)throws Exception {
  Path work=Path.of(args[0]);
  int homeIndex=1;for(String version:List.of("5.1","4.11","4.11.1")){var r=HomerGenomeRuntime.open(Path.of(args[homeIndex++]),"linux-x86_64-genome-"+version);r.verify();System.out.println("verified-runtime="+version);}
  var positions=new ArrayList<String[]>();for(String line:Files.readAllLines(work.resolve("positions.pos")))positions.add(line.split("\t"));
  try(var reference=new IndexedSequences(work.resolve("genome.fa"))){
   for(boolean mask:List.of(false,true))try(var out=Files.newBufferedWriter(work.resolve("native-extract-"+mask+".tsv"),StandardCharsets.UTF_8)){
    for(String[] f:positions){var p=new HomerGenomicSequences.Interval(Long.parseLong(f[2]),Long.parseLong(f[3]),f[4].charAt(0));out.write(f[0]+"\t"+HomerGenomicSequences.extract(reference.sequence(f[1]),p,mask,100000)+"\n");}
   }
  }
  for(int[] pair:new int[][]{{3,0},{4,0},{199,0},{200,0},{200,-11},{199,11}})try(var out=Files.newBufferedWriter(work.resolve("native-resize-"+pair[0]+"-"+pair[1]+".pos"))){
   for(String[] f:positions){var p=HomerGenomicSequences.resize(new HomerGenomicSequences.Interval(Long.parseLong(f[2]),Long.parseLong(f[3]),f[4].charAt(0)),pair[0],pair[1]);out.write(f[0]+"\t"+f[1]+"\t"+p.start1()+"\t"+p.end1()+"\t"+p.strand()+"\t"+f[5]+"\n");}
  }
  try(var out=Files.newBufferedWriter(work.resolve("native-frequency.tsv"));var gc=Files.newBufferedWriter(work.resolve("native-bins-2.tsv"));var cpg=Files.newBufferedWriter(work.resolve("native-bins-1.tsv"))){
   for(String line:Files.readAllLines(work.resolve("frequency-input.tsv"))){String[] f=line.split("\t");if(f[1].chars().noneMatch(c->c!='N'))continue;var q=HomerGenomicSequences.frequency(f[1]);out.write(String.format(Locale.ROOT,"%s\t%.6f\t%.6f\t%.6f\t%.6f\t%d\n",f[0],q.cpg(),q.gc(),q.ag(),q.ac(),q.canonicalBases()));gc.write(f[0]+"\t"+q.gcBin()+"\n");cpg.write(f[0]+"\t"+q.cpgBin()+"\n");}
  }
  for(double limit:new double[]{0,.5,.7,1})try(var out=Files.newBufferedWriter(work.resolve("native-filter-"+limit+".tsv"))){
   for(String line:Files.readAllLines(work.resolve("filter-input.tsv"))){String[] f=line.split("\t");if(HomerGenomicSequences.disposition(f[1],limit)==HomerGenomicSequences.Disposition.RETAINED)out.write(line+"\n");}
  }
  var members=new ArrayList<HomerGenomicSequences.Member>();var bins=new HashMap<String,Integer>();for(String line:Files.readAllLines(work.resolve("weight-bins.tsv"))){String[] f=line.split("\t");bins.put(f[0],Integer.parseInt(f[1]));}
  for(String line:Files.readAllLines(work.resolve("weight-group.tsv"))){String[] f=line.split("\t");members.add(new HomerGenomicSequences.Member(f[0],f[1].equals("1"),bins.get(f[0]),Double.parseDouble(f[2])));}
  try(var out=Files.newBufferedWriter(work.resolve("native-weights.tsv"))){for(var w:HomerGenomicSequences.weightBins(members))if(w.retained())out.write(String.format(Locale.ROOT,"%s\t%d\t%.5f\n",w.member().alias(),w.member().target()?1:0,w.weight()));}
  try(var out=Files.newBufferedWriter(work.resolve("native-rounding-weights.tsv"))){for(var w:HomerGenomicSequences.weightBins(List.of(new HomerGenomicSequences.Member("t",true,0,2.000005),new HomerGenomicSequences.Member("b",false,0,1))))out.write(String.format(Locale.ROOT,"%s\t%d\t%.5f\n",w.member().alias(),w.member().target()?1:0,w.weight()));}

 }
}
