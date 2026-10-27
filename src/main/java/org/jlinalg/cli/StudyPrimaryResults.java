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
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudyPrimaryPolicy;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** One scoped, producer-verified primary identity shared by statistical and editorial consumers. */
final class StudyPrimaryResults {
    private StudyPrimaryResults() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        private final transient Map<String,Object> receipt;
        Unavailable(String reason,Map<String,Object> value){super(reason);receipt=Map.copyOf(value);}
        Map<String,Object> receipt(){return receipt;}
    }
    record Producer(Path directory,Map<String,Object> manifest,String type,String id,String hypothesis) { }
    record Source(Producer producer,String dataset,Path path,String sha256) { }
    record Selection(Source source,DelimitedData original,DelimitedData table,Map<String,Double> probabilities,
            Map<String,Double> adjusted,Map<String,Object> family,String adjustment,String term,
            Map<String,Object> receipt) {
        boolean owns(Path path){return source.path().toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize());}
        boolean selectedRow(DelimitedData data,String[] row){return !data.header().contains("term")||row[data.column("term")].equals(term);}
        boolean completeObservedFamily(){return family.isEmpty()||"complete".equals(family.get("coverage"))&&(!(family.get("tested_count") instanceof Number n)||n.longValue()<=original.rows().size());}
    }
    static Map<String,Object> policy(Context c){return StudyPrimaryPolicy.resolve(map(StudyResultTables.analysis(c).get("primary_result")));}

    static List<Producer> producers(Context c)throws IOException {
        String hypothesis=text(c.operation().get("hypothesis"));var result=new ArrayList<Producer>();var seen=new LinkedHashSet<Path>();
        for(Path raw:c.dependencyDirectories().values()){
            Path directory=raw.toAbsolutePath().normalize();if(!seen.add(directory)||!Files.isRegularFile(directory.resolve("manifest.yaml")))continue;
            var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));String type=text(m.get("operation_type"));if(!Set.of("meta_analysis","discovery_meta_analysis","association","validate_harmonize").contains(type))continue;
            String sourceHypothesis=text(m.get("hypothesis")),id=text(m.get("operation_id"));
            if(sourceHypothesis.isBlank()||id.isBlank())throw new IOException("Primary result producer needs an explicit hypothesis and operation_id: "+directory);
            if(!hypothesis.equals(sourceHypothesis))continue;
            result.add(new Producer(directory,m,type,id,sourceHypothesis));
        }
        return result;
    }
    static Map<String,Source> cohorts(Context c)throws IOException {
        return cohorts(c,producers(c),"",false);
    }
    static Map<String,Source> unfiltered(Context c)throws IOException {
        return cohorts(c,producers(c),"",true);
    }
    private static Map<String,Source> cohorts(Context c,List<Producer> producers,String operationId,boolean unfiltered)throws IOException {
        var selected=selected(c);var candidates=new TreeMap<String,List<Source>>();
        String registeredDataset=text(policy(c).get("dataset_id"));
        for(var producer:producers){
            if(!producer.type().equals("association")||!operationId.isBlank()&&!operationId.equals(producer.id()))continue;
            if("complete".equals(producer.manifest().get("state"))&&!Boolean.FALSE.equals(map(producer.manifest().get("details")).get("primary_selection_eligible")))continue;
            var hashes=map(producer.manifest().get("output_sha256"));
            var sourceIds=new LinkedHashSet<String>();for(String name:hashes.keySet())if(name.matches("standard-results/[^/]+\\.tsv"))sourceIds.add(Path.of(name).getFileName().toString().replaceFirst("\\.tsv$",""));
            if(!registeredDataset.isBlank()&&!sourceIds.isEmpty()&&!sourceIds.contains(registeredDataset))continue;
            if(!selected.isEmpty()&&!sourceIds.isEmpty()&&java.util.Collections.disjoint(selected,sourceIds))continue;
            throw unavailable(c,policy(c),"incomplete_primary_association","A scoped association producer is incomplete or ineligible; partial tests and other cohorts cannot replace its registered complete family");
        }
        for(var producer:producers){if(!Set.of("association","validate_harmonize").contains(producer.type())||!"complete".equals(producer.manifest().get("state"))||!operationId.isBlank()&&!operationId.equals(producer.id()))continue;
            var hashes=map(producer.manifest().get("output_sha256"));var ids=new LinkedHashSet<String>();
            for(String name:hashes.keySet())if(name.matches(unfiltered?"pre-effect-qc-results/[^/]+\\.tsv":"(?:standard-results|post-effect-qc-results)/[^/]+\\.tsv"))ids.add(Path.of(name).getFileName().toString().replaceFirst("\\.tsv$",""));
            for(String id:ids){if(!selected.isEmpty()&&!selected.contains(id))continue;String post="post-effect-qc-results/"+id+".tsv",standard="standard-results/"+id+".tsv",name=unfiltered?"pre-effect-qc-results/"+id+".tsv":hashes.containsKey(post)?post:standard;
                candidates.computeIfAbsent(id,k->new ArrayList<>()).add(new Source(producer,id,producer.directory().resolve(name),text(hashes.get(name))));}
        }
        var result=new TreeMap<String,Source>();
        for(var entry:candidates.entrySet()){
            var nativeSources=entry.getValue().stream().filter(s->s.producer().type().equals("association")).toList();var eligible=nativeSources.isEmpty()?entry.getValue():nativeSources;
            if(eligible.size()!=1)throw new IOException("Ambiguous completed cohort result producers for "+entry.getKey());var source=eligible.get(0);verify(source);result.put(entry.getKey(),source);
        }
        return result;
    }
    static Selection official(Context c)throws IOException {
        var p=policy(c);var producers=producers(c);String operation=text(p.get("operation_id")),dataset=text(p.get("dataset_id")),kind=text(p.get("kind"));
        var metas=producers.stream().filter(s->s.type().equals("meta_analysis")&&(operation.isBlank()||s.id().equals(operation))).toList();
        boolean association=kind.equals("association")||!dataset.isBlank()||!kind.equals("meta_analysis")&&!operation.isBlank()&&producers.stream().anyMatch(s->s.id().equals(operation)&&Set.of("association","validate_harmonize").contains(s.type()));
        if(!association&&metas.isEmpty()&&partialPoolExpected(c))
            throw unavailable(c,p,"incomplete_primary_meta_analysis","The supplied primary meta-analysis explicitly reports partial coverage and no matching native pool is available; no cohort or sensitivity fallback was selected");
        boolean expectedMeta=kind.equals("meta_analysis")||!association&&(producers.stream().anyMatch(s->s.type().equals("meta_analysis"))||plannedMeta(c));
        if(!association&&(expectedMeta||!metas.isEmpty())){
            if(metas.size()!=1)throw unavailable(c,p,metas.isEmpty()?"missing_primary_meta_analysis":"ambiguous_primary_meta_analysis",metas.isEmpty()?"The registered primary meta-analysis is unavailable; cohort results cannot replace it":"Multiple meta-analysis producers need an explicit primary_result.operation_id and rationale");
            var producer=metas.get(0);if(!"complete".equals(producer.manifest().get("state")))throw unavailable(c,p,"incomplete_primary_meta_analysis","The registered primary meta-analysis is not complete; no cohort or sensitivity fallback was selected");
            var details=map(producer.manifest().get("details"));if(details.containsKey("role")&&!"primary".equals(details.get("role")))throw unavailable(c,p,"nonprimary_producer_role","The selected meta-analysis is not declared primary");
            if(Boolean.FALSE.equals(details.get("primary_selection_eligible")))throw unavailable(c,p,"incomplete_primary_meta_analysis","The retained imported meta-analysis is not eligible for primary statistical selection; no cohort fallback was selected");
            var hashes=map(producer.manifest().get("output_sha256"));if(!hashes.containsKey("meta-analysis.tsv"))throw new IOException("Completed primary meta-analysis has no sealed canonical meta-analysis.tsv");
            return select(c,new Source(producer,"",producer.directory().resolve("meta-analysis.tsv"),text(hashes.get("meta-analysis.tsv"))),p,"official");
        }
        var cohorts=cohorts(c,producers,operation,false);
        if(!dataset.isBlank()){Source source=cohorts.get(dataset);if(source==null)throw unavailable(c,p,"missing_primary_dataset","The registered primary dataset has no completed scoped association producer: "+dataset);return select(c,source,p,"official");}
        if(cohorts.size()>1)throw unavailable(c,p,"ambiguous_primary_cohort","Multiple cohort result sources need a registered primary meta-analysis or explicit primary_result.dataset_id; no best-P cohort was chosen");
        if(cohorts.isEmpty()){
            if(!operation.isBlank()||association)throw unavailable(c,p,"missing_primary_association","The registered primary association source is unavailable");
            record(c,"official",Map.of("hypothesis",text(c.operation().get("hypothesis")),"status","not_applicable_no_feature_primary_source","registered_policy",p));return null;
        }
        return select(c,cohorts.values().iterator().next(),p,"official");
    }
    static boolean partialPoolExpected(Context c) {
        return org.jlinalg.study.StudySuppliedResultPolicy.resolve(StudyResultTables.analysis(c)).stream()
            .anyMatch(d->"meta_analysis".equals(d.get("kind"))&&org.jlinalg.study.StudySuppliedResultPolicy.partialPrimary(d));
    }
    /** Explicit dataset analyses, such as per-cohort GSEA, are not silently labelled the official pooled result. */
    static Selection dataset(Context c,String id,String purpose)throws IOException {
        Source source=cohorts(c).get(id);if(source==null)throw unavailable(c,policy(c),"missing_scoped_dataset","The explicit "+purpose+" dataset has no completed sealed result: "+id);
        return select(c,source,Map.of("kind","association"),purpose+":"+id);
    }
    /** A frozen discovery pool has its own source family; validation cannot redefine it. */
    static Selection discovery(Context c,Path path)throws IOException {
        var candidates=producers(c).stream().filter(p->p.type().equals("discovery_meta_analysis")
            &&p.directory().resolve("discovery-results.tsv").toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())).toList();
        if(candidates.size()!=1)throw new IOException("Frozen discovery selection requires one scoped discovery_meta_analysis producer");
        var producer=candidates.get(0);
        if(!"complete".equals(producer.manifest().get("state")))throw new IOException("Frozen discovery producer is incomplete");
        var source=new Source(producer,"",path,text(map(producer.manifest().get("output_sha256")).get("discovery-results.tsv")));
        return select(c,source,Map.of("kind","discovery_meta_analysis","rationale",
            "Frozen discovery selection for validation; separate from the official all-cohort primary result"),"frozen_discovery");
    }
    /** Cohort influence has its own all-cohort pooling baseline; it never promotes that fit to official. */
    static Path metaBaseline(Context c)throws IOException {
        String registered=text(policy(c).get("operation_id"));
        var candidates=producers(c).stream().filter(p->p.type().equals("meta_analysis")).toList();
        if(!registered.isBlank()&&candidates.stream().anyMatch(p->p.id().equals(registered)))
            candidates=candidates.stream().filter(p->p.id().equals(registered)).toList();
        if(candidates.size()>1)throw new IOException("Ambiguous primary meta_analysis diagnostic baseline; register primary_result.operation_id");
        candidates=candidates.stream().filter(p->"complete".equals(p.manifest().get("state"))).toList();
        if(candidates.isEmpty())return null;
        var producer=candidates.get(0);var hashes=map(producer.manifest().get("output_sha256"));if(!hashes.containsKey("meta-analysis.tsv"))return null;
        if(Boolean.FALSE.equals(map(producer.manifest().get("details")).get("primary_selection_eligible")))return null;
        var source=new Source(producer,"",producer.directory().resolve("meta-analysis.tsv"),text(hashes.get("meta-analysis.tsv")));
        select(c,source,Map.of("kind","meta_analysis","rationale","Registered all-cohort influence and heterogeneity baseline; separate from official source or contrast overrides"),"all_cohort_meta_baseline");
        return source.path();
    }
    private static Selection select(Context c,Source source,Map<String,Object> p,String scope)throws IOException {
        if(source.producer().type().equals("validate_harmonize")&&!source.producer().id().equals(text(p.get("operation_id")))&&requiresAssociation(c,source.dataset()))
            throw unavailable(c,p,"incomplete_primary_association","The registered association fit is unavailable; its harmonization copy cannot replace it");
        verify(source);Path path=source.path();String before=source.sha256();var table=DelimitedData.readResult(path);table.column("feature_id");table.column("p_value");
        var details=map(source.producer().manifest().get("details"));if(Boolean.FALSE.equals(details.get("primary_selection_eligible")))throw unavailable(c,p,"unresolved_source_testing_family","The selected source is not eligible for primary statistical selection; no adjustment or source was substituted");
        Path supplied=path.getParent().resolve("supplied-analysis.yaml");if(Files.isRegularFile(supplied)){var hashes=map(source.producer().manifest().get("output_sha256"));if(!hashes.containsKey("supplied-analysis.yaml"))throw new IOException("Supplied primary metadata is not sealed in its producer");StudyFindingInventory.verified(source.producer().directory(),"supplied-analysis.yaml",text(hashes.get("supplied-analysis.yaml")));}
        String metadataHash=Files.isRegularFile(supplied)?FollowupSupport.hash(supplied):"not_supplied";
        String term="",rule="single_result_columns";
        if(table.header().contains("term")){
            var terms=new LinkedHashSet<String>();for(String[] row:table.rows())terms.add(row[table.column("term")]);
            if(p.containsKey("term")){term=text(p.get("term"));rule="registered_term";}
            else if(Set.of("meta_analysis","discovery_meta_analysis").contains(source.producer().type())&&terms.contains("pooled")){term="pooled";rule="pooled_meta_analysis_term";}
            else if(terms.size()==1){term=terms.iterator().next();rule="unique_observed_term";}
            else if(!terms.isEmpty())throw unavailable(c,p,"ambiguous_primary_contrast","Multiple source terms require primary_result.term and its rationale; no coefficient or minimum P was selected");
            if(!terms.isEmpty()&&!terms.contains(term))throw unavailable(c,p,"missing_registered_primary_term","The registered primary term is absent from the chosen source");
        }else if(p.containsKey("term"))throw unavailable(c,p,"unverifiable_registered_primary_term","A term was registered but the source has no term column");
        var rows=new ArrayList<String[]>();var probabilities=new LinkedHashMap<String,Double>();var ids=new LinkedHashSet<String>();
        for(String[] row:table.rows()){
            if(table.header().contains("term")&&!row[table.column("term")].equals(term))continue;String id=row[table.column("feature_id")];if(id.isBlank()||id.chars().anyMatch(Character::isISOControl)||!ids.add(id))throw new IOException("Ambiguous or invalid primary feature identity in its scoped contrast: "+id);
            rows.add(row);double probability=StudyCohortResults.numeric(row[table.column("p_value")]);if(Double.isFinite(probability)&&(probability<0||probability>1))throw new IOException("Primary P value outside [0,1]");
            boolean estimable=!table.header().contains("status")||Set.of("ok","tested","complete","single_cohort").contains(row[table.column("status")].toLowerCase(java.util.Locale.ROOT));
            if(StudyResultShape.pOnly(table,row)){if(!StudyResultShape.missingEffects(table,row))throw new IOException("P-only primary results contain contradictory effect estimates");}
            else if(table.header().contains("beta")||table.header().contains("se"))estimable&=table.header().contains("beta")&&table.header().contains("se")&&Double.isFinite(StudyCohortResults.numeric(row[table.column("beta")]))&&Double.isFinite(StudyCohortResults.numeric(row[table.column("se")]))&&StudyCohortResults.numeric(row[table.column("se")])>0;
            if(estimable&&Double.isFinite(probability))probabilities.put(id,probability);
        }
        var view=table.selectRows(rows);var family=StudySuppliedResultPolicy.family(path);String adjustment=family.isEmpty()?StudyCohortResults.method(c):text(family.getOrDefault("adjustment","PROVIDED"));
        var adjusted=StudySuppliedFamilies.adjusted(path,view,probabilities,StudyCohortResults.adjust(probabilities,StudyCohortResults.method(c)));
        if(!FollowupSupport.hash(path).equals(before))throw new IOException("Primary source changed while reading its scoped testing family");
        String manifestSnapshot=snapshot(c,source.producer().directory().resolve("manifest.yaml"),"primary-source-manifests");
        if(!StudyYaml.loadArtifact(c.outputDirectory().resolve(manifestSnapshot)).equals(source.producer().manifest()))throw new IOException("Primary producer manifest changed while reading its source");
        String metadataSnapshot=Files.isRegularFile(supplied)?snapshot(c,supplied,"primary-source-metadata"):"not_supplied";
        if(!metadataSnapshot.equals("not_supplied")&&!metadataHash.equals(FollowupSupport.hash(c.outputDirectory().resolve(metadataSnapshot))))throw new IOException("Primary family metadata changed while reading its source");
        var receipt=new LinkedHashMap<String,Object>();receipt.put("schema_version",1);receipt.put("hypothesis",text(c.operation().get("hypothesis")));receipt.put("status","selected");receipt.put("selection_scope",scope);receipt.put("official_primary",scope.equals("official"));receipt.put("producer_type",source.producer().type());receipt.put("producer_id",source.producer().id());receipt.put("dataset_id",source.dataset());receipt.put("source_artifact",c.project().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/'));receipt.put("source_sha256",before);receipt.put("producer_manifest_sha256",FollowupSupport.hash(c.outputDirectory().resolve(manifestSnapshot)));receipt.put("registered_policy",p);receipt.put("policy_sha256",StudyFindingInventory.hash(StudyAssistance.json(new TreeMap<>(p))));receipt.put("selected_term",term);receipt.put("term_selection_rule",rule);receipt.put("source_rows",table.rows().size());receipt.put("scoped_rows",rows.size());receipt.put("estimable_features",probabilities.size());receipt.put("adjustment",adjustment);receipt.put("adjusted_values_origin",family.isEmpty()?"computed_from_complete_observed_scoped_input":"preserved_source_full_family");receipt.put("source_testing_family",family);receipt.put("source_metadata_sha256",metadataHash);receipt.put("testing_denominator",family.getOrDefault("tested_count",probabilities.size()));receipt.put("testing_alpha",family.getOrDefault("alpha",StudyCohortResults.alpha(c)));receipt.put("required_manuscript_caveat",family.isEmpty()?"The family covers eligible observed rows in the registered contrast. Completeness and calibration of the original external assay/export are not independently certified.":"Source testing-family, completeness and correction declarations are preserved; normalization does not independently reproduce the external analysis.");
        receipt.put("source_testing_family",StudySuppliedResultPolicy.aggregateFamily(family));
        receipt.put("producer_manifest_snapshot",manifestSnapshot);receipt.put("source_metadata_snapshot",metadataSnapshot);
        var identity=new TreeMap<String,Object>();
        for(String field:List.of("hypothesis","producer_type","producer_id","dataset_id","source_sha256","source_metadata_sha256","selected_term","policy_sha256","adjustment","testing_denominator","testing_alpha"))identity.put(field,receipt.get(field));
        receipt.put("primary_identity_sha256",StudyFindingInventory.hash(StudyAssistance.json(identity)));
        record(c,scope,receipt);return new Selection(source,table,view,Map.copyOf(probabilities),Map.copyOf(adjusted),Map.copyOf(family),adjustment,term,Map.copyOf(receipt));
    }
    private static void verify(Source source)throws IOException {
        var hashes=map(source.producer().manifest().get("output_sha256"));String name=source.producer().directory().relativize(source.path()).toString().replace('\\','/');if(!hashes.containsKey(name))throw new IOException("Primary artifact is not sealed in its producer");StudyFindingInventory.verified(source.producer().directory(),name,source.sha256());
    }
    private static String snapshot(Context c,Path source,String folder)throws IOException {
        String hash=FollowupSupport.hash(source);Path target=c.outputDirectory().resolve(folder).resolve(hash+".yaml");Files.createDirectories(target.getParent());
        if(!Files.isRegularFile(target))Files.copy(source,target);
        if(!hash.equals(FollowupSupport.hash(source))||!hash.equals(FollowupSupport.hash(target)))throw new IOException("Primary provenance changed while preserving its snapshot");
        return c.outputDirectory().relativize(target).toString().replace('\\','/');
    }
    private static boolean requiresAssociation(Context c,String dataset)throws IOException {
        String hypothesis=text(c.operation().get("hypothesis"));
        if(list(c.plan().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(op->hypothesis.equals(op.get("hypothesis"))&&"association".equals(op.get("type"))&&(dataset.equals(op.get("dataset_id"))||list(op.get("datasets")).contains(dataset))))return true;
        for(var producer:producers(c))if(producer.type().equals("association")&&map(producer.manifest().get("output_sha256")).keySet().stream().anyMatch(name->name.equals("standard-results/"+dataset+".tsv")||name.equals("post-effect-qc-results/"+dataset+".tsv")))return true;
        return false;
    }
    private static Set<String> selected(Context c){var ids=new LinkedHashSet<String>();for(Object id:list(c.operation().get("datasets")))ids.add(text(id));if(ids.isEmpty())for(Object raw:list(c.study().get("hypotheses"))){var h=map(raw);if(h.get("id").equals(c.operation().get("hypothesis")))for(Object value:map(h.get("inputs")).values())for(Object id:list(value))ids.add(text(id));}return ids;}
    private static boolean plannedMeta(Context c){String hypothesis=text(c.operation().get("hypothesis"));if(list(StudyResultTables.analysis(c).get("goals")).contains("meta_analysis"))return true;return list(c.plan().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(p->hypothesis.equals(p.get("hypothesis"))&&"meta_analysis".equals(p.get("type")));}
    private static Unavailable unavailable(Context c,Map<String,Object> p,String status,String reason)throws IOException {var receipt=Map.<String,Object>of("hypothesis",text(c.operation().get("hypothesis")),"status",status,"reason",reason,"registered_policy",p,"official_primary",true);record(c,"official",receipt);return new Unavailable(reason,receipt);}
    private static void record(Context c,String scope,Map<String,Object> receipt)throws IOException {
        Files.createDirectories(c.outputDirectory());Path file=c.outputDirectory().resolve("primary-result-selection.yaml");var selections=new LinkedHashMap<String,Object>();if(Files.isRegularFile(file))selections.putAll(map(StudyYaml.loadArtifact(file).get("selections")));String key=text(c.operation().get("hypothesis"))+":"+scope;
        var old=map(selections.get(key));if(old.containsKey("primary_identity_sha256")&&!old.get("primary_identity_sha256").equals(receipt.get("primary_identity_sha256")))throw new IOException("A frozen primary identity changed inside one operation; start a new approved analysis attempt");if(old.equals(receipt))return;selections.put(key,receipt);StudyPublicEvidence.write(file,Map.of("schema_version",1,"selections",selections));
    }
    static Result waiting(Context c,Unavailable unavailable)throws IOException {var need=StudyNeed.review("primary-result:"+c.operation().get("hypothesis"),"Resolve the registered primary result",unavailable.getMessage(),true);var details=new LinkedHashMap<String,Object>();details.put("primary_selection",unavailable.receipt());details.put("needs",List.of(need.document()));details.put("method_report",Map.of("performed",false,"reason",unavailable.getMessage()));StudyPublicEvidence.write(c.outputDirectory().resolve("request.yaml"),details);return new Result(State.WAITING_FOR_INPUT,unavailable.getMessage(),List.of("request.yaml","primary-result-selection.yaml"),details);}
}
