/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Portable strict owned-process receipt contract; never creates a current-host ticket. */
final class TomtomProcessReceipt {
    private TomtomProcessReceipt() { }
    static void verify(Path root,List<String> command,RsemProcess.Limits limits,List<String> accounted,Runnable check)throws IOException {
        check.run();var process=NextflowFiles.object(NextflowFiles.json(root.resolve("process.json"),16777216));var launch=NextflowFiles.object(NextflowFiles.json(root.resolve("launch.json"),16777216));
        require("completed-process-only".equals(process.get("status"))&&NextflowFiles.integer(process.get("exit_code"),0,0)==0&&NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty(),"Original Tomtom process/owned cleanup is not complete");
        require("strict-zero-exit-process".equals(process.get("process_purpose"))&&NextflowFiles.list(process.get("accepted_process_exit_codes")).size()==1&&NextflowFiles.integer(NextflowFiles.list(process.get("accepted_process_exit_codes")).get(0),0,0)==0,"Original Tomtom process did not require strict zero exit");
        require(NextflowJson.json(process.get("limits")).equals(NextflowJson.json(SimpleJson.parse(RsemJson.json(limits))))&&NextflowJson.json(launch.get("limits")).equals(NextflowJson.json(process.get("limits"))),"Complete original Tomtom process capacities differ from requested limits");
        var wrapped=new ArrayList<>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+limits.addressSpaceBytes(),"--cpu="+limits.cpuSeconds(),"--fsize="+limits.fileBytes(),"--nofile="+limits.openFiles(),"--core=0","--"));wrapped.addAll(command);
        require(wrapped.equals(NextflowFiles.strings(process.get("command")))&&wrapped.equals(NextflowFiles.strings(launch.get("command"))),"Captured original Tomtom supervised command differs");
        require(accounted.equals(NextflowFiles.strings(process.get("accounted_roots")))&&accounted.equals(NextflowFiles.strings(launch.get("accounted_roots"))),"Complete Tomtom prepared/process accounting scope differs");
        // This elapsed record also includes cleanup/final integrity I/O; the
        // supervisor enforces wall time while the original process is live.
        NextflowFiles.integer(process.get("elapsed_millis"),0,Long.MAX_VALUE);check.run();
    }
    private static void require(boolean value,String message)throws IOException {MemeRuntimeFiles.require(value,message);}
}
