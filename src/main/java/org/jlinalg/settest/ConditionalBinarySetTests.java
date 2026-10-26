/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import jdistlib.Beta;

/** Finite conditional and independent-pilot Monte Carlo binary set tests. */
public final class ConditionalBinarySetTests {
    private ConditionalBinarySetTests() { }
    private static final double[] RHO={0,.01,.04,.09,.16,.25,.5,1};
    private static final List<String> TESTS=List.of("burden","skat","skat-o","acat-v","acat-o");
    public record Options(String mode,int maximumExactStates,int pilotDraws,int draws,long seed,
            long maximumWork,long maximumCells,double acatMacThreshold){
        public Options {
            if(!List.of("auto","exact","monte_carlo").contains(mode)||maximumExactStates<1||maximumExactStates>1000000||pilotDraws<10||pilotDraws>1000000||draws<10||draws>10000000||maximumWork<1||maximumCells<1||!Double.isFinite(acatMacThreshold)||acatMacThreshold<0)throw new IllegalArgumentException("Invalid conditional binary set controls");
        }
        public static Options defaults(){return new Options("auto",100000,10000,100000,1,1000000000,20000000,10);}
    }
    public record Test(String test,double statistic,double pValue,double logPValue,long tailDraws,
            double tailProbabilityLower95,double tailProbabilityUpper95,boolean constantEvaluatedNull) { }
    public record Result(List<Test> tests,String calibration,long allocationStates,int pilotDraws,int draws,
            double pResolution,long seed,long work,String interpretation){public Result{tests=List.copyOf(tests);}}
    public record Weights(double[] burden,double[] kernel,double[] acat,String acatScheme){
        public Weights(double[] burden,double[] kernel,double[] acat){this(burden,kernel,acat,"custom");}
        public Weights{burden=burden.clone();kernel=kernel.clone();acat=acat.clone();if(!List.of("custom","beta_1_25","beta_1_1").contains(acatScheme))throw new IllegalArgumentException("Unknown conditional ACAT-V weight scheme");}
        @Override public double[] burden(){return burden.clone();}@Override public double[] kernel(){return kernel.clone();}@Override public double[] acat(){return acat.clone();}
    }

