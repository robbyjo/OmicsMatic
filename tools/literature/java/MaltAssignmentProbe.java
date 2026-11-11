/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltAssignment;
import org.jlinalg.metagenomics.MaltTaxonomy;

/** Development probe; expected assignment columns are never used as native inputs. */
public final class MaltAssignmentProbe {
 private MaltAssignmentProbe(){ }
 private static Set<Integer> ids(String text){var out=new HashSet<Integer>();if(!text.isEmpty())for(String id:text.split(",",-1))if(!out.add(Integer.valueOf(id)))throw new IllegalArgumentException("Duplicate disabled id");return out;}
 public static void main(String[] args)throws Exception {
  if(args.length!=5)throw new IllegalArgumentException("Require complete numeric tree, case TSV, complete RMA export directory, NEW output directory and wide control tree");
  Path out=Path.of(args[3]);Files.createDirectory(out);MaltTaxonomy tree;
  try(var reader=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.UTF_8)){tree=MaltTaxonomy.read(reader,new MaltTaxonomy.Limits(100000000,3000000,1024));}
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
    String[] f=line.split("\t",-1);if(f.length!=11)throw new IOException("Incomplete case row");var matches=new ArrayList<MaltAssignment.Match>();
    if(!f[10].isEmpty())for(String item:f[10].split(";",-1)){String[] m=item.split(",",-1);if(m.length!=5)throw new IOException("Incomplete match");matches.add(new MaltAssignment.Match(Integer.parseInt(m[0]),Float.parseFloat(m[1]),Float.parseFloat(m[2]),Float.parseFloat(m[3]),Boolean.parseBoolean(m[4])));}
    MaltAssignment.Result result;
    if(f[9].equals("FILTER"))result=MaltAssignment.assign(tree,Integer.parseInt(f[1]),matches,new MaltAssignment.Filter(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Float.parseFloat(f[5])),ids(f[8]),Float.parseFloat(f[6]),Boolean.parseBoolean(f[7]));
    else {BitSet active=new BitSet();for(int i:ids(f[9]))active.set(i);result=new MaltAssignment.Result(MaltAssignment.assignActive(tree,Integer.parseInt(f[1]),matches,active,ids(f[8]),Float.parseFloat(f[6]),Boolean.parseBoolean(f[7])),active);}
    writer.write(f[0]+"\t"+result.active()+"\t"+result.taxon()+"\n");
   }
  }
  try(var files=Files.list(Path.of(args[2]));var writer=Files.newBufferedWriter(out.resolve("rma-assignments.tsv"))) {
   writer.write("library\tuid\tactive_indices\tassigned_taxon\n");
   for(Path reads:files.filter(p->p.getFileName().toString().endsWith(".reads.tsv")).sorted().toList()) {
    String label=reads.getFileName().toString().replace(".reads.tsv","");var byUid=new LinkedHashMap<Long,List<MaltAssignment.Match>>();
    try(var reader=Files.newBufferedReader(reads.resolveSibling(label+".matches.tsv"))) {
     if(!reader.readLine().startsWith("uid\tmatch_index\ttaxon_id\t"))throw new IOException("Wrong original match header");String line;
     while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=12)throw new IOException("Incomplete original match row");var rows=byUid.computeIfAbsent(Long.valueOf(f[0]),ignored->new ArrayList<>());if(Integer.parseInt(f[1])!=rows.size())throw new IOException("Noncontiguous original match index");rows.add(new MaltAssignment.Match(Integer.parseInt(f[2]),Float.parseFloat(f[3]),Float.parseFloat(f[4]),Float.parseFloat(f[5]),Boolean.parseBoolean(f[6])));}
    }
    try(var reader=Files.newBufferedReader(reads)) {
     if(!reader.readLine().startsWith("uid\tread_name_base64\t"))throw new IOException("Wrong original read header");String line;
     while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=11)throw new IOException("Incomplete original read row");long uid=Long.parseLong(f[0]);var matches=byUid.remove(uid);if(matches==null)matches=List.of();if(matches.size()!=Integer.parseInt(f[7]))throw new IOException("Original read/match population differs");var result=MaltAssignment.assign(tree,Integer.parseInt(f[6]),matches,new MaltAssignment.Filter(50,1,1,0),Set.of(),80,true);writer.write(label+"\t"+uid+"\t"+result.active()+"\t"+result.taxon()+"\n");}
    }
    if(!byUid.isEmpty())throw new IOException("Unconsumed original matches");
   }
  }
  MaltTaxonomy wide;
  try(var reader=Files.newBufferedReader(Path.of(args[4]))){wide=MaltTaxonomy.read(reader,new MaltTaxonomy.Limits(10000000,100000,16));}
  MessageDigest wideDigest=MessageDigest.getInstance("SHA-256");int[] wideIds=wide.ids();Arrays.sort(wideIds);
  try(var stream=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),wideDigest))) {
   stream.writeUTF("omicsmatic-malt-taxonomy-v1");for(int id:wideIds){String address=wide.address(id);stream.writeInt(id);stream.writeInt(wide.parent(id));stream.writeInt(wide.childCount(id));stream.writeInt(address.length());for(int i=0;i<address.length();i++)stream.writeChar(address.charAt(i));}
  }
  Files.writeString(out.resolve("wide-digest.txt"),HexFormat.of().formatHex(wideDigest.digest())+"\n");
  var wideMatches=List.of(new MaltAssignment.Match(10000000,100,0,99,false),new MaltAssignment.Match(10065534,100,0,99,false));
  try(var writer=Files.newBufferedWriter(out.resolve("wide-assignments.tsv"))) {
   writer.write("coverage\tassigned_taxon\n");for(float coverage:new float[]{0,50,80,100})writer.write(coverage+"\t"+MaltAssignment.assign(wide,2,wideMatches,new MaltAssignment.Filter(50,1,1,0),Set.of(),coverage,true).taxon()+"\n");
  }
  System.out.println("PASS_NATIVE_COMPLETE_TREE_AND_ASSIGNMENT_PROBE_ONLY");
 }
}
