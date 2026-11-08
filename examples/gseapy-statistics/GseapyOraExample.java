/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.GseapyStatistics;

/** Prepared local gene sets; no software installation, remote Enrichr or guessed species mapping. */
public final class GseapyOraExample {
    private GseapyOraExample(){ }
    public static void main(String[] args){
        var query=List.of("G1","G2","G2");
        var sets=Map.of("pathway-A",List.of("G1","G3"),
                "pathway-B",List.of("G1","G2"),"no-overlap",List.of("G6"));
        var background=new GseapyStatistics.Universe(Set.of("G1","G2","G3","G4","G5","G6"));
        var rows=GseapyStatistics.overRepresentation(
                GseapyStatistics.Edition.V1_1_2,query,sets,background,0.05);
        for(var row:rows){
            for(String warning:row.warnings())System.err.println("WARNING / REQUIRED ACTION: "+warning);
            if(!Double.isFinite(row.pValue())||!Double.isFinite(row.adjustedPValue()))
                throw new IllegalStateException("Undefined original test; skip dependent inference and correct the explicit background.");
            System.out.println(row.term()+" overlap="+row.overlap()+"/"+row.termSize()
                    +" p="+row.pValue()+" q="+row.adjustedPValue()+" odds="+row.oddsRatio());
        }
        if(rows.size()!=2||Math.abs(rows.get(0).pValue()-0.6)>1e-14
                ||Math.abs(rows.get(1).pValue()-1.0/15)>1e-14)
            throw new IllegalStateException("Complete prepared-set example did not match its exact combinatoric tails");
        System.out.println("Native local one-sided ORA complete. Remote Enrichr and whole-paper reproduction remain separate.");
    }
}
