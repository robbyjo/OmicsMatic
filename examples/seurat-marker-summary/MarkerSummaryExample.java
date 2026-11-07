/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.jlinalg.sequence.SeuratMarkerSummary.*;

/** Synthetic summaries only: no Wilcoxon/logistic test or significance claim. */
public final class MarkerSummaryExample {
    public static void main(String[] args) {
        var matrix=new Matrix(List.of("peak_A","peak_B"),List.of("cell_1","cell_2","cell_3","cell_4"),
            new int[]{0,2,4,6,8},new int[]{0,1,0,1,0,1,0,1},new double[]{4,0,5,1,1,5,0,4});
        var summary=summarize(matrix,new int[]{0,1},new int[]{2,3},Options.current(Slot.COUNTS));
        var selected=prefilter(summary,new Filters(.1,Double.NEGATIVE_INFINITY,.25,false),Slot.DATA);
        emit("feature\tfold_change\tpct_1\tpct_2");
        for(var f:selected)emit(f.id()+"\t"+f.foldChange()+"\t"+f.fraction1()+"\t"+f.fraction2());
        for(String warning:summary.warnings())emit("WARNING: "+warning);
        emit("SUMMARY ONLY: no hypothesis test, p value or biological significance was computed.");
    }
    private static void emit(String text){System.out.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
