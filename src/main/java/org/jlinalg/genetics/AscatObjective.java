/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Native components of ASCAT 2.5.2 and 3.2.0: make_segments and the numerical
 * objective in create_distance_matrix. Adapted from Peter Van Loo and the
 * ASCAT contributors' GPL-3.0 sources in VanLoo-lab/ascat (2.5.2: R/ascat.R;
 * 3.2.0: R/ascat.runAscat.R). These components require aligned, already
 * segmented autosomal logR/BAF. They do not segment reads, select a valid
 * purity/ploidy solution, or estimate copy number for a tumor.
 */
public final class AscatObjective {
    private AscatObjective() { }

    /** A run length counts retained loci, after removing source NA/NaN pairs. */
    public record Segment(double logR,double baf,long length) {
        public Segment {
            finite(logR,"segmented logR");frequency(baf);
            if(length<1||length>9_007_199_254_740_991L)
                throw new IllegalArgumentException("Require an exactly representable positive segment length");
        }
    }

    /**
     * Source make_segments on equal-length finite-or-missing scientific vectors.
     * Missing pairs are removed before adjacent runs are compared. No genomic
     * sorting, joining by ID, chromosome separation or segmentation is done.
     * Empty/all-missing vectors fail, as does the original function.
     */
    public static List<Segment> segments(double[] logR,double[] baf) {
        return segments(logR,baf,()->{});
    }

    /** Cancellation-aware variant of {@link #segments(double[], double[])}. */
    public static List<Segment> segments(double[] logR,double[] baf,Runnable check) {
        Objects.requireNonNull(logR,"logR");Objects.requireNonNull(baf,"baf");
        Objects.requireNonNull(check,"check");
        if(logR.length!=baf.length)throw new IllegalArgumentException("Aligned logR and BAF lengths differ; R recycling is unsupported");
        var result=new ArrayList<Segment>();double r=0,b=0;long length=0;
        for(int i=0;i<logR.length;i++) {
            if((i&1023)==0)check.run();
            // R na.omit removes the entire pair, including a NaN paired with Inf.
            if(Double.isNaN(logR[i])||Double.isNaN(baf[i]))continue;
            finite(logR[i],"segmented logR");frequency(baf[i]);
            if(length!=0&&(logR[i]!=r||baf[i]!=b)) {result.add(new Segment(r,b,length));length=0;}
            if(length==0){r=logR[i];b=baf[i];}
            length++;
        }
        if(length==0)throw new IllegalArgumentException("No retained segmented loci; no ASCAT objective is reportable");
        result.add(new Segment(r,b,length));check.run();return List.copyOf(result);
    }

    /**
     * One cell of the original distance matrix. Allele choice compares the
     * UNWEIGHTED sums of the two allele vectors across the complete segment
     * population. It is not a per-segment minimum. Lengths weight the subsequent
     * squared distance to nonnegative, ties-to-even rounded copy numbers.
     * Exactly balanced BAF=0.5 receives the source weight 0.05.
     */
    public static double distance(List<Segment> segments,double gamma,double psi,double rho) {
        return distance(segments,gamma,psi,rho,()->{});
    }

    /** Cancellation-aware overload; an exception never returns a partial cell. */
    public static double distance(List<Segment> segments,double gamma,double psi,double rho,Runnable check) {
        Objects.requireNonNull(segments,"segments");Objects.requireNonNull(check,"check");
        if(segments.isEmpty())throw new IllegalArgumentException("Require the complete nonempty segment population");
        positive(gamma,"gamma");positive(psi,"psi");positive(rho,"rho");
        if(rho>1.05)throw new IllegalArgumentException("Purity grid exceeds the original 1.05 upper domain");
        var sumA=new Sum();var sumB=new Sum();double normal=(1-rho)*2+rho*psi;
        for(int i=0;i<segments.size();i++) {
            if((i&1023)==0)check.run();Segment s=Objects.requireNonNull(segments.get(i),"segment");
            double power=StrictMath.pow(2,s.logR()/gamma);
            double a=(rho-1-(s.baf()-1)*power*normal)/rho;
            double b=(rho-1+s.baf()*power*normal)/rho;
            finite(a,"allele A copy value");finite(b,"allele B copy value");sumA.add(a);sumB.add(b);
        }
        boolean useA=sumA.value()<sumB.value();var total=new Sum();
        for(int i=0;i<segments.size();i++) {
            if((i&1023)==0)check.run();Segment s=segments.get(i);
            double power=StrictMath.pow(2,s.logR()/gamma);
            double minor=useA?(rho-1-(s.baf()-1)*power*normal)/rho:(rho-1+s.baf()*power*normal)/rho;
            double residual=Math.abs(minor-Math.max(Math.rint(minor),0));
            total.add(residual*residual*s.length()*(s.baf()==0.5?0.05:1));
        }
        check.run();return total.value();
    }

    /**
     * Complete explicit grid, in psi-major/rho-minor order. Axes are supplied by
     * the caller; no author range, platform gamma, or optimal solution is inferred.
     */
    public static double[][] grid(List<Segment> segments,double gamma,double[] psi,double[] rho,Runnable check) {
        Objects.requireNonNull(check,"check");
        axis(psi,"psi");axis(rho,"rho");
        if((long)psi.length*rho.length>1_000_000L)
            throw new IllegalArgumentException("ASCAT objective exceeds 1000000 grid cells");
        double[][] result=new double[psi.length][rho.length];
        for(int i=0;i<psi.length;i++)for(int j=0;j<rho.length;j++) {
            check.run();result[i][j]=distance(segments,gamma,psi[i],rho[j],check);
        }
        return result;
    }

    private static void axis(double[] values,String name) {
        Objects.requireNonNull(values,name);
        if(values.length==0)throw new IllegalArgumentException("Empty "+name+" axis");
        for(int i=0;i<values.length;i++) {
            positive(values[i],name);
            if(i>0&&values[i]<=values[i-1])throw new IllegalArgumentException("Require strictly increasing "+name+" axis");
        }
    }
    private static void frequency(double value){finite(value,"BAF");if(value<0||value>1)throw new IllegalArgumentException("Require BAF in [0,1]");}
    private static void positive(double value,String name){finite(value,name);if(value<=0)throw new IllegalArgumentException("Require positive "+name);}
    private static void finite(double value,String name){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite "+name+"; no objective is reportable");}

    // Compensated accumulation approximates R's long-double sum on supported
    // finite operands. It does not turn an overflow into a finite result.
    private static final class Sum {
        private double sum,correction;
        void add(double value) {
            finite(value,"objective summand");double next=sum+value;finite(next,"objective accumulation");
            correction+=Math.abs(sum)>=Math.abs(value)?(sum-next)+value:(value-next)+sum;
            finite(correction,"objective compensation");sum=next;
        }
        double value(){double value=sum+correction;finite(value,"objective sum");return value;}
    }
}
