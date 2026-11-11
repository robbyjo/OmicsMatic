/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Complete original MALT RMA/SAM/query and database-taxonomy objects for assignment controls. */
public final class NfcoreMaltTaxonomyExport {
 private NfcoreMaltTaxonomyExport() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require verified engine bundle and NEW complete export directory");Path selected=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var before=NfcoreEngineBundle.verify(selected,()->{});var files=new NfcoreScientificFiles(before);var members=new LinkedHashMap<String,Object>();var cases=new ArrayList<Map<String,Object>>();
  for(String library:List.of("JK2782","JK2802")) {
   var row=new LinkedHashMap<String,Object>();row.put("library",library);
   for(var item:Map.of("rma6","metagenomic_classification/malt/"+library+".unmapped.rma6","sam_gzip","metagenomic_classification/malt/"+library+".unmapped.blastn.sam.gz","unmapped_fastq","samtools/filter/"+library+".unmapped.fastq.gz").entrySet()) {
    String name="data/"+library+"/"+Path.of(item.getValue()).getFileName();copy(files.output(item.getValue()),out,name,members,"original_output",item.getValue());row.put(item.getKey(),name);
   }cases.add(row);
  }
  for(String name:List.of("taxonomy.idx","taxonomy.tre","taxonomy.map","ref.db","ref.idx","ref.inf"))copy(files.inputMember(files.alias("malt_database")+"/"+name),out,"database/"+name,members,"original_input",files.alias("malt_database")+"/"+name);
  copy(files.output("metagenomic_classification/malt/malt.log"),out,"malt.log",members,"original_output","metagenomic_classification/malt/malt.log");NextflowFiles.writeJson(out.resolve("cases.json"),cases);members.put("cases.json",Map.of("pin",NextflowFiles.pin(out.resolve("cases.json"),1048576,()->{})));
  var after=NfcoreEngineBundle.verify(selected,()->{});if(!before.manifest().equals(after.manifest()) || !before.attempt().equals(after.attempt()))throw new IOException("Original complete bundle changed");NextflowFiles.writeJson(out.resolve("SOURCE.json"),Map.of("status","complete-original-malt-taxonomy-and-rma-query-populations-only","members",members,"source_engine_manifest",before.manifest(),"hops_taxonomy_substituted",false,"biological_result_admitted",false,"new_tool_credit",0));System.out.println("PASS_COMPLETE_ORIGINAL_MALT_RMA_AND_DATABASE_TAXONOMY_EXPORT_ONLY");
 }
 private static void copy(Path source,Path out,String name,Map<String,Object> members,String kind,String original)throws IOException {var pin=NextflowFiles.pin(source,1000000000,()->{});Path target=out.resolve(name);Files.createDirectories(target.getParent());Files.copy(source,target);if(!pin.equals(NextflowFiles.pin(target,1000000000,()->{})))throw new IOException("Complete exported data differ");members.put(name,Map.of("pin",pin,kind,original));}
}
