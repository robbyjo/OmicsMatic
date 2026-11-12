/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.*;

/** Native serial pipeline controls with no expected thresholds or taxon values as inputs. */
public final class MaltReassignmentProbe {
 private MaltReassignmentProbe(){ }
 public static void main(String[] args)throws Exception {
  if(args.length!=4)throw new IOException("Require provider, complete RMA export directory, input cases and NEW output directory");Path out=Path.of(args[3]);Files.createDirectory(out);var session=new MaltReassignment.Session(Path.of(args[0]),new MaltTaxonomy.Limits(100000000,3000000,1024));var limits=new MaltRmaReader.Limits(1000000000,1000000,10000000,100000,10000000,1000000);
  int cases=0;long reads=0;
  try(var reader=Files.newBufferedReader(Path.of(args[2]));var summary=Files.newBufferedWriter(out.resolve("thresholds.tsv"));var assigned=Files.newBufferedWriter(out.resolve("assignments.tsv"))) {
   if(!reader.readLine().equals("case_id\tlibrary\tminimum_support\tsupport_percent\tdisabled"))throw new IOException("Wrong complete case schema");summary.write("case_id\tselected_minimum\tstored_reads\n");assigned.write("case_id\tuid\tfinal_taxon\n");String line;var seen=new HashSet<String>();
   while((line=reader.readLine())!=null){String[] f=line.split("\t",-1);if(f.length!=5 || !seen.add(f[0]))throw new IOException("Incomplete/duplicate case");var disabled=new HashSet<Integer>();if(!f[4].isEmpty())for(String id:f[4].split(",",-1))if(!disabled.add(Integer.valueOf(id)))throw new IOException("Duplicate disabled id");Path input=Path.of(args[1]).resolve("data/"+f[1]+"/"+f[1]+".unmapped.rma6");var result=session.reassign(input,new MaltReassignment.Options(50f,1f,1f,0f,80f,Integer.parseInt(f[2]),Float.parseFloat(f[3]),disabled),limits);if(!result.source().profile().equals(MaltRmaReader.SERIAL_PROCESSOR) || result.rows().size()!=result.source().reads())throw new IOException("Wrong source getter profile/population");summary.write(f[0]+"\t"+result.selectedMinimum()+"\t"+result.source().reads()+"\n");for(var row:result.rows().stream().sorted(Comparator.comparingLong(MaltReassignment.Row::uid)).toList())assigned.write(f[0]+"\t"+row.uid()+"\t"+row.finalTaxon()+"\n");cases++;reads+=result.rows().size();}
  }
  if(cases!=32 || reads!=2752)throw new IOException("Incomplete native control population");Files.writeString(out.resolve("probe-counts.tsv"),"cases\t"+cases+"\nread_assignments\t"+reads+"\n");System.out.println("PASS_NATIVE_SERIAL_RMA_REASSIGNMENT_SOURCE_CONTROLS_ONLY");
 }
}
