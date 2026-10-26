/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.enrichment.OlsResidualPermutation;
import org.jlinalg.enrichment.RankedEnrichment.Rank;
import org.jlinalg.formula.Formula;
import org.jlinalg.model.MissingDataPolicy;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.pipeline.DelimitedMatrixSource;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyNetworkPolicy;

/** Local full-family phenotype nulls; aggregate external ensembles can be reused without raw data. */
final class StudyGseaNullRanks implements java.io.Closeable {
    static final String CAVEAT="Shared model-null draws preserve dependence among the fixed measured features. Native Freedman-Lane residual permutation requires independent exchangeable homoscedastic errors within declared strata; with general nuisance covariates its calibration is approximate, even when every residual permutation is enumerated. It does not establish calibration for kinship, repeated measures, heterogeneous errors or other model families. Imported model/exchangeability/completeness claims are source declarations, not independently reproduced. NES does not establish pathway activity or causality. Adjusted P uses the registered BH/BY/NONE family, not Broad's pooled-NES empirical GSEA FDR.";
    final List<Rank> observed;
    final Path observedFile;
    final int draws;
    final boolean exhaustive;
    final String method;
    final long modelWork;
    private final IntFunction<List<Rank>> generator;
    private final java.io.Closeable resource;
    private StudyGseaNullRanks(List<Rank> observed,Path file,int draws,boolean exhaustive,String method,long work,IntFunction<List<Rank>> generator,java.io.Closeable resource){this.observed=observed;observedFile=file;this.draws=draws;this.exhaustive=exhaustive;this.method=method;modelWork=work;this.generator=generator;this.resource=resource;}
    List<Rank> draw(int index){return generator.apply(index);}
    @Override public void close() throws IOException{resource.close();}

