/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.metagenomics.MaltExtractTaxonomy;

/** Complete native weighted numeric taxonomy vector; no source vector as input. */
public final class MaltExtractTaxonomyNativeProbe {
 private MaltExtractTaxonomyNativeProbe() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete ncbi.tre and NEW gzipped vector");MaltExtractTaxonomy tree;
  try(var in=Files.newBufferedReader(Path.of(args[0]))){tree=MaltExtractTaxonomy.read(in,new MaltExtractTaxonomy.Limits(100000000,3000000,1024));}
  try(var out=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(Path.of(args[1]),StandardOpenOption.CREATE_NEW)),StandardCharsets.UTF_8))) {
   out.write("taxon_id\tparent_id\tdepth\tchildren\tbranch_length_bits\n");
   for(int id:tree.ids()){if(!tree.hasLength(id))throw new IOException("This source vector requires every physical branch length");out.write(id+"\t"+tree.parent(id)+"\t"+tree.depth(id)+"\t"+tree.childCount(id)+"\t"+Long.toHexString(Double.doubleToRawLongBits(tree.length(id)))+"\n");}
  }
  System.out.println("COMPLETE_NATIVE_MALTEXTRACT_TAXONOMY_NODES="+tree.size());
 }
}
