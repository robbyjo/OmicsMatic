/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import NCBI_MapReader.NCBI_MapReader;

/** Direct original1.7 complete resource lookup with source target-file trimming. */
public final class MaltExtractNamesSourceProbe {
 private MaltExtractNamesSourceProbe() { }
 private static String encode(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require map cases and NEW result TSV");Path input=Paths.get(args[0]);
  try(BufferedReader in=Files.newBufferedReader(input,StandardCharsets.UTF_8);BufferedWriter out=Files.newBufferedWriter(Paths.get(args[1]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
   if(!"case_id\tresource_directory\ttargets_base64_file\tdisplay_ids".equals(in.readLine()))throw new IOException("Wrong map fixture schema");out.write("case_id\tkind\tkey_base64\tvalue\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Incomplete map case");StringBuilder result=new StringBuilder();
    try {
     NCBI_MapReader map=new NCBI_MapReader(input.getParent().resolve(f[1]).toString());List<String> names=Files.readAllLines(input.getParent().resolve(f[2]),StandardCharsets.UTF_8);TreeSet<Integer> ids=new TreeSet<Integer>();if(!f[3].isEmpty())for(String id:f[3].split(","))ids.add(Integer.parseInt(id));int i=0;
     for(String encoded:names){String raw=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);Integer id=map.getNcbiNameToIdMap().get(raw.trim());if(id!=null)ids.add(id);result.append(f[0]).append("\tT").append(i++).append('\t').append(encode(raw)).append('\t').append(id==null?"MISSING":id.toString()).append('\n');}
     for(int id:ids){String name=map.getNcbiIdToNameMap().get(id);result.append(f[0]).append("\tI\t").append(encode(Integer.toString(id))).append('\t').append(name==null?"MISSING":encode(name)).append('\n');}
     out.write(result.toString());
    }catch(Exception failure){out.write(f[0]+"\tERROR\t\t\n");}
   }
  }
 }
}
