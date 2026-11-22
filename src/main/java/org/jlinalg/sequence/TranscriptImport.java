/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;

/**
 * Dense transcript import arithmetic following tximport 1.40.0 point estimates.
 * Counts can be fractional estimates; abundances are TPM and lengths are
 * effective transcript lengths. No quantification or differential test is run.
 * See META-INF/NOTICE-tximport.txt for provenance and scope.
 */
public final class TranscriptImport {
    private TranscriptImport() { }

    /** Output level. */
    public enum Level { /** Transcript estimates. */ TRANSCRIPT, /** Gene summaries. */ GENE }
    /** Explicit abundance-to-count transformation. */
    public enum Scaling {
        /** Preserve estimated counts. */ NO,
        /** Scale TPM to the retained estimated library total. */ SCALED_TPM,
        /** Scale TPM by across-sample mean length and library total. */ LENGTH_SCALED_TPM,
        /** Use one median isoform length per gene for transcript usage. */ DTU_SCALED_TPM
    }
    /** How quantification IDs are matched to an unchanged annotation map. */
    public enum IdPolicy {
        /** Exact IDs. */ EXACT,
        /** Strip the first dot and following characters for matching. */ STRIP_VERSION,
        /** Strip the first bar and following characters; also change transcript output IDs. */ STRIP_BAR
    }
    /** What to do with unannotated transcripts during gene summarization. */
    public enum Unmapped { /** Fail. */ ERROR, /** Explicitly omit, recording every status. */ DROP }

    /**
     * Rectangular feature-by-sample point estimates. All arrays are copied.
     * @param features exact feature IDs
     * @param samples exact sample IDs
     * @param counts nonnegative finite estimated counts
     * @param abundance nonnegative finite TPM
     * @param length positive finite effective lengths
     */
    public record Estimates(List<String> features, List<String> samples,
                            double[][] counts, double[][] abundance, double[][] length) {
        /**
         * Validates dimensions, identities and numerical domains, then copies.
         * @param features feature IDs
         * @param samples sample IDs
         * @param counts estimated counts
         * @param abundance TPM
         * @param length effective lengths
         */
        public Estimates {
            features = ids(features, "feature"); samples = ids(samples, "sample");
            counts = matrix(counts, features.size(), samples.size(), false, "counts");
            abundance = matrix(abundance, features.size(), samples.size(), false, "abundance");
            length = matrix(length, features.size(), samples.size(), true, "length");
        }
        @Override public double[][] counts() { return copy(counts); }
        @Override public double[][] abundance() { return copy(abundance); }
        @Override public double[][] length() { return copy(length); }
    }

    /**
     * Complete input-row accounting and length fallback decisions.
     * @param transcript original transcript ID
     * @param matchingId ID used for annotation matching
     * @param gene mapped gene, or empty if absent
     * @param retained whether this row contributed to output
     */
    public record TranscriptStatus(String transcript, String matchingId, String gene, boolean retained) { }
    /**
     * Gene length replacement used when gene abundance is zero.
     * @param gene gene ID
     * @param sample sample ID
     * @param method other-sample geometric mean or all-sample/isoform arithmetic mean
     * @param length replacement length
     */
    public record LengthFallback(String gene, String sample, String method, double length) { }
    /**
     * Immutable import result with audit records.
     * @param estimates output counts, TPM and effective lengths
     * @param transcripts every input transcript's status in original order
     * @param fallbacks all gene/sample length replacements
     */
    public record Result(Estimates estimates, List<TranscriptStatus> transcripts, List<LengthFallback> fallbacks) {
        /**
         * Copies the audit lists; Estimates is already immutable.
         * @param estimates output
         * @param transcripts transcript accounting
         * @param fallbacks fallback accounting
         */
        public Result { Objects.requireNonNull(estimates); transcripts = List.copyOf(transcripts); fallbacks = List.copyOf(fallbacks); }
    }

