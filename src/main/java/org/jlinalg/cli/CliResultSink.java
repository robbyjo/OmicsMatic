/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.pipeline.AssociationPipelineEstimate;
import org.jlinalg.pipeline.AssociationPipelineFailure;
import org.jlinalg.pipeline.AssociationPipelineSink;
import org.jlinalg.pipeline.OmicsAssociationEstimate;
import org.jlinalg.pipeline.OmicsAssociationSink;
import org.jlinalg.pipeline.VariantFilterResult;
import org.jlinalg.pipeline.VariantRecord;
import org.jlinalg.pipeline.VariantStatistics;
import org.jlinalg.pipeline.VariantQualityOptions;
import org.jlinalg.pipeline.VariantQualityDiagnostics;

/** Type-specific streaming result tables for genotype and numeric omics scans. */
final class CliResultSink
        implements AssociationPipelineSink, OmicsAssociationSink, AutoCloseable {
    private static final List<String> INFERENCE_HEADER=List.of("test_method","calibration","estimator","standard_error_method","statistic_type",
        "fit_converged","null_fit_converged","fit_iterations","null_fit_iterations","penalized_log_likelihood","null_penalized_log_likelihood","convergence_reason","log_p_value",
        "result_type","effect_estimation_status","exact_status","exact_reason","exact_alternative","exact_work","exact_maximum_states",
        "test_n_samples","test_n_cases","test_n_controls","test_strata","informative_strata","conditional_score","conditional_null_mean","conditional_null_variance","exact_score_direction");
    private static final List<String> GENOTYPE_HEADER = List.of(
        "status", "id", "chromosome", "position",
        "reference_allele", "alternate_allele", "effect_allele",
        "other_allele", "effect_allele_frequency",
        "minor_allele_frequency", "minor_allele_count",
        "called", "missing", "missing_rate", "imputation_info",
        "hwe_p_all", "hwe_n_all", "hwe_p_cases", "hwe_n_cases",
        "hwe_p_controls", "hwe_n_controls", "hwe_method",
        "beta", "standard_error", "statistic",
        "df_numerator", "df_denominator", "partial_r2", "p_value",
        "filter_reason", "failure_reason",
        "n_samples","allele_number","n_cases","n_controls","called_cases","called_controls",
        "missing_cases","missing_controls","minor_allele_orientation","minor_allele_count_cases","minor_allele_count_controls",
        "missingness_p_value","missingness_method","hwe_filter_p","hwe_filter_n","hwe_filter_method","hwe_filter_scope",
        "genotype_origin","ploidy_status","non_diploid_samples","source_multiallelic_split");
    private static final List<String> OMICS_HEADER = List.of(
        "status", "id", "beta", "standard_error", "statistic",
        "df_numerator", "df_denominator", "partial_r2", "p_value",
        "failure_reason");
    private final boolean genotype;
    private final String statisticType;
    private final AnnotationLookup annotation;
    private final int[] caseControlGroups;
    private final ExternalBh output;
    private final ConditionalGwasExport scoreExport;
    private List<String> scoreFields=List.of();
    private final VariantQualityOptions qualityOptions;
    private VariantQualityDiagnostics quality;
    private VariantStatistics rowStatistics;
    private VariantRecord rowVariant;
    private java.util.Map<String,String> rowInference=java.util.Map.of();
    private long unresolvedTests;

    CliResultSink(
            Path path, boolean overwrite, boolean genotype,
            String statisticType, AnnotationLookup annotation,
            int[] caseControlGroups) throws IOException {
        this(path,overwrite,genotype,statisticType,annotation,caseControlGroups,null);
    }
    CliResultSink(Path path,boolean overwrite,boolean genotype,String statisticType,
            AnnotationLookup annotation,int[] caseControlGroups,ConditionalGwasExport scoreExport) throws IOException {
        this(path,overwrite,genotype,statisticType,annotation,caseControlGroups,scoreExport,
            new VariantQualityOptions(0,"all",caseControlGroups,null,0,100000,"auto",false));
    }
    CliResultSink(Path path,boolean overwrite,boolean genotype,String statisticType,
            AnnotationLookup annotation,int[] caseControlGroups,ConditionalGwasExport scoreExport,VariantQualityOptions qualityOptions) throws IOException {
        if(!java.util.Arrays.equals(caseControlGroups,qualityOptions.caseControlGroups()))throw new IllegalArgumentException("Output QC and model binary groups must agree");
        this.qualityOptions=qualityOptions;
        this.scoreExport=scoreExport;
        this.genotype = genotype;
        this.statisticType = statisticType;
        this.annotation = annotation;
        this.caseControlGroups = caseControlGroups == null ? null
            : caseControlGroups.clone();
        output = new ExternalBh(path, overwrite);
        List<String> header = new ArrayList<>(
            genotype ? GENOTYPE_HEADER : OMICS_HEADER);
        if(genotype)header.addAll(INFERENCE_HEADER);
        for (String column : annotation.columns())
            header.add("annot_" + column);
        if(scoreExport!=null)header.addAll(ConditionalGwasExport.COLUMNS);
        output.writeHeader(header);
    }

    @Override
    public void acceptEstimate(AssociationPipelineEstimate estimate)
            throws IOException {
        VariantRecord variant = estimate.variant();
        scoreFields=scoreExport==null?List.of():scoreExport.summarize(variant);
        VariantStatistics qc = estimate.variantStatistics();
        rowVariant=variant;rowStatistics=qc;
        rowInference=estimate.inference();
        Hwe hwe = hwe(variant);
        writeGenotype("ok", variant.id(), variant.chromosome(),
            variant.position() == 0 ? "" : Long.toString(variant.position()),
            variant.referenceAllele(), variant.alternateAllele(),
            variant.alternateAllele(), variant.referenceAllele(),
            number(qc.alternateAlleleFrequency()),
            number(qc.minorAlleleFrequency()),
            number(qc.minorAlleleCount()),
            Integer.toString(qc.calledSamples()),
            Integer.toString(qc.missingSamples()),
            number(qc.missingRate()), number(variant.imputationQuality()), hwe,
            estimate.beta(), estimate.standardError(), estimate.statistic(),
            estimate.degreesOfFreedom(), estimate.pValue(), "", "");
    }

    @Override
    public void acceptExcluded(VariantFilterResult excluded)
            throws IOException {
        VariantRecord variant = excluded.variant();
        VariantStatistics qc = excluded.statistics();
        rowVariant=variant;rowStatistics=qc;
        rowInference=java.util.Map.of();
        Hwe hwe = hwe(variant);
        writeGenotype("filtered", variant.id(), variant.chromosome(),
            variant.position() == 0 ? "" : Long.toString(variant.position()),
            variant.referenceAllele(), variant.alternateAllele(),
            variant.alternateAllele(), variant.referenceAllele(),
            number(qc.alternateAlleleFrequency()),
            number(qc.minorAlleleFrequency()),
            number(qc.minorAlleleCount()),
            Integer.toString(qc.calledSamples()),
            Integer.toString(qc.missingSamples()),
            number(qc.missingRate()), number(variant.imputationQuality()), hwe,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            excluded.reasons().stream().map(Enum::name)
                .collect(java.util.stream.Collectors.joining(";")), "");
    }

    @Override
    public void acceptEstimate(OmicsAssociationEstimate estimate)
            throws IOException {
        writeOmics("ok", estimate.featureId(), estimate.beta(),
            estimate.standardError(), estimate.statistic(),
            estimate.degreesOfFreedom(), estimate.pValue(), "");
    }

    @Override
    public void acceptFailure(AssociationPipelineFailure failure)
            throws IOException {
        rowVariant=null;rowStatistics=null;quality=null;
        rowInference=failure.inference();
        if(java.util.Set.of("budget_exceeded","numerical_unavailable").contains(rowInference.getOrDefault("exact_status","")))unresolvedTests++;
        String reason = failureReason(
            failure.exceptionType(), failure.message());
        if(genotype&&failure.variant()!=null) {
            VariantRecord variant=failure.variant();VariantStatistics qc=failure.statistics();rowVariant=variant;rowStatistics=qc;Hwe hwe=hwe(variant);
            writeGenotype("failed",variant.id(),variant.chromosome(),variant.position()==0?"":Long.toString(variant.position()),
                variant.referenceAllele(),variant.alternateAllele(),variant.alternateAllele(),variant.referenceAllele(),
                number(qc.alternateAlleleFrequency()),number(qc.minorAlleleFrequency()),number(qc.minorAlleleCount()),
                Integer.toString(qc.calledSamples()),Integer.toString(qc.missingSamples()),number(qc.missingRate()),number(variant.imputationQuality()),hwe,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,"",reason);
            return;
        }
        if (genotype)
            writeGenotype("failed", failure.variantId(), "", "", "", "",
                "", "", "", "", "", "", "", "", "", Hwe.empty(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                "", reason);
        else
            writeOmics("failed", failure.variantId(), Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, reason);
    }

    void finish() throws IOException { output.finish(unresolvedTests==0); }
    long adjustedTests() { return unresolvedTests==0?output.tests():0; }
    long unresolvedTests() { return unresolvedTests; }
    @Override public void close() throws IOException { output.close(); }

    private void writeGenotype(
            String status, String id, String chromosome, String position,
            String reference, String alternate, String effect, String other,
            String eaf, String maf, String mac, String called, String missing,
            String missingRate, String info, Hwe hwe,
            double beta, double standardError, double statistic,
            double degreesOfFreedom, double pValue,
            String filterReason, String failureReason) throws IOException {
        if (!genotype)
            throw new IllegalStateException(
                "genotype row sent to a numeric omics sink");
        List<String> fields = new ArrayList<>(
            GENOTYPE_HEADER.size() + annotation.columns().size());
        fields.addAll(List.of(status, id, chromosome, position,
            reference, alternate, effect, other, eaf, maf, mac, called,
            missing, missingRate, info, number(hwe.all().pValue()),
            integer(hwe.all().samples()), number(hwe.cases().pValue()),
            integer(hwe.cases().samples()), number(hwe.controls().pValue()),
            integer(hwe.controls().samples()), hwe.method()));
        addStatistics(fields, beta, standardError, statistic,
            degreesOfFreedom, pValue);
        fields.add(filterReason);
        fields.add(failureReason);
        fields.addAll(qualityFields());
        for(String column:INFERENCE_HEADER)fields.add(rowInference.getOrDefault(column,column.equals("statistic_type")&&status.equals("ok")?statisticType:""));
        fields.addAll(List.of(annotation.values(id)));
        if(scoreExport!=null) {
            fields.addAll(scoreFields.isEmpty()?java.util.Collections.nCopies(ConditionalGwasExport.COLUMNS.size(),""):scoreFields);
            scoreFields=List.of();
        }
        output.write(fields, pValue);
    }

    private void writeOmics(
            String status, String id, double beta, double standardError,
            double statistic, double degreesOfFreedom, double pValue,
            String failureReason) throws IOException {
        if (genotype)
            throw new IllegalStateException(
                "numeric omics row sent to a genotype sink");
        List<String> fields = new ArrayList<>(
            OMICS_HEADER.size() + annotation.columns().size());
        fields.add(status);
        fields.add(id);
        addStatistics(fields, beta, standardError, statistic,
            degreesOfFreedom, pValue);
        fields.add(failureReason);
        fields.addAll(List.of(annotation.values(id)));
        output.write(fields, pValue);
    }

    private void addStatistics(
            List<String> fields, double beta, double standardError,
            double statistic, double degreesOfFreedom, double pValue) {
        double partial = statisticType.equals("t")
            ? partialR2(statistic, degreesOfFreedom) : Double.NaN;
        fields.add(number(beta));
        fields.add(number(standardError));
        fields.add(number(statistic));
        fields.add(statisticType.equals("conditional_centered_allele_score")?"":"1");
        fields.add(number(degreesOfFreedom));
        fields.add(number(partial));
        fields.add(number(pValue));
    }

    private Hwe hwe(VariantRecord variant) {
        quality=VariantQualityDiagnostics.calculate(variant,rowStatistics,qualityOptions);
        return new Hwe(convert(quality.hweAll()),convert(quality.hweCases()),convert(quality.hweControls()),quality.hweAll().method());
    }
    private static HardyWeinberg.Result convert(org.jlinalg.genetics.HardyWeinberg.Result r){return new HardyWeinberg.Result(r.pValue(),r.samples(),r.method());}
    private List<String> qualityFields() {
        if(quality==null)return java.util.Collections.nCopies(21,"");
        var cases=quality.cases();var controls=quality.controls();boolean binary=caseControlGroups!=null,filtered=qualityOptions.minimumHweP()>0;
        return List.of(Integer.toString(rowStatistics.samples()),number(rowStatistics.calledAlleleNumber()),
            binary?Integer.toString(cases.samples()):"",binary?Integer.toString(controls.samples()):"",
            binary?Integer.toString(cases.called()):"",binary?Integer.toString(controls.called()):"",
            binary?Integer.toString(cases.missing()):"",binary?Integer.toString(controls.missing()):"",
            rowStatistics.calledSamples()==0?"":quality.minorIsAlternate()?"alternate":"reference",
            binary?number(cases.minorAlleleCount()):"",binary?number(controls.minorAlleleCount()):"",
            number(quality.missingnessP()),quality.missingnessMethod(),filtered?number(quality.filterHwe().pValue()):"",
            filtered?Integer.toString(quality.filterHwe().samples()):"",filtered?quality.filterHwe().method():"disabled",filtered?qualityOptions.hweScope():"disabled",
            rowVariant.genotypeMetadata().origin().name().toLowerCase(java.util.Locale.ROOT),rowVariant.genotypeMetadata().ploidyStatus(),Integer.toString(rowVariant.genotypeMetadata().nonDiploidSamples()),Boolean.toString(rowVariant.genotypeMetadata().splitMultiallelic()));
    }

    private static String failureReason(String type, String message) {
        String kind = type == null ? "" : type.trim();
        String detail = message == null ? "" : message.trim();
        if (kind.isEmpty()) return detail;
        if (detail.isEmpty() || detail.equals(kind)) return kind;
        return kind + ": " + detail;
    }

    private static double partialR2(double statistic, double df) {
        if (!Double.isFinite(statistic) || !Double.isFinite(df) || df <= 0)
            return Double.NaN;
        double squared = statistic * statistic;
        return squared / (squared + df);
    }
    private static String integer(int value) {
        return value == 0 ? "" : Integer.toString(value);
    }
    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "";
    }

    private record Hwe(
            HardyWeinberg.Result all,
            HardyWeinberg.Result cases,
            HardyWeinberg.Result controls,
            String method) {
        private static Hwe empty() {
            HardyWeinberg.Result empty =
                new HardyWeinberg.Result(Double.NaN, 0, "");
            return new Hwe(empty, empty, empty, "");
        }
    }
}
