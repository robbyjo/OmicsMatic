/* Copyright (C) 2026 JLinAlg contributors
 * Based on LDSC algorithms, Copyright (C) 2014-2015 Brendan Bulik-Sullivan
 * and Hilary Finucane. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.Arrays;
import java.util.List;
import jdistlib.T;
import static org.jlinalg.xwas.LdscHeritability.*;

/** Prepared observed-scale partitioned Hsq, selected CBIIT 3.0.2 old_weights=True
 * path used by its partitioned CLI. No iterative or two-step weight substitution.
 * The caller declares the complete QC-filtered genome order, annotation columns,
 * sample sizes and reference annotation counts. No rows/columns are removed.
 */
public final class StratifiedLdsc {
    public static final int MAXIMUM_ANNOTATIONS=256;
    public static final long MAXIMUM_DESIGN_CELLS=8_000_000,MAXIMUM_BLOCK_CELLS=8_000_000,
            MAXIMUM_GRAM_OPERATIONS=500_000_000;
    /** Axes of coefficient/deletion/covariance arrays are annotations, then intercept.
     * For a fixed intercept, its SE is null and its delete values equal the fixed value.
     * Allocation M_j*tau_j is not overlapping-category heritability; use overlap(). */
    public record Result(int rows,int blocks,int annotations,double meanSampleSize,
            double[] annotationVariants,double initialH2,double weightingH2,double[] weights,
            int[] separators,double[] estimates,double[][] deleteValues,double[][] covariance,
            double[] allocations,double[][] allocationCovariance,double totalH2,double totalStandardError,
            double[] proportions,double[] proportionJackknifeEstimates,double[][] proportionCovariance,
            double[] snpProportions,double[] enrichments,Double interceptStandardError,
            double meanChiSquare,double lambdaGc,Double ratio,Double ratioStandardError,List<String> warnings) {
        public Result {
            annotationVariants=annotationVariants.clone();weights=weights.clone();separators=separators.clone();
            estimates=estimates.clone();deleteValues=copy(deleteValues);covariance=copy(covariance);
            allocations=allocations.clone();allocationCovariance=copy(allocationCovariance);
            proportions=proportions.clone();proportionJackknifeEstimates=proportionJackknifeEstimates.clone();
            proportionCovariance=copy(proportionCovariance);snpProportions=snpProportions.clone();
            enrichments=enrichments.clone();warnings=List.copyOf(warnings);
        }
        @Override public double[] annotationVariants(){return annotationVariants.clone();}
        @Override public double[] weights(){return weights.clone();}
        @Override public int[] separators(){return separators.clone();}
        @Override public double[] estimates(){return estimates.clone();}
        @Override public double[][] deleteValues(){return copy(deleteValues);}
        @Override public double[][] covariance(){return copy(covariance);}
        @Override public double[] allocations(){return allocations.clone();}
        @Override public double[][] allocationCovariance(){return copy(allocationCovariance);}
        @Override public double[] proportions(){return proportions.clone();}
        @Override public double[] proportionJackknifeEstimates(){return proportionJackknifeEstimates.clone();}
        @Override public double[][] proportionCovariance(){return copy(proportionCovariance);}
        @Override public double[] snpProportions(){return snpProportions.clone();}
        @Override public double[] enrichments(){return enrichments.clone();}
    }
    /** Prepared overlapping-annotation summary. Null enrichment P means the source
     * contrast has zero SE, e.g. the all-variants baseline. df is blocks, as upstream. */
    public record Overlap(double[] snpProportions,double[] h2Proportions,double[] h2ProportionStandardErrors,
            double[] enrichments,double[] enrichmentStandardErrors,Double[] enrichmentPValues,
            double[] enrichmentLogPValues,double[] coefficients,double[] coefficientStandardErrors,
            double[] coefficientZScores,double[] contrastEstimates,double[] contrastStandardErrors) {
        public Overlap {
            snpProportions=snpProportions.clone();h2Proportions=h2Proportions.clone();
            h2ProportionStandardErrors=h2ProportionStandardErrors.clone();enrichments=enrichments.clone();
            enrichmentStandardErrors=enrichmentStandardErrors.clone();enrichmentPValues=enrichmentPValues.clone();
            enrichmentLogPValues=enrichmentLogPValues.clone();coefficients=coefficients.clone();
            coefficientStandardErrors=coefficientStandardErrors.clone();coefficientZScores=coefficientZScores.clone();
            contrastEstimates=contrastEstimates.clone();contrastStandardErrors=contrastStandardErrors.clone();
        }
        @Override public double[] snpProportions(){return snpProportions.clone();}
        @Override public double[] h2Proportions(){return h2Proportions.clone();}
        @Override public double[] h2ProportionStandardErrors(){return h2ProportionStandardErrors.clone();}
        @Override public double[] enrichments(){return enrichments.clone();}
        @Override public double[] enrichmentStandardErrors(){return enrichmentStandardErrors.clone();}
        @Override public Double[] enrichmentPValues(){return enrichmentPValues.clone();}
        @Override public double[] enrichmentLogPValues(){return enrichmentLogPValues.clone();}
        @Override public double[] coefficients(){return coefficients.clone();}
        @Override public double[] coefficientStandardErrors(){return coefficientStandardErrors.clone();}
        @Override public double[] coefficientZScores(){return coefficientZScores.clone();}
        @Override public double[] contrastEstimates(){return contrastEstimates.clone();}
        @Override public double[] contrastStandardErrors(){return contrastStandardErrors.clone();}
    }
    private StratifiedLdsc(){ }
    private static double[][] copy(double[][] a){return Arrays.stream(a).map(double[]::clone).toArray(double[][]::new);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalArgumentException(why);}

