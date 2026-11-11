// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import java.util.List;
import org.jlinalg.sequence.HtseqFeatureAssignment;
import org.jlinalg.sequence.HtseqFeatureAssignment.*;

/** Synthetic, already-aligned interval example; does not read or count sequencing files. */
public final class AssignFeatures {
    private AssignFeatures() { }
    private static Interval interval(long start,long end) { return new Interval("chr1",start,end,'+'); }
    private static void check(Index index,String label,Interval query,Mode mode,List<String> expected) {
        var result=index.assign(List.of(query),mode);
        if(!result.features().equals(expected))throw new AssertionError("Unexpected feature assignment: "+label+"/"+mode);
        System.out.println(label+" "+mode+" "+result.outcome()+" "+result.features());
    }
    public static void main(String[] args) {
        var index=HtseqFeatureAssignment.index(List.of(
            new Feature(interval(0,12),"gene-A"),new Feature(interval(7,18),"gene-B"),
            new Feature(interval(20,26),"gene-A")),true);
        check(index,"overlap",interval(5,10),Mode.UNION,List.of("gene-A","gene-B"));
        check(index,"overlap",interval(5,10),Mode.INTERSECTION_STRICT,List.of("gene-A"));
        check(index,"overlap",interval(5,10),Mode.INTERSECTION_NONEMPTY,List.of("gene-A"));
        check(index,"gap",interval(19,24),Mode.UNION,List.of("gene-A"));
        check(index,"gap",interval(19,24),Mode.INTERSECTION_STRICT,List.of());
        check(index,"gap",interval(19,24),Mode.INTERSECTION_NONEMPTY,List.of("gene-A"));
        try {index.steps(interval(4,4));throw new AssertionError("Zero-length query was admitted");}
        catch(IllegalArgumentException expected) {System.out.println("zero-length query REFUSED");}
        var limited=HtseqFeatureAssignment.index(List.of(new Feature(interval(0,10),"gene-A")),true,
            new Limits(1,2,1,10,1),()->false);
        try {limited.assign(List.of(interval(0,5),interval(0,5)),Mode.UNION);throw new AssertionError("Exceeded match capacity was admitted");}
        catch(IllegalArgumentException expected) {System.out.println("whole-assignment capacity REFUSED");}
    }
}
