/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.ArrayList;
import java.util.List;
import jdistlib.T;

/** Ordinary independent, Gaussian, common-variance one-way fit for treatment-control contrasts.
 * This class prepares and audits the model; it does not compute multivariate Dunnett inference.
 */
public final class DunnettModel {
    public static final int MAXIMUM_OBSERVATIONS = 2_000_000;
    public static final int MAXIMUM_GROUPS = 256;
    public record Group(int index, int size, double mean) { }
    /** Positive effects mean treatment minus control. The unadjusted test is two-sided. */
    public record Contrast(int treatment, int control, double difference, double standardError,
            double t, double unadjustedP, double logUnadjustedP) { }
    public record Result(List<Group> groups, int control, int observations, int residualDegrees,
            double residualSd, List<Contrast> contrasts, double[][] correlation) {
        public Result {
            groups = List.copyOf(groups); contrasts = List.copyOf(contrasts);
            correlation = copy(correlation);
        }
        @Override public double[][] correlation() { return copy(correlation); }
        private static double[][] copy(double[][] x) {
            double[][] result = new double[x.length][];
            for (int i=0;i<x.length;i++) result[i]=x[i].clone();
            return result;
        }
    }
    private DunnettModel() { }
    /** All declared groups participate in the residual variance. No rows or groups are removed. */
    public static Result fit(double[][] groups, int control) {
        if (groups==null || groups.length<2 || groups.length>MAXIMUM_GROUPS
                || control<0 || control>=groups.length)
            throw new IllegalArgumentException("Declare 2..256 complete groups and an existing control");
        int count=0;
        for (double[] group:groups) {
            if (group==null || group.length<1 || group.length>MAXIMUM_OBSERVATIONS-count)
                throw new IllegalArgumentException("Nonempty groups and at most two million observations required");
            count+=group.length;
        }
        int df=count-groups.length;
        if (df<1) throw new IllegalArgumentException("Positive pooled residual degrees of freedom required");
        double[][] samples=new double[groups.length][];
        var rows=new ArrayList<Group>();
        for (int i=0;i<groups.length;i++) {
            samples[i]=StatisticsSupport.sample(groups[i],1,"group "+i);
            double mean=StatisticsSupport.mean(samples[i]);
            if (!Double.isFinite(mean)) throw new ArithmeticException("Unrepresentable group mean");
            rows.add(new Group(i,samples[i].length,mean));
        }
        double sd=0, divisor=Math.sqrt(df);
        for (int i=0;i<samples.length;i++) for (double x:samples[i]) {
            double residual=x-rows.get(i).mean();
            if (!Double.isFinite(residual)) throw new ArithmeticException("Residual difference exceeds numeric capacity");
            sd=Math.hypot(sd,residual/divisor);
        }
        if (!(sd>0) || !Double.isFinite(sd))
            throw new ArithmeticException("Positive representable pooled residual SD required; no degenerate inference");
        var contrasts=new ArrayList<Contrast>();
        for (Group row:rows) if (row.index()!=control) {
            double difference=row.mean()-rows.get(control).mean();
            double factor=Math.sqrt(1.0/row.size()+1.0/rows.get(control).size());
            double se=sd*factor, t=(difference/sd)/factor;
            if (!Double.isFinite(difference) || !(se>0) || !Double.isFinite(se) || !Double.isFinite(t))
                throw new ArithmeticException("Unrepresentable contrast, standard error or t statistic; no partial family");
            double logP=Math.log(2)+T.cumulative(Math.abs(t),df,false,true);
            // At t=0 the tail is exactly 1/2; retain the exact two-sided probability.
            if (t==0) logP=0;
            if (Double.isNaN(logP) || logP>0) throw new ArithmeticException("Unresolved univariate Student-t tail");
            contrasts.add(new Contrast(row.index(),control,difference,se,t,Math.exp(logP),logP));
        }
        double[][] correlation=new double[contrasts.size()][contrasts.size()];
        int nc=rows.get(control).size();
        for (int i=0;i<correlation.length;i++) for (int j=0;j<correlation.length;j++) {
            double ni=rows.get(contrasts.get(i).treatment()).size(),nj=rows.get(contrasts.get(j).treatment()).size();
            correlation[i][j]=i==j?1:Math.sqrt(ni/(ni+nc))*Math.sqrt(nj/(nj+nc));
        }
        return new Result(rows,control,count,df,sd,contrasts,correlation);
    }
}
