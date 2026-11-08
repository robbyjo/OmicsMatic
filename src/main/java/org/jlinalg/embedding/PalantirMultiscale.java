/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.embedding;

import java.util.*;
import java.util.concurrent.CancellationException;

/**
 * Source-bound Palantir determine_multiscale_space on explicit ordered float64 eigenpairs.
 * Follows the MIT-licensed original 1.4.4/1.4.6 calculation; original source/license
 * notices are retained in the palantir-multiscale resource profile.
 * This is a coordinate component, not diffusion-map fitting or a trajectory workflow.
 */
public final class PalantirMultiscale {
    private PalantirMultiscale() { }

    /** Explicit complete-family capacities; no partial result is returned. */
    public record Limits(int maximumCells,int maximumComponents,long maximumValues,long maximumOperations) {
        public Limits {
            if(maximumCells<1||maximumComponents<1||maximumValues<1||maximumOperations<1)
                throw new IllegalArgumentException("Palantir capacities must be positive");
        }
        public static Limits defaults(){return new Limits(100000,4096,20000000,100000000);}
    }

    /** Immutable complete output, including source positional selection and nonfinite dispositions. */
    public static final class Result {
        private final String[] cells;
        private final double[][] coordinates;
        private final int[] selected;
        private final double[] weights,gaps;
        private final int nEigs;
        private final boolean automatic;
        private final long operations,nanCount,infiniteCount;
        private final List<String> warnings;
        private Result(String[] cells,double[][] coordinates,int[] selected,double[] weights,double[] gaps,
                       int nEigs,boolean automatic,long operations,long nanCount,long infiniteCount,List<String> warnings){
            this.cells=cells.clone();this.coordinates=coordinates;this.selected=selected;this.weights=weights;this.gaps=gaps;
            this.nEigs=nEigs;this.automatic=automatic;this.operations=operations;this.nanCount=nanCount;this.infiniteCount=infiniteCount;this.warnings=List.copyOf(warnings);
        }
        public String[] cells(){return cells.clone();}
        public double[][] coordinates(){double[][] copy=new double[coordinates.length][];for(int i=0;i<copy.length;i++)copy[i]=coordinates[i].clone();return copy;}
        /** Original eigenvector positions; output columns are newly numbered 0..k-1, as in upstream. */
        public int[] selectedComponents(){return selected.clone();}
        public double[] weights(){return weights.clone();}
        public double[] eigenGaps(){return gaps.clone();}
        public int nEigs(){return nEigs;}
        public boolean automatic(){return automatic;}
        public long operations(){return operations;}
        public long nanOutputs(){return nanCount;}
        public long infiniteOutputs(){return infiniteCount;}
        public List<String> warnings(){return warnings;}
    }

