/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Test-only native semantic replay; no execution/authenticity or portable analysis admission is claimed. */
public final class CiceroAuditReplay {
    private CiceroAuditReplay() { }
    public static void main(String[] args){
        try{
            require(args.length==3,"Require candidate JAR, request and copied worker paths");
            require(CiceroWorkflowCli.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(Path.of(args[0]).toAbsolutePath().toUri()),"Wrong candidate class source");
            Path file=Path.of(args[1]).toAbsolutePath(),worker=Path.of(args[2]).toAbsolutePath();byte[] raw=NextflowFiles.bytes(file,1048576);var r=CiceroWorkflowRequest.parse(object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000)),file.getParent());
            var p=CiceroWorkflowInputs.validate(worker.getParent().resolve("inputs"),r);var meter=new CiceroWorkflowAudit.Meter(r.auditOperations());var c=CiceroWorkflowAudit.constructed(worker,p,r,meter);
            for(var a:r.analyses())CiceroWorkflowAudit.analysis(worker.resolve("cases").resolve(text(a.get("id"))),a,p,c,r,meter);
            System.out.println("Native semantic replay passed; original solver was not refitted or execution authenticated");
        }catch(Exception failure){System.err.println("NATIVE AUDIT REFUSED: "+failure);System.exit(2);}
    }
}
