/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.*;
import jdistlib.Normal;
import jdistlib.T;

/** Source-bound coefficient and finite/pairwise Pearson operations, not model fitting. */
public final class Monocle131Statistics {
    private Monocle131Statistics() { }
    public record Coefficient(String gene,String component,String term,double estimate,double standardError,
                              double statistic,double p,double adjustedP,String distribution,double residualDf) { }
    public record Audit(int genes,int rows,int testingFamilies,double maximumLogPError,double maximumQError) { }
    public enum Kind { VALUE, NA, NAN, NEGATIVE_INFINITY }
    public record Pearson(double coefficient,Kind kind,int pairs) {
        public boolean greaterThan(double threshold){return kind==Kind.VALUE&&coefficient>threshold;}
    }
    public static Audit audit(List<String> genes,List<Coefficient> coefficients) {
        var population=new LinkedHashSet<>(genes);require(!population.isEmpty()&&population.size()==genes.size(),"Unique complete gene axis required");
        var families=new LinkedHashMap<String,List<Coefficient>>();var seen=new HashSet<String>();double logError=0,qError=0;
        for(var c:coefficients){
            require(population.contains(c.gene)&&c.component!=null&&!c.component.isEmpty()&&c.term!=null&&!c.term.isEmpty()&&c.component.indexOf('\0')<0&&c.term.indexOf('\0')<0,"Coefficient identity differs from complete population");
            require(seen.add(c.gene+'\0'+c.component+'\0'+c.term),"Duplicate gene/component/term coefficient");
            require(Double.isFinite(c.estimate)&&Double.isFinite(c.standardError)&&c.standardError>0,"Unadmitted estimate or standard error");
            double statistic=c.estimate/c.standardError;compareValue(statistic,c.statistic,"estimate/standard-error statistic");
            double p;
            if(c.distribution.equals("NORMAL"))p=2*Normal.cumulative(Math.abs(statistic),0,1,false,false);
            else {require(c.distribution.equals("T")&&Double.isFinite(c.residualDf)&&c.residualDf>0,"Explicit original NORMAL/T distribution and residual df required");p=2*T.cumulative(Math.abs(statistic),c.residualDf,false,false);}
            probability(c.p);probability(p);probability(c.adjustedP);
            if(p==0||c.p==0)require(p==c.p,"Original/native p-value underflow differs");else{double e=Math.abs(Math.log(p)-Math.log(c.p));require(e<=1e-8,"Original/native coefficient tail probability differs");logError=Math.max(logError,e);}
            families.computeIfAbsent(c.component+'\0'+c.term,k->new ArrayList<>()).add(c);
        }
        require(!families.isEmpty(),"Empty original coefficient table");
        for(var rows:families.values()){
            var ids=new HashSet<String>();for(var c:rows)ids.add(c.gene);require(ids.equals(population)&&rows.size()==genes.size(),"Incomplete term/component testing family");
            double[] p=rows.stream().mapToDouble(Coefficient::p).toArray(),q=holm(p);
            for(int i=0;i<q.length;i++){double e=Math.abs(q[i]-rows.get(i).adjustedP);require(e<=1e-12*Math.max(1,Math.abs(q[i])),"Original grouped default-Holm adjustment differs");if(q[i]==0||rows.get(i).adjustedP==0)require(q[i]==rows.get(i).adjustedP,"Original/native adjusted-p underflow differs");else require(Math.abs(Math.log(q[i])-Math.log(rows.get(i).adjustedP))<=1e-8,"Original/native adjusted tail probability differs");qError=Math.max(qError,e);}
        }
        return new Audit(genes.size(),coefficients.size(),families.size(),logError,qError);
    }
    /** Original p.adjust default Holm, for a complete finite testing family. */
    public static double[] holm(double[] p) {
        Integer[] order=new Integer[p.length];for(int i=0;i<p.length;i++){probability(p[i]);order[i]=i;}
        Arrays.sort(order,Comparator.comparingDouble(i->p[i]));double[] q=new double[p.length];double previous=0;
        for(int rank=0;rank<order.length;rank++){int i=order[rank];previous=Math.max(previous,(p.length-rank)*p[i]);q[i]=Math.min(1,previous);}return q;
    }
    /**
     * Pearson branch of GPL>=2 Hmisc rcorr (Harrell and contributors), selected
     * source 8683675a8e40cf8f0e3e36ff7129912cc2e95857, R/rcorr.s and src/rcorr.f90.
     * Keeps original input order, naive double sums, pairwise <1e49 sentinel
     * rule and undefined constant results; does not clip/rescale coefficients.
     * This is not full Hmisc/Spearman or a recovered paper-version claim.
     */
    public static Pearson pearson(double[] expression,double[] score) {
        require(expression.length==score.length&&expression.length>=5,"Hmisc rcorr requires matched axes and at least five observations");
        int n=0;double sx=0,sy=0;
        for(int i=0;i<expression.length;i++){require(!Double.isInfinite(expression[i])&&!Double.isInfinite(score[i]),"Original R .Fortran call refuses infinite arguments");if(expression[i]<1e49&&score[i]<1e49){n++;sx+=expression[i];sy+=score[i];}}
        if(n<2)return new Pearson(Double.NaN,Kind.NA,n);
        double mx=sx/n,my=sy/n,vx=0,vy=0,xy=0;
        for(int i=0;i<expression.length;i++)if(expression[i]<1e49&&score[i]<1e49){double x=expression[i]-mx,y=score[i]-my;vx+=x*x;vy+=y*y;xy+=x*y;}
        if(vx==0||vy==0)return new Pearson(Double.NaN,Kind.NA,n);
        double r=xy/Math.sqrt(vx*vy);
        if(r>1e49)return new Pearson(Double.NaN,Kind.NA,n);
        return new Pearson(r,Double.isNaN(r)?Kind.NAN:r==Double.NEGATIVE_INFINITY?Kind.NEGATIVE_INFINITY:Kind.VALUE,n);
    }
    private static void compareValue(double actual,double expected,String name){require(!Double.isNaN(actual)&&!Double.isNaN(expected),"NaN "+name);if(Double.isInfinite(actual)||Double.isInfinite(expected))require(actual==expected,"Original/native "+name+" infinity differs");else require(Math.abs(actual-expected)<=1e-12+1e-10*Math.abs(expected),"Original/native "+name+" differs");}
    private static void probability(double p){require(Double.isFinite(p)&&p>=0&&p<=1,"Unadmitted coefficient probability");}
    private static void require(boolean condition,String cause){if(!condition)throw new IllegalArgumentException(cause);}
}
