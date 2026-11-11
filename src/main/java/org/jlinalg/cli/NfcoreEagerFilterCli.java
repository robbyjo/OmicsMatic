/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.workflow.NfcoreEagerReadFilter;

/** Native source-derived BAM read-length helper; no upstream runtime needed. */
public final class NfcoreEagerFilterCli {
 private NfcoreEagerFilterCli() { }
 public static void main(String[] args) {
  try {
   if(args.length==1 && args[0].equals("--help")) {
    System.out.println("--input ORIGINAL.bam --out NEW.bam --minimum-length INTEGER --include-unmapped true|false --maximum-records INTEGER\nNative eager query-sequence-length filtering, including soft clips; no MAPQ or TLEN filter. Preserve original input and all diagnostics. Complete record capacity never truncates an admitted result.");return;
   }
   var options=new LinkedHashMap<String,String>();var expected=Set.of("--input","--out","--minimum-length","--include-unmapped","--maximum-records");
   for(int i=0;i<args.length;i+=2)if(i+1>=args.length || !expected.contains(args[i]) || options.putIfAbsent(args[i],args[i+1])!=null)throw new IllegalArgumentException("Require complete unique options; see --help");
   if(!options.keySet().equals(expected) || !Set.of("true","false").contains(options.get("--include-unmapped")))throw new IllegalArgumentException("Declare every option and a literal boolean; see --help");
   var result=NfcoreEagerReadFilter.filterBam(Path.of(options.get("--input")),Path.of(options.get("--out")),Long.parseLong(options.get("--minimum-length")),Boolean.parseBoolean(options.get("--include-unmapped")),Long.parseLong(options.get("--maximum-records")));
   System.out.println(NextflowJson.json(Map.of("input_records",result.inputRecords(),"output_records",result.outputRecords(),"input_unmapped_records",result.inputUnmappedRecords(),"output_unmapped_records",result.outputUnmappedRecords(),"minimum_length",result.minimumLength(),"include_unmapped",result.includeUnmapped(),"full_eager_workflow_admitted",false)));
   if(result.outputRecords()==0)System.err.println("WARNING: Filtering retained zero records. This is a filter count, not a negative biological finding or downstream-analysis admission.");
  }catch(Exception failure){System.err.println("EAGER FILTER FAIL2 NO RESULT ADMITTED: "+failure+"\nRequired action: retain original inputs and any partial NEW output; resolve the named field/path/BAM/capacity error and retry a NEW path. Existing outputs are not overwritten. This helper does not admit a complete eager workflow or biological conclusion.");System.exit(2);}
 }
}
