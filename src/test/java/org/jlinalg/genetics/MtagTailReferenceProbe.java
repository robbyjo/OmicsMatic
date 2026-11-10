/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Complete source-tail controls, including explicit finite-profile refusals; no GWAS fit. */
public final class MtagTailReferenceProbe {
    private MtagTailReferenceProbe() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Require INPUT and NEW_OUTPUT");
        try(var reader=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.UTF_8);
            var writer=Files.newBufferedWriter(Path.of(args[1]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            if(!"row\tz_hex".equals(reader.readLine()))throw new IllegalArgumentException("Complete tail schema required");
            writer.write("row\tz_hex\tstatus\tp_hex\n");int i=0;
            for(String line;(line=reader.readLine())!=null;){String[] f=line.split("\t",-1);
                if(f.length!=2||Integer.parseInt(f[0])!=++i)throw new IllegalArgumentException("Complete original tail row order differs");
                double z=Double.parseDouble(f[1]);
                try {double p=MtagScipy121NormalTail.twoSidedP(z);writer.write(f[0]+"\t"+f[1]+"\tNATIVE_RETURNED\t"+Double.toHexString(p)+"\n");}
                catch(IllegalArgumentException unsupported){if(Double.isFinite(z))throw unsupported;writer.write(f[0]+"\t"+f[1]+"\tNATIVE_FINITE_PROFILE_REFUSAL\t\n");}
            }
        }
    }
}
