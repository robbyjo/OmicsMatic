/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.HopsEvidence;

/** Compare all source statistics/matrices with exact original R AST controls. */
public final class HopsEvidenceControl {
 private HopsEvidenceControl() { }
 private static final HopsEvidence.Limits LIMITS=new HopsEvidence.Limits(67108864,1000000,100000000,10000,100000);
 public static void main(String[] args)throws Exception {
  var config=NextflowFiles.object(NextflowFiles.json(Path.of(args[0]),1048576));Path out=Path.of(args[1]);Files.createDirectory(out);long pairs=0;int groups=0,classes=0,refused=0;var reports=new ArrayList<Map<String,Object>>();Path first=null;
  for(Object raw:NextflowFiles.list(config.get("groups"))) {
   var group=NextflowFiles.object(raw);Path data=Path.of((String)group.get("data")),reference=Path.of((String)group.get("expected"));if(first==null)first=data;
   var result=HopsEvidence.inspect(data,Files.readAllLines(data.resolve("literal-nodes.txt")),LIMITS);List<String> expected=Files.readAllLines(reference.resolve("statistics.tsv"));require(expected.size()==result.pairs().size()+1,"Every original sample/node pair");
   for(int i=0;i<result.pairs().size();i++) {String[] row=expected.get(i+1).split("\t",-1);var pair=result.pairs().get(i);require(row.length==16 && pair.sample().equals(row[0]) && pair.taxon().equals(row[1]),"Literal sample/node/order");compare(pair.defaults(),row,2);compare(pair.ancient(),row,9);}
   require(result.fullMatrix().equals(matrix(reference.resolve("full-matrix.tsv"))),"Complete original full matrix");require(result.reducedMatrix().equals(matrix(reference.resolve("reduced-matrix.tsv"))),"Complete original reduced matrix");pairs+=result.pairs().size();groups++;reports.add(Map.of("group",group.get("label"),"pairs",result.pairs().size(),"full_matrix",result.fullMatrix(),"reduced_matrix",result.reducedMatrix()));
  }
  List<String> cases=Files.readAllLines(Path.of((String)config.get("classification_cases"))),expected=Files.readAllLines(Path.of((String)config.get("classification_expected")));require(cases.size()==expected.size(),"Complete class boundary population");
  for(int i=1;i<cases.size();i++) {String[] row=cases.get(i).split("\t",-1),wanted=expected.get(i).split("\t",-1);require(row[0].equals(wanted[0]),"Classification identity/order");var defaults=new HopsEvidence.Statistics("literal",null,null,number(row[1]),null,number(row[2]),null);var ancient=new HopsEvidence.Statistics("literal",null,null,number(row[3]),null,null,null);require(HopsEvidence.evidenceClass(defaults,ancient)==Integer.parseInt(wanted[1]),"Exact original threshold/NA/NaN class: "+row[0]);classes++;}
  if(first==null)throw new AssertionError("Require complete source groups");Path data=first;List<String> nodes=Files.readAllLines(data.resolve("literal-nodes.txt"));
  for(var limits:List.of(new HopsEvidence.Limits(1,1000000,100000000,10000,100000),new HopsEvidence.Limits(67108864,1,100000000,10000,100000),new HopsEvidence.Limits(67108864,1000000,1,10000,100000),new HopsEvidence.Limits(67108864,1000000,100000000,1,100000),new HopsEvidence.Limits(67108864,1000000,100000000,10000,1))) {
   try {HopsEvidence.inspect(data,nodes,limits);throw new AssertionError("Incomplete-capacity assessment admitted");}catch(IOException refusal) {require(refusal.getMessage().contains("Required action:"),"Prominent recovery action");refused++;}
  }
  try {HopsEvidence.inspect(data,List.of(nodes.get(0),nodes.get(0)),LIMITS);throw new AssertionError("Duplicate node repaired");}catch(IOException refusal) {refused++;}
  try {HopsEvidence.statistics("literal",new long[]{-1,2,0,0,0,0},new Double[]{0.,0.,0.,0.},0.,false);throw new AssertionError("Negative edit counts admitted");}catch(IllegalArgumentException refusal) {refused++;}
  var absent=HopsEvidence.inspect(data,List.of("MISSING_LITERAL_NODE"),LIMITS);require(absent.pairs().stream().allMatch(x->x.evidenceClass()==1 && x.defaults().node()==null && x.defaults().dr4()==null && x.ancient().dr4()==null),"Missing nodes stay NA even when source class1");require(absent.warnings().stream().anyMatch(x->x.startsWith("UNASSESSABLE:")),"Missing-node warning");NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","native-hops-source-statistics-and-classes-only","groups",groups,"sample_node_pairs",pairs,"classification_boundaries",classes,"refusals",refused,"missing_node_unassessable_checks",absent.pairs().size(),"reports",reports,"new_tool_credit",0,"biological_result_admitted",false));System.out.println("PASS_NATIVE_HOPS_SOURCE_STATISTICS pairs="+pairs+" classification="+classes+" refusals="+refused);
 }
 private static void compare(HopsEvidence.Statistics actual,String[] fields,int start) {
  require(Objects.equals(actual.node(),fields[start].equals("NA")?null:fields[start]),"Original retained/missing node: "+fields[start]);
  numeric(actual.dr6(),fields[start+1]);integer(actual.n6(),fields[start+2]);numeric(actual.dr4(),fields[start+3]);integer(actual.n4(),fields[start+4]);numeric(actual.maximumTerminalDamage(),fields[start+5]);numeric(actual.uniquePerReference(),fields[start+6]);
 }
 private static void numeric(Double actual,String text) {Double wanted=number(text);require(actual==null?wanted==null:wanted!=null && Double.doubleToLongBits(actual)==Double.doubleToLongBits(wanted),"Exact original numeric value: expected "+text+", native "+actual);}
 private static void integer(Long actual,String text) {require(Objects.equals(actual,text.equals("NA")?null:new java.math.BigDecimal(text).longValueExact()),"Original source count: "+text+" native "+actual);}
 private static Double number(String text) {return switch(text){case "NA"->null;case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.valueOf(text);};}
 private static Map<String,Map<String,Integer>> matrix(Path path)throws IOException {
  var lines=Files.readAllLines(path);String[] header=lines.get(0).split("\t",-1);var result=new LinkedHashMap<String,Map<String,Integer>>();
  for(int i=1;i<lines.size();i++) {String[] fields=lines.get(i).split("\t",-1);require(fields.length==header.length,"Complete matrix row");var row=new LinkedHashMap<String,Integer>();for(int j=1;j<header.length;j++)row.put(header[j],Integer.valueOf(fields[j]));require(result.putIfAbsent(fields[0],row)==null,"Literal matrix identity");}return result;
 }
 private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
