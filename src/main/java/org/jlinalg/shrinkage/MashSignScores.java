/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Explicit products of marginal sign-concordance scores; no joint-event probability is inferred. */
public final class MashSignScores {
    private MashSignScores() { }
    public record Score(double product,double logProduct,boolean underflowToZero,int nominallySignificant,boolean eligible,boolean exceedsThreshold) { }
    public record Result(Score[][] scores) {
        public Result {scores=copy(scores);}
        @Override public Score[][] scores(){return copy(scores);}
        public Score score(int row,int group){return scores[row][group];}
    }

    /**
     * For each explicit group calculate prod(1-lfsr). P021 additionally uses a
     * strict nominal-P gate in at least two subclasses and a strict score &gt;0.9.
     * These are configurable explicit inputs; the method does not infer missing
     * effects/SE, group membership, nominal P values, independence or a joint
     * posterior. Conditions within each group must be unique; groups may overlap.
     * Products are rounded once from the exact product of binary-double factors;
     * log scores survive representational underflow. Classification uses the
     * rounded product, including equality and underflow, as in numeric R output.
     */
    public static Result calculate(double[][] lfsr,double[][] nominalP,int conditions,List<int[]> groups,double nominalThreshold,int minimumNominal,double scoreThreshold,MashSharing.Limits limits,Runnable check){
        Objects.requireNonNull(lfsr);Objects.requireNonNull(nominalP);Objects.requireNonNull(groups);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        MashSharing.probability(nominalThreshold,"nominal P threshold");MashSharing.probability(scoreThreshold,"composite score threshold");if(groups.isEmpty()||minimumNominal<0)throw new IllegalArgumentException("Declare groups and a nonnegative nominal count gate");
        int rows=lfsr.length;long members=0,work=0,cells=Math.multiplyExact(2L*rows,conditions);
        MashSharing.admit(rows,conditions,cells,0,65536,limits);
        for(int[] group:groups){
            Objects.requireNonNull(group);if(group.length==0||group.length>conditions)throw new IllegalArgumentException("Declare a nonempty unique condition group");
            // No allocation proportional to an unadmitted condition dimension.
            for(int i=0;i<group.length;i++){if(group[i]<0||group[i]>=conditions)throw new IllegalArgumentException("Grouped condition outside complete matrix");for(int j=0;j<i;j++)if(group[i]==group[j])throw new IllegalArgumentException("A condition must occur once per sign-score group");}
            members=Math.addExact(members,group.length);work=Math.addExact(work,Math.multiplyExact(group.length+1L,group.length+1L));
        }
        long bytes=Math.addExact(65536+128L*conditions,Math.multiplyExact(128L*rows,groups.size()));
        MashSharing.admit(rows,conditions,Math.addExact(cells,members),Math.addExact(cells,Math.multiplyExact(rows+1L,work)),bytes,limits);
        MashSharing.validate(lfsr,rows,conditions,true,check);MashSharing.validate(nominalP,rows,conditions,true,check);Score[][] result=new Score[rows][groups.size()];
        for(int r=0;r<rows;r++)for(int g=0;g<groups.size();g++){
            check.run();BigDecimal exact=BigDecimal.ONE;double log=0,compensation=0;int nominal=0;boolean zero=false;
            for(int c:groups.get(g)){
                check.run();double q=1-lfsr[r][c];if(nominalP[r][c]<nominalThreshold)nominal++;
                if(q==0)zero=true;else{
                    exact=exact.multiply(new BigDecimal(q));double value=Math.log(q)-compensation,next=log+value;compensation=(next-log)-value;log=next;
                }
            }
            double product=zero?0:exact.doubleValue();boolean underflow=!zero&&product==0;boolean eligible=nominal>=minimumNominal;
            result[r][g]=new Score(product,zero?Double.NEGATIVE_INFINITY:log,underflow,nominal,eligible,eligible&&product>scoreThreshold);
        }
        check.run();return new Result(result);
    }
    private static Score[][] copy(Score[][] source){Score[][] result=new Score[source.length][];for(int i=0;i<source.length;i++)result[i]=source[i].clone();return result;}
}