    /** Fixed initial weights, full/delete joint weighted fits and original ratio
     * jackknife. A null intercept estimates it; a finite value fixes it explicitly.
     * M is per-annotation reference count, never inferred from the regression rows.
     * Scores may be signed; only weight construction floors summed LD/weight LD.
     */
    public static Result fit(double[] chiSquare,double[][] ldScores,double[] weightLdScore,
            double[] sampleSize,double[] annotationVariants,int blocks,Double fixedIntercept) {
        require(chiSquare!=null&&ldScores!=null&&weightLdScore!=null&&sampleSize!=null&&annotationVariants!=null,"Complete prepared arrays required");
        int n=chiSquare.length,k=annotationVariants.length,p=k+(fixedIntercept==null?1:0);
        require(k>=2&&k<=MAXIMUM_ANNOTATIONS&&n>=p+1&&n<=MAXIMUM_ROWS&&ldScores.length==n&&weightLdScore.length==n&&sampleSize.length==n,"Matching complete rows and 2..256 annotation columns required");
        require(blocks>=2&&blocks<=Math.min(n,MAXIMUM_BLOCKS)&&(fixedIntercept==null||Double.isFinite(fixedIntercept)),"Declare 2..10000 blocks and a finite fixed intercept or null");
        long width=(long)p*(p+1)/2+p;
        require((long)n*k<=MAXIMUM_DESIGN_CELLS&&(long)blocks*width<=MAXIMUM_BLOCK_CELLS&&(long)n*p*p+(long)blocks*p*p*p<=MAXIMUM_GRAM_OPERATIONS,"Complete partitioned design exceeds declared cell/operation capacity; no truncation");
        for(double[] row:ldScores)require(row!=null&&row.length==k,"Every row must contain all declared annotation scores");
        double[] y=chiSquare.clone(),ns=sampleSize.clone(),wld=weightLdScore.clone(),m=annotationVariants.clone();double[][] ld=copy(ldScores);
        Sum sm=new Sum(),sn=new Sum(),sy=new Sum(),snx=new Sum();double[] totalLd=new double[n];
        for(double count:m){require(Double.isFinite(count)&&count>0,"Positive finite reference annotation counts required");sm.add(count);}double mtot=positive(sm.value());
        for(int i=0;i<n;i++) {
            require(ld[i].length==k&&Double.isFinite(y[i])&&y[i]>=0&&Double.isFinite(wld[i])&&Double.isFinite(ns[i])&&ns[i]>0,"Finite complete chi-square>=0, LD, weight-LD and positive N rows required; no filtering");
            Sum sx=new Sum();for(double score:ld[i])sx.add(finite(score));totalLd[i]=sx.value();
            sn.add(ns[i]/n);sy.add(y[i]/n);snx.add(finite(ns[i]*totalLd[i])/n);
        }
        double nbar=positive(sn.value()),mean=finite(sy.value()),int0=fixedIntercept==null?1:fixedIntercept;
        double initial=finite(mtot*(mean-int0)/snx.value()),bounded=Math.max(0,Math.min(1,initial));
        double[] w=new double[n],scales=new double[p];
        for(int i=0;i<n;i++) {
            double expected=finite(int0+bounded*ns[i]/mtot*Math.max(1,totalLd[i]));w[i]=positive(1/(2*expected*expected)/Math.max(1,wld[i]));
            for(int j=0;j<k;j++){ld[i][j]=finite(ns[i]*ld[i][j]/nbar);scales[j]=Math.max(scales[j],Math.abs(ld[i][j]));}
        }
        for(int j=0;j<k;j++)positive(scales[j]);if(fixedIntercept==null)scales[k]=1;
        int[] boundaries=separators(n,blocks);int cells=(int)width;double[][] pieces=new double[blocks][cells];double maxWeight=Arrays.stream(w).max().orElseThrow();
        for(int block=0;block<blocks;block++) {
            double[] correction=new double[cells],x=new double[p];
            for(int i=boundaries[block];i<boundaries[block+1];i++) {
                for(int j=0;j<k;j++)x[j]=ld[i][j]/scales[j];if(fixedIntercept==null)x[k]=1;
                double wi=positive(w[i]/maxWeight),response=fixedIntercept==null?y[i]:finite(y[i]-fixedIntercept);int cell=0;
                for(int j=0;j<p;j++)for(int l=0;l<=j;l++)add(pieces[block],correction,cell++,finite(wi*x[j]*x[l]));
                for(int j=0;j<p;j++)add(pieces[block],correction,cell++,finite(wi*x[j]*response));
            }
        }
        double[] all=sumBlocks(pieces,-1);double[] fit=solve(all,p,scales);double[][] deletes=new double[blocks][k+1];
        for(int block=0;block<blocks;block++) {
            require(n-(boundaries[block+1]-boundaries[block])>=p,"Delete population has fewer rows than joint parameters");
            double[] remaining=all.clone();for(int cell=0;cell<remaining.length;cell++)remaining[cell]=finite(all[cell]-pieces[block][cell]);
            double[] deleted=solve(remaining,p,scales);
            for(int j=0;j<k;j++)deletes[block][j]=finite(deleted[j]/nbar);deletes[block][k]=fixedIntercept==null?deleted[k]:fixedIntercept;
        }
        double[] estimates=new double[k+1];for(int j=0;j<k;j++)estimates[j]=finite(fit[j]/nbar);estimates[k]=fixedIntercept==null?fit[k]:fixedIntercept;
        double[][] cov=covarianceOf(deletes),allocationCov=new double[k][k];double[] cat=new double[k];Sum sumCat=new Sum(),sumVar=new Sum();
        for(int j=0;j<k;j++) {cat[j]=finite(m[j]*estimates[j]);sumCat.add(cat[j]);for(int l=0;l<k;l++){allocationCov[j][l]=finite(m[j]*m[l]*cov[j][l]);sumVar.add(allocationCov[j][l]);}}
        double total=finite(sumCat.value());require(total!=0,"Zero total heritability makes original proportions undefined");double totalSe=finite(Math.sqrt(sumVar.value()));
        double[] prop=new double[k],propJ=new double[k],mprop=new double[k],enrichment=new double[k];double[][] ratios=new double[blocks][k];
        for(int j=0;j<k;j++){prop[j]=finite(cat[j]/total);mprop[j]=positive(m[j]/mtot);enrichment[j]=finite(estimates[j]/(total/mtot));}
        for(int block=0;block<blocks;block++) {
            Sum denom=new Sum();for(int j=0;j<k;j++)denom.add(finite(m[j]*deletes[block][j]));require(denom.value()!=0,"Zero delete total heritability makes original ratio jackknife undefined");
            for(int j=0;j<k;j++)ratios[block][j]=finite(m[j]*deletes[block][j]/denom.value());
        }
        double[][] propCov=covarianceOf(ratios);for(int j=0;j<k;j++){Sum sum=new Sum();for(double[] row:ratios)sum.add(row[j]/blocks);propJ[j]=finite(blocks*prop[j]-(blocks-1)*sum.value());}
        Double intSe=fixedIntercept==null?finite(Math.sqrt(cov[k][k])):null,ratio=fixedIntercept==null&&mean>1?finite((estimates[k]-1)/(mean-1)):null,ratioSe=ratio==null?null:finite(intSe/(mean-1));
        double[] ordered=y.clone();Arrays.sort(ordered);double median=n%2==0?ordered[n/2-1]/2+ordered[n/2]/2:ordered[n/2];
        var warnings=new java.util.ArrayList<String>();warnings.add("Prepared partitioned Hsq uses the original fixed initial weights (old_weights=True), not unpartitioned IRWLS or a two-step fit. No SNP/annotation removal, munging, LD/annotation generation, liability conversion or multiple-testing family is inferred.");
        warnings.add("M_j*tau_j and the unadjusted proportions/enrichments are coefficient allocations. For overlapping annotations use the explicit overlap summary and the unique reference-universe M; sum(M_j) double-counts shared variants.");
        if(initial!=bounded)warnings.add("Initial weighting h2 was clamped to [0,1] following the source; final coefficients/heritability were not clamped.");
        if(total<0||total>1||Arrays.stream(estimates,0,k).anyMatch(v->v<0))warnings.add("Negative coefficients or total h2 outside [0,1] are retained. Review the complete design, reference population and model before interpretation.");
        return new Result(n,blocks,k,nbar,m,initial,bounded,w,boundaries,estimates,deletes,cov,cat,allocationCov,total,totalSe,prop,propJ,propCov,mprop,enrichment,intSe,mean,finite(median/.4549),ratio,ratioSe,warnings);
    }
    /** Overlap matrix is sum of annotation-product values in the same explicitly
     * declared reference universe. Binary count invariants are required here;
     * continuous-annotation overlap summaries need a separately specified model.
     * Enrichment P uses the original two-sided Student t tail with df=blocks.
     */
    public static Overlap overlap(Result fit,double[][] overlap,double universeVariants) {
        require(fit!=null&&overlap!=null,"Fit and complete overlap matrix required");int k=fit.annotations();require(overlap.length==k&&Double.isFinite(universeVariants)&&universeVariants>0,"Positive finite unique-universe M and matching overlap matrix required");
        double[] m=fit.annotationVariants(),est=fit.estimates(),prop=fit.proportions();double[][] ov=copy(overlap),cov=fit.covariance(),propCov=fit.proportionCovariance();
        for(int j=0;j<k;j++)require(ov[j].length==k&&m[j]<=universeVariants,"Binary annotation counts must fit the declared unique universe");
        double[] mp=new double[k],hp=new double[k],hpSe=new double[k],enrich=new double[k],enrichSe=new double[k],logp=new double[k],coef=Arrays.copyOf(est,k),coefSe=new double[k],z=new double[k],diff=new double[k],diffSe=new double[k];Double[] pv=new Double[k];
        for(int j=0;j<k;j++) {
            double[] map=new double[k],contrast=new double[k];
            for(int l=0;l<k;l++) {
                double value=ov[j][l];require(Double.isFinite(value)&&value>=0&&value<=Math.min(m[j],m[l])&&value==ov[l][j]&&(j!=l||value==m[j])&&value>=Math.max(0,m[j]+m[l]-universeVariants),"Require finite symmetric binary overlap counts, exact M diagonal and valid universe bounds");
                map[l]=finite(value/m[l]);contrast[l]=m[j]==universeVariants?0:finite(value/m[j]-(m[l]-value)/(universeVariants-m[j]));
            }
            mp[j]=positive(m[j]/universeVariants);hp[j]=dot(map,prop);hpSe[j]=finite(Math.sqrt(Math.max(0,quadratic(map,propCov))));enrich[j]=finite(hp[j]/mp[j]);enrichSe[j]=finite(hpSe[j]/mp[j]);
            diff[j]=dot(contrast,coef);diffSe[j]=finite(Math.sqrt(quadratic(contrast,cov)));
            if(diffSe[j]==0){pv[j]=null;logp[j]=Double.NaN;}else{double statistic=finite(Math.abs(diff[j]/diffSe[j]));pv[j]=finite(Math.min(1,2*T.cumulative(statistic,fit.blocks(),false,false)));logp[j]=Math.min(0,Math.log(2)+T.cumulative(statistic,fit.blocks(),false,true));}
            coefSe[j]=finite(Math.sqrt(cov[j][j]));require(coefSe[j]!=0,"Original overlapping coefficient z diagnostic is undefined with zero SE; no overlap summary admitted");z[j]=finite(coef[j]/coefSe[j]);
        }
        return new Overlap(mp,hp,hpSe,enrich,enrichSe,pv,logp,coef,coefSe,z,diff,diffSe);
    }
    private static void add(double[] a,double[] c,int i,double value){double adjusted=value-c[i],next=finite(a[i]+adjusted);c[i]=(next-a[i])-adjusted;a[i]=next;}
    private static double[] sumBlocks(double[][] blocks,int excluded){double[] sum=new double[blocks[0].length],c=new double[sum.length];for(int b=0;b<blocks.length;b++)if(b!=excluded)for(int j=0;j<sum.length;j++)add(sum,c,j,blocks[b][j]);return sum;}
    private static double[] solve(double[] packed,int p,double[] scale) {
        double[][] a=new double[p][p];int cell=0;for(int j=0;j<p;j++)for(int l=0;l<=j;l++)a[j][l]=a[l][j]=packed[cell++];
        double[] diag=new double[p];for(int j=0;j<p;j++)diag[j]=positive(Math.sqrt(a[j][j]));
        // Standardize columns before a Cholesky solve. Never use a pseudoinverse
        // or drop a dependent annotation, including in an individual delete fit.
        double[][] lower=new double[p][p];double[] rhs=new double[p];
        for(int j=0;j<p;j++){rhs[j]=finite(packed[cell++]/diag[j]);for(int l=0;l<=j;l++){Sum sum=new Sum();sum.add(finite(a[j][l]/diag[j]/diag[l]));for(int q=0;q<l;q++)sum.add(-lower[j][q]*lower[l][q]);double value=sum.value();if(j==l){if(!(value>1e-12))throw new ArithmeticException("Full/delete joint design rank deficient or below 1e-12 standardized Cholesky pivot; retain all annotations and review design");lower[j][l]=Math.sqrt(value);}else lower[j][l]=finite(value/lower[l][l]);}}
        double[] v=new double[p],x=new double[p];for(int j=0;j<p;j++){Sum s=new Sum();s.add(rhs[j]);for(int l=0;l<j;l++)s.add(-lower[j][l]*v[l]);v[j]=finite(s.value()/lower[j][j]);}
        for(int j=p-1;j>=0;j--){Sum s=new Sum();s.add(v[j]);for(int l=j+1;l<p;l++)s.add(-lower[l][j]*x[l]);x[j]=finite(s.value()/lower[j][j]);}
        for(int j=0;j<p;j++)x[j]=finite(x[j]/diag[j]/scale[j]);return x;
    }
    private static double[][] covarianceOf(double[][] deletes) {
        int b=deletes.length,p=deletes[0].length;double[] mean=new double[p];for(int j=0;j<p;j++){Sum s=new Sum();for(double[] row:deletes)s.add(row[j]/b);mean[j]=s.value();}
        double[][] cov=new double[p][p];for(int j=0;j<p;j++)for(int l=0;l<=j;l++){Sum s=new Sum();for(double[] row:deletes)s.add(finite((row[j]-mean[j])*(row[l]-mean[l])));cov[j][l]=cov[l][j]=finite(s.value()*(b-1.0)/b);}return cov;
    }
    private static double dot(double[] x,double[] y){Sum s=new Sum();for(int j=0;j<x.length;j++)s.add(finite(x[j]*y[j]));return s.value();}
    private static double quadratic(double[] x,double[][] cov){Sum s=new Sum();for(int j=0;j<x.length;j++)for(int l=0;l<x.length;l++)s.add(finite(x[j]*cov[j][l]*x[l]));return s.value();}
}
