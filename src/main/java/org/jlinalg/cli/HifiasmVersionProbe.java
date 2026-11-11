/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Source-defined version exits only; never accepts a nonzero assembly or compilation result. */
final class HifiasmVersionProbe {
    private HifiasmVersionProbe(){ }
    static Map<String,Object> probe(Path binary,String edition,Map<String,String> environment,Path work,RsemProcess.Check integrity)throws IOException{
        int expected=List.of("0.14.2-r315","0.15.1-r328").contains(edition)?1:0;IOException observed=null;
        try{RsemProcess.executeOwned(List.of(binary.toString(),"--version"),environment,work,RsemProcess.Limits.health(),integrity,()->{});}
        catch(IOException failure){if(expected!=1||!"Original RSEM process failed, exit 1".equals(failure.getMessage())||failure.getSuppressed().length!=0)throw failure;observed=failure;}
        var raw=GseapyExecutionRuntime.read(work.resolve("process.json"),1048576);require(integer(raw.get("exit_code"),0,1)==expected&&list(raw.get("owned_live_after_cleanup")).isEmpty(),"Original version exit/cleanup differs from its exact source contract");
        if(expected==1)require(observed!=null&&"java.io.IOException: Original RSEM process failed, exit 1".equals(raw.get("error"))&&list(raw.get("suppressed")).isEmpty(),"Original version probe has another failure or unknown cleanup");
        else require(observed==null&&"completed-process-only".equals(raw.get("status"))&&!raw.containsKey("error"),"Original zero-exit version probe failed");
        require(Files.readString(work.resolve("process.stdout")).equals(edition+"\n")&&Files.size(work.resolve("process.stderr"))==0,"Actual original version text/diagnostics differs");integrity.verify();var result=Map.<String,Object>of("status","PASS_SOURCE_DEFINED_VERSION_PROBE_ONLY","edition",edition,"expected_exit_code",expected,"actual_exit_code",expected,"raw_process_receipt_unchanged",true,"assembly_admitted",false,"interpretation","Only the original --version invocation has this edition-specific exit contract. The strict process primitive and its raw receipt stay unchanged; no nonzero compilation or assembly is accepted.");NextflowFiles.writeJson(work.resolve("source-version-interpretation.json"),result);return result;
    }
}
