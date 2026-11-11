/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.HopsEvidence;

/** Native def_anc data statistics only; no upstream runtime/install, plotting or biological authentication. */
public final class HopsEvidenceCli {
 private HopsEvidenceCli() { }
 private static final String ACTION="Required action: use complete original default/ancient MaltExtract tables and explicit literal output node identities. Read WARNINGS.md and retain missing/NaN/Inf statistics and failed attempts. Resolve the named format, identity or capacity discrepancy before retrying a NEW directory. Classes are screening criteria, not read counts, absence or biological authentication. This component produces statistics/matrices only; it does not replace MALT, LCA, MaltExtract filtering or original PDF/RData/default-mode behavior. Missing or forbidden required upstream dependencies skip their dependent analyses; this native component performs no installation or upstream execution.";
 public static void main(String[] args) {
  Path out=null;boolean owned=false;
  try {
   if(args.length==1 && args[0].equals("--help")) {System.out.println("--results COMPLETE_MALTEXTRACT_RESULTS --nodes-file LITERAL_OUTPUT_NAMES --out NEW_DIRECTORY\nOne exact output node per line; no quote stripping, whitespace substitution or deduplication. def_anc data only.\nOptional positive capacities: --maximum-file-bytes (67108864), --maximum-table-rows (1000000), --maximum-table-cells (100000000), --maximum-samples (10000), --maximum-taxa (100000).\n"+ACTION);return;}
   var required=Set.of("--results","--nodes-file","--out");var optional=Set.of("--maximum-file-bytes","--maximum-table-rows","--maximum-table-cells","--maximum-samples","--maximum-taxa");var options=new LinkedHashMap<String,String>();
   for(int i=0;i<args.length;i+=2)if(i+1>=args.length || !required.contains(args[i])&&!optional.contains(args[i]) || options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique complete options; see --help");
   if(!options.keySet().containsAll(required))throw new IOException("Declare all result, literal-node and output paths; see --help");
   out=Path.of(options.get("--out")).toAbsolutePath().normalize();Files.createDirectory(out);owned=true;
   var limits=new HopsEvidence.Limits(value(options,"--maximum-file-bytes",67108864),value(options,"--maximum-table-rows",1000000),value(options,"--maximum-table-cells",100000000),(int)value(options,"--maximum-samples",10000),(int)value(options,"--maximum-taxa",100000));
   Path nodes=Path.of(options.get("--nodes-file"));if(Files.size(nodes)>limits.fileBytes())throw new IOException("Complete literal node list exceeds byte capacity");
   var result=HopsEvidence.inspect(Path.of(options.get("--results")),Files.readAllLines(nodes),limits);var document=new LinkedHashMap<String,Object>(result.document());document.put("status","selected-native-hops-def-anc-statistics-only");document.put("biological_result_admitted",false);document.put("full_hops_or_eager_adapter_admitted",false);document.put("new_tool_credit",0);NextflowFiles.writeJson(out.resolve("statistics.json"),document);
   Files.writeString(out.resolve("WARNINGS.md"),"# HOPS statistics component only\n\n"+String.join("\n",result.warnings())+"\n\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(out.resolve("COMPONENT_COMPLETED.json"),Map.of("status","native-hops-def-anc-statistics-only","statistics",NextflowFiles.pin(out.resolve("statistics.json"),67108864,()->{}),"new_tool_credit",0,"biological_result_admitted",false));System.out.println("Native HOPS def_anc statistics completed.\n"+ACTION);
  }catch(Exception error) {System.err.println("HOPS EVIDENCE FAIL2 NO RESULT ADMITTED: "+error+"\n"+ACTION);if(owned && out!=null)try {NextflowFiles.writeJson(out.resolve("FAILURE.json"),Map.of("status","FAIL2","error",error.toString(),"required_action",ACTION));Files.writeString(out.resolve("WARNINGS.md"),"# FAIL2 NO RESULT\n\n"+error+"\n\n"+ACTION+"\n",StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic) {System.err.println("Retain the failed component; diagnostic write failed: "+diagnostic);}System.exit(2);}
 }
 private static long value(Map<String,String> options,String key,long fallback)throws IOException {long value=options.containsKey(key)?Long.parseLong(options.get(key)):fallback;if(value<1 || (key.equals("--maximum-samples") || key.equals("--maximum-taxa")) && value>Integer.MAX_VALUE)throw new IOException("Require a positive representable complete-population capacity: "+key);return value;}
}
