/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;
/** Runs the packaged independent audit against a NEW, separately copied complete source-control family. */
public final class CellPhoneDbWorkflowAuditMatrix {
    private CellPhoneDbWorkflowAuditMatrix() { }
    public static void main(String[] args)throws Exception {
        require(args.length==3,"Require request, copied control root and NEW result JSON");Path path=Path.of(args[0]),home=Path.of(args[1]),out=Path.of(args[2]);require(!Files.exists(out),"Existing audit evidence protected");var root=CellPhoneDbWorkflowRuntime.read(path,16777216);var request=CellPhoneDbWorkflowRequest.parse(root,path.getParent());var captured=new LinkedHashMap<String,Path>();for(var input:request.inputs())captured.put(input.id(),input.source());var preflight=CellPhoneDbWorkflowInputs.preflight(request,captured);var meter=new CellPhoneDbWorkflowAudit.Meter(request.auditOperations());var warnings=new ArrayList<String>();var audits=new ArrayList<Object>();
        for(var specification:request.analyses()){String name=text(specification.get("id"));System.out.println("AUDIT "+name);audits.add(CellPhoneDbWorkflowAudit.analyze(specification,home.resolve(name),preflight.metadata().get(name),request,warnings,meter));}
        NextflowFiles.writeJson(out,Map.of("status","PASS_NATIVE_AUDIT_OF_COPIED_ORIGINAL_CONTROLS_ONLY","cases",audits,"operations",meter.used,"numeric_cells",meter.numeric,"identity_states",meter.identities,"tsv_cells",meter.tsv,"warnings",warnings,"implementation_credit",false));
    }
}
