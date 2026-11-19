/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import org.jlinalg.sequence.SeuratSnn;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

final class SeuratWorkflowTest {
    private static final SeuratSnn.Limits LIMITS=new SeuratSnn.Limits(10000000,1000000);
    @Test void nativeSnnPreservesInclusivePruningDiagonalAndAbsentZeroOverlap() {
        int[][] nn={{0,1},{1,2},{2,3},{3,0}};
        assertEquals(12,SeuratSnn.compute(nn,0,LIMITS,()->false).size());
        assertEquals(12,SeuratSnn.compute(nn,1.0/3,LIMITS,()->false).size());
        var diagonal=SeuratSnn.compute(nn,Math.nextUp(1.0/3),LIMITS,()->false);
        assertEquals(4,diagonal.size());for(var e:diagonal){assertEquals(e.row(),e.column());assertEquals(1,e.weight());}
    }
    @Test void nativeSnnMatchesIndependentSetIntersectionAcrossRanksAndPruning() {
        var rng=new Random(39105);
        for(int n=2;n<=23;n++)for(int k=1;k<=n;k++) {
            int[][] nn=new int[n][k];boolean[][] membership=new boolean[n][n];
            for(int row=0;row<n;row++){
                List<Integer> order=new ArrayList<>();for(int i=0;i<n;i++)order.add(i);Collections.shuffle(order,rng);
                for(int i=0;i<k;i++){nn[row][i]=order.get(i);membership[row][nn[row][i]]=true;}
            }
            for(double prune:new double[]{0,1.0/15,1.0/3,.5,1}) {
                List<SeuratSnn.Edge> expected=new ArrayList<>();
                for(int row=0;row<n;row++)for(int col=0;col<n;col++){
                    int intersection=0,union=0;for(int cell=0;cell<n;cell++){if(membership[row][cell]&&membership[col][cell])intersection++;if(membership[row][cell]||membership[col][cell])union++;}
                    double weight=(double)intersection/union;if(intersection>0&&weight>=prune)expected.add(new SeuratSnn.Edge(row,col,weight));
                }
                assertEquals(expected,SeuratSnn.compute(nn,prune,LIMITS,()->false),"n="+n+" k="+k+" prune="+prune);
            }
        }
    }
    @Test void nativeSnnRejectsAmbiguousOrIncompletePopulationsAndCapacity() {
        assertThrows(IllegalArgumentException.class,()->SeuratSnn.compute(new int[][]{{0,0},{0,1}},0,LIMITS,()->false));
        assertThrows(IllegalArgumentException.class,()->SeuratSnn.compute(new int[][]{{0},{2}},0,LIMITS,()->false));
        assertThrows(IllegalArgumentException.class,()->SeuratSnn.compute(new int[][]{{0},{0,1}},0,LIMITS,()->false));
        assertThrows(IllegalArgumentException.class,()->SeuratSnn.compute(new int[][]{{0},{0}},0,new SeuratSnn.Limits(1,100),()->false));
        assertThrows(IllegalArgumentException.class,()->SeuratSnn.compute(new int[][]{{0},{0}},0,new SeuratSnn.Limits(100,1),()->false));
        assertThrows(CancellationException.class,()->SeuratSnn.compute(new int[][]{{0}},0,LIMITS,()->true));
    }
    private static Map<String,Object> request(){Map<String,Object> r=new LinkedHashMap<>();r.put("schema_version",1);for(String key:List.of("out","counts","features","cells","rscript"))r.put(key,key);r.put("r_libraries",List.of("private-library"));return r;}
    @Test void workflowRejectsIgnoredSolverAndBackendOptions()throws Exception {
        Map<String,Object> r=request();r.put("pca",Map.of("solver","exact","tolerance",.001));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));
        r.put("pca",Map.of("solver","source_default","max_iterations",100));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("pca");
        r.put("neighbors",Map.of("method","rann","trees",50));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("neighbors");
        r.put("clustering",Map.of("algorithm",1,"leiden_backend","igraph"));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));
        r.put("clustering",Map.of("algorithm",4,"starts",10));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));
    }
    @Test void workflowRefusesImplicitRepairAndUnusedScientificFields()throws Exception {
        Map<String,Object> r=request();r.put("scale_factor",10000.5);assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("scale_factor");
        r.put("normalization","SCT-v2");r.put("scale_center",true);assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("scale_center");
        r.put("regressors",List.of(Map.of("name","batch","kind","factor","levels",List.of("A","B"))));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("regressors");
        r.put("umap",Map.of("enabled",false,"neighbors",12));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));r.remove("umap");
        r.put("pca",Map.of("components",4));r.put("neighbors",Map.of("dimensions",List.of(2,1)));assertThrows(IOException.class,()->SeuratWorkflowContracts.validate(r));
    }
    @Test void workflowDistinguishesExplicitLeidenBackendAndOptionalEmbeddings()throws Exception {
        Map<String,Object> r=request();r.put("clustering",Map.of("algorithm",4,"leiden_backend","leidenbase","objective","CPM"));r.put("umap",Map.of("enabled",false));r.put("tsne",Map.of("enabled",false));
        var effective=SeuratWorkflowContracts.validate(r);var c=SeuratDifferentialContracts.object(effective.get("clustering"),"clustering");assertEquals("leidenbase",c.get("leiden_backend"));assertEquals("CPM",c.get("objective"));assertFalse(c.containsKey("starts"));
        assertEquals(effective,SeuratWorkflowContracts.validate(effective));
    }
}
