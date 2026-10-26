/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Allocate bounded literature retrieval after an uncapped, provisional impact review. */
final class StudyLiteraturePriority {
    static final String CAVEAT = "Provisional allocation of literature retrieval, before source appraisal. This review does not establish novelty, mechanism, causality or clinical value and cannot change statistical eligibility, primary results, FDR or final manuscript selection. Missing prior literature is an uncertainty to investigate, not evidence against a finding. Batch comparisons are advisory and do not prove a globally optimal ranking.";
    private static final List<String> CAVEATS = List.of("pre_literature_uncertainty", "source_family_statistics", "retrieval_not_manuscript_selection");
    record Selection(List<StudyBioinformatics.Feature> features, Map<String,Object> report, String need) { }
    private final StudyAssistance assistance;
    StudyLiteraturePriority(StudyAssistance assistance) { this.assistance=assistance; }

    Selection select(Context c,List<StudyBioinformatics.Feature> inventory,int cap)throws IOException {
        Path root=c.outputDirectory().resolve("literature-priority");Files.createDirectories(root);
        String configured=text(StudyBioinformatics.config(c).getOrDefault("literature_selection","auto"));
        if(!Set.of("auto","impact","p_value").contains(configured))throw new IllegalArgumentException("literature_selection must be auto, impact or p_value");
        var config=map(c.study().get("assistance"));boolean impact=configured.equals("impact")||configured.equals("auto")&&Boolean.TRUE.equals(config.get("enabled"))&&Boolean.TRUE.equals(config.get("impact"));
        var report=new LinkedHashMap<String,Object>();report.put("schema_version",1);report.put("configured_mode",configured);report.put("eligible_targets",inventory.size());report.put("review_limit",cap);report.put("required_manuscript_caveat",CAVEAT);
        if(inventory.size()<=cap||!impact){
            var selected=List.copyOf(inventory.subList(0,Math.min(cap,inventory.size())));
            report.put("mode",inventory.size()<=cap?"all_eligible_within_cap":"source_p_value");report.put("status","complete");report.put("AI_performed",false);report.put("selected_feature_ids",selected.stream().map(StudyBioinformatics.Feature::id).toList());
            report.put("selection_complete",true);report.put("assessed_targets",0);report.put("new_interactions",0);report.put("provider_requests_sent_this_attempt",0);report.put("reused_interactions",0);
            write(root.resolve("selection.yaml"),report);return new Selection(selected,report,"");
        }
        report.put("mode","provisional_AI_impact");report.put("AI_performed",false);
        var features=new LinkedHashMap<String,StudyBioinformatics.Feature>();var candidates=new ArrayList<Map<String,Object>>();
        var effects=effects(c);StringBuilder projection=new StringBuilder();
        for(var feature:inventory.stream().sorted(Comparator.comparing(StudyBioinformatics.Feature::id)).toList()){
            String id="lit-"+StudyFindingInventory.hash(StudyAssistance.json(List.of(text(c.operation().get("hypothesis")),feature.id())));
            if(features.putIfAbsent(id,feature)!=null)throw new IOException("Duplicate literature candidate identity");
            for(String value:List.of(feature.id(),feature.symbol(),feature.gene()))if(value.length()>4000||value.chars().anyMatch(Character::isISOControl))throw new IOException("Literature candidate identifiers must be bounded aggregate feature labels");
            var row=new LinkedHashMap<String,Object>();row.put("candidate_id",id);row.put("feature_id",feature.id());row.put("gene_symbol",feature.symbol());row.put("gene_id",feature.gene());row.put("p_value",feature.p());row.put("adjusted_p",feature.fdr());
            row.put("source_scope",feature.selection().isEmpty()?"association_primary":Set.of("association_primary","separate_source_families","rare_primary_gene_context","rare_primary_projection").contains(text(feature.selection().get("kind")))?text(feature.selection().get("kind")):"source_family_context");
            if(feature.selection().isEmpty()&&effects.containsKey(feature.id()))row.put("signed_summary",effects.get(feature.id()));
            row.put("evidence_ids",List.of("eligible-source-projection"));row.put("caveat_ids",CAVEATS);
            candidates.add(row);projection.append(StudyAssistance.json(row)).append('\n');
        }
        Files.writeString(root.resolve("candidates.jsonl"),projection);String sourceHash=FollowupSupport.hash(root.resolve("candidates.jsonl"));report.put("candidate_projection_sha256",sourceHash);report.put("candidate_order","feature_id; no P-value preselection");
        int batchSize=StudyBioinformatics.integer(config,"impact_batch_size",8,2,1000),maxChars=StudyBioinformatics.integer(config,"impact_max_payload_chars",120000,5000,2000000),maxCalls=StudyBioinformatics.integer(config,"impact_max_calls",100,1,10000);
        var nodes=new ArrayList<Object>();var assessed=new LinkedHashMap<String,Map<String,Object>>();var budget=new int[]{0,0,0};
        if(phenotypes(c).isEmpty())return held(root,report,nodes,assessed,budget,"Provisional literature allocation requires an explicit public phenotype label; no hypothesis description or private notes were substituted");
        var batches=batches(c,candidates,"assess",cap,sourceHash,batchSize,maxChars);
        if(batches==null)return held(root,report,nodes,assessed,budget,"A complete provisional candidate exceeds impact_max_payload_chars; no candidate was truncated");
        int number=0;
        for(var batch:batches){String node=String.format(java.util.Locale.ROOT,"assess-%05d",++number);var payload=payload(c,batch,"assess",cap,sourceHash);
            var answer=answer(c,root,node,payload,config,maxCalls,budget,nodes);
            if(answer==null)return held(root,report,nodes,assessed,budget,"Complete provisional candidate assessment requires a usable response or additional impact interaction budget; sealed responses remain reusable");
            if(answer.get("decision").equals("deferred"))return held(root,report,nodes,assessed,budget,"The advisor deferred provisional literature allocation: "+text(answer.get("reason")));
            for(Object raw:list(answer.get("assessments"))){var row=map(raw);assessed.put(text(row.get("candidate_id")),row);}
        }
        var queue=new ArrayList<Map<String,Object>>();for(var row:candidates){var compact=new LinkedHashMap<>(row);compact.put("assessment",assessed.get(text(row.get("candidate_id"))));queue.add(compact);}
        int round=0;
        while(!queue.isEmpty()){
            round++;batches=batches(c,queue,"select",cap,sourceHash,Math.max(cap+1,Math.max(batchSize,cap*2)),maxChars);
            if(batches==null)return held(root,report,nodes,assessed,budget,"A complete assessed candidate exceeds the comparative payload budget; no assessment was truncated");
            var advancing=new ArrayList<Map<String,Object>>();
            for(var batch:batches){String node=String.format(java.util.Locale.ROOT,"select-%05d-%05d",round,++number);var payload=payload(c,batch,"select",cap,sourceHash);
                var answer=answer(c,root,node,payload,config,maxCalls,budget,nodes);
                if(answer==null)return held(root,report,nodes,assessed,budget,"Comparative literature allocation requires a usable response or additional impact interaction budget; completed comparisons remain reusable");
                if(answer.get("decision").equals("deferred"))return held(root,report,nodes,assessed,budget,"The advisor deferred comparative literature allocation: "+text(answer.get("reason")));
                for(Object id:list(answer.get("selected_ids")))advancing.add(batch.stream().filter(row->id.equals(row.get("candidate_id"))).findFirst().orElseThrow());
            }
            if(batches.size()==1){var chosen=advancing.stream().map(row->features.get(text(row.get("candidate_id")))).toList();finish(report,nodes,assessed,budget);report.put("status","complete");report.put("selection_complete",true);report.put("selected_feature_ids",chosen.stream().map(StudyBioinformatics.Feature::id).toList());write(root.resolve("selection.yaml"),report);return new Selection(chosen,report,"");}
            if(advancing.size()>=queue.size())return held(root,report,nodes,assessed,budget,"Payload budget cannot compare more than the review cap at once; increase impact_max_payload_chars rather than discard an unexamined subset");
            queue=advancing;
        }
        throw new IOException("Provisional literature ranking lost its candidate queue");
    }

