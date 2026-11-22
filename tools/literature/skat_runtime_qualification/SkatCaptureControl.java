/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Test-only coherent resealing; never calls the audit or changes original R objects. */
public final class SkatCaptureControl {
    private SkatCaptureControl() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("science|seal|seal-preserve-warnings ROOT");
        Path root=Path.of(args[1]);
        Map<String,Object> raw=SkatWorkflowFiles.read(root.resolve("request.json"),2_097_152);
        var request=SkatWorkflowContracts.validate(raw);
        var manifest=SkatWorkflowFiles.read(root.resolve("manifest.json"),268_435_456);
        var state=SkatWorkflowFiles.read(root.resolve("analysis-status.json"),2_097_152);
        if(args[0].equals("science")) {
            SkatWorkflowFiles.json(root.resolve("effective-request.json"),request);
            Files.writeString(root.resolve("worker/parameters.tsv"),SkatWorkflowFiles.parameters(request),StandardCharsets.UTF_8);
            manifest.put("source_request_sha256",SkatWorkflowFiles.hash(Files.readAllBytes(root.resolve("source-request.json"))));
            SkatWorkflowFiles.json(root.resolve("manifest.json"),manifest);
        }else if(!args[0].equals("seal")&&!args[0].equals("seal-preserve-warnings"))throw new IllegalArgumentException("Unknown control operation");
        if(!args[0].equals("seal-preserve-warnings"))
            Files.writeString(root.resolve("WARNINGS.md"),SkatWorkflowCli.warnings(root,(String)state.get("status"),(String)state.get("error"),manifest),StandardCharsets.UTF_8);
        var seal=SkatWorkflowFiles.read(root.resolve("capture-files.json"),268_435_456);
        seal.put("files",SkatWorkflowFiles.inventory(root,request,false));
        SkatWorkflowFiles.json(root.resolve("capture-files.json"),seal);
        System.out.println("Test-only coherent reseal;no original R calls,no audit regeneration,no authentication claim");
    }
}
