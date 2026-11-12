/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import NCBI_MapReader.NCBI_TreeReader;

/** Direct original1.7 getAllStrains set semantics, with complete explicit keys. */
public final class MaltExtractSelectionSourceProbe {
 private MaltExtractSelectionSourceProbe() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete cases TSV and NEW output TSV");Path cases=Paths.get(args[0]);Map<String,NCBI_TreeReader> trees=new HashMap<String,NCBI_TreeReader>();
  try(BufferedReader in=Files.newBufferedReader(cases,StandardCharsets.UTF_8);BufferedWriter out=Files.newBufferedWriter(Paths.get(args[1]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
   if(!"case_id\tresource_directory\ttarget\tkeys".equals(in.readLine()))throw new IOException("Wrong source selection schema");out.write("case_id\tselected_ids\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Incomplete source selection row");String name=cases.getParent().resolve(f[1]).toString();NCBI_TreeReader tree=trees.get(name);if(tree==null){tree=new NCBI_TreeReader(name);trees.put(name,tree);}Set<Integer> keys=new HashSet<Integer>();if(!f[3].isEmpty())for(String key:f[3].split(","))keys.add(Integer.parseInt(key));
    try {
     TreeSet<Integer> selected=new TreeSet<Integer>(tree.getAllStrains(Integer.parseInt(f[2]),keys));StringBuilder result=new StringBuilder();for(int id:selected){if(result.length()>0)result.append(',');result.append(id);}out.write(f[0]+"\t"+result+"\n");
    }catch(Exception failure){out.write(f[0]+"\tERROR\n");}
   }
  }
 }
}
