/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.zip.GZIPOutputStream;
import NCBI_MapReader.NCBI_TreeReader;
import org.forester.phylogeny.*;
import org.forester.phylogeny.iterators.PhylogenyNodeIterator;

/** Every original NCBI_TreeReader/Forester node, in original preorder. */
public final class MaltExtractTaxonomySourceProbe {
 private MaltExtractTaxonomySourceProbe() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require complete resource directory and NEW gzipped vector");
  NCBI_TreeReader reader=new NCBI_TreeReader(args[0]);Field field=NCBI_TreeReader.class.getDeclaredField("ph");field.setAccessible(true);Phylogeny tree=(Phylogeny)field.get(reader);long count=0;
  try(BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(Paths.get(args[1]),StandardOpenOption.CREATE_NEW)),StandardCharsets.UTF_8))) {
   out.write("taxon_id\tparent_id\tdepth\tchildren\tbranch_length_bits\n");
   PhylogenyNodeIterator nodes=tree.iteratorPreorder();while(nodes.hasNext()) {
    PhylogenyNode node=nodes.next();int id=Integer.parseInt(node.getName()),parent=node.getParent()==null?0:Integer.parseInt(node.getParent().getName());
    out.write(id+"\t"+parent+"\t"+node.calculateDepth()+"\t"+node.getNumberOfDescendants()+"\t"+Long.toHexString(Double.doubleToRawLongBits(node.getDistanceToParent()))+"\n");count++;
   }
  }
  System.out.println("COMPLETE_ORIGINAL_MALTEXTRACT_TAXONOMY_NODES="+count);
 }
}
