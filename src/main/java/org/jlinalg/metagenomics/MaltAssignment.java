/* Copyright (C) 2018 Daniel H. Huson; Copyright (C) 2026 JLinAlg contributors;
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Native May2018 MALT/MEGAN taxonomic active-hit and unweighted LCA rules.
 * Source: husonlab/megan-ce commit24e70bf6cd0a434ff40a62b98cf0b54d72954908,
 * ActiveMatches, AssignmentUsingLCAForTaxonomy and LCAAddressing (GPL3+).
 * Does not align sequences, read/write RMA6 or apply minimum-support relocation.
 * Duplicate hit addresses and original float/nonfinite comparisons are retained.
 */
public final class MaltAssignment {
    private MaltAssignment(){ }
    public record Match(int taxon,float bitScore,float expected,float percentIdentity,boolean ignored){ }
    public record Filter(double minimumScore,double topPercent,double maximumExpected,float minimumIdentity){ }
    public record Result(int taxon,BitSet active) {
        public Result {active=(BitSet)Objects.requireNonNull(active).clone();}
        @Override public BitSet active(){return (BitSet)active.clone();}
    }
    /** The original Taxonomy active filter aliases its assignment mapper's disabled set. */
    public static Result assign(MaltTaxonomy tree,int totalMatches,List<Match> matches,Filter filter,
            Set<Integer> disabled,float percentToCover,boolean ignoreAncestors) {
        Objects.requireNonNull(tree);Objects.requireNonNull(matches);Objects.requireNonNull(filter);
        Objects.requireNonNull(disabled);
        if(totalMatches<0 || matches.size()>totalMatches)throw new IllegalArgumentException("Available matches exceed the complete read's original match count");
        BitSet active=activeMatches(matches,filter,disabled);
        return new Result(assignActive(tree,totalMatches,matches,active,disabled,percentToCover,ignoreAncestors),active);
    }
    public static BitSet activeMatches(List<Match> matches,Filter filter,Set<Integer> disabled) {
        Objects.requireNonNull(matches);Objects.requireNonNull(filter);Objects.requireNonNull(disabled);
        BitSet active=new BitSet();float best=0;
        for(int i=0;i<matches.size();i++) {
            Match m=Objects.requireNonNull(matches.get(i));
            if(!m.ignored() && !disabled.contains(m.taxon()) && m.taxon()>0 && m.bitScore()>=filter.minimumScore()
                    && m.expected()<=filter.maximumExpected() && (filter.minimumIdentity()==0 || m.percentIdentity()>=filter.minimumIdentity())) {
                active.set(i);if(m.bitScore()>best)best=m.bitScore();
            }
        }
        if(filter.topPercent()>0 && filter.topPercent()<100) {
            double threshold=(1-filter.topPercent()/100.0)*best;
            for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1))if(matches.get(i).bitScore()<threshold)active.clear(i);
        }
        return active;
    }
    public static int assignActive(MaltTaxonomy tree,int totalMatches,List<Match> matches,BitSet active,
            Set<Integer> disabled,float percentToCover,boolean ignoreAncestors) {
        Objects.requireNonNull(tree);Objects.requireNonNull(matches);Objects.requireNonNull(active);Objects.requireNonNull(disabled);
        if(totalMatches<0 || matches.size()>totalMatches || active.length()>matches.size())throw new IllegalArgumentException("Incomplete or inconsistent original match population");
        if(totalMatches==0)return -1;if(active.isEmpty())return -2;
        var addresses=new ArrayList<String>();boolean hasDisabled=false;
        for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1)) {
            int id=matches.get(i).taxon();if(id>0) {
                if(disabled.contains(id))hasDisabled=true;
                else {String address=tree.address(id);if(address!=null)addresses.add(address);}
            }
        }
        if(addresses.isEmpty() && hasDisabled)for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1)) {
            int id=matches.get(i).taxon();if(id>0){String address=tree.address(id);if(address!=null)addresses.add(address);}
        }
        if(addresses.isEmpty())return -2;
        float proportion=percentToCover/100f;
        String prefix=proportion==1?commonPrefix(addresses,ignoreAncestors):coverPrefix(addresses,
                (int)Math.min(addresses.size(),Math.ceil(proportion*addresses.size())));
        int id=tree.taxonAt(prefix);return id>0?id:-2;
    }
    public static int naiveLca(MaltTaxonomy tree,Collection<Integer> taxa,Set<Integer> disabled,boolean ignoreAncestors) {
        Objects.requireNonNull(tree);Objects.requireNonNull(taxa);Objects.requireNonNull(disabled);
        if(taxa.isEmpty())return -1;if(taxa.size()==1)return taxa.iterator().next();
        var addresses=new ArrayList<String>();for(int id:taxa)if(!disabled.contains(id)){String address=tree.address(id);if(address!=null)addresses.add(address);}
        return addresses.isEmpty()?-2:tree.taxonAt(commonPrefix(addresses,ignoreAncestors));
    }
    private static String commonPrefix(List<String> addresses,boolean ignoreAncestors) {
        if(addresses.size()==1)return addresses.get(0);String reference=null;
        for(String other:addresses)if(!other.isEmpty() && (reference==null || (ignoreAncestors?other.length()>reference.length():other.length()<reference.length())))reference=other;
        if(reference==null)return "";
        for(int pos=0;pos<reference.length();pos++)for(String other:addresses)
            if(pos<other.length() && other.charAt(pos)!=reference.charAt(pos))return reference.substring(0,pos);
        return reference;
    }
    private static String coverPrefix(List<String> addresses,int required) {
        BitSet active=new BitSet();active.set(0,addresses.size());StringBuilder prefix=new StringBuilder();
        // Original Java8 HashMap<Character,Integer>(65535,1f) iterates these
        // distinct char hash buckets in ascending order; retain its tie choice.
        var counts=new TreeMap<Character,Integer>();
        for(int pos=0;;pos++) {
            counts.clear();
            for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1)) {
                String address=addresses.get(i);if(pos==address.length())active.clear(i);
                else counts.merge(address.charAt(pos),1,Integer::sum);
            }
            if(active.isEmpty())break;Character best=null;int weight=0;
            for(var entry:counts.entrySet())if(entry.getValue()>weight){best=entry.getKey();weight=entry.getValue();}
            if(best==null || weight<required)break;prefix.append(best.charValue());
            for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1))if(addresses.get(i).charAt(pos)!=best)active.clear(i);
            if(active.isEmpty())break;
        }
        return prefix.toString();
    }
}
