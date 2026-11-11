/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.CellChatLegacyRequest.*;
import static org.jlinalg.cli.CellChatLegacyTables.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete axis/filter/network checks; original R fitting is not reimplemented. */
final class CellChatLegacyAudit {
    private CellChatLegacyAudit() { }
    private record Key(String feature,String source,String target) { }
    private record Node(String source,String target) { }
    private record Centrality(String slot,String feature,String group,String metric) { }
    static Map<String,Object> verify(Path worker,Request request,Population population)throws IOException {
        Map<?,?> report=object(json(worker.resolve("worker-report.json"),16777216));
        require(text(report.get("status")).equals("COMPLETED_ORIGINAL_CELLCHAT_LEGACY_WORKER_ONLY")&&Boolean.FALSE.equals(report.get("analysis_admitted")),"Original worker did not complete its non-admitting protocol");
        Map<?,?> cases=object(report.get("cases"));List<String> conditionOrder=names(request.analysis().get("condition_order"),2,2),groupOrder=names(request.analysis().get("group_order"),2,10000);
        var expected=new ArrayList<String>();expected.addAll(List.of("condition-1","condition-2"));
        require(cases.keySet().equals(new HashSet<>(expected)),"Original fit population differs");
        double threshold=number(object(request.analysis().get("network")).get("pvalue_threshold"),0,1,true);
        int nboot=(int)integer(object(request.analysis().get("inference")).get("nboot"),2,10000);
        var audits=new ArrayList<Map<String,Object>>();var pathwayByCase=new LinkedHashMap<String,Map<Key,Double>>();var pathwayOrder=new LinkedHashMap<String,List<String>>();var groupByCase=new LinkedHashMap<String,List<String>>();long projectionCells=0;var byCase=new LinkedHashMap<String,Map<Key,Double>>();long fallbackCount=0,nonfiniteCentrality=0,probabilityCells=0;
        for(String id:expected) {
            Path home=worker.resolve("cases").resolve(id);Map<?,?> c=object(cases.get(id));
            require(c.equals(object(json(home.resolve("case-report.json"),1048576))),"Case report differs from worker summary");
            boolean condition=id.startsWith("condition-");int index=Integer.parseInt(id.substring(id.indexOf('-')+1))-1;
            String value=conditionOrder.get(index);
            require(text(c.get("kind")).equals(condition?"condition":"sample")&&text(c.get("value")).equals(value)&&Boolean.TRUE.equals(c.get("worker_case_complete")),"Case identity/completion differs");
            var selectedCells=new ArrayList<String>();for(String cell:population.cells())if(population.metadata().get(cell)[2].equals(value))selectedCells.add(cell);
            List<Map<String,String>> dispositions=read(home.resolve("cell-dispositions.tsv"),request.limits().cells());require(dispositions.size()==selectedCells.size(),"Incomplete cell dispositions");
            var counts=new LinkedHashMap<String,Integer>();for(String g:groupOrder)counts.put(g,0);
            for(String cell:selectedCells)counts.compute(population.metadata().get(cell)[1],(k,v)->v+1);
            long prefilter=integer(request.analysis().get("prefilter_min_group_cells"),0,1000000);var included=new ArrayList<String>();var includedCounts=new LinkedHashMap<String,Integer>();
            boolean anyCondition=text(request.analysis().get("prefilter_scope")).equals("any-condition");var globallyExcluded=new HashSet<String>();
            if(anyCondition)for(String group:groupOrder)for(String conditionValue:conditionOrder)if(population.metadata().values().stream().filter(row->row[1].equals(group)&&row[2].equals(conditionValue)).count()<prefilter)globallyExcluded.add(group);
            for(int i=0;i<selectedCells.size();i++) {
                String cell=selectedCells.get(i);Map<String,String> d=dispositions.get(i);boolean keep=anyCondition?!globallyExcluded.contains(population.metadata().get(cell)[1]):counts.get(population.metadata().get(cell)[1])>=prefilter;
                require(cell.equals(d.get("cell_id"))&&Boolean.toString(keep).equalsIgnoreCase(d.get("included"))&&d.get("reason").equals(keep?"included":anyCondition?"explicit-any-condition-min-group-prefilter":"explicit-min-group-prefilter"),"Source cell disposition/order differs");
                if(keep){included.add(cell);includedCounts.merge(population.metadata().get(cell)[1],1,Integer::sum);}
            }
            var groups=new ArrayList<String>();for(String g:groupOrder)if(includedCounts.containsKey(g))groups.add(g);
            require(groups.equals(names(c.get("groups"),2,10000))&&included.size()==integer(c.get("cells"),1,request.limits().cells()),"Case group axis/cell count differs");
            List<Map<String,String>> selected=read(home.resolve("selected-interactions.tsv"),100000);var ids=new ArrayList<String>();var pathById=new LinkedHashMap<String,String>();
            for(var row:selected){String interaction=row.get("resource_row");identifier(interaction);require(pathById.put(interaction,row.get("pathway_name"))==null,"Duplicate selected original interaction");ids.add(interaction);}
            require(ids.size()==integer(c.get("selected_interactions"),1,100000),"Selected-interaction population differs");
            List<Map<String,String>> before=read(home.resolve("probabilities-unfiltered.tsv"),10000000),after=read(home.resolve("probabilities-filtered.tsv"),10000000);
            long expectedCells=(long)ids.size()*groups.size()*groups.size();require(before.size()==expectedCells&&after.size()==expectedCells,"Incomplete original probability cube");
            var probabilities=new LinkedHashMap<Key,Double>();var pvalues=new LinkedHashMap<Key,Double>();var sums=new HashMap<Node,Double>();var links=new HashMap<Node,Integer>();var pathwaySums=new HashMap<Key,Double>();
            long minCells=integer(request.analysis().get("min_cells"),0,1000000);int at=0;
            for(String interaction:ids)for(String source:groups)for(String target:groups) {
                Map<String,String> original=before.get(at),filtered=after.get(at++);Key key=new Key(interaction,source,target);
                for(var row:List.of(original,filtered))require(interaction.equals(row.get("interaction"))&&source.equals(row.get("source"))&&target.equals(row.get("target")),"Original cube labels/order differ");
                double old=finite(original.get("value")),p=finite(filtered.get("pvalue")),score=finite(filtered.get("value"));
                require(old>=0&&score>=0&&(score==0||score==old)&&p==finite(original.get("pvalue"))&&p>=0&&p<=1&&Math.abs(p-Math.rint(p*nboot)/nboot)<=1e-12,"Invalid original filter or permutation-count transport");
                if(includedCounts.get(source)<=minCells||includedCounts.get(target)<=minCells)require(score==0,"Original <=min.cells boundary differs");
                probabilities.put(key,score);pvalues.put(key,p);Node node=new Node(source,target);
                if(p<threshold&&score>0){sums.merge(node,score,Double::sum);links.merge(node,1,Integer::sum);}
                if(p<=threshold)pathwaySums.merge(new Key(pathById.get(interaction),source,target),score,Double::sum);
            }
            for(String metric:List.of("counts","weights")) {
                List<Map<String,String>> network=read(home.resolve("network-"+metric+".tsv"),10000000);require(network.size()==groups.size()*groups.size(),"Incomplete aggregate network matrix");var seen=new HashSet<Node>();
                for(var row:network) {
                    Node node=new Node(row.get("source"),row.get("target"));require(groups.contains(node.source)&&groups.contains(node.target)&&seen.add(node),"Aggregate network axis/duplicate differs");
                    if(metric.equals("counts"))require(finite(row.get("value"))==links.getOrDefault(node,0),"Original aggregate link count differs");
                    else close(finite(row.get("value")),sums.getOrDefault(node,0.0),"Original aggregate strength differs");
                }
            }
            List<Map<String,String>> pathways=read(home.resolve("pathways.tsv"),10000000);var active=new HashSet<String>();
            for(var entry:pathwaySums.entrySet())if(entry.getValue()>0)active.add(entry.getKey().feature);
            require(pathways.size()==(long)active.size()*groups.size()*groups.size(),"Incomplete original pathway cube");var pathwayValues=new HashMap<Key,Double>();
            for(var row:pathways) {
                Key key=new Key(row.get("pathway"),row.get("source"),row.get("target"));require(active.contains(key.feature)&&groups.contains(key.source)&&groups.contains(key.target)&&!pathwayValues.containsKey(key),"Pathway cube axis/duplicate differs");
                double score=finite(row.get("value"));close(score,pathwaySums.getOrDefault(key,0.0),"Original pathway <=threshold strength differs");pathwayValues.put(key,score);
            }
            var centrality=new HashMap<Centrality,Double>();long nonfinite=0;
            for(var row:read(home.resolve("centrality.tsv"),10000000)) {
                Centrality key=new Centrality(row.get("slot"),row.get("feature"),row.get("group"),row.get("metric"));require(!centrality.containsKey(key)&&groups.contains(key.group),"Duplicate/unknown centrality axis");
                double number;String token=row.get("value").strip();
                if(Set.of("NA","NaN","Inf","-Inf").contains(token)){nonfinite++;number=Double.NaN;}else number=finite(token);
                centrality.put(key,number);
            }
            Set<String> metrics=Set.of("outdeg_unweighted","indeg_unweighted","outdeg","indeg","hub","authority","eigen","page_rank","betweenness","flowbet","info");
            for(String slot:List.of("net","netP"))for(String feature:slot.equals("net")?ids:active)for(String group:groups) {
                for(String metric:metrics)require(centrality.containsKey(new Centrality(slot,feature,group,metric)),"Incomplete original centrality measures");
                double outgoing=0,incoming=0;
                for(String other:groups) {
                    Key outKey=new Key(feature,group,other),inKey=new Key(feature,other,group);
                    if(slot.equals("net")) {
                        outgoing+=probabilities.get(outKey);
                        incoming+=probabilities.get(inKey);
                    }else{outgoing+=pathwayValues.get(outKey);incoming+=pathwayValues.get(inKey);}
                }
                close(centrality.get(new Centrality(slot,feature,group,"outdeg")),outgoing,"Original outgoing centrality strength differs");
                close(centrality.get(new Centrality(slot,feature,group,"indeg")),incoming,"Original incoming centrality strength differs");
            }
            require(centrality.size()==(ids.size()+active.size())*groups.size()*metrics.size(),"Extra centrality records");
            List<Map<String,String>> fallbacks=read(home.resolve("original-centrality-fallbacks.tsv"),1000000);
            require(fallbacks.size()==integer(c.get("original_centrality_fallbacks"),0,1000000),"Incomplete original fallback diagnostics");
            for(var row:fallbacks)for(String group:groups)require(centrality.get(new Centrality(row.get("slot"),row.get("feature"),group,row.get("metric")))==0,"Source fallback zero not retained");
            projectionCells+=projection(worker,home,request,population,included);pathwayByCase.put(id,pathwayValues);pathwayOrder.put(id,names(c.get("pathways"),1,100000));groupByCase.put(id,groups);
            byCase.put(id,probabilities);fallbackCount+=fallbacks.size();nonfiniteCentrality+=nonfinite;probabilityCells+=expectedCells;
            audits.add(Map.of("id",id,"cells",included.size(),"groups",groups,"interactions",ids.size(),"pathways",active.size(),"probability_cells",expectedCells,"original_centrality_fallbacks",fallbacks.size(),"original_nonfinite_centrality_fields",nonfinite));
        }
        require(groupByCase.get("condition-1").equals(groupByCase.get("condition-2")),"Functional comparison requires identical ordered group axes after prefiltering; no padding/reordering is inferred");
        List<String> groups=groupByCase.get("condition-1");var labels=new ArrayList<String>();var prepared=new ArrayList<double[][]>();
        for(int c=1;c<=2;c++) {
            String id="condition-"+c;Map<Key,Double> values=pathwayByCase.get(id);double cutoff=0;
            Object trim=object(request.analysis().get("similarity")).get("trim_fraction");
            if(trim!=null) {double q=number(trim,0,0.25,false);double[] nonzero=values.values().stream().filter(v->v!=0).mapToDouble(Double::doubleValue).sorted().toArray();require(nonzero.length>0,"No pathway scores to trim");double pos=(nonzero.length-1)*q;int lo=(int)Math.floor(pos);cutoff=nonzero[lo]+(pos-lo)*(nonzero[Math.min(lo+1,nonzero.length-1)]-nonzero[lo]);}
            for(String path:pathwayOrder.get(id)) {
                labels.add(path+"--"+conditionOrder.get(c-1));double[][] graph=new double[groups.size()][groups.size()];
                for(int i=0;i<groups.size();i++)for(int j=0;j<groups.size();j++){double score=values.get(new Key(path,groups.get(i),groups.get(j)));graph[i][j]=score<cutoff?0:score;}prepared.add(graph);
            }
        }
        require(new HashSet<>(labels).size()==labels.size(),"Original pathway--condition labels are ambiguous");
        double[][] functional=org.jlinalg.omics.CellChatNetworkSimilarity.functional(prepared.toArray(double[][][]::new),10000000);
        long duplicateRanks=0,abnormalSimilarity=0,omittedCloserNeighbors=0;
        for(String kind:List.of("functional","structural")) {
            Path comparison=worker.resolve("comparison");double[][] raw=networkMatrix(comparison.resolve(kind+"-raw.tsv"),labels),smooth=networkMatrix(comparison.resolve(kind+"-similarity.tsv"),labels);
            int n=labels.size();Object chosen=object(request.analysis().get("similarity")).get("k");int k=chosen==null?(int)Math.ceil(Math.sqrt(n))+(n>25?1:0):(int)integer(chosen,2,n);
            require(k>=2&&k<=n,"Original SNN rank population is unsupported");int[][] ranks=new int[n][k];for(int[] row:ranks)java.util.Arrays.fill(row,-1);
            List<Map<String,String>> neighbors=read(comparison.resolve(kind+"-knn.tsv"),10000000);require(neighbors.size()==n*k,"Incomplete original FNN rank population");
            for(var row:neighbors) {
                int feature=labels.indexOf(row.get("feature")),rank=exact(row.get("rank"),1,k)-1,neighbor=exact(row.get("neighbor"),1,n)-1;
                require(feature>=0&&ranks[feature][rank]==-1,"Unknown or duplicate original neighbor coordinate");ranks[feature][rank]=neighbor;
            }
            for(int i=0;i<n;i++) {
                if(kind.equals("functional"))for(int j=0;j<n;j++)close(raw[i][j],functional[i][j],"Original directed Jaccard differs");
                var selected=new HashSet<Integer>();double previous=-1;
                for(int r=0;r<k;r++) {
                    require(ranks[i][r]>=0,"Missing original nearest neighbor");if(!selected.add(ranks[i][r]))duplicateRanks++;double distance=distance(raw,i,ranks[i][r]);require(distance+2e-12*Math.max(1,distance)>=previous,"Original neighbor distances are not ordered");previous=distance;
                }
                // Original FNN tie handling can omit a strictly closer graph
                // after removing an assumed self neighbor. Retain the original
                // ranks and count this source behavior instead of replacing it.
                for(int j=0;j<n;j++)if(!selected.contains(j)){double distance=distance(raw,i,j);if(distance+2e-12*Math.max(1,distance)<previous)omittedCloserNeighbors++;}
            }
            double[][] snn=org.jlinalg.omics.CellChatNetworkSimilarity.sharedNearestNeighbors(ranks,10000000);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){double value=smooth[i][j];same(value,raw[i][j]*snn[i][j],"Original source SNN smoothing differs");if(!Double.isFinite(value)||value<0||value>1)abnormalSimilarity++;}
        }
        long rankZeros=rank(worker,request,conditionOrder,groups,pathwayByCase);
        long abnormalP=0;
        for(Path file:List.of(worker.resolve("cases/condition-1/overexpression.tsv"),worker.resolve("cases/condition-2/overexpression.tsv"),worker.resolve("comparison/differential-genes.tsv"),worker.resolve("comparison/differential-ligand-receptor.tsv"))) {
            for(var row:read(file,10000000))for(var field:row.entrySet())if(field.getKey().endsWith("pvalues")||field.getKey().equals("pvalues.adj")) {
                String token=field.getValue().strip();if(token.equals("NA"))continue;
                if(Set.of("NaN","Inf","-Inf").contains(token)){abnormalP++;continue;}double value=finite(token);if(value<0||value>1)abnormalP++;
            }
        }
        long bubbleRows=0;
        for(String kind:List.of("all","up","down")) {
            Path file=worker.resolve("comparison/bubble-"+kind+".tsv");require(java.nio.file.Files.isRegularFile(file),"Required original bubble table is missing");
            for(var row:read(file,10000000)) {
                String name=row.get("dataset");int c=conditionOrder.indexOf(name);require(c>=0,"Unknown bubble dataset");
                String token=row.get("prob.original").strip();if(token.equals("NA"))continue;
                Key key=new Key(row.get("interaction_name"),row.get("source"),row.get("target"));Double expectedScore=byCase.get("condition-"+(c+1)).get(key);
                require(expectedScore!=null,"Bubble plot invented an unselected LR key");close(finite(token),expectedScore,"Bubble original probability differs");bubbleRows++;
            }
        }
        var result=new LinkedHashMap<String,Object>();result.put("cases",audits);result.put("probability_cells",probabilityCells);result.put("projection_cells",projectionCells);result.put("similarity_features",labels.size());result.put("original_centrality_fallbacks",fallbackCount);result.put("original_nonfinite_centrality_fields",nonfiniteCentrality);result.put("original_ranknet_zero_fallbacks",rankZeros);result.put("original_out_of_range_p_fields",abnormalP);result.put("original_duplicate_neighbor_entries",duplicateRanks);result.put("original_omitted_strictly_closer_neighbors",omittedCloserNeighbors);result.put("original_similarity_outside_unit_interval",abnormalSimilarity);result.put("bubble_original_probability_rows",bubbleRows);result.put("original_R_fits_independently_refitted",false);return result;
    }

    private static long projection(Path worker,Path home,Request request,Population population,List<String> included)throws IOException {
        Map<?,?> resource=object(json(worker.resolve("ppi-resource.json"),16777216));String species=text(object(request.analysis().get("projection")).get("species")),name="PPI."+species;
        require(name.equals(resource.get("resource")),"PPI resource identity differs");
        Map<?,?> lock=object(object(object(json(worker.getParent().resolve("source-lock.json"),2097152)).get("sources")).get("1.1.3"));
        Pin wanted=fingerprint(object(lock.get("decoded_ppi_exports")).get(name),Long.MAX_VALUE);require(wanted.equals(pin(worker.resolve("ppi-matrix.tsv"),wanted.bytes(),()->{})),"Decoded original PPI does not match independent resource export");
        require(text(object(object(lock.get("files")).get("data/"+name+".rda")).get("sha256")).equals(resource.get("file_sha256")),"Original PPI file identity differs");
        List<String> network=names(resource.get("genes"),1,100000);require(network.equals(names(resource.get("columns"),1,100000)),"Original PPI axes differ");
        List<Map<String,String>> signal=read(home.resolve("signaling-expression.tsv"),request.limits().genes()),projected=read(home.resolve("projected-expression.tsv"),request.limits().genes());
        require(signal.size()>=3&&projected.size()==signal.size(),"Incomplete projected expression rows");
        var genes=new ArrayList<String>();for(var row:signal){String gene=row.get("gene_id");require(population.genes().contains(gene)&&!genes.contains(gene),"Unknown/duplicate signaling gene");genes.add(gene);}
        double[][] expression=expressionMatrix(home.resolve("signaling-expression.tsv"),genes,included),observed=expressionMatrix(home.resolve("projected-expression.tsv"),genes,included),ppi=expressionMatrix(worker.resolve("ppi-matrix.tsv"),network,network);
        List<Map<String,String>> raw=read(worker.getParent().resolve("inputs/expression.tsv"),request.limits().genes());var input=new HashMap<String,Map<String,String>>();for(var row:raw)input.put(row.get("gene_id"),row);
        for(int i=0;i<genes.size();i++)for(int j=0;j<included.size();j++)require(expression[i][j]==finite(input.get(genes.get(i)).get(included.get(j))),"Original prepared signaling expression changed input values/order");
        Map<?,?> p=object(request.analysis().get("projection"));var normalization=text(p.get("normalization")).equals("rows")?org.jlinalg.omics.CellChatProjection.Normalization.ROWS:org.jlinalg.omics.CellChatProjection.Normalization.COLUMNS;
        var result=org.jlinalg.omics.CellChatProjection.project(genes.toArray(String[]::new),expression,network.toArray(String[]::new),ppi,number(p.get("alpha"),0,1,true),normalization,new org.jlinalg.omics.CellChatProjection.Limits(network.size(),request.limits().genes(),request.limits().cells(),request.limits().projectionWorkingBytes(),request.limits().projectionUpdates()));
        double[][] expected=result.expression();for(int i=0;i<genes.size();i++)for(int j=0;j<included.size();j++)close(observed[i][j],expected[i][j],"Complete original PPI projection differs from native solve");
        Map<?,?> report=object(json(home.resolve("projection-report.json"),1048576));var overlapping=new ArrayList<String>();for(String gene:genes)if(network.contains(gene))overlapping.add(gene);
        require(integer(report.get("overlap_count"),0,genes.size())==overlapping.size()&&names(report.get("overlapping_genes"),0,genes.size()).equals(overlapping)&&bool(report.get("projection_consumed"))!=bool(object(request.analysis().get("inference")).get("raw_use")),"Projection overlap/consumption report differs");
        return (long)genes.size()*included.size();
    }
    private static double[][] expressionMatrix(Path file,List<String> genes,List<String> columns)throws IOException {
        List<Map<String,String>> rows=read(file,genes.size());require(rows.size()==genes.size(),"Incomplete dense matrix");
        String expected=quote("gene_id");for(String column:columns)expected+="\t"+quote(column);
        try(var reader=java.nio.file.Files.newBufferedReader(file,java.nio.charset.StandardCharsets.UTF_8)){require(expected.equals(reader.readLine()),"Dense matrix column axes/order differ");}
        double[][] result=new double[genes.size()][columns.size()];for(int i=0;i<genes.size();i++) {
            var row=rows.get(i);require(genes.get(i).equals(row.get("gene_id"))&&row.size()==columns.size()+1,"Dense row axes/width differ");
            for(int j=0;j<columns.size();j++)result[i][j]=finite(row.get(columns.get(j)));
        }return result;
    }
    private static String quote(String text){return "\""+text.replace("\"","\"\"")+"\"";}
    private static double[][] networkMatrix(Path file,List<String> labels)throws IOException {
        List<Map<String,String>> rows=read(file,10000000);int n=labels.size();require(rows.size()==(long)n*n,"Incomplete similarity matrix");double[][] result=new double[n][n];int at=0;
        for(int i=0;i<n;i++)for(int j=0;j<n;j++){var row=rows.get(at++);require(labels.get(i).equals(row.get("row"))&&labels.get(j).equals(row.get("column")),"Original similarity axes/order differ");String token=row.get("value").strip();result[i][j]=switch(token){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->finite(token);};}return result;
    }
    private static double distance(double[][] matrix,int a,int b){double sum=0;for(double[] row:matrix){double delta=row[a]-row[b];sum+=delta*delta;}return sum;}
    private static int exact(String token,int min,int max)throws IOException {double d=finite(token);require(d>=min&&d<=max&&d==Math.rint(d),"Original index outside exact bounds");return (int)d;}
    private static long rank(Path worker,Request request,List<String> conditions,List<String> groups,Map<String,Map<Key,Double>> pathways)throws IOException {
        var all=new java.util.LinkedHashSet<String>();for(var values:pathways.values())for(Key key:values.keySet())all.add(key.feature);
        List<Map<String,String>> rows=read(worker.resolve("comparison/information-flow.tsv"),1000000);require(rows.size()==all.size()*2,"Incomplete original information-flow comparison");var seen=new HashSet<String>();var byPath=new HashMap<String,List<Map<String,String>>>();
        for(var row:rows) {
            String path=row.get("name"),condition=row.get("group");int index=conditions.indexOf(condition);require(index>=0&&all.contains(path)&&seen.add(path+"\r"+condition),"Unknown/duplicate original information-flow row");
            double expected=0;for(var value:pathways.get("condition-"+(index+1)).entrySet())if(value.getKey().feature.equals(path))expected+=value.getValue();close(finite(row.get("contribution")),expected,"Original pathway information flow differs");byPath.computeIfAbsent(path,k->new ArrayList<>()).add(row);
        }
        Map<String,String> expectedFallbacks=new HashMap<>();boolean tests=bool(object(request.analysis().get("rank_net")).get("do_stat"));
        if(tests)for(String path:all) {
            boolean missing=false,equal=true;int retained=0;
            for(String source:groups)for(String target:groups) {
                Key key=new Key(path,source,target);Double x=pathways.get("condition-1").get(key),y=pathways.get("condition-2").get(key);
                if((x!=null&&x!=0)||(y!=null&&y!=0)){retained++;if(x==null||y==null)missing=true;else if(!x.equals(y))equal=false;}
            }
            String cause=retained<=3?"three-or-fewer-nonzero-pairs":missing?"pathway-absent-in-a-condition":equal?"original-nonfinite-wilcox-replaced-zero":null;
            if(cause!=null){expectedFallbacks.put(path,cause);for(var row:byPath.get(path))require(finite(row.get("pvalues"))==0,"Original rankNet zero replacement changed");}
        }
        List<Map<String,String>> fallbacks=read(worker.resolve("comparison/original-rankNet-zero-fallbacks.tsv"),1000000);require(fallbacks.size()==expectedFallbacks.size(),"Incomplete original rankNet replacement diagnostics");
        for(var row:fallbacks)require(row.get("cause").equals(expectedFallbacks.remove(row.get("pathway")))&&finite(row.get("original_replacement_pvalue"))==0,"Original rankNet replacement cause differs");
        return fallbacks.size();
    }
    private static void close(double actual,double expected,String message)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),message);}
    private static void same(double actual,double expected,String message)throws IOException {if(!Double.isFinite(actual)||!Double.isFinite(expected))require(Double.isNaN(actual)&&Double.isNaN(expected)||actual==expected,message);else close(actual,expected,message);}
}
