/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerScreeningReads;

/** Real complete two-library upstream software-control intake; no biological admission. */
public final class NfcoreEagerScreeningControl {
 private NfcoreEagerScreeningControl() { }
 public static void main(String[] args)throws Exception {
  Path selected=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);
  var verified=NfcoreEngineBundle.verify(selected,()->{});var files=new NfcoreScientificFiles(verified);
  var params=files.parameters();if(!Boolean.TRUE.equals(params.get("run_metagenomic_screening")) || !Boolean.TRUE.equals(params.get("run_maltextract")) || !"malt".equals(params.get("metagenomic_tool")))throw new java.io.IOException("Require the complete selected original MALT/HOPS control");
  int mapq=(int)NextflowFiles.integer(params.get("bam_mapping_quality_threshold"),0,255);long minLength=params.containsKey("bam_filter_minreadlength")?NextflowFiles.integer(params.get("bam_filter_minreadlength"),0,1000000000):0;
  String database=files.alias("malt_database");var references=NfcoreEagerScreeningReads.references(files.inputMember(database+"/ref.db"),files.inputMember(database+"/ref.idx"),files.inputMember(database+"/ref.inf"),1000000,10000000000L);if(references.size()!=3)throw new AssertionError("Require every original upstream control reference");
  Path views=Files.createDirectory(out.resolve("views"));var reports=new ArrayList<Map<String,Object>>();
  for(String library:List.of("JK2782","JK2802")) {
   Path fastq=views.resolve(library+".fastq.gz");Files.copy(files.output("samtools/filter/"+library+".unmapped.fastq.gz"),fastq);
   String mapping=library.equals("JK2782")?"JK2782_PE.mapped.bam":"JK2802_SE.mapped.bam";
   var result=NfcoreEagerScreeningReads.inspect(files.output("mapping/bwa/"+mapping),files.output("samtools/filter/"+library+".filtered.bam"),fastq,files.output("metagenomic_classification/malt/"+library+".unmapped.blastn.sam.gz"),references,mapq,minLength,new NfcoreEagerScreeningReads.Limits(1000000,1000000,50000000,1000000));
   var report=new LinkedHashMap<String,Object>(result.document());report.put("library",library);reports.add(report);
  }
  var after=NfcoreEngineBundle.verify(selected,()->{});if(!verified.attempt().equals(after.attempt()) || !verified.manifest().equals(after.manifest()))throw new java.io.IOException("Complete original result changed during native screening inspection");
  NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","complete-selected-native-host-FASTQ-MALT-alignment-component-only","references",references,"libraries",reports,"engine_manifest",verified.manifest(),"biological_result_admitted",false,"full_eager_adapter_admitted",false,"new_tool_credit",0));System.out.println("PASS_COMPLETE_ORIGINAL_EAGER_SCREENING_READ_POPULATIONS_ONLY");
 }
}