    /** Primary weights are fixed independently of outcome assignment; ACAT-O uses beta(1,25)/(1,1). */
    public static Result analyze(ConditionalSetNull.Model model,double[][] genotype,double[] maf,double[] mac,
            double[] primaryWeights,Options options){
        return analyze(model,genotype,maf,mac,new Weights(primaryWeights,primaryWeights,primaryWeights),options);
    }
    public static Result analyze(ConditionalSetNull.Model model,double[][] genotype,double[] maf,double[] mac,
            Weights weights,Options options){
        int m=maf.length;
        if(mac.length!=m||weights.burden.length!=m||weights.kernel.length!=m||weights.acat.length!=m||m<1||m>2000)throw new IllegalArgumentException("Conditional set variant dimensions differ");
        for(int j=0;j<m;j++)if(!Double.isFinite(maf[j])||maf[j]<0||maf[j]>.5||!Double.isFinite(mac[j])||mac[j]<0)throw new IllegalArgumentException("Invalid fixed MAF/MAC");
        for(double[] vector:List.of(weights.burden,weights.kernel,weights.acat))for(double value:vector)if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid fixed conditional set weight");
        var budget=new ConditionalSetNull.Budget(options.maximumWork,options.maximumCells);
        var distribution=new ConditionalSetNull.Distribution(model,genotype,options.maximumExactStates,budget);
        if(distribution.dimensions!=m)throw new IllegalArgumentException("Conditional set genotype and frequency dimensions differ");
        long exactCells=distribution.states*(m+1L);for(var s:distribution.strata)exactCells+=s.states*(m+1L);
        boolean exact=!options.mode.equals("monte_carlo")&&distribution.states<=options.maximumExactStates&&exactCells<=options.maximumCells;
        if(options.mode.equals("exact")&&!exact)throw new IllegalArgumentException("Exact conditional set allocation exceeds maximum_exact_states or maximum_cells; no sampled null was substituted");
        double[][] pilot;double[] log;
        var random=new SplittableRandom(options.seed);
        if(exact){var table=distribution.enumerate(budget);pilot=table.scores();log=table.logWeights();}
        else{budget.cells((long)options.pilotDraws*(m+20));pilot=new double[options.pilotDraws][];log=new double[options.pilotDraws];for(int i=0;i<pilot.length;i++)pilot[i]=distribution.sample(random,budget);}
        var definition=new Definition(maf,mac,weights,options.acatMacThreshold,pilot,log,!exact,budget);
        double[] observed=definition.statistics(distribution.observed,budget);var results=new ArrayList<Test>();
        if(exact){
            for(int t=0;t<TESTS.size();t++){
                double[] values=new double[pilot.length];for(int i=0;i<values.length;i++)values[i]=definition.pilotStatistics[i][t];var cdf=new Cdf(values,log,false,budget);double lp=cdf.logInclusive(observed[t]);if(!cdf.contains(observed[t]))throw new IllegalArgumentException("Observed conditional statistic is absent from complete enumerated support");
                results.add(new Test(TESTS.get(t),observed[t],Math.exp(lp),lp,-1,Double.NaN,Double.NaN,cdf.keys.length==1));
            }
        }else{
            long[] hits=new long[TESTS.size()];double[] first=new double[TESTS.size()];boolean[] constant=new boolean[TESTS.size()];Arrays.fill(constant,true);
            for(int i=0;i<options.draws;i++){double[] score=distribution.sample(random,budget),stats=definition.statistics(score,budget);for(int t=0;t<stats.length;t++){if(i==0)first[t]=stats[t];else constant[t]&=stats[t]==first[t];if(stats[t]>=observed[t])hits[t]++;}}
            for(int t=0;t<TESTS.size();t++){long h=hits[t];double lp=Math.log(h+1.0)-Math.log(options.draws+1.0),lower=h==0?0:Beta.quantile(.025,h,options.draws-h+1,true,false),upper=h==options.draws?1:Beta.quantile(.975,h+1,options.draws-h,true,false);results.add(new Test(TESTS.get(t),observed[t],Math.exp(lp),lp,h,lower,upper,constant[t]&&observed[t]==first[t]));}
        }
        String method=exact?"finite_conditional_genotype_pattern_enumeration":"conditional_monte_carlo_independent_pilot_plus_one";
        return new Result(results,method,distribution.states,exact?0:options.pilotDraws,exact?0:options.draws,exact?Double.NaN:1.0/(options.draws+1),options.seed,budget.used,
            "All five primary P values calibrate their complete statistic under fixed stratum case totals and known offsets. Adaptive kernel/Cauchy statistics use internal mid-tail ranks, not unadjusted association P values. Monte Carlo uses a separate pilot to freeze those transformations and independent main draws with a plus-one test; intervals estimate the fixed-pilot statistic's conditional tail probability. Statistics retain 40 fraction bits. Burden is unsigned test-only evidence; no odds ratio, beta, SE or direction is estimated.");
    }
    static final class Definition {
        final double[] maf,mac,primary,burdenPrimary;final double threshold;
        final Shape main,a,b,acatMain;final Cdf[] rho;final Cdf[] acatVCdf;final double[][] pilotStatistics;
        Definition(double[] maf,double[] mac,Weights weights,double threshold,double[][] scores,double[] log,boolean sampled,ConditionalSetNull.Budget budget){
            this.maf=maf.clone();this.mac=mac.clone();this.primary=normalize(weights.kernel);burdenPrimary=normalize(weights.burden);this.threshold=threshold;
            int n=scores.length,m=maf.length;budget.cells((long)n*(4L*m+180));budget.spend((long)n*(m+40));
            Cdf[] variants=new Cdf[m];for(int j=0;j<m;j++)if(mac[j]>threshold){double[] values=new double[n];for(int i=0;i<n;i++)values[i]=canonical(scores[i][j]*scores[i][j]);variants[j]=new Cdf(values,log,sampled,budget);}
            double[] w25=new double[m],equal=new double[m];Arrays.fill(equal,1);for(int j=0;j<m;j++)w25[j]=Math.pow(1-maf[j],24);
            w25=normalize(w25);a=new Shape(w25,maf,mac,threshold,scores,log,sampled,false,variants,budget);b=new Shape(equal,maf,mac,threshold,scores,log,sampled,false,variants,budget);
            main=Arrays.equals(primary,w25)?a:Arrays.equals(primary,equal)?b:new Shape(primary,maf,mac,threshold,scores,log,sampled,true,variants,budget);
            double[] acatWeights=normalize(weights.acat);
            if(weights.acatScheme.equals("beta_1_25")){requireWeights(acatWeights,w25);acatMain=a;}
            else if(weights.acatScheme.equals("beta_1_1")){requireWeights(acatWeights,equal);acatMain=b;}
            else acatMain=new Shape(acatWeights,maf,mac,threshold,scores,log,sampled,true,variants,budget);
            rho=new Cdf[RHO.length];for(int k=0;k<RHO.length;k++){double[] values=new double[n];for(int i=0;i<n;i++)values[i]=canonical((1-RHO[k])*main.kernel[i]+RHO[k]*main.burden[i]);rho[k]=new Cdf(values,log,sampled,budget);}
            acatVCdf=new Cdf[]{new Cdf(a.acat,log,sampled,budget),new Cdf(b.acat,log,sampled,budget)};
            pilotStatistics=new double[n][TESTS.size()];for(int i=0;i<n;i++)pilotStatistics[i]=statistics(scores[i],budget);
        }
        double[] statistics(double[] u,ConditionalSetNull.Budget budget){
            budget.spend((long)u.length*8+100);double[] core=quadratic(u,primary);double min=1;
            for(int k=0;k<RHO.length;k++)min=Math.min(min,rho[k].mid(canonical((1-RHO[k])*core[1]+RHO[k]*core[0])));
            double av=acatMain.acat(u);double[] aq=quadratic(u,a.weights),bq=quadratic(u,b.weights);
            double[] rank={a.kernelCdf.mid(aq[1]),b.kernelCdf.mid(bq[1]),a.burdenCdf.mid(aq[0]),b.burdenCdf.mid(bq[0]),acatVCdf[0].mid(a.acat(u)),acatVCdf[1].mid(b.acat(u))};
            double ao=cauchy(rank,null);return new double[]{quadratic(u,burdenPrimary)[0],core[1],canonical(-min),av,ao};
        }
    }
    private static final class Shape {
        final double[] weights,pWeights,burden,kernel,acat;final int[] high,low;final Cdf[] variant;final Cdf collapsed,burdenCdf,kernelCdf;final double collapseWeight;
        Shape(double[] weights,double[] maf,double[] mac,double threshold,double[][] scores,double[] log,boolean sampled,boolean custom,Cdf[] variants,ConditionalSetNull.Budget budget){
            this.weights=weights;int m=weights.length,n=scores.length;var hi=new ArrayList<Integer>();var lo=new ArrayList<Integer>();pWeights=new double[m];
            for(int j=0;j<m;j++){pWeights[j]=weights[j]*weights[j]*maf[j]*(1-maf[j]);if(mac[j]<=threshold)lo.add(j);else hi.add(j);}high=hi.stream().mapToInt(Integer::intValue).toArray();low=lo.stream().mapToInt(Integer::intValue).toArray();
            burden=new double[n];kernel=new double[n];for(int i=0;i<n;i++){double[] q=quadratic(scores[i],weights);burden[i]=q[0];kernel[i]=q[1];}
            burdenCdf=new Cdf(burden,log,sampled,budget);kernelCdf=new Cdf(kernel,log,sampled,budget);
            variant=new Cdf[high.length];for(int k=0;k<high.length;k++)variant[k]=variants[high[k]];
            if(low.length==0){collapsed=null;collapseWeight=0;}
            else{double[] values=new double[n];for(int i=0;i<n;i++){double score=0;for(int j:low)score=Math.fma(scores[i][j],weights[j],score);values[i]=canonical(score*score);}collapsed=new Cdf(values,log,sampled,budget);
                double mean=0;for(int j:low)mean+=maf[j]/low.length;
                if(custom){double w=0;for(int j:low)w+=pWeights[j]/low.length;collapseWeight=w;}
                else{double max=0;for(double f:maf)max=Math.max(max,Math.pow(1-f,24));boolean equal=true;for(double w:weights)equal&=w==1;double w=equal?1:Math.pow(1-mean,24)/max;collapseWeight=w*w*mean*(1-mean);}
            }
            acat=new double[n];for(int i=0;i<n;i++)acat[i]=acat(scores[i]);
        }
        double acat(double[] u){double[] ranks=new double[high.length+(low.length==0?0:1)],w=new double[ranks.length];for(int k=0;k<high.length;k++){int j=high[k];ranks[k]=variant[k].mid(canonical(u[j]*u[j]));w[k]=pWeights[j];}if(low.length>0){double score=0;for(int j:low)score=Math.fma(u[j],weights[j],score);ranks[high.length]=collapsed.mid(canonical(score*score));w[high.length]=collapseWeight;}return cauchy(ranks,w);}
    }
    private static double[] normalize(double[] weights){double max=Arrays.stream(weights).max().orElse(0);if(!(max>0))throw new IllegalArgumentException("Conditional set weights need at least one positive value");double[] out=weights.clone();for(int i=0;i<out.length;i++)out[i]/=max;return out;}
    private static void requireWeights(double[] supplied,double[] expected){for(int i=0;i<supplied.length;i++)if(Math.abs(supplied[i]-expected[i])>1e-12*Math.max(1,expected[i]))throw new IllegalArgumentException("Declared canonical conditional ACAT-V weights disagree with their frequency definition");}
    private static double[] quadratic(double[] u,double[] weights){double burden=0,kernel=0;for(int j=0;j<u.length;j++){double s=u[j]*weights[j];burden+=s;kernel=Math.fma(s,s,kernel);}return new double[]{canonical(burden*burden),canonical(kernel)};}
    private static double cauchy(double[] ranks,double[] weights){if(ranks.length==0)return 0;if(weights!=null&&Arrays.stream(weights).noneMatch(w->w>0))return 0;double t=weights==null?Acat.combine(ranks).statistic():Acat.combine(ranks,weights).statistic();return canonical(t);}
    /** One fixed numeric statistic definition removes round-off-only tie splits. */
    static double canonical(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Unrepresentable conditional set statistic");if(value==0)return 0;long bits=Double.doubleToRawLongBits(Math.abs(value));double rounded=Double.longBitsToDouble((bits+2048)&~4095L);if(!Double.isFinite(rounded))throw new IllegalArgumentException("Conditional statistic exceeds numeric range");return Math.copySign(rounded,value);}
    static final class Cdf {
        final double[] keys,mass,suffix;final double total;final boolean sampled;
        Cdf(double[] input,double[] logWeights,boolean sampled,ConditionalSetNull.Budget budget){
            int n=input.length;if(n<1||logWeights.length!=n)throw new IllegalArgumentException("Conditional CDF dimensions differ");this.sampled=sampled;
            budget.spend((long)n*(3+Math.max(1,32-Integer.numberOfLeadingZeros(n))));int[] order=new int[n],temp=new int[n];for(int i=0;i<n;i++)order[i]=i;
            for(int width=1;width<n;width*=2){for(int low=0;low<n;low+=2*width){int mid=Math.min(n,low+width),end=Math.min(n,low+2*width),i=low,j=mid,k=low;while(i<mid||j<end)temp[k++]=j>=end||i<mid&&input[order[i]]<=input[order[j]]?order[i++]:order[j++];}int[] swap=order;order=temp;temp=swap;if(width>n/2)break;}
            var values=new ArrayList<Double>();var logs=new ArrayList<Double>();for(int i:order){double key=canonical(input[i]),weight=logWeights[i];if(!Double.isFinite(weight))throw new IllegalArgumentException("Conditional allocation mass is not finite");if(values.isEmpty()||values.get(values.size()-1)!=key){values.add(key);logs.add(weight);}else{int last=logs.size()-1;logs.set(last,ConditionalSetNull.add(logs.get(last),weight));}}
            keys=values.stream().mapToDouble(Double::doubleValue).toArray();mass=logs.stream().mapToDouble(Double::doubleValue).toArray();suffix=new double[keys.length+1];suffix[keys.length]=Double.NEGATIVE_INFINITY;for(int i=keys.length-1;i>=0;i--)suffix[i]=ConditionalSetNull.add(suffix[i+1],mass[i]);total=suffix[0];
        }
        private int lower(double value){int lo=0,hi=keys.length;while(lo<hi){int mid=(lo+hi)>>>1;if(keys[mid]<value)lo=mid+1;else hi=mid;}return lo;}
        boolean contains(double value){int i=lower(canonical(value));return i<keys.length&&keys[i]==canonical(value);}
        double logInclusive(double value){int i=lower(canonical(value));return Math.min(0,suffix[i]-total);}
        double mid(double value){value=canonical(value);int i=lower(value);boolean equal=i<keys.length&&keys[i]==value;double upper=equal?ConditionalSetNull.add(suffix[i+1],mass[i]-Math.log(2)):suffix[i];double denominator=sampled?ConditionalSetNull.add(total,0):total;if(sampled)upper=ConditionalSetNull.add(upper,-Math.log(2));double p=Math.exp(upper-denominator);return Math.max(Double.MIN_NORMAL,Math.min(Math.nextDown(1.0),p));}
    }
}
