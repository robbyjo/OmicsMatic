/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import org.jlinalg.genetics.LdscLdScores;
import org.jlinalg.genetics.PlinkBedDataset;

/** Complete synthetic L018 SDK demonstration, run from the repository root. */
public final class LdscLdScoresExample {
    private LdscLdScoresExample(){ }
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("This fixed synthetic demonstration takes no arguments");
        Path folder=Path.of("examples/ldsc-scores");
        var ids=new HashSet<>(Files.readAllLines(folder.resolve("variant-ids.txt")));
        try(var dataset=PlinkBedDataset.open(folder.resolve("reference"),ids)) {
            var variants=dataset.selectedVariants();var lines=Files.readAllLines(folder.resolve("coordinates.tsv"));
            if(lines.size()!=variants.size()+1||!lines.get(0).equals("variant\tcoordinate"))throw new IllegalArgumentException("Complete declared coordinate table required");
            double[] coordinates=new double[variants.size()];
            for(int i=0;i<coordinates.length;i++){String[] row=lines.get(i+1).split("\t",-1);if(row.length!=2||!row[0].equals(variants.get(i).id()))throw new IllegalArgumentException("Exact selected BIM order required; no inferred join");coordinates[i]=Double.parseDouble(row[1]);}
            // Synthetic original L018: all FAM samples, unpartitioned all-ones
            // annotation, coordinate window10, requested chunk1, strict MAF>0.
            var result=LdscLdScores.fromPlink(dataset,null,coordinates,null,true,10,1,0,LdscLdScores.Limits.defaults());
            System.err.println("SYNTHETIC SDK DEMONSTRATION; no study or whole-paper result.");
            for(String warning:result.warnings())System.err.println(warning);
            System.err.println("M="+Arrays.toString(result.annotationCounts()));
            System.err.println("M_5_50="+Arrays.toString(result.commonAnnotationCounts()));
            int[] kept=result.retainedInputIndices();double[][] scores=result.ldScores();
            System.out.println("variant\tL2");for(int i=0;i<kept.length;i++)System.out.println(variants.get(kept[i]).id()+"\t"+scores[i][0]);
        }
    }
}
