/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.*;

/**
 * Deterministic maximal unrelated-set selection from a supplied relatedness graph.
 * Eligibility is applied before initial degrees are counted. Participants are
 * visited in increasing initial degree order; a retained participant excludes
 * its neighbors. This is a maximal independent set, not a maximum-cardinality
 * solution. It does not calculate a GRM or infer the completeness or scale of
 * the supplied pair data.
 */
public final class RelatedSampleSelection {
    private RelatedSampleSelection() { }
    /** Explicit resolution of equal initial degrees; the source paper does not specify ties. */
    public enum TiePolicy { INPUT_ORDER, LEXICAL_ID }
    /** An unordered supplied pair; self-pairs and duplicate unordered pairs are rejected. */
    public record Pair(String first,String second,double relatedness) { }
    /** Every source participant is retained, excluded by a named neighbor, or ineligible. */
    public record Decision(String sampleId,boolean eligible,int initialDegree,String status,String excludedBy) { }
    /** Immutable complete decisions in source order, plus retained IDs in visitation order. */
    public record Selection(List<String> retained,List<Decision> decisions,int suppliedPairs,
                            int thresholdPairs,int eligibleThresholdPairs,double cutoff,TiePolicy ties) {
        public Selection {retained=List.copyOf(retained);decisions=List.copyOf(decisions);}
    }
    /**
     * Select using strict {@code relatedness > cutoff}; values equal to the
     * cutoff do not form an edge. The caller must establish that every relevant
     * pair above this cutoff is present, and that values use the declared
     * relationship scale. Missing pairs are not independently verified here.
     *
     * @param sampleIds complete unique source order, including isolated participants
     * @param eligible explicit phenotype/covariate-eligible subset; no missingness is inferred
     * @param pairs supplied unordered pairs, including optional values below the cutoff
     * @param cutoff finite nonnegative threshold on the supplied relationship scale
     * @param ties explicit deterministic rule for equal initial degrees
     * @return full source-order decisions with an auditable retained blocking participant
     */
    public static Selection select(List<String> sampleIds,Set<String> eligible,List<Pair> pairs,
                                   double cutoff,TiePolicy ties) {
        Objects.requireNonNull(sampleIds,"sampleIds");Objects.requireNonNull(eligible,"eligible");Objects.requireNonNull(pairs,"pairs");Objects.requireNonNull(ties,"ties");
        if(sampleIds.isEmpty()||sampleIds.size()>1_000_000||pairs.size()>10_000_000||!Double.isFinite(cutoff)||cutoff<0)throw new IllegalArgumentException("Require1..1000000participants,at most10000000pairs and a finite nonnegative cutoff");
        int n=sampleIds.size();var index=new HashMap<String,Integer>();for(int i=0;i<n;i++){String id=sampleIds.get(i);if(id==null||id.isBlank()||!id.equals(id.strip())||id.chars().anyMatch(Character::isISOControl)||index.put(id,i)!=null)throw new IllegalArgumentException("Source sample IDs must be unique,nonblank,single-line and without surrounding whitespace");}
        if(!index.keySet().containsAll(eligible))throw new IllegalArgumentException("Eligible sample IDs are absent from the complete source universe");
        boolean[] use=new boolean[n];for(String id:eligible)use[index.get(id)]=true;int[] degree=new int[n],first=new int[pairs.size()],second=new int[pairs.size()];long[] identities=new long[pairs.size()];int thresholdPairs=0,edges=0;
        for(int i=0;i<pairs.size();i++){
            Pair pair=Objects.requireNonNull(pairs.get(i),"pair");Integer a=index.get(pair.first()),b=index.get(pair.second());if(a==null||b==null||a.equals(b)||!Double.isFinite(pair.relatedness()))throw new IllegalArgumentException("Pairs require two distinct known source IDs and finite relatedness");int lo=Math.min(a,b),hi=Math.max(a,b);identities[i]=((long)lo<<32)|Integer.toUnsignedLong(hi);if(pair.relatedness()>cutoff){thresholdPairs++;if(use[a]&&use[b]){first[edges]=a;second[edges]=b;degree[a]=Math.incrementExact(degree[a]);degree[b]=Math.incrementExact(degree[b]);edges++;}}
        }
        Arrays.sort(identities);for(int i=1;i<identities.length;i++)if(identities[i]==identities[i-1])throw new IllegalArgumentException("Duplicate unordered relatedness pair;do not silently average or keep the last value");
        int[] offset=new int[n+1];for(int i=0;i<n;i++)offset[i+1]=Math.addExact(offset[i],degree[i]);int[] adjacent=new int[offset[n]],cursor=offset.clone();for(int i=0;i<edges;i++){adjacent[cursor[first[i]]++]=second[i];adjacent[cursor[second[i]]++]=first[i];}
        int[] tieRank=new int[n],byRank=new int[n];if(ties==TiePolicy.INPUT_ORDER){for(int i=0;i<n;i++)tieRank[i]=byRank[i]=i;}else{Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;Arrays.sort(order,Comparator.comparing(sampleIds::get));for(int i=0;i<n;i++){tieRank[order[i]]=i;byRank[i]=order[i];}}
        long[] visits=new long[eligible.size()];int count=0;for(int i=0;i<n;i++)if(use[i])visits[count++]=((long)degree[i]<<32)|Integer.toUnsignedLong(tieRank[i]);Arrays.sort(visits);int[] blockedBy=new int[n];Arrays.fill(blockedBy,-1);boolean[] selected=new boolean[n];var retained=new ArrayList<String>();
        for(long visit:visits){int i=byRank[(int)visit];if(blockedBy[i]>=0)continue;selected[i]=true;retained.add(sampleIds.get(i));for(int k=offset[i];k<offset[i+1];k++){int neighbor=adjacent[k];if(selected[neighbor])throw new IllegalStateException("Retained participants share a supplied above-cutoff edge");if(blockedBy[neighbor]<0)blockedBy[neighbor]=i;}}
        var decisions=new ArrayList<Decision>();for(int i=0;i<n;i++){if(use[i]&&!selected[i]&&(blockedBy[i]<0||!selected[blockedBy[i]]))throw new IllegalStateException("Selection is not maximal in the eligible graph");decisions.add(new Decision(sampleIds.get(i),use[i],degree[i],!use[i]?"ineligible":selected[i]?"retained":"excluded-related",blockedBy[i]<0?"":sampleIds.get(blockedBy[i])));}
        return new Selection(retained,decisions,pairs.size(),thresholdPairs,edges,cutoff,ties);
    }
}
