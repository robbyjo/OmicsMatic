/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.jlinalg.genetics.GeneticPrincipalComponents.Limits;
import org.jlinalg.genetics.GeneticPrincipalComponents.Marker;
import org.jlinalg.genetics.GeneticPrincipalComponents.Missingness;
import org.jlinalg.genetics.GeneticPrincipalComponents.Options;
import org.jlinalg.genetics.GeneticPrincipalComponents.SampleId;

class GeneticPrincipalComponentsTest {
    private static final double N = Double.NaN;
    private static final Limits LIMITS = Limits.defaults();
    private static final double[][] TOY = {{0,1,2,N},{0,2,1,0},{2,0,1,2},{1,0,2,1}};
    private static final List<Marker> MARKERS = IntStream.range(0,4).mapToObj(i -> new Marker("v"+i,"A","G")).toList();
    private static final List<SampleId> SAMPLES = List.of(new SampleId("a_b","c"),new SampleId("a","b_c"),new SampleId("家族","人_α"),new SampleId("a","c"));
    private static final int[] BASIS = {0,1,2,3};

    @Test void originalPlink1AllPairwise() throws IOException { original("plink1-pca-all-pairwise", false, Missingness.PAIRWISE_COMPLETE); }
    @Test void originalPlink1FounderPairwise() throws IOException { original("plink1-pca-founders-pairwise", true, Missingness.PAIRWISE_COMPLETE); }
    @Test void originalPlink2AllPairwise() throws IOException { original("plink2-pca-all-pairwise", false, Missingness.PAIRWISE_COMPLETE); }
    @Test void originalPlink2FounderPairwise() throws IOException { original("plink2-pca-founders-pairwise", true, Missingness.PAIRWISE_COMPLETE); }
    @Test void originalPlink2AllMean() throws IOException { original("plink2-pca-all-mean", false, Missingness.MEAN_IMPUTE); }
    @Test void originalPlink2FounderMean() throws IOException { original("plink2-pca-founders-mean", true, Missingness.MEAN_IMPUTE); }

    private static void original(String name, boolean founders, Missingness mode) throws IOException {
        var samples = new ArrayList<SampleId>(); var references = new ArrayList<Integer>();
        List<String> sampleRows = lines("samples.tsv");
        for (int i = 1; i < sampleRows.size(); i++) {
            String[] row = sampleRows.get(i).split("\t"); samples.add(new SampleId(row[0],row[1]));
            if (!founders || row[2].equals("1")) references.add(i-1);
        }
        var markers = new ArrayList<Marker>();
        for (String line : lines("markers.tsv").subList(1,88)) {
            String[] row = line.split("\t"); markers.add(new Marker(row[0],row[1],row[2]));
        }
        double[][] g = lines("dosages.tsv").stream().map(line -> Arrays.stream(line.split("\t"))
            .mapToDouble(s -> s.equals("NA") ? N : Double.parseDouble(s)).toArray()).toArray(double[][]::new);
        int[] basis = IntStream.range(0,samples.size()).toArray();
        var af = GeneticPrincipalComponents.estimateFrequencies(g, references.stream().mapToInt(Integer::intValue).toArray(), LIMITS);
        assertEquals(founders ? 123 : 125, af.referenceSamples().length);
        var model = GeneticPrincipalComponents.fit(markers,samples,g,basis,af.values(),Options.defaults(5,mode));
        assertEquals(samples,model.basisSamples()); assertEquals(markers,model.markers());
        double[] matrix = model.relationshipMatrix(), spectrum = model.spectrum(); int n = samples.size();
        List<String> matrixRows = lines(name+".rel"); assertEquals(n,matrixRows.size());
        for (int i=0;i<n;i++) {
            String[] fields=matrixRows.get(i).trim().split("\\s+"); assertEquals(n,fields.length);
            for(int j=0;j<n;j++) assertPrinted(fields[j],matrix[i*n+j],1e-11);
        }
        List<String> eigenvalues = lines(name+".eigenval");
        for (int c=0;c<5;c++) assertPrinted(eigenvalues.get(c),spectrum[c],1e-10);
        double[][] actual = model.fittedCoordinates(); List<String> rows = lines(name+".eigenvec");
        for (int c=0;c<5;c++) {
            double dot=0,errorSquared=0,differenceSquared=0;
            for(int i=0;i<n;i++) {
                String[] fields=rows.get(i+1).trim().split("\\s+");
                assertEquals(samples.get(i),new SampleId(fields[0],fields[1]));
                double original=Double.parseDouble(fields[c+2]);dot+=original*actual[i][c];
                errorSquared+=Math.pow(quantum(original)/2,2);
            }
            double sign=dot<0?-1:1;
            for(int i=0;i<n;i++) {
                double original=Double.parseDouble(rows.get(i+1).trim().split("\\s+")[c+2]);
                differenceSquared+=Math.pow(actual[i][c]-sign*original,2);
            }
            // Original vectors print six significant digits. All unrounded native
            // eigenpairs must fit within that vector's accumulated print error.
            assertTrue(Math.sqrt(differenceSquared)<=Math.sqrt(errorSquared)+1e-9,name+" PC"+(c+1));
        }
        assertTrue(model.maximumEigenResidual()<1e-10); assertTrue(model.maximumOrthogonalityError()<1e-10);
        var projected=model.project(markers,samples,g,LIMITS); double[][] coordinates=projected.coordinates();
        double maximumDifference=0;
        for(int i=0;i<n;i++) for(int c=0;c<5;c++) maximumDifference=Math.max(maximumDifference,Math.abs(actual[i][c]-coordinates[i][c]));
        if(mode==Missingness.MEAN_IMPUTE) {
            assertTrue(spectrum[n-1]>-1e-12);assertTrue(maximumDifference<1e-10);
        } else {
            assertTrue(spectrum[n-1]<-.004);assertTrue(maximumDifference>.0019 && maximumDifference<.0022);
        }
    }

