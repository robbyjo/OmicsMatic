/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.metagenomics.Malt052Assignment;
import org.jlinalg.metagenomics.MaltTaxonomy;

/** Development probe; expected assignment columns are never used as native inputs. */
public final class Malt052AssignmentNativeProbe {
 private Malt052AssignmentNativeProbe(){ }
 private static Set<Integer> ids(String text){var out=new HashSet<Integer>();if(!text.isEmpty())for(String id:text.split(",",-1))if(!out.add(Integer.valueOf(id)))throw new IllegalArgumentException("Duplicate disabled id");return out;}
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("Require complete numeric tree, case TSV, complete RMA export directory, NEW output directory and wide control tree");
  Path out=Path.of(args[2]);Files.createDirectory(out);MaltTaxonomy tree;
  tree=org.jlinalg.metagenomics.Malt052RmaReader.defaultTaxonomy(Path.of(args[0]),new MaltTaxonomy.Limits(100000000,3000000,1024));
  MessageDigest digest=MessageDigest.getInstance("SHA-256");int maximum=0;int[] all=tree.ids();Arrays.sort(all);
  try(var stream=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
   stream.writeUTF("omicsmatic-malt-taxonomy-v1");
   for(int id:all){String address=tree.address(id);stream.writeInt(id);stream.writeInt(tree.parent(id));int degree=tree.childCount(id);maximum=Math.max(maximum,degree);stream.writeInt(degree);stream.writeInt(address.length());for(int i=0;i<address.length();i++)stream.writeChar(address.charAt(i));}
  }
  Files.writeString(out.resolve("tree-summary.tsv"),"nodes\t"+tree.size()+"\nsha256\t"+HexFormat.of().formatHex(digest.digest())+"\nmax_children\t"+maximum+"\n",StandardCharsets.UTF_8);
  try(var reader=Files.newBufferedReader(Path.of(args[1]));var writer=Files.newBufferedWriter(out.resolve("synthetic.tsv"))) {
   String header=reader.readLine();if(!header.equals("case_id\ttotal_matches\tminimum_score\ttop_percent\tmaximum_expected\tminimum_identity\tcoverage\tignore_ancestors\tdisabled\texplicit_active\tmatches"))throw new IOException("Unexpected complete case schema");
   writer.write("case_id\tactive_indices\tassigned_taxon\n");String line;
   while((line=reader.readLine())!=null) {
    String[] f=line.split("\t",-1);if(f.length!=11)throw new IOException("Incomplete case row");var matches=new ArrayList<Malt052Assignment.Match>();
    if(!f[10].isEmpty())for(String item:f[10].split(";",-1)){String[] m=item.split(",",-1);if(m.length!=5)throw new IOException("Incomplete match");matches.add(new Malt052Assignment.Match(Integer.parseInt(m[0]),Float.parseFloat(m[1]),Float.parseFloat(m[2]),Float.parseFloat(m[3]),Boolean.parseBoolean(m[4])));}
    Malt052Assignment.Result result;
    if(f[9].equals("FILTER"))result=Malt052Assignment.assign(tree,Integer.parseInt(f[1]),matches,new Malt052Assignment.Filter(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Float.parseFloat(f[5])),ids(f[8]),Float.parseFloat(f[6]),Boolean.parseBoolean(f[7]));
    else {BitSet active=new BitSet();for(int i:ids(f[9]))active.set(i);result=new Malt052Assignment.Result(Malt052Assignment.assignActive(tree,Integer.parseInt(f[1]),matches,active,ids(f[8]),Float.parseFloat(f[6]),Boolean.parseBoolean(f[7])),active);}
    writer.write(f[0]+"\t"+result.active()+"\t"+result.taxon()+"\n");
   }
  }
  System.out.println("PASS_NATIVE_MALT052_COMPLETE_SYNTHETIC_ASSIGNMENTS_ONLY");
 }
}
