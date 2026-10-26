/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RankedEnrichmentTest {
    private static RankedEnrichment.Options options(double exponent,int exact,int permutations,boolean ties) {
        return new RankedEnrichment.Options(exponent,1,100,permutations,exact,7,10000000,ties,EnrichmentAnalysis.Fdr.BH);
    }
    @Test void weightedSignedScoresAndConditionalExactNullMatchHandEnumeration() {
        var ranks=List.of(new RankedEnrichment.Rank("A",4),new RankedEnrichment.Rank("B",3),new RankedEnrichment.Rank("C",-1),new RankedEnrichment.Rank("D",-2));
        var result=RankedEnrichment.analyze(ranks,List.of(new GeneSet("top","Top",Set.of("A","B")),new GeneSet("bottom","Bottom",Set.of("C","D"))),options(1,100,100,false));
        assertTrue(result.completeFamily());var top=result.results().get(0);var bottom=result.results().get(1);
        assertEquals(1,top.enrichmentScore(),1e-14);assertEquals(-1,bottom.enrichmentScore(),1e-14);
        assertEquals(.25,top.pValue(),1e-14);assertEquals(.5,bottom.pValue(),1e-14);
        assertEquals(1/((1+.8+2.0/3+.5)/4),top.normalizedScore(),1e-13);assertEquals(-4.0/3,bottom.normalizedScore(),1e-13);
        assertEquals(List.of("A","B"),top.leadingEdge());assertEquals(List.of("C","D"),bottom.leadingEdge());
        assertEquals(.5,top.adjustedP(),1e-14);assertEquals(.5,bottom.adjustedP(),1e-14);assertEquals(6,top.nullStates());assertEquals(4,top.sameSignStates());
    }
    @Test void optimizedWalkAndExactTailsMatchFullGeneScanForEverySmallMembership() {
        var ranks=new ArrayList<RankedEnrichment.Rank>();for(int i=0;i<7;i++)ranks.add(new RankedEnrichment.Rank("g"+i,4.0-i));
        for(double exponent:new double[]{0,1,2}) {
            var sets=new ArrayList<GeneSet>();var masks=new ArrayList<Integer>();
            for(int mask=1;mask<127;mask++){var members=new HashSet<String>();for(int i=0;i<7;i++)if((mask&(1<<i))!=0)members.add("g"+i);sets.add(new GeneSet("S"+mask,"Subset",members));masks.add(mask);}
            var result=RankedEnrichment.analyze(ranks,sets,options(exponent,1000,100,false));
            for(int at=0;at<masks.size();at++) {
                int mask=masks.get(at);double observed=scan(mask,ranks,exponent);var row=result.results().get(at);
                assertEquals(observed,row.enrichmentScore(),1e-13,"mask "+mask+" exponent "+exponent);
                int same=0,tail=0;double total=0;
                for(int alternative=1;alternative<127;alternative++)if(Integer.bitCount(alternative)==Integer.bitCount(mask)) {
                    double value=scan(alternative,ranks,exponent);
                    if(observed>=0?value>=0:value<0){same++;total+=Math.abs(value);if(observed>=0?value>=observed-1e-12:value<=observed+1e-12)tail++;}
                }
                // A singleton zero statistic is undefined under weighted scoring;
                // a matched-size null containing it must not be silently dropped.
                boolean undefined=exponent>0&&Integer.bitCount(mask)==1;
                if(undefined){assertFalse(result.completeFamily());assertTrue(Double.isNaN(row.pValue()));}
                else {assertEquals((double)tail/same,row.pValue(),1e-13);assertEquals(observed/(total/same),row.normalizedScore(),1e-12);}
            }
        }
    }
    private static double scan(int mask,List<RankedEnrichment.Rank> ranking,double exponent) {
        double total=0;for(int i=0;i<ranking.size();i++)if((mask&(1<<i))!=0)total+=exponent==0?1:Math.pow(Math.abs(ranking.get(i).statistic()),exponent);
        if(total==0)return Double.NaN;
        double running=0,positive=0,negative=0;int misses=ranking.size()-Integer.bitCount(mask);
        for(int i=0;i<ranking.size();i++){running+=(mask&(1<<i))!=0?(exponent==0?1:Math.pow(Math.abs(ranking.get(i).statistic()),exponent))/total:-1.0/misses;positive=Math.max(positive,running);negative=Math.min(negative,running);}
        return positive>=-negative-1e-12?positive:negative;
    }
    @Test void monteCarloIsReproducibleNonzeroAndReportsItsConditionalResolution() {
        var ranking=new ArrayList<RankedEnrichment.Rank>();for(int i=0;i<40;i++)ranking.add(new RankedEnrichment.Rank("g"+i,20.5-i));
        var set=new GeneSet("top","Top",Set.of("g0","g1","g2","g3","g4"));var a=RankedEnrichment.analyze(ranking,List.of(set),options(1,0,1000,false));var b=RankedEnrichment.analyze(ranking,List.of(set),options(1,0,1000,false));
        assertEquals(a,b);var r=a.results().get(0);assertEquals("monte_carlo_gene_set",r.nullMethod());assertTrue(r.pValue()>0);assertEquals((r.tailStates()+1.0)/(r.sameSignStates()+1),r.pValue(),0);assertEquals(1.0/(r.sameSignStates()+1),r.pResolution(),0);assertTrue(r.pMonteCarloLower()<=r.pValue());assertTrue(r.pMonteCarloUpper()>=r.pValue());
    }
    @Test void tiesDuplicatesAndWorkLimitsHaveNoSilentSubsetFallback() {
        var ranking=List.of(new RankedEnrichment.Rank("B",1),new RankedEnrichment.Rank("A",1),new RankedEnrichment.Rank("C",-1));var sets=List.of(new GeneSet("S","Set",Set.of("A")));
        assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyze(ranking,sets,options(0,100,10,false)));
        var explicit=RankedEnrichment.analyze(ranking,sets,options(0,100,10,true));assertEquals("A",explicit.ranking().get(0).gene());assertEquals(2,explicit.tiedGenes());
        assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyze(List.of(new RankedEnrichment.Rank("A",2),new RankedEnrichment.Rank("A",1)),sets,options(0,100,10,false)));
        assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyze(ranking,sets,new RankedEnrichment.Options(0,1,10,100,100,1,1,true,EnrichmentAnalysis.Fdr.BH)));
        var bounded=new RankedEnrichment.Options(0,1,10,100,100,1,40,true,EnrichmentAnalysis.Fdr.BH);
        assertTrue(RankedEnrichment.analyze(ranking,sets,bounded).completeFamily());
        var repeatedSize=new ArrayList<GeneSet>();
        for(int i=0;i<20;i++)repeatedSize.add(new GeneSet("S"+i,"Same-size term",Set.of("A")));
        // Sharing a three-state null does not remove sixty term-tail comparisons.
        assertThrows(IllegalArgumentException.class,()->RankedEnrichment.analyze(ranking,repeatedSize,bounded));
    }
    @Test void sizeAndCompleteUniverseTermsRetainIndependentDispositions() {
        var ranks=List.of(new RankedEnrichment.Rank("A",3),new RankedEnrichment.Rank("B",2),new RankedEnrichment.Rank("C",1));
        var result=RankedEnrichment.analyze(ranks,List.of(new GeneSet("all","All",Set.of("A","B","C")),new GeneSet("absent","Absent",Set.of("X"))),options(1,100,10,false));
        assertTrue(result.results().isEmpty());assertTrue(result.completeFamily());assertEquals("covers_complete_ranking_no_complement",result.dispositions().get("all"));assertEquals("below_minimum_ranked_members",result.dispositions().get("absent"));
    }
}
