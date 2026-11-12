/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.Malt052RmaReader;

/** Complete source RMA fields keyed by literal read name; file-local UIDs are retained separately. */
public final class Malt052RmaNativeProbe {
 private Malt052RmaNativeProbe(){ }
 private static String text(String s){return s==null?"NULL":Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
 private static String bits(float value){return Integer.toHexString(Float.floatToRawIntBits(value));}
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IOException("Require exact reader provider, complete RMA and NEW output directory");Path out=Path.of(args[2]);Files.createDirectory(out);var rows=new ArrayList<Malt052RmaReader.Read>();var report=Malt052RmaReader.read(Path.of(args[0]),Path.of(args[1]),new Malt052RmaReader.Limits(1000000000,2000000,100000000,100000,10000000,1000000),rows::add);var names=new HashSet<String>();
  try(var reads=Files.newBufferedWriter(out.resolve("reads.tsv"));var matches=Files.newBufferedWriter(out.resolve("matches.tsv"));var identities=Files.newBufferedWriter(out.resolve("identities.tsv"));var classes=Files.newBufferedWriter(out.resolve("classes.tsv"))) {
   reads.write("name_base64\theader_base64\tsequence_base64\tlength\tweight\tstored_matches\tavailable_matches\tstored_taxon\n");matches.write("name_base64\tindex\ttaxon\tbit_score_bits\texpected_bits\tidentity_bits\tignored\treference_base64\ttext_base64\taligned_start\taligned_end\treference_length\n");identities.write("uid\tname_base64\tstored_taxon\n");classes.write("taxon\treads\tweighted_reads_bits\n");
   for(var row:rows.stream().sorted(Comparator.comparing(row -> text(row.name()))).toList()) {if(!names.add(row.name()))throw new IOException("Nonunique canonical read identity; no representative selected");String name=text(row.name());reads.write(name+"\t"+text(row.header())+"\t"+text(row.sequence())+"\t"+row.length()+"\t"+row.weight()+"\t"+row.totalMatches()+"\t"+row.matches().size()+"\t"+row.storedTaxon()+"\n");identities.write(row.uid()+"\t"+name+"\t"+row.storedTaxon()+"\n");for(var hit:row.matches())matches.write(name+"\t"+hit.index()+"\t"+hit.taxon()+"\t"+bits(hit.bitScore())+"\t"+bits(hit.expected())+"\t"+bits(hit.percentIdentity())+"\t"+hit.ignored()+"\t"+text(hit.reference())+"\t"+text(hit.text())+"\t"+hit.alignedStart()+"\t"+hit.alignedEnd()+"\t"+hit.referenceLength()+"\n");}
   for(var row:report.classifications().values())classes.write(row.taxon()+"\t"+row.reads()+"\t"+bits(row.weightedReads())+"\n");
  }
  Files.writeString(out.resolve("counts.tsv"),"reads\t"+report.reads()+"\nstored_matches\t"+report.storedMatches()+"\navailable_matches\t"+report.availableMatches()+"\n");if(report.availableMatches()!=report.storedMatches())throw new IOException("Not all original raw matches were available");System.out.println("PASS_COMPLETE_CANONICAL_RMA_FIELDS_WITH_SEPARATE_FILE_LOCAL_UIDS_ONLY");
 }
}
