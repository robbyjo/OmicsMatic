/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.CellChatLegacyRequest.*;
import static org.jlinalg.cli.CellChatLegacyTables.*;
import static org.jlinalg.cli.CellChatNetworkRequest.Request;

/** Matrix/subset/orientation and original cluster-summary transport checks. */
final class CellChatNetworkAudit {
    private CellChatNetworkAudit() { }
    private record Matrix(List<String> names,double[][] values) { }
    static void preflight(Path parent,Request request)throws IOException {
        for(String type:names(request.analysis().get("types"),1,2))matrix(parent.resolve("results/comparison/"+type+"-similarity.tsv"),request.limits().features());
    }
    static Map<String,Object> verify(Path worker,Path parent,Request request)throws IOException {
        Map<?,?> report=object(json(worker.resolve("worker-report.json"),16777216));require(text(report.get("status")).equals("COMPLETED_ORIGINAL_CELLCHAT_NETWORK_WORKER_ONLY")&&Boolean.FALSE.equals(report.get("analysis_admitted")),"Original network worker did not complete its non-admitting protocol");
        List<String> types=names(request.analysis().get("types"),1,2);Map<?,?> reports=object(report.get("types"));require(reports.keySet().equals(new HashSet<>(types)),"Original network result population differs");var summaries=new ArrayList<Map<String,Object>>();
        Map<?,?> embedding=object(request.analysis().get("embedding")),clustering=object(request.analysis().get("clustering"));
        for(String type:types) {
            Path home=worker.resolve(type);Matrix original=matrix(parent.resolve("results/comparison/"+type+"-similarity.tsv"),request.limits().features());Matrix before=matrix(home.resolve("similarity-before.tsv"),request.limits().features());require(original.names.equals(before.names),"Original network feature order changed");
            for(int i=0;i<original.names.size();i++)for(int j=0;j<original.names.size();j++)require(original.values[i][j]==before.values[i][j],"Original similarity value changed before embedding");
            var dispositions=read(home.resolve("feature-dispositions.tsv"),request.limits().features());require(dispositions.size()==original.names.size(),"Incomplete feature dispositions");List<String> removed=embedding.get("pathway_remove")==null?null:names(embedding.get("pathway_remove"),0,request.limits().features());if(removed!=null)require(original.names.containsAll(removed),"Explicit removal names are absent from the original matrix");
            var included=new ArrayList<String>();var indices=new ArrayList<Integer>();
            for(int i=0;i<original.names.size();i++) {
                var row=dispositions.get(i);String feature=original.names.get(i);require(feature.equals(row.get("feature")),"Feature disposition order differs");double sum=0;for(double[] values:original.values)sum+=values[i];double sourceSum=finite(row.get("column_sum"));close(sourceSum,sum);
                // Retain the original R colSums equality boundary, not a rounded
                // or generic near-one rule. Numerical agreement is checked above.
                boolean keep=removed==null?sourceSum!=1:!removed.contains(feature);require(row.get("included").equals(keep?"TRUE":"FALSE")&&row.get("reason").equals(keep?"included":removed==null?"original-column-sum-equals-one":"explicit-pathway-removal"),"Source subset decision differs");
                if(keep){included.add(feature);indices.add(i);}
            }
            require(included.size()>=4,"Insufficient original network features for this validated 2D embedding profile");var input=read(home.resolve("umap-input.tsv"),request.limits().features());require(input.size()==included.size(),"Incomplete UMAP input rows");
            for(int i=0;i<input.size();i++) {
                var row=input.get(i);require(included.get(i).equals(row.get("feature"))&&row.size()==included.size()+1,"UMAP row/column population differs");
                for(int j=0;j<included.size();j++)require(finite(row.get(included.get(j)))==original.values[indices.get(j)][indices.get(i)],"Original transposed UMAP input differs");
            }
            var points=read(home.resolve("points.tsv"),request.limits().features());require(points.size()==included.size(),"Incomplete original embedding");var byCluster=new TreeMap<Integer,List<double[]>>();
            for(int i=0;i<points.size();i++) {
                var row=points.get(i);require(included.get(i).equals(row.get("feature"))&&row.keySet().equals(java.util.Set.of("feature","UMAP1","UMAP2","cluster")),"Embedding feature/coordinate fields differ");int group=integerToken(row.get("cluster"),1,points.size());double[] point={finite(row.get("UMAP1")),finite(row.get("UMAP2"))};byCluster.computeIfAbsent(group,k->new ArrayList<>()).add(point);
            }
            int expectedLabel=1;for(int label:byCluster.keySet())require(label==expectedLabel++,"Original kmeans labels are not contiguous positive integers");if(clustering.get("k")!=null)require(byCluster.size()==integer(clustering.get("k"),1,10000),"Explicit original kmeans group count differs");
            var centers=read(home.resolve("cluster-summary.tsv"),request.limits().features());require(centers.size()==byCluster.size(),"Incomplete cluster summaries");int at=0;double totalSS=0;
            for(var entry:byCluster.entrySet()) {
                var row=centers.get(at++);require(integerToken(row.get("cluster"),1,points.size())==entry.getKey()&&integerToken(row.get("size"),1,points.size())==entry.getValue().size(),"Original cluster population differs");double x=0,y=0;for(double[] p:entry.getValue()){x+=p[0];y+=p[1];}x/=entry.getValue().size();y/=entry.getValue().size();close(finite(row.get("mean_1")),x);close(finite(row.get("mean_2")),y);double ss=0;for(double[] p:entry.getValue())ss+=(p[0]-x)*(p[0]-x)+(p[1]-y)*(p[1]-y);close(finite(row.get("within_ss")),ss);totalSS+=ss;
            }
            Map<?,?> one=object(reports.get(type));require(integer(one.get("points"),4,request.limits().features())==points.size()&&integer(one.get("clusters"),1,points.size())==byCluster.size()&&text(one.get("comparison_name")).equals("1-2")&&Boolean.TRUE.equals(one.get("input_transposed"))&&text(one.get("metric")).equals("correlation"),"Original embedding report differs");long neighbors=embedding.get("n_neighbors")==null?(long)Math.ceil(Math.sqrt(included.size()))+1:integer(embedding.get("n_neighbors"),2,10000);require(integer(one.get("neighbors_argument"),2,10000)==neighbors,"Original neighbor argument differs");
            boolean plot=bool(clustering.get("plot_embedding"));require(java.nio.file.Files.isRegularFile(home.resolve("embedding.pdf"))==plot&&java.nio.file.Files.isRegularFile(home.resolve("original-plot.rds"))==plot,"Original embedding plot disposition differs");summaries.add(Map.of("type",type,"input_features",original.names.size(),"included_features",included.size(),"clusters",byCluster.size(),"within_ss",totalSS));
        }
        long rng=0;for(Object warning:list(report.get("warnings")))if(text(warning).contains("UNRELIABLE VALUE"))rng++;require(integer(report.get("original_parallel_rng_warnings"),0,1000000)==rng,"Original RNG warning census differs");
        Map<?,?> guards=object(json(worker.resolve("guarded-original-functions.json"),2097152));require(guards.keySet().equals(java.util.Set.of("netEmbedding","runUMAP","netClustering","computeEigengap","netVisual_embeddingPairwise")),"Original function guard population differs");
        Map<?,?> parentAudit=object(json(parent.resolve("provenance.json"),16777216));var parentDiagnostics=new LinkedHashMap<String,Object>();
        for(String field:List.of("original_centrality_fallbacks","original_nonfinite_centrality_fields","original_out_of_range_p_fields","original_duplicate_neighbor_entries","original_similarity_outside_unit_interval","original_omitted_strictly_closer_neighbors","original_ranknet_zero_fallbacks"))if(parentAudit.containsKey(field)){long value=integer(parentAudit.get(field),0,Long.MAX_VALUE);if(value>0)parentDiagnostics.put(field,value);}
        var audit=new LinkedHashMap<String,Object>();audit.put("types",summaries);audit.put("original_parallel_rng_warnings",rng);audit.put("original_warning_count",list(report.get("warnings")).size());audit.put("native_UMAP_and_kmeans_independently_refitted",false);audit.put("source_similarity_rescaled",false);audit.put("original_umap_constructor_random_state_supplied",false);audit.put("exact_whole_pipeline_replay_claimed",false);audit.put("parent_original_source_diagnostics",parentDiagnostics);return audit;
    }
    private static Matrix matrix(Path file,int maximum)throws IOException {
        var rows=read(file,(int)Math.min(10000000L,(long)maximum*maximum));var names=new ArrayList<String>();for(var row:rows)if(!names.contains(row.get("row")))names.add(row.get("row"));require(names.size()>=4&&names.size()<=maximum&&rows.size()==(long)names.size()*names.size(),"Incomplete or over-capacity similarity matrix");double[][] values=new double[names.size()][names.size()];int at=0;
        for(int i=0;i<names.size();i++)for(int j=0;j<names.size();j++){var row=rows.get(at++);require(names.get(i).equals(row.get("row"))&&names.get(j).equals(row.get("column")),"Source similarity axes/order differ");values[i][j]=finite(row.get("value"));}return new Matrix(List.copyOf(names),values);
    }
    private static int integerToken(String raw,int min,int max)throws IOException {double value=finite(raw);require(value>=min&&value<=max&&value==Math.rint(value),"Invalid original cluster count");return(int)value;}
    private static void close(double actual,double expected)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),"Native network summary differs");}
}
