/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.MaltAlignmentAudit;
import org.jlinalg.sequence.FastqIO;

/** Replay retained original source-output refusals, without fitting or rewriting data. */
public final class MaltAlignmentRefusalReplay {
 private MaltAlignmentRefusalReplay(){ }
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IOException("Require complete retained bundle, projection|consumption, NEW output directory");Path root=Path.of(args[0]),out=Path.of(args[2]);Files.createDirectory(out);Path query=root.resolve(Files.isRegularFile(root.resolve("inputs/reads.fastq.gz"))?"inputs/reads.fastq.gz":"inputs/reads.fastq");String reason;
  if(args[1].equals("projection")) {
   try {MaltAlignmentAudit.audit(query,root.resolve("native/reads.blastn.sam.gz"),root.resolve("inputs/database"),new MaltAlignmentAudit.Limits(1000000,100000000,10000000,10000000,100000000000L));throw new AssertionError("Original source projection discrepancy accepted");}
   catch(IOException expected){reason=expected.getMessage();if(!reason.contains("SAM query bases/qualities differ after source DNA5/strand/clip projection")||!reason.contains("Required action:"))throw expected;}
  } else if(args[1].equals("consumption")) {
   long complete=FastqIO.inspect(query,null).read1().reads(),reported=MaltAlignCli.sourceCounters(root.resolve("native/process.stderr")).get("queries");if(complete==reported)throw new AssertionError("Original source early-stop witness is absent");reason="Original MALT reported "+reported+" queries; complete retained FASTQ has "+complete+". No prefix is admitted.";
  }else throw new IOException("Unknown exact refusal replay kind");
  if(Files.exists(root.resolve("COMPONENT_COMPLETED.json")))throw new IOException("A source-discrepancy bundle cannot carry a success receipt");NextflowFiles.writeJson(out.resolve("REFUSAL_REPLAY.json"),Map.of("status","retained-original-source-discrepancy-refused","kind",args[1],"reason",reason,"original_alignment_refit",false,"biological_result_admitted",false,"new_tool_credit",0));System.out.println("PASS_RETAINED_ORIGINAL_SOURCE_OUTPUT_REFUSAL_ONLY");
 }
}