    @Test void frequencyPopulationAndBasisAreIndependentExplicitSelections() {
        var af=GeneticPrincipalComponents.estimateFrequencies(TOY,new int[]{0,2},LIMITS);
        assertArrayEquals(new double[]{.5,.25,.75,.75},af.values());
        assertArrayEquals(new int[]{2,2,2,2},af.observedSamples());
        var model=GeneticPrincipalComponents.fit(MARKERS,SAMPLES,TOY,new int[]{3,1,2},af.values(),Options.defaults(1,Missingness.MEAN_IMPUTE));
        assertEquals(List.of(SAMPLES.get(3),SAMPLES.get(1),SAMPLES.get(2)),model.basisSamples());
        assertArrayEquals(af.values(),model.frequencies());
    }

    @Test void alleleReversalPreservesRelationshipsAndChangesWeightOrientation() {
        var model=toy();double[][] reversed=Arrays.stream(TOY).map(row->Arrays.stream(row).map(x->2-x).toArray()).toArray(double[][]::new);
        var flippedMarkers=MARKERS.stream().map(v->new Marker(v.id(),v.otherAllele(),v.countedAllele())).toList();
        double[] p=Arrays.stream(model.frequencies()).map(x->1-x).toArray();
        var flipped=GeneticPrincipalComponents.fit(flippedMarkers,SAMPLES,reversed,BASIS,p,Options.defaults(1,Missingness.MEAN_IMPUTE));
        assertArrayEquals(model.relationshipMatrix(),flipped.relationshipMatrix(),1e-13);
        for(int i=0;i<4;i++)assertEquals(-model.projectionWeights()[i][0],flipped.projectionWeights()[i][0],1e-12);
    }

    @Test void projectionsFlagMissingTargetsAndKeepIdentities() {
        var model=toy();double[][] targets={{0,N},{1,N},{N,N},{2,N}};
        var result=model.project(MARKERS,SAMPLES.subList(0,2),targets,LIMITS);
        assertEquals(List.of(GeneticPrincipalComponents.ProjectionStatus.PARTIALLY_IMPUTED,
            GeneticPrincipalComponents.ProjectionStatus.NO_OBSERVED_DOSAGES),result.statuses());
        assertArrayEquals(new int[]{3,0},result.observedVariants());assertTrue(Double.isNaN(result.coordinates()[1][0]));
        double expected=0;double[] p=model.frequencies();double[][] w=model.projectionWeights();
        for(int v=0;v<4;v++)if(!Double.isNaN(targets[v][0]))expected+=(targets[v][0]-2*p[v])/Math.sqrt(2*p[v]*(1-p[v]))*w[v][0];
        assertEquals(expected,result.coordinates()[0][0],1e-14);
        assertEquals(SAMPLES.subList(0,2),result.samples());
    }

