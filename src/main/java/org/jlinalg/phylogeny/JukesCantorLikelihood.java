/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import java.util.Map;
import java.util.Objects;

/**
 * Native JC69 pruning likelihood for a supplied tree and complete DNA alignment.
 * Branch lengths are expected substitutions per site. IUPAC ambiguities are sets
 * of possible states; N, question mark and gap are wholly unobserved states.
 * This evaluates a fixed model/tree, not IQ-TREE's model selection or search.
 */
public final class JukesCantorLikelihood {
    private JukesCantorLikelihood() { }

    public record Limits(int taxa, int sites, long cells, long nodeSiteVisits) {
        public Limits {
            if (taxa<1 || sites<1 || cells<1 || nodeSiteVisits<1)
                throw new IllegalArgumentException("Likelihood capacities must be positive");
        }
        public static Limits defaults() { return new Limits(1_000_000,10_000_000,100_000_000,200_000_000); }
    }

    /** Negative infinity is retained only for mathematically zero-probability sites. */
    public record Result(double logLikelihood, double[] siteLogLikelihoods, int zeroProbabilitySites) {
        public Result { siteLogLikelihoods=siteLogLikelihoods.clone(); }
        @Override public double[] siteLogLikelihoods() { return siteLogLikelihoods.clone(); }
    }

    public static Result evaluate(NewickTree tree, Map<String,String> alignment) {
        return evaluate(tree,alignment,Limits.defaults());
    }

    public static Result evaluate(NewickTree tree, Map<String,String> alignment, Limits limits) {
        Objects.requireNonNull(tree); Objects.requireNonNull(alignment); Objects.requireNonNull(limits);
        tree.requireMetricLengths(); alignment=Map.copyOf(alignment);
        if (!alignment.keySet().equals(tree.taxa()))
            throw new IllegalArgumentException("JC69 alignment must contain the complete exact tree taxon set");
        if (alignment.size()>limits.taxa()) throw new IllegalArgumentException("Complete JC69 taxon capacity exceeded");
        int sites=-1;
        String[] sequences=new String[tree.nodes().size()];
        for (var leaf:tree.leafNodes().entrySet()) {
            String value=alignment.get(leaf.getKey());
            if (sites<0) sites=value.length();
            if (value.length()!=sites || sites==0) throw new IllegalArgumentException("JC69 sequences must be nonempty and aligned to equal lengths");
            if (sites>limits.sites()) throw new IllegalArgumentException("Complete JC69 site capacity exceeded");
            for (int i=0;i<value.length();i++) states(value.charAt(i));
            sequences[leaf.getValue()]=value;
        }
        if (Math.multiplyExact((long)alignment.size(),sites)>limits.cells() || Math.multiplyExact((long)tree.nodes().size(),sites)>limits.nodeSiteVisits())
            throw new IllegalArgumentException("Complete JC69 cell/operation capacity exceeded; raise declared capacities");
        double[] partials=new double[Math.multiplyExact(tree.nodes().size(),4)];
        double[] scales=new double[tree.nodes().size()];
        double[] exponent=new double[tree.nodes().size()],different=new double[tree.nodes().size()];
        double[] logSame=new double[tree.nodes().size()],logDifferent=new double[tree.nodes().size()];
        boolean tinyTransition=false;
        for (var node:tree.nodes()) if (node.parent()>=0) {
            // expm1 retains short positive branches without subtracting two nearly equal ones.
            double changed=-StrictMath.expm1((-4.0/3.0)*node.branchLength());
            different[node.id()]=.25*changed; exponent[node.id()]=StrictMath.exp((-4.0/3.0)*node.branchLength());
            double t=node.branchLength();
            if (t==0) { logSame[node.id()]=0;logDifferent[node.id()]=Double.NEGATIVE_INFINITY; }
            else {
                // Log-domain short-branch series avoids rounding a positive transition to zero.
                logDifferent[node.id()]=t<1e-8 ? StrictMath.log(t)-StrictMath.log(3)-(2.0/3)*t+(2.0/27)*t*t : StrictMath.log(changed)-StrictMath.log(4);
                logSame[node.id()]=t<1e-8 ? -t+t*t/6+t*t*t/27 : StrictMath.log1p(-.75*changed);
                if (different[node.id()]==0) tinyTransition=true;
            }
        }
        double[] siteValues=new double[sites],logPartials=null; double total=0,correction=0; int impossible=0;
        for (int site=0;site<sites;site++) {
            boolean underflow=tinyTransition;
            boolean allMissing=true;
            for (var leaf:tree.leafNodes().entrySet()) if (states(sequences[leaf.getValue()].charAt(site))!=15) { allMissing=false; break; }
            if (allMissing) { siteValues[site]=0; continue; }
            for (int id=tree.nodes().size()-1;id>=0;id--) {
                var node=tree.nodes().get(id); int offset=4*id;
                if (node.isLeaf()) {
                    int mask=states(sequences[id].charAt(site));
                    for (int k=0;k<4;k++) partials[offset+k]=(mask&(1<<k))!=0 ? 1 : 0;
                    scales[id]=0;
                } else {
                    for (int k=0;k<4;k++) partials[offset+k]=1;
                    double scale=0;
                    for (int child:node.children()) {
                        int childOffset=4*child;
                        double sum=partials[childOffset]+partials[childOffset+1]+partials[childOffset+2]+partials[childOffset+3];
                        for (int k=0;k<4;k++) {
                            double contribution=different[child]*sum+exponent[child]*partials[childOffset+k];
                            double before=partials[offset+k],value=before*contribution;
                            if (before>0 && contribution>0 && value==0) underflow=true;
                            partials[offset+k]=value;
                        }
                        scale+=scales[child];
                        double maximum=0;
                        for (int k=0;k<4;k++) maximum=Math.max(maximum,partials[offset+k]);
                        if (maximum==0) { scale=Double.NEGATIVE_INFINITY; break; }
                        for (int k=0;k<4;k++) partials[offset+k]/=maximum;
                        scale+=StrictMath.log(maximum);
                    }
                    scales[id]=scale;
                }
            }
            int rootOffset=4*tree.root();
            double probability=.25*(partials[rootOffset]+partials[rootOffset+1]+partials[rootOffset+2]+partials[rootOffset+3]);
            double value=scales[tree.root()]+StrictMath.log(probability);
            if (underflow || value==Double.NEGATIVE_INFINITY) {
                if (logPartials==null) logPartials=new double[partials.length];
                value=logSite(tree,sequences,site,logSame,logDifferent,logPartials);
            }
            if (Double.isNaN(value) || value==Double.POSITIVE_INFINITY) throw new ArithmeticException("JC69 likelihood is undefined");
            siteValues[site]=value;
            if (value==Double.NEGATIVE_INFINITY) { impossible++; continue; }
            double adjusted=value-correction, next=total+adjusted; correction=(next-total)-adjusted;total=next;
        }
        return new Result(impossible>0 ? Double.NEGATIVE_INFINITY : total,siteValues,impossible);
    }

