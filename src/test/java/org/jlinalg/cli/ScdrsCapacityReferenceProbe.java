/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Parse declared capacities without loading expression data or allocating its logical matrix. */
public final class ScdrsCapacityReferenceProbe {
    private ScdrsCapacityReferenceProbe() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Require request and NEW output");
        var request=ScdrsRequest.read(Path.of(args[0]));
        Files.writeString(Path.of(args[1]),request.limits().matrixElements()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