    private Map<String,Object> answer(Context c,Path root,String node,Map<String,Object> payload,Map<String,Object> config,int maxCalls,int[] budget,List<Object> nodes)throws IOException {
        var requestConfig=new LinkedHashMap<String,Object>();for(String key:List.of("enabled","impact","provider","model","endpoint","auth","api_key_env","oauth_token_env","impact_max_output_tokens","response_files"))if(config.containsKey(key))requestConfig.put(key,config.get(key));
        String identity=StudyFindingInventory.hash(StudyAssistance.json(Map.of("payload",payload,"assistance",requestConfig,"data_policy",map(map(c.study().get("project")).get("data_policy")))));
        Path directory=root.resolve("comparisons/"+node);var saved=reuse(c,node,identity,payload,directory);boolean reused=saved!=null;
        if(saved==null){
            if(budget[0]>=maxCalls){nodes.add(Map.of("node",node,"state","interaction_budget","candidate_count",list(payload.get("candidates")).size()));return null;}
            budget[0]++;var operation=new LinkedHashMap<>(c.operation());operation.put("id",c.operation().get("id")+"-literature-"+node);operation.put("type","impact_assessment");
            var result=assistance.execute(new Context(c.project(),directory,operation,c.study(),c.plan(),c.dependencyDirectories()),payload);
            if(Boolean.TRUE.equals(result.details().get("request_sent")))budget[2]++;
            if(result.state()!=State.COMPLETE||!(result.details().get("literature_prioritization") instanceof Map<?,?>)){
                nodes.add(Map.of("node",node,"state",result.state().value(),"candidate_count",list(payload.get("candidates")).size(),"details",result.details()));return null;}
            saved=validate(payload,map(result.details().get("literature_prioritization")));write(directory.resolve("node-result.yaml"),Map.of("identity",identity,"answer",saved));
        }else budget[1]++;
        nodes.add(Map.of("node",node,"state","complete","reused",reused,"candidate_ids",list(payload.get("candidates")).stream().map(raw->map(raw).get("candidate_id")).toList(),"answer",saved));return saved;
    }
    private static Map<String,Object> reuse(Context c,String node,String identity,Map<String,Object> payload,Path target)throws IOException {
        Path runs=c.project().resolve("runs");if(!Files.isDirectory(runs))return null;String prefix="literature-priority/comparisons/"+node+"/";
        try(var paths=Files.walk(runs)){for(Path path:paths.filter(p->p.getFileName().toString().equals("manifest.yaml")).sorted().toList()){
            var manifest=StudyYaml.loadArtifact(path);if(!"literature_search".equals(manifest.get("operation_type"))||!text(c.operation().get("hypothesis")).equals(text(manifest.get("hypothesis"))))continue;
            var hashes=map(manifest.get("output_sha256"));String name=prefix+"node-result.yaml";if(!hashes.containsKey(name))continue;
            var saved=StudyYaml.loadArtifact(StudyFindingInventory.verified(path.getParent(),name,text(hashes.get(name))));if(!identity.equals(saved.get("identity")))continue;var answer=validate(payload,map(saved.get("answer")));if("deferred".equals(answer.get("decision")))continue;
            for(var entry:hashes.entrySet())if(entry.getKey().startsWith(prefix)){Path source=StudyFindingInventory.verified(path.getParent(),entry.getKey(),text(entry.getValue())),destination=target.resolve(entry.getKey().substring(prefix.length())).normalize();if(!destination.startsWith(target))throw new IOException("Unsafe literature comparison cache path");Files.createDirectories(destination.getParent());Files.copy(source,destination);}
            write(target.resolve("reuse.yaml"),Map.of("source_manifest_sha256",FollowupSupport.hash(path),"new_provider_call",false));return answer;
        }}return null;
    }
    private static Selection held(Path root,Map<String,Object> report,List<Object> nodes,Map<String,Map<String,Object>> assessed,int[] budget,String reason)throws IOException {
        finish(report,nodes,assessed,budget);report.put("status","waiting_for_input");report.put("selection_complete",false);report.put("selected_feature_ids",List.of());report.put("need",reason);write(root.resolve("selection.yaml"),report);return new Selection(List.of(),report,reason);
    }
    private static void finish(Map<String,Object> report,List<Object> nodes,Map<String,Map<String,Object>> assessed,int[] budget){report.put("AI_performed",budget[2]>0||budget[1]>0||nodes.stream().map(StudyPublicEvidence::map).anyMatch(n->"complete".equals(n.get("state"))));report.put("assessed_targets",assessed.size());report.put("assessments",List.copyOf(assessed.values()));report.put("comparisons",nodes);report.put("new_interactions",budget[0]);report.put("provider_requests_sent_this_attempt",budget[2]);report.put("reused_interactions",budget[1]);report.put("scientific_appraisal","pending");}
    private static List<List<Map<String,Object>>> batches(Context c,List<Map<String,Object>> rows,String phase,int cap,String source,int count,int chars){var out=new ArrayList<List<Map<String,Object>>>();var batch=new ArrayList<Map<String,Object>>();for(var row:rows){var trial=new ArrayList<>(batch);trial.add(row);if(trial.size()>count||StudyAssistance.json(payload(c,trial,phase,cap,source)).length()>chars){if(batch.isEmpty())return null;out.add(batch);batch=new ArrayList<>();}batch.add(row);if(StudyAssistance.json(payload(c,batch,phase,cap,source)).length()>chars)return null;}if(!batch.isEmpty())out.add(batch);return out;}
    private static Map<String,Object> payload(Context c,List<Map<String,Object>> candidates,String phase,int cap,String source){
        return Map.of("literature_priority_protocol_version",1,"phase",phase,"review_limit",cap,"hypothesis",text(c.operation().get("hypothesis")),"phenotype_labels",phenotypes(c),"molecular_field_labels",labels(StudyBioinformatics.config(c).get("literature_terms")),"candidates",candidates,"source_catalog",Map.of("eligible-source-projection",Map.of("sha256",source,"kind","verified_primary_eligibility_projection")),"required_caveat_ids",CAVEATS,"scope",CAVEAT);
    }
    private static List<String> phenotypes(Context c){var values=new LinkedHashSet<String>();for(Object raw:list(c.study().get("hypotheses"))){var h=map(raw);if(text(c.operation().get("hypothesis")).equals(text(h.get("id")))&&!text(h.get("outcome")).isBlank())values.addAll(labels(List.of(h.get("outcome"))));}values.addAll(labels(StudyBioinformatics.config(c).get("phenotype_terms")));return List.copyOf(values);}
    private static List<String> labels(Object raw){var result=new LinkedHashSet<String>();for(Object value:list(raw)){String label=text(value).strip();if(label.isBlank()||label.length()>150||label.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Provisional context needs bounded public phenotype/field labels");result.add(label);}return List.copyOf(result);}
    private static Map<String,Map<String,Object>> effects(Context c)throws IOException {
        var result=new LinkedHashMap<String,Map<String,Object>>();StudyPrimaryResults.Selection primary;
        try{primary=StudyPrimaryResults.official(c);}catch(StudyPrimaryResults.Unavailable missing){return result;}
        if(primary==null)return result;var table=primary.table();if(!table.header().contains("feature_id"))return result;
        for(String[] row:table.rows())if(primary.selectedRow(table,row)){var values=new LinkedHashMap<String,Object>();for(String field:List.of("beta","se","ci_lower","ci_upper","n","n_cohorts","i_squared","q_p_value"))if(table.header().contains(field))try{double n=Double.parseDouble(row[table.column(field)]);if(Double.isFinite(n))values.put(field,n);}catch(NumberFormatException unavailable){/* source missingness stays unavailable */}if(!values.isEmpty())result.put(row[table.column("feature_id")],values);}
        return result;
    }
    static Map<String,Object> validate(Map<String,Object> payload,Map<String,Object> answer){
        if(!answer.keySet().equals(Set.of("decision","assessments","selected_ids","reason")))throw new IllegalArgumentException("Literature-priority response fields do not match the contract");
        bounded(answer.get("reason"),6000,"Literature-priority reason");String decision=text(answer.get("decision")),phase=text(payload.get("phase"));var assessments=list(answer.get("assessments"));var selected=list(answer.get("selected_ids"));
        if(decision.equals("deferred")){if(!assessments.isEmpty()||!selected.isEmpty())throw new IllegalArgumentException("Deferred priority responses cannot promote partial results");return new LinkedHashMap<>(answer);}
        var candidates=new LinkedHashMap<String,Map<String,Object>>();for(Object raw:list(payload.get("candidates"))){var row=map(raw);candidates.put(text(row.get("candidate_id")),row);}
        if(phase.equals("assess")){
            if(!decision.equals("assessed")||!selected.isEmpty())throw new IllegalArgumentException("Assess every target before selecting literature targets");var seen=new LinkedHashSet<String>();
            for(Object raw:assessments){var row=map(raw);if(!row.keySet().equals(Set.of("candidate_id","priority","reason","missing_evidence","evidence_ids","caveat_ids")))throw new IllegalArgumentException("Priority assessment fields do not match the contract");String id=text(row.get("candidate_id"));var candidate=candidates.get(id);if(candidate==null||!seen.add(id))throw new IllegalArgumentException("Unknown or duplicate literature candidate");if(!Set.of("high","medium","low","unknown").contains(row.get("priority")))throw new IllegalArgumentException("Unknown provisional priority");bounded(row.get("reason"),400,"Priority assessment reason");bounded(row.get("missing_evidence"),250,"Missing evidence");if(!list(row.get("evidence_ids")).equals(List.of("eligible-source-projection")))throw new IllegalArgumentException("Priority assessments must bind the supplied eligibility projection");var caveats=list(row.get("caveat_ids"));if(caveats.size()!=CAVEATS.size()||!new LinkedHashSet<>(caveats).equals(new LinkedHashSet<>(CAVEATS)))throw new IllegalArgumentException("All provisional caveats must be retained");}
            if(!seen.equals(candidates.keySet()))throw new IllegalArgumentException("Every provisional literature target must be assessed");
        }else if(phase.equals("select")){
            int expected=Math.min(((Number)payload.get("review_limit")).intValue(),candidates.size());
            if(!decision.equals("selected")||!assessments.isEmpty()||selected.size()!=expected||new LinkedHashSet<>(selected).size()!=selected.size()||!candidates.keySet().containsAll(selected))throw new IllegalArgumentException("Literature allocation needs exactly the available review slots, unique supplied IDs, or an explicit deferral");
        }else throw new IllegalArgumentException("Unknown literature-priority phase");
        return new LinkedHashMap<>(answer);
    }
    private static void bounded(Object value,int limit,String label){if(!(value instanceof String s)||s.isBlank()||s.length()>limit||s.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException(label+" must be bounded nonblank text");}
    static String instructions(){return "Allocate literature retrieval using the supplied provisional protocol. "+CAVEAT+" In phase assess, return decision assessed and one assessment for every candidate, including its exact candidate_id, priority high/medium/low/unknown, a concise reason (<=400 characters), missing_evidence (<=250 characters), evidence_ids [eligible-source-projection] and every required_caveat_id. selected_ids must be empty. Assess relevance, reliability, information gain and evidence gaps; never invent literature, biological functions or an interpretation of an unspecified effect scale. In phase select, return decision selected, assessments [], and exactly min(review_limit,candidate_count) unique supplied selected_ids ordered by comparative retrieval priority. All prior assessments remain available; unreviewed or uncertain targets can deserve retrieval. Explain cross-candidate comparisons in reason. If unable to complete either phase, use decision deferred with empty assessments/selected_ids and explain why. This is retrieval allocation; final manuscript ranking remains separate. Return only the required JSON.";}
    static Map<String,Object> schema(){
        var string=Map.of("type","string");var ids=Map.of("type","array","items",string);
        var item=Map.of("type","object","additionalProperties",false,"required",List.of("candidate_id","priority","reason","missing_evidence","evidence_ids","caveat_ids"),"properties",Map.of("candidate_id",string,"priority",Map.of("type","string","enum",List.of("high","medium","low","unknown")),"reason",string,"missing_evidence",string,"evidence_ids",ids,"caveat_ids",ids));
        return Map.of("type","object","additionalProperties",false,"required",List.of("decision","assessments","selected_ids","reason"),"properties",Map.of("decision",Map.of("type","string","enum",List.of("assessed","selected","deferred")),"assessments",Map.of("type","array","items",item),"selected_ids",ids,"reason",string));
    }
}
