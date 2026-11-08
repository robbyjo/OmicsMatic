/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Single retained-data audit for real corruption controls, without Python execution or batch admission. */
public final class GseapyExecutionSingleAudit {
    private GseapyExecutionSingleAudit(){ }
    public static void main(String[] args)throws Exception{
        require(args.length==5&&!Files.exists(Path.of(args[4])),"Require case, request, captured input, case ID and NEW receipt");Path home=Path.of(args[0]).toAbsolutePath().normalize(),requestPath=Path.of(args[1]).toAbsolutePath().normalize(),inputPath=Path.of(args[2]).toAbsolutePath().normalize(),out=Path.of(args[4]).toAbsolutePath().normalize();var request=GseapyWorkflowRequest.parse(GseapyExecutionRuntime.read(requestPath,16777216),requestPath.getParent());var specification=request.analyses().stream().filter(a->args[3].equals(a.get("id"))).findFirst().orElseThrow();
        try{var input=GseapyWorkflowInputs.read(inputPath,specification,request);var meter=new GseapyExecutionAudit.Meter(request.auditOperations());var audit=GseapyExecutionAudit.analyze(specification,home,input,request,new ArrayList<>(),meter);NextflowFiles.writeJson(out,Map.of("status","passed-single-native-audit-only","audit",audit,"analysis_admitted",false));}
        catch(Exception error){NextflowFiles.writeJson(out,Map.of("status","rejected-no-analysis-admitted","error",error.toString(),"analysis_admitted",false));error.printStackTrace(System.err);System.exit(2);}
    }
}
