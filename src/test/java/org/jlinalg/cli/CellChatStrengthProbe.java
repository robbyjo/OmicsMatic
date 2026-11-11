/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.omics.CellChatSignalingStrengths;

/** Original-R rank references, strict cutoff boundaries and invalid-input controls. */
public final class CellChatStrengthProbe {
    private CellChatStrengthProbe() { }
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("--worker-audit")) {
            Path root=Path.of(args[1]);var request=CellChatStrengthRequest.parse(NextflowFiles.json(root.resolve("source-request.json"),1048576),root);
            var population=CellChatStrengthTables.inputs(root.resolve("inputs/expression.tsv"),root.resolve("inputs/metadata.tsv"),request);
            var audit=CellChatStrengthAudit.verify(root.resolve("unadmitted-worker"),request,population);
            System.out.println("PASS_RETAINED_WORKER_COMPONENT_AUDIT_ONLY_NO_ANALYSIS_ADMISSION "+NextflowJson.json(audit));return;
        }
        Path folder=Path.of(args[0]);int comparisons=0;
        for(var row:CellChatStrengthTables.read(folder.resolve("ranks.tsv"),10000)) {
            var value=CellChatSignalingStrengths.rankSum(values(row.get("control")),values(row.get("treated")));
            if(value.statistic()!=Double.parseDouble(row.get("statistic"))||value.tiedBlocks()!=Integer.parseInt(row.get("tied_blocks"))||value.tiedObservations()!=Integer.parseInt(row.get("tied_observations")))throw new AssertionError("Original midrank reference differs: "+row.get("id"));comparisons++;
        }
        for(var row:CellChatStrengthTables.read(folder.resolve("contrasts.tsv"),10000))for(var mode:CellChatSignalingStrengths.DifferenceMode.values()) {
            var c=CellChatSignalingStrengths.contrast(Double.parseDouble(row.get("control")),Double.parseDouble(row.get("treated")),Double.parseDouble(row.get("threshold")),mode);
            boolean p=row.get("positive").equals("TRUE"),n=row.get("negative").equals("TRUE"),a=row.get("absolute").equals("TRUE"),selected=switch(mode){case POSITIVE->p;case NEGATIVE->n;case ABSOLUTE->a;};
            if(c.positiveAboveThreshold()!=p||c.negativeAboveThreshold()!=n||c.absoluteAboveThreshold()!=a||c.selected()!=selected)throw new AssertionError("Original strict cutoff differs: "+row.get("id"));comparisons++;
        }
        int refusals=0;
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            refuse(()->CellChatSignalingStrengths.contrast(bad,1,0.25,CellChatSignalingStrengths.DifferenceMode.ABSOLUTE));refusals++;
            refuse(()->CellChatSignalingStrengths.rankSum(new double[]{bad},new double[]{1}));refusals++;
        }
        refuse(()->CellChatSignalingStrengths.rankSum(new double[0],new double[]{1}));refusals++;
        refuse(()->CellChatSignalingStrengths.contrast(0,1,-1,CellChatSignalingStrengths.DifferenceMode.ABSOLUTE));refusals++;
        if(comparisons<100)throw new AssertionError("Insufficient original controls");
        System.out.println("PASS_ORIGINAL_STRENGTH_AND_RANK_REFERENCES comparisons="+comparisons+" refusals="+refusals);
    }
    private static double[] values(String text){return List.of(text.split(",")).stream().mapToDouble(Double::parseDouble).toArray();}
    private static void refuse(Runnable code){boolean refused=false;try{code.run();}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new AssertionError("Invalid scientific input admitted");}
}
