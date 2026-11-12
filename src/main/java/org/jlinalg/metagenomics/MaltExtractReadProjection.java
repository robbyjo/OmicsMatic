/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.IOException;
import java.util.*;

/** Native read projection for explicitly unstacked, non-deduplicated, unsampled
 * MaltExtract1.7 default/ancient profiles. RMA decoding remains an original-API adapter.
 * Behaviour attribution: Ron Huebler, HOPS0.35 GPL3+ distribution, pinned
 * MaltExtract1.7.jar SHA256 8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca:
 * NodeProcessor, ExperimentalRMA6{Ancient,}Destacker, CompositionMap,
 * Alignment and embedded jloda.util.DNAComplexityMeasure.
 * This preserves that binary's float division, first-score threshold, ten-match
 * ancient limit, case-sensitive lesions, read-key concatenation and unusual
 * complexity loop. It does not implement destacking, deduplication or damage statistics.
 */
public final class MaltExtractReadProjection {
    private MaltExtractReadProjection() { }
    public enum Mode { DEFAULT, ANCIENT }
    public record Options(Mode mode,double topFraction,double minimumIdentity,int maximumLength,
                          double minimumComplexity,boolean topAlignmentOnly,boolean singleStranded) {
        public Options {
            Objects.requireNonNull(mode);
            if(!Double.isFinite(topFraction)||topFraction<0||topFraction>1||
               !Double.isFinite(minimumIdentity)||minimumIdentity<0||minimumIdentity>100||
               maximumLength<0||!Double.isFinite(minimumComplexity)||minimumComplexity<0||minimumComplexity>1)
                throw new IllegalArgumentException("Explicit finite source-profile parameters required");
        }
        public static Options complete(Mode mode){return new Options(mode,0.01,0,0,0,false,false);}
    }
    public record ReadPair(String header,String sequence) { }
    public record Result(int taxon,long assigned,long sequenceEligible,long consideredMatches,
                         long retainedMatches,List<ReadPair> reads) {
        public Result {reads=List.copyOf(reads);}
    }
    /** Complete input rows must have been decoded with MaltExtractRmaReader.EXTRACTION.
     * A class's complete population is required; no subsampling occurs here.
     * Output order is explicitly deterministic; compare multisets with upstream FASTA.
     */
    public static Result project(List<MaltExtractRmaReader.Read> rows,int taxon,Options options)throws IOException {
        Objects.requireNonNull(rows);Objects.requireNonNull(options);
        long assigned=0,eligible=0,considered=0,retained=0;
        Map<String,ReadPair> chosen=new HashMap<>();
        for(var row:rows) {
            if(row.storedTaxon()!=taxon)continue;
            assigned++;
            if(row.name()==null||row.sequence()==null)throw failure("Missing literal read name or sequence");
            if((options.maximumLength()!=0&&row.length()>options.maximumLength())||
               !(options.minimumComplexity()<=minimumComplexity(row.sequence())))continue;
            eligible++;
            if(row.matches().isEmpty())throw failure("Source processing would index an empty available-match array");
            float first=row.matches().get(0).bitScore();
            boolean retainedRead=false;
            for(int i=0;i<row.matches().size();i++) {
                var hit=row.matches().get(i);
                // Deliberately divide in float precision, then compare as double.
                if((double)(hit.bitScore()/first)<1.0-options.topFraction()||
                   options.mode()==Mode.ANCIENT&&i==10)break;
                boolean damage=terminalDamage(hit.text(),options.singleStranded());
                if(options.mode()==Mode.DEFAULT)considered++;
                boolean keep=options.minimumIdentity()<=hit.percentIdentity()&&
                    (options.mode()==Mode.DEFAULT||damage);
                if(!keep)continue;
                if(options.mode()==Mode.ANCIENT)considered++;
                if(options.topAlignmentOnly()&&i!=0)continue;
                retained++;retainedRead=true;
            }
            if(retainedRead) {
                String header=row.name().startsWith(">")?row.name():">"+row.name();
                ReadPair pair=new ReadPair(header,row.sequence());
                String key=row.name()+row.sequence();
                ReadPair previous=chosen.putIfAbsent(key,pair);
                if(previous!=null&&!previous.equals(pair))
                    throw failure("Distinct literal reads collide under the source name-plus-sequence key; retain and review the original ambiguous population");
            }
        }
        var result=new ArrayList<>(chosen.values());
        result.sort(Comparator.comparing(ReadPair::header).thenComparing(ReadPair::sequence));
        return new Result(taxon,assigned,eligible,considered,retained,result);
    }
    /** Exact binary complexity behaviour, including its step16 and length-minus16
     * loop bound. This is not a corrected sliding-window implementation.
     * Short sequences return0; lengths16..32 return1, including homopolymers.
     */
    public static float minimumComplexity(String sequence)throws IOException {
        if(sequence==null||sequence.length()<16)return 0f;
        int[] counts=new int[4];for(int pos=0;pos<16;pos++)counts[index(sequence.charAt(pos))]++;
        double minimum=1.0;
        for(int pos=16;pos<sequence.length()-16;pos+=16) {
            double product=1.0;
            for(int count:counts) {
                if(count<0||count>16)throw failure("Source complexity factorial index falls outside0..16");
                double factorial=1.0;for(int i=1;i<=count;i++)factorial*=i;
                product*=factorial;
            }
            double value=0.0625*Math.log(2.0922789888E13/product)/Math.log(4.0);
            counts[index(sequence.charAt(pos-16))]--;counts[index(sequence.charAt(pos))]++;
            if(value<minimum)minimum=value;
        }
        return (float)Math.max(1.0E-4,minimum);
    }
    private static int index(char base) {
        return switch(base){case 'C','c'->1;case 'G','g'->2;case 'T','U','t','u'->3;default->0;};
    }
    /** Source terminal-lesion predicate on the literal original BLAST text.
     * Preserve case, gaps, wrapping and the source's length gate. No strand
     * normalization, uppercasing, gap removal or sequence repair occurs.
     */
    public static boolean terminalDamage(String text,boolean singleStranded)throws IOException {
        if(text==null)throw failure("Missing original alignment text");
        StringBuilder query=new StringBuilder(),reference=new StringBuilder();
        for(String line:text.split("\n")) {
            if(line.contains("Query"))appendSequence(line,query);
            if(line.contains("Sbjct"))appendSequence(line,reference);
        }
        for(int k=0;k<5;k++) {
            int length=query.length();
            if(reference.length()>1+k*2&&length>1+k*2&&reference.length()==length) {
                if(ct(reference,query,k)||
                   (singleStranded?ct(reference,query,length-k-1):ga(reference,query,length-k-1)))return true;
            }
        }
        return false;
    }
    private static void appendSequence(String line,StringBuilder target) {
        for(String fragment:line.split(":|\\s")) {
            String token=fragment.trim();if(token.matches("[ATGCNatgcn-]+"))target.append(token);
        }
    }
    private static boolean ct(CharSequence reference,CharSequence query,int at) {
        return reference.charAt(at)=='C'&&query.charAt(at)=='T'||reference.charAt(at)=='c'&&query.charAt(at)=='t';
    }
    private static boolean ga(CharSequence reference,CharSequence query,int at) {
        return reference.charAt(at)=='G'&&query.charAt(at)=='A'||reference.charAt(at)=='g'&&query.charAt(at)=='a';
    }
    private static IOException failure(String cause) {
        return new IOException(cause+". NO RESULT ADMITTED. Required action: retain complete inputs and upstream diagnostics; resolve the named source-format, identity or capacity issue before retrying. Do not repair or omit records silently.");
    }
}
