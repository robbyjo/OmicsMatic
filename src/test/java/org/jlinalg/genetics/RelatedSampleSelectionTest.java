package org.jlinalg.genetics;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jlinalg.genetics.RelatedSampleSelection.Pair;
import org.jlinalg.genetics.RelatedSampleSelection.TiePolicy;
import org.junit.jupiter.api.Test;

class RelatedSampleSelectionTest {
    @Test void sourcePaperGreedyRuleKeepsLowDegreeLeavesAndIsolatedParticipants() {
        var ids=List.of("center","leaf1","leaf2","leaf3","isolated");var pairs=List.of(new Pair("center","leaf1",.25),new Pair("center","leaf2",.25),new Pair("center","leaf3",.25));var result=RelatedSampleSelection.select(ids,new HashSet<>(ids),pairs,.125,TiePolicy.INPUT_ORDER);assertEquals(List.of("isolated","leaf1","leaf2","leaf3"),result.retained());assertEquals("leaf1",result.decisions().get(0).excludedBy());assertEquals(3,result.decisions().get(0).initialDegree());assertEquals(ids,result.decisions().stream().map(RelatedSampleSelection.Decision::sampleId).toList());
    }
    @Test void eligibilityIsAppliedBeforeDegreesRatherThanFilteringAFinishedSelection() {
        var ids=List.of("A","B","C");var pairs=List.of(new Pair("A","B",.3),new Pair("A","C",.3));var all=RelatedSampleSelection.select(ids,new HashSet<>(ids),pairs,.1,TiePolicy.INPUT_ORDER);assertEquals(List.of("B","C"),all.retained());var filtered=RelatedSampleSelection.select(ids,Set.of("A","C"),pairs,.1,TiePolicy.INPUT_ORDER);assertEquals(List.of("A"),filtered.retained());assertEquals("ineligible",filtered.decisions().get(1).status());assertEquals(1,filtered.decisions().get(0).initialDegree());assertEquals(0,filtered.decisions().get(1).initialDegree());assertEquals("A",filtered.decisions().get(2).excludedBy());
    }
    @Test void strictCutoffKeepsEqualPairsAndNamedTiePoliciesAreDeterministic() {
        var ids=List.of("Z","A","E");var pairs=List.of(new Pair("Z","A",.2),new Pair("A","E",.125));assertEquals(List.of("E","Z"),RelatedSampleSelection.select(ids,new HashSet<>(ids),pairs,.125,TiePolicy.INPUT_ORDER).retained());assertEquals(List.of("E","A"),RelatedSampleSelection.select(ids,new HashSet<>(ids),pairs,.125,TiePolicy.LEXICAL_ID).retained());
    }
    @Test void emptyEligibilityAndNoPairsHaveExplicitFullDecisions() {
        var ids=List.of("B","A");var none=RelatedSampleSelection.select(ids,Set.of(),List.of(),0,TiePolicy.INPUT_ORDER);assertTrue(none.retained().isEmpty());assertTrue(none.decisions().stream().allMatch(d->d.status().equals("ineligible")));assertEquals(List.of("A","B"),RelatedSampleSelection.select(ids,new HashSet<>(ids),List.of(),0,TiePolicy.LEXICAL_ID).retained());assertThrows(UnsupportedOperationException.class,()->none.decisions().clear());
    }
    @Test void duplicateUnknownSelfNonfiniteAndAmbiguousSourceIdentitiesFail() {
        var ids=List.of("A","B");for(var pairs:List.of(List.of(new Pair("A","B",.2),new Pair("B","A",.3)),List.of(new Pair("A","A",.3)),List.of(new Pair("A","unknown",.3)),List.of(new Pair("A","B",Double.NaN))))assertThrows(IllegalArgumentException.class,()->RelatedSampleSelection.select(ids,Set.of("A"),pairs,.1,TiePolicy.INPUT_ORDER));assertThrows(IllegalArgumentException.class,()->RelatedSampleSelection.select(List.of("A","A"),Set.of("A"),List.of(),.1,TiePolicy.INPUT_ORDER));assertThrows(IllegalArgumentException.class,()->RelatedSampleSelection.select(ids,Set.of("unknown"),List.of(),.1,TiePolicy.INPUT_ORDER));assertThrows(IllegalArgumentException.class,()->RelatedSampleSelection.select(ids,Set.of("A"),List.of(),Double.NaN,TiePolicy.INPUT_ORDER));
    }
    @Test void randomizedGraphsHaveNoRetainedEdgeAndEveryExcludedVertexHasARetainedBlocker() {
        var random=new Random(194);for(int trial=0;trial<250;trial++){int n=1+random.nextInt(40);var ids=new ArrayList<String>();for(int i=0;i<n;i++)ids.add("id"+i);var eligible=new HashSet<String>();for(String id:ids)if(random.nextBoolean())eligible.add(id);var pairs=new ArrayList<Pair>();for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(random.nextInt(4)==0)pairs.add(new Pair(ids.get(i),ids.get(j),random.nextBoolean()?.2:.05));var result=RelatedSampleSelection.select(ids,eligible,pairs,.1,TiePolicy.INPUT_ORDER);var kept=new HashSet<>(result.retained());for(Pair pair:pairs)if(pair.relatedness()>.1)assertFalse(kept.contains(pair.first())&&kept.contains(pair.second()));for(var d:result.decisions())if(d.status().equals("excluded-related")){assertTrue(kept.contains(d.excludedBy()));assertTrue(pairs.stream().anyMatch(p->p.relatedness()>.1&&(p.first().equals(d.sampleId())&&p.second().equals(d.excludedBy())||p.second().equals(d.sampleId())&&p.first().equals(d.excludedBy()))));}assertEquals(eligible.size(),result.retained().size()+result.decisions().stream().filter(d->d.status().equals("excluded-related")).count());}
    }
}
