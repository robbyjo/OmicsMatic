/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The reported dreamlet/P104 independent-marginal proxy composite. Its source
 * score is retained separately from stable log diagnostics. It is not numerical
 * integration of a joint posterior, an independence test or a calibrated P value.
 */
public final class MashCompositeScores {
    private MashCompositeScores() { }
    public enum Mode { ALL, AT_LEAST_ONE }
    public enum Scale { SIGN_PROBABILITY, LFSR }
    public enum Missing { REJECT, SOURCE_ZERO_PROBABILITY }
    public enum Assumption { INDEPENDENT_MARGINAL_PROXIES }
    public enum State { DEFINED, EXACT_ZERO, SOURCE_ROUNDING_TO_ZERO, UNDERFLOW_TO_ZERO }
    /**
     * Explicit zero-based selectors. Duplicates and overlap retain literal source
     * multiplication; diagnostics identify them. Unlisted conditions are ignored.
     * ALL with an empty inclusion has factor1; AT_LEAST_ONE has factor0.
     */
    public record Pattern(String name,int[] include,int[] exclude,Mode mode) {
        public Pattern {Objects.requireNonNull(name);Objects.requireNonNull(include);Objects.requireNonNull(exclude);Objects.requireNonNull(mode);if(name.isBlank())throw new IllegalArgumentException("Composite pattern name is required");include=include.clone();exclude=exclude.clone();}
        @Override public int[] include(){return include.clone();}
        @Override public int[] exclude(){return exclude.clone();}
    }
    /** Source rounded score and factors; stableLogScore diagnoses arithmetic loss. */
    public record Score(double sourceScore,double sourceInclusion,double sourceExclusion,double stableLogScore,
                        State state,int missingSelectedOccurrences,int roundedMarginalOccurrences,
                        int repeatedSelectorOccurrences,int overlappingConditions,boolean numericalLoss) {
        public boolean passes(double threshold,MashSharing.Boundary boundary){MashSharing.probability(threshold,"composite threshold");Objects.requireNonNull(boundary);return boundary==MashSharing.Boundary.STRICT?sourceScore>threshold:sourceScore>=threshold;}
    }
    public record Result(Score[][] scores,long missingMatrixCells,Assumption assumption) {
        public Result {scores=copy(scores);Objects.requireNonNull(assumption);}
        @Override public Score[][] scores(){return copy(scores);}
        public Score score(int row,int pattern){return scores[row][pattern];}
    }
    /**
     * p=1-LFSR for LFSR input; otherwise p is supplied directly. Original source
     * replaces NA/NaN p with0 only when explicitly requested here. It uses
     * prod(p[include]) or 1-prod(1-p[include]), multiplied by prod(1-p[exclude]).
     * Products are rounded once from exact binary factors before the literal
     * double complement/multiplication. Stable logs use log1p/expm1 on the
     * original input scale, without replacing the declared source score.
     */
    public static Result calculate(double[][] input,int conditions,List<Pattern> patterns,Scale scale,Missing missing,
                                   Assumption assumption,MashSharing.Limits limits,Runnable check){
        Objects.requireNonNull(input);Objects.requireNonNull(patterns);Objects.requireNonNull(scale);Objects.requireNonNull(missing);Objects.requireNonNull(assumption);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        int rows=input.length;if(conditions<0||conditions>limits.conditions()||rows>limits.rows()||patterns.isEmpty())throw new IllegalArgumentException("Invalid complete composite dimensions or empty pattern family");
        long cells=Math.multiplyExact((long)rows,conditions),selectorCells=0,patternWork=0,metadata=Math.addExact(65536+128L*conditions,128L*patterns.size());
        if(Math.addExact(cells,patterns.size())>limits.inputCells()||metadata>limits.ownedBytes()||metadata>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete composite family metadata exceeds capacity");
        var names=new HashSet<String>();int[] repeated=new int[patterns.size()],overlap=new int[patterns.size()];
        for(int k=0;k<patterns.size();k++){
            check.run();Pattern p=Objects.requireNonNull(patterns.get(k));if(!names.add(p.name))throw new IllegalArgumentException("Composite pattern names must be unique");var a=new HashSet<Integer>();var b=new HashSet<Integer>();
            long size=Math.addExact(p.include.length,(long)p.exclude.length);if(size>Integer.MAX_VALUE)throw new IllegalArgumentException("Selector occurrence count exceeds supported integer range");
            for(int c:p.include){check.run();if(c<0||c>=conditions)throw new IllegalArgumentException("Unknown included condition index");if(!a.add(c))repeated[k]++;}
            for(int c:p.exclude){check.run();if(c<0||c>=conditions)throw new IllegalArgumentException("Unknown excluded condition index");if(!b.add(c))repeated[k]++;}
            for(int c:a)if(b.contains(c))overlap[k]++;
            selectorCells=Math.addExact(selectorCells,size);patternWork=Math.addExact(patternWork,Math.multiplyExact(size+1,size+1));
        }
        long work=Math.addExact(cells,Math.multiplyExact(rows+1L,patternWork)),bytes=Math.addExact(Math.addExact(metadata,64L*selectorCells),Math.multiplyExact(160L*rows,patterns.size()));
        if(Math.addExact(Math.addExact(cells,selectorCells),patterns.size())>limits.inputCells()||work>limits.work()||bytes>limits.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete composite family exceeds declared cells/work/storage or half the heap; no prefix returned");
        long missingCount=0;
        for(double[] row:input){check.run();if(row==null||row.length!=conditions)throw new IllegalArgumentException("Complete composite matrix is ragged");for(double value:row){if(Double.isNaN(value)){if(missing==Missing.REJECT)throw new IllegalArgumentException("Missing marginal probability requires explicit SOURCE_ZERO_PROBABILITY policy");missingCount++;}else MashSharing.probability(value,"marginal input");}}
        Score[][] result=new Score[rows][patterns.size()];
        for(int r=0;r<rows;r++)for(int k=0;k<patterns.size();k++){
            check.run();Pattern p=patterns.get(k);BigDecimal included=BigDecimal.ONE,excluded=BigDecimal.ONE;var includeLog=new LogSum();var excludeLog=new LogSum();int absent=0,rounding=0;
            for(int c:p.include){
                check.run();double raw=input[r][c];boolean absentInput=Double.isNaN(raw);double q=absentInput?0:scale==Scale.LFSR?1-raw:raw;
                if(absentInput)absent++;if(!absentInput&&scale==Scale.LFSR&&raw>0&&q==1)rounding++;
                double factor=p.mode==Mode.ALL?q:1-q;included=included.multiply(new BigDecimal(factor));
                if(!absentInput&&p.mode==Mode.AT_LEAST_ONE&&q>0&&factor==1)rounding++;
                includeLog.add(p.mode==Mode.ALL?presentLog(raw,scale,absentInput):absentLog(raw,scale,absentInput));
            }
            for(int c:p.exclude){
                check.run();double raw=input[r][c];boolean absentInput=Double.isNaN(raw);double q=absentInput?0:scale==Scale.LFSR?1-raw:raw,complement=1-q;
                if(absentInput)absent++;if(!absentInput&&scale==Scale.LFSR&&raw>0&&q==1)rounding++;if(q>0&&complement==1)rounding++;
                excluded=excluded.multiply(new BigDecimal(complement));excludeLog.add(absentLog(raw,scale,absentInput));
            }
            double inclusion=p.mode==Mode.ALL?included.doubleValue():1-included.doubleValue(),exclusion=excluded.doubleValue(),score=inclusion*exclusion;
            double li=p.mode==Mode.ALL?includeLog.value():logOneMinusExp(includeLog.value()),le=excludeLog.value();double log=li==Double.NEGATIVE_INFINITY||le==Double.NEGATIVE_INFINITY?Double.NEGATIVE_INFINITY:li+le;
            State state=score!=0?State.DEFINED:log==Double.NEGATIVE_INFINITY?State.EXACT_ZERO:log<Math.log(Double.MIN_VALUE)-Math.log(2)?State.UNDERFLOW_TO_ZERO:State.SOURCE_ROUNDING_TO_ZERO;
            boolean loss=score==0&&Double.isFinite(log)||score>0&&Double.isFinite(log)&&Math.abs(Math.log(score)-log)>1e-12*(1+Math.abs(log));
            if(!Double.isFinite(score)||score<0||score>1||Double.isNaN(log)||log>0)throw new ArithmeticException("Unreportable complete composite score");
            result[r][k]=new Score(score,inclusion,exclusion,log,state,absent,rounding,repeated[k],overlap[k],loss);
        }
        check.run();return new Result(result,missingCount,assumption);
    }
    private static double presentLog(double value,Scale scale,boolean absent){if(absent)return Double.NEGATIVE_INFINITY;return scale==Scale.LFSR?Math.log1p(-value):Math.log(value);}
    private static double absentLog(double value,Scale scale,boolean absent){if(absent)return 0;return scale==Scale.LFSR?Math.log(value):Math.log1p(-value);}
    private static double logOneMinusExp(double value){if(value==Double.NEGATIVE_INFINITY)return 0;if(value==0)return Double.NEGATIVE_INFINITY;return value < -Math.log(2)?Math.log1p(-Math.exp(value)):Math.log(-Math.expm1(value));}
    private static final class LogSum {
        private double sum,correction;private boolean zero;
        void add(double value){if(value==Double.NEGATIVE_INFINITY){zero=true;return;}double y=value-correction,next=sum+y;correction=(next-sum)-y;sum=next;}
        double value(){return zero?Double.NEGATIVE_INFINITY:sum;}
    }
    private static Score[][] copy(Score[][] source){Score[][] result=new Score[source.length][];for(int i=0;i<source.length;i++)result[i]=source[i].clone();return result;}
}