    private static double logSite(NewickTree tree,String[] sequences,int site,double[] same,double[] different,double[] partials) {
        for (int id=tree.nodes().size()-1;id>=0;id--) {
            var node=tree.nodes().get(id);int offset=4*id;
            if (node.isLeaf()) {
                int mask=states(sequences[id].charAt(site));
                for (int k=0;k<4;k++) partials[offset+k]=(mask&(1<<k))!=0 ? 0 : Double.NEGATIVE_INFINITY;
            } else {
                for (int k=0;k<4;k++) partials[offset+k]=0;
                for (int child:node.children()) for (int k=0;k<4;k++) {
                    int at=4*child;
                    partials[offset+k]+=logSum(partials[at]+(k==0?same[child]:different[child]),
                        partials[at+1]+(k==1?same[child]:different[child]),
                        partials[at+2]+(k==2?same[child]:different[child]),
                        partials[at+3]+(k==3?same[child]:different[child]));
                }
            }
        }
        int root=4*tree.root();
        return logSum(partials[root],partials[root+1],partials[root+2],partials[root+3])-StrictMath.log(4);
    }

    private static double logSum(double a,double b,double c,double d) {
        double maximum=Math.max(Math.max(a,b),Math.max(c,d));
        if (maximum==Double.NEGATIVE_INFINITY) return maximum;
        return maximum+StrictMath.log(StrictMath.exp(a-maximum)+StrictMath.exp(b-maximum)+StrictMath.exp(c-maximum)+StrictMath.exp(d-maximum));
    }

    private static int states(char symbol) {
        return switch (symbol) {
            case 'A','a' -> 1;
            case 'C','c' -> 2;
            case 'G','g' -> 4;
            case 'T','t' -> 8;
            case 'R','r' -> 5;
            case 'Y','y' -> 10;
            case 'S','s' -> 6;
            case 'W','w' -> 9;
            case 'K','k' -> 12;
            case 'M','m' -> 3;
            case 'B','b' -> 14;
            case 'D','d' -> 13;
            case 'H','h' -> 11;
            case 'V','v' -> 7;
            case 'N','n','?','-' -> 15;
            default -> throw new IllegalArgumentException("Unsupported JC69 DNA state: "+symbol);
        };
    }
}
