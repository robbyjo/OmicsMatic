/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Complete named HOPS outputs from verified immutable source objects; no provider binaries. */
public final class NfcoreEagerHopsExport {
 private NfcoreEagerHopsExport() { }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IOException("Require verified engine bundle and NEW data export directory");
  Path selected=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);var before=NfcoreEngineBundle.verify(selected,()->{});var files=new NfcoreScientificFiles(before);var outputs=NextflowFiles.object(NextflowFiles.object(NextflowFiles.json(before.attempt().resolve("outputs.json"),67108864)).get("members"));var members=new LinkedHashMap<String,Object>();
  String prefix="maltextract/results/";
  for(Object key:outputs.keySet()) {
   String name=NextflowFiles.text(key);var row=NextflowFiles.object(outputs.get(key));if(!name.startsWith(prefix) || !"file".equals(row.get("kind")))continue;
   Path source=files.output(name),target=out.resolve(name.substring(prefix.length()));Files.createDirectories(target.getParent());Files.copy(source,target);var pin=NextflowFiles.pin(source,1000000000,()->{});if(!pin.equals(NextflowFiles.pin(target,1000000000,()->{})))throw new IOException("Exported HOPS data differ");members.put(name.substring(prefix.length()),Map.of("original_output",name,"pin",pin));
  }
  if(members.size()!=55)throw new IOException("Require all55 original selected HOPS output files");Files.copy(files.inputMember(files.alias("taxon_list")),out.resolve("original-taxon-list.txt"));members.put("original-taxon-list.txt",Map.of("original_input",files.alias("taxon_list"),"pin",NextflowFiles.pin(out.resolve("original-taxon-list.txt"),1048576,()->{})));
  var after=NfcoreEngineBundle.verify(selected,()->{});if(!before.manifest().equals(after.manifest()) || !before.attempt().equals(after.attempt()))throw new IOException("Source bundle changed");NextflowFiles.writeJson(out.resolve("SOURCE.json"),Map.of("status","all55-original-hops-files-and-target-list-exported-only","members",members,"source_engine_manifest",before.manifest(),"full_eager_adapter_admitted",false,"biological_result_admitted",false));System.out.println("PASS_ALL55_ORIGINAL_HOPS_DATA_FILES_EXPORT_ONLY");
 }
}
