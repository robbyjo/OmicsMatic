/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.workflow.NfcoreEagerSamples;

/** Compare every source-oracle case, tuple and raw cell; no scientific data. */
public final class NfcoreEagerSamplesControl {
 private NfcoreEagerSamplesControl() { }
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);var cases=NextflowFiles.list(NextflowFiles.json(root.resolve("cases.json"),1048576));
  int accepted=0,refused=0;
  for(String version:List.of("2.3.4","2.4.4")) {
   var observations=NextflowFiles.list(NextflowFiles.json(root.resolve("observed-"+version+".json"),1048576));
   require(cases.size()==49 && cases.size()==observations.size(),"Complete original populations");
   for(int i=0;i<cases.size();i++) {
    var item=NextflowFiles.object(cases.get(i));var oracle=NextflowFiles.object(observations.get(i));
    require(item.get("name").equals(oracle.get("name")),"Source case order");
    var rows=new ArrayList<Map<String,String>>();
    for(Object raw:NextflowFiles.list(item.get("rows"))) {
     var row=new LinkedHashMap<String,String>();
     for(var entry:NextflowFiles.object(raw).entrySet())row.put((String)entry.getKey(),(String)entry.getValue());
     rows.add(row);
    }
    var exist=new HashSet<>(NextflowFiles.list(item.get("existing_files")));
    List<NfcoreEagerSamples.Row> inspected;
    try { inspected=NfcoreEagerSamples.inspect(version.equals("2.3.4")?NfcoreEagerSamples.Version.EAGER_234:NfcoreEagerSamples.Version.EAGER_244,rows,exist::contains); }
    catch(IOException expected) {
     require(oracle.get("status").equals("refused"),"Source accepted a Java-refused case: "+item.get("name"));
     require(expected.getMessage().contains("FAIL2") && expected.getMessage().contains("Required action:"),"Prominent diagnostic/action");refused++;continue;
    }
    require(oracle.get("status").equals("accepted-row-inspection-only"),"Java accepted source refusal: "+item.get("name"));
    var tuples=NextflowFiles.list(oracle.get("tuples"));require(inspected.size()==tuples.size(),"Complete output rows");
    for(int j=0;j<inspected.size();j++) {
     var row=inspected.get(j);var tuple=NextflowFiles.list(tuples.get(j));require(row.cells().equals(rows.get(j)),"Literal cells");require(tuple.size()==11,"Source tuple width");
     for(int k=0;k<11;k++) {
      String value=row.cells().get(NfcoreEagerSamples.COLUMNS.get(k));
      if(k>=8 && !value.equals("NA"))value=item.get("file_scope")+"/"+value;
      require(value.equals(tuple.get(k)),"Complete source tuple cell "+k+" in "+item.get("name"));
     }
     var files=new ArrayList<String>();for(String key:List.of("R1","R2","BAM"))if(!row.cells().get(key).equals("NA"))files.add(row.cells().get(key));
     require(files.equals(row.files()),"All literal paths and order");
     boolean immutable=false;try {row.cells().put("Sample_Name","changed");}catch(UnsupportedOperationException expected){immutable=true;}require(immutable,"Immutable source metadata snapshot");
    }
    accepted++;
   }
  }
  require(accepted==26 && refused==72,"All positive and refusal cases across both source versions");
  System.out.println("PASS_ORIGINAL_EAGER_ROW_CASES accepted="+accepted+" refused="+refused+"; no TSV parser, workflow or scientific admission");
 }
 private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
