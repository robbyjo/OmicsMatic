/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
class ModelNullEnrichmentTest {
    private static List<RankedEnrichment.Rank> ranks(double... v){var r=new ArrayList<RankedEnrichment.Rank>();for(int i=0;i<v.length;i++)r.add(new RankedEnrichment.Rank("g"+i,v[i]));return r;}
    private static RankedEnrichment.Options options(boolean ties,long work){return new RankedEnrichment.Options(1,1,20,10,100,1,work,ties,EnrichmentAnalysis.Fdr.BY);}
    private static List<GeneSet> sets(){return List.of(new GeneSet("A","First",Set.of("g0","g1")),new GeneSet("B","Second",Set.of("g2","g3")));}
    @Test void termSpecificJointRankNullTailsAndLeadingEdgesMatchHandValues(){
        var observed=ranks(4,3,-1,-2);var draws=List.of(observed,ranks(-4,-3,1,2),ranks(1,4,3,-2),ranks(4,-2,3,1));
        var a=RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),4,true,"fixture_exhaustive",draws::get);assertTrue(a.completeFamily());var top=a.results().get(0);var bottom=a.results().get(1);
        assertEquals(1,top.enrichmentScore(),1e-14);assertEquals(-1,bottom.enrichmentScore(),1e-14);assertEquals(List.of("g0","g1"),top.leadingEdge());assertEquals(List.of("g2","g3"),bottom.leadingEdge());
        assertEquals(3,top.sameSignStates());assertEquals(1,top.tailStates());assertEquals(1.0/3,top.pValue(),1e-14);assertEquals(.75,top.adjustedP(),1e-14);assertEquals(1/( (1+.8+2.0/3)/3),top.normalizedScore(),1e-13);
        assertEquals(2,bottom.sameSignStates());assertEquals(1,bottom.tailStates());assertEquals(.5,bottom.pValue(),0);
        var mc=RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),4,false,"fixture_mc",draws::get);assertEquals(.5,mc.results().get(0).pValue(),0);assertEquals(2.0/3,mc.results().get(1).pValue(),1e-14);
    }
    @Test void completeGeneIdentitiesAndEveryDrawAreMandatory(){
        var observed=ranks(4,3,-1,-2);assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),2,false,"bad",i->i==0?observed:ranks(1,2,3)));
        var wrong=List.of(new RankedEnrichment.Rank("g0",1),new RankedEnrichment.Rank("g1",2),new RankedEnrichment.Rank("g2",3),new RankedEnrichment.Rank("other",4));assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),1,false,"bad",i->wrong));
        assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),1,false,"bad",i->List.of(observed.get(0),observed.get(0),observed.get(2),observed.get(3))));
    }
    @Test void nullTiesAndBudgetNeedExplicitProtocolAndHaveNoPartialFallback(){
        var observed=ranks(4,3,-1,-2);assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyzeRankings(observed,sets(),options(false,100000),1,false,"tie",i->ranks(1,1,-1,-2)));assertTrue(RankedEnrichment.analyzeRankings(observed,sets(),options(true,100000),1,false,"tie",i->ranks(1,1,-1,-2)).completeFamily());
        int[] calls={0};assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyzeRankings(observed,sets(),options(false,100),100,false,"bounded",i->{calls[0]++;return observed;}));assertEquals(0,calls[0]);
    }
    @Test void undefinedDrawSuppressesWholeCorrectionFamily(){var result=RankedEnrichment.analyzeRankings(ranks(4,3,-1,-2),sets(),options(true,100000),2,false,"undefined",i->i==0?ranks(4,3,-1,-2):ranks(0,0,0,0));assertFalse(result.completeFamily());assertTrue(result.results().stream().allMatch(r->Double.isNaN(r.adjustedP())));}
}
