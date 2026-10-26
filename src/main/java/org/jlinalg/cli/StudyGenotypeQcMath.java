/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/** Independent hard-call pair counts and unrelated-basis genotype PCA. */
final class StudyGenotypeQcMath {
    private StudyGenotypeQcMath() { }
    record Pair(int first,int second,int shared,int hetFirst,int hetSecond,int bothHet,int oppositeHom,int identical,double within,double between) { }
    record Pca(double[] eigenvalues,double[] fractions,double[][] scores,double[][] weights,double[] means,double[] scales,int[] markers,int[] anchors,double maxResidual) { }
    static List<Pair> pairs(List<byte[]> variants,int samples) {
        int words=(variants.size()+63)/64;long[][] ref=new long[samples][words],het=new long[samples][words],alt=new long[samples][words];
        for(int k=0;k<variants.size();k++){byte[] row=variants.get(k);if(row.length!=samples)throw new IllegalArgumentException("Genotype dimensions differ");for(int i=0;i<samples;i++){long mask=1L<<(k%64);switch(row[i]){case 0->ref[i][k/64]|=mask;case 1->het[i][k/64]|=mask;case 2->alt[i][k/64]|=mask;case -1->{ }default->throw new IllegalArgumentException("Only hard calls 0/1/2 and missing -1 are supported");}}}
        var result=new ArrayList<Pair>();for(int i=0;i<samples;i++)for(int j=i+1;j<samples;j++) {
            int shared=0,hi=0,hj=0,hh=0,opposite=0,identical=0;
            for(int k=0;k<words;k++){long ci=ref[i][k]|het[i][k]|alt[i][k],cj=ref[j][k]|het[j][k]|alt[j][k];shared+=Long.bitCount(ci&cj);hi+=Long.bitCount(het[i][k]&cj);hj+=Long.bitCount(het[j][k]&ci);hh+=Long.bitCount(het[i][k]&het[j][k]);opposite+=Long.bitCount((ref[i][k]&alt[j][k])|(alt[i][k]&ref[j][k]));identical+=Long.bitCount((ref[i][k]&ref[j][k])|(het[i][k]&het[j][k])|(alt[i][k]&alt[j][k]));}
            double within=hi+hj==0?Double.NaN:(hh-2.*opposite)/(hi+hj),between=Math.min(hi,hj)==0?Double.NaN:.5-(hi+hj-2.*hh+4.*opposite)/(4.*Math.min(hi,hj));
            result.add(new Pair(i,j,shared,hi,hj,hh,opposite,identical,within,between));
        }return result;
    }
    static int[] unrelated(List<Pair> pairs,List<String> ids,double[] callRate,double cutoff) {
        int n=ids.size();boolean[][] adjacent=new boolean[n][n];for(Pair pair:pairs)if(pair.between()>cutoff)adjacent[pair.first()][pair.second()]=adjacent[pair.second()][pair.first()]=true;
        Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;Arrays.sort(order,Comparator.<Integer>comparingDouble(i->callRate[i]).reversed().thenComparing(ids::get));
        var keep=new ArrayList<Integer>();for(int i:order)if(keep.stream().noneMatch(j->adjacent[i][j]))keep.add(i);return keep.stream().sorted().mapToInt(Integer::intValue).toArray();
    }
    static double rSquared(byte[] a,byte[] b,int minimumSamples) {
        int n=0;double sa=0,sb=0,saa=0,sbb=0,sab=0;for(int i=0;i<a.length;i++)if(a[i]>=0&&b[i]>=0){n++;sa+=a[i];sb+=b[i];saa+=a[i]*a[i];sbb+=b[i]*b[i];sab+=a[i]*b[i];}
        if(n<minimumSamples)return Double.NaN;double va=saa-sa*sa/n,vb=sbb-sb*sb/n;if(!(va>0)||!(vb>0))return Double.NaN;double covariance=sab-sa*sb/n;return Math.max(0,Math.min(1,covariance*covariance/(va*vb)));
    }
    static Pca pca(List<byte[]> variants,int samples,int[] anchors,int components) {
        if(components<1||components>=anchors.length)throw new IllegalArgumentException("PCA needs more unrelated basis samples than components");
        var retained=new ArrayList<Integer>();var meanList=new ArrayList<Double>();var scaleList=new ArrayList<Double>();
        for(int j=0;j<variants.size();j++){int count=0,sum=0;for(int i:anchors)if(variants.get(j)[i]>=0){sum+=variants.get(j)[i];count++;}double mean=count==0?Double.NaN:sum/(double)count,variance=mean*(1-mean/2);if(variance>1e-12){retained.add(j);meanList.add(mean);scaleList.add(Math.sqrt(variance));}}
        int m=retained.size(),n=anchors.length;if(m<components)throw new IllegalArgumentException("Insufficient polymorphic unrelated-basis markers");double[][] z=new double[m][samples];double[] means=new double[m],scales=new double[m];
        for(int j=0;j<m;j++){means[j]=meanList.get(j);scales[j]=scaleList.get(j);byte[] calls=variants.get(retained.get(j));for(int i=0;i<samples;i++)z[j][i]=calls[i]<0?0:(calls[i]-means[j])/scales[j];}
        double[] gram=new double[Math.multiplyExact(n,n)];for(double[] row:z)for(int i=0;i<n;i++)for(int j=0;j<=i;j++)gram[i*n+j]+=row[anchors[i]]*row[anchors[j]]/m;
        for(int i=0;i<n;i++)for(int j=0;j<i;j++)gram[j*n+i]=gram[i*n+j];double[] values,vectors;
        try(var context=BackendContext.select(BackendPolicy.CPU)){var decomposition=context.backend().dsyev(gram.clone(),n);values=decomposition.eigenvalues();vectors=decomposition.eigenvectors();}
        Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;Arrays.sort(order,(a,b)->Double.compare(values[b],values[a]));double max=values[order[0]],trace=0;for(double v:values){if(!Double.isFinite(v)||v < -1e-8*Math.max(1,max))throw new IllegalArgumentException("Invalid genotype PCA spectrum");trace+=Math.max(0,v);}
        if(!(values[order[components-1]]>1e-10*Math.max(1,max)))throw new IllegalArgumentException("Registered PCA rank is unavailable");
        if(components<n&&values[order[components-1]]-values[order[components]]<=1e-8*Math.max(1,max))throw new IllegalArgumentException("Requested PCA boundary splits a numerically tied eigenspace");
        double[][] weights=new double[m][components],scores=new double[samples][components];double[] eigenvalues=new double[components],fractions=new double[components];double residual=0;
        for(int k=0;k<components;k++){int col=order[k];double lambda=values[col];eigenvalues[k]=lambda;fractions[k]=lambda/trace;
            for(int j=0;j<m;j++)for(int i=0;i<n;i++)weights[j][k]+=z[j][anchors[i]]*vectors[i*n+col]/(m*lambda);
            int pivot=0;for(int j=1;j<m;j++)if(Math.abs(weights[j][k])>Math.abs(weights[pivot][k]))pivot=j;double sign=weights[pivot][k]<0?-1:1;
            for(int j=0;j<m;j++){weights[j][k]*=sign;for(int i=0;i<samples;i++)scores[i][k]+=z[j][i]*weights[j][k];}
            for(int i=0;i<n;i++){double product=0;for(int j=0;j<n;j++)product+=gram[i*n+j]*scores[anchors[j]][k];residual=Math.max(residual,Math.abs(product-lambda*scores[anchors[i]][k]));}
        }
        if(!Double.isFinite(residual)||residual>1e-7*Math.max(1,max)||Arrays.stream(scores).flatMapToDouble(Arrays::stream).anyMatch(v->!Double.isFinite(v)))throw new IllegalArgumentException("Genotype PCA numerical verification failed");
        return new Pca(eigenvalues,fractions,scores,weights,means,scales,retained.stream().mapToInt(Integer::intValue).toArray(),anchors.clone(),residual);
    }
}
