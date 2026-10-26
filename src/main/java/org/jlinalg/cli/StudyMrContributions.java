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
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Explicit external feature ownership with native completion and a separate joint MR family. */
final class StudyMrContributions {
    static final String CAVEAT="Completed external MR tests were reused for explicitly registered molecular features. JLinAlg validated the supplied schema, hashes and declared primary protocol; it did not reproduce those fits, instruments, sample QC or causal assumptions. Original source adjustments remain source-specific; joint MR adjustments refer only to this registered completion family. A reused analysis is not independent replication.";
    record Loaded(List<Object> records,Set<String> covered,List<StudyNeed> needs,Map<String,Object> summary) { }
    private StudyMrContributions() { }

    static List<String> validateTable(List<String> header,List<String[]> rows,Map<String,Object> definition) {
        var problems=new ArrayList<String>();
        for(String field:List.of("feature_id","beta","se","p_value","method","n_instruments"))if(!header.contains(field))problems.add("MR contributions require logical "+field);
        if(!problems.isEmpty())return problems;
        var declared=new TreeSet<String>();for(Object f:list(map(definition.get("scope")).get("feature_ids")))declared.add(text(f));
        var observed=new TreeSet<String>();String primary=text(map(map(definition.get("method")).get("parameters")).get("primary_estimator"));
        for(String[] row:rows) {
            String feature=row[header.indexOf("feature_id")];if(!observed.add(feature))problems.add("Duplicate contributed feature: "+feature);
            String status=header.contains("status")?row[header.indexOf("status")]:"complete";String method=row[header.indexOf("method")];double count=number(row[header.indexOf("n_instruments")]);
            boolean exhausted=status.equals("not_estimable");
            if("not_estimable".equals(definition.get("reported_status"))&&!exhausted)problems.add("A globally unestimable contribution cannot contain a completed estimable row: "+feature);
            if(exhausted) {
                if(!header.contains("reason")||row[header.indexOf("reason")].isBlank()||row[header.indexOf("reason")].length()>4000||row[header.indexOf("reason")].chars().anyMatch(Character::isISOControl))problems.add("Completed unestimable contribution needs a bounded public reason: "+feature);
                if(!missing(row[header.indexOf("beta")])||!missing(row[header.indexOf("se")])||!missing(row[header.indexOf("p_value")]))problems.add("Unestimable contribution must leave beta, se and P unavailable: "+feature);
                if(!method.equals(primary)&&!method.equals("wald-ratio"))problems.add("Unknown contributed primary estimator: "+feature);
            } else {
                if(!Set.of("complete","ok","tested").contains(status))problems.add("Incomplete source row cannot be reused as a completed test: "+feature);
                double beta=number(row[header.indexOf("beta")]),se=number(row[header.indexOf("se")]),p=number(row[header.indexOf("p_value")]);
                if(!Double.isFinite(beta)||!Double.isFinite(se)||se<=0||!Double.isFinite(p)||p<0||p>1)problems.add("Completed MR contribution needs a finite estimate, positive SE and valid P: "+feature);
                if(count<1||!method.equals(count==1?"wald-ratio":primary))problems.add("Contributed method/instrument count disagrees with the declared primary and Wald fallback: "+feature);
            }
            if(!Double.isFinite(count)||count<0||count!=Math.rint(count)||count>Integer.MAX_VALUE)problems.add("Invalid contributed instrument count: "+feature);
        }
        if(!observed.equals(declared))problems.add("Contributed rows must cover scope.feature_ids exactly; absent rows are not completed or null tests");
        return problems;
    }

