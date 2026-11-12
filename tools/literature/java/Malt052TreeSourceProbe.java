/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import megan.classification.ClassificationManager;
/** Direct original0.5.2 complete ordered-tree oracle, including program-added sentinels. */
public final class Malt052TreeSourceProbe {
 private Malt052TreeSourceProbe() { }
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[0]);Files.createDirectory(out);jloda.swing.util.ResourceManager.addResourceRoot(megan.main.Megan6.class,"megan.resources");var tree=ClassificationManager.get("Taxonomy",true).getFullTree();int[] ids=tree.getIds().stream().mapToInt(Integer::intValue).sorted().toArray();if(ids.length!=2259893||(Integer)tree.getRoot().getInfo()!=1)throw new IOException("Incomplete original taxonomy");var digest=MessageDigest.getInstance("SHA-256");int maximum=0;
  try(var keys=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out.resolve("ids.bin"))));var stream=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
   stream.writeUTF("omicsmatic-malt-taxonomy-v1");for(int id:ids){keys.writeInt(id);var node=tree.getANode(id);String address=tree.getAddress(id);stream.writeInt(id);stream.writeInt(node.getInDegree()==0?0:(Integer)node.getFirstInEdge().getSource().getInfo());int degree=node.getOutDegree();maximum=Math.max(maximum,degree);stream.writeInt(degree);stream.writeInt(address.length());for(int i=0;i<address.length();i++)stream.writeChar(address.charAt(i));}
  }
  var children=new ArrayList<Integer>();for(var edge=tree.getRoot().getFirstOutEdge();edge!=null;edge=tree.getRoot().getNextOutEdge(edge))children.add((Integer)edge.getTarget().getInfo());
  StringBuilder report=new StringBuilder("nodes\t"+ids.length+"\nsha256\t"+hex(digest.digest())+"\nmax_children\t"+maximum+"\nroot_children\t"+children+"\n");for(int id:ids)if(id<=0)report.append("sentinel\t").append(id).append('\t').append(ClassificationManager.get("Taxonomy",false).getName2IdMap().get(id)).append('\n');Files.writeString(out.resolve("tree-summary.tsv"),report.toString());System.out.print(report);
 }
 private static String hex(byte[] bytes){var out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
}