    @Test void resultsAndInputsAreDefensivelyOwned() {
        double[][] input=Arrays.stream(TOY).map(double[]::clone).toArray(double[][]::new);
        var af=GeneticPrincipalComponents.estimateFrequencies(input,BASIS,LIMITS);double[] p=af.values();int[] basis=BASIS.clone();
        var model=GeneticPrincipalComponents.fit(MARKERS,SAMPLES,input,basis,p,Options.defaults(1,Missingness.MEAN_IMPUTE));
        double value=model.relationshipMatrix()[0],weight=model.projectionWeights()[0][0],frequency=model.frequencies()[0];
        input[0][0]=2;p[0]=.9;basis[0]=3;af.values()[0]=.1;af.observedSamples()[0]=0;af.referenceSamples()[0]=3;
        model.relationshipMatrix()[0]=999;model.projectionWeights()[0][0]=999;model.frequencies()[0]=.1;
        model.spectrum()[0]=999;model.fittedCoordinates()[0][0]=999;
        assertEquals(value,model.relationshipMatrix()[0]);assertEquals(weight,model.projectionWeights()[0][0]);assertEquals(frequency,model.frequencies()[0]);
        assertEquals(3,af.observedSamples()[0]); // First marker has one missing call, not an overwritten snapshot.
    }

    @Test void incompleteOrMisorientedProjectionPanelsAreRejected() {
        var model=toy();var reversed=new ArrayList<>(MARKERS);java.util.Collections.reverse(reversed);
        assertThrows(IllegalArgumentException.class,()->model.project(reversed,SAMPLES,TOY,LIMITS));
        var wrong=new ArrayList<>(MARKERS);wrong.set(0,new Marker("v0","G","A"));
        assertThrows(IllegalArgumentException.class,()->model.project(wrong,SAMPLES,TOY,LIMITS));
        assertThrows(IllegalArgumentException.class,()->model.project(MARKERS,List.of(SAMPLES.get(0),SAMPLES.get(0),SAMPLES.get(2),SAMPLES.get(3)),TOY,LIMITS));
    }

    @Test void rejectsInvalidDosagesFrequenciesAndIdentityCollisions() {
        double[] p={.5,.5,.5,.5};
        for(double bad:new double[]{-.1,2.1,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            double[][] input=Arrays.stream(TOY).map(double[]::clone).toArray(double[][]::new);input[3][3]=bad;
            assertThrows(IllegalArgumentException.class,()->fit(input,p,1));
        }
        for(double bad:new double[]{0,1,-.1,1.1,N,Double.POSITIVE_INFINITY}) {
            double[] badP=p.clone();badP[3]=bad;assertThrows(IllegalArgumentException.class,()->fit(TOY,badP,1));
        }
        assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.fit(List.of(MARKERS.get(0),MARKERS.get(0),MARKERS.get(2),MARKERS.get(3)),SAMPLES,TOY,BASIS,p,Options.defaults(1,Missingness.MEAN_IMPUTE)));
        assertThrows(IllegalArgumentException.class,()->new Marker("x","0","G"));
        assertThrows(IllegalArgumentException.class,()->new Marker("x","a","A"));
        assertThrows(IllegalArgumentException.class,()->new SampleId("bad id","i"));
    }

