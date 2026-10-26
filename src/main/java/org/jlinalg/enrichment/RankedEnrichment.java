/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Weighted running-sum enrichment with explicit gene-set or shared model-ranking nulls. */
public final class RankedEnrichment {
    private RankedEnrichment() { }

    public record Rank(String gene, double statistic) {
        public Rank {
            if (gene == null || gene.isBlank() || !Double.isFinite(statistic))
                throw new IllegalArgumentException("Require nonblank gene IDs and finite rank statistics");
        }
    }
    public record Options(double exponent, int minimumSize, int maximumSize,
            int permutations, int maximumExactStates, long seed,
            long maximumWork, boolean allowLexicographicTies,
            EnrichmentAnalysis.Fdr adjustment) {
        public Options {
            if (!Double.isFinite(exponent) || exponent < 0 || exponent > 2
                    || minimumSize < 1 || maximumSize < minimumSize
                    || permutations < 1 || maximumExactStates < 0
                    || maximumWork < 1 || adjustment == null)
                throw new IllegalArgumentException("Invalid preranked enrichment controls");
        }
    }
    public record Result(String id, String name, int size, String status,
            double enrichmentScore, double normalizedScore, double pValue,
            double adjustedP, String nullMethod, int nullStates,
            int sameSignStates, int tailStates, double pResolution,
            double pMonteCarloLower, double pMonteCarloUpper,
            List<String> leadingEdge, List<String> members) {
        public Result { leadingEdge = List.copyOf(leadingEdge); members = List.copyOf(members); }
    }
    public record Analysis(List<Rank> ranking, List<Result> results,
            Map<String,String> dispositions, boolean completeFamily,
            int tiedGenes, long workUnits) {
        public Analysis {
            ranking = List.copyOf(ranking); results = List.copyOf(results);
            dispositions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(dispositions));
        }
    }
    private record Walk(double value, int extremum) { }
    private record NullScores(double[] values, boolean exact, boolean undefined) { }

    /**
     * Uses complete model-specific null rankings, with one shared draw across
     * all genes and terms. The caller must generate the declared joint null;
     * this method validates its finite, identical gene universe, not its model.
     * Draws are consumed once and are never retained as a draws-by-genes matrix.
     */
    public static Analysis analyzeRankings(List<Rank> input, List<GeneSet> sets,
            Options options, int draws, boolean exact, String method,
            java.util.function.IntFunction<List<Rank>> nullRanking) {
        if(draws<1||draws>1_000_000||method==null||method.isBlank()||nullRanking==null)
            throw new IllegalArgumentException("Require a bounded, named complete null-ranking ensemble");
        var ranking=ordered(input,options);int n=ranking.size();
        var universe=ranking.stream().map(Rank::gene).collect(java.util.stream.Collectors.toSet());
        var descriptions=new LinkedHashMap<String,GeneSet>();
        var eligible=new LinkedHashMap<String,Set<String>>();var dispositions=new LinkedHashMap<String,String>();
        long perDraw=(long)n*(4+32-Integer.numberOfLeadingZeros(n));
        for(GeneSet set:sets){
            if(descriptions.putIfAbsent(set.id(),set)!=null)throw new IllegalArgumentException("Duplicate gene-set ID: "+set.id());
            var members=new HashSet<>(set.genes());members.retainAll(universe);int k=members.size();
            String disposition=k<options.minimumSize()?"below_minimum_ranked_members":k>options.maximumSize()?"above_maximum_ranked_members":k==n?"covers_complete_ranking_no_complement":"eligible";
            dispositions.put(set.id(),disposition);
            if(disposition.equals("eligible")){eligible.put(set.id(),members);perDraw=addWork(perDraw,(long)k*(3+32-Integer.numberOfLeadingZeros(k)),options.maximumWork());}
        }
        if(perDraw>options.maximumWork()/(draws+1L))throw new IllegalArgumentException("Complete model-null GSEA family exceeds maximumWork; no genes, draws or terms were truncated");
        long work=perDraw*(draws+1L);int m=eligible.size();
        Walk[] observed=new Walk[m];int[][] members=new int[m][];int[] same=new int[m],tail=new int[m];double[] absolute=new double[m];boolean[] undefined=new boolean[m];
        var ids=new ArrayList<>(eligible.keySet());var positions=positions(ranking);double[] weights=weights(ranking,options.exponent());
        for(int j=0;j<m;j++){members[j]=indices(eligible.get(ids.get(j)),positions);observed[j]=walk(weights,members[j]);}
        // Even an empty eligible term family validates every declared ranking.
        for(int draw=0;draw<draws;draw++){
            var simulated=ordered(nullRanking.apply(draw),options);
            if(simulated.size()!=n||!simulated.stream().map(Rank::gene).collect(java.util.stream.Collectors.toSet()).equals(universe))throw new IllegalArgumentException("Null ranking "+(draw+1)+" must contain exactly the complete observed gene universe");
            var simulatedPositions=positions(simulated);double[] simulatedWeights=weights(simulated,options.exponent());
            for(int j=0;j<m;j++){
                double value=walk(simulatedWeights,indices(eligible.get(ids.get(j)),simulatedPositions)).value();
                if(!Double.isFinite(value)){undefined[j]=true;continue;}
                if(observed[j].value()>=0?value>=0:value<0){same[j]++;absolute[j]+=Math.abs(value);if(observed[j].value()>=0?value>=observed[j].value()-1e-12:value<=observed[j].value()+1e-12)tail[j]++;}
            }
        }
        var results=new ArrayList<Result>();boolean complete=true;
        for(int j=0;j<m;j++){
            Walk score=observed[j];String status=!Double.isFinite(score.value())?"zero_observed_hit_weight":undefined[j]?"undefined_zero_weight_null":same[j]==0?"no_same_sign_null":"tested";
            boolean tested=status.equals("tested");complete&=tested;
            double p=tested?(exact?(double)tail[j]/same[j]:(tail[j]+1.0)/(same[j]+1.0)):Double.NaN;
            double normalized=tested&&absolute[j]>0?score.value()/(absolute[j]/same[j]):Double.NaN;
            double[] interval=tested?(exact?new double[]{p,p}:wilson(tail[j],same[j])):new double[]{Double.NaN,Double.NaN};
            var leading=new ArrayList<String>();var genes=new ArrayList<String>();
            for(int at:members[j]){String gene=ranking.get(at).gene();genes.add(gene);if(Double.isFinite(score.value())&&(score.value()>=0?at<=score.extremum():at>score.extremum()))leading.add(gene);}
            GeneSet set=descriptions.get(ids.get(j));results.add(new Result(set.id(),set.name(),genes.size(),status,score.value(),normalized,p,Double.NaN,method,draws,same[j],tail[j],tested?1.0/(same[j]+(exact?0:1)):Double.NaN,interval[0],interval[1],leading,genes));
        }
        if(complete){double[] adjusted=EnrichmentAnalysis.adjustLog(results.stream().mapToDouble(r->Math.log(r.pValue())).toArray(),options.adjustment());var revised=new ArrayList<Result>();for(int j=0;j<m;j++){var r=results.get(j);revised.add(new Result(r.id(),r.name(),r.size(),r.status(),r.enrichmentScore(),r.normalizedScore(),r.pValue(),options.adjustment()==EnrichmentAnalysis.Fdr.NONE?r.pValue():Math.exp(adjusted[j]),r.nullMethod(),r.nullStates(),r.sameSignStates(),r.tailStates(),r.pResolution(),r.pMonteCarloLower(),r.pMonteCarloUpper(),r.leadingEdge(),r.members()));}results=revised;}
        return new Analysis(ranking,results,dispositions,complete,ties(ranking),work);
    }
    private static List<Rank> ordered(List<Rank> input,Options options){
        if(input==null||input.size()<2)throw new IllegalArgumentException("Require at least two ranked genes in every observed/null ranking");
        var result=new ArrayList<>(input);var seen=new HashSet<String>();for(var r:result)if(r==null||!seen.add(r.gene()))throw new IllegalArgumentException("Duplicate/null gene in a complete ranking");
        result.sort(Comparator.comparingDouble(Rank::statistic).reversed().thenComparing(Rank::gene));
        if(!options.allowLexicographicTies()&&ties(result)>0)throw new IllegalArgumentException("Tied observed/null ranking values need an explicit lexicographic-tie protocol");return result;
    }
    private static int ties(List<Rank> ranking){int count=0;for(int a=0;a<ranking.size();){int b=a+1;while(b<ranking.size()&&ranking.get(b).statistic()==ranking.get(a).statistic())b++;if(b-a>1)count+=b-a;a=b;}return count;}
    private static Map<String,Integer> positions(List<Rank> ranking){var result=new HashMap<String,Integer>();for(int i=0;i<ranking.size();i++)result.put(ranking.get(i).gene(),i);return result;}
    private static int[] indices(Set<String> genes,Map<String,Integer> positions){return genes.stream().mapToInt(positions::get).sorted().toArray();}
    private static double[] weights(List<Rank> ranking,double exponent){double maximum=ranking.stream().mapToDouble(r->Math.abs(r.statistic())).max().orElse(0);double[] result=new double[ranking.size()];for(int i=0;i<result.length;i++)result[i]=exponent==0?1:maximum==0?0:Math.pow(Math.abs(ranking.get(i).statistic())/maximum,exponent);return result;}

    public static Analysis analyze(List<Rank> input, List<GeneSet> sets, Options options) {
        if (input.size() < 2) throw new IllegalArgumentException("Require at least two ranked genes");
        var ranking = new ArrayList<>(input);
        var positions = new HashMap<String,Integer>();
        for (Rank rank : ranking) if (positions.putIfAbsent(rank.gene(), 0) != null)
            throw new IllegalArgumentException("Duplicate ranked gene: " + rank.gene());
        ranking.sort(Comparator.comparingDouble(Rank::statistic).reversed().thenComparing(Rank::gene));
        int tied = 0;
        for (int start=0; start<ranking.size();) {
            int end=start+1;
            while(end<ranking.size() && ranking.get(end).statistic()==ranking.get(start).statistic()) end++;
            if(end-start>1) tied+=end-start;
            start=end;
        }
        if(tied>0 && !options.allowLexicographicTies())
            throw new IllegalArgumentException("Tied ranking values need an explicit lexicographic-tie protocol or a better resolved ranking");
        for(int i=0;i<ranking.size();i++) positions.put(ranking.get(i).gene(),i);
        double maximum=ranking.stream().mapToDouble(r->Math.abs(r.statistic())).max().orElse(0);
        double[] weights=new double[ranking.size()];
        for(int i=0;i<weights.length;i++) weights[i]=options.exponent()==0?1:maximum==0?0:Math.pow(Math.abs(ranking.get(i).statistic())/maximum,options.exponent());
        var eligible=new LinkedHashMap<String,int[]>();
        var descriptions=new LinkedHashMap<String,GeneSet>();
        var dispositions=new LinkedHashMap<String,String>();
        var sizes=new java.util.TreeMap<Integer,Integer>();
        long work=0;
        for(GeneSet set:sets) {
            if(descriptions.putIfAbsent(set.id(),set)!=null) throw new IllegalArgumentException("Duplicate gene-set ID: "+set.id());
            int[] members=set.genes().stream().filter(positions::containsKey).mapToInt(positions::get).sorted().toArray();
            String disposition=members.length<options.minimumSize()?"below_minimum_ranked_members":members.length>options.maximumSize()?"above_maximum_ranked_members":members.length==ranking.size()?"covers_complete_ranking_no_complement":"eligible";
            dispositions.put(set.id(),disposition);
            if(disposition.equals("eligible")){eligible.put(set.id(),members);sizes.merge(members.length,1,Integer::sum);work=addWork(work,members.length,options.maximumWork());}
        }
        for(int size:sizes.keySet()) {
            long exact=combinations(ranking.size(),size,options.maximumExactStates());
            long states=exact<=options.maximumExactStates()?exact:options.permutations();
            // Cached null generation is shared, but every term still scans every
            // null score for its own signed tail and normalization denominator.
            long perState=Math.max(1,(long)size*(2+32-Integer.numberOfLeadingZeros(size)))+sizes.get(size);
            if(states>options.maximumWork()/perState) throw new IllegalArgumentException("Complete GSEA null family exceeds maximumWork; no terms were selected for convenience");
            work=addWork(work,states*perState,options.maximumWork());
        }
        Map<Integer,NullScores> nulls=new HashMap<>();
        for(int size:sizes.keySet()) nulls.put(size,generateNull(weights,size,options));
        var results=new ArrayList<Result>();boolean complete=true;
        for(var entry:eligible.entrySet()) {
            int[] members=entry.getValue();Walk observed=walk(weights,members);
            NullScores distribution=nulls.get(members.length);int signStates=0,tail=0;double absolute=0;
            for(double value:distribution.values()) if(Double.isFinite(value) && (observed.value()>=0?value>=0:value<0)) {
                signStates++;absolute+=Math.abs(value);
                if(observed.value()>=0?value>=observed.value()-1e-12:value<=observed.value()+1e-12) tail++;
            }
            String status=!Double.isFinite(observed.value())?"zero_observed_hit_weight":distribution.undefined()?"undefined_zero_weight_null":signStates==0?"no_same_sign_null":"tested";
            boolean tested=status.equals("tested");complete&=tested;
            double p=tested?(distribution.exact()?(double)tail/signStates:(tail+1.0)/(signStates+1.0)):Double.NaN;
            double normalized=tested&&absolute>0?observed.value()/(absolute/signStates):Double.NaN;
            double[] interval=tested?(distribution.exact()?new double[]{p,p}:wilson(tail,signStates)):new double[]{Double.NaN,Double.NaN};
            var leading=new ArrayList<String>();var genes=new ArrayList<String>();
            for(int member:members) {
                String gene=ranking.get(member).gene();genes.add(gene);
                if(Double.isFinite(observed.value()) && (observed.value()>=0?member<=observed.extremum():member>observed.extremum())) leading.add(gene);
            }
            GeneSet set=descriptions.get(entry.getKey());
            results.add(new Result(set.id(),set.name(),members.length,status,observed.value(),normalized,p,Double.NaN,
                distribution.exact()?"exact_gene_set_enumeration":"monte_carlo_gene_set",distribution.values().length,signStates,tail,
                tested?1.0/(signStates+(distribution.exact()?0:1)):Double.NaN,interval[0],interval[1],leading,genes));
        }
        if(complete) {
            double[] adjusted=EnrichmentAnalysis.adjustLog(results.stream().mapToDouble(r->Math.log(r.pValue())).toArray(),options.adjustment());
            var revised=new ArrayList<Result>();
            for(int i=0;i<results.size();i++){var r=results.get(i);revised.add(new Result(r.id(),r.name(),r.size(),r.status(),r.enrichmentScore(),r.normalizedScore(),r.pValue(),options.adjustment()==EnrichmentAnalysis.Fdr.NONE?r.pValue():Math.exp(adjusted[i]),r.nullMethod(),r.nullStates(),r.sameSignStates(),r.tailStates(),r.pResolution(),r.pMonteCarloLower(),r.pMonteCarloUpper(),r.leadingEdge(),r.members()));}
            results=revised;
        }
        return new Analysis(ranking,results,dispositions,complete,tied,work);
    }

    /** Running sum is evaluated at hit and miss extrema, without scanning every gene. */
    private static Walk walk(double[] weights,int[] hits) {
        double mass=0;for(int hit:hits)mass+=weights[hit];
        if(!(mass>0))return new Walk(Double.NaN,-1);
        int misses=weights.length-hits.length,previous=-1;double value=0,maximum=0,minimum=0;int maxAt=-1,minAt=-1;
        for(int hit:hits) {
            value-=(double)(hit-previous-1)/misses;
            if(value<minimum){minimum=value;minAt=hit-1;}
            value+=weights[hit]/mass;
            if(value>maximum){maximum=value;maxAt=hit;}
            previous=hit;
        }
        // The final miss run returns to zero and cannot be an additional extremum.
        return maximum>=-minimum-1e-12?new Walk(maximum,maxAt):new Walk(minimum,minAt);
    }

    private static NullScores generateNull(double[] weights,int size,Options options) {
        long count=combinations(weights.length,size,options.maximumExactStates());
        boolean exact=count<=options.maximumExactStates();
        double[] values=new double[exact?(int)count:options.permutations()];boolean undefined=false;
        int[] members=new int[size];
        if(exact) {
            for(int i=0;i<size;i++)members[i]=i;
            for(int iteration=0;iteration<values.length;iteration++) {
                values[iteration]=walk(weights,members).value();undefined|=!Double.isFinite(values[iteration]);
                int at=size-1;while(at>=0&&members[at]==weights.length-size+at)at--;
                if(at>=0){members[at]++;for(int i=at+1;i<size;i++)members[i]=members[i-1]+1;}
            }
        } else {
            Random random=new Random(options.seed()^(0x9e3779b97f4a7c15L*size));
            int[] pool=new int[weights.length];for(int i=0;i<pool.length;i++)pool[i]=i;
            int[] swaps=new int[size];
            for(int iteration=0;iteration<values.length;iteration++) {
                for(int j=0;j<size;j++){int selected=j+random.nextInt(pool.length-j);swaps[j]=selected;int value=pool[j];pool[j]=pool[selected];pool[selected]=value;members[j]=pool[j];}
                Arrays.sort(members);values[iteration]=walk(weights,members).value();undefined|=!Double.isFinite(values[iteration]);
                for(int j=size-1;j>=0;j--){int value=pool[j];pool[j]=pool[swaps[j]];pool[swaps[j]]=value;}
            }
        }
        return new NullScores(values,exact,undefined);
    }
    private static long combinations(int n,int k,int cap) {
        if(cap==0)return 1;
        k=Math.min(k,n-k);long count=1;
        for(int i=1;i<=k;i++){if(count>(long)cap*i/(n-k+i))return (long)cap+1;count=count*(n-k+i)/i;}
        return count;
    }
    private static long addWork(long current,long next,long maximum) {
        if(next>maximum-current)throw new IllegalArgumentException("Complete GSEA family exceeds maximumWork; do not truncate favorable terms");
        return current+next;
    }
    private static double[] wilson(int successes,int count) {
        double z=1.959963984540054,z2=z*z,p=(double)successes/count;
        double center=(p+z2/(2*count))/(1+z2/count);
        double half=z*Math.sqrt(p*(1-p)/count+z2/(4.0*count*count))/(1+z2/count);
        return new double[]{Math.max(0,center-half),Math.min(1,center+half)};
    }
}
