/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Replay the actual audit against retained original data; never execute Python or admit a failed original batch. */
public final class GseapyExecutionReplay {
    private GseapyExecutionReplay(){ }
    public static void main(String[] args)throws Exception{
        require(args.length==2&&!Files.exists(Path.of(args[1])),"Require retained Java execution root and NEW replay receipts");Path source=Path.of(args[0]).toAbsolutePath().normalize(),out=Files.createDirectory(Path.of(args[1]).toAbsolutePath().normalize());var records=new ArrayList<Object>();int failures=0;
        for(String edition:List.of("1.0.4","1.1.2","1.1.3","1.1.8")){
            Path home=source.resolve(edition);var raw=GseapyExecutionRuntime.read(home.resolve("request.original.json"),16777216);var request=GseapyWorkflowRequest.parse(raw,home);var meter=new GseapyExecutionAudit.Meter(request.auditOperations());var warnings=new ArrayList<String>();
            for(var specification:request.analyses()){
                String id=text(specification.get("id"));var record=new LinkedHashMap<String,Object>();record.put("edition",edition);record.put("id",id);System.out.println("AUDIT "+edition+"/"+id);
                try{var input=GseapyWorkflowInputs.read(home.resolve("inputs").resolve(id+".json"),specification,request);record.put("audit",GseapyExecutionAudit.analyze(specification,home.resolve("worker").resolve(id),input,request,warnings,meter));record.put("status","passed-retained-data-audit-only");}
                catch(Exception error){record.put("status","failed-no-case-admitted");record.put("error",error.toString());error.printStackTrace(System.err);failures++;}
                records.add(record);
            }
        }
        NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status",failures==0?"passed-complete-retained-native-audit-replay-only":"failed-retained-native-audit-replay","cases",records,"failed_cases",failures,"original_execution_admitted",false));System.out.println("Replay failures: "+failures);if(failures>0)System.exit(2);
    }
}