    /**
     * Imports point estimates or summarizes transcripts to genes. Gene rows use
     * Java String order, independent of host locale; transcript/sample order is
     * retained. Annotation keys are never normalized or assigned implicitly.
     * Unmapped rows are omitted before gene library totals are calculated,
     * matching tximport. All-zero scaling denominators fail rather than
     * publishing tximport's NaN. Positive lengths are required at this API.
     * @param input point estimates
     * @param annotation exact matching-transcript to gene map; required for genes/DTU
     * @param level output level
     * @param scaling count transformation
     * @param idPolicy explicit transcript matching policy
     * @param unmapped gene omission policy; DTU always requires every transcript mapped
     * @return output and complete audit records
     */
    public static Result summarize(Estimates input, Map<String,String> annotation, Level level,
                                   Scaling scaling, IdPolicy idPolicy, Unmapped unmapped) {
        Objects.requireNonNull(input); Objects.requireNonNull(level); Objects.requireNonNull(scaling);
        Objects.requireNonNull(idPolicy); Objects.requireNonNull(unmapped); Objects.requireNonNull(annotation);
        boolean needsMap = level == Level.GENE || scaling == Scaling.DTU_SCALED_TPM;
        if (level == Level.GENE && scaling == Scaling.DTU_SCALED_TPM)
            throw new IllegalArgumentException("dtuScaledTPM requires transcript output");
        if (needsMap && annotation.isEmpty()) throw new IllegalArgumentException("Transcript-to-gene annotation is required");
        for (var entry : annotation.entrySet()) { id(entry.getKey(), "Annotation transcript"); id(entry.getValue(), "Annotation gene"); }
        int rows = input.features.size(), columns = input.samples.size();
        String[] genes = new String[rows]; boolean[] retained = new boolean[rows];
        var statuses = new ArrayList<TranscriptStatus>(); var seen = new HashSet<String>();
        var outputIds = new ArrayList<String>();
        for (int row = 0; row < rows; row++) {
            String original = input.features.get(row), match = matchingId(original, idPolicy);
            if (needsMap && !seen.add(match)) throw new IllegalArgumentException("Transcript IDs collide after declared matching policy: " + match);
            genes[row] = annotation.get(match);
            if (needsMap && genes[row] == null && (unmapped == Unmapped.ERROR || scaling == Scaling.DTU_SCALED_TPM))
                throw new IllegalArgumentException("Unmapped transcript: " + original + "; supply a matching annotation or explicitly choose gene --unmapped drop");
            retained[row] = !needsMap || genes[row] != null;
            statuses.add(new TranscriptStatus(original, match, genes[row] == null ? "" : genes[row], retained[row]));
            if (level == Level.TRANSCRIPT) outputIds.add(idPolicy == IdPolicy.STRIP_BAR ? match : original);
        }
        double[][] counts, abundance, length;
        var fallbacks = new ArrayList<LengthFallback>();
        if (level == Level.GENE) {
            outputIds = new ArrayList<>(new TreeSet<>(Arrays.stream(genes).filter(Objects::nonNull).toList()));
            if (outputIds.isEmpty()) throw new IllegalArgumentException("No transcripts match the supplied annotation");
            var indexes = new HashMap<String,Integer>(); for (int i=0;i<outputIds.size();i++) indexes.put(outputIds.get(i),i);
            int number = outputIds.size(); counts = new double[number][columns]; abundance = new double[number][columns];
            length = new double[number][columns]; double[] fallback = new double[number]; int[] isoforms = new int[number];
            for (int row=0;row<rows;row++) if (retained[row]) {
                int gene = indexes.get(genes[row]); isoforms[gene]++;
                double mean = mean(input.length[row]); fallback[gene] = finite(fallback[gene]+mean);
                for (int column=0;column<columns;column++) {
                    counts[gene][column] = finite(counts[gene][column]+input.counts[row][column]);
                    abundance[gene][column] = finite(abundance[gene][column]+input.abundance[row][column]);
                    length[gene][column] = finite(length[gene][column]+finite(input.abundance[row][column]*input.length[row][column]));
                }
            }
            for (int gene=0;gene<number;gene++) {
                int expressed = 0; double logTotal = 0;
                for (int column=0;column<columns;column++) if (abundance[gene][column]>0) {
                    length[gene][column] = positive(length[gene][column]/abundance[gene][column]);
                    logTotal += Math.log(length[gene][column]); expressed++;
                }
                double replacement = expressed == 0 ? positive(fallback[gene]/isoforms[gene]) : positive(Math.exp(logTotal/expressed));
                for (int column=0;column<columns;column++) if (abundance[gene][column]==0) {
                    length[gene][column] = replacement;
                    fallbacks.add(new LengthFallback(outputIds.get(gene),input.samples.get(column),
                            expressed==0?"all-sample-isoform-arithmetic-mean":"other-sample-geometric-mean",replacement));
                }
            }
        } else { counts=copy(input.counts); abundance=copy(input.abundance); length=copy(input.length); }
        if (scaling != Scaling.NO) {
            double[] fixedLength = new double[counts.length];
            for (int row=0;row<counts.length;row++) fixedLength[row] = scaling==Scaling.SCALED_TPM ? 1 : mean(length[row]);
            if (scaling == Scaling.DTU_SCALED_TPM) {
                var byGene = new HashMap<String,List<Double>>();
                for (int row=0;row<rows;row++) byGene.computeIfAbsent(genes[row],ignored->new ArrayList<>()).add(mean(length[row]));
                var medians = new HashMap<String,Double>();
                byGene.forEach((gene,values)->{ values.sort(Double::compare); int n=values.size(); medians.put(gene,n%2==1?values.get(n/2):finite(values.get(n/2-1)/2+values.get(n/2)/2)); });
                for (int row=0;row<rows;row++) fixedLength[row]=medians.get(genes[row]);
            }
            for (int column=0;column<columns;column++) {
                double library=0, denominator=0;
                for (int row=0;row<counts.length;row++) {
                    library=finite(library+counts[row][column]);
                    denominator=finite(denominator+finite(abundance[row][column]*fixedLength[row]));
                }
                if (!(denominator>0)) throw new IllegalArgumentException("Zero abundance scaling denominator in sample " + input.samples.get(column) + "; use counts-from-abundance no or resolve the empty sample explicitly");
                double factor=finite(library/denominator);
                for (int row=0;row<counts.length;row++) counts[row][column]=finite(finite(abundance[row][column]*fixedLength[row])*factor);
            }
        }
        return new Result(new Estimates(outputIds,input.samples,counts,abundance,length),statuses,fallbacks);
    }

