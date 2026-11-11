/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;
import java.util.List;
import static org.jlinalg.cli.MtagJson.*;

/** External development consumer for typed request/runtime contracts; no GWAS analysis. */
public final class MtagWorkflowReferenceProbe {
    private MtagWorkflowReferenceProbe() { }
    public static void main(String[] args) {
        try {
            need(args.length==3&&List.of("request","runtime").contains(args[0]),"Require request|runtime INPUT NEW_OUTPUT");
            Path source=Path.of(args[1]).toAbsolutePath().normalize(),out=Path.of(args[2]).toAbsolutePath().normalize();
            if(args[0].equals("request")) {
                var r=MtagRequest.read(source);
                write(out,map("status","PASS_TYPED_MTAG_REQUEST_ONLY","traits",(long)r.traits().size(),"output",r.out(),
                    "runtime_profile",r.profile(),"sigma",r.covariance().get("sigma"),"omega",r.covariance().get("omega"),
                    "fdr",r.fdr().get("mode"),"analysis_executed",false));
            } else {
                var runtime=MtagRuntime.read(source);
                write(out,map("status","PASS_COMPLETE_MTAG_RUNTIME_ONLY","python",runtime.python(),"environment",runtime.environment(),
                    "source",runtime.source(),"analysis_executed",false,"runtime_profile",MtagRuntime.pin(source,67108864)));
            }
            System.out.println("PASS MTAG development contract; no analysis or catalog credit");
        } catch(MtagRuntime.Unavailable unavailable) {
            System.err.println("SKIP3_MTAG_DEPENDENCY_UNAVAILABLE: "+unavailable.getMessage()+"\nRequired action: skip dependent original MTAG analysis; supply the exact permitted runtime or exported setup prerequisites, then retry NEW paths.");System.exit(3);
        } catch(Exception error) {
            System.err.println("FAIL2_NO_MTAG_RESULT: "+error+"\nRequired action: preserve the attempt and original inputs, correct the named request/integrity/capacity cause and retry NEW paths. No repair or result admission.");System.exit(2);
        }
    }
}
