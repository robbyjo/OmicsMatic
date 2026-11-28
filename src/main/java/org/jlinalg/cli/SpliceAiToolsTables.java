/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.jlinalg.genetics.SpliceAiVcf;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Portable whole-VCF ledgers; raw source spelling, missing values and per-gene contexts remain explicit. */
final class SpliceAiToolsTables {
    private SpliceAiToolsTables(){ }
    static Map<String,Object> render(Path original,Path annotated,Path directory,Map<String,Object> request,boolean compare)throws IOException {
        var schema=new AtomicReference<SpliceAiVcf.Schema>();SpliceAiVcf.Summary summary;
        try(var output=new SpliceAiToolsFiles.Output(directory,request,compare);var variants=output.writer("variants.tsv");var annotations=output.writer("annotations.tsv");var alleles=output.writer("alleles.tsv");var genes=output.writer("genes.tsv");var changed=output.writer("source-reserialization.tsv");var schemaWriter=output.writer("schema.json")){
            SpliceAiToolsFiles.row(variants,"input_row","original_fields_json","annotated_fields_json","raw_SpliceAI_INFO","annotations","unannotated_alleles","source_reserialized_columns_json");var header=new ArrayList<Object>(List.of("input_row","annotation_index","allele_index"));header.addAll(SpliceAiVcf.FIELDS);header.addAll(List.of("maximum_observed_DS","minimum_observed_DS","complete_DS","complete_DP","state"));SpliceAiToolsFiles.row(annotations,header.toArray());
            SpliceAiToolsFiles.row(alleles,"input_row","allele_index","literal_ALT","annotations","fully_scored_annotations","missing_score_annotations","nonfinite_score_annotations","genes_json","maximum_observed_DS","minimum_observed_DS");SpliceAiToolsFiles.row(genes,"input_row","allele_index","literal_ALT","literal_SYMBOL","annotations","fully_scored_annotations","annotation_indexes_json","maximum_observed_DS","minimum_observed_DS");SpliceAiToolsFiles.row(changed,"input_row","column","original_token","source_token");
            var limits=new SpliceAiVcf.Limits(SpliceAiToolsRequest.number(request,"maximum_decoded_characters"),SpliceAiToolsRequest.number(request,"maximum_variants"),SpliceAiToolsRequest.number(request,"maximum_annotations"),(int)SpliceAiToolsRequest.number(request,"maximum_line_characters"));
            summary=SpliceAiVcf.audit(original,annotated,limits,s->{schema.set(s);schemaWriter.write(NextflowJson.json(Map.of("vcf_columns",s.columns(),"canonical_annotation_fields",SpliceAiVcf.FIELDS,"source_headers",s.spliceAiHeaders(),"source_prediction_options_inferred",false))+"\n");},v->{SpliceAiToolsFiles.row(variants,v.row(),NextflowJson.json(v.originalFields()),NextflowJson.json(v.annotatedFields()),v.rawSpliceAi(),v.annotations(),v.unannotatedAlleles(),NextflowJson.json(v.sourceReserializedColumns()));for(String column:v.sourceReserializedColumns()){int i=schema.get().columns().indexOf(column);SpliceAiToolsFiles.row(changed,v.row(),column,v.originalFields().get(i),v.annotatedFields().get(i));}},a->{var line=new ArrayList<Object>(List.of(a.row(),a.index(),a.alleleNumber()));line.addAll(a.fields());line.addAll(List.of(missing(a.maximumObservedScore()),missing(a.minimumObservedScore()),a.completeScores(),a.completePositions(),a.state()));SpliceAiToolsFiles.row(annotations,line.toArray());},a->SpliceAiToolsFiles.row(alleles,a.row(),a.alleleNumber(),a.alternate(),a.annotations(),a.fullyScoredAnnotations(),a.missingScoreAnnotations(),a.nonfiniteScoreAnnotations(),NextflowJson.json(a.genes()),missing(a.maximumObservedScore()),missing(a.minimumObservedScore())),g->SpliceAiToolsFiles.row(genes,g.row(),g.alleleNumber(),g.alternate(),g.gene(),g.annotations(),g.fullyScoredAnnotations(),NextflowJson.json(g.annotationIndexes()),missing(g.maximumObservedScore()),missing(g.minimumObservedScore())));
        }
        var report=new LinkedHashMap<String,Object>();report.put("variants",summary.variants());report.put("alternate_alleles",summary.alternateAlleles());report.put("annotations",summary.annotations());report.put("gene_groups",summary.geneGroups());report.put("unannotated_alleles",summary.unannotatedAlleles());report.put("missing_score_annotations",summary.missingScoreAnnotations());report.put("nonfinite_score_annotations",summary.nonfiniteScoreAnnotations());report.put("partial_tuples",summary.partialTuples());report.put("annotations_without_gene",summary.annotationsWithoutGene());report.put("source_reserialized_rows",summary.sourceReserializedRows());report.put("original_input_audited",summary.originalInputAudited());report.put("missing_or_nonfinite_values_replaced",false);report.put("clinical_or_author_masks_applied",false);return report;
    }
    private static String missing(String value){return value==null?"NA":value;}
}
