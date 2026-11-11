/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;

/** Copy complete selected source records from verified immutable objects for native-only replay. */
public final class NfcoreEagerScreeningExport {
 private NfcoreEagerScreeningExport() { }
 public static void main(String[] args)throws Exception {
  Path selected=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var verified=NfcoreEngineBundle.verify(selected,()->{});var files=new NfcoreScientificFiles(verified);var members=new LinkedHashMap<String,Object>();var cases=new ArrayList<Map<String,Object>>();
  for(String library:List.of("JK2782","JK2802")) {
   String mapping=library.equals("JK2782")?"JK2782_PE.mapped.bam":"JK2802_SE.mapped.bam";var row=new LinkedHashMap<String,Object>();row.put("library",library);
   var paths=new LinkedHashMap<String,String>();paths.put("mapped_bam","mapping/bwa/"+mapping);paths.put("filtered_bam","samtools/filter/"+library+".filtered.bam");paths.put("unmapped_fastq","samtools/filter/"+library+".unmapped.fastq.gz");paths.put("malt_sam_gzip","metagenomic_classification/malt/"+library+".unmapped.blastn.sam.gz");paths.put("raw_flagstat","samtools/stats/"+library+"_flagstat.stats");paths.put("filtered_flagstat","samtools/filtered_stats/"+library+"_postfilterflagstat.stats");
   for(var entry:paths.entrySet()) {Path source=files.output(entry.getValue());String name="data/"+library+"/"+entry.getValue().substring(entry.getValue().lastIndexOf('/')+1);Path target=out.resolve(name);Files.createDirectories(target.getParent());Files.copy(source,target);var pin=NextflowFiles.pin(source,1000000000,()->{});if(!pin.equals(NextflowFiles.pin(target,1000000000,()->{})))throw new java.io.IOException("Export bytes differ");row.put(entry.getKey(),name);members.put(name,Map.of("pin",pin,"original_output",entry.getValue()));}
   cases.add(row);
  }
  for(String name:List.of("ref.db","ref.idx","ref.inf")) {
   Path source=files.inputMember(files.alias("malt_database")+"/"+name),target=out.resolve(name);Files.copy(source,target);members.put(name,Map.of("pin",NextflowFiles.pin(source,1000000000,()->{}),"original_input",files.alias("malt_database")+"/"+name));
  }
  Files.copy(files.output("metagenomic_classification/malt/malt.log"),out.resolve("malt.log"));members.put("malt.log",Map.of("pin",NextflowFiles.pin(out.resolve("malt.log"),1000000000,()->{}),"original_output","metagenomic_classification/malt/malt.log"));
  NextflowFiles.writeJson(out.resolve("cases.json"),cases);members.put("cases.json",Map.of("pin",NextflowFiles.pin(out.resolve("cases.json"),1048576,()->{})));
  var after=NfcoreEngineBundle.verify(selected,()->{});if(!verified.manifest().equals(after.manifest()) || !verified.attempt().equals(after.attempt()))throw new java.io.IOException("Original complete result changed");
  NextflowFiles.writeJson(out.resolve("SOURCE.json"),Map.of("status","complete-selected-source-populations-exported-only","members",members,"engine_manifest",verified.manifest(),"raw_engine_status_labels_reinterpreted",false,"biological_result_admitted",false));System.out.println("PASS_COMPLETE_SOURCE_SCREENING_POPULATION_EXPORT_ONLY");
 }
}
