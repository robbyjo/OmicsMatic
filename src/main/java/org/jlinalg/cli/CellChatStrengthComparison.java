/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.omics.CellChatSignalingStrengths;
import static org.jlinalg.cli.CellChatStrengthRequest.*;
import static org.jlinalg.cli.CellChatStrengthTables.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Replay original node-strength selection and independently check rank statistics. */
final class CellChatStrengthComparison {
    private CellChatStrengthComparison() { }
    private record Key(String slot,String feature,String group,String direction) { }
    private record Case(String id,String kind,String value,String condition,List<String> groups,Map<Key,Double> strengths) { }
    private static final List<String> FIELDS=List.of("slot","feature","group","direction","control","treated","treated_minus_control","positive_difference_above_threshold","negative_difference_above_threshold","absolute_difference_above_threshold","difference_mode","selected","control_sample_count","treated_sample_count","control_missing_count","treated_missing_count","wilcox_statistic","wilcox_pvalue","test_method","test_warning_count","status");

    static Map<String,Object> verify(Path worker,Request request,Population population)throws IOException {
        List<String> conditions=names(request.analysis().get("condition_order"),2,2),samples=names(request.analysis().get("sample_order"),4,256),groups=names(request.analysis().get("group_order"),2,10000);
        Map<?,?> parameter=object(request.analysis().get("comparison"));String mode=text(parameter.get("difference_mode"));double threshold=number(parameter.get("strength_difference_threshold"),0,1000000,false);
        var selection=CellChatSignalingStrengths.DifferenceMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT));
        var sampleConditions=new HashMap<String,String>();for(String[] row:population.metadata().values())require(sampleConditions.putIfAbsent(row[2],row[3])==null||sampleConditions.get(row[2]).equals(row[3]),"Sample crosses condition populations");
        var cases=new ArrayList<Case>();var features=new LinkedHashMap<String,LinkedHashSet<String>>();for(String slot:List.of("net","netP"))features.put(slot,new LinkedHashSet<>());
        for(int i=0;i<2+samples.size();i++) {
            boolean pooled=i<2;String id=(pooled?"condition-":"sample-")+(pooled?i+1:i-1);String value=pooled?conditions.get(i):samples.get(i-2);String condition=pooled?value:sampleConditions.get(value);Path home=worker.resolve("cases").resolve(id);
            Map<?,?> report=object(json(home.resolve("case-report.json"),1048576));List<String> caseGroups=names(report.get("groups"),2,10000);
            for(var row:read(home.resolve("selected-interactions.tsv"),100000))features.get("net").add(row.get("resource_row"));features.get("netP").addAll(names(report.get("pathways"),1,100000));
            var strengths=new LinkedHashMap<Key,Double>();
            for(var row:read(home.resolve("centrality.tsv"),10000000))if(Set.of("outdeg","indeg").contains(row.get("metric"))) {
                var key=new Key(row.get("slot"),row.get("feature"),row.get("group"),row.get("metric").equals("outdeg")?"outgoing":"incoming");double number=finite(row.get("value"));require(number>=0&&strengths.put(key,number)==null,"Duplicate/invalid original centrality strength");
            }
            cases.add(new Case(id,pooled?"condition":"sample",value,condition,caseGroups,strengths));
        }
        var keys=new ArrayList<Key>();for(String slot:features.keySet())for(String feature:features.get(slot))for(String group:groups)for(String direction:List.of("outgoing","incoming"))keys.add(new Key(slot,feature,group,direction));
        Path comparison=worker.resolve("comparison");header(comparison.resolve("strengths-by-case.tsv"),List.of("case","kind","case_value","condition","slot","feature","group","direction","value","status"));
        var longRows=read(comparison.resolve("strengths-by-case.tsv"),10000000);require(longRows.size()==(long)keys.size()*cases.size(),"Incomplete case-strength population");
        int index=0;long absentStrengths=0;
        for(Key key:keys)for(Case c:cases) {
            var row=longRows.get(index++);axes(row,key);require(c.id.equals(row.get("case"))&&c.kind.equals(row.get("kind"))&&c.value.equals(row.get("case_value"))&&c.condition.equals(row.get("condition")),"Case/sample/condition strength order differs");Double value=c.strengths.get(key);nullable(row.get("value"),value);
            String status=value!=null?"present-original-strength":!c.groups.contains(key.group)?"group-absent-after-prefilter":"feature-not-selected";require(status.equals(row.get("status")),"Missing original strength status differs");if(value==null)absentStrengths++;
        }
        header(comparison.resolve("pooled-and-sample-strengths.tsv"),FIELDS);header(comparison.resolve("selected-strengths.tsv"),FIELDS);
        var rows=read(comparison.resolve("pooled-and-sample-strengths.tsv"),10000000);var selectedRows=read(comparison.resolve("selected-strengths.tsv"),10000000);require(rows.size()==keys.size(),"Incomplete pooled-strength population");
        long selected=0,notComparable=0,notTested=0,nonfinite=0,tested=0,testWarnings=0;index=0;int selectedAt=0;
        for(Key key:keys) {
            var row=rows.get(index++);require(row.keySet().equals(Set.copyOf(FIELDS)),"Strength-comparison fields differ");axes(row,key);require(mode.equals(row.get("difference_mode")),"Requested difference mode differs");Double control=cases.get(0).strengths.get(key),treated=cases.get(1).strengths.get(key);nullable(row.get("control"),control);nullable(row.get("treated"),treated);
            CellChatSignalingStrengths.Contrast contrast=null;
            if(control==null||treated==null) {
                notComparable++;for(String field:List.of("treated_minus_control","positive_difference_above_threshold","negative_difference_above_threshold","absolute_difference_above_threshold","selected"))require(row.get(field).strip().equals("NA"),"An absent pooled strength was filled or selected");
            }else {
                contrast=CellChatSignalingStrengths.contrast(control,treated,threshold,selection);close(finite(row.get("treated_minus_control")),contrast.treatedMinusControl());flag(row,"positive_difference_above_threshold",contrast.positiveAboveThreshold());flag(row,"negative_difference_above_threshold",contrast.negativeAboveThreshold());flag(row,"absolute_difference_above_threshold",contrast.absoluteAboveThreshold());flag(row,"selected",contrast.selected());
            }
            if(contrast!=null&&contrast.selected()){selected++;require(selectedAt<selectedRows.size()&&row.equals(selectedRows.get(selectedAt++)),"Selected-strength subset/order differs");}
            var x=new ArrayList<Double>();var y=new ArrayList<Double>();int missingX=0,missingY=0;
            for(int i=2;i<cases.size();i++) {
                Case c=cases.get(i);Double value=c.strengths.get(key);boolean first=c.condition.equals(conditions.get(0));require(first||c.condition.equals(conditions.get(1)),"Unknown sample condition");
                if(value==null){if(first)missingX++;else missingY++;}else(first?x:y).add(value);
            }
            count(row,"control_sample_count",x.size());count(row,"treated_sample_count",y.size());count(row,"control_missing_count",missingX);count(row,"treated_missing_count",missingY);
            String status=contrast==null?"not-tested-incomplete-pooled-strength":!contrast.selected()?"not-tested-pooled-difference-below-or-equal-threshold":missingX+missingY>0?"not-tested-incomplete-sample-strength":x.size()<2||y.size()<2?"not-tested-fewer-than-two-samples-per-condition":"test-required";
            long warnings=integerToken(row.get("test_warning_count"));testWarnings+=warnings;
            if(!status.equals("test-required")) {
                notTested++;require(status.equals(row.get("status"))&&row.get("wilcox_statistic").strip().equals("NA")&&row.get("wilcox_pvalue").strip().equals("NA")&&row.get("test_method").equals("NA")&&warnings==0,"An unrequested/incomplete strength test was admitted");
            }else {
                var rank=CellChatSignalingStrengths.rankSum(x.stream().mapToDouble(Double::doubleValue).toArray(),y.stream().mapToDouble(Double::doubleValue).toArray());close(finite(row.get("wilcox_statistic")),rank.statistic());require(row.get("test_method").startsWith("Wilcoxon rank sum"),"Missing original test method");String p=row.get("wilcox_pvalue").strip();
                if(Set.of("NA","NaN","Inf","-Inf").contains(p)){nonfinite++;require(row.get("status").equals("original-nonfinite-wilcox-result"),"Original nonfinite strength-test disposition differs");}
                else{double number=finite(p);require(number>=0&&number<=1&&row.get("status").equals("tested-original-wilcox"),"Invalid strength-test p-value/status");tested++;}
            }
        }
        require(selectedAt==selectedRows.size(),"Extra selected-strength rows");Map<?,?> report=object(json(comparison.resolve("strength-report.json"),1048576));
        require(integer(report.get("comparison_rows"),0,10000000)==keys.size()&&integer(report.get("selected_rows"),0,10000000)==selected&&integer(report.get("not_tested_rows"),0,10000000)==notTested&&integer(report.get("nonfinite_test_rows"),0,10000000)==nonfinite&&integer(report.get("original_strength_test_warnings"),0,10000000)==testWarnings,"Strength-report census differs");require(mode.equals(text(report.get("difference_mode")))&&Boolean.FALSE.equals(report.get("missing_strengths_filled"))&&Boolean.FALSE.equals(report.get("multiplicity_correction_inferred")),"Strength-report scope differs");
        Map<?,?> stats=object(json(comparison.resolve("guarded-original-stats.json"),2097152));require(stats.get("before").equals(stats.get("after"))&&object(stats.get("before")).keySet().equals(Set.of("generic","default")),"Original rank-sum functions changed");
        var result=new LinkedHashMap<String,Object>();result.put("strength_comparison_rows",keys.size());result.put("selected_strength_comparisons",selected);result.put("not_comparable_pooled_strengths",notComparable);result.put("not_tested_strength_comparisons",notTested);result.put("original_nonfinite_strength_test_fields",nonfinite);result.put("tested_strength_comparisons",tested);result.put("original_strength_test_warning_count",testWarnings);result.put("missing_case_strengths",absentStrengths);return result;
    }
    private static void axes(Map<String,String> row,Key key)throws IOException {require(key.slot.equals(row.get("slot"))&&key.feature.equals(row.get("feature"))&&key.group.equals(row.get("group"))&&key.direction.equals(row.get("direction")),"Strength axes/order differ");}
    private static void header(Path file,List<String> fields)throws IOException {
        String expected=fields.stream().map(f->"\""+f+"\"").collect(java.util.stream.Collectors.joining("\t"));
        try(var reader=java.nio.file.Files.newBufferedReader(file,java.nio.charset.StandardCharsets.UTF_8)){require(expected.equals(reader.readLine()),"Strength-table fields/order differ: "+file.getFileName());}
    }
    private static void count(Map<String,String> row,String field,long expected)throws IOException {require(integerToken(row.get(field))==expected,"Strength sample count differs: "+field);}
    private static long integerToken(String raw)throws IOException {double x=finite(raw);require(x>=0&&x<=10000000&&x==Math.rint(x),"Invalid strength census");return(long)x;}
    private static void flag(Map<String,String> row,String field,boolean expected)throws IOException {require(row.get(field).equals(expected?"TRUE":"FALSE"),"Strict strength threshold flag differs: "+field);}
    private static void nullable(String value,Double expected)throws IOException {if(expected==null)require(value.strip().equals("NA"),"Missing original strength was filled");else close(finite(value),expected);}
    private static void close(double actual,double expected)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=2e-12*Math.max(1,Math.abs(expected)),"Native strength or rank statistic differs");}
}
