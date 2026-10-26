/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdistlib.T;
import org.jlinalg.pipeline.DataFormat;
import org.jlinalg.pipeline.DelimitedMatrixSource;
import org.jlinalg.pipeline.VariantBlock;
import org.jlinalg.pipeline.VariantSource;
import org.jlinalg.pipeline.VariantSources;
import org.jlinalg.plot.PlotOptions;
import org.jlinalg.plot.Plots;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;

/** Local sample alignment, complete-case selection, call-rate QC and diagnostics. */
final class StudyPrefitQc implements StudyOperationExecutor {
    @Override public Result execute(Context context) throws IOException {
        Files.createDirectories(context.outputDirectory());
        String datasetId = text(context.operation().get("dataset_id"));
        Map<String, Object> dataset = StudyBioinformatics.datasets(context).stream()
            .filter(row -> datasetId.equals(row.get("id"))).findFirst().orElseThrow();
        Map<String, Object> hypothesis = StudyBioinformatics.hypothesis(context);
        Map<String, Object> files = map(dataset.get("files")), columns = map(dataset.get("columns"));
        Map<String, Object> analysis = map(hypothesis.get("analysis")), options = map(analysis.get("association"));
        org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(options);
        boolean nativeAssociation=StudyCliExecutor.supportsBulkAssociation(text(dataset.get("type")))&&list(context.plan().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(op->("association".equals(op.get("type"))||"ranked_gsea".equals(op.get("type"))&&map(analysis.get("gsea")).containsKey("phenotype"))&&!op.containsKey("supplied_result_id")&&!map(op.get("configuration")).containsKey("command")&&text(op.get("hypothesis")).equals(text(hypothesis.get("id")))&&text(op.get("dataset_id")).equals(datasetId));
        if(text(options.get("model")).isBlank()&&(nativeAssociation||options.containsKey("survival")))return new StudyModelReview().execute(context,hypothesis,dataset);
        boolean survival="cox".equals(options.get("model"));
        var survivalPolicy=survival?org.jlinalg.study.StudySurvivalPolicy.resolve(map(options.get("survival"))):Map.<String,Object>of();
        if(survival){var prerequisite=StudySurvivalSupport.prerequisite(context,survivalPolicy);if(prerequisite!=null)return prerequisite;}
        var genotypeQcPolicy=org.jlinalg.study.StudyGenotypeSampleQcPolicy.forDataset(org.jlinalg.study.StudyGenotypeSampleQcPolicy.resolve(map(analysis.get("genotype_sample_qc"))),datasetId);
        var generatedPcs=org.jlinalg.study.StudyGenotypeSampleQcPolicy.pcCovariates(genotypeQcPolicy);
        boolean genotype = Set.of("gwas", "genomics").contains(dataset.get("type"));boolean extendedGenotypeQc=genotype&&Boolean.TRUE.equals(genotypeQcPolicy.get("enabled"));
        var humanPloidy=org.jlinalg.study.StudyHumanPloidyPolicy.resolve(options);org.jlinalg.study.StudyHumanPloidyPolicy.dataset(dataset,humanPloidy);
        HumanPloidySamples ploidySamples=humanPloidy.isEmpty()?null:new HumanPloidySamples(context.project().resolve(text(files.get("ploidy_samples"))));
        HumanPloidySamples.Selection ploidySelection=null;
        List<String> ploidyOutputs=new ArrayList<>();
        if(!genotype&&!generatedPcs.isEmpty())throw new IOException("Generated genotype PCs do not apply to a non-genotype matrix");
        Path originalPhenotype=context.project().resolve(text(files.get("phenotype")));var originalQcInputs=new LinkedHashMap<String,String>();if(extendedGenotypeQc)originalQcInputs.put("phenotype",FollowupSupport.hash(originalPhenotype));
        DelimitedData phenotype = DelimitedData.read(originalPhenotype);
        String idName = text(columns.getOrDefault("sample_id", "id"));
        int id = phenotype.column(idName);
        String outcome = survival?StudySurvivalSupport.column(columns,survivalPolicy,"event_column"):mapped(map(columns.get("phenotypes")), text(hypothesis.get("outcome")));
        List<String> variables = new ArrayList<>(List.of(outcome));
        if(survival)for(String field:List.of("time_column","entry_column","strata_column"))if(survivalPolicy.containsKey(field)){String name=StudySurvivalSupport.column(columns,survivalPolicy,field);if(!variables.contains(name))variables.add(name);}
        for (Object covariate : list(hypothesis.get("covariates"))) {
            if(generatedPcs.contains(text(covariate))){if(!text(map(columns.get("covariates")).getOrDefault(text(covariate),covariate)).equals(covariate))throw new IOException("Generated genotype PC columns cannot be remapped");continue;}
            String name = mapped(map(columns.get("covariates")), text(covariate));
            if (!variables.contains(name)) variables.add(name);
        }
        var association=map(map(hypothesis.get("analysis")).get("association"));
        if(association.containsKey("offset_column")) {
            String offset=mapped(map(columns.get("covariates")),text(association.get("offset_column")));
            if(!variables.contains(offset))variables.add(offset);
        }
        if(association.containsKey("exact_strata_column")) {
            String strata=mapped(map(columns.get("covariates")),text(association.get("exact_strata_column")));if(!variables.contains(strata))variables.add(strata);
        }
        for (String variable : variables) phenotype.column(variable);
        Path matrix = StudyAssayQcSources.matrix(context,dataset);
        if(extendedGenotypeQc){originalQcInputs.put("matrix",FollowupSupport.hash(matrix));for(String role:List.of("sample","samples","qc_genotypes","qc_samples","sample_qc_report","sample_qc_samples","sample_qc_pcs"))if(files.containsKey(role))originalQcInputs.put(role,FollowupSupport.hash(context.project().resolve(text(files.get(role)))));}
        VariantSource source = null;
        List<String> matrixIds;
        if (genotype) {
            String sampleRole = files.containsKey("sample") ? "sample" : "samples";
            List<String> external = files.containsKey(sampleRole) ? SampleFiles.read(context.project().resolve(text(files.get(sampleRole)))) : null;
            source = VariantSources.open(matrix, DataFormat.infer(matrix), external);
            matrixIds = source.metadata().sampleIds();
        } else matrixIds = DelimitedMatrixSource.open(matrix).metadata().sampleIds();
        Set<String> available = new HashSet<>(matrixIds), seen = new HashSet<>();
        List<String[]> retained = new ArrayList<>();
        List<String[]> sampleExclusions = new ArrayList<>();
        Map<String, Integer> excluded = new LinkedHashMap<>();
        for (String[] row : phenotype.rows()) {
            if (row[id].isBlank() || !seen.add(row[id])) throw new IOException("Participant QC requires unique nonblank sample IDs; resolve repeated visits before association");
            String reason = !available.contains(row[id]) ? "not_in_matrix" : "";
            if (reason.isEmpty() && variables.stream().anyMatch(name -> missing(row[phenotype.column(name)]))) reason = "missing_outcome_or_covariate";
            if (reason.isEmpty()) retained.add(row); else {
                excluded.merge(reason, 1, Integer::sum);
                sampleExclusions.add(new String[] {row[id], reason, "NA"});
            }
        }
        long examinedVariants = 0, missingQuality = 0;
        if(ploidySamples!=null){ploidySelection=ploidySamples.select(retained.stream().map(row->row[id]).toList(),org.jlinalg.study.StudyHumanPloidyPolicy.profile(humanPloidy));var selectedIds=new HashSet<>(ploidySelection.ids());retained=new ArrayList<>(retained.stream().filter(row->selectedIds.contains(row[id])).toList());for(String[] row:ploidySelection.audit())if(!row[2].equals("selected")){excluded.merge(row[2],1,Integer::sum);sampleExclusions.add(new String[]{row[0],row[2],"NA"});}}
        boolean suppliedGenotypeQc=extendedGenotypeQc&&"supplied".equals(genotypeQcPolicy.get("source"));
        StudyExternalGenotypeQc.Loaded externalQc=null;StudyGenotypeCallRate.Quality externalQuality=null;
        if(suppliedGenotypeQc){externalQc=StudyExternalGenotypeQc.load(context,dataset,hypothesis,phenotype,retained,matrixIds,genotypeQcPolicy,originalQcInputs);retained=new ArrayList<>(externalQc.retained());for(String[] row:externalQc.exclusions()){sampleExclusions.add(row);excluded.merge(row[1],1,Integer::sum);}}
        boolean nativePca=!suppliedGenotypeQc||"native".equals(genotypeQcPolicy.get("pca_source"))&&((Number)genotypeQcPolicy.get("pca_components")).intValue()>0;
        boolean impossibleQcBound=extendedGenotypeQc&&nativePca&&(retained.size()>((Number)genotypeQcPolicy.get("maximum_samples")).intValue()||(double)retained.size()*((Number)genotypeQcPolicy.get("panel_markers")).longValue()>((Number)genotypeQcPolicy.get("maximum_genotype_cells")).longValue()||((Number)genotypeQcPolicy.get("pca_components")).intValue()>0&&(double)retained.size()*retained.size()*retained.size()>((Number)genotypeQcPolicy.get("maximum_pca_work")).longValue());
        if(externalQc!=null&&externalQc.sampleComplete()&&!retained.isEmpty()&&!impossibleQcBound){externalQuality=StudyGenotypeCallRate.quality(hypothesis,source,matrixIds,retained.stream().map(row->row[id]).toList(),options,genotypeQcPolicy);missingQuality=externalQuality.missing();}
        StudyGenotypeCallRate.Selection observedCallRate=null;
        if(extendedGenotypeQc&&!suppliedGenotypeQc&&source!=null&&!retained.isEmpty()&&!impossibleQcBound){observedCallRate=StudyGenotypeCallRate.select(context,dataset,hypothesis,phenotype,retained,source,matrixIds,options,genotypeQcPolicy);examinedVariants=observedCallRate.examined();missingQuality=observedCallRate.missingQuality();retained=new ArrayList<>(observedCallRate.retained());for(String[] row:observedCallRate.exclusions()){sampleExclusions.add(row);excluded.merge(row[1],1,Integer::sum);}}
        else if (source != null && !retained.isEmpty()&&!impossibleQcBound&&!extendedGenotypeQc) {
            HumanPloidySession ploidySession=ploidySamples==null?null:new HumanPloidySession(context.outputDirectory().resolve("prefit-callrate"),org.jlinalg.study.StudyHumanPloidyPolicy.profile(humanPloidy),ploidySamples,ploidySelection);
            VariantSource callRateSource=ploidySession==null?source:ploidySession.wrap(source,"hard_calls".equals(options.get("genotype_kind")));
            if(ploidySession!=null)for(String suffix:List.of(".human-ploidy.yaml",".human-ploidy-samples.tsv",".human-ploidy-variants.tsv",".human-ploidy-invalid-calls.tsv"))ploidyOutputs.add("prefit-callrate"+suffix);
            Map<String, Integer> sourcePositions = new HashMap<>();
            for (int i = 0; i < matrixIds.size(); i++) sourcePositions.put(matrixIds.get(i), i);
            int[] order = retained.stream().mapToInt(row -> sourcePositions.get(row[id])).toArray();
            long[] called = new long[order.length];
            try (ploidySession;var reader = callRateSource.open(order)) {
                VariantBlock block;
                while ((block = reader.read(1024)) != null) for (var variant : block.variants()) {
                    examinedVariants++;
                    if (!Double.isFinite(variant.imputationQuality())) missingQuality++;
                    double[] dosage = variant.dosages();
                    for (int i = 0; i < dosage.length; i++) if (Double.isFinite(dosage[i])) {
                        if (dosage[i] < 0 || dosage[i] > variant.genotypeMetadata().declaredOrAssumedPloidy(i)) throw new IOException("Genotype dosage is outside its observed or declared ploidy");
                        called[i]++;
                    }
                }
                if(ploidySession!=null)ploidySession.finish();
            }
            if (examinedVariants == 0) return waiting(context, "No genotype variants were available for call-rate QC");
            double minimum = ((Number) options.getOrDefault("minimum_sample_call_rate", 0.98)).doubleValue();
            List<String[]> passed = new ArrayList<>();
            for (int i = 0; i < retained.size(); i++) {
                if ((double) called[i] / examinedVariants >= minimum) passed.add(retained.get(i));
                else {
                    excluded.merge("sample_call_rate", 1, Integer::sum);
                    sampleExclusions.add(new String[] {retained.get(i)[id], "sample_call_rate", Double.toString((double) called[i] / examinedVariants)});
                }
            }
            retained = passed;
        }
        List<String> inputVariables=List.copyOf(variables);StudyGenotypeSampleQc.Output genotypeQc=null;
        if(extendedGenotypeQc){
            if(externalQc==null)genotypeQc=StudyGenotypeSampleQc.execute(context,dataset,hypothesis,phenotype,retained,matrixIds,source,genotypeQcPolicy,originalQcInputs,observedCallRate==null?List.of():observedCallRate.problems());
            else {var prerequisites=new ArrayList<String>();if(externalQuality!=null)prerequisites.addAll(externalQuality.problems());
                if(nativePca){prerequisites.addAll(externalQc.problems());genotypeQc=StudyGenotypeSampleQc.execute(context,dataset,hypothesis,phenotype,retained,matrixIds,source,genotypeQcPolicy,originalQcInputs,prerequisites,externalQc);}
                else genotypeQc=StudyExternalGenotypeQc.finish(context,dataset,hypothesis,phenotype,externalQc,genotypeQcPolicy,originalQcInputs,prerequisites);
            }
        }
        final DelimitedData analysisData=genotypeQc!=null&&genotypeQc.complete()?DelimitedData.read(genotypeQc.phenotype()):phenotype;
        boolean genotypeQcHeld=genotypeQc!=null&&!genotypeQc.complete();
        if(genotypeQc!=null&&genotypeQc.complete()){retained=new ArrayList<>(analysisData.rows());for(String pc:generatedPcs)if(!variables.contains(pc))variables.add(pc);for(String[] row:genotypeQc.exclusions()){sampleExclusions.add(row);excluded.merge(row[1],1,Integer::sum);}}
        Map<String, Object> summary = new LinkedHashMap<>();
        if(ploidySelection!=null){ploidySamples.verify();summary.put("human_ploidy",humanPloidy);summary.put("ploidy_population_selection",ploidySelection.summary());summary.put("ploidy_sample_declaration_sha256",ploidySamples.hash);FollowupSupport.table(context.outputDirectory().resolve("ploidy-population-selection.tsv"),List.of("id","declared_complement","disposition"),ploidySelection.audit());ploidyOutputs.add("ploidy-population-selection.tsv");}
        summary.put("dataset_id", datasetId); summary.put("input_phenotype_records", phenotype.rows().size());
        summary.put("matrix_samples", matrixIds.size()); summary.put("analysis_samples", genotypeQcHeld?0:retained.size());summary.put("candidate_samples_for_diagnostics",retained.size());
        if(genotypeQc!=null)summary.put("genotype_sample_qc",genotypeQc.summary());
        if(observedCallRate!=null)summary.put("observed_genotype_call_rate",observedCallRate.summary());
        if(externalQc!=null){summary.put("external_sample_qc",externalQc.summary());summary.put("sample_call_rate_panel","Externally declared observed-autosomal call-rate scope; source records preserved without native re-execution");if(externalQuality!=null)summary.put("imputation_quality_availability",Map.of("status",externalQuality.status(),"examined_variants",externalQuality.examined(),"missing_annotations",externalQuality.missing(),"scope","Availability probe only; per-marker quality filters remain required"));}
        summary.put("excluded_by_reason", excluded); summary.put("sample_call_rate_panel", suppliedGenotypeQc?"External observed-autosomal call-rate scope, preserved without native re-execution":extendedGenotypeQc?"registered observed hard-call autosomal SNP panel before within-run sample/MAF/LD filters":!humanPloidy.isEmpty()?"registered human-ploidy region and complement, after explicit normalization and before marker filtering":"all supplied genotype variants before association filtering");
        Map<String, Long> missingCounts = new LinkedHashMap<>();
        for (String variable : inputVariables) missingCounts.put(variable,
            phenotype.rows().stream().filter(row -> missing(row[phenotype.column(variable)])).count());
        summary.put("input_missing_counts", missingCounts);
        summary.put("examined_genotype_variants", examinedVariants);
        summary.put("examined_genotype_variant_scope",suppliedGenotypeQc?"No native sample call-rate scan; source QC was supplied. Any main-matrix quality availability probe is counted separately":!humanPloidy.isEmpty()&&!extendedGenotypeQc?"Registered human-ploidy region scan before marker filtering; structurally absent chromosomes are outside the registered population":"Initial sample call-rate source scan before association filtering");
        summary.put("sample_call_rate_status",externalQc!=null?externalQc.sampleComplete()?"externally_supplied":"external_scope_unresolved":impossibleQcBound?"not_examined_registered_native_qc_bound":observedCallRate!=null?!observedCallRate.problems().isEmpty()?"unresolved":((Number)observedCallRate.summary().get("eligible_call_rate_sites")).longValue()==0?"not_estimable_no_eligible_hard_call_sites":"complete":extendedGenotypeQc?"not_examined_no_eligible_participants":genotype?"complete":"not_applicable");
        summary.put("variants_without_imputation_quality", missingQuality);
        summary.put("imputation_quality_count_scope",extendedGenotypeQc?"Main-matrix availability probe only; not a whole-source quality audit":"All examined main-matrix variants");
        summary.put("sample_selection", "Phenotype/matrix intersection, unique IDs, complete source outcome/covariates, then declared genotype call rate and optional registered panel QC/relatedness action. Generated genotype PCs are computed after source eligibility and enter the model only when registered. No outlier-based participant deletion.");
        summary.put("privacy", "Aggregate diagnostics are local. analysis-phenotype.tsv contains participant data and must not enter remote assistance payloads.");
        if(survival&&!retained.isEmpty()){
            try{summary.put("survival",StudySurvivalSupport.population(analysisData,retained,columns,survivalPolicy).summary());}
            catch(IOException invalid){return StudySurvivalSupport.waiting(context,invalid.getMessage());}
        }
        if (retained.isEmpty()) {
            var need=StudyNeed.review("prefit-qc:"+datasetId,"Resolve the participant-QC prerequisite","No eligible participants remain after sample QC",true);summary.put("needs",List.of(need.document()));write(context.outputDirectory().resolve("prefit-qc.yaml"), summary);FollowupSupport.table(context.outputDirectory().resolve("sample-exclusions.tsv"),List.of("sample_id","reason","observed_call_rate"),sampleExclusions);var emptyOutputs=new ArrayList<>(List.of("prefit-qc.yaml","sample-exclusions.tsv"));emptyOutputs.addAll(ploidyOutputs);if(genotypeQc!=null)emptyOutputs.addAll(genotypeQc.outputs());if(observedCallRate!=null)emptyOutputs.addAll(observedCallRate.outputs());return new Result(State.WAITING_FOR_INPUT,"No eligible participants remain after sample QC",emptyOutputs,summary);
        }
        String phenotypeOutput=genotypeQcHeld?"diagnostic-phenotype.tsv":"analysis-phenotype.tsv";
        FollowupSupport.table(context.outputDirectory().resolve(phenotypeOutput), analysisData.header(), retained);
        FollowupSupport.table(context.outputDirectory().resolve("sample-exclusions.tsv"), List.of("sample_id", "reason", "observed_call_rate"), sampleExclusions);
        List<Object> distributions = new ArrayList<>(), differences = new ArrayList<>();
        List<String> outputs = new ArrayList<>(List.of(phenotypeOutput, "sample-exclusions.tsv", "prefit-qc.yaml"));outputs.addAll(ploidyOutputs);if(genotypeQc!=null)outputs.addAll(genotypeQc.outputs());if(observedCallRate!=null)outputs.addAll(observedCallRate.outputs());
        List<Group> groups = new ArrayList<>();
        for (String variable : variables) if (!variable.equals(outcome) && variable.toLowerCase(java.util.Locale.ROOT).matches(".*(sex|gender|obesity).*"))
            groups.add(new Group(variable, variable, null, "",Set.of()));
        Set<String> explicitGroups = new HashSet<>();
        for (Object raw : list(analysis.get("subgroups"))) {
            Map<String, Object> group = map(raw);
            for (String key : group.keySet()) if (!Set.of("id", "column", "cutoff", "comparison","derived_from").contains(key))
                throw new IOException("Unknown subgroup setting: " + key);
            String name = mapped(map(columns.get("covariates")), text(group.get("column")));
            if(genotypeQcHeld&&generatedPcs.contains(name)){distributions.add(Map.of("variable",name,"group",group.getOrDefault("id",name),"status","unavailable_generated_pc_group","reason","Registered genotype QC is unresolved; no PC-defined subgroup was inferred"));continue;}analysisData.column(name);
            Double cutoff = null;
            if (group.containsKey("cutoff")) {
                if (!(group.get("cutoff") instanceof Number value) || !Double.isFinite(value.doubleValue()))
                    throw new IOException("Subgroup cutoff must be a finite number");
                cutoff = value.doubleValue();
            }
            String comparison = text(group.getOrDefault("comparison", cutoff == null ? "" : "greater_than"));
            if (cutoff != null && !Set.of("greater_than", "greater_than_or_equal", "less_than", "less_than_or_equal").contains(comparison)
                    || cutoff == null && !comparison.isEmpty()) throw new IOException("Invalid subgroup cutoff/comparison contract");
            String groupId = text(group.getOrDefault("id", cutoff == null ? name : name + "_" + comparison + "_" + cutoff));
            if (groupId.isBlank() || !explicitGroups.add(groupId))
                throw new IOException("Subgroup IDs must be nonblank and unique: " + groupId);
            groups.removeIf(existing -> existing.id().equals(groupId));
            var derived=new HashSet<String>();for(Object rawName:list(group.get("derived_from")))derived.add(mapped(map(columns.get("covariates")),text(map(columns.get("phenotypes")).getOrDefault(text(rawName),rawName))));
            groups.add(new Group(groupId, name, cutoff, comparison,derived));
        }
        var interactionPolicy=org.jlinalg.study.StudySubgroupInteractionPolicy.resolve(map(analysis.get("subgroup_interactions")));
        if(Boolean.TRUE.equals(interactionPolicy.get("enabled")))for(Object raw:list(interactionPolicy.get("groups"))) {
            var g=map(raw);String name=mapped(map(columns.get("covariates")),text(map(columns.get("phenotypes")).getOrDefault(text(g.get("column")),g.get("column"))));if(genotypeQcHeld&&generatedPcs.contains(name)){distributions.add(Map.of("variable",name,"group",g.get("id"),"status","unavailable_generated_pc_group","reason","Registered genotype QC is unresolved; no PC-defined subgroup was inferred"));continue;}analysisData.column(name);
            Double cutoff=g.get("cutoff") instanceof Number n?n.doubleValue():null;String operator=cutoff==null?"":text(g.get("comparison"));
            var derived=new HashSet<String>();for(Object key:list(g.get("derived_from")))derived.add(mapped(map(columns.get("covariates")),text(map(columns.get("phenotypes")).getOrDefault(text(key),key))));
            String groupId=text(g.get("id"));for(Group existing:groups)if(existing.id().equals(groupId)&&(!existing.column().equals(name)||!java.util.Objects.equals(existing.cutoff(),cutoff)||!existing.comparison().equals(operator)))throw new IOException("Descriptive and interaction subgroup definitions disagree: "+groupId);
            groups.removeIf(existing->existing.id().equals(groupId));groups.add(new Group(groupId,name,cutoff,operator,derived));
        }
        for (String variable : variables) {
            List<Double> values = numeric(retained, analysisData.column(variable));
            Map<String, Object> distribution = new LinkedHashMap<>();
            distribution.put("variable", variable); distribution.put("N", retained.size());
            Map<String, Integer> categories = categories(retained, analysisData.column(variable));
            if (values.size() == retained.size() && groups.stream().noneMatch(group -> group.column().equals(variable) && group.cutoff() == null)) {
                distribution.put("mean", values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                distribution.put("outlier_warning_count", outliers(values));
                distribution.put("outlier_rule", "absolute normal-consistent median/MAD score > 4; warning only; skipped when scale is zero");
                String stem = "distribution-" + variables.indexOf(variable);
                plot(context, values, stem, variable, outputs);
                for (Group group : groups) {
                    Map<String, List<Double>> partitions = new LinkedHashMap<>();
                    for (String[] row : retained) {
                        String label = group.label(row[analysisData.column(group.column())]);
                        if (label == null) continue;
                        partitions.computeIfAbsent(label, ignored -> new ArrayList<>()).add(Double.valueOf(row[analysisData.column(variable)]));
                    }
                    if (partitions.size() > 10) {
                        distributions.add(Map.of("variable", variable, "group", group.id(), "status", "too_many_groups", "groups", partitions.size()));
                        continue;
                    }
                    int partition = 0;
                    for (var entry : partitions.entrySet()) plot(context, entry.getValue(), stem + "-" + groups.indexOf(group) + "-" + ++partition,
                        variable + " by " + group.id() + " = " + entry.getKey(), outputs);
                    if (partitions.size() == 2) {
                        var samples = new ArrayList<>(partitions.values());
                        Map<String, Object> comparison = new LinkedHashMap<>(Map.of("variable", variable, "group", group.document(),
                            "group_labels", List.copyOf(partitions.keySet()), "group_N", samples.stream().map(List::size).toList()));
                        comparison.put("group_means", samples.stream().map(sample -> sample.stream().mapToDouble(Double::doubleValue).average().orElseThrow()).toList());
                        boolean dependent = files.containsKey("grm") || files.containsKey("pedigree") || Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants")) || Boolean.TRUE.equals(map(dataset.get("metadata")).get("repeated_measures"));
                        boolean circular = group.column().equals(variable)||group.derived().contains(variable);
                        Double p = dependent || circular ? null : welch(samples.get(0), samples.get(1));
                        comparison.put("p_value", p == null ? "NA" : p);
                        comparison.put("method", circular ? "No inferential test: groups are defined by the variable being compared"
                            : dependent ? "adjusted dependence-aware comparison required" : "unadjusted two-sided Welch mean comparison; diagnostic only, not an interaction test");
                        differences.add(comparison);
                    }
                }
            } else {
                distribution.put("category_counts", categories);
                if (categories.size() <= 32) {
                    String stem = "distribution-" + variables.indexOf(variable);
                    var figure = Plots.barChart(List.copyOf(categories.keySet()), categories.values().stream().mapToDouble(Integer::doubleValue).toArray(),
                        PlotOptions.builder().title(variable).xLabel(variable).yLabel("Count").size(850, 620).build());
                    save(context, figure, stem, outputs);
                } else distribution.put("plot_status", "category_plot_budget_exceeded");
            }
            distributions.add(distribution);
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (int i = 0; i < differences.size(); i++) {
            Object p = map(differences.get(i)).get("p_value"); if (p instanceof Number value) probabilities.put(Integer.toString(i), value.doubleValue());
        }
        Map<String, Double> adjusted = StudyCliExecutor.bh(probabilities);
        for (int i = 0; i < differences.size(); i++) {
            var comparison = map(differences.get(i));
            comparison.put("adjusted_p", adjusted.containsKey(Integer.toString(i)) ? adjusted.get(Integer.toString(i)) : "NA");
            comparison.put("sensitivity_candidate", adjusted.getOrDefault(Integer.toString(i), 1.0) < 0.05);
            comparison.put("candidate_basis","unadjusted_distribution_review_only");comparison.put("effect_modification_established",false);
            differences.set(i, comparison);
        }
        summary.put("distributions", distributions); summary.put("subgroups", groups.stream().map(Group::document).toList()); summary.put("subgroup_comparisons", differences);
        summary.put("subgroup_interpretation", "Diagnostic differences suggest review. They do not establish adjusted effect modification or change primary models. Outcome-defined subgroups require an explicit scientific plan.");
        summary.put("checks_not_established", List.of("assay/caller-specific quality", "contamination and sample swaps", "ancestry/kinship correctness", humanPloidy.isEmpty()?"biological sex/PAR/ploidy interpretation":"inferred sex or certified karyotype, pooled X-inactivation/interaction, non-XX/XY complements and chromosome-specific model calibration", "sparse or imbalanced binary model calibration", "adjusted subgroup interaction"));
        summary.put("method_report", StudyMethods.record("Sample eligibility and prefit distribution diagnostics",
            Map.of("dataset", datasetId, "complete_case_variables", variables, "analysis_samples", genotypeQcHeld?0:retained.size(),"diagnostic_samples",retained.size(),
                "genotype_call_rate_threshold", genotype ? options.getOrDefault("minimum_sample_call_rate", 0.98) : "not_applicable",
                "outlier_policy", "median/MAD score > 4 warning only", "subgroup_test", "independent two-group Welch; BH across estimable diagnostics"),
            List.of(), options.get("rationale"), List.of("sample quality control", "Welch t-test")));
        if(genotypeQc!=null){var method=new LinkedHashMap<>(map(summary.get("method_report")));var parameters=new LinkedHashMap<>(map(method.get("parameters")));parameters.put("genotype_sample_qc",genotypeQc.summary().get("method_report"));parameters.put("genotype_sample_qc_complete",genotypeQc.complete());method.put("parameters",parameters);if(genotypeQc.complete())method.put("references",map(genotypeQc.summary().get("method_report")).get("references"));summary.put("method_report",method);}
        if(genotypeQcHeld){var need=StudyNeed.review("genotype-sample-qc:"+datasetId,"Resolve the registered genotype sample-QC prerequisite",String.join("; ",list(genotypeQc.summary().get("problems")).stream().map(StudyPublicEvidence::text).toList()),true);summary.put("needs",List.of(need.document()));write(context.outputDirectory().resolve("prefit-qc.yaml"),summary);return new Result(State.WAITING_FOR_INPUT,"Genotype sample QC is unresolved; diagnostic rows cannot enter association",outputs,summary);}
        if (genotype && !extendedGenotypeQc && "imputed".equals(options.get("genotype_kind")) && missingQuality == examinedVariants) {
            StudyNeed need = StudyNeed.review("imputation-quality:" + datasetId, "Where is the compatible per-variant imputation-quality metadata?",
                "The imputed genotype source contains no quality values. Missing quality cannot be treated as a passing INFO/R-squared value.", true);
            summary.put("needs", List.of(need.document()));
            write(context.outputDirectory().resolve("prefit-qc.yaml"), summary);
            return new Result(State.WAITING_FOR_INPUT, "Imputed genotype association requires quality metadata", outputs, summary);
        }
        write(context.outputDirectory().resolve("prefit-qc.yaml"), summary);
        return new Result(State.COMPLETE, "Selected " + retained.size() + " analyzed participants and generated prefit distributions", outputs, summary);
    }

    private static Result waiting(Context context, String reason) throws IOException {
        StudyNeed need = StudyNeed.review("prefit-qc:" + context.operation().get("dataset_id"), "How should the participant-QC prerequisite be resolved?", reason, true);
        return new Result(State.WAITING_FOR_INPUT, reason, Files.exists(context.outputDirectory().resolve("prefit-qc.yaml")) ? List.of("prefit-qc.yaml") : List.of(), Map.of("needs", List.of(need.document())));
    }

    private static String mapped(Map<String, Object> mapping, String key) { return text(mapping.getOrDefault(key, key)); }
    private record Group(String id, String column, Double cutoff, String comparison,Set<String> derived) {
        Map<String, Object> document() {
            Map<String, Object> result = new LinkedHashMap<>(Map.of("id", id, "column", column));
            if (cutoff != null) { result.put("cutoff", cutoff); result.put("comparison", comparison); }
            if(!derived.isEmpty())result.put("derived_from",derived.stream().sorted().toList());
            return result;
        }
        String label(String raw) throws IOException {
            if (missing(raw)) return null;
            if (cutoff == null) return raw;
            double value;
            try { value = Double.parseDouble(raw); }
            catch (NumberFormatException failure) { throw new IOException("Threshold subgroup needs a numeric column: " + column, failure); }
            if (!Double.isFinite(value)) return null;
            boolean selected = switch (comparison) {
                case "greater_than" -> value > cutoff;
                case "greater_than_or_equal" -> value >= cutoff;
                case "less_than" -> value < cutoff;
                case "less_than_or_equal" -> value <= cutoff;
                default -> throw new IOException("Unsupported subgroup comparison");
            };
            return selected ? "criterion_met" : "criterion_not_met";
        }
    }
    private static boolean missing(String value) { return Set.of("", ".", "NA", "N/A", "NAN", "NULL", "INF", "INFINITY", "-INF", "-INFINITY").contains(value.strip().toUpperCase(java.util.Locale.ROOT)); }
    private static List<Double> numeric(List<String[]> rows, int column) {
        List<Double> values = new ArrayList<>();
        for (String[] row : rows) try { double value = Double.parseDouble(row[column]); if (Double.isFinite(value)) values.add(value); }
        catch (NumberFormatException ignored) { /* Categorical variables retain counts. */ }
        return values;
    }
    private static Map<String, Integer> categories(List<String[]> rows, int column) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] row : rows) counts.merge(row[column], 1, Integer::sum);
        return counts;
    }
    private static void plot(Context context, List<Double> values, String stem, String title, List<String> outputs) throws IOException {
        if (values.isEmpty()) return;
        double[] data = values.stream().mapToDouble(Double::doubleValue).toArray();
        var settings = PlotOptions.builder().title(title).xLabel(title).yLabel("Count").size(850, 620).build();
        var figure = isConstant(data) ? Plots.barChart(List.of(Double.toString(data[0])), new double[] {data.length}, settings)
            : Plots.histogram(data, Math.min(30, Math.max(1, (int) Math.sqrt(data.length))), settings);
        save(context, figure, stem, outputs);
    }
    private static boolean isConstant(double[] values) { return java.util.Arrays.stream(values).allMatch(value -> value == values[0]); }
    private static void save(Context context, org.jlinalg.plot.Plot figure, String stem, List<String> outputs) throws IOException {
        for (String extension : List.of("png", "pdf")) {
            String name = stem + "." + extension; figure.save(context.outputDirectory().resolve(name)); outputs.add(name);
        }
    }
    private static int outliers(List<Double> values) {
        double[] ordered = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = median(ordered);
        double[] deviations = values.stream().mapToDouble(value -> Math.abs(value - median)).sorted().toArray();
        double scale = 1.482602218505602 * median(deviations);
        return !(scale > 0) ? 0 : (int) values.stream().filter(value -> Math.abs(value - median) / scale > 4).count();
    }
    private static double median(double[] values) { int n = values.length; return n % 2 == 0 ? values[n / 2 - 1] / 2 + values[n / 2] / 2 : values[n / 2]; }
    private static Double welch(List<Double> left, List<Double> right) {
        if (left.size() < 2 || right.size() < 2) return null;
        double scale=0;for(double value:left)scale=Math.max(scale,Math.abs(value));for(double value:right)scale=Math.max(scale,Math.abs(value));
        if(!(scale>0)||!Double.isFinite(scale))return null;
        final double unit=scale;
        double a = left.stream().mapToDouble(value->value/unit).average().orElseThrow(), b = right.stream().mapToDouble(value->value/unit).average().orElseThrow();
        double x = left.stream().mapToDouble(value -> (value/unit - a) * (value/unit - a)).sum() / (left.size() - 1) / left.size();
        double y = right.stream().mapToDouble(value -> (value/unit - b) * (value/unit - b)).sum() / (right.size() - 1) / right.size();
        if (!(x + y > 0) || !Double.isFinite(x + y)) return null;
        double fraction=x/(x+y),other=y/(x+y),df=1/(fraction*fraction/(left.size()-1)+other*other/(right.size()-1));
        double p=Math.min(1,2*T.cumulative(-Math.abs(a-b)/Math.sqrt(x+y),df,true,false));
        return Double.isFinite(p)?p:null;
    }
}
