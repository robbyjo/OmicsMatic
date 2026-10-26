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
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyRareFollowupPolicy;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Verified primary gene/mask projections; no new association P or gene-level FDR. */
final class StudyRareFollowup {
    record View(Path directory,DelimitedData table,Map<String,Object> details) { }
    record Source(Path directory,Map<String,Object> manifest,Path results) { }
    static Map<String,Object> policy(Context c) {
        return StudyRareFollowupPolicy.resolve(map(map(StudyResultTables.analysis(c).get("rare_variants")).get("followup")));
    }
    static boolean planned(Context c)throws IOException {
        String h=text(c.operation().get("hypothesis"));if(list(c.plan().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(o->h.equals(o.get("hypothesis"))&&"rare_set_followup".equals(o.get("type"))))return true;
        for(Path directory:c.dependencyDirectories().values())if(Files.isRegularFile(directory.resolve("manifest.yaml"))){var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));if(h.equals(m.get("hypothesis"))&&"rare_set_followup".equals(m.get("operation_type")))return true;}return false;
    }
    Result execute(Context c)throws IOException {
        Files.createDirectories(c.outputDirectory());if(Boolean.FALSE.equals(policy(c).get("enabled")))return new Result(State.NOT_APPLICABLE,"Rare-set follow-up is disabled",List.of(),Map.of("performed",false));Source source=source(c);
        if(source==null)return waiting(c,"A unique verified primary rare-set family is required; no cohort, discovery or sensitivity table is substituted");
        var metadata=map(source.manifest.get("details"));boolean partial="waiting_for_input".equals(source.manifest.get("state"));boolean omnibus="rare_set_omnibus".equals(source.manifest.get("operation_type"));
        if(!"complete".equals(source.manifest.get("state"))&&!(partial&&Boolean.TRUE.equals(metadata.get("rare_primary_complete"))))
            return waiting(c,"The primary rare-set testing family is unresolved; completing another scope cannot substitute for it");
        if(Boolean.FALSE.equals(metadata.get("primary_selection_eligible")))return waiting(c,"The supplied rare-set family is not eligible for primary selection");
        var input=DelimitedData.readResult(source.results);for(String name:List.of("feature_id","mask_id","test","p_value","adjusted_p","is_primary_test"))input.column(name);
        var family=StudySuppliedResultPolicy.family(source.results);String adjustment=text(family.get("adjustment"));Object denominator=family.get("tested_count");
        if(family.isEmpty()) {
            for(Object raw:list(metadata.get("families")))if("primary".equals(map(raw).get("scope"))) {
                var f=map(raw);if(Boolean.FALSE.equals(f.get("complete")))return waiting(c,"The registered primary rare-set family is incomplete");
                denominator=f.get("testing_denominator");adjustment=text(f.get("adjustment"));
            }
            if(adjustment.isBlank())adjustment=text(map(map(metadata.get("method_report")).get("parameters")).get("multiple_testing"));
            if(adjustment.isBlank())return waiting(c,"The source must retain the original association-family correction method");
            if(denominator==null&&input.rows().isEmpty()&&metadata.get("primary_estimable_tests") instanceof Number count&&count.longValue()==0&&metadata.get("result_rows") instanceof Number total&&total.longValue()==0)denominator=0;
            family=new LinkedHashMap<>(Map.of("coverage","complete","adjustment",adjustment,"tested_count",denominator==null?"not_declared":denominator));
            Object alpha=map(metadata.get("effective_policy")).get("alpha");if(alpha instanceof Number)family.put("alpha",alpha);
        }
        if(!(denominator instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())return waiting(c,"The source must retain its full joint mask/test denominator");
        var rows=new ArrayList<String[]>();var ids=new LinkedHashSet<String>();var keys=new LinkedHashSet<String>();var masks=new LinkedHashSet<String>();var allMasks=new LinkedHashSet<String>();var allTests=new LinkedHashSet<String>();long finite=0,observedFamily=0;
        for(String[] row:input.rows()) {
            if(input.header().contains("scope")&&!row[input.column("scope")].equals("primary"))continue;
            String feature=row[input.column("feature_id")],mask=row[input.column("mask_id")],test=row[input.column("test")],pair=feature+"\u001f"+mask;
            if(feature.isBlank()||mask.isBlank()||test.isBlank()||feature.chars().anyMatch(Character::isISOControl)||mask.chars().anyMatch(Character::isISOControl)||test.chars().anyMatch(Character::isISOControl)||!allTests.add(pair+"\u001f"+test))throw new IOException("Duplicate or invalid primary rare set/mask/test identity");allMasks.add(pair);
            String flag=row[input.column("is_primary_test")];if(!Set.of("true","false").contains(flag.toLowerCase(java.util.Locale.ROOT)))throw new IOException("Invalid source primary-test flag");
            double p=probability(row[input.column("p_value")]),q=probability(row[input.column("adjusted_p")]);
            if(Double.isFinite(p)){observedFamily++;if(!Double.isFinite(q))return waiting(c,"An estimable rare-set test has no original full-family adjusted P value; the joint family is unresolved");}
            if(!flag.equalsIgnoreCase("true"))continue;
            if(!keys.add(pair))throw new IOException("Multiple primary tests declared for a rare set/mask");
            String registered=text(map(metadata.get("primary_identity")).get("test"));if(!registered.isBlank()&&!registered.equalsIgnoreCase(test))throw new IOException("Primary-test flag contradicts the source's registered primary identity");
            if(Double.isFinite(p))finite++;rows.add(row);ids.add(feature);masks.add(mask);
        }
        if(!allMasks.equals(keys))return waiting(c,"Every retained primary set/mask requires its prespecified primary test, including unavailable tests; no successful-test substitution is performed");
        if(observedFamily>n.longValue())throw new IOException("Rare-set source denominator is smaller than its retained joint testing family");
        Path projection=c.outputDirectory().resolve("rare-followup-results.tsv");FollowupSupport.table(projection,input.header(),rows);
        var outputs=new ArrayList<String>(List.of("rare-followup-results.tsv"));
        Files.copy(source.results,c.outputDirectory().resolve("rare-source-results.tsv"));outputs.add("rare-source-results.tsv");
        var sourceHashes=map(source.manifest.get("output_sha256"));
        if(sourceHashes.containsKey("feature-annotation.tsv")) {
            Path annotation=StudyFindingInventory.verified(source.directory,"feature-annotation.tsv",text(sourceHashes.get("feature-annotation.tsv")));
            Files.copy(annotation,c.outputDirectory().resolve("feature-annotation.tsv"));outputs.add("feature-annotation.tsv");
        } else {
            var tables=new LinkedHashMap<String,Path>();for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(list(c.operation().get("datasets")).contains(d.get("id")))tables.put(text(d.get("id")),projection);}
            outputs.addAll(StudyFeatureAnnotations.write(c,tables));
        }
        Path producer=c.outputDirectory().resolve("rare-source-manifest.yaml");Files.copy(source.directory.resolve("manifest.yaml"),producer);outputs.add("rare-source-manifest.yaml");
        var assessed=DelimitedData.readResult(c.outputDirectory().resolve("feature-annotation.tsv"));var mappingStatus=new TreeMap<String,String>();for(String[] row:assessed.rows())mappingStatus.merge(row[assessed.column("feature_id")],row[assessed.column("mapping_status")],(a,b)->a.equals(b)?a:"ambiguous");
        var dispositions=new ArrayList<String[]>();for(String[] r:rows)dispositions.add(new String[]{r[input.column("feature_id")],r[input.column("mask_id")],r[input.column("test")],r[input.column("p_value")],r[input.column("adjusted_p")],mappingStatus.getOrDefault(r[input.column("feature_id")],"unmapped"),adjustment,String.valueOf(n.longValue())});
        FollowupSupport.table(c.outputDirectory().resolve("rare-feature-dispositions.tsv"),List.of("feature_id","mask_id","test","p_value","source_adjusted_p","mapping_status","association_adjustment","association_testing_denominator"),dispositions);outputs.add("rare-feature-dispositions.tsv");
        var details=new LinkedHashMap<String,Object>();details.put("source_operation",source.manifest.get("operation_id"));details.put("source_operation_state",source.manifest.get("state"));details.put("primary_complete",true);details.put("source_testing_family",family);details.put("source_sha256",FollowupSupport.hash(source.results));details.put("source_manifest_sha256",FollowupSupport.hash(producer));details.put("source_rows",input.rows().size());details.put("retained_primary_rows",rows.size());details.put("set_ids",ids.size());details.put("masks",List.copyOf(masks));details.put("policy",policy(c));
        String species=StudyBioinformatics.species(c);if(species.isBlank()){String declared=text(map(metadata.get("scope")).get("species"));species=Set.of("human","Homo sapiens","9606").contains(declared)?"9606":declared;}details.put("species",species);
        details.put("source_family_coverage",family.getOrDefault("coverage","unknown"));
        details.put("source_artifact",c.project().toAbsolutePath().normalize().relativize(source.results.toAbsolutePath().normalize()).toString().replace('\\','/'));
        details.put("source_unit_omnibus",omnibus);
        details.put("required_manuscript_caveat",(omnibus?"Source adjusted P values cover the registered unit omnibus family; original component mask/test families remain separate. ":"Source adjusted P values cover the registered joint set/mask/test family. They are not recalculated across just primary tests or mapped genes. ")+"Set identifiers require reviewed gene mappings; variant membership and gene names do not establish causality. "+text(metadata.get("required_manuscript_caveat"))+" "+text(metadata.get("model_limitations"))+(partial?" Other requested source scopes remain unresolved; this separately complete primary family is retained.":""));
        write(c.outputDirectory().resolve("rare-followup.yaml"),details);outputs.add("rare-followup.yaml");return new Result(State.COMPLETE,"Preserved primary rare-set families and assessed explicit gene mappings",outputs,details);
    }
    static View view(Context c)throws IOException {
        View found=null;for(Path directory:c.dependencyDirectories().values()) {
            if(!Files.isRegularFile(directory.resolve("manifest.yaml")))continue;var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
            if(!"rare_set_followup".equals(m.get("operation_type"))||!c.operation().get("hypothesis").equals(m.get("hypothesis"))||!"complete".equals(m.get("state")))continue;
            if(found!=null&&!found.directory.equals(directory))throw new IOException("Ambiguous rare-set follow-up producer");
            var hashes=map(m.get("output_sha256"));Path path=StudyFindingInventory.verified(directory,"rare-followup-results.tsv",text(hashes.get("rare-followup-results.tsv")));
            Path doc=StudyFindingInventory.verified(directory,"rare-followup.yaml",text(hashes.get("rare-followup.yaml")));found=new View(directory,DelimitedData.readResult(path),StudyYaml.loadArtifact(doc));
        }return found;
    }
    static Map<String,List<String>> mappings(Context c,View view)throws IOException {
        var m=StudyYaml.loadArtifact(view.directory.resolve("manifest.yaml"));var hashes=map(m.get("output_sha256"));Path path=StudyFindingInventory.verified(view.directory,"feature-annotation.tsv",text(hashes.get("feature-annotation.tsv")));var t=DelimitedData.readResult(path);var out=new TreeMap<String,List<String>>();var excluded=new LinkedHashSet<String>();
        for(String[] r:t.rows()) {String id=r[t.column("feature_id")];var fields=new ArrayList<>(List.of("gene_symbol","gene_id","chromosome","start","end","genome_build","mapping_status").stream().map(k->t.header().contains(k)?r[t.column(k)]:"").map(v->Set.of("NA","NaN",".").contains(v)?"":v).toList());if(fields.get(0).matches("ENS[GT]\\d+(?:\\.\\d+)?"))fields.set(0,"");
            if(!Set.of("located","missing_location").contains(fields.get(6)))excluded.add(id);var prior=out.putIfAbsent(id,fields);if(prior!=null&&!prior.equals(fields))excluded.add(id);
        }excluded.forEach(out::remove);return out;
    }
    static List<StudyBioinformatics.Feature> features(Context c,int limit,boolean strict)throws IOException {
        var view=view(c);if(view==null)return List.of();var mappings=mappings(c,view);var t=view.table();double threshold=((Number)StudyBioinformatics.config(c).getOrDefault("fdr",.05)).doubleValue();if(Boolean.TRUE.equals(view.details.get("source_unit_omnibus"))&&map(view.details.get("source_testing_family")).get("alpha") instanceof Number level)threshold=Math.min(threshold,level.doubleValue());
        var qualified=new TreeMap<String,List<Map<String,Object>>>();var representative=new TreeMap<String,double[]>();
        for(String[] r:t.rows()) {
            String id=r[t.column("feature_id")];double p=numeric(r[t.column("p_value")]),q=numeric(r[t.column("adjusted_p")]);String status=t.header().contains("status")?r[t.column("status")]:"ok";
            if(!Set.of("ok","tested","complete","single_cohort").contains(status.toLowerCase(java.util.Locale.ROOT))||!Double.isFinite(p)||!Double.isFinite(q)||(strict?q>=threshold:q>threshold)||!mappings.containsKey(id)||("literature_search".equals(c.operation().get("type"))&&!mappings.get(id).get(0).matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))continue;
            var family=map(view.details.get("source_testing_family"));var source=new LinkedHashMap<String,Object>();source.put("mask_id",r[t.column("mask_id")]);source.put("test",r[t.column("test")]);source.put("p_value",p);source.put("source_adjusted_p",q);source.put("adjustment",family.get("adjustment"));source.put("testing_denominator",family.get("tested_count"));source.put("source_sha256",view.details.get("source_sha256"));qualified.computeIfAbsent(id,k->new ArrayList<>()).add(source);
            double[] old=representative.get(id);if(old==null||p<old[0])representative.put(id,new double[]{p,q});
        }
        var out=new ArrayList<StudyBioinformatics.Feature>();
        for(var entry:qualified.entrySet()) {
            String id=entry.getKey();var a=mappings.get(id);double[] value=representative.get(id);boolean omnibus=Boolean.TRUE.equals(view.details.get("source_unit_omnibus"));var selection=Map.<String,Object>of("kind",omnibus?"rare_unit_omnibus":"rare_set_primary_tests","qualifying_source_tests",entry.getValue(),"probability_scope",omnibus?"Registered unit omnibus and separately adjusted gene/set family":"Original gene/set-mask test family; no combined gene-level P or FDR estimated","display_rule",omnibus?"Original unit omnibus P orders contextual retrieval; component source evidence remains separately retained":"Smallest original eligible primary-test P orders contextual retrieval only; every qualifying mask/test remains listed","source_operation_state",view.details.get("source_operation_state"));
            out.add(new StudyBioinformatics.Feature(id,a.get(0),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(6),value[0],value[1],selection));
        }
        return out.stream().sorted(java.util.Comparator.comparingDouble(StudyBioinformatics.Feature::p).thenComparing(StudyBioinformatics.Feature::id)).limit(limit).toList();
    }
    static Source source(Context c)throws IOException {
        if(!org.jlinalg.study.StudyRareOmnibusPolicy.primary(StudyResultTables.analysis(c)))return sourceMask(c);
        Source found=null;for(Path directory:c.dependencyDirectories().values()){
            if(!Files.isRegularFile(directory.resolve("manifest.yaml")))continue;var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));if(!"rare_set_omnibus".equals(m.get("operation_type"))||!c.operation().get("hypothesis").equals(m.get("hypothesis")))continue;
            var hashes=map(m.get("output_sha256"));String name="rare-omnibus-results.tsv";if(!hashes.containsKey(name))continue;Path result=StudyFindingInventory.verified(directory,name,text(hashes.get(name)));if(found!=null&&!found.directory.equals(directory))throw new IOException("Ambiguous registered rare omnibus producer");found=new Source(directory,m,result);
        }return found;
    }
    static Source sourceMask(Context c)throws IOException {
        Source found=null;for(Path directory:c.dependencyDirectories().values()) {
            if(!Files.isRegularFile(directory.resolve("manifest.yaml")))continue;var m=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));if(!"rare_variant_analysis".equals(m.get("operation_type"))||!c.operation().get("hypothesis").equals(m.get("hypothesis")))continue;
            var hashes=map(m.get("output_sha256"));if(!hashes.containsKey("rare-results.tsv"))continue;
            Path result=StudyFindingInventory.verified(directory,"rare-results.tsv",text(hashes.get("rare-results.tsv")));if(found!=null&&!found.directory.equals(directory))throw new IOException("Ambiguous rare-set primary producer");found=new Source(directory,m,result);
        }return found;
    }
    static Result waiting(Context c,String reason)throws IOException {
        var need=StudyNeed.review("rare-followup:"+c.operation().get("hypothesis")+":"+c.operation().get("type")+":"+StudyFindingInventory.hash(reason).substring(0,20),"Resolve rare-set downstream prerequisites",reason,true);var details=Map.<String,Object>of("needs",List.of(need.document()),"reason",reason,"method_report",Map.of("performed",false));write(c.outputDirectory().resolve("request.yaml"),details);return new Result(State.WAITING_FOR_INPUT,reason,List.of("request.yaml"),details);
    }
    private static double probability(String value)throws IOException {if(Set.of("","NA","NaN",".").contains(value))return Double.NaN;double p;try{p=Double.parseDouble(value);}catch(NumberFormatException invalid){throw new IOException("Malformed rare-set source probability",invalid);}if(!Double.isFinite(p)||p<0||p>1)throw new IOException("Invalid rare-set source probability");return p;}
    static double numeric(String value){try{return Double.parseDouble(value);}catch(NumberFormatException invalid){return Double.NaN;}}
}