    /**
     * Applies the explicit matching rule to a single ID.
     * @param original ID to match
     * @param policy matching rule
     * @return nonempty matching ID
     */
    public static String matchingId(String original, IdPolicy policy) {
        id(original,"Transcript"); Objects.requireNonNull(policy);
        int position=policy==IdPolicy.EXACT?-1:original.indexOf(policy==IdPolicy.STRIP_VERSION?'.':'|');
        return id(position<0?original:original.substring(0,position),"Matching transcript");
    }
    private static List<String> ids(List<String> values,String kind) {
        Objects.requireNonNull(values); if(values.isEmpty())throw new IllegalArgumentException("Empty "+kind+" universe");
        var seen=new HashSet<String>();for(String value:values)if(!seen.add(id(value,kind)))throw new IllegalArgumentException("Duplicate "+kind+" ID: "+value);
        return List.copyOf(values);
    }
    private static String id(String value,String kind) {
        if(value==null||value.isBlank()||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(kind+" ID must be nonblank without control characters");return value;
    }
    private static double[][] matrix(double[][] values,int rows,int columns,boolean positive,String kind) {
        if(values==null||values.length!=rows)throw new IllegalArgumentException(kind+" row count mismatch");
        for(double[] row:values) { if(row==null||row.length!=columns)throw new IllegalArgumentException(kind+" column count mismatch");for(double value:row)if(!Double.isFinite(value)||value<0||(positive&&value==0))throw new IllegalArgumentException(kind+" must be finite and "+(positive?"positive":"nonnegative")); }
        return copy(values);
    }
    private static double[][] copy(double[][] values) { return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new); }
    private static double mean(double[] values) { double sum=0;for(double value:values)sum=finite(sum+value/values.length);return positive(sum); }
    private static double finite(double value) { if(!Double.isFinite(value))throw new IllegalArgumentException("Transcript arithmetic exceeds finite range; rescale explicitly");return value; }
    private static double positive(double value) { finite(value);if(!(value>0))throw new IllegalArgumentException("Effective length underflow; rescale explicitly");return value; }
}
