/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Export only complete named research outputs/data; never copy protected provider programs or engine object trees. */
public final class NfcoreEagerCaptureExport {
 private NfcoreEagerCaptureExport() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require verified private engine bundle and NEW selected-data export directory");
  Path selected=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var before=NfcoreEngineBundle.verify(selected,()->{});var files=new NfcoreScientificFiles(before);var members=new LinkedHashMap<String,Object>();
  for(String sample:List.of("JK2782","JK2802","QC_VARIANT"))for(String name:List.of("genotyping/"+sample+".unifiedgenotyper.vcf.gz","bcftools/stats/"+sample+".vcf.stats"))copy(files.output(name),out,name,members,"original_output",name);
  copy(files.output("pipeline_info/software_versions.csv"),out,"software_versions.csv",members,"original_output","pipeline_info/software_versions.csv");
  for(String alias:List.of("fasta","design","adapters"))copy(files.inputMember(files.alias(alias)),out,"inputs/"+alias+(alias.equals("fasta")?".fasta":alias.equals("design")?".tsv":".txt"),members,"original_input",files.alias(alias));
  String synthetic=files.alias("bundle")+"/inputs/QC_VARIANT.complete-windows.fastq.gz";copy(files.inputMember(synthetic),out,"inputs/QC_VARIANT.complete-windows.fastq.gz",members,"original_input",synthetic);
  NextflowFiles.writeJson(out.resolve("params.json"),files.parameters());members.put("params.json",Map.of("pin",NextflowFiles.pin(out.resolve("params.json"),67108864,()->{})));
  var after=NfcoreEngineBundle.verify(selected,()->{});if(!before.manifest().equals(after.manifest()) || !before.attempt().equals(after.attempt()))throw new IOException("Private source bundle changed during selected export");
  NextflowFiles.writeJson(out.resolve("SOURCE.json"),Map.of("status","complete-named-capture-control-data-export-only","members",members,"private_engine_manifest",before.manifest(),"standalone_complete_engine_bundle",false,"protected_program_bytes_included",false,"biological_result_admitted",false,"new_tool_credit",0));System.out.println("PASS_SELECTED_COMPLETE_CAPTURE_DATA_EXPORT_NO_PROGRAMS");
 }
 private static void copy(Path source,Path out,String name,Map<String,Object> members,String kind,String original)throws IOException {
  var pin=NextflowFiles.pin(source,1000000000,()->{});Path target=out.resolve(name);Files.createDirectories(target.getParent());Files.copy(source,target);if(!pin.equals(NextflowFiles.pin(target,1000000000,()->{})))throw new IOException("Complete exported data differ");members.put(name,Map.of("pin",pin,kind,original));
 }
}
