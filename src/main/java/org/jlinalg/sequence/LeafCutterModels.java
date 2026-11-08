/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import jdistlib.ChiSquare;
import jdistlib.math.MathFunctions;

/**
 * Evaluates the original LeafCutter multi-concentration Stan posterior and
 * original R effect-size/LRT definitions. This does not optimize a model.
 * Source/version/license identities are packaged under leafcutter-models.
 */
public final class LeafCutterModels {
    private LeafCutterModels() { }
    public record Parameters(List<List<Double>> betaRaw,List<Double> betaScale,List<Double> concentration,Double outlierProbability) {
        public Parameters {betaRaw=betaRaw.stream().map(List::copyOf).toList();betaScale=List.copyOf(betaScale);concentration=List.copyOf(concentration);}
    }
    public record Priors(double concentrationShape,double concentrationRate,double outlierA,double outlierB) {
        public Priors {positive(concentrationShape);positive(concentrationRate);positive(outlierA);positive(outlierB);}
        public static Priors sourceDefaults(){return new Priors(1.0001,1e-4,1.01,100);}
    }
    public record Effects(List<Double> logEffects,List<Double> baseline,List<Double> perturbed,List<Double> deltaPsi) {
        public Effects {logEffects=List.copyOf(logEffects);baseline=List.copyOf(baseline);perturbed=List.copyOf(perturbed);deltaPsi=List.copyOf(deltaPsi);}
    }
    public record Anova(double logRatio,int degreesOfFreedom,double pValue) { }

    /** Row p, intron k coefficient = scale[p] * (simplex[p,k] - 1/K). */
    public static List<List<Double>> coefficientRows(Parameters parameters) {
        Objects.requireNonNull(parameters);int k=parameters.concentration().size(),p=parameters.betaRaw().size();require(k>=2&&p>0&&parameters.betaScale().size()==p,"Complete coefficient/concentration family required");
        for(double c:parameters.concentration())positive(c);var result=new ArrayList<List<Double>>();
        for(int i=0;i<p;i++){var row=parameters.betaRaw().get(i);require(row.size()==k,"Ragged coefficient simplex");double total=0;for(double v:row){require(Double.isFinite(v)&&v>0&&v<1,"Strictly interior finite original simplex required; never repair parameters");total+=v;}require(Math.abs(total-1)<=1e-12,"Original simplex must sum to one");double scale=parameters.betaScale().get(i);finite(scale);var values=new ArrayList<Double>();for(double v:row){double value=scale*(v-1.0/k);finite(value);values.add(value);}result.add(List.copyOf(values));}
        return List.copyOf(result);
    }

    /**
     * Original constrained-space log posterior, with Stan's parameter-independent
     * prior constants omitted and no transformation Jacobian or multinomial
     * coefficient. Counts must be physical integers and remain unchanged by this
     * call. Reject integers/sample totals above 2^53 rather than claim exact Stan
     * double conversion. This evaluates supplied parameters, not a fitted optimum.
     */
    public static double logPosterior(long[][] counts,double[][] design,Parameters parameters,Priors priors,boolean robust) {
        Objects.requireNonNull(counts);Objects.requireNonNull(design);Objects.requireNonNull(priors);var beta=coefficientRows(parameters);int n=counts.length,k=parameters.concentration().size(),p=beta.size();require(n>0&&design.length==n&&(long)n*(k+p)<=10000000,"Complete model dimensions/capacity required");double result=0;
        for(double c:parameters.concentration())result+=(priors.concentrationShape()-1)*Math.log(c)-priors.concentrationRate()*c;
        double outlier=0;if(robust){require(parameters.outlierProbability()!=null,"Robust outlier parameter required");outlier=parameters.outlierProbability();require(Double.isFinite(outlier)&&outlier>0&&outlier<1,"Strictly interior outlier probability required");result+=(priors.outlierA()-1)*Math.log(outlier)+(priors.outlierB()-1)*Math.log1p(-outlier);}
        for(int i=0;i<n;i++){
            require(counts[i].length==k&&design[i].length==p,"Ragged original count/design matrix");long total=0;for(long y:counts[i]){require(y>=0&&y<=9007199254740992L,"Physical exact-double integer count required; do not round");total=Math.addExact(total,y);}require(total<=9007199254740992L,"Sample total exceeds exact-double count profile");
            double[] logits=new double[k];for(int j=0;j<k;j++){for(int c=0;c<p;c++){finite(design[i][c]);logits[j]+=beta.get(c).get(j)*design[i][c];}finite(logits[j]);}double[] s=softmax(logits);double suma=0;double[] a=new double[k];for(int j=0;j<k;j++){a[j]=parameters.concentration().get(j)*s[j];positive(a[j]);suma+=a[j];}positive(suma);
            double value=MathFunctions.lgammafn(suma)-MathFunctions.lgammafn(suma+total);for(int j=0;j<k;j++)value+=MathFunctions.lgammafn(a[j]+counts[i][j])-MathFunctions.lgammafn(a[j]);finite(value);
            if(robust){double uniform=MathFunctions.lgammafn(k)-MathFunctions.lgammafn(k+(double)total);for(long y:counts[i])uniform+=MathFunctions.lgammafn(1.0+y);double left=Math.log1p(-outlier)+value,right=Math.log(outlier)+uniform;double maximum=Math.max(left,right);value=maximum+Math.log(Math.exp(left-maximum)+Math.exp(right-maximum));}
            result+=value;
        }
        finite(result);return result;
    }

