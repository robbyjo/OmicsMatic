/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.network;

import java.util.*;
import jdistlib.Normal;

/** Native components qualified against WGCNA 1.72-1. No q-value or network-fit substitution. */
public final class Wgcna1721 {
    private Wgcna1721() { }
    public enum Ties { AVERAGE, MIN, MAX, FIRST, LAST }
    public enum Missing { KEEP, FIRST, LAST }
    public enum Method { RANK, SCALE, ALL }
    public record RankResult(List<String> columns,double[][] values) { }

    /** Original rankPvalue with calculateQvalue=FALSE. NaN input denotes R NA/NaN. */
    public static RankResult rankPvalue(double[][] scores,double[] suppliedWeights,Ties ties,Missing missing,Method method) {
        int p=shape(scores);int n=scores.length;
        Objects.requireNonNull(ties);Objects.requireNonNull(missing);Objects.requireNonNull(method);
        double[] w=new double[p];Arrays.fill(w,1);
        if(suppliedWeights!=null) {
            if(suppliedWeights.length!=p)throw new IllegalArgumentException("Weights must match score columns");
            double total=0;for(double value:suppliedWeights){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Native weights require finite nonnegative values");total+=value;}
            if(!Double.isFinite(total))throw new IllegalArgumentException("Native weight sum overflow");
            for(int j=0;j<p;j++)w[j]=total==1?suppliedWeights[j]:suppliedWeights[j]/total;
        }
        List<String> columns=new ArrayList<>();
        if(method!=Method.SCALE)columns.addAll(List.of("pValueExtreme","pValueLow","pValueHigh"));
        if(method!=Method.RANK)columns.addAll(List.of("pValueExtremeScale","pValueLowScale","pValueHighScale"));
        double[][] result=new double[n][columns.size()];
        if(method!=Method.SCALE) {
            double[] low=new double[n],high=new double[n],expected=new double[n],variance=new double[n];
            for(int j=0;j<p;j++) {
                double[] x=column(scores,j,1),neg=column(scores,j,-1);
                double[] a=percentiles(x,ties,missing),b=percentiles(neg,ties,missing);
                for(int i=0;i<n;i++) {
                    double present=Double.isNaN(x[i])?0:1;
                    double weighted=present*w[j];
                    // R rowSums(...,na.rm=TRUE) omits NA even after weighting.
                    expected[i]+=omit(weighted)*.5;variance[i]+=omit(weighted*weighted)/12;
                    low[i]+=omit(a[i]*w[j]);high[i]+=omit(b[i]*w[j]);
                }
            }
            for(int i=0;i<n;i++){double a=normal((low[i]-expected[i])/Math.sqrt(variance[i])),b=normal((high[i]-expected[i])/Math.sqrt(variance[i]));result[i][0]=Math.min(a,b);result[i][1]=a;result[i][2]=b;}
        }
        if(method!=Method.RANK) {
            double[] low=new double[n],high=new double[n],variance=new double[n];
            for(int j=0;j<p;j++) {
                double[] x=column(scores,j,1),a=scale(x),b=scale(column(scores,j,-1));
                for(int i=0;i<n;i++) {double present=(Double.isNaN(x[i])?0:1)*w[j];variance[i]+=present*present;low[i]+=omit(a[i]*w[j]);high[i]+=omit(b[i]*w[j]);}
            }
            int offset=method==Method.ALL?3:0;
            for(int i=0;i<n;i++){double z=low[i]/Math.sqrt(variance[i]);result[i][offset]=2*normal(-Math.abs(z));result[i][offset+1]=normal(z);result[i][offset+2]=normal(high[i]/Math.sqrt(variance[i]));}
        }
        return new RankResult(List.copyOf(columns),result);
    }
    private static double[] percentiles(double[] x,Ties ties,Missing missing) {
        Integer[] order=new Integer[x.length];int count=0,absent=0;
        for(int i=0;i<x.length;i++)if(!Double.isNaN(x[i]))order[count++]=i;else absent++;
        Arrays.sort(order,0,count,(a,b)->x[a]==x[b]?0:Double.compare(x[a],x[b]));
        double[] rank=new double[x.length];Arrays.fill(rank,Double.NaN);int offset=missing==Missing.FIRST?absent:0;
        for(int a=0;a<count;) {
            int b=a+1;while(b<count&&x[order[b]]==x[order[a]])b++;
            for(int k=a;k<b;k++)rank[order[k]]=offset+switch(ties){case AVERAGE->(a+1+b)/2.;case MIN->a+1.;case MAX->(double)b;case FIRST->k+1.;case LAST->a+b-k;};a=b;
        }
        if(missing!=Missing.KEEP){int k=missing==Missing.FIRST?1:count+1;for(int i=0;i<x.length;i++)if(Double.isNaN(x[i]))rank[i]=k++;}
        double maximum=Double.NEGATIVE_INFINITY;for(double value:rank)if(!Double.isNaN(value))maximum=Math.max(maximum,value);
        for(int i=0;i<x.length;i++)rank[i]=(rank[i]-.5)/maximum;
        return rank;
    }
    private static double[] scale(double[] x) {
        double mean=0;int n=0;for(double value:x)if(!Double.isNaN(value)){mean+=value;n++;}mean/=n;
        // R mean.default corrects finite means for accumulated rounding.
        if(Double.isFinite(mean)){double correction=0;for(double value:x)if(!Double.isNaN(value))correction+=value-mean;mean+=correction/n;}
        double[] result=new double[x.length];double sum=0;int present=0;
        for(int i=0;i<x.length;i++){result[i]=x[i]-mean;if(!Double.isNaN(result[i])){sum+=result[i]*result[i];present++;}}
        double sd=Math.sqrt(sum/Math.max(1,present-1));for(int i=0;i<result.length;i++)result[i]/=sd;return result;
    }
    /** Original column sums, diagonal removal, small-module rule and scaleByMax missingness. */
    public static double[][] intramodularConnectivity(double[][] adjacency,String[] colors,boolean scaleByMax) {
        int p=shape(adjacency),n=adjacency.length;if(n!=p||colors==null||colors.length!=n)throw new IllegalArgumentException("Require square adjacency and one module per node");
        Map<String,List<Integer>> modules=new LinkedHashMap<>();for(int i=0;i<n;i++){if(colors[i]==null||colors[i].isEmpty())throw new IllegalArgumentException("Missing module identity");modules.computeIfAbsent(colors[i],k->new ArrayList<>()).add(i);}
        double[][] result=new double[n][4];
        for(var module:modules.values()) {
            double maximum=Double.NEGATIVE_INFINITY;
            for(int j:module){double within=0;if(module.size()>=3)for(int i:module)if(i!=j)within+=omit(adjacency[i][j]);result[j][1]=within;maximum=Math.max(maximum,within);}
            if(scaleByMax&&module.size()>=3)for(int j:module)result[j][1]/=maximum;
        }
        for(int j=0;j<n;j++){double total=0;for(int i=0;i<n;i++)if(i!=j)total+=omit(adjacency[i][j]);result[j][0]=total;result[j][2]=scaleByMax?Double.NaN:total-result[j][1];result[j][3]=result[j][1]-result[j][2];}
        return result;
    }
    private static int shape(double[][] x){if(x==null||x.length<2||x[0]==null||x[0].length<1)throw new IllegalArgumentException("Require at least two rows and one column");int p=x[0].length;for(double[] row:x)if(row==null||row.length!=p)throw new IllegalArgumentException("Ragged matrix");return p;}
    private static double[] column(double[][] x,int j,double sign){double[] a=new double[x.length];for(int i=0;i<a.length;i++)a[i]=sign*x[i][j];return a;}
    private static double omit(double x){return Double.isNaN(x)?0:x;}
    private static double normal(double x){return Double.isNaN(x)?Double.NaN:Normal.cumulative(x,0,1,true,false);}
}
