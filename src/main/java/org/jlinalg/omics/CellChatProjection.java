/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Prepared RNA PPI projection following original CellChat projectData:
 * solve (I-alpha*normalize(A))*X=(1-alpha)*E in the COMPLETE network space,
 * then replace only overlapping rows of the original expression matrix.
 * Nonnegative finite weights, unique literal names and identical PPI axes are
 * required. This dense implementation does not select genes, normalize RNA,
 * infer a PPI, select raw.use, or perform communication inference.
 */
public final class CellChatProjection {
    private CellChatProjection() { }
    public enum Normalization { ROWS, COLUMNS }

    /** Logical capacities; these are not an operating-system memory quota. */
    public record Limits(int maxNetworkGenes, int maxExpressionGenes, int maxCells,
            long maxWorkingBytes, long maxArithmeticUpdates) {
        public Limits {
            require(maxNetworkGenes>0 && maxExpressionGenes>0 && maxCells>0
                && maxWorkingBytes>0 && maxArithmeticUpdates>0,"Capacities must be positive");
        }
        public static Limits defaults() {
            return new Limits(1024,100000,100000,512L*1024*1024,500000000L);
        }
    }

    public static final class Result {
        private final double[][] expression;
        private final int overlappingGenes, networkOnlyGenes;
        private final double relativeResidual, residualTolerance;
        private final List<String> warnings;
        private Result(double[][] expression,int overlappingGenes,int networkOnlyGenes,
                double relativeResidual,double residualTolerance,List<String> warnings) {
            this.expression=expression;this.overlappingGenes=overlappingGenes;
            this.networkOnlyGenes=networkOnlyGenes;this.relativeResidual=relativeResidual;
            this.residualTolerance=residualTolerance;this.warnings=List.copyOf(warnings);
        }
        public double[][] expression() { return copy(expression); }
        public int overlappingGenes() { return overlappingGenes; }
        public int networkOnlyGenes() { return networkOnlyGenes; }
        /** Maximum componentwise residual scaled by |M|*|X|+|B|. */
        public double relativeResidual() { return relativeResidual; }
        public double residualTolerance() { return residualTolerance; }
        public List<String> warnings() { return warnings; }
    }

