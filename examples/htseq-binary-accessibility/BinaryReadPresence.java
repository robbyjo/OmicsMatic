/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.sequence.HtseqBinaryAccessibility;
import org.jlinalg.sequence.HtseqBinaryAccessibility.Read;
import org.jlinalg.sequence.HtseqFeatureAssignment;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;

/** Prepared native binary presence; cell assignment and filtering are already supplied. */
public final class BinaryReadPresence {
    private BinaryReadPresence() { }
    public static void main(String[] args) {
        var features=List.of(new Feature(new Interval("chr1",0,10,'+'),"A"),
            new Feature(new Interval("chr1",5,15,'-'),"B"),new Feature(new Interval("chr2",0,10,'+'),"zero"));
        var peaks=List.of("B","zero","A");var cells=List.of("c2","c1","empty");
        var model=HtseqBinaryAccessibility.model(features,peaks,false);
        var reads=List.of(read("c1",1,3),read("c1",1,3),read("c1",6,7),read("c2",12,13),new Read("c2",List.of()));
        var matrix=HtseqBinaryAccessibility.presence(model,cells,reads);
        if(matrix.nonzero()!=3||matrix.processedReads()!=5||matrix.readsOverlappingAnyPeak()!=4||matrix.readsWithoutPeakOverlap()!=1)throw new AssertionError("Incomplete binary presence");
        for(var entry:matrix.entries())System.out.println(matrix.peakIds().get(entry.peakRow())+"\t"+matrix.cellIds().get(entry.cellColumn())+"\t1");
        if(matrix.accessible(1,0)||matrix.accessible(1,1)||matrix.accessible(1,2)||matrix.accessible(0,2)||matrix.accessible(2,2))throw new AssertionError("Zero axes were not retained");
        System.out.println("reads=5 overlapping=4 nonzero=3; zero peak/cell retained");
        var limits=new HtseqBinaryAccessibility.Limits(3,3,10,1,HtseqFeatureAssignment.Limits.defaults());
        var bounded=HtseqBinaryAccessibility.model(features,peaks,false,limits,()->false);
        try{HtseqBinaryAccessibility.presence(bounded,cells,List.of(read("c1",6,7)));throw new AssertionError("Capacity was silently truncated");}
        catch(IllegalArgumentException expected){System.out.println("nonzero-capacity REFUSED");}
    }
    private static Read read(String cell,long start,long end){return new Read(cell,List.of(new Interval("chr1",start,end,'+')));}
}
