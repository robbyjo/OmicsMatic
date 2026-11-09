/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;
import java.util.Map;

/** Native audit of a retained original worker, without executing an R process. */
public final class CellChatWorkflowAuditProbe {
    private CellChatWorkflowAuditProbe() { }
    public static void main(String[] args)throws Exception {
        Path input=Path.of(args[0]).toAbsolutePath().normalize(),worker=Path.of(args[1]).toAbsolutePath().normalize();
        Object analysis=NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(input,1048576)),1048576,100000);
        Map<String,Object> root=Map.of("schema_version",1,"out","unused-out","runtime_profile","unused-runtime-profile.json","analysis",analysis,
            "limits",Map.of("input_bytes",268435456,"output_bytes",1073741824,"wall_seconds",1800,"max_genes",100000,"max_cells",100000,"max_files",200000,"runtime_bytes",34359738368L));
        var request=CellChatWorkflowRequest.parse(root,input.getParent());
        var population=CellChatWorkflowTables.inputs(input.getParent().resolve("expression.tsv"),input.getParent().resolve("metadata.tsv"),request);
        var result=CellChatWorkflowAudit.verify(worker,request,population);
        System.out.println(NextflowJson.json(result));
    }
}
