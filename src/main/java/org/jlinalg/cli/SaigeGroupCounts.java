/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Independent unweighted membership/MAC audit of the original gene-test collapse. */
final class SaigeGroupCounts {
    private SaigeGroupCounts() { }
    record Member(String id,String annotation,SaigeGenotypes.Calls calls) { }
    record Summary(String status,List<String> rare,List<String> ultraRare,
                   double minorCount,double caseMinorCount,double controlMinorCount) {
        Summary {rare=List.copyOf(rare);ultraRare=List.copyOf(ultraRare);}
    }

    /**
     * Original gene QC and masks use post-imputation allele frequency/count.
     * Ultra-rare variants collapse by per-participant maximum minor dosage,
     * preserving a dosage of two. This is not a binary carrier indicator or a
     * sum of overlapping ultra-rare calls. No burden weights or P values are
     * calculated here. Zero maximumMac means no additional MAC upper limit.
     */
    static Summary summarize(List<Member> members,Set<String> annotations,double minimumMaf,
                             double minimumMac,double maximumMissing,double maximumMaf,
                             double maximumMac,double collapseMac,int[] binaryOutcomes) {
        Objects.requireNonNull(members);Objects.requireNonNull(annotations);
        if(annotations.isEmpty()||annotations.stream().anyMatch(a->a==null||a.isBlank())||!Double.isFinite(maximumMaf)||maximumMaf<0||maximumMaf>.5||!Double.isFinite(maximumMac)||maximumMac<0||!Double.isFinite(collapseMac)||collapseMac<0)throw new IllegalArgumentException("Require explicit annotation mask and finite valid MAF/MAC/collapse bounds");
        if(members.isEmpty())throw new IllegalArgumentException("Group source contains no declared variants");
        int n=Objects.requireNonNull(members.get(0).calls()).summary().samples();
        if(binaryOutcomes!=null&&binaryOutcomes.length!=n)throw new IllegalArgumentException("Group binary outcome shape differs from cohort");
        int cases=0;if(binaryOutcomes!=null)for(int y:binaryOutcomes){if(y!=0&&y!=1)throw new IllegalArgumentException("Group binary outcomes must be explicit0/1");cases+=y;}
        if(binaryOutcomes!=null&&(cases==0||cases==n))throw new IllegalArgumentException("Group binary cohort requires both cases and controls");
        var seen=new HashSet<String>();var rare=new ArrayList<String>();var ultra=new ArrayList<String>();double[] sumRare=new double[n],maxUltra=new double[n];
        for(Member member:members){
            Objects.requireNonNull(member);var calls=Objects.requireNonNull(member.calls());var stats=calls.summary();
            if(member.id()==null||member.id().isBlank()||member.annotation()==null||member.annotation().isBlank()||!seen.add(member.id()))throw new IllegalArgumentException("Each group requires unique variant IDs and explicit annotations");
            if(stats.samples()!=n||binaryOutcomes!=null&&(stats.cases()!=cases||stats.controls()!=n-cases))throw new IllegalArgumentException("Group member cohort or binary counts differ");
            boolean qc=SaigeGenotypes.passesGroupQc(stats,minimumMaf,minimumMac,maximumMissing);
            double maf=Math.min(stats.frequency(),1-stats.frequency()),mac=Math.min(stats.alleleCount(),2.0*n-stats.alleleCount());
            if(!annotations.contains(member.annotation())||!qc||maf>maximumMaf||maximumMac>0&&mac>maximumMac)continue;
            double[] g=calls.minorDosages();if(g.length!=n)throw new IllegalArgumentException("Group member dosage shape differs");
            if(mac<=collapseMac){ultra.add(member.id());for(int i=0;i<n;i++)maxUltra[i]=Math.max(maxUltra[i],g[i]);}
            else{rare.add(member.id());for(int i=0;i<n;i++)sumRare[i]+=g[i];}
        }
        double total=0,caseTotal=0,controlTotal=0;
        for(int i=0;i<n;i++){double value=sumRare[i]+maxUltra[i];total+=value;if(binaryOutcomes!=null){if(binaryOutcomes[i]==1)caseTotal+=value;else controlTotal+=value;}}
        return new Summary(rare.isEmpty()&&ultra.isEmpty()?"empty-mask":"eligible",rare,ultra,total,binaryOutcomes==null?Double.NaN:caseTotal,binaryOutcomes==null?Double.NaN:controlTotal);
    }
}
