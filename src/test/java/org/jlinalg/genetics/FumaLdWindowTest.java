/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FumaLdWindowTest {
    private static FumaLocusDefinition.ReferenceVariant v(int chr,int pos){return new FumaLocusDefinition.ReferenceVariant(chr,pos,"A","G","rs"+chr+"_"+pos,.2);}
    private static FumaLocusDefinition.LdPair p(int chr,int a,int b,double r2){return new FumaLocusDefinition.LdPair(chr,a,b,r2);}
    @Test void coverageSeparatesStoredSparseWindowAndChromosomeCases(){
        FumaLocusDefinition.ReferenceVariant a=v(1,10),b=v(1,20),c=v(1,30),d=v(2,10);var reference=new FumaLocusDefinition.Reference("explicit",List.of(a,b,c,d),List.of(p(1,10,20,.8)),.05,new FumaLocusDefinition.LdWindow(10,1));
        assertEquals(FumaLocusDefinition.PairCoverage.SELF,reference.coverage(a.uid(),a.uid()));assertEquals(FumaLocusDefinition.PairCoverage.STORED,reference.coverage(a.uid(),b.uid()));assertEquals(FumaLocusDefinition.PairCoverage.BELOW_DECLARED_FLOOR,reference.coverage(b.uid(),c.uid()));assertEquals(FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW,reference.coverage(a.uid(),c.uid()));assertEquals(FumaLocusDefinition.PairCoverage.DIFFERENT_CHROMOSOMES,reference.coverage(a.uid(),d.uid()));assertTrue(reference.storedRSquared(a.uid(),c.uid()).isEmpty());assertTrue(reference.storedRSquared(b.uid(),c.uid()).isEmpty());
    }
    @Test void defaultConstructorRetainsUnboundedContract(){FumaLocusDefinition.ReferenceVariant a=v(1,1),b=v(1,2000000);var r=new FumaLocusDefinition.Reference("legacy",List.of(a,b),List.of(),.05);assertEquals(FumaLocusDefinition.LdWindow.unlimited(),r.window());assertEquals(FumaLocusDefinition.PairCoverage.BELOW_DECLARED_FLOOR,r.coverage(a.uid(),b.uid()));assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Reference("missing",List.of(a,b),List.of(),0));}
    @Test void indexGapUsesGenomicOrderRatherThanSuppliedOrder(){FumaLocusDefinition.ReferenceVariant a=v(1,10),b=v(1,20),c=v(1,30);var r=new FumaLocusDefinition.Reference("rank",List.of(c,a,b),List.of(p(1,10,20,.9),p(1,20,30,.8)),0,new FumaLocusDefinition.LdWindow(100,1));assertEquals(FumaLocusDefinition.PairCoverage.STORED,r.coverage(a.uid(),b.uid()));assertEquals(FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW,r.coverage(a.uid(),c.uid()));}
    @Test void storedOutOfWindowPairsFailRatherThanOverrideTheDeclaredProtocol(){var data=List.of(v(1,10),v(1,20),v(1,30));assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Reference("bp",data,List.of(p(1,10,30,1)),.05,new FumaLocusDefinition.LdWindow(19,10)));assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Reference("rank",data,List.of(p(1,10,30,1)),.05,new FumaLocusDefinition.LdWindow(100,1)));}
    @Test void inclusiveDistanceAndZeroWindowsHaveExplicitCoverage(){FumaLocusDefinition.ReferenceVariant a=v(1,10),b=v(1,20);var edge=new FumaLocusDefinition.Reference("inclusive",List.of(a,b),List.of(p(1,10,20,.4)),0,new FumaLocusDefinition.LdWindow(10,1));assertEquals(FumaLocusDefinition.PairCoverage.STORED,edge.coverage(a.uid(),b.uid()));var none=new FumaLocusDefinition.Reference("empty-pair-window",List.of(a,b),List.of(),0,new FumaLocusDefinition.LdWindow(0,0));assertEquals(FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW,none.coverage(a.uid(),b.uid()));}
    @Test void zeroFloorCountsEveryEligiblePairAcrossChromosomes(){
        Random random=new Random(20261102);for(int run=0;run<100;run++){
            var variants=new ArrayList<FumaLocusDefinition.ReferenceVariant>();for(int chr=1;chr<=3;chr++)for(int i=0;i<10;i++)variants.add(v(chr,10+i*17));int gap=random.nextInt(12);long bp=random.nextInt(200);var pairs=new ArrayList<FumaLocusDefinition.LdPair>();
            for(int i=0;i<variants.size();i++)for(int j=i+1;j<variants.size();j++){var a=variants.get(i);var b=variants.get(j);if(a.chromosome()==b.chromosome()&&b.position()-a.position()<=bp&&j-i<=gap)pairs.add(p(a.chromosome(),a.position(),b.position(),random.nextDouble()));}
            var window=new FumaLocusDefinition.LdWindow(bp,gap);var r=new FumaLocusDefinition.Reference("all-eligible",variants,pairs,0,window);assertEquals(30,r.variantCount());if(!pairs.isEmpty()){pairs.remove(pairs.size()-1);assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Reference("incomplete",variants,pairs,0,window));}
        }
    }
    @Test void longRangeIndependenceIsADeclaredWindowChoice(){
        FumaLocusDefinition.ReferenceVariant a=v(1,10),b=v(1,1000);var input=List.of(new FumaLocusDefinition.Gwas(1,10,"A","G",a.rsId(),1e-9),new FumaLocusDefinition.Gwas(1,1000,"A","G",b.rsId(),2e-9));
        var options=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);
        var full=new FumaLocusDefinition.Reference("long-range-stored",List.of(a,b),List.of(p(1,10,1000,1)),.05);assertEquals(1,FumaLocusDefinition.define(input,full,options).independent().size());
        var local=new FumaLocusDefinition.Reference("local-protocol",List.of(a,b),List.of(),.05,new FumaLocusDefinition.LdWindow(100,10));var result=FumaLocusDefinition.define(input,local,options);assertEquals(2,result.independent().size());assertTrue(result.warnings().stream().anyMatch(s->s.contains("not assessed")&&s.contains("not declared zero")));assertEquals(FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW,local.coverage(a.uid(),b.uid()));
    }
    @Test void invalidWindowAndUnknownIdsFail(){assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.LdWindow(-1,1));assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.LdWindow(1,-1));var r=new FumaLocusDefinition.Reference("known",List.of(v(1,1)),List.of(),0);assertThrows(IllegalArgumentException.class,()->r.coverage("unknown",v(1,1).uid()));}
}
