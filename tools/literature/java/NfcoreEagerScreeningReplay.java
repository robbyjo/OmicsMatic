/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerScreeningReads;

/** Complete source-population replays plus deliberately inconsistent real source records. */
public final class NfcoreEagerScreeningReplay {
 private NfcoreEagerScreeningReplay() { }
 public static void main(String[] args)throws Exception {
  Path reference=Path.of(args[0]),expected=Path.of(args[1]),mutations=Path.of(args[2]),out=Path.of(args[3]);Files.createDirectory(out);
  var cases=NextflowFiles.list(NextflowFiles.json(reference.resolve("cases.json"),1048576));var wanted=NextflowFiles.list(NextflowFiles.json(expected.resolve("expected-populations.json"),1048576));
  var refs=NfcoreEagerScreeningReads.references(reference.resolve("ref.db"),reference.resolve("ref.idx"),reference.resolve("ref.inf"),1000000,10000000000L);
  require(refs.equals(NextflowFiles.object(NextflowFiles.json(expected.resolve("expected-references.json"),1048576))),"Complete original MALT API reference metadata");require(cases.size()==2 && wanted.size()==2,"Complete original libraries");var reports=new ArrayList<Map<String,Object>>();
  for(int i=0;i<cases.size();i++) {
   var row=NextflowFiles.object(cases.get(i));var source=NextflowFiles.object(wanted.get(i));require(row.get("library").equals(source.get("library")),"Literal source library/order");
   var result=inspect(reference,mutations,row,Map.of(),refs,Map.of());var normalized=NextflowFiles.object(SimpleJson.parse(NextflowJson.json(result.document())));
   require(normalized.get("host").equals(source.get("host")),"Every complete original host/FASTQ count");require(normalized.get("malt").equals(source.get("malt")),"Every complete original MALT count/statistic");
   var report=new LinkedHashMap<String,Object>(result.document());report.put("library",row.get("library"));reports.add(report);
  }
  var adverse=NextflowFiles.list(NextflowFiles.json(mutations.resolve("mutations.json"),1048576));int refused=0,empty=0;var first=NextflowFiles.object(cases.get(0));
  for(Object raw:adverse) {
   var mutation=NextflowFiles.object(raw);var replacements=NextflowFiles.object(mutation.get("replacements"));var limits=NextflowFiles.object(mutation.get("limits"));String name=(String)mutation.get("name");boolean accepted=false;
   try {
    Map<String,Long> selected=refs;
    if(replacements.containsKey("reference_db") || replacements.containsKey("reference_index") || replacements.containsKey("reference_info"))
     selected=NfcoreEagerScreeningReads.references(path(reference,mutations,Map.of("reference_db","ref.db"),replacements,"reference_db"),path(reference,mutations,Map.of("reference_index","ref.idx"),replacements,"reference_index"),path(reference,mutations,Map.of("reference_info","ref.inf"),replacements,"reference_info"),1000000,10000000000L);
    var result=inspect(reference,mutations,first,replacements,selected,limits);accepted=true;
    require("empty-alignments-valid-counts-unassessable-identity".equals(mutation.get("expected")),"Altered source data unexpectedly admitted: "+name);
    require(result.malt().alignments()==0 && result.malt().alignedQueries()==0 && result.malt().minimumIdentity()==null && result.malt().maximumIdentity()==null,"Empty identities must remain absent, not zero");
    require(result.warnings().stream().anyMatch(x->x.contains("No aligned queries")),"Prominent empty-result limitation");empty++;
   }catch(IOException invalid) {require(!"empty-alignments-valid-counts-unassessable-identity".equals(mutation.get("expected")),"Valid empty alignment count refused");Files.writeString(out.resolve(name+".refusal.txt"),invalid.toString()+"\n",StandardOpenOption.CREATE_NEW);refused++;}
   if(!accepted)require("refused".equals(mutation.get("expected")),"Unexpected mutation outcome");
  }
  require(refused==adverse.size()-1 && empty==1,"All deliberate refusal/empty populations");
  NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","complete-native-screening-source-populations-and-semantic-refusals-only","libraries",reports,"source_reference_sequences",refs.size(),"deliberate_refusals",refused,"empty_statistics_preserved_absent",empty,"biological_result_admitted",false,"full_eager_adapter_admitted",false));
  System.out.println("PASS_COMPLETE_SCREENING_SOURCE_POPULATIONS refusals="+refused+" empty="+empty);
 }
 private static NfcoreEagerScreeningReads.Inspection inspect(Path reference,Path mutations,Map<?,?> row,Map<?,?> replacements,Map<String,Long> refs,Map<?,?> limits)throws IOException {
  return NfcoreEagerScreeningReads.inspect(path(reference,mutations,row,replacements,"mapped_bam"),path(reference,mutations,row,replacements,"filtered_bam"),path(reference,mutations,row,replacements,"unmapped_fastq"),path(reference,mutations,row,replacements,"malt_sam_gzip"),refs,(int)value(limits,"minimum_mapq",37),value(limits,"minimum_length",0),new NfcoreEagerScreeningReads.Limits(value(limits,"records",1000000),value(limits,"queries",1000000),value(limits,"query_bases",50000000),value(limits,"alignments",1000000)));
 }
 private static long value(Map<?,?> values,String name,long fallback)throws IOException {return values.containsKey(name)?NextflowFiles.integer(values.get(name),0,Long.MAX_VALUE):fallback;}
 private static Path path(Path reference,Path mutations,Map<?,?> row,Map<?,?> replacements,String name)throws IOException {return replacements.containsKey(name)?mutations.resolve(NextflowFiles.member((String)replacements.get(name))):reference.resolve(NextflowFiles.member((String)row.get(name)));}
 private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
