/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;

/** Recomputes one declared cross-hypothesis MR family, preserving all source adjustments. */
final class StudyMrFamilyAdjustment implements StudyOperationExecutor {
    static final String CAVEAT="Cross-hypothesis testing applies only to the declared member hypotheses and their scoped primary MR tests, including source feature-budget definitions. Source adjustments remain source-specific. Missing or unresolved tests are not null findings. BH/BY are FDR methods under their respective assumptions; Bonferroni controls familywise error and NONE is nominal without multiplicity control. Adjustment does not establish instrument validity, causal identification, independence from association-based selection or calibrated input P values. Final editorial selection does not inherit a separate FDR guarantee.";
    private record Test(String hypothesis,String testId,String feature,String pairs,String estimator,String disposition,String p,String sourceQ,String sourceMethod,String sourceFamilySize,String sourceFamilyStatus,String source,String hash) { }
    @Override public Result execute(Context c)throws IOException {
        Files.createDirectories(c.outputDirectory());var p=map(c.operation().get("configuration"));String family=text(p.get("id"));
        var registered=org.jlinalg.study.StudyMrFamilyPolicy.resolve(c.study()).stream().filter(row->family.equals(row.get("id"))).toList();
        if(registered.size()!=1||!registered.get(0).equals(p))throw new IOException("MR family operation must match its registered policy");
        var tests=new ArrayList<Test>();var coverage=new ArrayList<Object>();var problems=new ArrayList<String>();int denominator=0;
        for(Object member:list(p.get("hypotheses"))){String h=text(member);var sources=new ArrayList<Path>();
            for(Path directory:c.dependencyDirectories().values()){
                var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));if(h.equals(m.get("hypothesis"))&&"mendelian_randomization".equals(m.get("operation_type")))sources.add(directory);
            }
            if(sources.size()!=1){problems.add(h+": requires one declared MR producer");coverage.add(Map.of("hypothesis",h,"status","missing_or_ambiguous_producer"));continue;}
            Path directory=sources.get(0);var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));var hashes=map(m.get("output_sha256"));
            if(!hashes.containsKey("mr-primary-results.tsv")){problems.add(h+": missing complete primary MR table");coverage.add(Map.of("hypothesis",h,"status","primary_table_unavailable","producer_state",text(m.get("state"))));continue;}
            Path file=StudyFindingInventory.verified(directory,"mr-primary-results.tsv",text(hashes.get("mr-primary-results.tsv")));var table=DelimitedData.readResult(file);
            boolean nativeTable=table.header().contains("test_id")&&table.header().contains("causal_p_value");
            var details=map(m.get("details"));var sourceFamily=map(details.get("resolved_family"));
            boolean nativeSource=nativeTable&&sourceFamily.isEmpty()&&!"externally_produced".equals(details.get("execution_origin"))&&!Files.isRegularFile(directory.resolve("supplied-analysis.yaml"));
            boolean complete="complete".equals(m.get("state"))||"not_applicable".equals(m.get("state"))&&table.rows().isEmpty();
            if(!nativeSource)complete&=Boolean.TRUE.equals(details.get("primary_selection_eligible"))&&"complete".equals(sourceFamily.get("coverage"))&&sourceFamily.get("tested_count") instanceof Number n&&n.longValue()==table.rows().size();
            var seen=new java.util.HashSet<String>();var rows=new ArrayList<Test>();int localDenominator=0;
            for(String[] row:table.rows()){
                String feature=cell(table,row,"feature_id"),testId=nativeTable?cell(table,row,"test_id"):"provided-"+StudyFindingInventory.hash(feature).substring(0,24);
                for(String field:row)if(field.chars().anyMatch(Character::isISOControl))throw new IOException("Control character in primary MR source metadata");
                if(feature.isBlank()||testId.isBlank()||!seen.add(testId))throw new IOException("MR family member has blank or duplicate test identity: "+h);
                String disposition=nativeTable?cell(table,row,"disposition"):table.header().contains("status")?cell(table,row,"status"):"complete";
                String rawP=cell(table,row,nativeTable?"causal_p_value":"p_value");double value=number(rawP);
                String status=nativeTable?cell(table,row,"family_status"):text(sourceFamily.get("coverage"));
                if(nativeSource){complete&=status.equals("complete_declared_family");int size=positiveOrZero(cell(table,row,"family_size"));if(localDenominator!=0&&size!=localDenominator)throw new IOException("Inconsistent source MR family size");localDenominator=size;}
                boolean estimable=Double.isFinite(value)&&value>=0&&value<=1;
                if(!estimable&&!disposition.equals("not_estimable"))complete=false;
                if(Double.isFinite(value)&&(value<0||value>1))throw new IOException("Invalid raw primary MR P value");
                if(disposition.equals("not_estimable")&&estimable)throw new IOException("Not-estimable MR test cannot carry a primary P value");
                if(!Set.of("primary_tested","complete","ok","not_estimable").contains(disposition))complete=false;
                rows.add(new Test(h,testId,feature,cell(table,row,"pair_ids"),nativeTable?cell(table,row,"primary_estimator"):cell(table,row,"requested_estimator"),disposition,rawP,cell(table,row,"adjusted_p"),nativeTable?cell(table,row,"adjustment"):text(sourceFamily.get("adjustment")),nativeTable?cell(table,row,"family_size"):text(sourceFamily.get("tested_count")),status,text(m.get("operation_id")),FollowupSupport.hash(file)));
            }
            if(nativeSource&&localDenominator!=rows.size())complete=false;
            if(!nativeSource)localDenominator=rows.size();
            // Empty native output requires its separately sealed family-size receipt.
            if(nativeSource&&rows.isEmpty()){
                if(hashes.containsKey("mr-testing.yaml")){
                    var receipt=StudyYaml.loadArtifact(StudyFindingInventory.verified(directory,"mr-testing.yaml",text(hashes.get("mr-testing.yaml"))));
                    complete&="complete_declared_family".equals(receipt.get("status"))&&receipt.get("family_size") instanceof Number n&&n.longValue()==0&&receipt.get("eligible_features") instanceof Number eligible&&eligible.longValue()==0;
                }else complete=false;
            }
            tests.addAll(rows);denominator=Math.addExact(denominator,localDenominator);
            coverage.add(Map.of("hypothesis",h,"status",complete?"complete":"unresolved","producer",m.get("operation_id"),"source_sha256",FollowupSupport.hash(file),"source_declared_tests",nativeSource?localDenominator:sourceFamily.getOrDefault("tested_count","unknown"),"observed_rows",rows.size(),"producer_state",text(m.get("state"))));
            if(!complete)problems.add(h+": incomplete or unresolved source family");
        }
        String status=problems.isEmpty()?"complete_cross_hypothesis_family":"unresolved_cross_hypothesis_family";
        var probabilities=new LinkedHashMap<String,Double>();for(var test:tests){double value=number(test.p());if(Double.isFinite(value)&&value>=0&&value<=1)probabilities.put(identity(test.hypothesis(),test.testId()),value);}
        Map<String,Double> adjusted=problems.isEmpty()?StudyMrMultiplicity.adjust(probabilities,denominator,text(p.get("adjustment"))):Map.of();
        var out=new ArrayList<String[]>();for(var test:tests){double q=adjusted.getOrDefault(identity(test.hypothesis(),test.testId()),Double.NaN);out.add(new String[]{family,test.hypothesis(),test.testId(),test.feature(),test.pairs(),test.estimator(),test.disposition(),test.p(),test.sourceQ(),test.sourceMethod(),test.sourceFamilySize(),test.sourceFamilyStatus(),Double.isFinite(q)?Double.toString(q):"NA",text(p.get("adjustment")),problems.isEmpty()?Integer.toString(denominator):"NA",status,Boolean.toString(problems.isEmpty()&&Double.isFinite(q)&&q<((Number)p.get("alpha")).doubleValue()),test.source(),test.hash()});}
        FollowupSupport.table(c.outputDirectory().resolve("mr-family-results.tsv"),List.of("family_id","hypothesis","test_id","feature_id","pair_ids","primary_estimator","disposition","causal_p_value","source_adjusted_p","source_adjustment","source_family_size","source_family_status","joint_adjusted_p","joint_adjustment","joint_family_size","joint_family_status","joint_significant_primary","source_operation_id","source_result_sha256"),out);
        var summary=new LinkedHashMap<String,Object>();summary.put("policy",p);summary.put("status",status);summary.put("coverage",coverage);summary.put("family_size",problems.isEmpty()?denominator:"unresolved");summary.put("known_member_test_count",denominator);summary.put("estimable_primary_tests",probabilities.size());summary.put("observed_rows",tests.size());summary.put("source_adjustments_preserved",true);summary.put("required_manuscript_caveat",CAVEAT);summary.put("problems",problems);
        if(!problems.isEmpty())summary.put("needs",List.of(StudyNeed.review("mr-family:"+family,"Resolve every registered member of the cross-hypothesis MR family",String.join("; ",problems)+". No partial-family joint adjustment or discovery was substituted.",true).document()));
        summary.put("method_report",StudyMethods.record("Declared cross-hypothesis primary MR multiplicity",p,List.of(),p.get("rationale"),List.of("Benjamini Yekutieli multiple testing dependence","Benjamini Hochberg false discovery rate")));
        write(c.outputDirectory().resolve("mr-family.yaml"),summary);
        return new Result(problems.isEmpty()?State.COMPLETE:State.WAITING_FOR_INPUT,problems.isEmpty()?"Applied the registered testing policy to the complete cross-hypothesis MR family":"Cross-hypothesis MR family is unresolved; joint discoveries withheld",List.of("mr-family.yaml","mr-family-results.tsv"),summary);
    }
    private static String identity(String h,String id){return h+"\n"+id;}
    private static String cell(DelimitedData t,String[] r,String name){return t.header().contains(name)?r[t.column(name)]:"";}
    private static double number(String value){try{return Double.parseDouble(value);}catch(NumberFormatException ignored){return Double.NaN;}}
    private static int positiveOrZero(String value)throws IOException{try{int n=Integer.parseInt(value);if(n>=0)return n;}catch(NumberFormatException ignored){ }throw new IOException("Invalid source MR family size");}
}
