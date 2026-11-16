/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.jlinalg.alignment.SambambaBamAudit;

/** Validation harness only; it does not replace any original scientific operation. */
public final class QualificationProbe {
    private QualificationProbe() { }
    public static void main(String[] args) {
        try {
            switch (args[0]) {
                case "read" -> {
                    SambambaWorkflowBundle.read(Path.of(args[1]).toRealPath());
                    System.out.println("PASS_SAVED_PAYLOAD_COHERENCE_ONLY_NOT_FRESH_CLASSIFICATION");
                }
                case "compare" -> {
                    var a=SambambaWorkflowBundle.read(Path.of(args[1]).toRealPath());
                    var b=SambambaWorkflowBundle.read(Path.of(args[2]).toRealPath());
                    if(!a.config().get("edition").equals(b.config().get("edition"))
                            ||a.config().remove()!=b.config().remove())throw new IllegalArgumentException("Comparison requires the same edition/action");
                    for(boolean remove:a.config().remove()?java.util.List.of(false,true):java.util.List.of(false)) {
                        String phase=remove?"remove":"mark",name=remove?"deduplicated.bam":"marked.bam";
                        SambambaBamAudit.compareActualRuns(a.root().resolve(name),b.root().resolve(name),
                            SambambaWorkflowFiles.headerCommand(SambambaWorkflowBundle.sourceCommand(phase,a.config(),a.capture())),
                            SambambaWorkflowFiles.headerCommand(SambambaWorkflowBundle.sourceCommand(phase,b.config(),b.capture())),
                            SambambaBamAudit.Limits.defaults());
                    }
                    System.out.println("PASS_COMPLETE_ORIGINAL_BAM_RECORD_COMPARISON_INDEX_NOT_QUALIFIED");
                }
                case "late-console" -> {
                    String[] cli=Arrays.copyOfRange(args,1,args.length);Path out=null;
                    for(int i=0;i<cli.length-1;i+=2)if(cli[i].equals("--out"))out=Path.of(cli[i+1]);
                    Path marker=out.resolve("COMPLETED.properties");
                    var fault=new PrintStream(System.out) {
                        @Override public void println(String message) {
                            if(message.startsWith("PASS_ORIGINAL_SAMBAMBA_")) {
                                try {
                                    if(!Files.isRegularFile(marker))throw new IllegalStateException("Fault did not reach completion");
                                    System.err.println("CONTROLLED_FAILURE_AFTER_COMPLETION marker_sha256="+SambambaWorkflowFiles.sha(marker));
                                }catch(Exception e){throw new IllegalStateException("Control setup failed",e);}
                                throw new IllegalStateException("CONTROLLED_FINAL_CONSOLE_RECORDING_FAILURE");
                            }
                            super.println(message);
                        }
                    };
                    System.exit(SambambaWorkflowCli.run(cli,fault,System.err));
                }
                default -> throw new IllegalArgumentException("Use read, compare or late-console");
            }
        }catch(Throwable failure) {
            failure.printStackTrace(System.err);System.exit(2);
        }
    }
}
