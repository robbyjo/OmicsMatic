/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltExtractSelection;
import org.jlinalg.metagenomics.MaltExtractTaxonomy;

/** Native source1.7 selector: no expected data used as inputs. */
public final class MaltExtractSelectionNativeProbe {
 private MaltExtractSelectionNativeProbe() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete cases TSV and NEW output TSV");Path cases=Path.of(args[0]);var trees=new HashMap<String,MaltExtractSelection.Model>();
  try(var in=Files.newBufferedReader(cases);var out=Files.newBufferedWriter(Path.of(args[1]),StandardOpenOption.CREATE_NEW)) {
   if(!"case_id\tresource_directory\ttarget\tkeys".equals(in.readLine()))throw new IOException("Wrong native selection schema");out.write("case_id\tselected_ids\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Incomplete native selection row");String name=cases.getParent().resolve(f[1]).toString();var tree=trees.get(name);if(tree==null){try(var input=Files.newBufferedReader(Path.of(name).resolve("ncbi.tre"))){tree=new MaltExtractSelection.Model(MaltExtractTaxonomy.read(input,new MaltExtractTaxonomy.Limits(100000000,3000000,1024)));}trees.put(name,tree);}var keys=new HashSet<Integer>();if(!f[3].isEmpty())for(String key:f[3].split(","))keys.add(Integer.parseInt(key));
    try {
     var selected=tree.sourceDescendants(Integer.parseInt(f[2]),keys);StringJoiner result=new StringJoiner(",");for(int id:selected)result.add(Integer.toString(id));out.write(f[0]+"\t"+result+"\n");
    }catch(Exception failure){out.write(f[0]+"\tERROR\n");}
   }
  }
 }
}
