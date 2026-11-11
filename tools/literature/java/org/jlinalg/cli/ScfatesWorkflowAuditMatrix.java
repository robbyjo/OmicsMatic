/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Recheck an entire completed original worker family without rerunning upstream software.
 * This audit receipt cannot admit an earlier failed public CLI batch. */
public final class ScfatesWorkflowAuditMatrix {
    private ScfatesWorkflowAuditMatrix() { }
    public static void main(String[] args)throws Exception {
        require(args.length==2,"RUN_DIRECTORY NEW_REPORT");Path home=Path.of(args[0]).toAbsolutePath().normalize(),report=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(report,LinkOption.NOFOLLOW_LINKS),"New native-only report required");var request=ScfatesWorkflowRequest.parse(ScfatesWorkflowRuntime.read(home.resolve("request.original.json"),16777216),home);var worker=ScfatesWorkflowRuntime.read(home.resolve("worker/COMPLETED.json"),16777216);require(text(worker.get("status")).equals("PASS_COMPLETE_ORIGINAL_SCFATES_WORKFLOWS_ONLY")&&Boolean.FALSE.equals(worker.get("namespace_patched"))&&Boolean.FALSE.equals(worker.get("analysis_installed_software"))&&Boolean.FALSE.equals(worker.get("pickle_deserialized"))&&list(worker.get("cases")).size()==request.analyses().size(),"A complete unchanged original family is required; partial source batches stay unadmitted");var inputs=new LinkedHashMap<String,Map<String,ScfatesWorkflowInputs.Matrix>>();for(var input:request.inputs()){var matrix=ScfatesWorkflowInputs.read(input,home.resolve("inputs").resolve(input.key()+"."+input.format()),request);inputs.computeIfAbsent(input.id(),ignored->new LinkedHashMap<>()).put(input.role(),matrix);}var meter=new ScfatesWorkflowAudit.Meter(request.auditOperations());var warnings=new ArrayList<String>();var cases=new ArrayList<Object>();for(int i=0;i<request.analyses().size();i++){var spec=request.analyses().get(i);String id=text(spec.get("id"));require(text(object(list(worker.get("cases")).get(i)).get("id")).equals(id),"Original cases reordered");ScfatesWorkflowInputs.choices(spec,inputs.get(id),request);cases.add(ScfatesWorkflowAudit.analyze(spec,home.resolve("worker").resolve(id),inputs.get(id),request,warnings,meter));}NextflowFiles.writeJson(report,Map.of("status","PASS_COMPLETE_NATIVE_SCFATES_OBSERVED_WORKFLOW_AUDIT_ONLY","cases",cases,"numeric_states",meter.numeric,"identity_states",meter.identities,"tsv_states",meter.tsv,"audit_operations",meter.used,"warnings",warnings,"public_execution_adapter_batch_admitted",false,"independent_trajectory_refit",false));
    }
}
