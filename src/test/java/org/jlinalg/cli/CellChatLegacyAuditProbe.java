/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;
import org.jlinalg.omics.CellChatNetworkSimilarity;
import static org.jlinalg.cli.NextflowFiles.*;

/** Development tests only. Never changes an attempt's admission status. */
public final class CellChatLegacyAuditProbe {
    private CellChatLegacyAuditProbe() { }
    public static void main(String[] args)throws Exception {
        double[][] unique=CellChatNetworkSimilarity.sharedNearestNeighbors(new int[][]{{0,1},{1,2},{2,0}},100);
        if(unique[0][1]!=1.0/3||unique[0][0]!=1)throw new AssertionError("Original unique-rank SNN");
        double[][] duplicate=CellChatNetworkSimilarity.sharedNearestNeighbors(new int[][]{{0,0,1},{1,1,0},{2,2,0}},100);
        if(duplicate[0][1]!=2||duplicate[0][0]!=1)throw new AssertionError("Original duplicate-rank SNN multiplicity lost");
        double[][] infinite=CellChatNetworkSimilarity.sharedNearestNeighbors(new int[][]{{0,0,1,1},{1,1,1,0},{2,2,2,2},{3,3,3,3}},100);
        if(infinite[0][1]!=Double.POSITIVE_INFINITY)throw new AssertionError("Original nonfinite SNN was repaired");
        double[][] functional=CellChatNetworkSimilarity.functional(new double[][][]{{{0,1},{0,0}},{{0,0},{1,0}},{{0,0},{0,0}}},100);
        if(functional[0][1]!=0||functional[2][2]!=1||functional[0][2]!=0)throw new AssertionError("Directed/empty Jaccard changed");
        if(args.length==0){System.out.println("PASS_NATIVE_LEGACY_NETWORK_API");return;}
        if(args.length!=2)throw new IllegalArgumentException("Probe requires request and provisional worker directory");
        Path requestPath=Path.of(args[0]).toAbsolutePath().normalize(),worker=Path.of(args[1]).toAbsolutePath().normalize();
        var request=CellChatLegacyRequest.parse(NextflowParameters.parse(utf8(bytes(requestPath,1048576)),1048576,100000),requestPath.getParent());
        var population=CellChatLegacyTables.inputs(worker.getParent().resolve("inputs/expression.tsv"),worker.getParent().resolve("inputs/metadata.tsv"),request);
        System.out.println(NextflowJson.json(CellChatLegacyAudit.verify(worker,request,population)));
        System.err.println("PROBE ONLY: ORIGINAL ATTEMPT ADMISSION STATUS UNCHANGED");
    }
}
