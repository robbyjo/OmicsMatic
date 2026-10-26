/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jlinalg.pipeline.DelimitedMatrixSource;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyRiskScorePolicy;

/** Derive participant-row score inputs directly from scoped cohort matrices, never association hits. */
final class StudyRiskScoreInputs {
    private StudyRiskScoreInputs() { }
    static Path prepare(Context c,Map<String,Object> p)throws IOException {
        if(p.containsKey("file"))return c.project().resolve(text(p.get("file")));
        var selected=new LinkedHashSet<>(list(c.operation().get("datasets")).stream().map(StudyPublicEvidence::text).toList());
        var datasets=StudyBioinformatics.datasets(c).stream().filter(d->selected.contains(text(d.get("id")))&&org.jlinalg.study.StudyNetworkPolicy.MOLECULAR.contains(text(d.get("type")))&&map(d.get("files")).containsKey("phenotype")&&map(d.get("files")).containsKey("matrix")).toList();
        if(datasets.isEmpty())throw new IllegalArgumentException("Register risk_score.file or supported participant-level molecular matrices with phenotype tables and explicit partition columns");
        List<String> features=StudyRiskScorePolicy.list(p,"features");if(!"scores".equals(p.get("mode"))&&features.isEmpty())throw new IllegalArgumentException("Matrix scoring requires the same independently prespecified risk_score.features across source cohorts");
        if(features.size()>((Number)p.get("max_features")).intValue())throw new IllegalArgumentException("Matrix score feature universe exceeds max_features");
        var columns=new LinkedHashSet<String>();columns.add(text(p.get("id_column")));columns.add(text(p.get("partition_column")));columns.add(text(p.get("outcome")));
        for(String key:List.of("covariates","group_columns","subgroups"))columns.addAll(StudyRiskScorePolicy.list(p,key));for(Object raw:list(p.get("related_phenotypes")))columns.add(text(map(raw).get("column")));for(String key:List.of("score_column","prediction_column","baseline_column"))if(p.containsKey(key))columns.add(text(p.get(key)));
        if(features.stream().anyMatch(columns::contains))throw new IllegalArgumentException("Score matrix features conflict with metadata/outcome columns");columns.addAll(features);var header=new ArrayList<>(columns);var output=new ArrayList<String[]>();var receipts=new ArrayList<Object>();var exclusions=new ArrayList<String[]>();
        for(var dataset:datasets){String id=text(dataset.get("id"));var files=map(dataset.get("files"));var mapping=map(dataset.get("columns"));Path matrix=StudyAssayQcSources.matrix(c,dataset),phenotype=c.project().resolve(text(files.get("phenotype")));if(!Files.isRegularFile(matrix)||!Files.isRegularFile(phenotype))throw new IllegalArgumentException("Supply the registered score matrix and phenotype table: "+id);
            if(Files.size(phenotype)>134217728L)throw new IllegalArgumentException("Phenotype table exceeds dense score-input bound");String matrixHash=FollowupSupport.hash(matrix),phenotypeHash=FollowupSupport.hash(phenotype);Path snapshots=c.outputDirectory().resolve("cohort-inputs");Files.createDirectories(snapshots);Path saved=snapshots.resolve(id+"-phenotype"+(phenotype.toString().endsWith(".csv")?".csv":".tsv"));Files.copy(phenotype,saved);var pheno=DelimitedData.read(saved);int sample=pheno.column(text(mapping.getOrDefault("sample_id","id")));var bySample=new LinkedHashMap<String,String[]>();for(String[] row:pheno.rows())if(row[sample].isBlank()||bySample.putIfAbsent(row[sample],row)!=null)throw new IllegalArgumentException("Score phenotypes need unique nonblank IDs per cohort");
            var source=DelimitedMatrixSource.open(matrix);var metadata=source.metadata();int n=metadata.sampleIds().size();if((long)(output.size()+n)*Math.max(1,features.size())>20000000L)throw new IllegalArgumentException("Combined cohort score design exceeds dense storage bounds");if((long)n*Math.max(1,features.size())>20000000L||metadata.rowCount()*n>((Number)p.get("max_work")).longValue())throw new IllegalArgumentException("Matrix score input exceeds registered streaming/dense work bounds");
            var values=new LinkedHashMap<String,double[]>();if(!features.isEmpty())try(var reader=source.open((int[])null)){org.jlinalg.pipeline.NumericBlock block;while((block=reader.read(256))!=null)for(var row:block.rows())if(features.contains(row.id())&&values.putIfAbsent(row.id(),row.values())!=null)throw new IllegalArgumentException("Duplicate score feature in cohort matrix: "+row.id());}if(!values.keySet().containsAll(features))throw new IllegalArgumentException("A cohort lacks registered score predictors; no missing-feature substitution or favorable intersection: "+id);
            for(int i=0;i<n;i++){String sid=metadata.sampleIds().get(i);String[] row=bySample.remove(sid);if(row==null){exclusions.add(new String[]{id,sid,"matrix_sample_without_phenotype_record"});continue;}String[] merged=new String[header.size()];for(int j=0;j<header.size();j++){String key=header.get(j);if(key.equals(text(p.get("id_column"))))merged[j]=sid;else if(key.equals("dataset_id"))merged[j]=id;else if(key.equals("cohort_id"))merged[j]=text(dataset.get("cohort"));else if(features.contains(key))merged[j]=String.valueOf(values.get(key)[i]);else{String physical=text(map(mapping.get("phenotypes")).getOrDefault(key,map(mapping.get("covariates")).getOrDefault(key,key)));merged[j]=row[pheno.column(physical)];}}output.add(merged);if(output.size()>((Number)p.get("max_samples")).intValue())throw new IllegalArgumentException("Combined score population exceeds max_samples");}
            for(String sid:bySample.keySet())exclusions.add(new String[]{id,sid,"phenotype_record_without_matrix_sample"});if(!matrixHash.equals(FollowupSupport.hash(matrix))||!phenotypeHash.equals(FollowupSupport.hash(phenotype))||!phenotypeHash.equals(FollowupSupport.hash(saved)))throw new IOException("Cohort score source changed while deriving participant input");
            receipts.add(Map.of("dataset_id",id,"cohort",dataset.getOrDefault("cohort","not_declared"),"matrix_sha256",matrixHash,"phenotype_sha256",phenotypeHash,"matrix_samples",n,"selected_predictors",features,"selection_rule","prespecified feature IDs; no association P or FDR selection"));
        }
        FollowupSupport.table(c.outputDirectory().resolve("input-alignment-exclusions.tsv"),List.of("dataset_id","sample_id","reason"),exclusions);write(c.outputDirectory().resolve("cohort-score-inputs.yaml"),Map.of("sources",receipts,"participants",output.size(),"mapping","Dataset sample_id, phenotypes and covariates mappings; source cohort metadata never implies independent validation"));Path file=c.outputDirectory().resolve("prepared-participant-input.tsv");FollowupSupport.table(file,header,output);return file;
    }
}
