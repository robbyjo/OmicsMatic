/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Bounded sufficient-subset finite-MPLE search and exact full-risk monotonicity
 * verification. A positive full-rank balance in any actual risk-contrast subset
 * excludes every nonzero recession direction of the full partial likelihood.
 * A subset monotone direction is never accepted without checking all original
 * risk sets. Deterministic support rounds (32,128,512,2048 rows plus covariate/
 * event anchors) improve ordinary large-cohort checks without claiming complete
 * search or event-tail calibration. Work/pair budgets span all rounds.
 */
public final class CoxAdaptiveLikelihood {
    private CoxAdaptiveLikelihood() { }
    public record Result(CoxLikelihoodExistence.Result proof,List<Integer> supportingRows,
                         int attempts,long fullRiskQueries,boolean fullDirectionVerified) implements java.io.Serializable {
        public Result { supportingRows=List.copyOf(supportingRows); }
        public CoxLikelihoodExistence.Status status(){return proof.status();}
        public String message(){return proof.message();}
        public boolean finiteMaximum(){return proof.finiteMaximum();}
    }
    public record DirectionCheck(boolean verified,String message,long work,long riskQueries) { }
    public static Result analyze(CoxSurvivalData survival,double[][] x){return analyze(survival,x,CoxLikelihoodExistence.Options.defaults());}
    public static Result analyze(CoxSurvivalData survival,double[][] x,CoxLikelihoodExistence.Options options){
        validate(survival,x,options);int n=x.length,p=x[0].length;Budget budget=new Budget(options);CoxLikelihoodExistence.Result last=null;List<Integer> support=List.of();int attempts=0;
        try{
            if(p==0)return new Result(CoxLikelihoodExistence.analyze(survival,x,options),List.of(),1,0,false);
            if(p>options.maximumDimension())return unresolved(p,"adaptive likelihood dimension bound reached",budget,last,support,attempts);
            budget.spend(3L*n*p+n);TreeSet<Integer> anchors=new TreeSet<>();int firstEvent=-1,lastEvent=-1;
            for(int i=0;i<n;i++)if(survival.eventView()[i]){if(firstEvent<0)firstEvent=i;lastEvent=i;}
            anchors.add(firstEvent);anchors.add(lastEvent);
            for(int j=0;j<p;j++){
                int low=0,high=0,lowEvent=firstEvent,highEvent=firstEvent;
                for(int i=0;i<n;i++){if(x[i][j]<x[low][j])low=i;if(x[i][j]>x[high][j])high=i;if(survival.eventView()[i]){if(x[i][j]<x[lowEvent][j])lowEvent=i;if(x[i][j]>x[highEvent][j])highEvent=i;}}
                anchors.add(low);anchors.add(high);anchors.add(lowEvent);anchors.add(highEvent);
            }
            for(int size:new int[]{32,128,512,2048}){
                if(budget.remainingPairs()<1 || budget.remainingWork()<1)throw new Exhausted("adaptive likelihood budget exhausted");
                TreeSet<Integer> selected=new TreeSet<>(anchors);int count=Math.min(n,size);
                for(int k=0;k<count;k++)selected.add(count==1?0:(int)((long)k*(n-1)/(count-1)));
                if(selected.size()==support.size())continue;
                List<Integer> candidateRows=List.copyOf(selected);int m=candidateRows.size();budget.spend((long)m*(p+4));double[][] values=new double[m][p];double[] start=new double[m],stop=new double[m];boolean[] event=new boolean[m];int[] strata=new int[m];
                for(int i=0;i<m;i++){int row=candidateRows.get(i);values[i]=x[row].clone();start[i]=survival.startView()[row];stop[i]=survival.stopView()[row];event[i]=survival.eventView()[row];strata[i]=survival.strataView()[row];}
                if(budget.remainingWork()<1)throw new Exhausted("adaptive likelihood budget exhausted before support search");
                var remaining=new CoxLikelihoodExistence.Options(budget.remainingPairs(),budget.remainingWork(),options.maximumIterations(),options.maximumDimension(),options.maximumCertificateBits(),options.tolerance());
                var searched=CoxLikelihoodExistence.analyze(new CoxSurvivalData(start,stop,event,strata),values,remaining);support=candidateRows;last=searched;attempts++;budget.work+=last.work();budget.pairs+=last.pairVisits();
                if(last.finiteMaximum())return mapped(last,CoxLikelihoodExistence.Status.FINITE_MAXIMUM,"Exact positive full-rank supporting-risk balance proves a finite unique full-model maximum",support,budget,attempts,false);
                if(last.status()==CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD){
                    var check=verify(survival,x,last.exactDirection(),budget);
                    if(check)return mapped(last,CoxLikelihoodExistence.Status.MONOTONE_LIKELIHOOD,"Exact direction verified over every original risk set; no finite maximum",support,budget,attempts,true);
                }
                if(m==n)break;
            }
            return unresolved(p,"adaptive support search did not certify the full model; increase bounds or use the exhaustive check",budget,last,support,attempts);
        }catch(Exhausted exhausted){return unresolved(p,exhausted.getMessage(),budget,last,support,attempts);}
    }
    /** Audits a rational direction against every original risk set in an entry/exit sweep. */
    public static DirectionCheck verifyDirection(CoxSurvivalData survival,double[][] x,List<CoxLikelihoodExistence.ExactCoefficient> direction,CoxLikelihoodExistence.Options options){
        validate(survival,x,options);Budget budget=new Budget(options);
        try{boolean valid=verify(survival,x,direction,budget);return new DirectionCheck(valid,valid?"exact full-risk monotone direction":"direction does not prove strict monotonicity in the full model",budget.work,budget.queries);}
        catch(Exhausted exhausted){return new DirectionCheck(false,exhausted.getMessage(),budget.work,budget.queries);}
    }
    private static void validate(CoxSurvivalData survival,double[][] x,CoxLikelihoodExistence.Options options){
        if(survival==null||options==null||x==null||x.length!=survival.observations()||x[0]==null)throw new IllegalArgumentException("row-aligned Cox response and design required");
        int p=x[0].length;for(double[] row:x){if(row==null||row.length!=p)throw new IllegalArgumentException("rectangular Cox design required");for(double v:row)if(!Double.isFinite(v))throw new IllegalArgumentException("finite Cox covariates required");}
    }
    private static boolean verify(CoxSurvivalData survival,double[][] x,List<CoxLikelihoodExistence.ExactCoefficient> direction,Budget budget){
        int n=x.length,p=x[0].length;budget.spend((long)n*(p+1));BigInteger[] numerator=new BigInteger[p],denominator=new BigInteger[p];Arrays.fill(numerator,BigInteger.ZERO);Arrays.fill(denominator,BigInteger.ONE);boolean[] seen=new boolean[p];BigInteger common=BigInteger.ONE;
        for(var value:direction){
            int j=value.column();if(j<0||j>=p||seen[j])throw new IllegalArgumentException("unique in-range direction columns required");seen[j]=true;
            if(value.numerator().length()>budget.options.maximumCertificateBits()||value.denominator().length()>budget.options.maximumCertificateBits())throw new Exhausted("direction certificate bit bound reached");
            numerator[j]=new BigInteger(value.numerator());denominator[j]=new BigInteger(value.denominator());if(denominator[j].signum()<=0)throw new IllegalArgumentException("positive direction denominator required");
            common=common.divide(common.gcd(denominator[j])).multiply(denominator[j]);budget.bits(common);budget.bits(numerator[j]);
        }
        boolean nonzero=false;for(int j=0;j<p;j++){numerator[j]=numerator[j].multiply(common.divide(denominator[j]));budget.bits(numerator[j]);nonzero|=numerator[j].signum()!=0;}if(!nonzero)return false;
        BigDecimal[] projection=new BigDecimal[n];Map<Integer,List<Integer>> groups=new TreeMap<>();
        for(int i=0;i<n;i++){
            BigDecimal value=BigDecimal.ZERO;for(int j=0;j<p;j++){budget.spend(1+(numerator[j].bitLength()+1023)/1024);value=value.add(new BigDecimal(x[i][j]).multiply(new BigDecimal(numerator[j])));}
            projection[i]=value;groups.computeIfAbsent(survival.strataView()[i],key->new ArrayList<>()).add(i);
        }
        boolean strict=false;
        for(List<Integer> rows:groups.values()){
            budget.spend(3L*rows.size()*(1+(int)(Math.log(Math.max(2,rows.size()))/Math.log(2))));
            var starts=new ArrayList<>(rows);var stops=new ArrayList<>(rows);var events=new ArrayList<Integer>();for(int row:rows)if(survival.eventView()[row])events.add(row);
            starts.sort(Comparator.comparingDouble((Integer row)->survival.startView()[row]).thenComparingInt(Integer::intValue));
            stops.sort(Comparator.comparingDouble((Integer row)->survival.stopView()[row]).thenComparingInt(Integer::intValue));events.sort(Comparator.comparingDouble((Integer row)->survival.stopView()[row]).thenComparingInt(Integer::intValue));
            TreeMap<BigDecimal,Integer> active=new TreeMap<>();int added=0,removed=0;
            for(int row:events){
                double time=survival.stopView()[row];
                while(added<starts.size() && survival.startView()[starts.get(added)]<time){int i=starts.get(added++);active.merge(projection[i],1,Integer::sum);budget.spend(1);}
                while(removed<stops.size() && survival.stopView()[stops.get(removed)]<time){int i=stops.get(removed++);active.compute(projection[i],(key,count)->count==1?null:count-1);budget.spend(1);}
                budget.spend(1);budget.queries++;
                if(active.isEmpty()||projection[row].compareTo(active.lastKey())<0)return false;
                strict|=projection[row].compareTo(active.firstKey())>0;
            }
        }
        return strict;
    }
    private static Result mapped(CoxLikelihoodExistence.Result result,CoxLikelihoodExistence.Status status,String message,List<Integer> support,Budget budget,int attempts,boolean verified){
        List<CoxLikelihoodExistence.ExtraWeight> weights=result.extraWeights().stream().map(w->new CoxLikelihoodExistence.ExtraWeight(new CoxLikelihoodExistence.Pair(support.get(w.pair().eventRow()),support.get(w.pair().riskRow())),w.value())).toList();
        List<CoxLikelihoodExistence.BasisCorrection> corrections=result.basisCorrections().stream().map(w->new CoxLikelihoodExistence.BasisCorrection(new CoxLikelihoodExistence.Pair(support.get(w.pair().eventRow()),support.get(w.pair().riskRow())),w.numerator(),w.denominator())).toList();
        var proof=new CoxLikelihoodExistence.Result(status,message,result.dimension(),result.numericalRank(),result.contrasts(),budget.pairs,budget.work,result.iterations(),result.searchResidual(),result.columnCenters(),result.columnScales(),result.direction(),result.exactDirection(),weights,corrections);
        return new Result(proof,support,attempts,budget.queries,verified);
    }
    private static Result unresolved(int dimension,String message,Budget budget,CoxLikelihoodExistence.Result last,List<Integer> support,int attempts){
        if(last!=null)return mapped(last,CoxLikelihoodExistence.Status.UNRESOLVED,message,support,budget,attempts,false);
        var proof=new CoxLikelihoodExistence.Result(CoxLikelihoodExistence.Status.UNRESOLVED,message,dimension,0,0,budget.pairs,budget.work,0,Double.NaN,new double[0],new double[0],new double[0],List.of(),List.of(),List.of());return new Result(proof,List.of(),attempts,budget.queries,false);
    }
    private static final class Exhausted extends RuntimeException {private static final long serialVersionUID=1L;Exhausted(String message){super(message);}}
    private static final class Budget {
        final CoxLikelihoodExistence.Options options;long work,pairs,queries;
        Budget(CoxLikelihoodExistence.Options options){this.options=options;}
        long remainingWork(){return options.maximumWork()-work;}long remainingPairs(){return options.maximumPairVisits()-pairs;}
        void spend(long units){if(units<0||units>remainingWork())throw new Exhausted("adaptive likelihood work bound reached");work+=units;}
        void bits(BigInteger value){spend(1);if(value.bitLength()>options.maximumCertificateBits())throw new Exhausted("direction certificate bit bound reached");}
    }
}
