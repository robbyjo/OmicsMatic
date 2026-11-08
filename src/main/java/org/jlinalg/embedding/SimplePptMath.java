/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: SimplePPT 1.1.7, Copyright (c) 2021 LouisFaure, BSD-3-Clause.
 * Original code, license conditions and source identity accompany simpleppt-core resources.
 */
package org.jlinalg.embedding;

import java.util.*;
import java.util.concurrent.CancellationException;

/**
 * Bounded float64 CPU arithmetic from original SimplePPT1.1.7.
 * This computes assignment, conditional-update and diagnostic equations only;
 * it does not fit a tree, choose a minimum spanning tree, draw random initial nodes,
 * select roots, or implement the scFates pseudotime workflow.
 * Point/node matrices use rows as observations and columns as coordinates.
 */
public final class SimplePptMath {
    private SimplePptMath() { }

    /** Limits apply to complete matrices and arithmetic; no partial result is returned. */
    public record Limits(int maximumRows,int maximumColumns,long maximumValues,long maximumOperations) {
        public Limits {require(maximumRows>0&&maximumColumns>0&&maximumValues>0&&maximumOperations>0,"Positive SimplePPT capacities required");}
        public static Limits defaults(){return new Limits(100000,4096,25000000,1000000000);}
    }
    /** Immutable full assignment result, including original all-zero underflow rows. */
    public static final class Assignments {
        private final double[][] kernel,weights;
        private final double[] sums;
        private final long zeroRows,nanMapped,operations;
        private Assignments(double[][] kernel,double[][] weights,double[] sums,long zeroRows,long nanMapped,long operations){this.kernel=kernel;this.weights=weights;this.sums=sums;this.zeroRows=zeroRows;this.nanMapped=nanMapped;this.operations=operations;}
        public double[][] kernel(){return copy(kernel);}
        public double[][] weights(){return copy(weights);}
        public double[] rowSums(){return sums.clone();}
        public long zeroMassRows(){return zeroRows;}
        public long sourceNaNsMappedToZero(){return nanMapped;}
        public long operations(){return operations;}
        public List<String> warnings(){return zeroRows==0?List.of():List.of("SOURCE ZERO-MASS ASSIGNMENTS: "+zeroRows+" complete rows underflowed to zero. Original CPU normalization maps their "+nanMapped+" 0/0 NaNs to zero; no fallback probability distribution is invented. These rows do not establish valid cell assignment. Inspect distance scale, sigma and original diagnostics before downstream analysis.");}
    }
    /** Immutable arithmetic output; nonfinite source states remain explicit. */
    public static final class MatrixResult {
        private final double[][] values;private final long nonfinite,operations;
        private MatrixResult(double[][] values,long operations){this.values=values;this.operations=operations;long n=0;for(double[] row:values)for(double v:row)if(!Double.isFinite(v))n++;nonfinite=n;}
        public double[][] values(){return copy(values);}
        public long nonfiniteValues(){return nonfinite;}
        public long operations(){return operations;}
        public List<String> warnings(){return nonfinite==0?List.of():List.of("NONFINITE SOURCE ARITHMETIC: retained "+nonfinite+" NaN/Inf states without replacement. No conditional solve, fitted tree or downstream analysis is admitted by this arithmetic component.");}
    }
    /** Original diagnostic terms are not a calibrated loss or an admission certificate. */
    public static final class Diagnostics {
        private final double[] terms;private final long operations;
        private Diagnostics(double[] terms,long operations){this.terms=terms;this.operations=operations;}
        public double[] terms(){return terms.clone();}
        public long operations(){return operations;}
        public List<String> warnings(){var result=new ArrayList<String>();result.add("Original diagnostic terms use correlation across coordinates, literal R*log(R), and pre-update node distances. They do not independently certify a fitted tree or biological accuracy.");if(Arrays.stream(terms).anyMatch(v->!Double.isFinite(v)))result.add("NONFINITE ORIGINAL DIAGNOSTIC: retain NaN/Inf, including 0*log(0) or zero coordinate variance. No epsilon, replacement or generic distance loss is substituted.");return List.copyOf(result);}
    }

