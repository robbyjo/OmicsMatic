/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltExtractRmaReader;
import org.jlinalg.metagenomics.MaltExtractReadProjection;

/** Complete native default/ancient read projection; upstream FASTA is never an input. */
public final class MaltExtractProjectionProbe {
 private MaltExtractProjectionProbe() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=4)throw new IOException("Require exact reader provider, complete RMA, literal comma-separated taxon IDs and NEW output");
  Path out=Path.of(args[3]);Files.createDirectory(out);var rows=new ArrayList<MaltExtractRmaReader.Read>();
  var report=MaltExtractRmaReader.read(Path.of(args[0]),Path.of(args[1]),
   new MaltExtractRmaReader.Limits(1000000000,1000000,10000000,100000,1000000,3000000),MaltExtractRmaReader.EXTRACTION,rows::add);
  StringBuilder counts=new StringBuilder("taxon\tmode\tassigned\tsequence_eligible\tconsidered_matches\tretained_matches\treads\n");
  for(String token:args[2].split(",",-1))for(var mode:MaltExtractReadProjection.Mode.values()) {
   int taxon=Integer.parseInt(token);var result=MaltExtractReadProjection.project(rows,taxon,MaltExtractReadProjection.Options.complete(mode));
   StringBuilder fasta=new StringBuilder();for(var pair:result.reads())fasta.append(pair.header()).append('\n').append(pair.sequence()).append('\n');
   Files.writeString(out.resolve(taxon+"-"+mode+".fasta"),fasta.toString(),StandardOpenOption.CREATE_NEW);
   counts.append(taxon).append('\t').append(mode).append('\t').append(result.assigned()).append('\t').append(result.sequenceEligible()).append('\t').append(result.consideredMatches()).append('\t').append(result.retainedMatches()).append('\t').append(result.reads().size()).append('\n');
  }
  Files.writeString(out.resolve("counts.tsv"),counts.toString(),StandardOpenOption.CREATE_NEW);
  Files.writeString(out.resolve("input-counts.tsv"),"reads\t"+report.reads()+"\nstored_matches\t"+report.storedMatches()+"\navailable_matches\t"+report.availableMatches()+"\n",StandardOpenOption.CREATE_NEW);
  System.out.println("PASS_COMPLETE_NATIVE_MALTEXTRACT_READ_PROJECTION_ONLY");
 }
}
