/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerReadFilter;

/** Run all original complete populations; the original provider compares every record afterwards. */
public final class NfcoreEagerFilterControl {
 private NfcoreEagerFilterControl() { }
 public static void main(String[] args)throws Exception {
  Path reference=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var records=new ArrayList<Map<String,Object>>();
  var cases=NextflowFiles.list(NextflowFiles.json(reference.resolve("native-cases.json"),1048576));require(cases.size()==36,"Complete source population count");
  for(Object raw:cases) {
   var row=NextflowFiles.object(raw);Path input=reference.resolve((String)row.get("input_member")),output=out.resolve((String)row.get("name")+".bam");
   var before=NextflowFiles.pin(input,2147483648L,()->{});long minimum=NextflowFiles.integer(row.get("minimum_length"),Long.MIN_VALUE,Long.MAX_VALUE);boolean include=(Boolean)row.get("include_unmapped");
   var result=NfcoreEagerReadFilter.filterBam(input,output,minimum,include,1000000);
   require(NextflowFiles.pin(input,2147483648L,()->{}).equals(before),"Original input changed");require(result.inputRecords()==NextflowFiles.integer(row.get("input_records"),0,1000000),"Complete input count");require(result.outputRecords()==NextflowFiles.integer(row.get("output_records"),0,1000000),"Original output count");require(result.outputUnmappedRecords()==NextflowFiles.integer(row.get("output_unmapped"),0,1000000),"Original unmapped count");
   records.add(Map.of("case",row.get("name"),"input_records",result.inputRecords(),"output_records",result.outputRecords(),"output_unmapped",result.outputUnmappedRecords(),"pin",NextflowFiles.pin(output,2147483648L,()->{})));
  }
  Path input=reference.resolve("inputs/input0.bam"),existing=out.resolve((String)NextflowFiles.object(cases.get(0)).get("name")+".bam");var originalInput=NextflowFiles.pin(input,2147483648L,()->{});var originalOutput=NextflowFiles.pin(existing,2147483648L,()->{});
  refused(()->NfcoreEagerReadFilter.filterBam(input,existing,30,true,1000000));require(NextflowFiles.pin(existing,2147483648L,()->{}).equals(originalOutput),"Existing output changed");
  refused(()->NfcoreEagerReadFilter.filterBam(input,input,30,true,1000000));require(NextflowFiles.pin(input,2147483648L,()->{}).equals(originalInput),"Same-path original changed");
  Path partial=out.resolve("capacity-refused.partial.bam");refused(()->NfcoreEagerReadFilter.filterBam(input,partial,0,true,1));require(Files.exists(partial),"Retain partial output on capacity refusal");
  Path invalid=out.resolve("invalid-input.bam");Files.writeString(invalid,"not a BAM");refused(()->NfcoreEagerReadFilter.filterBam(invalid,out.resolve("invalid-refused.bam"),0,true,1000000));
  NextflowFiles.writeJson(out.resolve("NATIVE_FILTER_COMPLETED.json"),Map.of("status","all-original-population-counts-match-semantics-pending-provider-comparison","cases",records,"refusals",4,"full_eager_adapter_admitted",false,"new_tool_credit",0));
  System.out.println("PASS_NATIVE_EAGER_FILTER_COUNTS_36_AND_4_REFUSALS; complete semantic BAM comparison remains required");
 }
 private interface Operation { void run()throws Exception; }
 private static void refused(Operation action)throws Exception {try{action.run();}catch(IOException expected){require(expected.getMessage().contains("FAIL2")&&expected.getMessage().contains("Required action:"),"Actionable no-result diagnostic");return;}throw new AssertionError("Expected a complete no-result refusal");}
 private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
