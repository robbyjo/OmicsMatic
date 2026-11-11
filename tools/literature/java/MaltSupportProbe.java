/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltSupport;
import org.jlinalg.metagenomics.MaltTaxonomy;

/** Differential control runner; never consumes expected relocation values. */
public final class MaltSupportProbe {
 private MaltSupportProbe(){ }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete control directory and NEW output directory");Path input=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var trees=new LinkedHashMap<String,MaltSupport>();
  try(var reader=Files.newBufferedReader(input.resolve("trees.tsv"));var writer=Files.newBufferedWriter(out.resolve("trees.tsv"))) {
   if(!reader.readLine().equals("tree\ttree_file\tmap_file"))throw new IOException("Unexpected tree manifest");writer.write("tree\tnodes\n");String line;
   while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=3 || trees.containsKey(f[0]))throw new IOException("Duplicate/incomplete tree manifest");MaltTaxonomy tree;try(var raw=Files.newBufferedReader(input.resolve(f[1]))){tree=MaltTaxonomy.read(raw,new MaltTaxonomy.Limits(100000000,3000000,1024));}trees.put(f[0],new MaltSupport(tree));writer.write(f[0]+"\t"+tree.size()+"\n");}
  }
  try(var reader=Files.newBufferedReader(input.resolve("cases.tsv"));var writer=Files.newBufferedWriter(out.resolve("relocations.tsv"))) {
   if(!reader.readLine().equals("case_id\ttree\tminimum_support\tdisabled\tweights"))throw new IOException("Unexpected support case schema");writer.write("case_id\trelocations\n");String line;var seen=new HashSet<String>();
   while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=5 || !seen.add(f[0]) || !trees.containsKey(f[1]))throw new IOException("Incomplete/duplicate support case");var disabled=new HashSet<Integer>();if(!f[3].isEmpty())for(String token:f[3].split(",",-1))if(!disabled.add(Integer.valueOf(token)))throw new IOException("Duplicate disabled id");var weights=new HashMap<Integer,Float>();if(!f[4].isEmpty())for(String token:f[4].split(";",-1)){String[] row=token.split("=",-1);int id=Integer.parseInt(row[0]);if(row.length!=2 || weights.containsKey(id))throw new IOException("Duplicate/incomplete weight");weights.put(id,row[1].equals("NULL")?null:Float.valueOf(row[1]));}writer.write(f[0]+"\t"+trees.get(f[1]).relocate(weights,Integer.parseInt(f[2]),disabled)+"\n");}
  }
  System.out.println("PASS_NATIVE_MINIMUM_SUPPORT_PROBE_ONLY");
 }
}