    @Test void rejectsEmptyRepeatedAndOutOfRangeSelections() {
        for(int[] reference:new int[][]{ {},{0,0},{-1},{4} })assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.estimateFrequencies(TOY,reference,LIMITS));
        for(int[] basis:new int[][]{{},{0},{0,0},{0,4}})assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.fit(MARKERS,SAMPLES,TOY,basis,new double[]{.5,.5,.5,.5},Options.defaults(1,Missingness.MEAN_IMPUTE)));
        assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.estimateFrequencies(new double[][]{{N,N}},new int[]{0,1},LIMITS));
    }

    @Test void rejectsNoCallBasisRowsAndSamples() {
        double[][] noMarker=Arrays.stream(TOY).map(double[]::clone).toArray(double[][]::new);Arrays.fill(noMarker[0],N);
        assertThrows(IllegalArgumentException.class,()->fit(noMarker,new double[]{.5,.5,.5,.5},1));
        double[][] noSample=Arrays.stream(TOY).map(double[]::clone).toArray(double[][]::new);for(double[] row:noSample)row[3]=N;
        assertThrows(IllegalArgumentException.class,()->fit(noSample,new double[]{.5,.5,.5,.5},1));
    }

    @Test void pairwiseNoJointCallsFailsAndTiedBoundaryIsExplicit() {
        double[][] g={{0,N},{N,2}};var markers=MARKERS.subList(0,2);var samples=SAMPLES.subList(0,2);
        assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.fit(markers,samples,g,new int[]{0,1},new double[]{.5,.5},Options.defaults(1,Missingness.PAIRWISE_COMPLETE)));
        var tie=assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.fit(markers,samples,g,new int[]{0,1},new double[]{.5,.5},Options.defaults(1,Missingness.MEAN_IMPUTE)));
        assertTrue(tie.getMessage().contains("tied eigenspace"));
        assertArrayEquals(new double[]{1,1},GeneticPrincipalComponents.fit(markers,samples,g,new int[]{0,1},new double[]{.5,.5},Options.defaults(2,Missingness.MEAN_IMPUTE)).spectrum(),1e-14);
        var rank=assertThrows(IllegalArgumentException.class,()->fit(new double[][]{{1,1,1,1},{1,1,1,1},{1,1,1,1},{1,1,1,1}},new double[]{.5,.5,.5,.5},1));
        assertTrue(rank.getMessage().contains("rank"));
    }

    @Test void exactResourceBoundsFailBeforeDenseWork() {
        for(Limits limit:List.of(new Limits(3,4,4,16,1000,1000,100000),new Limits(4,3,4,16,1000,1000,100000),
                new Limits(4,4,3,16,1000,1000,100000),new Limits(4,4,4,15,1000,1000,100000),
                new Limits(4,4,4,16,63,1000,100000),new Limits(4,4,4,16,1000,63,100000),
                new Limits(4,4,4,16,1000,1000,100)))
            assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.fit(MARKERS,SAMPLES,TOY,BASIS,new double[]{.5,.5,.5,.5},new Options(1,Missingness.MEAN_IMPUTE,1e-10,1e-8,limit)));
        assertThrows(IllegalArgumentException.class,()->toy().project(MARKERS,SAMPLES,TOY,new Limits(4,4,4,16,1000,1000,1)));
        assertThrows(IllegalArgumentException.class,()->GeneticPrincipalComponents.estimateFrequencies(TOY,BASIS,new Limits(4,4,4,16,1000,1000,1)));
    }

    private static GeneticPrincipalComponents.Model toy() {
        return fit(TOY,GeneticPrincipalComponents.estimateFrequencies(TOY,BASIS,LIMITS).values(),1);
    }
    private static GeneticPrincipalComponents.Model fit(double[][] input,double[] p,int k) {
        return GeneticPrincipalComponents.fit(MARKERS,SAMPLES,input,BASIS,p,Options.defaults(k,Missingness.MEAN_IMPUTE));
    }
    private static List<String> lines(String name) throws IOException {
        try(var stream=GeneticPrincipalComponentsTest.class.getResourceAsStream("/org/jlinalg/genetics/genetic-pca/"+name)) {
            if(stream==null)throw new IOException("Missing synthetic PCA fixture "+name);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines().toList();
        }
    }
    private static double quantum(double x) { return x==0 ? 0 : Math.pow(10,Math.floor(Math.log10(Math.abs(x)))-5); }
    private static void assertPrinted(String literal,double value,double roundoff) {
        double original=Double.parseDouble(literal);
        double q=original==0 || value==0 ? Math.max(quantum(original),quantum(value)) : Math.min(quantum(original),quantum(value));
        assertEquals(original,value,q/2+roundoff,literal);
    }
}