    /** Original CPU exp(-distance/sigma), followed by row sums and NaN-to-zero normalization. */
    public static Assignments assignments(double[][] distances,double sigma,Limits limits){
        Meter m=new Meter(limits);int k=shape(distances,m,"cell-to-node distances");require(k>0&&distances.length>0&&Double.isFinite(sigma)&&sigma>0,"Require nonempty distances and finite positive sigma; no scientific parameter repair");m.add(3L*distances.length*k);double[][] kernel=new double[distances.length][k],weights=new double[distances.length][k];double[] sums=new double[distances.length];long zero=0,nan=0;
        for(int i=0;i<distances.length;i++){m.check();for(int j=0;j<k;j++){double d=distances[i][j];require(Double.isFinite(d)&&d>=0,"This CPU distance profile requires finite nonnegative distances");kernel[i][j]=Math.exp(-d/sigma);sums[i]+=kernel[i][j];}if(sums[i]==0)zero++;for(int j=0;j<k;j++){double value=kernel[i][j]/sums[i];if(Double.isNaN(value)){value=0;nan++;}weights[i][j]=value;}}
        return new Assignments(kernel,weights,sums,zero,nan,m.used);
    }

    /**
     * Literal max_j sqrt(sum_d(new-old)^2)/sqrt(sum_d(new^2)).
     * The numerator squares the signed coordinate SUM; it is not the Euclidean change norm.
     * Cancellation can produce zero despite changed coordinates. NaN/Inf propagate as in NumPy max.
     */
    public static double convergenceError(double[][] previousNodes,double[][] newNodes,Limits limits){
        Meter m=new Meter(limits);int d=shape(previousNodes,m,"previous nodes");require(shape(newNodes,m,"new nodes")==d&&newNodes.length==previousNodes.length&&d>0&&newNodes.length>0,"Matching complete nonempty node shapes required");m.add(6L*newNodes.length*d);double maximum=Double.NEGATIVE_INFINITY;for(int j=0;j<newNodes.length;j++){m.check();double change=0,norm=0;for(int c=0;c<d;c++){change+=newNodes[j][c]-previousNodes[j][c];norm+=newNodes[j][c]*newNodes[j][c];}double error=Math.sqrt(change*change)/Math.sqrt(norm);maximum=Math.max(maximum,error);}return maximum;
    }

    /** M=lambda*(diag(column-degree(B))-B)+diag(column-sum(R)); symmetric binary B may be a forest. */
    public static MatrixResult conditionalSystem(int[][] adjacency,double[][] assignments,double lambda,Limits limits){
        Meter m=new Meter(limits);int k=shape(assignments,m,"assignments");require(k>0&&assignments.length>0&&adjacency!=null&&adjacency.length==k&&Double.isFinite(lambda),"Complete compatible conditional-system inputs required");require(k<=limits.maximumRows()&&(long)k*k<=limits.maximumValues(),"Complete conditional-system capacity exceeded");m.add(4L*k*k+(long)assignments.length*k);double[] degree=new double[k],mass=new double[k];for(int i=0;i<k;i++)require(adjacency[i]!=null&&adjacency[i].length==k,"Ragged complete adjacency");for(int i=0;i<k;i++){m.check();for(int j=0;j<k;j++){int b=adjacency[i][j];require((b==0||b==1)&&b==adjacency[j][i]&&(i!=j||b==0),"Require original symmetric binary adjacency with zero diagonal");degree[j]+=b;}}for(double[] row:assignments)for(int j=0;j<k;j++){require(Double.isFinite(row[j])&&row[j]>=0,"Require finite nonnegative original assignments");mass[j]+=row[j];}double[][] result=new double[k][k];for(int i=0;i<k;i++){m.check();for(int j=0;j<k;j++)result[i][j]=((i==j?degree[j]:0)-adjacency[i][j])*lambda+(i==j?mass[j]:0);}return new MatrixResult(result,m.used);
    }

    /** Return the node-by-coordinate transpose of (X.T*W.T)@R. Null W means literal ones. */
    public static MatrixResult conditionalRightHandSide(double[][] points,double[][] weights,double[][] assignments,Limits limits){
        Meter m=new Meter(limits);int d=shape(points,m,"points"),k=shape(assignments,m,"assignments");require(points.length==assignments.length&&points.length>0&&d>0&&k>0,"Complete compatible nonempty point/assignment shapes required");if(weights!=null)require(shape(weights,m,"weights")==d&&weights.length==points.length,"Complete weight/point shapes differ");require(k<=limits.maximumRows()&&(long)k*d<=limits.maximumValues(),"Complete RHS capacity exceeded");m.add(3L*points.length*d*k);double[][] result=new double[k][d];for(int i=0;i<points.length;i++){m.check();for(int j=0;j<k;j++){double r=assignments[i][j];require(Double.isFinite(r)&&r>=0,"Require finite nonnegative original assignments");for(int c=0;c<d;c++){require(Double.isFinite(points[i][c])&&(weights==null||Double.isFinite(weights[i][c])),"Require finite supplied coordinates/weights; no repair");result[j][c]+=(points[i][c]*(weights==null?1:weights[i][c]))*r;}}}return new MatrixResult(result,m.used);
    }

