/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Path;
import org.jlinalg.genetics.FlashpcaPcaAudit;

/** Native source-output check; no upstream execution, PCA refit or family admission. */
public final class NativeReplay {
    public static void main(String[] args)throws Exception {
        boolean legacy=args[2].equals("1.2.6");
        var options=new FlashpcaPcaAudit.Options(legacy?FlashpcaPcaAudit.Edition.V1_2_6:FlashpcaPcaAudit.Edition.V2_0,20,
            legacy?FlashpcaPcaAudit.Standardization.BINOM:FlashpcaPcaAudit.Standardization.BINOM2,
            legacy?FlashpcaPcaAudit.Divisor.N1:FlashpcaPcaAudit.Divisor.P,false,legacy?1e-4:1e-5);
        var limits=new FlashpcaPcaAudit.Limits(100000,1000000,1073741824,1073741824,2000000000L,65536);
        System.out.println(FlashpcaPcaAudit.audit(Path.of(args[0]),Path.of(args[1]),options,limits,()->{}));
    }
}