    static StudyGseaNullRanks nativeOls(Context c,Map<String,Object> p,Map<String,Object> summary) throws Exception {
        var controls=map(p.get("phenotype"));
        if(!Boolean.TRUE.equals(controls.get("independent_samples"))||!Boolean.TRUE.equals(controls.get("exchangeable_errors")))throw new IllegalArgumentException("Native phenotype GSEA needs explicit gsea.phenotype independent_samples and exchangeable_errors declarations; cannot substitute a gene-set null");
        String id=text(c.operation().get("dataset_id"));
        var dataset=StudyBioinformatics.datasets(c).stream().filter(d->id.equals(d.get("id"))).findFirst().orElse(null);
        if(dataset==null||!StudyNetworkPolicy.MOLECULAR.contains(text(dataset.get("type"))))throw new IllegalArgumentException("Native phenotype GSEA needs one scoped bulk molecular matrix; use a complete registered gene-level null ensemble for other modalities or pooled/meta-analysis ranks");
        var files=map(dataset.get("files"));var metadata=map(dataset.get("metadata"));var h=StudyBioinformatics.hypothesis(c);var association=map(map(h.get("analysis")).get("association"));
        if(!"ols".equals(association.get("model"))||association.containsKey("family")&&!"gaussian".equals(association.get("family"))||association.containsKey("link")&&!"identity".equals(association.get("link"))||association.containsKey("offset_column")||association.containsKey("formula")||association.containsKey("survival")||files.containsKey("grm")||files.containsKey("pedigree")||Boolean.TRUE.equals(metadata.get("related_participants"))||Boolean.TRUE.equals(metadata.get("repeated_measures")))throw new IllegalArgumentException("Native phenotype GSEA currently requires the registered independent Gaussian identity OLS model without custom formulas/offsets/kinship/repeated measures; supply a complete model-matched null-ranking ensemble for other designs");
        if(!"beta_se".equals(p.get("statistic")))throw new IllegalArgumentException("Native OLS phenotype GSEA ranks the registered signed partial t (beta/SE); arbitrary statistic columns require an external complete null-ranking ensemble");
        String producer=text(c.operation().get("participant_qc_producer"));Path qc=c.dependencyDirectories().get(producer);
        if(producer.isBlank()||qc==null||!Files.isRegularFile(qc.resolve("manifest.yaml")))throw new IllegalArgumentException("Native phenotype GSEA needs its exact scoped participant_qc producer and analyzed phenotype population");
        var manifest=StudyYaml.loadArtifact(qc.resolve("manifest.yaml"));var hashes=map(manifest.get("output_sha256"));
        if(!"participant_qc".equals(manifest.get("operation_type"))||!producer.equals(manifest.get("operation_id"))||!h.get("id").equals(manifest.get("hypothesis"))||!"complete".equals(manifest.get("state"))||!id.equals(map(manifest.get("details")).get("dataset_id")))throw new IOException("Phenotype GSEA participant-QC producer identity/population is inconsistent");
        if(!hashes.containsKey("analysis-phenotype.tsv"))throw new IOException("Phenotype GSEA participant-QC population is unsealed");
        Path phenotype=StudyFindingInventory.verified(qc,"analysis-phenotype.tsv",text(hashes.get("analysis-phenotype.tsv")));
        Path matrix=StudyAssayQcSources.matrix(c,dataset);String matrixHash=FollowupSupport.hash(matrix),phenotypeHash=FollowupSupport.hash(phenotype);
        var source=DelimitedMatrixSource.open(matrix);var matrixMetadata=source.metadata();int samples=matrixMetadata.sampleIds().size();long features=matrixMetadata.rowCount();
        if(samples>integer(controls,"maximum_samples")||features>integer(controls,"maximum_features")||features*samples>integer(controls,"maximum_cells"))throw new IllegalArgumentException("Complete phenotype GSEA matrix exceeds registered storage bounds; no top-feature subset was selected");
        var columns=map(dataset.get("columns"));String outcome=text(map(columns.get("phenotypes")).getOrDefault(text(h.get("outcome")),h.get("outcome")));
        var covariates=new ArrayList<String>();for(Object raw:list(h.get("covariates")))covariates.add(text(map(columns.get("covariates")).getOrDefault(text(raw),raw)));
        String formula=outcome+" ~ "+(covariates.isEmpty()?"1":String.join(" + ",covariates));
        var pheno=PhenotypeData.read(phenotype,text(columns.getOrDefault("sample_id","id")));var prepared=pheno.prepare(matrixMetadata.sampleIds(),outcome,false,null,null,null);
        // Bound treatment coding before Formula allocates a possibly N-by-N
        // matrix for an accidentally declared participant-ID covariate.
        long estimatedColumns=1;
        for(String covariate:covariates){var values=pheno.alignedValues(prepared.ids(),covariate);boolean numeric=true;for(String value:values)try{if(!Double.isFinite(Double.parseDouble(value)))numeric=false;}catch(NumberFormatException nonnumeric){numeric=false;}estimatedColumns+=numeric?1:new HashSet<>(values).size()-1L;}
        if(estimatedColumns>integer(controls,"maximum_design_columns")||estimatedColumns*prepared.ids().size()>integer(controls,"maximum_cells"))throw new IllegalArgumentException("Complete phenotype GSEA nuisance design exceeds maximum_design_columns/maximum_cells before encoding; no covariates were dropped");
        var fixed=Formula.compile(formula,prepared.modelTable(),MissingDataPolicy.ERROR);
        int n=fixed.rows(),q=fixed.columns();if(n<integer(controls,"minimum_samples")||q>integer(controls,"maximum_design_columns"))throw new IllegalArgumentException("Phenotype GSEA has too few analyzed samples or exceeds maximum_design_columns");
        double[][] design=new double[n][q];double[] flat=fixed.design();for(int i=0;i<n;i++)System.arraycopy(flat,i*q,design[i],0,q);
        List<String> strata=null;if(controls.containsKey("strata_column")){String logical=text(controls.get("strata_column")),column=text(map(columns.get("covariates")).getOrDefault(logical,logical));if(!covariates.contains(column))throw new IllegalArgumentException("Declare the phenotype permutation stratum among the registered nuisance covariates");strata=pheno.alignedValues(prepared.ids(),column);}
        int states=drawCount(n,strata,integer(p,"permutations"),integer(p,"maximum_exact_states"));
        long modelWork=(states+1L)*n*(4L*q*q+3*features)+8L*n*features*q*q;
        if(modelWork>=integer(p,"maximum_work"))throw new IllegalArgumentException("Complete phenotype model refitting exceeds maximum_work; no genes/draws were truncated");
        Map<String,Integer> samplePositions=new LinkedHashMap<>();for(int i=0;i<samples;i++)samplePositions.put(matrixMetadata.sampleIds().get(i),i);int[] order=prepared.ids().stream().mapToInt(samplePositions::get).toArray();
        var featureNames=new ArrayList<String>();var featureVectors=new ArrayList<double[]>();var qcRows=new ArrayList<String[]>();Set<String> unique=new HashSet<>();
        try(var reader=source.open(order)){org.jlinalg.pipeline.NumericBlock block;while((block=reader.read(256))!=null)for(var row:block.rows()){
            if(!unique.add(row.id()))throw new IOException("Duplicate phenotype GSEA feature ID");double[] x=row.values().clone();double maximum=0;int called=0;for(double v:x)if(Double.isFinite(v)){maximum=Math.max(maximum,Math.abs(v));called++;}
            if(called==0){qcRows.add(new String[]{row.id(),"no_observed_matrix_values","0"});continue;}
            double mean=0;for(double v:x)if(Double.isFinite(v))mean+=maximum==0?0:(v/maximum)/called;
            for(int i=0;i<x.length;i++)x[i]=Double.isFinite(x[i])?(maximum==0?0:x[i]/maximum):mean;
            double[] residual=Ols.fit(x,design,OlsOptions.defaults(),BackendPolicy.CPU).residuals();double ss=Arrays.stream(residual).map(v->v*v).sum();
            if(!(ss>Math.max(Double.MIN_NORMAL,1e-24*n))){qcRows.add(new String[]{row.id(),"constant_or_nuisance_explained",String.valueOf(n-called)});continue;}
            featureNames.add(row.id());featureVectors.add(x);qcRows.add(new String[]{row.id(),"eligible_before_outcome_ranking",String.valueOf(n-called)});
        }}
        FollowupSupport.table(c.outputDirectory().resolve("phenotype-feature-qc.tsv"),List.of("feature_id","disposition","mean_imputed_values"),qcRows);
        FollowupSupport.table(c.outputDirectory().resolve("phenotype-feature-universe.tsv"),List.of("feature_id"),featureNames.stream().map(f->new String[]{f}).toList());
        StudyFeatureAnnotations.write(c,Map.of(id,c.outputDirectory().resolve("phenotype-feature-universe.tsv")));var mappings=new StudyCliExecutor().geneMappings(c,new HashSet<>(featureNames));
        var geneFeatures=new LinkedHashMap<String,List<Integer>>();var mappingRows=new ArrayList<String[]>();
        for(int j=0;j<featureNames.size();j++){var mapping=mappings.get(featureNames.get(j));String gene=mapping==null?"":"symbol".equals(p.get("gene_id_type"))?mapping.symbol():"ensembl_gene".equals(p.get("gene_id_type"))?mapping.ensembl():"";mappingRows.add(new String[]{featureNames.get(j),gene,gene.isBlank()?"unmapped_gene_namespace":"mapped"});if(!gene.isBlank())geneFeatures.computeIfAbsent(gene,k->new ArrayList<>()).add(j);}
        FollowupSupport.table(c.outputDirectory().resolve("feature-rank-mapping.tsv"),List.of("feature_id","gene_id","status"),mappingRows);
        if(geneFeatures.size()<2||geneFeatures.size()>integer(p,"max_genes"))throw new IllegalArgumentException("Complete uniquely mapped phenotype GSEA gene universe is outside registered bounds");
        if("require_unique".equals(p.get("collapse"))&&geneFeatures.values().stream().anyMatch(v->v.size()>1))throw new IllegalArgumentException("Multiple measured features map to a gene; register mean/median collapse identically for observed and all null draws");
        var fit=new OlsResidualPermutation(fixed.response(),design,featureVectors.toArray(double[][]::new),strata,integer(p,"permutations"),integer(p,"maximum_exact_states"),integer(p,"seed"));
        var observed=collapse(fit.observedStatistics(),geneFeatures,text(p.get("collapse")));Path observedFile=c.outputDirectory().resolve("native-observed-ranking.tsv");
        FollowupSupport.table(observedFile,List.of("gene_id","statistic"),observed.stream().map(r->new String[]{r.gene(),String.valueOf(r.statistic())}).toList());
        FollowupSupport.table(c.outputDirectory().resolve("analysis-samples.tsv"),List.of("sample_id"),prepared.ids().stream().map(s->new String[]{s}).toList());
        if(!matrixHash.equals(FollowupSupport.hash(matrix))||!phenotypeHash.equals(FollowupSupport.hash(phenotype)))throw new IOException("Phenotype GSEA inputs changed during preparation");
        summary.put("ranking_origin","native_registered_ols_partial_t_all_estimable_features");summary.put("source_FDR_filter_applied",false);summary.put("native_model_formula",formula);summary.put("native_covariate_design",fixed.coefficientNames());summary.put("analysis_samples",n);summary.put("residual_df",fit.residualDegreesOfFreedom());summary.put("source_matrix_sha256",matrixHash);summary.put("analyzed_phenotype_sha256",phenotypeHash);summary.put("participant_qc_producer",producer);summary.put("species",dataset.getOrDefault("species","unknown"));summary.put("input_features",features);summary.put("eligible_features",featureNames.size());summary.put("model_work_units",modelWork);
        summary.put("missingness","Registered QC phenotype population; molecular predictors mean-imputed within those aligned samples before nuisance residualization. Feature eligibility is outcome-independent residual variation > max(Double.MIN_NORMAL, N * 1e-24) after max-absolute scaling; every feature disposition is retained.");
        summary.put("resampling_procedure","Freedman-Lane: reduced OLS outcome residuals permuted jointly for all features within strata, nuisance fitted values added back, same full-model signed partial t recomputed. Categorical encoding and complete-case model use the association Formula/PhenotypeData compiler. Identity included in exhaustive census; Monte Carlo samples with replacement using java.util.Random and within-stratum Fisher-Yates.");
        summary.put("null_rankings_source","native_refit");summary.put("null_sampling",fit.exhaustive()?"exhaustive_residual_permutations":"monte_carlo_residual_permutations");summary.put("null_draws",fit.drawCount());
        BufferedWriter writer=Files.newBufferedWriter(c.outputDirectory().resolve("null-rankings.tsv"));writer.write("draw_id\tgene_id\tstatistic\n");
        int[] next={0};return new StudyGseaNullRanks(observed,observedFile,fit.drawCount(),fit.exhaustive(),fit.exhaustive()?"enumerated_freedman_lane_residual":"monte_carlo_freedman_lane_residual",modelWork,index->{if(index!=next[0]++)throw new IllegalArgumentException("Null rankings must be consumed sequentially");var ranks=collapse(fit.nextStatistics(),geneFeatures,text(p.get("collapse")));try{for(var r:ranks)writer.write((index+1)+"\t"+r.gene()+"\t"+r.statistic()+"\n");}catch(IOException failure){throw new UncheckedIOException(failure);}return ranks;},writer);
    }

