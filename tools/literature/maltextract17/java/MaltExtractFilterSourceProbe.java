/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import megan.data.IMatchBlock;
import NCBI_MapReader.NCBI_MapReader;
import RMA6TaxonProcessor.*;
import behaviour.Filter;

/** Direct original1.7 complete-profile match filtering, including raw float bits. */
public final class MaltExtractFilterSourceProbe {
 private MaltExtractFilterSourceProbe() { }
 private static float bits(String s){return Float.intBitsToFloat((int)Long.parseLong(s,16));}
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IOException("Require fixture TSV, complete controlled map directory and NEW output TSV");
  NCBI_MapReader map=new NCBI_MapReader(args[1]);Logger log=Logger.getLogger("MaltExtract-filter-source-control");
  try(BufferedReader in=Files.newBufferedReader(Paths.get(args[0]),StandardCharsets.UTF_8);BufferedWriter out=Files.newBufferedWriter(Paths.get(args[2]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
   if(!"id\tmode\ttop\tminimum_identity\ttop_only\tsingle_stranded\tscore_bits\tidentity_bits\ttext_base64".equals(in.readLine()))throw new IOException("Wrong complete filter fixture schema");out.write("id\tsequence_eligible\tconsidered_matches\tretained_matches\treads\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);String[] scores=f[6].isEmpty()?new String[0]:f[6].split(","),identities=f[7].isEmpty()?new String[0]:f[7].split(",");if(scores.length!=identities.length)throw new IOException("Incomplete match fixture");
    final String text=new String(Base64.getDecoder().decode(f[8]),StandardCharsets.UTF_8);IMatchBlock[] blocks=new IMatchBlock[scores.length];
    for(int i=0;i<blocks.length;i++) {
     final float score=bits(scores[i]),identity=bits(identities[i]);
     blocks[i]=(IMatchBlock)Proxy.newProxyInstance(IMatchBlock.class.getClassLoader(),new Class<?>[]{IMatchBlock.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] values){String name=method.getName();if(name.equals("getBitScore"))return score;if(name.equals("getPercentIdentity"))return identity;if(name.equals("getText"))return text;if(name.equals("getTaxonId"))return 1;if(name.equals("getTextFirstWord"))return "reference";throw new IllegalStateException("Unexpected original control accessor: "+name);}});
    }
    try {
     boolean ancient=f[1].equals("ANCIENT"),all=!Boolean.parseBoolean(f[4]),ss=Boolean.parseBoolean(f[5]);double pi=Double.parseDouble(f[3]),top=Double.parseDouble(f[2]);
     RMA6TaxonProcessor processor=ancient?new ExperimentalRMA6AncientDestacker(1,pi,map,false,log,log,true,top,0,false,true,true,Filter.ANCIENT,all,ss):new ExperimentalRMA6Destacker(1,pi,map,false,log,log,true,top,0,false,true,true,Filter.NON,all,ss);
     processor.processMatchBlocks(blocks,"read",40,"ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGT");
     if(ancient)((ExperimentalRMA6AncientDestacker)processor).process(false);else((ExperimentalRMA6Destacker)processor).process(false);
     String[] result=processor.getFilterLine().split("\t");out.write(f[0]+"\t"+result[1]+"\t"+result[3]+"\t"+result[4]+"\t"+result[2]+"\n");
    }catch(Exception failure){out.write(f[0]+"\tERROR\tERROR\tERROR\tERROR\n");}
   }
  }
 }
}
