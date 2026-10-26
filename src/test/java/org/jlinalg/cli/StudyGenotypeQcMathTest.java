/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyGenotypeSampleQcPolicy;
import org.junit.jupiter.api.Test;

class StudyGenotypeQcMathTest {
    private DelimitedData resource(String name)throws Exception{return DelimitedData.readResult(Path.of(getClass().getResource("/genotype-sample-qc/"+name).toURI()));}
    private record Panel(List<String> ids,List<byte[]> calls,List<String> markers,int[] anchors) { }
    private Panel panel()throws Exception {
        var source=resource("hard-calls.tsv");var rows=new HashMap<String,String[]>();for(var row:source.rows())rows.put(row[0],row);var ids=source.header().subList(5,source.header().size());var calls=new ArrayList<byte[]>();var markers=new ArrayList<String>();
        for(var row:resource("expected-panel.tsv").rows())if(row[1].equals("retained")){String[] original=rows.get(row[0]);byte[] values=new byte[ids.size()];for(int i=0;i<values.length;i++)values[i]=original[i+5].equals("NA")?-1:Byte.parseByte(original[i+5]);calls.add(values);markers.add(row[0]);}
        var anchorList=new ArrayList<Integer>();var expected=resource("expected-pcs.tsv");for(var row:expected.rows())if(row[expected.column("pca_basis")].equals("true"))anchorList.add(ids.indexOf(row[0]));
        return new Panel(ids,calls,markers,anchorList.stream().mapToInt(Integer::intValue).toArray());
    }
    @Test void bitPackedMissingAwareCountsAndBothEstimatorsMatchIndependentNumpyForEveryPair()throws Exception {
        var p=panel();var pairs=StudyGenotypeQcMath.pairs(p.calls(),p.ids().size());var expected=resource("expected-pairs.tsv");assertEquals(2016,pairs.size());
        for(int i=0;i<pairs.size();i++){var actual=pairs.get(i);var row=expected.rows().get(i);assertEquals(row[0],p.ids().get(actual.first()));assertEquals(row[1],p.ids().get(actual.second()));int[] counts={actual.shared(),actual.hetFirst(),actual.hetSecond(),actual.bothHet(),actual.oppositeHom(),actual.identical()};for(int j=0;j<counts.length;j++)assertEquals(Integer.parseInt(row[j+2]),counts[j]);assertEquals(Double.parseDouble(row[expected.column("king_equation_9")]),actual.within(),1e-14);assertEquals(Double.parseDouble(row[expected.column("king_between_equation_11")]),actual.between(),1e-14);}
        var duplicate=pairs.stream().filter(x->x.first()==0&&x.second()==1).findFirst().orElseThrow();assertEquals(.5,duplicate.between(),1e-15);
    }
    @Test void projectedGenotypePcsAndWeightsMatchIndependentDenseNumpyEigenanalysis()throws Exception {
        var p=panel();var result=StudyGenotypeQcMath.pca(p.calls(),p.ids().size(),p.anchors(),2);var expected=resource("expected-pcs.tsv");
        for(int i=0;i<p.ids().size();i++)for(int k=0;k<2;k++)assertEquals(Double.parseDouble(expected.rows().get(i)[k+1]),result.scores()[i][k],2e-8,p.ids().get(i)+":"+k);
        var weights=resource("expected-weights.tsv");assertEquals(weights.rows().size(),result.markers().length);for(int j=0;j<result.markers().length;j++){var row=weights.rows().get(j);assertEquals(row[0],p.markers().get(result.markers()[j]));assertEquals(Double.parseDouble(row[1]),result.means()[j],1e-14);assertEquals(Double.parseDouble(row[2]),result.scales()[j],1e-14);for(int k=0;k<2;k++)assertEquals(Double.parseDouble(row[k+3]),result.weights()[j][k],2e-8);}
        var info=StudyPublicEvidence.map(SimpleJson.parse(java.nio.file.Files.readString(Path.of(getClass().getResource("/genotype-sample-qc/software.json").toURI()))));var eigen=StudyPublicEvidence.list(info.get("eigenvalues"));for(int k=0;k<2;k++)assertEquals(((Number)eigen.get(k)).doubleValue(),result.eigenvalues()[k],2e-8);assertTrue(result.maxResidual()<1e-8);
    }
    @Test void sourceSampleReorderingPreservesGreedyBasisAndProjectedScores()throws Exception {
        var p=panel();int n=p.ids().size();var reversedIds=new ArrayList<>(p.ids());java.util.Collections.reverse(reversedIds);var reversed=new ArrayList<byte[]>();for(byte[] row:p.calls()){byte[] values=new byte[n];for(int i=0;i<n;i++)values[i]=row[n-1-i];reversed.add(values);}
        double[] callRate=new double[n];for(int i=0;i<n;i++){int called=0;for(byte[] row:reversed)if(row[i]>=0)called++;callRate[i]=called/(double)reversed.size();}
        int[] anchors=StudyGenotypeQcMath.unrelated(StudyGenotypeQcMath.pairs(reversed,n),reversedIds,callRate,Math.pow(2,-3.5));assertEquals(Arrays.stream(p.anchors()).mapToObj(p.ids()::get).sorted().toList(),Arrays.stream(anchors).mapToObj(reversedIds::get).sorted().toList());
        var a=StudyGenotypeQcMath.pca(p.calls(),n,p.anchors(),2);var b=StudyGenotypeQcMath.pca(reversed,n,anchors,2);for(int i=0;i<n;i++)for(int k=0;k<2;k++)assertEquals(a.scores()[i][k],b.scores()[n-1-i][k],2e-8);
    }
    @Test void ldUsesOnlySharedObservedCallsAndDoesNotInventCorrelationForConstantInputs() {
        assertEquals(1,StudyGenotypeQcMath.rSquared(new byte[]{0,1,2,-1,0},new byte[]{2,1,0,1,-1},3),1e-15);
        assertTrue(Double.isNaN(StudyGenotypeQcMath.rSquared(new byte[]{0,1,-1},new byte[]{2,1,0},3)));
        assertTrue(Double.isNaN(StudyGenotypeQcMath.rSquared(new byte[]{1,1,1},new byte[]{0,1,2},3)));
    }
    @Test void zeroPairHeterozygosityRemainsUnavailableForBetweenFamilyKinship() {
        var pair=StudyGenotypeQcMath.pairs(List.of(new byte[]{0,0},new byte[]{2,2},new byte[]{0,1}),2).get(0);assertTrue(Double.isNaN(pair.between()));assertEquals(3,pair.shared());assertEquals(0,pair.hetFirst());assertThrows(IllegalArgumentException.class,()->StudyGenotypeQcMath.pairs(List.of(new byte[]{3,0}),2));
    }
    @Test void rankDeficiencyAndTiedComponentBoundariesAreHeld() {
        var calls=List.of(new byte[]{0,0,2,2},new byte[]{0,2,0,2});assertThrows(IllegalArgumentException.class,()->StudyGenotypeQcMath.pca(calls,4,new int[]{0,1,2,3},1));assertDoesNotThrow(()->StudyGenotypeQcMath.pca(calls,4,new int[]{0,1,2,3},2));assertThrows(IllegalArgumentException.class,()->StudyGenotypeQcMath.pca(List.of(new byte[]{0,0,0}),3,new int[]{0,1,2},1));
    }
    @Test void policyRequiresExplicitBuildRationaleAndSupportedBounds() {
        assertEquals(false,StudyGenotypeSampleQcPolicy.resolve(Map.of()).get("enabled"));assertThrows(IllegalArgumentException.class,()->StudyGenotypeSampleQcPolicy.resolve(Map.of("enabled",true,"rationale","test")));assertThrows(IllegalArgumentException.class,()->StudyGenotypeSampleQcPolicy.resolve(Map.of("genome_build","GRCh38")));
        var policy=StudyGenotypeSampleQcPolicy.resolve(Map.of("genome_build","GRCh38","rationale","Synthetic scientific scope","include_pcs",true,"pca_components",2));assertEquals(List.of("GPC1","GPC2"),StudyGenotypeSampleQcPolicy.pcCovariates(policy));assertThrows(IllegalArgumentException.class,()->StudyGenotypeSampleQcPolicy.resolve(Map.of("enabled",false,"pca_components",0,"include_pcs",true)));
    }
}