    static StudyGseaNullRanks supplied(Context c,Map<String,Object> p,List<Rank> observed,Path observedFile,Map<String,Object> summary) throws IOException {
        var contract=map(p.get("null_rankings"));Path source=c.project().resolve(text(contract.get("file")));
        if(!Files.isRegularFile(source))throw new IllegalArgumentException("Supply the complete local gsea.null_rankings.file; independent branches can continue");
        if(!FollowupSupport.hash(observedFile).equalsIgnoreCase(text(contract.get("observed_sha256"))))throw new IllegalArgumentException("Null-ranking observed_sha256 does not match the exact observed ranking file");
        long minimumWork=(long)observed.size()*(4+32-Integer.numberOfLeadingZeros(observed.size()))*(integer(contract,"draws")+1L);
        if(minimumWork>integer(p,"maximum_work"))throw new IllegalArgumentException("Complete model-null ranking work exceeds maximum_work before snapshotting; no genes/draws were truncated");
        Path copy=c.outputDirectory().resolve("supplied-null-rankings.tsv");String hash=FollowupSupport.hash(source);Files.copy(source,copy);if(!hash.equals(FollowupSupport.hash(copy))||!hash.equals(FollowupSupport.hash(source)))throw new IOException("Supplied null rankings changed while snapshotting");
        BufferedReader reader=Files.newBufferedReader(copy);String header=reader.readLine();if(!"draw_id\tgene_id\tstatistic".equals(header)){reader.close();throw new IllegalArgumentException("Null rankings need the exact TSV header draw_id, gene_id, statistic");}
        int draws=integer(contract,"draws");int[] next={0};String[] pending={null};Set<String> drawIds=new HashSet<>();
        summary.put("null_rankings_source","supplied_complete_model_matched_ensemble_not_refitted");summary.put("null_rankings_sha256",hash);summary.put("null_ensemble_contract",contract);summary.put("null_draws",draws);summary.put("null_sampling",contract.get("sampling"));
        return new StudyGseaNullRanks(observed,observedFile,draws,"exhaustive".equals(contract.get("sampling")),"supplied_"+contract.get("sampling")+"_model_null",0,index->{
            if(index!=next[0]++)throw new IllegalArgumentException("Null rankings must be consumed sequentially");var ranks=new ArrayList<Rank>();String drawId="";
            try{
                while(true){String line=pending[0];pending[0]=null;if(line==null)line=reader.readLine();if(line==null)break;
                    String[] fields=line.split("\t",-1);if(fields.length!=3||fields[0].isBlank())throw new IllegalArgumentException("Malformed complete null-ranking TSV row");
                    if(drawId.isEmpty()){drawId=fields[0];if(!drawIds.add(drawId))throw new IllegalArgumentException("Null draw IDs must be unique contiguous blocks");}
                    if(!drawId.equals(fields[0])){pending[0]=line;break;}
                    if(ranks.size()>=observed.size())throw new IllegalArgumentException("Null draw exceeds the complete observed gene universe");
                    ranks.add(new Rank(fields[1],Double.parseDouble(fields[2])));
                }
                if(ranks.size()!=observed.size())throw new IllegalArgumentException("Missing/incomplete declared null draw "+(index+1));
                if(index==draws-1&&(pending[0]!=null||reader.readLine()!=null))throw new IllegalArgumentException("Null ensemble contains more draws than declared; no favorable prefix was selected");
            }catch(IOException failure){throw new UncheckedIOException(failure);}return ranks;
        },reader);
    }
    private static int drawCount(int samples,List<String> strata,int requested,int cap){var counts=new LinkedHashMap<String,Integer>();for(int i=0;i<samples;i++)counts.merge(strata==null?"all":strata.get(i),1,Integer::sum);long states=1;for(int count:counts.values())for(int j=2;j<=count;j++){if(states>cap/(long)j)return requested;states*=j;}return cap>0&&states<=cap?(int)states:requested;}
    private static List<Rank> collapse(double[] statistics,Map<String,List<Integer>> mapping,String method){var ranks=new ArrayList<Rank>();for(var entry:mapping.entrySet()){double[] values=entry.getValue().stream().mapToDouble(i->statistics[i]).toArray();double score=0;if(method.equals("median")){Arrays.sort(values);int at=values.length/2;score=values.length%2==0?values[at-1]/2+values[at]/2:values[at];}else for(double value:values)score+=value/values.length;ranks.add(new Rank(entry.getKey(),score));}return ranks;}
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
}
