/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Path;
/** Test-side composition of the existing integrity verifier and the new summary consistency gate. */
public final class HifiasmVerifyConsistencyAudit {
    private HifiasmVerifyConsistencyAudit(){ }
    public static void main(String[] args){int code=2;try{if(args.length!=2||!args[0].equals("--out"))throw new java.io.IOException("Require --out retained-result");Path out=Path.of(args[1]).toAbsolutePath().normalize();HifiasmVerifyCli.verify(out);HifiasmResultConsistency.verify(out);System.out.println("PASS_COMPLETE_HIFIASM_INTEGRITY_AND_SUMMARY_CONSISTENCY_ONLY");code=0;}catch(Exception error){System.err.println("FAIL2_HIFIASM_INTEGRITY_OR_SUMMARY_CONSISTENCY: "+error);}System.exit(code);}
}
