/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.VepVcf;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Streaming full CSQ/variant/gene ledgers; saved verification recomputes every emitted byte. */
final class VepToolsTables {
    private VepToolsTables() { }
    static final Set<String> FILES=Set.of("variants.tsv","annotations.tsv","genes.tsv","schema.json","summary.json");
    static Map<String,Object> render(Path original,Path annotated,Path output,Map<String,Object> request,boolean sourceSummary,boolean verify)throws IOException {
        if(!verify)Files.createDirectory(output);else{try(var paths=Files.list(output)){if(!paths.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(FILES))throw new IOException("VEP result family changed");}}
        long[] emitted={0};long maximum=VepToolsRequest.number(request,"maximum_output_bytes");Map<String,Object> report=new LinkedHashMap<>();
        try(var variants=writer(output.resolve("variants.tsv"),verify,emitted,maximum);var annotations=writer(output.resolve("annotations.tsv"),verify,emitted,maximum);var genes=writer(output.resolve("genes.tsv"),verify,emitted,maximum)){
            FollowupSupport.row(genes,new String[]{"input_row","allele_number","ALT","Gene","annotation_count","distinct_features_json","distinct_consequences_json","observed_impacts_json","maximum_observed_impact","missing_impact_annotations"});
            final List<String>[] fields=fieldHolder();var limits=new VepVcf.Limits(VepToolsRequest.number(request,"maximum_decoded_characters"),VepToolsRequest.number(request,"maximum_variants"),VepToolsRequest.number(request,"maximum_annotations"),(int)VepToolsRequest.number(request,"maximum_line_characters"));
            VepVcf.Sink<VepVcf.Schema> schema=s->{
                fields[0]=s.csqFields();var a=new ArrayList<String>(List.of("input_row","annotation_index","allele_number","assigned_ALT","raw_CSQ"));for(String name:s.csqFields())a.add("CSQ."+name);FollowupSupport.row(annotations,a.toArray(String[]::new));
                var v=new ArrayList<String>(List.of("input_row","annotation_count","annotations_per_allele_json","maximum_observed_impacts_per_allele_json","missing_impact_annotations_per_allele_json","unassigned_summary_annotations","non_CSQ_INFO_order_changed"));for(String name:s.vcfColumns())v.add("VCF."+name);FollowupSupport.row(variants,v.toArray(String[]::new));
                var shape=new LinkedHashMap<String,Object>();shape.put("vcf_columns",s.vcfColumns());shape.put("csq_fields",s.csqFields());shape.put("original_vep_headers",s.vepHeaders());shape.put("raw_values_decoded_or_repaired",false);shape.put("source_summary_mode",sourceSummary);write(output.resolve("schema.json"),RsemJson.bytes(shape),verify,emitted,maximum);
            };
            VepVcf.Sink<VepVcf.Variant> variant=v->{var row=new ArrayList<String>(List.of(Long.toString(v.row()),Integer.toString(v.annotations()),NextflowJson.json(v.annotationsPerAllele()),NextflowJson.json(v.maximumObservedImpactsPerAllele()),NextflowJson.json(v.missingImpactAnnotationsPerAllele()),Integer.toString(v.unassignedSummaryAnnotations()),Boolean.toString(v.nonCsqInfoOrderChanged())));row.addAll(v.fields());FollowupSupport.row(variants,row.toArray(String[]::new));};
            VepVcf.Sink<VepVcf.Annotation> annotation=a->{var row=new ArrayList<String>(List.of(Long.toString(a.row()),Integer.toString(a.index()),a.alleleNumber()==0?"":Integer.toString(a.alleleNumber()),a.alternate(),a.rawCsq()));for(String field:fields[0])row.add(a.fields().get(field));FollowupSupport.row(annotations,row.toArray(String[]::new));};
            VepVcf.Sink<VepVcf.Gene> gene=g->FollowupSupport.row(genes,new String[]{Long.toString(g.row()),Integer.toString(g.alleleNumber()),g.alternate(),g.gene(),Integer.toString(g.annotations()),NextflowJson.json(g.features()),NextflowJson.json(g.consequences()),NextflowJson.json(g.originalImpacts()),g.maximumOriginalImpact(),Integer.toString(g.missingImpactAnnotations())});
            var policy=sourceSummary?VepVcf.Policy.SOURCE_CONSEQUENCE_SUMMARY:VepVcf.Policy.EXPLICIT_ALLELE_NUMBERS;
            var summary=original==null?VepVcf.inspect(annotated,limits,policy,schema,variant,annotation,gene):VepVcf.audit(original,annotated,limits,policy,schema,variant,annotation,gene);
            report.put("schema_version",1);report.put("engine","native-complete-literal-VEP-CSQ-ledgers");report.put("variants",summary.variants());report.put("annotations",summary.annotations());report.put("gene_groups",summary.geneGroups());report.put("unannotated_alleles",summary.unannotatedAlleles());report.put("annotations_without_gene",summary.annotationsWithoutGene());report.put("missing_impact_annotations",summary.missingImpacts());report.put("unassigned_source_summary_annotations",summary.unassignedSummaryAnnotations());report.put("non_CSQ_INFO_order_changes",summary.nonCsqInfoOrderChanges());report.put("original_input_identity_audited",summary.originalInputAudited());report.put("source_summary_mode",sourceSummary);report.put("transcripts_selected_or_merged",false);report.put("consequences_predicted",false);report.put("unknown_or_missing_values_repaired",false);report.put("absence_is_benign_or_no_effect",false);report.put("whole_paper_reproduced",false);
        }
        write(output.resolve("summary.json"),RsemJson.bytes(report),verify,emitted,maximum);return report;
    }
    @SuppressWarnings("unchecked") private static List<String>[] fieldHolder(){return(List<String>[])new List<?>[1];}
    private static Writer writer(Path path,boolean compare,long[] count,long maximum)throws IOException{return new BufferedWriter(new OutputStreamWriter(stream(path,compare,count,maximum),StandardCharsets.UTF_8),65536);}
    private static void write(Path path,byte[] value,boolean compare,long[] count,long maximum)throws IOException{try(var output=stream(path,compare,count,maximum)){output.write(value);}}
    private static OutputStream stream(Path path,boolean compare,long[] count,long maximum)throws IOException {
        if(compare&&(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximum))throw new IOException("Missing/nonregular/oversized saved VEP result: "+path.getFileName());
        InputStream input=compare?new BufferedInputStream(Files.newInputStream(path),65536):null;OutputStream output=compare?null:Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
        return new OutputStream(){
            private void capacity(int n)throws IOException{if(n>maximum-count[0])throw new IOException("Complete native VEP result capacity exceeded; no truncation");count[0]+=n;}
            @Override public void write(int b)throws IOException{capacity(1);if(compare){if(input.read()!=(b&255))throw new IOException("Saved VEP semantic recomputation differs: "+path.getFileName());}else output.write(b);}
            @Override public void write(byte[] bytes,int offset,int length)throws IOException{capacity(length);if(compare){byte[] other=input.readNBytes(length);if(other.length!=length)throw new IOException("Saved VEP result was truncated: "+path.getFileName());for(int i=0;i<length;i++)if(other[i]!=bytes[offset+i])throw new IOException("Saved VEP semantic recomputation differs: "+path.getFileName());}else output.write(bytes,offset,length);}
            @Override public void close()throws IOException{if(compare){try{if(input.read()!=-1)throw new IOException("Saved VEP result has extra bytes: "+path.getFileName());}finally{input.close();}}else output.close();}
        };
    }
}