    /** Original three score terms, retaining zero-probability entropy and zero-variance correlation NaNs. */
    public static Diagnostics diagnostics(double[][] points,double[][] finalNodes,double[][] assignments,double[][] preUpdateNodeDistances,int[][] adjacency,double sigma,double lambda,Limits limits){
        Meter m=new Meter(limits);int d=shape(points,m,"points"),nodeD=shape(finalNodes,m,"final nodes"),k=shape(assignments,m,"assignments");require(d==nodeD&&d>0&&points.length>0&&assignments.length==points.length&&k==finalNodes.length&&k>0&&shape(preUpdateNodeDistances,m,"pre-update node distances")==k&&preUpdateNodeDistances.length==k&&adjacency!=null&&adjacency.length==k&&Double.isFinite(sigma)&&Double.isFinite(lambda),"Complete compatible diagnostic shapes/parameters required");m.add(16L*points.length*k*d+3L*k*k);for(int i=0;i<k;i++)require(adjacency[i]!=null&&adjacency[i].length==k,"Ragged complete diagnostic adjacency");for(int i=0;i<k;i++)for(int j=0;j<k;j++)require((adjacency[i][j]==0||adjacency[i][j]==1)&&adjacency[i][j]==adjacency[j][i]&&(i!=j||adjacency[i][j]==0),"Require original symmetric binary diagnostic adjacency with zero diagonal");double first=0,second=0,third=0;double[] nodeMeans=new double[k],nodeNorms=new double[k];for(int j=0;j<k;j++){for(double v:finalNodes[j])nodeMeans[j]+=v;nodeMeans[j]/=d;for(double v:finalNodes[j]){double centered=v-nodeMeans[j];nodeNorms[j]+=centered*centered;}}
        for(int i=0;i<points.length;i++){m.check();double mean=0,norm=0;for(double v:points[i])mean+=v;mean/=d;for(double v:points[i]){double centered=v-mean;norm+=centered*centered;}for(int j=0;j<k;j++){double dot=0;for(int c=0;c<d;c++)dot+=(points[i][c]-mean)*(finalNodes[j][c]-nodeMeans[j]);double r=assignments[i][j];require(Double.isFinite(r)&&r>=0,"Require finite nonnegative original assignments");first+=(1-dot/Math.sqrt(norm*nodeNorms[j]))*r;second+=r*Math.log(r);}}
        for(int i=0;i<k;i++)for(int j=0;j<k;j++)third+=preUpdateNodeDistances[i][j]*adjacency[i][j];return new Diagnostics(new double[]{first/points.length,sigma/points.length*second,lambda/2*third},m.used);
    }

    private static int shape(double[][] values,Meter m,String name){require(values!=null&&values.length<=m.limits.maximumRows(),"Missing/oversized complete "+name);int columns=values.length==0?0:Objects.requireNonNull(values[0],name).length;require(columns<=m.limits.maximumColumns()&&(long)values.length*columns<=m.limits.maximumValues(),"Complete matrix capacity exceeded: "+name);m.add(values.length);for(double[] row:values)require(row!=null&&row.length==columns,"Ragged complete "+name);return columns;}
    private static double[][] copy(double[][] values){double[][] result=new double[values.length][];for(int i=0;i<values.length;i++)result[i]=values[i].clone();return result;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static final class Meter {
        final Limits limits;long used;
        Meter(Limits limits){this.limits=Objects.requireNonNull(limits,"limits");check();}
        void check(){if(Thread.currentThread().isInterrupted())throw new CancellationException("SimplePPT arithmetic cancelled; no partial result returned");}
        void add(long n){check();require(n>=0&&n<=limits.maximumOperations()-used,"Complete SimplePPT operation capacity exceeded");used+=n;}
    }
}
