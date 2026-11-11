/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Development numeric gate only; does not certify a runtime or admit a study bundle. */
public final class MashWorkflowProbe {
    private MashWorkflowProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Supply normalized inputs, original outputs and NEW numeric report");
        try{
            var result=MashWorkflowAudit.audit(Path.of(args[0]),Path.of(args[1]),MashWorkflowAudit.Limits.defaults(),()->{});
            var value=Map.of("status","passed-complete-native-numeric-audit-only","trainingRows",result.trainingRows(),
                "targetRows",result.targetRows(),"conditions",result.conditions(),"components",result.components(),
                "checkedNumericValues",result.checkedNumericValues(),"maximumAbsoluteError",result.maximumAbsoluteError(),
                "maximumScaledError",result.maximumScaledError(),"trainingRelativeDualGap",result.trainingRelativeDualGap(),"warnings",result.warnings());
            Files.write(Path.of(args[2]),RsemJson.bytes(value),StandardOpenOption.CREATE_NEW);
            System.out.println("Native numeric audit passed: "+result.trainingRows()+" training rows, "+result.targetRows()+" target rows, "+result.checkedNumericValues()+" comparisons. No runtime or complete-bundle admission is implied.");
        }catch(Exception error){System.err.println("MASHR NUMERIC FAIL2: "+error.getMessage()+" Required action: preserve all original and native evidence and resolve the named mismatch before admission.");System.exit(2);}
    }
}