    /**
     * Compute vectors[:,1:nEigs] * (lambda[1:nEigs]/(1-lambda[1:nEigs])).
     * nEigs is the upstream exclusive bound, so nEigs=4 returns three columns.
     * A null bound uses the literal largest/second-largest eigengap and minimum-three rules.
     * Canonical ordered NumPy eigenvalues or equivalently labelled Series are required;
     * arbitrary pandas Series label alignment is outside this API and is never repaired.
     * Duplicate/Unicode/empty string cell labels are preserved; controls and null labels fail.
     * Nonfinite numerical values are retained with output dispositions, not imputed.
     * A tied automatic ordering is accepted only when every possible top-two tie order
     * produces the same upstream bound. Otherwise use the original runtime or an explicit
     * scientifically chosen bound; no unsupported NumPy tie order is silently substituted.
     */
    public static Result scale(String[] cells,double[][] eigenvectors,double[] eigenvalues,Integer nEigs,Limits limits){
        Objects.requireNonNull(cells,"cells");Objects.requireNonNull(eigenvectors,"eigenvectors");Objects.requireNonNull(eigenvalues,"eigenvalues");Objects.requireNonNull(limits,"limits");
        Meter meter=new Meter(limits.maximumOperations());
        require(cells.length==eigenvectors.length,"Cell/eigenvector row count differs; preserve exact identities/order");
        require(cells.length<=limits.maximumCells()&&eigenvalues.length<=limits.maximumComponents(),"Complete Palantir input capacities exceeded");
        int columns=eigenvectors.length==0?eigenvalues.length:Objects.requireNonNull(eigenvectors[0],"eigenvector row").length;
        require(columns<=limits.maximumComponents(),"Complete eigenvector component capacity exceeded");
        require((long)cells.length*columns<=limits.maximumValues(),"Complete eigenvector matrix capacity exceeded");
        for(int i=0;i<cells.length;i++){
            meter.add(1);String cell=Objects.requireNonNull(cells[i],"cell label");
            for(int j=0;j<cell.length();j++){meter.add(1);char c=cell.charAt(j);require(!Character.isISOControl(c),"Control characters in cell labels are outside this profile");if(Character.isHighSurrogate(c)){require(j+1<cell.length()&&Character.isLowSurrogate(cell.charAt(j+1)),"Unpaired cell-label surrogate");j++;}else require(!Character.isLowSurrogate(c),"Unpaired cell-label surrogate");}
            require(eigenvectors[i]!=null&&eigenvectors[i].length==columns,"Ragged eigenvector matrix");
        }
        double[] gaps=new double[0];int count;
        if(nEigs==null){
            require(eigenvalues.length>=3,"Original automatic eigengap selection fails with fewer than three eigenvalues; use the original runtime or an explicit bound");
            gaps=new double[eigenvalues.length-1];for(int i=0;i<gaps.length;i++){meter.add(1);gaps[i]=eigenvalues[i]-eigenvalues[i+1];}
            count=automaticBound(gaps,meter);
        }else count=nEigs;
        int selectedCount=count>1?count-1:0;
        require(selectedCount==0||selectedCount<eigenvalues.length&&selectedCount<columns,"Requested upstream n_eigs selects a missing eigenvalue/eigenvector position; no truncation or repair");
        require((long)cells.length*selectedCount<=limits.maximumValues(),"Complete multiscale output capacity exceeded");
        // Reserve all arithmetic before allocating the complete output family.
        meter.add(Math.addExact((long)selectedCount*2,(long)cells.length*selectedCount));
        double[] weights=new double[selectedCount];int[] selected=new int[selectedCount];
        for(int j=0;j<selectedCount;j++){selected[j]=j+1;double value=eigenvalues[j+1];weights[j]=value/(1-value);}
        double[][] result=new double[cells.length][selectedCount];long nans=0,infinities=0;
        for(int i=0;i<cells.length;i++){
            meter.check();
            for(int j=0;j<selectedCount;j++){double value=eigenvectors[i][j+1]*weights[j];result[i][j]=value;if(Double.isNaN(value))nans++;else if(Double.isInfinite(value))infinities++;}
        }
        var warnings=new ArrayList<String>();warnings.add("Native Palantir multiscale-coordinate component only. Diffusion kernel/eigensolver, start/terminal selection, pseudotime, fate probabilities, entropy and whole-paper reproduction require their separate original workflows. Input eigenvalue order must already match the supplied eigenvector positions; arbitrary Series labels are not repaired.");
        if(nans+infinities>0)warnings.add("NONFINITE SOURCE OUTPUT: retained "+nans+" NaN and "+infinities+" infinite coordinates from the literal weighting/multiplication. No value is replaced; do not admit these as a completed downstream trajectory. Inspect eigenpairs, declared component selection and original diagnostics.");
        if(selectedCount==0)warnings.add("The explicit upstream n_eigs range selects zero coordinates. The complete empty-column family and all cell identities are retained; this is not a fitted trajectory.");
        return new Result(cells,result,selected,weights,gaps,count,nEigs==null,meter.used,nans,infinities,warnings);
    }

    private static int automaticBound(double[] gaps,Meter meter){
        List<Integer> largest=largest(gaps,-1,meter);var possible=new TreeSet<Integer>();
        for(int first:largest){
            if(first+1>=3)possible.add(first+1);
            else for(int second:largest(gaps,first,meter)){meter.add(1);possible.add(Math.max(3,second+1));if(possible.size()>1)ambiguous();}
            if(possible.size()>1)ambiguous();
        }
        require(possible.size()==1,"Incomplete source eigengap selection");return possible.first();
    }
    private static List<Integer> largest(double[] gaps,int excluded,Meter meter){
        var selected=new ArrayList<Integer>();double best=0;
        for(int i=0;i<gaps.length;i++){
            meter.add(1);if(i==excluded)continue;
            int order=selected.isEmpty()?1:compare(gaps[i],best);
            if(order>0){selected.clear();selected.add(i);best=gaps[i];}else if(order==0)selected.add(i);
        }
        require(!selected.isEmpty(),"Original second-gap index is unavailable");return selected;
    }
    /** NumPy ascending floating ordering places NaNs last and does not order signed zeros. */
    private static int compare(double a,double b){if(Double.isNaN(a))return Double.isNaN(b)?0:1;if(Double.isNaN(b))return -1;return a>b?1:a<b?-1:0;}
    private static void ambiguous(){throw new IllegalArgumentException("Tied automatic eigengaps admit multiple NumPy-dependent n_eigs bounds. Use the unchanged original Palantir runtime or an explicit independently chosen n_eigs; no Java tie order is inferred.");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static final class Meter {
        final long maximum;long used;
        Meter(long maximum){this.maximum=maximum;check();}
        void check(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Palantir multiscale computation cancelled; no partial result returned");}
        void add(long amount){check();require(amount>=0&&amount<=maximum-used,"Complete Palantir operation capacity exceeded; no partial result returned");used+=amount;}
    }
}
