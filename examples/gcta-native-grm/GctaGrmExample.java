/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.genetics.GctaHardcallGrm;

/** Prepared diploid autosomal allele counts, in variant-by-sample order. */
public final class GctaGrmExample {
    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("this synthetic example takes no arguments");
        var samples = List.of(new GctaHardcallGrm.SampleId("F1", "S1"),
            new GctaHardcallGrm.SampleId("F2", "S1"),
            new GctaHardcallGrm.SampleId("F3", "S3"));
        double missing = Double.NaN;
        var result = GctaHardcallGrm.compute(samples,
            List.of("variant1", "variant2", "variant3", "allMissing"),
            new double[][] {{0,1,2}, {2,1,0}, {missing,1,2}, {missing,missing,missing}},
            GctaHardcallGrm.Algorithm.SOURCE_0, GctaHardcallGrm.Limits.defaults());
        System.out.println("included_markers=" + result.includedMarkers());
        System.out.println("FID1\tIID1\tFID2\tIID2\tGRM\tjointly_called_markers");
        for (int i=0; i<samples.size(); i++) for (int j=0; j<=i; j++) {
            var first=samples.get(i); var second=samples.get(j);
            System.out.println(first.familyId()+"\t"+first.individualId()+"\t"
                +second.familyId()+"\t"+second.individualId()+"\t"
                +result.relationship(i,j)+"\t"+result.jointlyCalledMarkers(i,j));
        }
        for (var marker : result.markers())
            System.out.println(marker.id()+"\t"+marker.disposition());
    }
}
