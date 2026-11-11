/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BootstrapSupportTest {
    private static final Set<String> TAXA=Set.of("A","B","C","D");
    private static final String AB="((A:1,B:1):1,(C:1,D:1):1);";
    private static final String AC="((A:1,C:1):1,(B:1,D:1):1);";
    private static BootstrapSupport.Counts counts() throws IOException {
        return BootstrapSupport.count(new StringReader(AB+AB+AC),TAXA,3,true,BootstrapSupport.Limits.defaults());
    }
    @Test void countsEveryReplicateAndCountsDuplicateRootRepresentationsOnlyOnce() throws Exception {
        var count=counts();assertEquals(3,count.replicates());
        assertEquals(Map.of(Set.of("A","B"),2L,Set.of("A","C"),1L),count.splits());
        assertEquals(0,count.occurrences(Set.of("A","D")));
        assertThrows(UnsupportedOperationException.class,()->count.splits().clear());
        assertThrows(UnsupportedOperationException.class,()->count.splits().keySet().iterator().next().clear());
        assertThrows(UnsupportedOperationException.class,()->count.taxa().clear());
    }
    @Test void comparesEveryEdgeAndRetainsIndependentSupportColumnsAndMismatches() throws Exception {
        var tree=NewickTree.parse("((A:1,B:1)91.2/67:1,(C:1,D:1)81.3/66.7:1);");
        var result=BootstrapSupport.compare(tree,counts(),2,1,100);
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(BootstrapSupport.Comparison::agreesAtPrintedPrecision));
        assertEquals(Set.of("91.2","81.3"),Set.of(result.get(0).reportedColumns().get(0),result.get(1).reportedColumns().get(0)));
        assertEquals(200.0/3,result.get(0).percentage());assertEquals(2,result.get(0).occurrences());
        var wrong=BootstrapSupport.compare(NewickTree.parse("((A,B)66,(C,D)68);"),counts(),1,0,100);
        assertTrue(wrong.stream().noneMatch(BootstrapSupport.Comparison::agreesAtPrintedPrecision));
    }
    @Test void retainsBoundaryRoundingUncertaintyRatherThanAddingAnArbitraryTolerance() throws Exception {
        var count=new BootstrapSupport.Counts(TAXA,200,Map.of(Set.of("A","B"),133L));
        var both=BootstrapSupport.compare(NewickTree.parse("((A,B)66,(C,D)67);"),count,1,0,100);
        assertTrue(both.stream().allMatch(BootstrapSupport.Comparison::agreesAtPrintedPrecision));
        assertEquals(66.5,both.get(0).percentage());
        var precise=BootstrapSupport.compare(NewickTree.parse("((A,B)66.49,(C,D)66.51);"),count,1,0,100);
        assertTrue(precise.stream().noneMatch(BootstrapSupport.Comparison::agreesAtPrintedPrecision));
    }
    @Test void laterBadTreesCannotCertifyAValidPrefix() {
        for(String bad:List.of(AB+"((A,B),(C,D)",AB+"((A,B),(C,E));",AB+"(A,A,C,D);",AB+"trailing"))
            assertThrows(IOException.class,()->BootstrapSupport.count(new StringReader(bad),TAXA,2,false,BootstrapSupport.Limits.defaults()));
    }
    @Test void exactCountAndSuccessfulReadAndCloseAreRequired() throws Exception {
        for(int expected:List.of(1,3))assertThrows(IOException.class,()->BootstrapSupport.count(new StringReader(AB+AC),TAXA,expected,true,BootstrapSupport.Limits.defaults()));
        var closed=new boolean[1];
        var reader=new StringReader(AB) {
            @Override public void close() {closed[0]=true;super.close();throw new IllegalStateException("controlled close failure");}
        };
        assertThrows(IllegalStateException.class,()->BootstrapSupport.count(reader,TAXA,1,true,BootstrapSupport.Limits.defaults()));assertTrue(closed[0]);
    }
    @Test void readFailuresCloseTheOwnedInputWithoutReturningCounts() {
        var closed=new boolean[1];
        var reader=new java.io.Reader() {
            @Override public int read(char[] chars,int offset,int length)throws IOException {throw new IOException("controlled read failure");}
            @Override public void close(){closed[0]=true;}
        };
        assertThrows(IOException.class,()->BootstrapSupport.count(reader,TAXA,1,true,BootstrapSupport.Limits.defaults()));assertTrue(closed[0]);
    }
    @Test void declaredTreeAndDistinctSplitCapacitiesCannotReturnPrefixes() {
        var basic=BootstrapSupport.Limits.defaults();
        for(var limits:List.of(new BootstrapSupport.Limits(basic.collection(),1,100),
                new BootstrapSupport.Limits(basic.collection(),100,2),
                new BootstrapSupport.Limits(new NewickReader.Limits(NewickTree.Limits.defaults(),10,10),100,100)))
            assertThrows(IOException.class,()->BootstrapSupport.count(new StringReader(AB+AC),TAXA,2,true,limits));
        assertThrows(IllegalArgumentException.class,()->BootstrapSupport.count(new StringReader(AB),TAXA,0,true,basic));
    }
    @Test void branchLengthsAreRequiredOnlyWhenExplicitlyDeclared() throws Exception {
        String input="((A,B),(C,D));";
        assertEquals(1,BootstrapSupport.count(new StringReader(input),TAXA,1,false,BootstrapSupport.Limits.defaults()).replicates());
        assertThrows(IOException.class,()->BootstrapSupport.count(new StringReader(input),TAXA,1,true,BootstrapSupport.Limits.defaults()));
    }
    @Test void missingExtraAndNonfiniteOrOutOfRangeSupportLabelsAreRefused() throws Exception {
        var counts=counts();
        for(String label:List.of("", "NaN", "Infinity", "101", "-1", "50/60", "1e999", "50/", "word")) {
            var tree=NewickTree.parse("((A,B)"+label+",(C,D)67);");
            assertThrows(IllegalArgumentException.class,()->BootstrapSupport.compare(tree,counts,1,0,100),label);
        }
        assertThrows(IllegalArgumentException.class,()->BootstrapSupport.compare(NewickTree.parse("((A,B)67,(C,E)67);"),counts,1,0,100));
    }
    @Test void constructedCountsRejectNoncanonicalAndImpossibleSplitTables() {
        for(var splits:List.of(Map.of(Set.of("C","D"),1L),Map.of(Set.of("A","B"),0L),Map.of(Set.of("A","B"),4L),
                Map.of(Set.of("A","E"),1L),Map.of(Set.of("A"),1L)))
            assertThrows(IllegalArgumentException.class,()->new BootstrapSupport.Counts(TAXA,3,splits));
    }
}