    static Loaded load(Context c)throws IOException {
        var definitions=StudySuppliedResultPolicy.resolve(StudyResultTables.analysis(c)).stream().filter(d->"mendelian_randomization".equals(d.get("kind"))&&"contribution".equals(d.get("role"))).toList();
        if(definitions.isEmpty())return new Loaded(List.of(),Set.of(),List.of(),Map.of());
        var selected=new TreeMap<String,StudyBioinformatics.Feature>();for(var f:StudyBioinformatics.mrFeatures(c))selected.put(f.id(),f);
        var known=new TreeSet<String>();var primary=StudyPrimaryResults.official(c);
        if(primary!=null&&primary.table().header().contains("feature_id"))for(String[] row:primary.table().rows())if(primary.selectedRow(primary.table(),row))known.add(row[primary.table().column("feature_id")]);
        var covered=new TreeSet<String>();var contributions=new TreeMap<String,List<Map<String,Object>>>();var problemsByFeature=new TreeMap<String,List<String>>();var audit=new ArrayList<Object>();var needs=new ArrayList<StudyNeed>();
        String target=text(StudyBioinformatics.hypothesis(c).get("outcome")),estimator=text(StudyMrMultiplicity.policy(c).get("primary_estimator"));
        for(var definition:definitions) {
            String id=text(definition.get("id"));var scope=map(definition.get("scope"));var declared=list(scope.get("feature_ids")).stream().map(StudyPublicEvidence::text).toList();
            covered.addAll(declared);var issues=new ArrayList<String>();var sourceAudit=new LinkedHashMap<String,Object>();sourceAudit.put("supplied_id",id);sourceAudit.put("declared_features",declared);sourceAudit.put("outside_selected_features",declared.stream().filter(f->!selected.containsKey(f)).toList());
            if(primary==null)issues.add("The primary association-selection source is unavailable; external MR cannot invent association eligibility");
            if(!known.containsAll(declared))issues.add("Contribution declares a feature absent from the registered primary contrast");
            if(target.isBlank()||!target.equals(scope.get("outcome")))issues.add("Contribution outcome must match the declared hypothesis outcome exactly");
            if(!estimator.equals(map(map(definition.get("method")).get("parameters")).get("primary_estimator")))issues.add("Contribution primary estimator differs from the registered joint MR policy");
            var producers=new ArrayList<Path>();
            for(Path directory:c.dependencyDirectories().values())if(Files.isRegularFile(directory.resolve("manifest.yaml"))) {
                var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
                if("supplied_contribution".equals(m.get("operation_type"))&&c.operation().get("hypothesis").equals(m.get("hypothesis"))&&id.equals(map(m.get("details")).get("artifact_id")))producers.add(directory);
            }
            Map<String,Object> metadata=Map.of();DelimitedData table=null;
            if(producers.size()!=1)issues.add("One verified supplied_contribution producer is required for "+id);
            else {
                Path directory=producers.get(0);var manifest=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));var hashes=map(manifest.get("output_sha256"));
                try {
                    Path path=StudyFindingInventory.verified(directory,"supplied-analysis.yaml",text(hashes.get("supplied-analysis.yaml")));metadata=StudyYaml.loadArtifact(path);
                    for(var entry:hashes.entrySet())StudyFindingInventory.verified(directory,entry.getKey(),text(entry.getValue()));
                    if(!"complete".equals(manifest.get("state"))||!Boolean.TRUE.equals(metadata.get("contribution_reuse_eligible")))throw new IOException("Import/source family is incomplete; the declared coverage remains reserved rather than refitted");
                    if(!"contribution".equals(metadata.get("role"))||!"mendelian_randomization".equals(metadata.get("kind"))||!id.equals(metadata.get("artifact_id")))throw new IOException("Producer role, kind or artifact identity does not match the registered contribution");
                    if(!scope.equals(map(metadata.get("scope")))||!map(definition.get("provenance")).equals(map(metadata.get("provenance")))||!map(definition.get("method")).equals(map(metadata.get("method"))))throw new IOException("Supplied metadata does not match the current registered contribution");
                    var original=map(map(metadata.get("source_files")).get("results"));String expected=text(map(definition.get("provenance")).get("results_sha256"));
                    Path snapshot=StudyFindingInventory.verified(directory,text(original.get("snapshot")),text(hashes.get(text(original.get("snapshot")))));
                    if(!expected.equalsIgnoreCase(text(original.get("sha256")))||!expected.equalsIgnoreCase(FollowupSupport.hash(snapshot)))throw new IOException("Supplied source snapshot does not match the registered input version");
                    if(list(metadata.get("upstream_imports")).stream().map(StudyPublicEvidence::map).anyMatch(upstream->!"complete".equals(upstream.get("state"))))throw new IOException("An explicitly declared supplied dependency remains incomplete");
                    String name=text(metadata.get("canonical_results"));Path results=StudyFindingInventory.verified(directory,name,text(hashes.get(name)));
                    if(!FollowupSupport.hash(results).equals(metadata.get("canonical_sha256")))throw new IOException("Canonical contribution digest differs from its import receipt");
                    table=DelimitedData.readResult(results);issues.addAll(validateTable(table.header(),table.rows(),definition));
                    sourceAudit.put("producer_manifest_sha256",FollowupSupport.hash(directory.resolve("manifest.yaml")));sourceAudit.put("canonical_sha256",FollowupSupport.hash(results));
                } catch(IOException|IllegalArgumentException invalid){issues.add(String.valueOf(invalid.getMessage()));}
            }
            sourceAudit.put("problems",issues);sourceAudit.put("status",issues.isEmpty()?"validated_external_contribution":"unresolved_reserved_coverage");audit.add(sourceAudit);
            if(!issues.isEmpty()) {
                String reason=String.join("; ",issues);needs.add(StudyNeed.review("mr-contribution:"+c.operation().get("hypothesis")+":"+id,"Resolve the supplied MR contribution",reason,true));
                for(String feature:declared)if(selected.containsKey(feature))problemsByFeature.computeIfAbsent(feature,ignored->new ArrayList<>()).add(id+": "+reason);
                continue;
            }
            if(table==null)throw new IOException("Validated MR contribution has no canonical table");
            for(String[] row:table.rows()) {
                String feature=row[table.column("feature_id")];if(!selected.containsKey(feature))continue;
                var record=new LinkedHashMap<String,Object>();String status=table.header().contains("status")?row[table.column("status")]:"complete";
                record.put("status",status.equals("not_estimable")?"not_estimable":"complete");
                var source=new LinkedHashMap<String,Object>();source.put("kind","supplied_mr_source_context");source.put("supplied_id",id);source.put("analysis_id",map(definition.get("provenance")).get("analysis_id"));source.put("source_result_sha256",map(map(metadata.get("source_files")).get("results")).get("sha256"));
                source.put("source_family_id",map(metadata.get("resolved_family")).get("id"));source.put("source_testing_denominator",map(metadata.get("resolved_family")).getOrDefault("tested_count","not_declared"));source.put("source_adjustment",map(metadata.get("resolved_family")).getOrDefault("adjustment","PROVIDED"));source.put("source_adjusted_p",table.header().contains("adjusted_p")?row[table.column("adjusted_p")]:"NA");source.put("scope",scope);source.put("external_method",definition.get("method"));source.put("required_manuscript_caveat",metadata.get("required_manuscript_caveat"));
                source.put("canonical_sha256",sourceAudit.get("canonical_sha256"));source.put("producer_manifest_sha256",sourceAudit.get("producer_manifest_sha256"));record.put("supplied_sources",List.of(source));record.put("analysis_id",source.get("analysis_id"));
                int count=(int)number(row[table.column("n_instruments")]);
                if(status.equals("not_estimable"))record.put("reason",row[table.column("reason")]);
                else record.put("mr_primary",Map.of("requested_estimator",row[table.column("method")],"estimate",Double.toString(number(row[table.column("beta")])) ,"standard_error",Double.toString(number(row[table.column("se")])),"causal_p_value",Double.toString(number(row[table.column("p_value")])) ,"instruments",Integer.toString(count)));
                record.put("sources",sources(feature,text(source.get("analysis_id")),scope));record.put("feature_id",feature);record.put("n_instruments",count);contributions.computeIfAbsent(feature,ignored->new ArrayList<>()).add(record);
            }
        }
        var records=new ArrayList<Object>();var dispositions=new ArrayList<String[]>();
        for(String feature:covered) {
            if(!selected.containsKey(feature)){dispositions.add(new String[]{feature,"outside_current_MR_selection","","","External evidence retained; no MR eligibility inferred"});continue;}
            var aliases=contributions.getOrDefault(feature,List.of());var issues=problemsByFeature.getOrDefault(feature,List.of());
            boolean agree=!aliases.isEmpty()&&aliases.stream().allMatch(a->a.get("analysis_id").equals(aliases.get(0).get("analysis_id"))&&a.get("status").equals(aliases.get(0).get("status"))&&declaration(a).equals(declaration(aliases.get(0)))&&map(a.get("sources")).equals(map(aliases.get(0).get("sources")))&&a.get("n_instruments").equals(aliases.get(0).get("n_instruments"))&&map(a.get("mr_primary")).equals(map(aliases.get(0).get("mr_primary")))&&text(a.get("reason")).equals(text(aliases.get(0).get("reason"))));
            var record=new LinkedHashMap<String,Object>();
            if(issues.isEmpty()&&agree)record.putAll(aliases.get(0));
            else {
                String reason=issues.isEmpty()?"Overlapping supplied primary sources or estimates disagree for this feature; no lowest-P source or native fallback was selected":String.join("; ",issues);
                record.put("feature_id",feature);record.put("status","waiting_for_input");record.put("reason",reason);record.put("sources",sources(feature,"unresolved-contribution-"+feature,Map.of("exposure","unresolved","outcome",target)));
                if(issues.isEmpty())needs.add(StudyNeed.review("mr-contribution-conflict:"+c.operation().get("hypothesis")+":"+StudyFindingInventory.hash(feature).substring(0,20),"Resolve overlapping supplied primary MR evidence",reason,true));
            }
            record.put("supplied_sources",aliases.stream().flatMap(a->list(a.get("supplied_sources")).stream()).toList());record.put("pair_id","supplied-"+StudyFindingInventory.hash(feature).substring(0,24));record.put("supplied_coverage_reserved",true);record.put("reused_supplied_result",!"waiting_for_input".equals(record.get("status")));record.put("analysis_attempted",false);record.put("analysis_executed_by_jlinalg",false);record.put("execution_origin","externally_produced");record.put("primary_feature_selection",selected.get(feature).document());record.put("interpretation",CAVEAT);records.add(record);
            dispositions.add(new String[]{feature,text(record.get("status")),aliases.stream().map(a->text(a.get("analysis_id"))).distinct().sorted().collect(java.util.stream.Collectors.joining(";")),Integer.toString(aliases.size()),text(record.get("reason"))});
        }
        FollowupSupport.table(c.outputDirectory().resolve("mr-completion-features.tsv"),List.of("feature_id","disposition","source_analysis_ids","source_aliases","reason"),dispositions);
        var summary=new LinkedHashMap<String,Object>();summary.put("sources",audit);summary.put("registered_features",covered.size());summary.put("selected_supplied_features",records.size());summary.put("reused_estimable_features",records.stream().map(StudyPublicEvidence::map).filter(r->r.get("status").equals("complete")).count());summary.put("required_manuscript_caveat",CAVEAT);summary.put("source_adjustments_overwritten",false);summary.put("joint_adjustment_scope","The registered native-completion MR family, not the original external testing families");write(c.outputDirectory().resolve("mr-completion.yaml"),summary);
        return new Loaded(records,Set.copyOf(covered),needs,summary);
    }
    private static Map<String,Object> declaration(Map<String,Object> record) {
        var source=map(list(record.get("supplied_sources")).get(0));var scope=new TreeMap<>(map(source.get("scope")));scope.remove("feature_ids");
        var parameters=new TreeMap<>(map(map(source.get("external_method")).get("parameters")));parameters.remove("reuse_rationale");
        return Map.of("scope",scope,"parameters",parameters);
    }
    private static Map<String,Object> sources(String feature,String analysis,Map<String,Object> scope) {
        var exposure=new LinkedHashMap<String,Object>();var outcome=new LinkedHashMap<String,Object>();
        for(String key:List.of("genome_build","ancestry","tissue","assay"))if(scope.containsKey(key)){exposure.put(key,scope.get(key));outcome.put(key,scope.get(key));}
        exposure.put("source_id","supplied-analysis:"+analysis);exposure.put("feature_id",feature);exposure.put("trait",scope.get("exposure"));outcome.put("source_id","supplied-analysis:"+analysis);outcome.put("trait",scope.get("outcome"));
        return Map.of("feature_id",feature,"exposure",exposure,"outcome",outcome);
    }
    private static boolean missing(String value){return Set.of("","NA","NaN",".").contains(value);}
    private static double number(String value){try{return Double.parseDouble(value);}catch(NumberFormatException invalid){return Double.NaN;}}
}
