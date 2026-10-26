/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.network;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.jlinalg.network.NetworkLabelPermutation.Edge;
import org.junit.jupiter.api.Test;

class NetworkLabelPermutationTest {
    private static final List<Edge> TRIANGLE=List.of(new Edge(0,1,1),new Edge(0,2,1),new Edge(1,2,1));
    private static final int[][] ALL={{0,1,2,3,4}};
    @Test void exactTailAndEntireHistogramMatchCombinatorialTriangleReference(){
        var plan=NetworkLabelPermutation.plan(5,3,3,ALL,"auto",100000,999);
        assertEquals("exact",plan.method());assertEquals(120,plan.states());
        var result=NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,ALL,true,plan,17);
        assertEquals(Map.of(0,36L,1,72L,3,12L),result.histogram());assertEquals(3,result.observed());assertEquals(12,result.extreme());assertEquals(.1,result.pValue(),1e-15);assertEquals(1.0/120,result.resolution(),1e-15);assertEquals(result.pValue(),result.tailLower95());
    }
    @Test void signAndUnsignedStatisticsHaveDistinctScientificMeaning(){
        int[][] groups={{0,1,2,3}};var pattern=List.of(new Edge(0,1,1));var target=List.of(new Edge(0,1,-1));var p=NetworkLabelPermutation.plan(4,1,1,groups,"exact",24,1);
        var signed=NetworkLabelPermutation.test(4,pattern,target,groups,true,p,0);var unsigned=NetworkLabelPermutation.test(4,pattern,target,groups,false,p,0);
        assertEquals(0,signed.observed());assertEquals(1,signed.pValue());assertEquals(1,unsigned.observed());assertEquals(1.0/6,unsigned.pValue(),1e-15);
    }
    @Test void strataRetainTheConditioningUniverseAndCanRemoveAllDiscriminatingPower(){
        int[][] groups={{0,1,2},{3,4}};var plan=NetworkLabelPermutation.plan(5,3,3,groups,"exact",12,1);
        var result=NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,groups,true,plan,0);
        assertEquals(12,result.states());assertEquals(Map.of(3,12L),result.histogram());assertEquals(1,result.pValue());
    }
    @Test void sampledTailsAreSeededPositiveAndRetainBinomialPrecision(){
        var plan=NetworkLabelPermutation.plan(5,3,3,ALL,"monte_carlo",1,9999);
        var a=NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,ALL,true,plan,17);var b=NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,ALL,true,plan,17);
        assertEquals(a,b);assertEquals((a.extreme()+1.0)/10000,a.pValue());assertTrue(a.pValue()>0);assertTrue(a.tailLower95()<.1&&a.tailUpper95()>.1);assertEquals(9999,a.histogram().values().stream().mapToLong(Long::longValue).sum());
        assertNotEquals(a.histogram(),NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,ALL,true,plan,99).histogram());
    }
    @Test void exactTestsAreSuperUniformAcrossAllEquiprobableTargetTriangulations(){
        int rejectedAtPointOne=0,rejectedAtPointSeven=0;
        for(int a=0;a<5;a++)for(int b=a+1;b<5;b++)for(int c=b+1;c<5;c++){
            var target=List.of(new Edge(a,b,1),new Edge(a,c,1),new Edge(b,c,1));var plan=NetworkLabelPermutation.plan(5,3,3,ALL,"exact",120,1);var test=NetworkLabelPermutation.test(5,TRIANGLE,target,ALL,true,plan,17);
            if(test.pValue()<=.10000000001)rejectedAtPointOne++;if(test.pValue()<=.70000000001)rejectedAtPointSeven++;
        }
        assertEquals(1,rejectedAtPointOne);assertEquals(7,rejectedAtPointSeven);
    }
    @Test void exactBudgetsCannotBeSilentlyReplacedWithAPrefixOrSampledTail(){
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.plan(5,3,3,ALL,"exact",119,100));
        assertEquals("monte_carlo",NetworkLabelPermutation.plan(5,3,3,ALL,"auto",119,100).method());
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.test(5,TRIANGLE,TRIANGLE,ALL,true,new NetworkLabelPermutation.Plan("exact",119,true,1),17));
    }
    @Test void invalidPartitionsAndAmbiguousEdgesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.plan(5,3,3,new int[][]{{0,1,2,3}},"auto",100,100));
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.plan(5,3,3,new int[][]{{0,1,2},{2,3,4}},"auto",100,100));
        var plan=NetworkLabelPermutation.plan(5,3,3,ALL,"auto",120,100);
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.test(5,TRIANGLE,List.of(new Edge(0,1,1),new Edge(1,0,1)),ALL,true,plan,17));
        assertThrows(IllegalArgumentException.class,()->NetworkLabelPermutation.test(5,List.of(new Edge(0,0,1)),TRIANGLE,ALL,true,plan,17));
    }
}
