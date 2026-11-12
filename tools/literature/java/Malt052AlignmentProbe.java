/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.metagenomics.MaltAlignmentAudit;
import org.jlinalg.sequence.FastqIO;

/** Complete data/geometry and aligned-FASTA check; not RMA or biological admission. */
public final class Malt052AlignmentProbe {
 private Malt052AlignmentProbe(){ }
 public static void main(String[] args)throws Exception {
  if(args.length!=6)throw new IOException("Require complete query, SAM.gz, index, original stderr, aligned FASTA.gz and NEW output");
  Path query=Path.of(args[0]),sam=Path.of(args[1]),index=Path.of(args[2]),stderr=Path.of(args[3]),fasta=Path.of(args[4]),out=Path.of(args[5]);Files.createDirectory(out);
  var result=MaltAlignmentAudit.audit(query,sam,index,new MaltAlignmentAudit.Limits(1000000,100000000,10000000,10000000,100000000000L));var counters=MaltAlignCli.sourceCounters(stderr);
  if(counters.get("queries")!=result.queries() || counters.get("alignments")!=result.alignments() || counters.get("aligned_queries")!=result.alignedQueries())throw new IOException("Source completion counters do not cover complete query/SAM populations");
  var sequences=new HashMap<String,String>();try(var in=new FastqIO.Reader(query)){FastqIO.Read read;while((read=in.next())!=null)sequences.put(read.header().substring(1).split("\\s+",2)[0],dna5(read.sequence()));}
  var exported=new LinkedHashMap<String,String>();try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(fasta)),java.nio.charset.StandardCharsets.UTF_8))){String line,name=null;StringBuilder sequence=new StringBuilder();while((line=in.readLine())!=null){if(line.startsWith(">")){if(name!=null)put(exported,name,sequence.toString());name=line.substring(1).split("\\s+",2)[0];sequence.setLength(0);}else{if(name==null)throw new IOException("Sequence before aligned FASTA header");sequence.append(line);}}if(name!=null)put(exported,name,sequence.toString());}
  if(!exported.keySet().equals(result.counts().keySet()))throw new IOException("Complete aligned-FASTA identities differ from SAM aligned queries");
  for(var row:exported.entrySet())if(!Objects.equals(sequences.get(row.getKey()),row.getValue()))throw new IOException("Original aligned-FASTA sequence differs from complete source DNA5 query: "+row.getKey());
  NextflowFiles.writeJson(out.resolve("AUDIT.json"),Map.of("queries",result.queries(),"aligned_queries",result.alignedQueries(),"alignments",result.alignments(),"aligned_fasta_records",exported.size(),"source_completion_counters",counters,"reference_count",result.usedReferences(),"gapped_alignments",result.gappedAlignments(),"whole_malt052_or_paper_admitted",false));System.out.println("PASS_COMPLETE_MALT052_QUERY_SAM_GEOMETRY_COUNTERS_AND_ALIGNED_FASTA_ONLY");
 }
 private static void put(Map<String,String> rows,String name,String sequence)throws IOException{if(name.isEmpty()||sequence.isEmpty()||rows.putIfAbsent(name,sequence)!=null)throw new IOException("Empty/duplicate original aligned-FASTA record");}
 private static String dna5(String text){StringBuilder out=new StringBuilder();for(int i=0;i<text.length();i++){char c=Character.toUpperCase(text.charAt(i));out.append(c=='U'?'T':"ACGT".indexOf(c)>=0?c:'N');}return out.toString();}
}
