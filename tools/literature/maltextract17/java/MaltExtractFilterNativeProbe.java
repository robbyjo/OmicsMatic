/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltExtractRmaReader;
import org.jlinalg.metagenomics.MaltExtractReadProjection;

/** Complete native filtering output from the source-control input population. */
public final class MaltExtractFilterNativeProbe {
 private MaltExtractFilterNativeProbe() { }
 private static float bits(String s){return Float.intBitsToFloat((int)Long.parseLong(s,16));}
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require fixture TSV and NEW output TSV");
  try(var in=Files.newBufferedReader(Path.of(args[0]));var out=Files.newBufferedWriter(Path.of(args[1]),StandardOpenOption.CREATE_NEW)) {
   if(!"id\tmode\ttop\tminimum_identity\ttop_only\tsingle_stranded\tscore_bits\tidentity_bits\ttext_base64".equals(in.readLine()))throw new IOException("Wrong complete filter fixture schema");out.write("id\tsequence_eligible\tconsidered_matches\tretained_matches\treads\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);String[] scores=f[6].isEmpty()?new String[0]:f[6].split(","),identities=f[7].isEmpty()?new String[0]:f[7].split(",");if(scores.length!=identities.length)throw new IOException("Incomplete match fixture");
    String text=new String(Base64.getDecoder().decode(f[8]),StandardCharsets.UTF_8);var matches=new ArrayList<MaltExtractRmaReader.Match>();
    for(int i=0;i<scores.length;i++)matches.add(new MaltExtractRmaReader.Match(i,1,bits(scores[i]),0,bits(identities[i]),false,"reference",text,1,20,1000));
    var read=new MaltExtractRmaReader.Read(1,"read",">read","ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGT",40,1,scores.length,matches,1);
    try {
     var p=new MaltExtractReadProjection.Options(MaltExtractReadProjection.Mode.valueOf(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),0,0,Boolean.parseBoolean(f[4]),Boolean.parseBoolean(f[5]));var result=MaltExtractReadProjection.project(List.of(read),1,p);
     out.write(f[0]+"\t"+result.sequenceEligible()+"\t"+result.consideredMatches()+"\t"+result.retainedMatches()+"\t"+result.reads().size()+"\n");
    }catch(Exception failure){out.write(f[0]+"\tERROR\tERROR\tERROR\tERROR\n");}
   }
  }
 }
}
