/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.jlinalg.enrichment.FumaCelltypeAnalysis;

/** Original Signac FindMotifs statistics for explicit, ordered query/background rows.
 * Background selection and sequence motif scanning are separate upstream operations.
 */
public final class SignacMotifEnrichment {
    private SignacMotifEnrichment() { }
    public enum Profile { LEGACY_1_6, CORRECTED_1_7_PLUS }
    public enum Correction { BH, BY, HOLM, HOCHBERG, HOMMEL, BONFERRONI, NONE }
    /** A peaks-by-motifs CSC matrix; stored counts are exact nonnegative integers.
     * Explicit zeros, zero motif columns and a single motif are permitted.
     */
    public record Matrix(List<String> peaks,List<String> motifs,List<String> names,int[] pointers,int[] rows,double[] values) {
        public Matrix {
            peaks=List.copyOf(peaks);motifs=List.copyOf(motifs);names=List.copyOf(names);pointers=pointers.clone();rows=rows.clone();values=values.clone();
            if(peaks.isEmpty()||peaks.size()>2_000_000||motifs.size()>1_000_000||names.size()!=motifs.size()||values.length>20_000_000||pointers.length!=motifs.size()+1||pointers[0]!=0||pointers[motifs.size()]!=values.length||rows.length!=values.length)throw new IllegalArgumentException("Invalid bounded peaks-by-motifs CSC dimensions");
            for(var ids:List.of(peaks,motifs)){Set<String> seen=new HashSet<>();for(String id:ids){SignacFragments.identity(id);if(!seen.add(id))throw new IllegalArgumentException("Motif matrix row/column IDs must be unique");}}
            for(String name:names)SignacFragments.identity(name);
            for(int column=0;column<motifs.size();column++){
                if(pointers[column]<0||pointers[column+1]<pointers[column]||pointers[column+1]>values.length)throw new IllegalArgumentException("Invalid motif CSC pointers");
                int previous=-1;for(int i=pointers[column];i<pointers[column+1];i++){
                    if(rows[i]<=previous||rows[i]>=peaks.size()||!Double.isFinite(values[i])||values[i]<0||values[i]!=Math.floor(values[i])||values[i]>SignacFragments.MAX_EXACT)throw new IllegalArgumentException("Require sorted unique CSC coordinates and nonnegative exact motif counts");previous=rows[i];
                }
            }
        }
        @Override public int[] pointers(){return pointers.clone();}
        @Override public int[] rows(){return rows.clone();}
        @Override public double[] values(){return values.clone();}
    }
    /** The complete motif family is corrected before source ordering; null adjustedP means legacy absence. */
    public record Row(String motif,String name,double observed,double background,double percentObserved,double percentBackground,double foldEnrichment,double pvalue,Double adjustedP) { }
    public record Result(List<Row> rows,int queryRows,int backgroundRows,int repeatedQueryRows,int repeatedBackgroundRows,long work,List<String> warnings) {
        public Result {rows=List.copyOf(rows);warnings=List.copyOf(warnings);}
    }
    private static final class Budget {
        final long maximum;final BooleanSupplier cancelled;long used;
        Budget(long maximum,BooleanSupplier cancelled){if(maximum<1||cancelled==null)throw new IllegalArgumentException("Require positive motif work capacity and cancellation callback");this.maximum=maximum;this.cancelled=cancelled;}
        void tick(){SignacFragments.check(cancelled);if(++used>maximum)throw new IllegalArgumentException("Native motif enrichment exceeded its explicit work capacity");}
    }
    private record Selection(int[] multiplicity,int repeated) { }
    private static Selection selection(List<String> ids,Map<String,Integer> peaks,Budget budget)throws IOException {
        if(ids==null||ids.size()>2_000_000)throw new IllegalArgumentException("Require at most two million ordered selected peak IDs");int[] values=new int[peaks.size()];int repeated=0;
        for(String id:ids){budget.tick();Integer row=peaks.get(id);if(row==null)throw new IOException("Selected motif peak is absent: "+id);if(values[row]++>0)repeated++;}return new Selection(values,repeated);
    }
    /**
     * Reproduce FindMotifs after explicit background selection, including repeated rows,
     * separate-background hypergeometric population, nonfinite source outputs and stable ordering.
     * @param matrix exact captured motif counts/presence and effective display names
     * @param query ordered query peak IDs, with source duplicates retained
     * @param background ordered background IDs; null selects every matrix peak
     * @param profile legacy 1.6 has no p.adjust column; 1.7+ corrects the complete finite family
     * @param correction explicit source correction; ignored by legacy 1.6, as upstream
     * @param maximumWork explicit bound on counted sparse/selection/correction/sort work
     * @param cancelled cancellation/deadline callback
     * @return all original motif rows and review warnings, including nonfinite source results
     */
    public static Result enrich(Matrix matrix,List<String> query,List<String> background,Profile profile,Correction correction,long maximumWork,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(matrix);Objects.requireNonNull(profile);Objects.requireNonNull(correction);var budget=new Budget(maximumWork,cancelled);budget.tick();
        if(query==null)throw new IllegalArgumentException("Require an explicit query, possibly empty");query=List.copyOf(query);background=background==null?matrix.peaks:List.copyOf(background);
        Map<String,Integer> peaks=new HashMap<>();for(int i=0;i<matrix.peaks.size();i++){budget.tick();peaks.put(matrix.peaks.get(i),i);}
        Selection q=selection(query,peaks,budget),bg=selection(background,peaks,budget);List<Row> rows=new ArrayList<>();double[] probabilities=new double[matrix.motifs.size()];boolean counts=false,nonfinite=false;
        for(int column=0;column<matrix.motifs.size();column++){
            budget.tick();double observed=0,reference=0;
            for(int i=matrix.pointers[column];i<matrix.pointers[column+1];i++){
                budget.tick();double value=matrix.values[i];counts|=value>1;observed+=value*q.multiplicity[matrix.rows[i]];reference+=value*bg.multiplicity[matrix.rows[i]];
                if(observed>SignacFragments.MAX_EXACT||reference>SignacFragments.MAX_EXACT)throw new IOException("Selected motif sum exceeds the exact integer domain; no rounded counts admitted");
            }
            double percentObserved=observed/query.size()*100,percentBackground=reference/background.size()*100,fold=percentObserved/percentBackground;
            double probability=sourceTail(observed,reference,query.size(),background.size());probabilities[column]=probability;nonfinite|=!Double.isFinite(probability)||!Double.isFinite(fold)||!Double.isFinite(percentObserved)||!Double.isFinite(percentBackground);
            rows.add(new Row(matrix.motifs.get(column),matrix.names.get(column),observed,reference,percentObserved,percentBackground,fold,probability,null));
        }
        if(profile==Profile.CORRECTED_1_7_PLUS){
            int finite=0;for(double p:probabilities){budget.tick();if(Double.isFinite(p))finite++;}double[] values=new double[finite];int at=0;for(double p:probabilities)if(Double.isFinite(p))values[at++]=p;
            double[] adjusted=FumaCelltypeAnalysis.adjust(values,FumaCelltypeAnalysis.Correction.valueOf(correction.name()),new FumaCelltypeAnalysis.Limits(Math.max(1,values.length),1,Math.max(1,maximumWork-budget.used)),budget::tick);at=0;
            for(int i=0;i<rows.size();i++){budget.tick();Row row=rows.get(i);double value=Double.isFinite(probabilities[i])?adjusted[at++]:Double.NaN;rows.set(i,new Row(row.motif,row.name,row.observed,row.background,row.percentObserved,row.percentBackground,row.foldEnrichment,row.pvalue,value));}
        }
        rows.sort((a,b)->{budget.tick();int p=sourceCompare(a.pvalue,b.pvalue);return p!=0?p:sourceCompare(-a.foldEnrichment,-b.foldEnrichment);});
        List<String> warnings=new ArrayList<>();warnings.add("SOURCE_BACKGROUND_POPULATION: phyper(observed-1, backgroundCount, backgroundRows-backgroundCount, queryRows, lower.tail=FALSE) uses the background alone as its population, not query-plus-background Fisher testing. Review the declared populations and source inference.");
        if(query.size()<10)warnings.add("SMALL_QUERY: original Signac warns that testing motif enrichment using fewer than ten regions is not recommended.");
        if(q.repeated>0||bg.repeated>0)warnings.add("REPEATED_SELECTED_ROWS: query/background duplicates are counted repeatedly as in original matrix subsetting; no deduplication occurred. Review selection provenance.");
        if(counts)warnings.add("MOTIF_COUNTS_NOT_BINARY: raw motif counts are summed exactly as supplied; FindMotifs is ordinarily interpreted using presence/absence. Counts can yield invalid hypergeometric parameters. Review input meaning before interpretation.");
        if(nonfinite)warnings.add("NONFINITE_SOURCE_STATISTICS: source zero denominators, empty populations or invalid hypergeometric parameters yield NaN/Inf. These are retained explicitly, not repaired or interpreted as valid discoveries. Review every affected row and the input populations.");
        if(profile==Profile.LEGACY_1_6)warnings.add("LEGACY_NO_ADJUSTMENT_COLUMN: Signac1.6 FindMotifs has no p.adjust result; the requested correction is ignored by that source. Do not report unadjusted probabilities as FDR.");
        warnings.add("UPSTREAM_PREPARATION_REQUIRED: this native method does not scan sequences, infer TF activity, calculate reference GC or select KDE-matched backgrounds. Captured upstream selections and their RNG/profile provenance remain separate evidence.");
        return new Result(rows,query.size(),background.size(),q.repeated,bg.repeated,budget.used,warnings);
    }
    private static int sourceCompare(double a,double b){if(a==b)return 0;if(Double.isNaN(a))return Double.isNaN(b)?0:1;if(Double.isNaN(b))return -1;return a<b?-1:1;}
    /** Original direct-double upper-tail call; invalid source population parameters remain NaN. */
    public static double sourceTail(double observed,double backgroundCount,int queryRows,int backgroundRows){
        if(!Double.isFinite(observed)||!Double.isFinite(backgroundCount)||observed<0||backgroundCount<0||observed!=Math.floor(observed)||backgroundCount!=Math.floor(backgroundCount)||queryRows<0||backgroundRows<0||backgroundCount>backgroundRows||queryRows>backgroundRows)return Double.NaN;
        return jdistlib.HyperGeometric.cumulative(observed-1,backgroundCount,backgroundRows-backgroundCount,queryRows,false,false);
    }
}
