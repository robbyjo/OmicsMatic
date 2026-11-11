/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.IOException;
import java.io.StringReader;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaltAssignmentTest {
    private static MaltTaxonomy tree(String text)throws IOException {
        return MaltTaxonomy.read(new StringReader(text),new MaltTaxonomy.Limits(10000,100,20));
    }
    @Test void malformedTreesAndInsufficientCapacitiesRefuseCompleteResults()throws Exception {
        for(String invalid:List.of("", "();", "(2,2)1;", "(2)1", "(2)1;3;", "(2:0.5)1;", "('2')1;", "(2)1 2;", "(2,)1;", "(2147483648)1;", "((2)3;"))
            assertThrows(IOException.class,()->tree(invalid),invalid);
        assertThrows(IOException.class,()->MaltTaxonomy.read(new StringReader("(2)1;"),new MaltTaxonomy.Limits(4,100,20)));
        assertThrows(IOException.class,()->MaltTaxonomy.read(new StringReader("(2)1;"),new MaltTaxonomy.Limits(100,4,20)));
        assertThrows(IOException.class,()->MaltTaxonomy.read(new StringReader("((2)3)1;"),new MaltTaxonomy.Limits(100,100,1)));
        var complete=tree(" (2,3)1; \n");assertEquals(6,complete.size());assertEquals(1,complete.root());
        int[] ids=complete.ids();ids[0]=999;assertEquals(1,complete.root());assertFalse(complete.contains(999));
    }
    @Test void incompleteMatchPopulationsCannotProduceAnAssignment()throws Exception {
        var taxonomy=tree("(2,3)1;");var matches=List.of(new MaltAssignment.Match(2,100,0,99,false));
        var filter=new MaltAssignment.Filter(50,1,1,0);
        assertThrows(IllegalArgumentException.class,()->MaltAssignment.assign(taxonomy,0,matches,filter,Set.of(),80,true));
        assertThrows(IllegalArgumentException.class,()->MaltAssignment.assign(taxonomy,-1,List.of(),filter,Set.of(),80,true));
        BitSet outside=new BitSet();outside.set(1);
        assertThrows(IllegalArgumentException.class,()->MaltAssignment.assignActive(taxonomy,1,matches,outside,Set.of(),80,true));
        assertEquals(-1,MaltAssignment.assign(taxonomy,0,List.of(),filter,Set.of(),80,true).taxon());
        assertEquals(-2,MaltAssignment.assign(taxonomy,1,List.of(),filter,Set.of(),80,true).taxon());
    }
    @Test void disabledFilteringAndExplicitOriginalLcaFallbackRemainDistinctOperations()throws Exception {
        var taxonomy=tree("(2,3)1;");var matches=List.of(new MaltAssignment.Match(2,100,0,99,false));
        var result=MaltAssignment.assign(taxonomy,1,matches,new MaltAssignment.Filter(50,1,1,0),Set.of(2),80,true);
        assertTrue(result.active().isEmpty());assertEquals(-2,result.taxon());
        BitSet selected=new BitSet();selected.set(0);
        assertEquals(2,MaltAssignment.assignActive(taxonomy,1,matches,selected,Set.of(2),80,true));
        var saved=new MaltAssignment.Result(2,selected);selected.clear();assertEquals(1,saved.active().cardinality());
        BitSet copy=saved.active();copy.clear();assertEquals(1,saved.active().cardinality());
        assertEquals(999999,MaltAssignment.naiveLca(taxonomy,List.of(999999),Set.of(999999),true));
    }
}
