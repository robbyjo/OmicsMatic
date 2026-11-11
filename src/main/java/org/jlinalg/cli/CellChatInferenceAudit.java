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
import static org.jlinalg.cli.CellChatInferenceRequest.*;
import static org.jlinalg.cli.CellChatInferenceTables.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete axis/filter/network checks; original R fitting is not reimplemented. */
final class CellChatInferenceAudit {
    private CellChatInferenceAudit() { }
    private record Key(String feature,String source,String target) { }
    private record Node(String source,String target) { }
    private record Centrality(String slot,String feature,String group,String metric) { }
    static Map<String,Object> verify(Path worker,Request request,Population population)throws IOException {
        Map<?,?> report=object(json(worker.resolve("worker-report.json"),16777216));
        require(text(report.get("status")).equals("COMPLETED_ORIGINAL_CELLCHAT_INFERENCE_WORKER_ONLY")&&Boolean.FALSE.equals(report.get("analysis_admitted")),"Original worker did not complete its non-admitting protocol");
        Map<?,?> cases=object(report.get("cases"));List<String> conditionOrder=names(request.analysis().get("condition_order"),1,32),groupOrder=names(request.analysis().get("group_order"),2,10000);
        var expected=new ArrayList<String>();for(int i=1;i<=conditionOrder.size();i++)expected.add("condition-"+i);
        require(cases.keySet().equals(new HashSet<>(expected)),"Original fit population differs");
        double threshold=number(object(request.analysis().get("network")).get("pvalue_threshold"),0,1,true);
        String label=text(request.analysis().get("source_label"));boolean modern=label.equals("2.1.2");
        int nboot=(int)integer(object(request.analysis().get("inference")).get("nboot"),2,10000);
        var audits=new ArrayList<Map<String,Object>>();var pathwayByCase=new LinkedHashMap<String,Map<Key,Double>>();var pathwayOrder=new LinkedHashMap<String,List<String>>();var groupByCase=new LinkedHashMap<String,List<String>>();long projectionCells=projectionAll(worker,request,population,expected);var byCase=new LinkedHashMap<String,Map<Key,Double>>();long fallbackCount=0,nonfiniteCentrality=0,probabilityCells=0;
        for(String id:expected) {
            Path home=worker.resolve("cases").resolve(id);Map<?,?> c=object(cases.get(id));
            require(c.equals(object(json(home.resolve("case-report.json"),1048576))),"Case report differs from worker summary");
            boolean condition=id.startsWith("condition-");int index=Integer.parseInt(id.substring(id.indexOf('-')+1))-1;
            String value=conditionOrder.get(index);
            require(text(c.get("kind")).equals(condition?"condition":"sample")&&text(c.get("value")).equals(value)&&Boolean.TRUE.equals(c.get("worker_case_complete")),"Case identity/completion differs");
            var selectedCells=new ArrayList<String>();for(String cell:population.cells())if(population.metadata().get(cell)[3].equals(value))selectedCells.add(cell);
            List<Map<String,String>> dispositions=read(home.resolve("cell-dispositions.tsv"),request.limits().cells());require(dispositions.size()==selectedCells.size(),"Incomplete cell dispositions");
            var counts=new LinkedHashMap<String,Integer>();for(String g:groupOrder)counts.put(g,0);
            for(String cell:selectedCells)counts.compute(population.metadata().get(cell)[1],(k,v)->v+1);
            long prefilter=integer(request.analysis().get("prefilter_min_group_cells"),0,1000000);var included=new ArrayList<String>();var includedCounts=new LinkedHashMap<String,Integer>();
            boolean anyCondition=text(request.analysis().get("prefilter_scope")).equals("any-condition");var globallyExcluded=new HashSet<String>();
            if(anyCondition)for(String group:groupOrder)for(String conditionValue:conditionOrder)if(population.metadata().values().stream().filter(row->row[1].equals(group)&&row[3].equals(conditionValue)).count()<prefilter)globallyExcluded.add(group);
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
            Map<?,?> filter=request.analysis().get("filter")==null?null:object(request.analysis().get("filter"));long minCells=filter==null?-1:integer(filter.get("min_cells"),0,1000000);int at=0;
            for(String interaction:ids)for(String source:groups)for(String target:groups) {
                Map<String,String> original=before.get(at),filtered=after.get(at++);Key key=new Key(interaction,source,target);
                for(var row:List.of(original,filtered))require(interaction.equals(row.get("interaction"))&&source.equals(row.get("source"))&&target.equals(row.get("target")),"Original cube labels/order differ");
                double old=finite(original.get("value")),p=finite(filtered.get("pvalue")),score=finite(filtered.get("value"));
                require(old>=0&&score>=0&&(score==0||score==old)&&p==finite(original.get("pvalue"))&&p>=0&&p<=1&&Math.abs(p-Math.rint(p*nboot)/nboot)<=1e-12,"Invalid original filter or permutation-count transport");
                if(filter==null)require(score==old,"An unrequested communication filter changed probabilities");
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
            Set<String> metrics=modern?Set.of("outdeg_unweighted","indeg_unweighted","outdeg","indeg","hub","authority","eigen","page_rank","betweenness","flowbet","info"):Set.of("outdeg","indeg","hub","authority","eigen","page_rank","betweenness","flowbet","info");
            for(String slot:List.of("net","netP"))for(String feature:slot.equals("net")?ids:active)for(String group:groups) {
                for(String metric:metrics)require(centrality.containsKey(new Centrality(slot,feature,group,metric)),"Incomplete original centrality measures");
                double outgoing=0,incoming=0;
                for(String other:groups) {
                    Key outKey=new Key(feature,group,other),inKey=new Key(feature,other,group);
                    if(slot.equals("net")) {
                        if(!modern||pvalues.get(outKey)<threshold)outgoing+=probabilities.get(outKey);
                        if(!modern||pvalues.get(inKey)<threshold)incoming+=probabilities.get(inKey);
                    }else{outgoing+=pathwayValues.get(outKey);incoming+=pathwayValues.get(inKey);}
                }
                close(centrality.get(new Centrality(slot,feature,group,"outdeg")),outgoing,"Original outgoing centrality strength differs");
                close(centrality.get(new Centrality(slot,feature,group,"indeg")),incoming,"Original incoming centrality strength differs");
            }
            require(centrality.size()==(ids.size()+active.size())*groups.size()*metrics.size(),"Extra centrality records");
            List<Map<String,String>> fallbacks=read(home.resolve("original-centrality-fallbacks.tsv"),1000000);
            require(fallbacks.size()==integer(c.get("original_centrality_fallbacks"),0,1000000),"Incomplete original fallback diagnostics");
            for(var row:fallbacks)for(String group:groups)require(centrality.get(new Centrality(row.get("slot"),row.get("feature"),group,row.get("metric")))==0,"Source fallback zero not retained");
            pathwayByCase.put(id,pathwayValues);pathwayOrder.put(id,names(c.get("pathways"),1,100000));groupByCase.put(id,groups);
            byCase.put(id,probabilities);fallbackCount+=fallbacks.size();nonfiniteCentrality+=nonfinite;probabilityCells+=expectedCells;
            audits.add(Map.of("id",id,"cells",included.size(),"groups",groups,"interactions",ids.size(),"pathways",active.size(),"probability_cells",expectedCells,"original_centrality_fallbacks",fallbacks.size(),"original_nonfinite_centrality_fields",nonfinite));
        }
        long abnormalP=0;for(String id:expected)for(var row:read(worker.resolve("cases").resolve(id).resolve("overexpression.tsv"),10000000))for(String name:modern?List.of("pvalues","pvalues.adj"):List.of("pvalues")) {require(row.containsKey(name),"Missing original edition-specific overexpression column: "+name);String token=row.get(name).strip();if(Set.of("NA","NaN","Inf","-Inf").contains(token))abnormalP++;else {double p=finite(token);if(p<0||p>1)abnormalP++;}}
        var result=new LinkedHashMap<String,Object>();result.put("source_label",label);result.put("cases",audits);result.put("probability_cells",probabilityCells);result.put("projection_cells",projectionCells);result.put("original_centrality_fallbacks",fallbackCount);result.put("original_nonfinite_centrality_fields",nonfiniteCentrality);result.put("original_out_of_range_p_fields",abnormalP);result.put("original_R_fits_independently_refitted",false);return result;
    }
    private static long projectionAll(Path worker,Request request,Population population,List<String> cases)throws IOException {
        if(request.analysis().get("projection")==null){for(String id:cases){Path home=worker.resolve("cases").resolve(id);require(!java.nio.file.Files.exists(home.resolve("projected-expression.tsv"))&&!java.nio.file.Files.exists(home.resolve("projection-report.json")),"Unrequested projection was performed");}return 0;}
        Map<?,?> parameter=object(request.analysis().get("projection"));String species=text(parameter.get("species")),name="PPI."+species,label=text(request.analysis().get("source_label"));
        Map<?,?> lock=object(object(object(json(worker.getParent().resolve("source-lock.json"),2097152)).get("sources")).get(label));Map<?,?> export=object(object(lock.get("ppi_exports")).get(species));Pin metadataPin=fingerprint(export.get("metadata"),16777216),wanted=fingerprint(export.get("values"),Long.MAX_VALUE);
        require(metadataPin.equals(pin(worker.resolve("ppi-resource.json"),metadataPin.bytes(),()->{}))&&wanted.equals(pin(worker.resolve("ppi-values.f64le"),wanted.bytes(),()->{})),"Complete PPI differs from independent binary resource decode");
        Map<?,?> resource=object(json(worker.resolve("ppi-resource.json"),16777216));require(name.equals(resource.get("resource"))&&text(object(object(lock.get("files")).get("data/"+name+".rda")).get("sha256")).equals(resource.get("file_sha256")),"Original PPI identity differs");
        List<String> network=names(resource.get("genes"),1,100000);require(network.equals(names(resource.get("columns"),1,100000)),"Original PPI axes differ");
        var genes=new ArrayList<String>();var allCells=new ArrayList<String>();var cellsByCase=new LinkedHashMap<String,List<String>>();var signals=new LinkedHashMap<String,double[][]>();var observed=new LinkedHashMap<String,double[][]>();
        for(String id:cases) {
            Path home=worker.resolve("cases").resolve(id);var cells=new ArrayList<String>();for(var row:read(home.resolve("cell-dispositions.tsv"),request.limits().cells()))if(row.get("included").equals("TRUE"))cells.add(row.get("cell_id"));require(!cells.isEmpty(),"No included projection cells");
            var currentGenes=new ArrayList<String>();for(var row:read(home.resolve("signaling-expression.tsv"),request.limits().genes())){String gene=row.get("gene_id");require(population.genes().contains(gene)&&!currentGenes.contains(gene),"Unknown/duplicate signaling gene");currentGenes.add(gene);}
            if(genes.isEmpty())genes.addAll(currentGenes);else require(genes.equals(currentGenes),"Condition signaling axes differ; a joint projection audit cannot infer row mapping");
            signals.put(id,expressionMatrix(home.resolve("signaling-expression.tsv"),genes,cells));observed.put(id,expressionMatrix(home.resolve("projected-expression.tsv"),genes,cells));cellsByCase.put(id,cells);allCells.addAll(cells);
        }
        require(allCells.size()<=request.limits().cells()&&new HashSet<>(allCells).size()==allCells.size(),"Joint projection cell population differs");
        double[][] expression=new double[genes.size()][allCells.size()];int offset=0;
        for(String id:cases){double[][] values=signals.get(id);int count=cellsByCase.get(id).size();for(int g=0;g<genes.size();g++)System.arraycopy(values[g],0,expression[g],offset,count);offset+=count;}
        var raw=new HashMap<String,Map<String,String>>();for(var row:read(worker.getParent().resolve("inputs/expression.tsv"),request.limits().genes()))raw.put(row.get("gene_id"),row);
        for(int i=0;i<genes.size();i++)for(int j=0;j<allCells.size();j++)require(expression[i][j]==finite(raw.get(genes.get(i)).get(allCells.get(j))),"Original signaling expression changed input values/order");
        double[][] ppi=CellChatInferencePpi.read(worker.resolve("ppi-values.f64le"),network.size(),request.limits().projectionWorkingBytes());var normalization=text(parameter.get("normalization")).equals("rows")?org.jlinalg.omics.CellChatProjection.Normalization.ROWS:org.jlinalg.omics.CellChatProjection.Normalization.COLUMNS;
        var result=org.jlinalg.omics.CellChatProjection.project(genes.toArray(String[]::new),expression,network.toArray(String[]::new),ppi,number(parameter.get("alpha"),0,1,true),normalization,new org.jlinalg.omics.CellChatProjection.Limits(network.size(),request.limits().genes(),request.limits().cells(),request.limits().projectionWorkingBytes(),request.limits().projectionUpdates()));
        double[][] expected=result.expression();var overlapping=new ArrayList<String>();for(String gene:genes)if(network.contains(gene))overlapping.add(gene);offset=0;
        for(String id:cases) {
            int count=cellsByCase.get(id).size();double[][] values=observed.get(id);for(int g=0;g<genes.size();g++)for(int c=0;c<count;c++)close(values[g][c],expected[g][offset+c],"Complete original projection differs from joint native solve");offset+=count;
            Map<?,?> report=object(json(worker.resolve("cases").resolve(id).resolve("projection-report.json"),1048576));require(integer(report.get("overlap_count"),0,genes.size())==overlapping.size()&&names(report.get("overlapping_genes"),0,genes.size()).equals(overlapping)&&bool(report.get("projection_consumed"))!=bool(object(request.analysis().get("inference")).get("raw_use")),"Projection report/consumption differs");
        }
        return (long)genes.size()*allCells.size();
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
    private static void close(double actual,double expected,String message)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),message);}
}
