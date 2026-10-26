/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.Arrays;
import jdistlib.T;
import org.jlinalg.genetics.GeneticCovarianceValidation;

/** Observed-scale S-LDSC with binary or continuous annotations and explicit fixed initial weights. */
public final class StratifiedLdScoreRegression {
    private StratifiedLdScoreRegression() { }

    /** Annotation zero is the all-reference-SNP baseline; other columns may overlap. */
    public record Result(double[] coefficients, double[] coefficientCovariance,
            double intercept, double interceptStandardError, double totalHeritability,
            double totalStandardError, double[] categoryHeritability, double[] proportions,
            double[] proportionStandardErrors, double[] enrichments, double[] enrichmentStandardErrors,
            double[] contrasts, double[] contrastStandardErrors, double[] probabilities,
            double[][] deleteCoefficients, double[] deleteIntercepts, double[] deleteTotals,
            double[] weights, double meanChiSquare, double aggregateWeightHeritability) {
        public Result {
            coefficients=coefficients.clone();coefficientCovariance=coefficientCovariance.clone();
            categoryHeritability=categoryHeritability.clone();proportions=proportions.clone();
            proportionStandardErrors=proportionStandardErrors.clone();enrichments=enrichments.clone();
            enrichmentStandardErrors=enrichmentStandardErrors.clone();contrasts=contrasts.clone();
            contrastStandardErrors=contrastStandardErrors.clone();probabilities=probabilities.clone();
            deleteCoefficients=copy(deleteCoefficients);deleteIntercepts=deleteIntercepts.clone();
            deleteTotals=deleteTotals.clone();weights=weights.clone();
        }
        @Override public double[] coefficients(){return coefficients.clone();}
        @Override public double[] coefficientCovariance(){return coefficientCovariance.clone();}
        @Override public double[] categoryHeritability(){return categoryHeritability.clone();}
        @Override public double[] proportions(){return proportions.clone();}
        @Override public double[] proportionStandardErrors(){return proportionStandardErrors.clone();}
        @Override public double[] enrichments(){return enrichments.clone();}
        @Override public double[] enrichmentStandardErrors(){return enrichmentStandardErrors.clone();}
        @Override public double[] contrasts(){return contrasts.clone();}
        @Override public double[] contrastStandardErrors(){return contrastStandardErrors.clone();}
        @Override public double[] probabilities(){return probabilities.clone();}
        @Override public double[][] deleteCoefficients(){return copy(deleteCoefficients);}
        @Override public double[] deleteIntercepts(){return deleteIntercepts.clone();}
        @Override public double[] deleteTotals(){return deleteTotals.clone();}
        @Override public double[] weights(){return weights.clone();}
    }
    private static double[][] copy(double[][] x){return Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);}

    /**
     * Rows must already be QC-filtered and genome-ordered. Equal-count contiguous blocks
     * are numbered from zero. Free-intercept and intercept-one fits use LDSC's
     * partitioned old_weights convention; deletes retain those same whole-data weights.
     */
    public static Result fit(double[] chiSquare,double[][] ld,double[] weightLd,double[] sampleSize,
            int[] blocks,double[] counts,double[][] overlap,boolean freeIntercept,long maximumWork) {
        return fitInternal(chiSquare,ld,weightLd,sampleSize,blocks,counts,overlap,null,freeIntercept,maximumWork);
    }

    /**
     * Mixed annotations use complete reference sums and uncentred cross-products.
     * Continuous columns may be signed or centred. Initial weights depend only
     * on the all-SNP baseline LD/count, avoiding dependence on annotation units.
     * Binary rows retain category-versus-complement t(B) tests; continuous rows
     * test the conditional coefficient with a two-sided normal approximation.
     * All delete fits retain the full-data initial weights.
     */
    public static Result fitMixed(double[] chiSquare,double[][] ld,double[] weightLd,double[] sampleSize,
            int[] blocks,double[] sums,double[][] crossProducts,boolean[] binary,
            boolean freeIntercept,long maximumWork) {
        if(binary==null)throw new IllegalArgumentException("Mixed annotations require explicit binary/continuous kinds");
        return fitInternal(chiSquare,ld,weightLd,sampleSize,blocks,sums,crossProducts,binary.clone(),freeIntercept,maximumWork);
    }
    private static Result fitInternal(double[] chiSquare,double[][] ld,double[] weightLd,double[] sampleSize,
            int[] blocks,double[] counts,double[][] overlap,boolean[] binary,boolean freeIntercept,long maximumWork) {
        int n=chiSquare.length,c=counts.length,d=c+(freeIntercept?1:0);
        if(c<2||c>128||n<d+3||ld.length!=n||weightLd.length!=n||sampleSize.length!=n||blocks.length!=n)
            throw new IllegalArgumentException("S-LDSC needs aligned rows and 2..128 annotation columns");
        if(binary==null)validateMoments(counts,overlap);else validateMixedMoments(counts,overlap,binary);
        SummaryMath.finite(chiSquare);SummaryMath.finite(weightLd);SummaryMath.finite(sampleSize);
        int b=0;double nbar=0,mean=0,meanNx=0,mSum=binary==null?Arrays.stream(counts).sum():counts[0];
        for(int i=0;i<n;i++){
            SummaryMath.finite(ld[i]);
            if(ld[i].length!=c||chiSquare[i]<0||!(sampleSize[i]>0)||blocks[i]<0
                    ||i==0&&blocks[i]!=0||i>0&&blocks[i]!=blocks[i-1]&&blocks[i]!=blocks[i-1]+1)
                throw new IllegalArgumentException("Invalid S-LDSC row or noncontiguous blocks");
            b=Math.max(b,blocks[i]+1);nbar+=sampleSize[i]/n;mean+=chiSquare[i]/n;
            meanNx+=sampleSize[i]*(binary==null?Arrays.stream(ld[i]).sum():ld[i][0])/n;
        }
        if(b<3||b>n)throw new IllegalArgumentException("S-LDSC needs at least three contiguous blocks");
        int[] sizes=new int[b];for(int v:blocks)sizes[v]++;
        if(Arrays.stream(sizes).max().orElseThrow()-Arrays.stream(sizes).min().orElseThrow()>1)
            throw new IllegalArgumentException("S-LDSC jackknife blocks must have equal counts within one row");
        long work=(long)n*d*d+(long)(b+1)*d*d*d;
        if(maximumWork<=0||work>maximumWork)throw new IllegalArgumentException("S-LDSC exceeds maximum_work; no annotation or SNP truncation was applied");
        if(!(meanNx>0)||!Double.isFinite(meanNx)||!Double.isFinite(nbar))
            throw new IllegalArgumentException("Invalid aggregate LD-score scale");
        double aggregate=mSum*(mean-1)/meanNx;
        if(!Double.isFinite(aggregate))throw new IllegalArgumentException("Nonfinite aggregate weighting estimate");
        double weightH2=Math.max(0,Math.min(1,aggregate));double[] weights=new double[n],scales=new double[d];
        for(int i=0;i<n;i++){
            double expected=1+weightH2*sampleSize[i]*Math.max(1,binary==null?Arrays.stream(ld[i]).sum():ld[i][0])/mSum;
            weights[i]=1/(2*expected*expected*Math.max(1,weightLd[i]));
            if(!(weights[i]>0)||!Double.isFinite(weights[i]))throw new IllegalArgumentException("Invalid S-LDSC weight");
            for(int j=0;j<c;j++)scales[j]=Math.max(scales[j],Math.abs(sampleSize[i]/nbar*ld[i][j]));
        }
        if(freeIntercept)scales[c]=1;
        for(double scale:scales)if(!(scale>0)||!Double.isFinite(scale))throw new IllegalArgumentException("Zero or invalid LD-score column");
        double[][] gram=new double[b][d*d],rhs=new double[b][d];double[] x=new double[d];
        for(int i=0;i<n;i++){
            for(int j=0;j<c;j++)x[j]=sampleSize[i]/nbar*ld[i][j]/scales[j];
            if(freeIntercept)x[c]=1;int block=blocks[i];double y=chiSquare[i]-(freeIntercept?0:1);
            for(int j=0;j<d;j++){
                rhs[block][j]=Math.fma(weights[i]*x[j],y,rhs[block][j]);
                for(int k=0;k<=j;k++)gram[block][j*d+k]=Math.fma(weights[i]*x[j],x[k],gram[block][j*d+k]);
            }
        }
        double[] allGram=new double[d*d],allRhs=new double[d];
        for(int k=0;k<b;k++)for(int j=0;j<d;j++){
            allRhs[j]+=rhs[k][j];for(int q=0;q<=j;q++)allGram[j*d+q]+=gram[k][j*d+q];
        }
        double[] full=solve(allGram,allRhs,scales,c,nbar);
        double[][] deleted=new double[b][c];double[] di=new double[b],dt=new double[b];
        for(int k=0;k<b;k++){
            if(n-sizes[k]<=d)throw new IllegalArgumentException("Insufficient rows in S-LDSC block delete");
            double[] g=allGram.clone(),r=allRhs.clone();
            for(int j=0;j<d;j++){r[j]-=rhs[k][j];for(int q=0;q<=j;q++)g[j*d+q]-=gram[k][j*d+q];}
            double[] fit=solve(g,r,scales,c,nbar);System.arraycopy(fit,0,deleted[k],0,c);
            di[k]=freeIntercept?fit[c]:1;dt[k]=SummaryMath.dot(counts,deleted[k]);
        }
        double[] co=Arrays.copyOf(full,c),cov=SummaryMath.jackknifeCovariance(deleted);
        double total=SummaryMath.dot(counts,co),totalSe=Math.sqrt(variance(counts,cov));
        double intercept=freeIntercept?full[c]:1,ise=freeIntercept?se(di):0;
        double[] category=new double[c],prop=new double[c],pse=new double[c],enrich=new double[c],ese=new double[c];
        double[] contrast=new double[c],cse=new double[c],p=new double[c];Arrays.fill(p,Double.NaN);
        for(int j=0;j<c;j++){
            if(binary!=null&&!binary[j]){
                category[j]=prop[j]=pse[j]=enrich[j]=ese[j]=contrast[j]=cse[j]=Double.NaN;
                double coefficientSe=Math.sqrt(cov[j*c+j]);
                if(coefficientSe>0)p[j]=SummaryMath.p(co[j]/coefficientSe);
                continue;
            }
            category[j]=SummaryMath.dot(overlap[j],co);prop[j]=total==0?Double.NaN:category[j]/total;
            double[] ratios=new double[b];boolean valid=Double.isFinite(prop[j]);
            for(int k=0;k<b;k++){ratios[k]=SummaryMath.dot(overlap[j],deleted[k])/dt[k];valid&=Double.isFinite(ratios[k]);}
            pse[j]=valid?se(ratios):Double.NaN;double fraction=counts[j]/counts[0];
            enrich[j]=prop[j]/fraction;ese[j]=pse[j]/fraction;
            if(counts[j]==counts[0]){contrast[j]=Double.NaN;cse[j]=Double.NaN;continue;}
            double[] a=new double[c];for(int k=0;k<c;k++)a[k]=overlap[j][k]/counts[j]-(counts[k]-overlap[j][k])/(counts[0]-counts[j]);
            contrast[j]=SummaryMath.dot(a,co);cse[j]=Math.sqrt(variance(a,cov));
            if(cse[j]>0)p[j]=2*T.cumulative(-Math.abs(contrast[j]/cse[j]),b,true,false);
        }
        return new Result(co,cov,intercept,ise,total,totalSe,category,prop,pse,enrich,ese,contrast,cse,p,deleted,di,dt,weights,mean,aggregate);
    }

    /** Population SD on the complete annotation reference, and tau * SD(a) / (h2/M). */
    public record StandardizedEffects(double[] annotationStandardDeviations,double[] effects,
            double[] standardErrors,double[][] deleteEffects) {
        public StandardizedEffects {annotationStandardDeviations=annotationStandardDeviations.clone();effects=effects.clone();standardErrors=standardErrors.clone();deleteEffects=copy(deleteEffects);}
        @Override public double[] annotationStandardDeviations(){return annotationStandardDeviations.clone();}
        @Override public double[] effects(){return effects.clone();}
        @Override public double[] standardErrors(){return standardErrors.clone();}
        @Override public double[][] deleteEffects(){return copy(deleteEffects);}
    }
    public static StandardizedEffects standardizedEffects(Result result,double[] sums,double[][] products) {
        int c=sums.length;double[] coefficients=result.coefficients();double[][] deletes=result.deleteCoefficients();double[] totals=result.deleteTotals();
        if(c!=coefficients.length||products.length!=c||!(sums[0]>0))throw new IllegalArgumentException("Standardized-effect moment dimensions differ");
        double[] sd=new double[c],effects=new double[c],ses=new double[c];double[][] deleteEffects=new double[deletes.length][c];
        Arrays.fill(effects,Double.NaN);Arrays.fill(ses,Double.NaN);for(double[] row:deleteEffects)Arrays.fill(row,Double.NaN);
        for(int j=0;j<c;j++){
            if(products[j].length!=c)throw new IllegalArgumentException("Standardized-effect moment dimensions differ");
            double mean=sums[j]/sums[0],second=products[j][j]/sums[0],variance=Math.fma(-mean,mean,second);
            if(!Double.isFinite(variance)||variance< -1e-12*Math.max(Math.abs(second),mean*mean))throw new IllegalArgumentException("Invalid annotation reference variance");
            sd[j]=Math.sqrt(Math.max(0,variance));if(j==0||sd[j]==0)continue;
            boolean valid=result.totalHeritability()>0&&Double.isFinite(result.totalHeritability());
            effects[j]=valid?coefficients[j]*sd[j]*sums[0]/result.totalHeritability():Double.NaN;
            double[] ratios=new double[deletes.length];
            for(int k=0;k<deletes.length;k++){ratios[k]=totals[k]>0?deletes[k][j]*sd[j]*sums[0]/totals[k]:Double.NaN;deleteEffects[k][j]=ratios[k];valid&=Double.isFinite(ratios[k]);}
            ses[j]=valid?se(ratios):Double.NaN;
        }
        return new StandardizedEffects(sd,effects,ses,deleteEffects);
    }

    /** The same common-reference-SNP universe defines S_j=sum(a_j), Q_jk=sum(a_j*a_k). */
    public static void validateMixedMoments(double[] sums,double[][] products,boolean[] binary){
        SummaryMath.finite(sums);int c=sums.length;
        if(c<2||c>128||binary.length!=c||products.length!=c||!binary[0]||sums[0]<=1||sums[0]!=Math.rint(sums[0])||sums[0]>9e15)throw new IllegalArgumentException("Mixed annotation moments require a first binary all-reference-SNP baseline");
        for(double[] row:products){if(row.length!=c)throw new IllegalArgumentException("Mixed annotation cross-product dimensions differ");SummaryMath.finite(row);}
        double[] matrix=new double[c*c];
        for(int j=0;j<c;j++){
            if(!(products[j][j]>0))throw new IllegalArgumentException("Annotation reference second moments must be positive");
            double scale=Math.sqrt(sums[0])*Math.sqrt(products[j][j]);
            if(Math.abs(products[0][j]-sums[j])>1e-10*Math.max(Math.abs(sums[j]),scale))throw new IllegalArgumentException("Cross-products do not match annotation sums and all-SNP baseline");
            if(binary[j]&&(sums[j]<=0||sums[j]>sums[0]||sums[j]!=Math.rint(sums[j])||products[j][j]!=sums[j]||products[0][j]!=sums[j]))throw new IllegalArgumentException("Invalid binary annotation count/diagonal in mixed model");
            double mean=sums[j]/sums[0],variance=Math.fma(-mean,mean,products[j][j]/sums[0]);
            if(j>0&&!(variance>1e-14*(Math.abs(products[j][j]/sums[0])+mean*mean)))throw new IllegalArgumentException("Constant or numerically unresolved reference annotation cannot define a conditional effect");
            for(int k=0;k<c;k++){
                double value=products[j][k];
                if(binary[j]&&binary[k]&&(value!=Math.rint(value)||value<Math.max(0,sums[j]+sums[k]-sums[0])||value>Math.min(sums[j],sums[k])))throw new IllegalArgumentException("Inconsistent binary intersections in mixed model");
                matrix[j*c+k]=value;
            }
        }
        GeneticCovarianceValidation.requirePositiveSemidefinite(matrix,c);
        // A dependent reference annotation model cannot acquire identifiable
        // coefficients merely by supplying inconsistent noncollinear LD scores.
        SummaryMath.inverse(matrix,c);
    }
    /** Counts and intersections cover the same complete common-reference-SNP universe. */
    public static void validateMoments(double[] counts,double[][] overlap){
        SummaryMath.finite(counts);int c=counts.length;
        if(c<2||overlap.length!=c)throw new IllegalArgumentException("Annotation moment dimensions differ");
        for(double[] row:overlap){SummaryMath.finite(row);if(row.length!=c)throw new IllegalArgumentException("Annotation moment dimensions differ");}
        double[] matrix=new double[c*c];
        for(int j=0;j<c;j++){
            SummaryMath.finite(overlap[j]);if(overlap[j].length!=c||counts[j]<=0||counts[j]>counts[0]||counts[j]!=Math.rint(counts[j])||counts[0]>9e15)
                throw new IllegalArgumentException("Binary annotation counts must be positive reference-SNP counts");
            for(int k=0;k<c;k++){
                double v=overlap[j][k];
                if(v!=Math.rint(v)||v<Math.max(0,counts[j]+counts[k]-counts[0])||v>Math.min(counts[j],counts[k])||v!=overlap[k][j]
                        ||j==k&&v!=counts[j]||j==0&&v!=counts[k])throw new IllegalArgumentException("Inconsistent binary annotation overlaps or all-SNP baseline");
                matrix[j*c+k]=v;
            }
        }
        GeneticCovarianceValidation.requirePositiveSemidefinite(matrix,c);
        for(int j=0;j<c;j++)for(int k=0;k<j;k++)if(counts[j]==counts[k]&&overlap[j][k]==counts[j])
            throw new IllegalArgumentException("Identical binary annotations cannot define distinct S-LDSC coefficients");
    }
    private static double[] solve(double[] lower,double[] rhs,double[] scale,int c,double nbar){
        int d=rhs.length;double[] g=lower.clone();for(int j=0;j<d;j++)for(int k=0;k<j;k++)g[k*d+j]=g[j*d+k];
        double[] inverse=SummaryMath.inverse(g,d),solution=SummaryMath.multiply(inverse,rhs);
        // Reject numerically inaccurate normal-equation solutions; never ridge or drop columns.
        for(int j=0;j<d;j++){
            double check=0,bound=Math.abs(rhs[j]);for(int k=0;k<d;k++){check+=g[j*d+k]*solution[k];bound+=Math.abs(g[j*d+k]*solution[k]);}
            if(Math.abs(check-rhs[j])>1e-9*Math.max(Double.MIN_NORMAL,bound))throw new IllegalArgumentException("Unstable S-LDSC design; supply a noncollinear prespecified annotation model");
        }
        for(int j=0;j<d;j++)solution[j]/=scale[j]*(j<c?nbar:1);SummaryMath.finite(solution);return solution;
    }
    private static double variance(double[] a,double[] cov){
        int c=a.length;double v=0,scale=0;for(int j=0;j<c;j++)for(int k=0;k<c;k++){double term=a[j]*cov[j*c+k]*a[k];v+=term;scale+=Math.abs(term);}
        if(v<0&&v>=-1e-12*scale)v=0;if(v<0||!Double.isFinite(v))throw new IllegalArgumentException("Invalid S-LDSC jackknife variance");return v;
    }
    private static double se(double[] deletes){double[][] x=new double[deletes.length][1];for(int i=0;i<x.length;i++)x[i][0]=deletes[i];return Math.sqrt(SummaryMath.jackknifeCovariance(x)[0]);}
}
