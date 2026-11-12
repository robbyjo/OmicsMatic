/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import megan.rma6.RMA6Connector;
import megan.data.*;

/** Direct original MaltExtract1.7 Java8 API oracle; complete populations only. */
public final class MaltExtractRmaSourceProbe {
 private MaltExtractRmaSourceProbe() { }
 private static String text(String s){return s==null?"NULL":Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 private static String bits(float f){return Integer.toHexString(Float.floatToRawIntBits(f));}
 private static void write(Path out,String name,String value)throws IOException {Files.write(out.resolve(name),value.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);}
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IOException("Require complete RMA, RAW|EXTRACTION and NEW output directory");
  if(!args[1].equals("RAW")&&!args[1].equals("EXTRACTION"))throw new IOException("Explicit reader profile required");
  boolean raw=args[1].equals("RAW");float minimum=raw?0f:1f,maximum=raw?Float.MAX_VALUE:100f;
  Path out=Paths.get(args[2]);Files.createDirectory(out);RMA6Connector connector=new RMA6Connector(args[0]);
  long reportedReads=connector.getNumberOfReads(),reportedMatches=connector.getNumberOfMatches();
  Map<Long,Integer> membership=new HashMap<Long,Integer>();TreeMap<Integer,String> classes=new TreeMap<Integer,String>();
  IClassificationBlock classification=connector.getClassificationBlock("Taxonomy");
  if(classification==null&&reportedReads!=0)throw new IOException("Missing classification");
  if(classification!=null)for(int taxon:new TreeSet<Integer>(classification.getKeySet())) {
   int count=classification.getSum(taxon),found=0;
   try(IReadBlockIterator reads=connector.getReadsIterator("Taxonomy",taxon,0f,Float.MAX_VALUE,true,true)) {
    while(reads.hasNext()){IReadBlock read=reads.next();if(membership.put(read.getUId(),taxon)!=null)throw new IOException("Repeated class membership");found++;}
   }
   if(found!=count)throw new IOException("Incomplete class population");classes.put(taxon,taxon+"\t"+count+"\t"+bits(classification.getWeightedSum(taxon))+"\n");
  }
  TreeMap<String,String> rows=new TreeMap<String,String>(),hits=new TreeMap<String,String>(),uids=new TreeMap<String,String>();
  Set<Long> seen=new HashSet<Long>();long total=0,available=0;
  try(IReadBlockIterator reads=connector.getAllReadsIterator(minimum,maximum,true,true)) {
   while(reads.hasNext()) {
    IReadBlock r=reads.next();Integer taxon=membership.get(r.getUId());if(taxon==null||!seen.add(r.getUId()))throw new IOException("Missing or repeated full-read membership");
    String name=text(r.getReadName());int stored=r.getNumberOfMatches(),have=r.getNumberOfAvailableMatchBlocks();total+=stored;available+=have;
    String row=name+"\t"+text(r.getReadHeader())+"\t"+text(r.getReadSequence())+"\t"+r.getReadLength()+"\t"+r.getReadWeight()+"\t"+stored+"\t"+have+"\t"+taxon+"\n";
    if(rows.put(name,row)!=null)throw new IOException("Ambiguous canonical name");uids.put(name,r.getUId()+"\t"+name+"\t"+taxon+"\n");
    StringBuilder matches=new StringBuilder();
    for(int i=0;i<have;i++){IMatchBlock m=r.getMatchBlock(i);matches.append(name).append('\t').append(i).append('\t').append(m.getTaxonId()).append('\t').append(bits(m.getBitScore())).append('\t').append(bits(m.getExpected())).append('\t').append(bits(m.getPercentIdentity())).append('\t').append(m.isIgnore()).append('\t').append(text(m.getRefSeqId())).append('\t').append(text(m.getText())).append('\t').append(m.getAlignedQueryStart()).append('\t').append(m.getAlignedQueryEnd()).append('\t').append(m.getRefLength()).append('\n');}hits.put(name,matches.toString());
   }
  }
  if(rows.size()!=reportedReads||total!=reportedMatches||!seen.equals(membership.keySet()))throw new IOException("Incomplete source populations");
  write(out,"reads.tsv","name_base64\theader_base64\tsequence_base64\tlength\tweight\tstored_matches\tavailable_matches\tstored_taxon\n"+String.join("",rows.values()));
  write(out,"matches.tsv","name_base64\tindex\ttaxon\tbit_score_bits\texpected_bits\tidentity_bits\tignored\treference_base64\ttext_base64\taligned_start\taligned_end\treference_length\n"+String.join("",hits.values()));
  write(out,"classes.tsv","taxon\treads\tweighted_reads_bits\n"+String.join("",classes.values()));write(out,"identities.tsv","uid\tname_base64\tstored_taxon\n"+String.join("",uids.values()));
  write(out,"counts.tsv","reads\t"+reportedReads+"\nstored_matches\t"+reportedMatches+"\navailable_matches\t"+available+"\n");
  System.out.println("PASS_ORIGINAL_MALTEXTRACT17_RMA_FIELDS reads="+reportedReads+" matches="+available);
 }
}
