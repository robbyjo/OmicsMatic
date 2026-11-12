/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.metagenomics.*;
/** Complete native ordered-tree digest and serial readCount assignment controls. */
public final class Malt052NativeTreeProbe {
 private Malt052NativeTreeProbe() { }
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[2]);Files.createDirectory(out);var session=new Malt052Reassignment.Session(Path.of(args[0]),new MaltTaxonomy.Limits(100000000,3000000,1024));var tree=session.taxonomy();int[] ids=tree.ids();Arrays.sort(ids);var digest=MessageDigest.getInstance("SHA-256");int maximum=0;
  try(var expected=new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(args[1]).resolve("ids.bin"))));var stream=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
   stream.writeUTF("omicsmatic-malt-taxonomy-v1");for(int id:ids){if(expected.readInt()!=id)throw new IOException("Complete taxonomy IDs differ");String address=tree.address(id);stream.writeInt(id);stream.writeInt(tree.parent(id));int degree=tree.childCount(id);maximum=Math.max(maximum,degree);stream.writeInt(degree);stream.writeInt(address.length());for(int i=0;i<address.length();i++)stream.writeChar(address.charAt(i));}if(expected.read()!=-1)throw new IOException("Unconsumed taxonomy IDs");
  }
  String hash=HexFormat.of().formatHex(digest.digest());String source=Files.readString(Path.of(args[1]).resolve("tree-summary.tsv"));if(!source.contains("sha256\t"+hash+"\n")||tree.size()!=2259893)throw new IOException("Complete source/native parent, degree or ordered LCA address differs");Files.writeString(out.resolve("tree-summary.tsv"),"nodes\t"+tree.size()+"\nsha256\t"+hash+"\nmax_children\t"+maximum+"\n");
  try(var writer=Files.newBufferedWriter(out.resolve("assignments.tsv"))) {
   writer.write("case\tname_base64\tstored_taxon\tinitial_taxon\tfinal_taxon\tstored_matches\tavailable_matches\tactive\n");
   for(int i=3;i<args.length;i++) {Path input=Path.of(args[i]);var result=session.reassign(input,new Malt052Reassignment.Options(50,1,1,0,80,1,0.01f,Set.of()),new Malt052RmaReader.Limits(1000000000,1000000,10000000,100000,10000000,1000000));for(var row:result.rows()){if(row.storedTaxon()!=row.finalTaxon())throw new IOException("Source/native final assignment differs: "+row.name());writer.write(input.getFileName()+"\t"+Base64.getEncoder().encodeToString(row.name().getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\t"+row.storedTaxon()+"\t"+row.initialTaxon()+"\t"+row.finalTaxon()+"\t"+row.totalMatches()+"\t"+row.availableMatches()+"\t"+row.activeIndices()+"\n");}}
  }
  System.out.println("PASS_COMPLETE_MALT052_SOURCE_NATIVE_TREE_AND_STORED_ASSIGNMENTS_ONLY");
 }
}
