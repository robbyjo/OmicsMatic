import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import jloda.graph.Node;
import megan.algorithms.ActiveMatches;
import megan.algorithms.AssignmentUsingLCA;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.data.IMatchBlock;
import megan.data.IReadBlock;
import megan.viewer.TaxonomyData;

/** Calls original source-image bytecode, not a rewritten assignment oracle. */
public final class Malt052AssignmentSourceProbe {
 private static Set<Integer> ids(String text){Set<Integer> out=new HashSet<Integer>();if(!text.isEmpty())for(String id:text.split(",",-1))if(!out.add(Integer.valueOf(id)))throw new IllegalArgumentException("Duplicate disabled id");return out;}
 private static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format("%02x",b&255));return out.toString();}
 public static void main(String[] args)throws Exception {
  Path out=Paths.get(args[1]);Files.createDirectory(out);jloda.swing.util.ResourceManager.addResourceRoot(megan.main.Megan6.class,"megan.resources");Classification tree=ClassificationManager.get("Taxonomy",true);TaxonomyData.ensureDisabledTaxaInitialized();
  boolean alias=TaxonomyData.getDisabledTaxa()==tree.getIdMapper().getDisabledIds();Files.write(out.resolve("disabled-set-alias.txt"),(alias+"\n").getBytes(StandardCharsets.UTF_8));if(!alias)throw new IOException("Unexpected original Taxonomy disabled-set relationship");
  if(tree.getFullTree().getIds().size()!=2259893)throw new IOException("Incomplete original bundled taxonomy");
  MessageDigest digest=MessageDigest.getInstance("SHA-256");int maximum=0;int[] all=tree.getFullTree().getIds().stream().mapToInt(Integer::intValue).sorted().toArray();
  try(DataOutputStream stream=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int value){}public void write(byte[] data,int start,int length){}},digest))) {
   stream.writeUTF("omicsmatic-malt-taxonomy-v1");
   for(int id:all){Node node=tree.getFullTree().getANode(id);String address=tree.getFullTree().getAddress(id);stream.writeInt(id);stream.writeInt(node.getInDegree()==0?0:(Integer)node.getFirstInEdge().getSource().getInfo());int degree=node.getOutDegree();maximum=Math.max(maximum,degree);stream.writeInt(degree);stream.writeInt(address.length());for(int i=0;i<address.length();i++)stream.writeChar(address.charAt(i));}
  }
  Files.write(out.resolve("tree-summary.tsv"),("nodes\t"+all.length+"\nsha256\t"+hex(digest.digest())+"\nmax_children\t"+maximum+"\n").getBytes(StandardCharsets.UTF_8));
  Map<String,AssignmentUsingLCA> algorithms=new HashMap<String,AssignmentUsingLCA>();
  try(BufferedReader reader=Files.newBufferedReader(Paths.get(args[0]));BufferedWriter writer=Files.newBufferedWriter(out.resolve("synthetic.tsv"))) {
   if(!reader.readLine().startsWith("case_id\ttotal_matches\t"))throw new IOException("Wrong case header");writer.write("case_id\tactive_indices\tassigned_taxon\n");String line;
   while((line=reader.readLine())!=null) {
    final String[] f=line.split("\t",-1);if(f.length!=11)throw new IOException("Incomplete case row");final List<IMatchBlock> matches=new ArrayList<IMatchBlock>();
    if(!f[10].isEmpty())for(String item:f[10].split(";",-1)) {
     final String[] m=item.split(",",-1);if(m.length!=5)throw new IOException("Incomplete match");
     final int taxon=Integer.parseInt(m[0]);final float score=Float.parseFloat(m[1]),expected=Float.parseFloat(m[2]),identity=Float.parseFloat(m[3]);final boolean ignored=Boolean.parseBoolean(m[4]);
     matches.add((IMatchBlock)Proxy.newProxyInstance(IMatchBlock.class.getClassLoader(),new Class<?>[]{IMatchBlock.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] values){switch(method.getName()){case "getTaxonId":case "getId":return taxon;case "getBitScore":return score;case "getExpected":return expected;case "getPercentIdentity":return identity;case "isIgnore":return ignored;default:throw new AssertionError("Unexpected original match API: "+method);}}}));
    }
    final int total=Integer.parseInt(f[1]);IReadBlock read=(IReadBlock)Proxy.newProxyInstance(IReadBlock.class.getClassLoader(),new Class<?>[]{IReadBlock.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] values){switch(method.getName()){case "getNumberOfMatches":return total;case "getNumberOfAvailableMatchBlocks":return matches.size();case "getMatchBlock":return matches.get((Integer)values[0]);default:throw new AssertionError("Unexpected original read API: "+method);}}});
    TaxonomyData.getDisabledTaxa().clear();TaxonomyData.getDisabledTaxa().addAll(ids(f[8]));
    BitSet active=new BitSet();if(f[9].equals("FILTER"))ActiveMatches.compute(Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Float.parseFloat(f[5]),read,"Taxonomy",active);else for(int i:ids(f[9]))active.set(i);
    String key=f[6]+"/"+f[7];AssignmentUsingLCA algorithm=algorithms.get(key);if(algorithm==null){algorithm=new AssignmentUsingLCA("Taxonomy",false,Float.parseFloat(f[6]),Boolean.parseBoolean(f[7]));algorithms.put(key,algorithm);}
    int id=algorithm.computeId(active,read);writer.write(f[0]+"\t"+active+"\t"+id+"\n");
   }
  }
  System.out.println("PASS_ORIGINAL_MALT052_ALL_COMPLETE_SYNTHETIC_ASSIGNMENT_CASES_ONLY");
 }
}
