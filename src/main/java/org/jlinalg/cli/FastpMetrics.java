/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source statistics conventions: OpenGene fastp (MIT), see fastp-MIT-NOTICE.txt. */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent complete source-mask/quality/cycle/5-mer report statistics; no original process is launched. */
final class FastpMetrics {
    private FastpMetrics() { }
    record Limits(int cycles,long maximumResidentBytes) {
        Limits {if(cycles<1||cycles>10_000_000||maximumResidentBytes<65536||maximumResidentBytes>1_073_741_824L)throw new IllegalArgumentException("Invalid explicit fastp report-statistics capacity");}
    }
    record Totals(long reads,long bases,long q20,long q30,long sourceGc,int cycles,long meanLength) { }
    static final class Model {
        private final Limits limits;
        private long[][] counts=new long[8][0],quality=new long[8][0];
        private long[] cycleCounts=new long[0],cycleQuality=new long[0];
        private final long[] kmers=new long[1024];
        private int cycles;
        private long reads,bases,q20,q30,q40,gc;
        Model(Limits limits)throws IOException {
            this.limits=limits;if(limits.maximumResidentBytes()>Runtime.getRuntime().maxMemory()/4)throw new IOException("fastp report-statistics memory exceeds one quarter of the current Java heap;raise heap or lower the explicit capacity");
        }
        void add(FastpFastq.Read read,FastpFastq.Encoding encoding,Runnable check)throws IOException {
            int length=read.sequence().length();reserve(length);reads=Math.addExact(reads,1);bases=Math.addExact(bases,length);cycles=Math.max(cycles,length);
            for(int i=0;i<length;i++){
                if((i&8191)==0)check.run();char base=read.sequence().charAt(i);int score=read.quality().charAt(i)-encoding.offset,bucket=base&7;
                if(score<0||score>93)throw new IOException("Invalid declared fastp report quality encoding");
                counts[bucket][i]++;quality[bucket][i]=Math.addExact(quality[bucket][i],score);cycleCounts[i]++;cycleQuality[i]=Math.addExact(cycleQuality[i],score);
                if(score>=20)q20++;if(score>=30)q30++;if(score>=40)q40++;if(bucket==('G'&7)||bucket==('C'&7))gc++;
                if(i>=4){int key=0;boolean valid=true;for(int k=i-4;k<=i;k++){int value=value(read.sequence().charAt(k));if(value<0){valid=false;break;}key=(key<<2)|value;}if(valid)kmers[key]++;}
            }
        }
        Totals totals(){return new Totals(reads,bases,q20,q30,gc,cycles,reads==0?0:bases/reads);}
        long verifyDetail(Map<?,?> reported,String version)throws IOException {
            if(!List.of("0.22.0","0.23.2","0.25.0","1.0.1").contains(version))throw new FastpRuntime.Unavailable("Unsupported exact fastp statistics edition");
            var detailKeys=new java.util.HashSet<>(Set.of("total_reads","total_bases","q20_bases","q30_bases","total_cycles","quality_curves","content_curves","kmer_count","overrepresented_sequences"));if(version.equals("1.0.1"))detailKeys.add("q40_bases");
            exactKeys(reported,detailKeys,"detailed statistics");
            whole(reported,"total_reads",reads);whole(reported,"total_bases",bases);whole(reported,"q20_bases",q20);whole(reported,"q30_bases",q30);whole(reported,"total_cycles",cycles);long checked=5;
            if(version.equals("1.0.1")){whole(reported,"q40_bases",q40);checked++;}
            Map<?,?> qc=FastpRuntime.object(reported.get("quality_curves")),contents=FastpRuntime.object(reported.get("content_curves"));
            exactKeys(qc,Set.of("A","T","C","G","mean"),"quality curves");exactKeys(contents,Set.of("A","T","C","G","N","GC"),"content curves");
            for(String name:List.of("A","T","C","G","mean")){
                List<?> values=array(qc.get(name));if(values.size()!=cycles)throw new IOException("Incomplete fastp quality curve population: "+name);
                for(int i=0;i<cycles;i++){
                    double mean=(double)cycleQuality[i]/cycleCounts[i];int bucket=name.charAt(0)&7;
                    double expected=name.equals("mean")||counts[bucket][i]==0?mean:(double)quality[bucket][i]/counts[bucket][i];rounded(values.get(i),expected,"quality_curves/"+name+"/"+i);checked++;
                }
            }
            for(String name:List.of("A","T","C","G","N","GC")){
                List<?> values=array(contents.get(name));if(values.size()!=cycles)throw new IOException("Incomplete fastp content curve population: "+name);
                for(int i=0;i<cycles;i++){
                    long numerator=name.equals("GC")?Math.addExact(counts['G'&7][i],counts['C'&7][i]):counts[name.charAt(0)&7][i];rounded(values.get(i),(double)numerator/cycleCounts[i],"content_curves/"+name+"/"+i);checked++;
                }
            }
            Map<?,?> observed=FastpRuntime.object(reported.get("kmer_count"));if(observed.size()!=1024)throw new IOException("Incomplete fastp five-mer population");
            for(int i=0;i<1024;i++){String name=kmer(i);whole(observed,name,kmers[i]);checked++;}
            if(!FastpRuntime.object(reported.get("overrepresented_sequences")).isEmpty())throw new IOException("Overrepresentation output is outside the declared disabled capability");
            return checked;
        }
        private void reserve(int requested)throws IOException {
            if(requested>limits.cycles())throw new IOException("Complete fastp cycle population exceeds explicit statistics capacity");
            if(requested<=cycleCounts.length)return;
            int capacity=Math.min(limits.cycles(),Math.max(requested,Math.max(64,cycleCounts.length*2)));
            long bytes=Math.addExact(65536,Math.multiplyExact(144L,capacity));if(bytes>limits.maximumResidentBytes())throw new IOException("Complete fastp cycle statistics exceed explicit memory;raise heap/statistics capacity without truncating cycles");
            cycleCounts=Arrays.copyOf(cycleCounts,capacity);cycleQuality=Arrays.copyOf(cycleQuality,capacity);
            for(int i=0;i<8;i++){counts[i]=Arrays.copyOf(counts[i],capacity);quality[i]=Arrays.copyOf(quality[i],capacity);}
        }
    }
    static Model collect(Path path,FastpFastq.Limits input,FastpFastq.Encoding encoding,FastpFastq.Alphabet alphabet,FastpFastq.Plus plus,Limits statistics,boolean allowEmptyRead,Runnable check)throws IOException {
        var model=new Model(statistics);
        try(var reader=new FastpFastq.Reader(path,input,encoding,alphabet,plus,allowEmptyRead,check)){
            for(FastpFastq.Read row=reader.next();row!=null;row=reader.next())model.add(row,encoding,check);reader.completeStats();
        }
        return model;
    }
    static long verifySummary(Map<?,?> reported,Model first,Model second)throws IOException {
        Totals a=first.totals(),b=second==null?new Totals(0,0,0,0,0,0,0):second.totals();
        var keys=new java.util.HashSet<>(Set.of("total_reads","total_bases","q20_bases","q30_bases","q20_rate","q30_rate","read1_mean_length","gc_content"));if(second!=null)keys.add("read2_mean_length");exactKeys(reported,keys,"summary");
        long totalBases=Math.addExact(a.bases(),b.bases()),q20=Math.addExact(a.q20(),b.q20()),q30=Math.addExact(a.q30(),b.q30());
        whole(reported,"total_reads",Math.addExact(a.reads(),b.reads()));whole(reported,"total_bases",totalBases);whole(reported,"q20_bases",q20);whole(reported,"q30_bases",q30);whole(reported,"read1_mean_length",a.meanLength());if(second!=null)whole(reported,"read2_mean_length",b.meanLength());
        rounded(reported.get("q20_rate"),totalBases==0?0:(double)q20/totalBases,"q20_rate");rounded(reported.get("q30_rate"),totalBases==0?0:(double)q30/totalBases,"q30_rate");rounded(reported.get("gc_content"),totalBases==0?0:(double)Math.addExact(a.sourceGc(),b.sourceGc())/totalBases,"gc_content");return keys.size();
    }
    static void rounded(Object reported,double expected,String field)throws IOException {
        if(!(reported instanceof Number value)||!Double.isFinite(value.doubleValue())||!Double.isFinite(expected))throw new IOException("Invalid/nonfinite fastp numeric field: "+field);
        double actual=value.doubleValue();
        if(expected==0){if(actual!=0)throw new IOException("fastp source-zero field differs: "+field);return;}
        BigDecimal decimal=BigDecimal.valueOf(Math.abs(expected));int exponent=decimal.precision()-decimal.scale()-1;double tolerance=.500001*Math.pow(10,exponent-5)+8*Math.ulp(expected);
        if(Math.abs(actual-expected)>tolerance)throw new IOException("fastp original field differs beyond six-significant-digit output precision: "+field+" expected="+expected+" observed="+actual);
    }
    static void whole(Map<?,?> values,String field,long expected)throws IOException {if(FastpRuntime.integer(values.get(field),0,Long.MAX_VALUE)!=expected)throw new IOException("fastp complete report count differs: "+field);}
    static void exactKeys(Map<?,?> values,Set<String> keys,String context)throws IOException {if(!values.keySet().equals(keys))throw new IOException("Unexpected/incomplete fastp "+context+" field census: "+values.keySet());}
    static List<?> array(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require complete fastp report array");return list;}
    private static int value(char base){return switch(base){case 'A'->0;case 'T'->1;case 'C'->2;case 'G'->3;default->-1;};}
    private static String kmer(int key){char[] bases=new char[5];for(int i=4;i>=0;i--){bases[i]="ATCG".charAt(key&3);key>>=2;}return new String(bases);}
}
