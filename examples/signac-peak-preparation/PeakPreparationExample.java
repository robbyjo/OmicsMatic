/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.sequence.SignacFragments;
import org.jlinalg.sequence.SignacPeakPreparation;

/** Synthetic preparation only; no peak caller or significance test is run. */
public final class PeakPreparationExample {
    private PeakPreparationExample() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Require INPUT_FRAGMENTS NEW_OUTPUT_DIRECTORY");
        var groups=List.of(new SignacPeakPreparation.CellGroup("cell_a","group A"),
                          new SignacPeakPreparation.CellGroup("cell_b","group B"),
                          new SignacPeakPreparation.CellGroup("cell_c","group C"));
        // A null map explicitly declares object-cell IDs equal raw barcodes.
        var source=new SignacFragments.Source(Path.of(args[0]),null);
        var result=SignacPeakPreparation.split(List.of(source),groups,Path.of(args[1]),
                                               SignacPeakPreparation.Limits.defaults(),()->false);
        for(var group:result.groups())System.out.println(group.sourceName()+"\t"+group.records()+"\t"+group.file().getFileName());
        for(var warning:result.warnings())System.err.println(warning.code()+": "+warning.message()+" Required action: "+warning.requiredAction());
        System.out.println("PREPARATION ONLY: no MACS peak call, p value or biological result was produced.");
    }
}
