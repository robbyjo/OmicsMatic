/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerScreeningReads;

/** Native complete screening data consistency; no pipeline execution/install or biological inference. */
public final class NfcoreEagerScreeningCli {
 private NfcoreEagerScreeningCli() { }
 private static final String ACTION="Required action: retain complete original inputs and diagnostics, resolve the named identity, format, source-contract or capacity discrepancy and retry a NEW directory. Increase explicit complete-population limits/Java heap only where allowed; no subset is substituted. This component checks the supplied host-filter/FASTQ/MALT populations. It does not rescore MALT, validate LCA assignments, infer HOPS authenticity, reconcile author versions or admit a whole eager workflow/biological result. Missing or forbidden required runtime/installation means skip its dependent analysis; this native component performs no installation or upstream execution.";
 public static void main(String[] args) {
  Path out=null;boolean owned=false;
  try {
   if(args.length==1 && args[0].equals("--help")) {System.out.println("--mapped-bam FILE --filtered-bam FILE --unmapped-fastq FILE.gz --malt-sam-gzip FILE.sam.gz --malt-reference-db ref.db --malt-reference-index ref.idx --malt-reference-info ref.inf --minimum-mapq N --minimum-length N --out NEW_DIRECTORY\nOptional positive capacities: --maximum-records (10000000), --maximum-queries (1000000), --maximum-query-bases (50000000), --maximum-alignments (10000000), --maximum-references (1000000), --maximum-reference-bases (10000000000).\n"+ACTION);return;}
   var required=Set.of("--mapped-bam","--filtered-bam","--unmapped-fastq","--malt-sam-gzip","--malt-reference-db","--malt-reference-index","--malt-reference-info","--minimum-mapq","--minimum-length","--out");
   var optional=Set.of("--maximum-records","--maximum-queries","--maximum-query-bases","--maximum-alignments","--maximum-references","--maximum-reference-bases");var options=new LinkedHashMap<String,String>();
   for(int i=0;i<args.length;i+=2)if(i+1>=args.length || !required.contains(args[i])&&!optional.contains(args[i]) || options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique complete options; see --help");
   if(!options.keySet().containsAll(required))throw new IOException("Declare every input/filter/output option; see --help");
   out=Path.of(options.get("--out")).toAbsolutePath().normalize();Files.createDirectory(out);owned=true;
   var limits=new NfcoreEagerScreeningReads.Limits(number(options,"--maximum-records",10000000),number(options,"--maximum-queries",1000000),number(options,"--maximum-query-bases",50000000),number(options,"--maximum-alignments",10000000));
   var refs=NfcoreEagerScreeningReads.references(Path.of(options.get("--malt-reference-db")),Path.of(options.get("--malt-reference-index")),Path.of(options.get("--malt-reference-info")),number(options,"--maximum-references",1000000),number(options,"--maximum-reference-bases",10000000000L));
   var result=NfcoreEagerScreeningReads.inspect(Path.of(options.get("--mapped-bam")),Path.of(options.get("--filtered-bam")),Path.of(options.get("--unmapped-fastq")),Path.of(options.get("--malt-sam-gzip")),refs,Integer.parseInt(options.get("--minimum-mapq")),Long.parseLong(options.get("--minimum-length")),limits);
   var report=new LinkedHashMap<String,Object>(result.document());report.put("status","complete-selected-native-screening-read-consistency-only");report.put("complete_reference_sequences",refs.size());report.put("biological_result_admitted",false);report.put("full_eager_adapter_admitted",false);report.put("new_tool_credit",0);NextflowFiles.writeJson(out.resolve("audit.json"),report);
   Files.writeString(out.resolve("WARNINGS.md"),"# Screening component only\n\n"+String.join("\n",result.warnings())+"\n\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(out.resolve("COMPONENT_COMPLETED.json"),Map.of("status","selected-native-screening-component-only","audit",NextflowFiles.pin(out.resolve("audit.json"),67108864,()->{}),"biological_result_admitted",false,"full_eager_adapter_admitted",false));System.out.println("Complete native screening read consistency passed.\n"+ACTION);
  }catch(Exception failure){System.err.println("EAGER SCREENING FAIL2 NO RESULT ADMITTED: "+failure+"\n"+ACTION);if(owned && out!=null)try {NextflowFiles.writeJson(out.resolve("FAILURE.json"),Map.of("status","FAIL2","error",failure.toString(),"required_action",ACTION));Files.writeString(out.resolve("WARNINGS.md"),"# FAIL2 NO RESULT\n\n"+failure+"\n\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){System.err.println("Retain the failed component directory; diagnostic write failed: "+diagnostic);}System.exit(2);}
 }
 private static long number(Map<String,String> options,String name,long fallback)throws IOException {long value=options.containsKey(name)?Long.parseLong(options.get(name)):fallback;if(value<1)throw new IOException("Require a positive complete-population capacity: "+name);return value;}
}
