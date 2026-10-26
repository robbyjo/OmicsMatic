/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.network;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Conditional target-label randomization of a fixed reference-edge pattern.
 * Exact tails include every legal bijection. Random tails use independent
 * uniform within-stratum permutations with replacement and (b+1)/(B+1).
 * Validity requires the specified label-exchangeability null; this is not a
 * sampling model for participants, a WGCNA Z-summary, or causal inference.
 */
public final class NetworkLabelPermutation {
    private NetworkLabelPermutation() { }
    public record Edge(int a, int b, int sign) { }
    public record Plan(String method, long states, boolean groupSizeKnown, long work) { }
    public record Result(int observed, long states, long extreme, double pValue,
            double resolution, double tailLower95, double tailUpper95,
            Map<Integer,Long> histogram) { }

    public static Plan plan(int nodes, int patternEdges, int targetEdges, int[][] strata,
            String method, int exactMaximum, int draws) {
        validateGroups(nodes,strata);
        if (patternEdges < 0 || targetEdges < 0 || exactMaximum < 1 || draws < 1
                || !List.of("auto","exact","monte_carlo").contains(method))
            throw new IllegalArgumentException("Invalid label-randomization plan");
        long count=1;
        outer: for(int[] group:strata) for(int j=2;j<=group.length;j++) {
            if(count>exactMaximum/j){count=(long)exactMaximum+1;break outer;}
            count*=j;
        }
        boolean exact=!method.equals("monte_carlo")&&count<=exactMaximum;
        if(method.equals("exact")&&!exact)throw new IllegalArgumentException("Exact label-permutation group exceeds exact_maximum");
        long states=exact?count:draws;
        long work=(long)targetEdges+patternEdges+states*((long)nodes+patternEdges);
        return new Plan(exact?"exact":"monte_carlo",states,count<=exactMaximum,work);
    }

    public static Result test(int nodes, List<Edge> pattern, List<Edge> target,
            int[][] strata, boolean signed, Plan plan, long seed) {
        validateGroups(nodes,strata);
        var reference=List.copyOf(pattern);
        var targetSigns=new HashMap<Long,Integer>();
        for(Edge edge:target){validEdge(nodes,edge);if(targetSigns.putIfAbsent(key(edge.a,edge.b),edge.sign)!=null)throw new IllegalArgumentException("Duplicate target edge");}
        var unique=new HashSet<Long>();for(Edge edge:reference){validEdge(nodes,edge);if(!unique.add(key(edge.a,edge.b)))throw new IllegalArgumentException("Duplicate reference edge");}
        if(reference.isEmpty())throw new IllegalArgumentException("A reference edge pattern is required");
        // Recompute the exact group-size contract; callers cannot supply an arbitrary prefix as exact.
        if(plan.method.equals("exact")) {
            if(plan.states<1||plan.states>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid exact state count");
            Plan checked=plan(nodes,reference.size(),target.size(),strata,"exact",(int)plan.states,1);
            if(checked.states!=plan.states)throw new IllegalArgumentException("Exact plan does not enumerate the full group");
        } else if(!plan.method.equals("monte_carlo")||plan.states<1||plan.states>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid sampled permutation plan");
        int[] identity=new int[nodes];for(int i=0;i<nodes;i++)identity[i]=i;
        int observed=statistic(reference,targetSigns,identity,signed);
        int[][] groups=Arrays.stream(strata).map(int[]::clone).toArray(int[][]::new);
        for(int[] group:groups)Arrays.sort(group);
        int[][] values=Arrays.stream(groups).map(int[]::clone).toArray(int[][]::new);
        int[] permutation=identity.clone();var random=new Random(seed);var histogram=new TreeMap<Integer,Long>();
        long extreme=0;
        for(long draw=0;draw<plan.states;draw++) {
            if(plan.method.equals("monte_carlo"))for(int g=0;g<groups.length;g++){
                System.arraycopy(groups[g],0,values[g],0,groups[g].length);
                for(int j=values[g].length-1;j>0;j--){int k=random.nextInt(j+1),v=values[g][j];values[g][j]=values[g][k];values[g][k]=v;}
            }
            for(int g=0;g<groups.length;g++)for(int j=0;j<groups[g].length;j++)permutation[groups[g][j]]=values[g][j];
            int statistic=statistic(reference,targetSigns,permutation,signed);
            histogram.merge(statistic,1L,Long::sum);if(statistic>=observed)extreme++;
            if(plan.method.equals("exact")&&draw+1<plan.states){
                boolean advanced=false;
                for(int g=values.length-1;g>=0;g--){if(next(values[g])){advanced=true;break;}System.arraycopy(groups[g],0,values[g],0,groups[g].length);}
                if(!advanced)throw new IllegalArgumentException("Exact group exhausted before declared states");
            }
        }
        boolean exact=plan.method.equals("exact");
        double p=exact?(double)extreme/plan.states:(double)(extreme+1)/(plan.states+1);
        double lower=exact?p:extreme==0?0:jdistlib.Beta.quantile(.025,extreme,plan.states-extreme+1,true,false);
        double upper=exact?p:extreme==plan.states?1:jdistlib.Beta.quantile(.975,extreme+1,plan.states-extreme,true,false);
        return new Result(observed,plan.states,extreme,p,1.0/(plan.states+(exact?0:1)),lower,upper,Map.copyOf(histogram));
    }
    private static int statistic(List<Edge> pattern,Map<Long,Integer> target,int[] permutation,boolean signed){
        int count=0;for(Edge edge:pattern){Integer sign=target.get(key(permutation[edge.a],permutation[edge.b]));if(sign!=null&&(!signed||sign==edge.sign))count++;}return count;
    }
    private static long key(int a,int b){return ((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL);}
    private static void validEdge(int n,Edge e){if(e.a<0||e.a>=n||e.b<0||e.b>=n||e.a==e.b||Math.abs(e.sign)!=1)throw new IllegalArgumentException("Edges require distinct valid nodes and signs +/-1");}
    private static void validateGroups(int nodes,int[][] groups){
        if(nodes<2||nodes>100000||groups==null)throw new IllegalArgumentException("Invalid permutation universe");
        boolean[] seen=new boolean[nodes];int count=0;
        for(int[] group:groups){if(group==null||group.length==0)throw new IllegalArgumentException("Empty permutation stratum");for(int i:group){if(i<0||i>=nodes||seen[i])throw new IllegalArgumentException("Strata must partition the universe");seen[i]=true;count++;}}
        if(count!=nodes)throw new IllegalArgumentException("Strata do not cover the universe");
    }
    private static boolean next(int[] p){int i=p.length-2;while(i>=0&&p[i]>=p[i+1])i--;if(i<0)return false;int j=p.length-1;while(p[j]<=p[i])j--;int swap=p[i];p[i]=p[j];p[j]=swap;for(int a=i+1,b=p.length-1;a<b;a++,b--){swap=p[a];p[a]=p[b];p[b]=swap;}return true;}
}
