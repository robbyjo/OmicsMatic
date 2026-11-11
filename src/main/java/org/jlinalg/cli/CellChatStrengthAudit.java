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
import static org.jlinalg.cli.CellChatStrengthRequest.*;
import static org.jlinalg.cli.CellChatStrengthTables.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete axis/filter/network checks; original R fitting is not reimplemented. */
final class CellChatStrengthAudit {
    private CellChatStrengthAudit() { }
    private record Key(String feature,String source,String target) { }
    private record Node(String source,String target) { }
    private record Centrality(String slot,String feature,String group,String metric) { }
    static Map<String,Object> verify(Path worker,Request request,Population population)throws IOException {
        Map<?,?> report=object(json(worker.resolve("worker-report.json"),16777216));
        require(text(report.get("status")).equals("COMPLETED_ORIGINAL_CELLCHAT_STRENGTH_WORKER_ONLY")&&Boolean.FALSE.equals(report.get("analysis_admitted")),"Original worker did not complete its non-admitting protocol");
        Map<?,?> cases=object(report.get("cases"));List<String> sampleOrder=names(request.analysis().get("sample_order"),4,256),conditionOrder=names(request.analysis().get("condition_order"),2,2),groupOrder=names(request.analysis().get("group_order"),2,10000);
        var expected=new ArrayList<String>();expected.addAll(List.of("condition-1","condition-2"));for(int i=1;i<=sampleOrder.size();i++)expected.add("sample-"+i);
        require(cases.keySet().equals(new HashSet<>(expected)),"Original fit population differs");
        double threshold=number(object(request.analysis().get("network")).get("pvalue_threshold"),0,1,true);
        int nboot=(int)integer(object(request.analysis().get("inference")).get("nboot"),2,10000);
        var audits=new ArrayList<Map<String,Object>>();var byCase=new LinkedHashMap<String,Map<Key,Double>>();long fallbackCount=0,nonfiniteCentrality=0,probabilityCells=0;
        for(String id:expected) {
            Path home=worker.resolve("cases").resolve(id);Map<?,?> c=object(cases.get(id));
            require(c.equals(object(json(home.resolve("case-report.json"),1048576))),"Case report differs from worker summary");
            boolean condition=id.startsWith("condition-");int index=Integer.parseInt(id.substring(id.indexOf('-')+1))-1;
            String value=(condition?conditionOrder:sampleOrder).get(index);
            require(text(c.get("kind")).equals(condition?"condition":"sample")&&text(c.get("value")).equals(value)&&Boolean.TRUE.equals(c.get("worker_case_complete")),"Case identity/completion differs");
            var selectedCells=new ArrayList<String>();for(String cell:population.cells())if(population.metadata().get(cell)[condition?3:2].equals(value))selectedCells.add(cell);
            List<Map<String,String>> dispositions=read(home.resolve("cell-dispositions.tsv"),request.limits().cells());require(dispositions.size()==selectedCells.size(),"Incomplete cell dispositions");
            var counts=new LinkedHashMap<String,Integer>();for(String g:groupOrder)counts.put(g,0);
            for(String cell:selectedCells)counts.compute(population.metadata().get(cell)[1],(k,v)->v+1);
            long prefilter=integer(request.analysis().get("prefilter_min_group_cells"),0,1000000);var included=new ArrayList<String>();var includedCounts=new LinkedHashMap<String,Integer>();
            for(int i=0;i<selectedCells.size();i++) {
                String cell=selectedCells.get(i);Map<String,String> d=dispositions.get(i);boolean keep=counts.get(population.metadata().get(cell)[1])>=prefilter;
                require(cell.equals(d.get("cell_id"))&&Boolean.toString(keep).equalsIgnoreCase(d.get("included"))&&d.get("reason").equals(keep?"included":"explicit-min-group-prefilter"),"Source cell disposition/order differs");
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
            long minCells=integer(object(request.analysis().get(condition?"condition_filter":"sample_filter")).get("min_cells"),0,1000000);int at=0;
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
                    require(row.get("metric").equals(metric.equals("counts")?"count":"weight"),"Aggregate metric differs");
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
                        if(pvalues.get(outKey)<threshold)outgoing+=probabilities.get(outKey);
                        if(pvalues.get(inKey)<threshold)incoming+=probabilities.get(inKey);
                    }else{outgoing+=pathwayValues.get(outKey);incoming+=pathwayValues.get(inKey);}
                }
                close(centrality.get(new Centrality(slot,feature,group,"outdeg")),outgoing,"Original outgoing centrality strength differs");
                close(centrality.get(new Centrality(slot,feature,group,"indeg")),incoming,"Original incoming centrality strength differs");
            }
            require(centrality.size()==(ids.size()+active.size())*groups.size()*metrics.size(),"Extra centrality records");
            List<Map<String,String>> fallbacks=read(home.resolve("original-centrality-fallbacks.tsv"),1000000);
            require(fallbacks.size()==integer(c.get("original_centrality_fallbacks"),0,1000000),"Incomplete original fallback diagnostics");
            for(var row:fallbacks)for(String group:groups)require(centrality.get(new Centrality(row.get("slot"),row.get("feature"),group,row.get("metric")))==0,"Source fallback zero not retained");
            byCase.put(id,probabilities);fallbackCount+=fallbacks.size();nonfiniteCentrality+=nonfinite;probabilityCells+=expectedCells;
            audits.add(Map.of("id",id,"cells",included.size(),"groups",groups,"interactions",ids.size(),"pathways",active.size(),"probability_cells",expectedCells,"original_centrality_fallbacks",fallbacks.size(),"original_nonfinite_centrality_fields",nonfinite));
        }
        var allKeys=new java.util.LinkedHashSet<Key>();for(var values:byCase.values())allKeys.addAll(values.keySet());
        List<Map<String,String>> comparisons=read(worker.resolve("comparison/pooled-and-sample-probabilities.tsv"),10000000);require(comparisons.size()==allKeys.size(),"Incomplete comparison-key population");
        double differenceThreshold=number(object(request.analysis().get("comparison")).get("strength_difference_threshold"),0,1000000,false);
        var sampleCondition=new HashMap<String,String>();for(var row:population.metadata().values())sampleCondition.put(row[2],row[3]);
        long notTested=0,nonfiniteComparison=0,notComparablePooled=0,pooledWithZeroSamples=0;int compareAt=0;
        var zeroSampleExamples=new ArrayList<Map<String,Object>>();
        for(int condition=0;condition<conditionOrder.size();condition++)for(var entry:byCase.get("condition-"+(condition+1)).entrySet())if(entry.getValue()>0) {
            boolean selected=true,zero=true;var samples=new ArrayList<String>();
            for(int i=0;i<sampleOrder.size();i++)if(sampleCondition.get(sampleOrder.get(i)).equals(conditionOrder.get(condition))) {
                samples.add(sampleOrder.get(i));Double score=byCase.get("sample-"+(i+1)).get(entry.getKey());
                if(score==null)selected=false;else if(score!=0)zero=false;
            }
            if(selected&&zero&&!samples.isEmpty()) {
                pooledWithZeroSamples++;
                if(zeroSampleExamples.size()<5)zeroSampleExamples.add(Map.of("condition",conditionOrder.get(condition),"interaction",entry.getKey().feature,"source",entry.getKey().source,"target",entry.getKey().target,"pooled_score",entry.getValue(),"selected_samples_with_zero_score",samples));
            }
        }
        for(Key key:allKeys) {
            Map<String,String> row=comparisons.get(compareAt++);require(key.feature.equals(row.get("interaction"))&&key.source.equals(row.get("source"))&&key.target.equals(row.get("target")),"Comparison key order/axis differs");
            Double control=byCase.get("condition-1").get(key),treated=byCase.get("condition-2").get(key);
            nullable(row.get("control"),control,"Control comparison score differs");nullable(row.get("treated"),treated,"Treated comparison score differs");
            if(control==null||treated==null) {
                require(row.get("treated_minus_control").strip().equals("NA")&&row.get("strict_difference_above_threshold").equals("NA"),"Unselected pooled interaction was filled or compared");notComparablePooled++;
            }else {
                double delta=finite(row.get("treated_minus_control"));close(delta,treated-control,"Strict treated-minus-control score differs");
                require(row.get("strict_difference_above_threshold").equals(delta>differenceThreshold?"TRUE":"FALSE"),"Strict source difference cutoff differs");
            }
            boolean complete=true;int n1=0,n2=0;
            for(int i=0;i<sampleOrder.size();i++) {
                if(byCase.get("sample-"+(i+1)).get(key)==null)complete=false;
                if(sampleCondition.get(sampleOrder.get(i)).equals(conditionOrder.get(0)))n1++;else n2++;
            }
            String status=row.get("status");
            if(!complete||n1<2||n2<2) {
                require(status.equals(!complete?"not-tested-incomplete-selected-interaction":"not-tested-fewer-than-two-samples-per-condition")&&row.get("wilcox_statistic").strip().equals("NA")&&row.get("wilcox_pvalue").strip().equals("NA"),"Incomplete sample test was silently filled or admitted");notTested++;
            }else {
                double statistic=finite(row.get("wilcox_statistic"));require(statistic>=0&&statistic<=(long)n1*n2,"Original rank-sum statistic outside exact sample bound");
                String token=row.get("wilcox_pvalue").strip();
                if(Set.of("NA","NaN","Inf","-Inf").contains(token)){require(status.equals("original-nonfinite-wilcox-result"),"Original nonfinite sample-test status missing");nonfiniteComparison++;}
                else {double p=finite(token);require(p>=0&&p<=1&&status.equals("tested-original-wilcox"),"Original sample-test p-value/status differs");}
            }
        }
        var result=new LinkedHashMap<String,Object>();result.put("cases",audits);result.put("probability_cells",probabilityCells);result.put("original_centrality_fallbacks",fallbackCount);result.put("original_nonfinite_centrality_fields",nonfiniteCentrality);result.put("comparison_rows",comparisons.size());result.put("not_tested_sample_comparisons",notTested);result.put("not_comparable_pooled_rows",notComparablePooled);result.put("original_nonfinite_sample_test_fields",nonfiniteComparison);result.put("positive_pooled_rows_with_zero_selected_sample_scores",pooledWithZeroSamples);result.put("pooled_sample_zero_examples",zeroSampleExamples);result.put("pooled_sample_zero_example_limit",5);result.put("original_R_fits_independently_refitted",false);result.putAll(CellChatStrengthComparison.verify(worker,request,population));return result;
    }
    private static void close(double actual,double expected,String message)throws IOException {
        require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),message);
    }
    private static void nullable(String actual,Double expected,String message)throws IOException {
        if(expected==null)require(actual.strip().equals("NA"),"Unselected original interaction was silently filled");else close(finite(actual),expected,message);
    }
}
