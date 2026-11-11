/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Read-only complete observation audit driver, independent of the original runtime/JVM OS. */
public final class PalantirWorkflowMatrix {
    private PalantirWorkflowMatrix() { }
    public static void main(String[] args)throws Exception {
        require(args.length==3,"REQUEST_JSON ORIGINAL_WORKER_DIRECTORY NEW_REPORT");Path file=Path.of(args[0]).toAbsolutePath().normalize(),home=Path.of(args[1]).toAbsolutePath().normalize(),out=Path.of(args[2]).toAbsolutePath().normalize();require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"New report only");var request=PalantirWorkflowRequest.parse(PalantirWorkflowRuntime.read(file,16777216),file.getParent());var meter=new PalantirWorkflowAudit.Meter(request.auditOperations());var warnings=new ArrayList<String>();var cases=new ArrayList<Object>();for(int i=0;i<request.inputs().size();i++){var input=PalantirWorkflowInputs.read(request.inputs().get(i),request);var specification=request.analyses().get(i);PalantirWorkflowInputs.choices(specification,input,request);cases.add(PalantirWorkflowAudit.analyze(specification,home.resolve(text(specification.get("id"))),input,request,warnings,meter));}NextflowFiles.writeJson(out,Map.of("status","PASS_COMPLETE_ORIGINAL_PALANTIR_OBSERVATION_AUDITS_ONLY","cases",cases,"audit_operations",meter.used,"numeric_states",meter.numeric,"identity_states",meter.identities,"tsv_states",meter.tsv,"warnings",warnings,"independent_trajectory_refit",false));
    }
}