    /** Original two-condition effect definition, holding all other covariates at zero. */
    public static Effects effectSizes(Parameters parameters) {
        var beta=coefficientRows(parameters);require(beta.size()>=2,"Original effect definition needs intercept and group as the first two coefficients");int k=parameters.concentration().size();double[] first=new double[k],second=new double[k];var logEffects=new ArrayList<Double>();
        for(int j=0;j<k;j++){first[j]=beta.get(0).get(j);second[j]=first[j]+beta.get(1).get(j);logEffects.add(beta.get(1).get(j));}
        var baseline=sourceProportions(first,parameters.concentration());var perturbed=sourceProportions(second,parameters.concentration());var delta=new ArrayList<Double>();for(int j=0;j<k;j++)delta.add(perturbed.get(j)-baseline.get(j));return new Effects(logEffects,baseline,perturbed,delta);
    }
    /** Retains a negative source objective difference; its chi-square upper tail is one. */
    public static Anova anova(double nullObjective,double fullObjective,int nullColumns,int fullColumns,int introns) {
        finite(nullObjective);finite(fullObjective);require(nullColumns>0&&fullColumns>nullColumns&&introns>=2,"Positive nested-model degree-of-freedom profile required");int df=Math.multiplyExact(fullColumns-nullColumns,introns-1);double logRatio=fullObjective-nullObjective;finite(logRatio);double p=ChiSquare.cumulative(2*logRatio,df,false,false);require(Double.isFinite(p)&&p>=0&&p<=1,"Undefined original chi-square tail; no replacement");return new Anova(logRatio,df,p);
    }
    private static List<Double> sourceProportions(double[] beta,List<Double> concentration){
        // Preserve R's literal exp-normalize, concentration-weight, normalize
        // sequence. Nonfinite source effects are rejected, not silently stabilized.
        double[] values=new double[beta.length];double sum=0;for(int i=0;i<values.length;i++){values[i]=Math.exp(beta[i]);positive(values[i]);sum+=values[i];}positive(sum);double weighted=0;for(int i=0;i<values.length;i++){values[i]=values[i]/sum*concentration.get(i);positive(values[i]);weighted+=values[i];}positive(weighted);var result=new ArrayList<Double>();for(double value:values)result.add(value/weighted);return List.copyOf(result);
    }
    private static double[] softmax(double[] logits){double maximum=Arrays.stream(logits).max().orElseThrow(),sum=0;double[] result=new double[logits.length];for(int i=0;i<result.length;i++){result[i]=Math.exp(logits[i]-maximum);sum+=result[i];}positive(sum);for(int i=0;i<result.length;i++)result[i]/=sum;return result;}
    private static void positive(double value){require(Double.isFinite(value)&&value>0,"Finite positive original model quantity required; no replacement");}
    private static void finite(double value){require(Double.isFinite(value),"Finite original model quantity required; no replacement");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
