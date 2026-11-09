/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Offline complete artifact integrity check; does not rerun or certify upstream biology. */
public final class HifiasmVerifyCli {
    private HifiasmVerifyCli(){ }
    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){try{if(args.length!=2||!args[0].equals("--out"))throw new IOException("Use --out completed-analysis-directory");verify(Path.of(args[1]));System.out.println("PASS_COMPLETE_HIFIASM_RESULT_INTEGRITY_ONLY: every retained file matches; biological correctness was not independently certified");return 0;}catch(Exception invalid){System.err.println("FAIL2_HIFIASM_RESULT_INTEGRITY: "+invalid+"\nPreserve the receipt and complete original outputs; recover the exact original evidence or run a NEW analysis. Never refresh scientific hashes to hide a discrepancy.");return 2;}}
    static void verify(Path supplied)throws IOException{
        Path out=supplied.toAbsolutePath().normalize();require(out.equals(out.toRealPath())&&Files.isDirectory(out,LinkOption.NOFOLLOW_LINKS),"Require canonical completed result directory");var report=GseapyExecutionRuntime.read(out.resolve("COMPLETED.json"),33554432);require("PASS_COMPLETE_ORIGINAL_HIFIASM_AND_NATIVE_GRAPH_WORKFLOW".equals(report.get("status"))&&Boolean.TRUE.equals(report.get("analysis_admitted"))&&Boolean.FALSE.equals(report.get("source_namespace_patched"))&&Boolean.FALSE.equals(report.get("analysis_installed_software"))&&Boolean.TRUE.equals(report.get("raw_inputs_unchanged"))&&Boolean.TRUE.equals(report.get("host_bindings_unchanged"))&&list(report.get("owned_live_after_cleanup")).isEmpty(),"Entire original/native workflow was not admitted");
        Path index=HifiasmRuntime.regular(out.resolve("result-files.json"),33554432);require(NextflowFiles.fingerprint(report.get("result_files_pin"),33554432).equals(NextflowFiles.pin(index,33554432,()->{})),"Retained result inventory changed");require(NextflowFiles.fingerprint(report.get("warnings_pin"),16777216).equals(NextflowFiles.pin(out.resolve("WARNINGS.md"),16777216,()->{})),"Prominent source warnings/actions changed");var expected=GseapyExecutionRuntime.read(index,33554432);Set<String> found=new HashSet<>();try(var tree=Files.walk(out)){for(Path file:tree.toList()){require(!Files.isSymbolicLink(file),"Retained result contains a symbolic link");if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))continue;require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Retained result contains a nonregular node");String member=out.relativize(file).toString().replace('\\','/');if(Set.of("COMPLETED.json","result-files.json","WARNINGS.md").contains(member))continue;require(found.add(member)&&expected.containsKey(member),"Unexpected retained result file: "+member);require(NextflowFiles.fingerprint(expected.get(member),1000000000000L).equals(NextflowFiles.pin(file,1000000000000L,()->{})),"Retained original/native result changed: "+member);}}require(found.equals(expected.keySet()),"Complete retained result population differs");var request=GseapyExecutionRuntime.read(out.resolve("request.original.json"),16777216);var analyses=list(request.get("analyses"));require(analyses.size()==list(report.get("analyses")).size(),"Complete original analysis population differs");for(Object raw:analyses){var analysis=object(raw);String id=text(analysis.get("id"));require(id.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}"),"Invalid retained analysis identity");var process=GseapyExecutionRuntime.read(out.resolve("analyses").resolve(id).resolve("process/process.json"),16777216);require(integer(process.get("exit_code"),0,0)==0&&list(process.get("owned_live_after_cleanup")).isEmpty()&&"completed-process-only".equals(process.get("status")),"Retained original assembly exit/cleanup was not successful");}HifiasmResultConsistency.verify(out);
    }
}
