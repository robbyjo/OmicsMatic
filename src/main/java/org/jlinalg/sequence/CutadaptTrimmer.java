/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Adapter DP and quality rules derive from Cutadapt5.2, Copyright (c) 2010
 * Marcel Martin and contributors (MIT). See sequence/cutadapt-5.2/LICENSE. */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Cutadapt5.2 regular 3-prime alignment, fixed left cut and 3-prime quality trimming. */
public final class CutadaptTrimmer {
    /** Cooperative whole-run operation accounting and interruption. */
    @FunctionalInterface public interface Budget {void consume(int cells)throws IOException;}
    /** Original zero-based alignment coordinates, score and unit edit cost. */
    public record Match(int adapterStart,int adapterEnd,int readStart,int readEnd,int score,int errors) { }
    /** Complete retained read and independent trimming stages; match may be absent. */
    public record Result(FastqIO.Read read,int fixedLeft,int qualityRight,Match match) { }
    private final String adapter;
    private final double rate;
    private final int overlap;
    private final boolean wildcards;
    private final int[] ns;
    private record Probe(int start,String word) { }
    private final List<Probe> probes;
    /** maxErrors=1 means one absolute error in this source interface, not100percent. */
    public CutadaptTrimmer(String sequence,double maxErrors,int minimumOverlap) {
        adapter=sequence.toUpperCase(Locale.ROOT).replace('U','T');
        if(adapter.isEmpty()||adapter.length()>10000||!adapter.matches("[ACGTRYSWKMBDHVN]+"))throw new IllegalArgumentException("Require1..10000 IUPAC adapter bases");
        if(!Double.isFinite(maxErrors)||maxErrors<0||maxErrors>1||minimumOverlap<1||minimumOverlap>10000)throw new IllegalArgumentException("Invalid adapter error/overlap policy");
        wildcards=!adapter.matches("[ACGT]+");ns=new int[adapter.length()+1];
        for(int i=0;i<adapter.length();i++)ns[i+1]=ns[i]+(adapter.charAt(i)=='N'?1:0);
        int effective=adapter.length()-ns[adapter.length()];if(effective==0)throw new IllegalArgumentException("Cannot have only N wildcards in the adapter");
        rate=maxErrors>=1?maxErrors/effective:maxErrors;overlap=Math.min(minimumOverlap,adapter.length());
        probes=probes();
    }
    /** Locate the original regular BACK alignment. Reads retain literal source alphabet behavior. */
    public Match match(String query,Budget budget)throws IOException {
        if(query.length()>10000000||query.chars().anyMatch(c->c>127))throw new IllegalArgumentException("Require at most10million ASCII read bases");
        if(probes!=null&&!possible(query,budget))return null;
        int m=adapter.length(),n=query.length(),sentinel=m+n+1,k=(int)(rate*m);
        int[] cost=new int[m+1],score=new int[m+1],origin=new int[m+1];
        for(int i=0;i<=m;i++){cost[i]=i;score[i]=-2*i;}
        int last=Math.min(m,k+1),filled=0,lastOrigin=0;
        int bestCost=sentinel,bestScore=0,bestOrigin=0,bestRef=m,bestQuery=n;
        for(int j=1;j<=n;j++) {
            budget.consume(last);int diagonalCost=cost[0],diagonalScore=score[0],diagonalOrigin=origin[0];origin[0]++;
            for(int i=1;i<=last;i++) {
                int oldCost=cost[i],oldScore=score[i],oldOrigin=origin[i];int nextCost,nextScore,nextOrigin;
                boolean same=wildcards?(mask(adapter.charAt(i-1))&readMask(query.charAt(j-1)))!=0:adapter.charAt(i-1)==Character.toUpperCase(query.charAt(j-1));
                if(same){nextCost=diagonalCost;nextScore=diagonalScore+1;nextOrigin=diagonalOrigin;}
                else {
                    int mismatch=diagonalCost+1,insertion=oldCost+1,deletion=cost[i-1]+1;
                    if(mismatch<=deletion&&mismatch<=insertion){nextCost=mismatch;nextScore=diagonalScore-1;nextOrigin=diagonalOrigin;}
                    else if(deletion<=insertion){nextCost=deletion;nextScore=score[i-1]-2;nextOrigin=origin[i-1];}
                    else{nextCost=insertion;nextScore=oldScore-2;nextOrigin=oldOrigin;}
                }
                diagonalCost=oldCost;diagonalScore=oldScore;diagonalOrigin=oldOrigin;
                cost[i]=nextCost;score[i]=nextScore;origin[i]=nextOrigin;lastOrigin=nextOrigin;
            }
            filled=last;while(last>=0&&cost[last]>k)last--;
            if(last<m)last++;
            else {
                int length=m+Math.min(origin[m],0),effective=wildcards?length-(ns[m]-ns[m-length]):length;
                boolean acceptable=length>=overlap&&cost[m]<=effective*rate;
                int bestLength=m+Math.min(bestOrigin,0);
                if(acceptable&&(bestCost==sentinel||origin[m]<=bestOrigin+m/2&&score[m]>bestScore||length>bestLength&&score[m]>bestScore)) {
                    bestCost=cost[m];bestScore=score[m];bestOrigin=origin[m];bestRef=m;bestQuery=j;
                    if(cost[m]==0&&origin[m]>=0)break;
                }
            }
        }
        // Preserve the source's last-column ordering and its persisted `origin`
        // comparison; replacing it with origin[i] changes some boundary outcomes.
        for(int i=filled;i>=0;i--) {
            int length=i+Math.min(origin[i],0),start=-Math.min(origin[i],0),effective=wildcards?length-(ns[i]-ns[start]):length;
            int bestLength=bestRef+Math.min(bestOrigin,0);
            if(length>=overlap&&cost[i]<=effective*rate&&(bestCost==sentinel||lastOrigin<=bestOrigin+m/2&&score[i]>bestScore||length>bestLength&&score[i]>bestScore)) {
                bestCost=cost[i];bestScore=score[i];bestOrigin=origin[i];bestRef=i;bestQuery=n;
            }
        }
        return bestCost==sentinel?null:new Match(Math.max(0,-bestOrigin),bestRef,Math.max(0,bestOrigin),bestQuery,bestScore,bestCost);
    }
    /** Apply fixed cutting, then quality trimming, then adapter removal. */
    public Result trim(FastqIO.Read original,int cutLeft,int qualityCutoff,Budget budget)throws IOException {
        if(cutLeft<0||qualityCutoff<0||qualityCutoff>93)throw new IllegalArgumentException("Invalid fixed/quality cut");
        int left=Math.min(cutLeft,original.sequence().length());String sequence=original.sequence().substring(left),qualities=original.quality().substring(left);
        int stop=qualities.length(),sum=0,best=0;
        for(int i=qualities.length()-1;i>=0;i--) {sum+=qualityCutoff-(qualities.charAt(i)-33);if(sum<0)break;if(sum>best){best=sum;stop=i;}}
        int qualityRight=sequence.length()-stop;sequence=sequence.substring(0,stop);qualities=qualities.substring(0,stop);
        Match match=match(sequence,budget);int end=match==null?sequence.length():match.readStart();
        return new Result(new FastqIO.Read(original.header(),sequence.substring(0,end),qualities.substring(0,end)),left,qualityRight,match);
    }
    private static int readMask(char base){return switch(Character.toUpperCase(base)){case 'A'->1;case 'C'->2;case 'G'->4;case 'T','U'->8;default->128;};}
    private static int mask(char base){return switch(base){case 'A'->1;case 'C'->2;case 'G'->4;case 'T'->8;case 'R'->5;case 'Y'->10;case 'S'->6;case 'W'->9;case 'K'->12;case 'M'->3;case 'B'->14;case 'D'->13;case 'H'->11;case 'V'->7;case 'N'->143;default->0;};}
    private List<Probe> probes() {
        var ranges=new ArrayList<int[]>();int errors=0;
        for(int i=0;i<=adapter.length();i++)if((int)(i*rate)>errors){ranges.add(new int[]{errors,i-1});errors++;}
        ranges.add(new int[]{errors,adapter.length()});var result=new ArrayList<Probe>();int minimum=overlap;
        for(int[] range:ranges) {
            if(minimum>range[1])continue;
            if(range[0]==0&&minimum<5){for(int i=minimum;i<5;i++)result.add(new Probe(-i,adapter.substring(0,Math.min(i,adapter.length()))));minimum=5;}
            chunks(result,adapter.substring(0,Math.min(minimum,adapter.length())),range[0]+1,-range[1]);minimum=range[1]+1;
        }
        chunks(result,adapter,(int)(adapter.length()*rate)+1,0);
        // The original switches to MockKmerFinder for any word exceeding64bases.
        if(result.stream().anyMatch(p->p.word.length()>64))return null;
        return List.copyOf(result);
    }
    private static void chunks(List<Probe> probes,String sequence,int chunks,int start) {
        int size=sequence.length()/chunks,remainder=sequence.length()%chunks,offset=0;
        for(int i=0;i<chunks;i++){int length=size+(i<remainder?1:0);if(length>0)probes.add(new Probe(start,sequence.substring(offset,offset+length)));offset+=length;}
        if(probes.size()>1000000)throw new IllegalArgumentException("Native adapter kmer-profile capacity exceeded");
    }
    private boolean possible(String query,Budget budget)throws IOException {
        for(var probe:probes) {
            int start=probe.start<0?Math.max(0,query.length()+probe.start):probe.start;
            for(int i=start;i<=query.length()-probe.word.length();i++) {
                budget.consume(probe.word.length());boolean same=true;
                for(int j=0;j<probe.word.length();j++)if(wildcards?(mask(probe.word.charAt(j))&readMask(query.charAt(i+j)))==0:probe.word.charAt(j)!=Character.toUpperCase(query.charAt(i+j))){same=false;break;}
                if(same)return true;
            }
        }
        return false;
    }
}
