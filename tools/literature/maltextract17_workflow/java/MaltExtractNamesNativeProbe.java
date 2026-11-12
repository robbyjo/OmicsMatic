/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltExtractSelection;

/** Complete native lookup output; source expected values are never inputs. */
public final class MaltExtractNamesNativeProbe {
 private MaltExtractNamesNativeProbe() { }
 private static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require map cases and NEW result TSV");Path input=Path.of(args[0]);
  try(var in=Files.newBufferedReader(input);var out=Files.newBufferedWriter(Path.of(args[1]),StandardOpenOption.CREATE_NEW)) {
   if(!"case_id\tresource_directory\ttargets_base64_file\tdisplay_ids".equals(in.readLine()))throw new IOException("Wrong map fixture schema");out.write("case_id\tkind\tkey_base64\tvalue\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Incomplete map case");StringBuilder result=new StringBuilder();
    try {
     var names=Files.readAllLines(input.getParent().resolve(f[2])).stream().map(encoded->new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8)).toList();var ids=new TreeSet<Integer>();if(!f[3].isEmpty())for(String id:f[3].split(","))ids.add(Integer.parseInt(id));MaltExtractSelection.Names map;
     try(var reader=Files.newBufferedReader(input.getParent().resolve(f[1]).resolve("ncbi.map"))){map=MaltExtractSelection.names(reader,names,ids,new MaltExtractSelection.MapLimits(200000000,3000000,1000000,10000,3000000));}
     for(var target:map.targets()){if(target.taxon()!=null)ids.add(target.taxon());result.append(f[0]).append("\tT").append(target.index()).append('\t').append(encode(target.supplied())).append('\t').append(target.taxon()==null?"MISSING":target.taxon().toString()).append('\n');}
     for(int id:ids){String name=map.displayNames().get(id);result.append(f[0]).append("\tI\t").append(encode(Integer.toString(id))).append('\t').append(name==null?"MISSING":encode(name)).append('\n');}
     out.write(result.toString());
    }catch(Exception failure){out.write(f[0]+"\tERROR\t\t\n");}
   }
  }
 }
}
