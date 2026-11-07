/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.jlinalg.genetics.GenomicIntervals;

/** Structural cross-check of original motif positions, presence matrix, reference ranges and PFM identities.
 * This audit does not independently reproduce MOODS score calibration or threshold probabilities.
 */
public final class SignacMotifScanAudit {
    private SignacMotifScanAudit() { }
    public record Hit(String motif,String chromosome,int start,int end,String strand,double score) {
        public Hit {SignacFragments.identity(motif);SignacFragments.identity(chromosome);if(start<1||end<start||!Set.of("+","-","*").contains(strand)||!Double.isFinite(score))throw new IllegalArgumentException("Require valid finite original motif-position fields");}
    }
    public record Result(int hits,int matrixPresenceEntries,int missingChromosomeRows,int sourcePlaceholderEntries,long work,List<String> warnings) {
        public Result {warnings=List.copyOf(warnings);}
    }
    private static final class Budget {
        final long maximum;final BooleanSupplier cancelled;long used;
        Budget(long maximum,BooleanSupplier cancelled){if(maximum<1||cancelled==null)throw new IllegalArgumentException("Require bounded scan audit and cancellation callback");this.maximum=maximum;this.cancelled=cancelled;}
        void tick(){SignacFragments.check(cancelled);if(++used>maximum)throw new IllegalArgumentException("Motif position/presence audit exceeds its work capacity");}
    }
    public static Result audit(List<SignacPfmIO.Model> models,List<SignacFragments.Region> peaks,SignacReferenceStats.Result reference,SignacMotifEnrichment.Matrix matrix,List<Hit> hits,String profile,long maximumWork,BooleanSupplier cancelled)throws IOException{
        Objects.requireNonNull(models);Objects.requireNonNull(peaks);Objects.requireNonNull(reference);Objects.requireNonNull(matrix);Objects.requireNonNull(hits);if(!Set.of("1.6.0-source","1.9.0-source","1.14.0-source","1.17.1-package").contains(profile)||models.isEmpty()||hits.size()>2_000_000)throw new IllegalArgumentException("Unsupported source profile or motif population");var budget=new Budget(maximumWork,cancelled);
        if(peaks.size()!=matrix.peaks().size()||peaks.size()!=reference.rows().size()||models.size()!=matrix.motifs().size())throw new IOException("Scan/reference/motif populations differ");Map<String,Integer> motifs=new HashMap<>();List<String> names=new ArrayList<>();int[] widths=new int[models.size()];
        for(int i=0;i<models.size();i++){budget.tick();if(!models.get(i).id().equals(matrix.motifs().get(i))||motifs.put(models.get(i).id(),i)!=null)throw new IOException("Original PFM/matrix column identities/order differ");names.add(models.get(i).name());widths[i]=models.get(i).positions().length;}
        List<String> warnings=new ArrayList<>();boolean modern=Set.of("1.14.0-source","1.17.1-package").contains(profile);boolean unicode=names.stream().anyMatch(v->v.chars().anyMatch(c->c>127));boolean duplicates=new HashSet<>(names).size()!=names.size();
        if(!modern||!duplicates){if(!names.equals(matrix.names()))throw new IOException("Original motif display labels changed without a source duplicate-name rule");}
        else if(!unicode){if(!SignacGeneActivity.makeUnique(names).equals(matrix.names()))throw new IOException("Original ASCII motif make.unique labels differ");}
        else {
            if(new HashSet<>(matrix.names()).size()!=matrix.names().size())throw new IOException("Modern source motif labels are not unique");
            for(int i=0;i<names.size();i++){String raw=names.get(i),actual=matrix.names().get(i);if(!actual.equals(raw)&&!suffix(actual,raw)&&!suffix(actual,escaped(raw)))throw new IOException("Source Unicode duplicate motif label has an invalid prefix/suffix");}
            warnings.add("SOURCE_UNICODE_DISPLAY_NAME_SUFFIX: original R make.unique can translate generated duplicate labels under the pinned C locale. Exact reported labels remain captured. Native checks IDs and allowed prefix/suffix structure, not the complete R locale-specific suffix algorithm; review the source-to-effective display-name map.");
        }
        List<GenomicIntervals.Interval> intervals=new ArrayList<>();int missing=0,lastMissing=-1;
        for(int i=0;i<peaks.size();i++){budget.tick();var peak=peaks.get(i);var row=reference.rows().get(i);if(!peak.name().equals(matrix.peaks().get(i))||!peak.name().equals(row.peak()))throw new IOException("Canonical peak order differs between scan/reference inputs");if(!row.chromosomePresent()){missing++;lastMissing=i;}else intervals.add(new GenomicIntervals.Interval(peak.chromosome(),peak.start()-1L,peak.end(),Integer.toString(i),"."));}
        if(missing>0&&!modern)throw new IOException("Historical source AddMotifs fails on absent reference chromosomes; no repaired matrix admitted");if(intervals.isEmpty())throw new IOException("Original scanner cannot produce this all-missing-reference population");var index=new GenomicIntervals.Index(intervals);Set<Long> expected=new HashSet<>();
        for(Hit hit:hits){budget.tick();Integer motif=motifs.get(hit.motif());Long length=reference.reference().lengths().get(hit.chromosome());if(motif==null||length==null||hit.end()>length||hit.end()-hit.start()+1!=widths[motif])throw new IOException("Original motif position has an unknown ID/reference or wrong PFM width");boolean found=false;var query=new GenomicIntervals.Interval(hit.chromosome(),hit.start()-1L,hit.end(),"hit",".");
            for(var peak:index.overlaps(query,false)){budget.tick();if(peak.start()<=query.start()&&peak.end()>=query.end()){expected.add((long)motif*peaks.size()+Integer.parseInt(peak.id()));found=true;}}
            if(!found)throw new IOException("Original motif hit is not fully contained in a scanned peak");
        }
        int placeholders=0;if(missing>0){expected.add((long)(models.size()-1)*peaks.size()+lastMissing);placeholders=1;warnings.add("SOURCE_MISSING_CHROMOSOME_PLACEHOLDER: original sparseMatrix(i=missingCount,j=motifCount) adds one true entry for the last missing peak/final motif despite no sequence or position hit. It is retained as a source placeholder, not an observed motif. Review reference/contig compatibility and never interpret that entry as biological evidence.");}
        Set<Long> observed=new HashSet<>();int[] pointers=matrix.pointers(),rows=matrix.rows();double[] values=matrix.values();
        for(int motif=0;motif<models.size();motif++)for(int i=pointers[motif];i<pointers[motif+1];i++){budget.tick();if(values[i]!=0&&values[i]!=1)throw new IOException("Original AddMotifs presence matrix contains nonbinary values");if(values[i]==1)observed.add((long)motif*peaks.size()+rows[i]);}
        if(!expected.equals(observed))throw new IOException("Original motif positions/source placeholder do not reconstruct the complete presence matrix");
        warnings.add("UPSTREAM_MOODS_SCORING: Java verifies position widths/containment, identities, binary matrix membership and the explicit source placeholder. It does not independently recompute MOODS calibrated scores or p thresholds; exact upstream source/runtime controls remain separate provenance.");return new Result(hits.size(),observed.size(),missing,placeholders,budget.used,warnings);
    }
    private static boolean suffix(String value,String prefix){if(!value.startsWith(prefix+"."))return false;String tail=value.substring(prefix.length()+1);return tail.matches("[1-9][0-9]*");}
    private static String escaped(String value){StringBuilder out=new StringBuilder();value.codePoints().forEach(c->{if(c<=127)out.append((char)c);else out.append(String.format(Locale.ROOT,"<U+%04X>",c));});return out.toString();}
}