    public static Result project(String[] genes,double[][] expression,
            String[] networkGenes,double[][] adjacency,double alpha,
            Normalization normalization,Limits limits) {
        require(limits!=null && normalization!=null,"Explicit normalization and limits required");
        require(Double.isFinite(alpha) && alpha>0 && alpha<1,"Original projectData requires 0 < alpha < 1");
        require(genes!=null && networkGenes!=null && expression!=null && adjacency!=null,
            "Named expression and adjacency matrices required");
        int n=networkGenes.length,e=genes.length;
        require(n>0 && n<=limits.maxNetworkGenes && e>0 && e<=limits.maxExpressionGenes,
            "Complete gene populations exceed declared capacity or are empty");
        require(expression.length==e && expression[0]!=null,"Expression row count mismatch");
        int cells=expression[0].length;
        require(cells>0 && cells<=limits.maxCells,"Complete cell population exceeds capacity or is empty");
        long bytes,updates;
        try {
            long nn=Math.multiplyExact((long)n,n),nc=Math.multiplyExact((long)n,cells),ec=Math.multiplyExact((long)e,cells);
            bytes=Math.addExact(Math.multiplyExact(32,nn),Math.addExact(Math.multiplyExact(40,nc),Math.multiplyExact(32,ec)));
            updates=Math.addExact(Math.multiplyExact(nn,n),Math.multiplyExact(3,Math.multiplyExact(nn,cells)));
        } catch(ArithmeticException overflow) { throw new IllegalArgumentException("Projection capacity arithmetic overflow",overflow); }
        require(bytes<=limits.maxWorkingBytes && updates<=limits.maxArithmeticUpdates,
            "Complete projection exceeds declared working-byte/arithmetic capacity; no partial projection");
        Map<String,Integer> expressionIndex=index(genes);index(networkGenes);
        require(adjacency.length==n,"PPI must be square with identical ordered axes");
        for(double[] row:expression) values(row,cells,"Expression");
        double[] sums=new double[n],rows=new double[n],columns=new double[n];
        for(int i=0;i<n;i++) {
            values(adjacency[i],n,"PPI");
            for(int j=0;j<n;j++) { rows[i]+=adjacency[i][j];columns[j]+=adjacency[i][j]; }
        }
        for(int i=0;i<n;i++) {
            require(Double.isFinite(rows[i]) && rows[i]>0 && Double.isFinite(columns[i]) && columns[i]>0,
                "Original projectData refuses zero PPI rows/columns; overflowing sums also refused");
            sums[i]=normalization==Normalization.ROWS?rows[i]:columns[i];
        }
        double[][] lu=new double[n][n],rhs=new double[n][cells];
        int overlap=0;
        for(int i=0;i<n;i++) {
            for(int j=0;j<n;j++) lu[i][j]=(i==j?1:0)-alpha*adjacency[i][j]/sums[normalization==Normalization.ROWS?i:j];
            Integer old=expressionIndex.get(networkGenes[i]);
            if(old!=null) { overlap++;for(int c=0;c<cells;c++) rhs[i][c]=(1-alpha)*expression[old][c]; }
        }
        // Partial-pivot Gaussian elimination; solve every cell with the same factors.
        for(int k=0;k<n;k++) {
            cancelled();int pivot=k;
            for(int i=k+1;i<n;i++) if(Math.abs(lu[i][k])>Math.abs(lu[pivot][k])) pivot=i;
            require(lu[pivot][k]!=0 && Double.isFinite(lu[pivot][k]),"Numerically singular projection; no result");
            double[] swap=lu[k];lu[k]=lu[pivot];lu[pivot]=swap;swap=rhs[k];rhs[k]=rhs[pivot];rhs[pivot]=swap;
            for(int i=k+1;i<n;i++) {
                double multiplier=lu[i][k]/lu[k][k];lu[i][k]=0;
                for(int j=k+1;j<n;j++) lu[i][j]-=multiplier*lu[k][j];
                for(int c=0;c<cells;c++) rhs[i][c]-=multiplier*rhs[k][c];
            }
        }
        for(int i=n-1;i>=0;i--) {
            cancelled();
            for(int c=0;c<cells;c++) {
                double value=rhs[i][c];for(int j=i+1;j<n;j++) value-=lu[i][j]*rhs[j][c];
                rhs[i][c]=value/lu[i][i];require(Double.isFinite(rhs[i][c]),"Nonfinite projection solve; no result");
            }
        }
        double residual=0;
        for(int i=0;i<n;i++) {
            cancelled();Integer old=expressionIndex.get(networkGenes[i]);
            for(int c=0;c<cells;c++) {
                double b=old==null?0:(1-alpha)*expression[old][c],value=-b,scale=Math.abs(b);
                for(int j=0;j<n;j++) {
                    double m=(i==j?1:0)-alpha*adjacency[i][j]/sums[normalization==Normalization.ROWS?i:j];
                    value+=m*rhs[j][c];scale+=Math.abs(m)*Math.abs(rhs[j][c]);
                }
                require(Double.isFinite(value) && Double.isFinite(scale),"Nonfinite residual arithmetic; no result");
                residual=Math.max(residual,scale==0?(value==0?0:Double.POSITIVE_INFINITY):Math.abs(value)/scale);
            }
        }
        double tolerance=128*Math.ulp(1.0)*Math.max(1,n);
        require(residual<=tolerance,"Projection residual exceeds numerical admission bound; no result");
        double[][] projected=copy(expression);
        for(int i=0;i<n;i++) { Integer old=expressionIndex.get(networkGenes[i]);if(old!=null) projected[old]=rhs[i].clone(); }
        var warnings=new ArrayList<String>();
        warnings.add("Projection alone does not change CellChat inference. Select raw.use=FALSE explicitly in an upstream workflow to consume projected expression; no paper setting is inferred.");
        if(overlap==0) warnings.add("No expression genes overlap the PPI: every original expression row is unchanged.");
        if(alpha>0.99) warnings.add("Alpha is close to one. A small backward residual does not establish small forward error; compare with the original solver for sensitive downstream decisions.");
        return new Result(projected,overlap,n-overlap,residual,tolerance,warnings);
    }

    private static Map<String,Integer> index(String[] names) {
        var result=new HashMap<String,Integer>();var unique=new HashSet<String>();
        for(int i=0;i<names.length;i++) {
            String name=names[i];require(name!=null && !name.isEmpty() && name.equals(name.strip())
                && name.chars().noneMatch(c->Character.isISOControl(c)),"Invalid literal gene identifier");
            require(unique.add(name),"Duplicate literal gene identifier");result.put(name,i);
        }
        return result;
    }
    private static void values(double[] row,int count,String kind) {
        require(row!=null && row.length==count,kind+" matrix is ragged");
        for(double value:row) require(Double.isFinite(value) && value>=0,kind+" must be finite and nonnegative");
    }
    private static double[][] copy(double[][] value) {
        double[][] result=new double[value.length][];for(int i=0;i<value.length;i++) result[i]=value[i].clone();return result;
    }
    private static void cancelled() { if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Projection cancelled; no result"); }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalArgumentException(message); }
}
