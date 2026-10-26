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
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.study.StudyCohortPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Registered feature and widespread alerts; confidence/power changes never select a primary subset. */
final class StudyHeterogeneity implements StudyOperationExecutor {
    static final String CAVEAT="Heterogeneity alerts are diagnostic rules, not evidence of cohort failure or a reason to select the lowest-I-squared subset. I-squared and tau-squared are uncertain with few cohorts. Q and its P value depend on degrees of freedom and power; a larger Q P value after omission does not establish reduced biological heterogeneity. Generalized Q conditions on the declared sampling covariance. Source coverage, testing families and missing statistics limit widespread claims. Primary cohorts and results remain unchanged.";
    record Profile(StudyCohortResults.Table table,String metric,Map<String,Double> values,
            Map<String,Boolean> high,Set<String> eligible,Set<String> scope,
            Map<String,String> dispositions,Map<String,Object> summary,boolean complete) { }
    @Override public Result execute(Context c)throws Exception {
        Files.createDirectories(c.outputDirectory());var p=policy(c);var details=new LinkedHashMap<String,Object>();details.put("required_manuscript_caveat",CAVEAT);
        if(!Boolean.TRUE.equals(p.get("enabled")))return finish(c,p,details,true,false,"Registered heterogeneity review disabled");
        Path primary=StudyPrimaryResults.metaBaseline(c);if(primary==null)return finish(c,p,details,false,false,"Heterogeneity review needs the unique completed primary meta_analysis artifact; discovery-only pools and supplementary fits cannot supply its baseline");
        var profile=assess(c,primary,p);details.putAll(profile.summary());Path copy=c.outputDirectory().resolve("primary-meta-analysis.tsv");StudyProteinInputs.snapshot(primary,copy);features(c.outputDirectory().resolve("heterogeneity-features.tsv"),profile,p);
        return finish(c,p,details,profile.complete(),!profile.eligible().isEmpty(),profile.complete()?"Applied the registered feature and widespread heterogeneity rules to the unchanged primary result":"Heterogeneity coverage/statistics are incomplete; observed diagnostics are retained without a full-family widespread-heterogeneity conclusion");
    }
    static Map<String,Object> policy(Context c){return StudyCohortPolicy.heterogeneity(map(StudyResultTables.analysis(c).get("heterogeneity")));}
    static Profile assess(Context c,Path source,Map<String,Object> p)throws IOException {
        var t=StudyCohortResults.read(source,StudyCohortResults.method(c));var candidate=new TreeSet<String>();var dispositions=new LinkedHashMap<String,String>();var definitions=new TreeSet<String>();boolean unresolved=false;
        for(var row:t.rows().values()){
            String definition=row.label("q_definition");if(!definition.isBlank())definitions.add(definition);
            if(!row.estimable()||!Double.isFinite(row.beta())||!Double.isFinite(row.se())||row.se()<=0){dispositions.put(row.feature(),"association_not_estimable");continue;}
            double sources=sourceCount(row);if(!Double.isFinite(sources)||sources!=Math.rint(sources)||sources<1){dispositions.put(row.feature(),"source_count_unavailable");unresolved=true;continue;}
            if(sources<integer(p,"minimum_sources")){dispositions.put(row.feature(),"below_minimum_sources");continue;}
            candidate.add(row.feature());
        }
        String metric=text(p.get("metric"));boolean covariance=definitions.contains("generalized_sampling_covariance");
        if(metric.equals("auto")){boolean i2=candidate.stream().anyMatch(id->Double.isFinite(t.rows().get(id).number("i_squared")));metric=covariance||!i2?"q_p_value":"i_squared";}
        boolean mixedDefinitions=covariance&&definitions.size()>1;if(mixedDefinitions)unresolved=true;
        boolean sourceComplete=t.family().isEmpty()||"complete".equals(t.family().get("coverage"))&&(!(t.family().get("tested_count") instanceof Number n)||n.longValue()<=t.rows().size());
        var raw=new LinkedHashMap<String,Double>();
        for(String id:candidate){double value=t.rows().get(id).number(metric);if(!Double.isFinite(value)||value<0||value>(metric.equals("i_squared")?100:1)){dispositions.put(id,"heterogeneity_metric_unavailable_or_invalid");unresolved=true;}else raw.put(id,value);}
        var values=new LinkedHashMap<String,Double>();boolean qFamilyComplete=raw.size()==candidate.size()&&sourceComplete&&!mixedDefinitions;
        if(metric.equals("i_squared"))values.putAll(raw);else if(qFamilyComplete)values.putAll(StudyCohortResults.adjust(raw,text(p.get("q_adjustment"))));
        var eligible=new TreeSet<>(values.keySet());var scope=new TreeSet<String>();var high=new LinkedHashMap<String,Boolean>();double associationAlpha=p.containsKey("association_alpha")?((Number)p.get("association_alpha")).doubleValue():StudyCohortResults.alpha(c);
        for(String id:eligible){boolean inScope=!"primary_significant".equals(p.get("feature_scope"))||t.adjusted().getOrDefault(id,Double.NaN)<associationAlpha;if(inScope)scope.add(id);double value=values.get(id);high.put(id,metric.equals("i_squared")?value>=number(p,"i_squared_threshold"):value<number(p,"q_alpha"));dispositions.put(id,inScope?"eligible_in_alert_scope":"eligible_outside_primary_significant_scope");}
        if(metric.equals("q_p_value")&&!qFamilyComplete)for(String id:raw.keySet())dispositions.put(id,"Q_adjustment_withheld_incomplete_family");
        long highCount=scope.stream().filter(id->Boolean.TRUE.equals(high.get(id))).count();double fraction=scope.isEmpty()?Double.NaN:highCount/(double)scope.size();boolean complete=sourceComplete&&!unresolved;boolean enough=scope.size()>=integer(p,"minimum_features");boolean widespread=complete&&enough&&highCount>=integer(p,"minimum_high_features")&&Double.isFinite(fraction)&&(p.get("fraction_comparison").equals("greater_than")?fraction>number(p,"widespread_fraction"):fraction>=number(p,"widespread_fraction"));
        var summary=new LinkedHashMap<String,Object>();summary.put("primary_source",StudyCohortResults.receipt(c,t));summary.put("metric",metric);summary.put("requested_metric",p.get("metric"));summary.put("q_test_definitions",List.copyOf(definitions));summary.put("q_adjustment",metric.equals("q_p_value")?p.get("q_adjustment"):"not_applicable_descriptive_I_squared");summary.put("q_testing_denominator",metric.equals("q_p_value")?candidate.size():0);summary.put("q_family_complete",qFamilyComplete);summary.put("source_family_complete",sourceComplete);summary.put("assessment_complete",complete);summary.put("feature_scope",p.get("feature_scope"));summary.put("association_alpha",associationAlpha);summary.put("candidate_multisource_features",candidate.size());summary.put("eligible_features",eligible.size());summary.put("scope_features",scope.size());summary.put("observed_high_features",highCount);summary.put("high_features",complete?highCount:"NA");summary.put("high_fraction",complete?StudyCohortResults.finite(fraction):"NA");summary.put("widespread_alert",widespread);summary.put("alert_disposition",!complete?"unavailable_incomplete_source_or_statistics":!enough?"not_evaluable_below_minimum_feature_count":widespread?"registered_widespread_threshold_met":"registered_widespread_threshold_not_met");summary.put("required_manuscript_caveat",CAVEAT);
        return new Profile(t,metric,Map.copyOf(values),Map.copyOf(high),Set.copyOf(eligible),Set.copyOf(scope),Map.copyOf(dispositions),summary,complete);
    }
    static void features(Path output,Profile profile,Map<String,Object> p)throws IOException {
        var rows=new ArrayList<String[]>();for(String id:new TreeSet<>(profile.table().rows().keySet())){var row=profile.table().rows().get(id);rows.add(new String[]{id,profile.metric(),value(profile.values().getOrDefault(id,Double.NaN)),profile.metric().equals("i_squared")?text(p.get("i_squared_threshold")):text(p.get("q_alpha")),profile.metric().equals("i_squared")?"greater_than_or_equal":"less_than",profile.metric().equals("i_squared")?"NONE_descriptive":text(p.get("q_adjustment")),String.valueOf(profile.scope().contains(id)),profile.high().containsKey(id)?String.valueOf(profile.high().get(id)):"NA",profile.dispositions().getOrDefault(id,"not_eligible"),value(row.beta()),value(row.se()),value(row.p()),value(profile.table().adjusted().getOrDefault(id,Double.NaN)),value(sourceCount(row)),value(row.number("q")),value(row.number("q_df")),value(row.number("q_p_value")),value(row.number("i_squared")),value(row.number("tau_squared")),row.label("q_definition")});}
        FollowupSupport.table(output,List.of("feature_id","metric","metric_value","threshold","threshold_comparison","adjustment","in_alert_scope","high_heterogeneity","status","beta","se","p_value","association_adjusted_p","n_sources","q","q_df","q_p_value","i_squared","tau_squared","q_definition"),rows);
    }
    static boolean recordedAlert(Context c,String type,String file)throws IOException {
        Path source=StudyResultTables.artifact(c,type,file);if(source==null)return false;var summary=StudyYaml.loadArtifact(source);if(type.equals("heterogeneity_review"))return Boolean.TRUE.equals(summary.get("widespread_alert"));return list(summary.get("zero_overlap_alerts")).stream().map(StudyPublicEvidence::map).anyMatch(a->Boolean.TRUE.equals(a.get("alert")));
    }
    static boolean recordedComplete(Context c,String type,String file)throws IOException {
        Path source=StudyResultTables.artifact(c,type,file);if(source==null)return false;var summary=StudyYaml.loadArtifact(source);return Boolean.TRUE.equals(summary.get(type.equals("heterogeneity_review")?"assessment_complete":"alert_assessment_complete"));
    }
    static double sourceCount(StudyCohortResults.Row row){double count=row.number("n_sources");return Double.isFinite(count)?count:row.number("n_cohorts");}
    private static int integer(Map<String,Object> p,String key){return ((Number)p.get(key)).intValue();}
    private static double number(Map<String,Object> p,String key){return ((Number)p.get(key)).doubleValue();}
    static String value(double n){return Double.isFinite(n)?String.valueOf(n):"NA";}
    private static Result finish(Context c,Map<String,Object> p,Map<String,Object> details,boolean complete,boolean performed,String reason)throws IOException {
        details.put("method_report",Map.of("performed",performed,"operation","heterogeneity_review","method","Registered per-feature and widespread heterogeneity diagnostics","parameters",p,"software","JLinAlg "+JLinAlgCli.version(),"references",List.of("https://doi.org/10.1002/sim.1186","https://www.cochrane.org/authors/handbooks-and-manuals/handbook/current/chapter-10")));details.put("interpretation",reason);if(!complete)details.put("needs",List.of(StudyNeed.review("heterogeneity:"+c.operation().get("hypothesis"),"Resolve primary heterogeneity coverage and statistics",reason,true).document()));write(c.outputDirectory().resolve("heterogeneity-review.yaml"),details);var outputs=new ArrayList<String>();try(var files=Files.walk(c.outputDirectory())){for(Path file:files.filter(Files::isRegularFile).sorted().toList())outputs.add(c.outputDirectory().relativize(file).toString().replace('\\','/'));}return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,reason,outputs,details);
    }
}
