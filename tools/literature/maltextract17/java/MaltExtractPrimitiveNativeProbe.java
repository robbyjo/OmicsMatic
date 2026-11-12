/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltExtractReadProjection;

/** Complete native primitive output; no expected data are inputs. */
public final class MaltExtractPrimitiveNativeProbe {
 private MaltExtractPrimitiveNativeProbe() { }
 private static String decode(String s){return s.equals("NULL")?null:new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete fixture TSV and NEW output TSV");
  try(var in=Files.newBufferedReader(Path.of(args[0]));var out=Files.newBufferedWriter(Path.of(args[1]),StandardOpenOption.CREATE_NEW)) {
   if(!"id\tsequence_base64\ttext_base64\tsingle_stranded".equals(in.readLine()))throw new IOException("Wrong fixture schema");
   out.write("id\tcomplexity_bits\tterminal_damage\n");String line;
   while((line=in.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Wrong complete fixture row");String complexity,damage;
    try{complexity=Integer.toHexString(Float.floatToRawIntBits(MaltExtractReadProjection.minimumComplexity(decode(f[1]))));}catch(Exception e){complexity="ERROR";}
    try{damage=Boolean.toString(MaltExtractReadProjection.terminalDamage(decode(f[2]),Boolean.parseBoolean(f[3])));}catch(Exception e){damage="ERROR";}
    out.write(f[0]+"\t"+complexity+"\t"+damage+"\n");
   }
  }
 }
}
