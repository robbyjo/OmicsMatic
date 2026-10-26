/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.enrichment.EnrichmentAnalysis;
import org.jlinalg.enrichment.EnrichmentResult;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Exploratory mapped-gene ORA, with one joint mask-by-term correction family. */
final class StudyRareEnrichment {
    private record Row(String mask,EnrichmentResult value) { }
    Result execute(Context c,String collection,String output)throws Exception {
        var view=StudyRareFollowup.view(c);if(view==null)return StudyRareFollowup.waiting(c,"Rare-set enrichment needs a completed verified primary projection and explicit gene mappings");
        var family=map(view.details().get("source_testing_family"));
        if(!"complete".equals(family.get("coverage")))return StudyRareFollowup.waiting(c,"Selected-only rare-set results cannot establish the complete eligible gene background for enrichment");
        if(view.table().rows().stream().noneMatch(r->Double.isFinite(StudyRareFollowup.numeric(r[view.table().column("p_value")]))))return new Result(State.NOT_APPLICABLE,"No estimable primary rare-set tests remain for enrichment",List.of(),Map.of("source_testing_family",family,"performed",false));
        if(!"9606".equals(text(view.details().get("species"))))return StudyRareFollowup.waiting(c,"The built-in GO/Reactome reference needs an explicitly human rare-set source");
        var p=StudyRareFollowup.policy(c);if(Boolean.FALSE.equals(p.get("enabled")))return new Result(State.NOT_APPLICABLE,"Rare-set enrichment is disabled by the registered policy",List.of(),Map.of("performed",false));
        var mappings=StudyRareFollowup.mappings(c,view);var table=view.table();boolean symbol=collection.equals("GO");
        double alpha=family.get("alpha") instanceof Number level?level.doubleValue():((Number)map(StudyResultTables.analysis(c).get("multiple_testing")).getOrDefault("alpha",.05)).doubleValue();
        var universes=new TreeMap<String,Set<String>>();var selections=new TreeMap<String,Set<String>>();var geneOwners=new TreeMap<String,String>();var dispositions=new ArrayList<String[]>();var incomplete=new LinkedHashSet<String>();
        for(String[] r:table.rows()) {
            String id=r[table.column("feature_id")],mask=r[table.column("mask_id")];double probability=StudyRareFollowup.numeric(r[table.column("p_value")]),q=StudyRareFollowup.numeric(r[table.column("adjusted_p")]);
            String state=table.header().contains("status")?r[table.column("status")]:"ok";boolean eligible=Set.of("ok","tested","complete","single_cohort").contains(state.toLowerCase(java.util.Locale.ROOT))&&Double.isFinite(probability)&&Double.isFinite(q);
            var mapping=mappings.get(id);String gene=mapping==null?"":mapping.get(symbol?0:1);if(!symbol){gene=gene.replaceFirst("\\.\\d+$","");if(!gene.matches("ENSG\\d+"))gene="";}
            String reason=!eligible?"not_estimable_or_outcome_independent_exclusion":mapping==null?"mapping_unavailable_or_ambiguous":gene.isBlank()?"required_gene_namespace_unavailable":"mapped_eligible";
            if(eligible) {
                universes.computeIfAbsent(mask,k->new TreeSet<>());selections.computeIfAbsent(mask,k->new TreeSet<>());
                if(!gene.isBlank()) {
                    String old=geneOwners.putIfAbsent(mask+"\u001f"+gene,id);if(old!=null&&!old.equals(id)){incomplete.add(mask);reason="multiple_sets_map_to_one_gene_requires_registered_collapse";}
                    universes.get(mask).add(gene);if(q<alpha)selections.get(mask).add(gene);
                }
            }
            dispositions.add(new String[]{mask,id,gene,String.valueOf(eligible),String.valueOf(eligible&&q<alpha),reason,Double.isFinite(q)?String.valueOf(q):"NA"});
        }
        FollowupSupport.table(c.outputDirectory().resolve("rare-gene-dispositions.tsv"),List.of("mask_id","feature_id","gene_id","source_estimable","source_selected","disposition","source_adjusted_p"),dispositions);
        if(!incomplete.isEmpty()||universes.values().stream().anyMatch(Set::isEmpty))return StudyRareFollowup.waiting(c,"At least one eligible mask has no mapped background or multiple tested sets per gene. Supply unambiguous one-set-per-gene mappings or a calibrated supplied enrichment; no minimum-P gene collapse was used");
        var details=new LinkedHashMap<String,Object>();details.put("source_selection",view.details());details.put("source_family",family);details.put("association_selection_alpha",alpha);details.put("policy",p);details.put("adjustment",p.get("enrichment_adjustment"));details.put("enrichment_source","rare_sets");
        var resourcePolicy=Map.<String,Object>of("collection",collection,"gene_id_type",symbol?"symbol":"ensembl_gene","max_sets",p.get("maximum_gene_sets"));
        var sets=StudyGsea.geneSets(c,resourcePolicy,details,"human");if(sets==null)return StudyRareFollowup.waiting(c,"The declared rare-set enrichment collection is unavailable; source associations and mappings remain retained");
        if(sets.size()>((Number)p.get("maximum_gene_sets")).intValue()||(long)sets.size()*universes.size()>((Number)p.get("maximum_testing_rows")).longValue())return StudyRareFollowup.waiting(c,"The complete mask-by-term family exceeds the registered enrichment bounds; no masks or favorable terms were selected");
        var results=new ArrayList<Row>();var maskSummary=new ArrayList<Object>();
        for(var e:universes.entrySet()) {
            var selected=selections.get(e.getKey());var terms=EnrichmentAnalysis.ora(e.getValue(),selected,sets,((Number)p.get("minimum_term_genes")).intValue(),((Number)p.get("maximum_term_genes")).intValue(),EnrichmentAnalysis.Fdr.NONE);
            for(var result:terms)results.add(new Row(e.getKey(),result));
            maskSummary.add(Map.of("mask_id",e.getKey(),"mapped_tested_genes",e.getValue().size(),"selected_genes",selected.size(),"tested_terms",terms.size()));
        }
        var correction=EnrichmentAnalysis.Fdr.valueOf(text(p.get("enrichment_adjustment")));double[] logQ=EnrichmentAnalysis.adjustLog(results.stream().mapToDouble(r->r.value.logPValue()).toArray(),correction);var rows=new ArrayList<String[]>();
        for(int i=0;i<results.size();i++) {
            var r=results.get(i);var v=r.value;String key="rare-mask-"+Base64.getUrlEncoder().withoutPadding().encodeToString(r.mask.getBytes(StandardCharsets.UTF_8))+":"+v.id();
            rows.add(new String[]{key,v.id(),v.name(),r.mask,"one_sided_hypergeometric","ok",String.valueOf(v.universeSize()),String.valueOf(v.selectedSize()),String.valueOf(v.setSize()),String.valueOf(v.overlap()),String.valueOf(v.pValue()),String.valueOf(Math.exp(logQ[i])),String.valueOf(v.logPValue()),String.valueOf(logQ[i]),String.join(";",v.hitGenes()),text(p.get("enrichment_adjustment")),String.valueOf(results.size()),text(family.get("adjustment")),text(family.get("tested_count")),"rare_sets"});
        }
        FollowupSupport.table(c.outputDirectory().resolve(output),List.of("enrichment_id","term_id","term_name","mask_id","method","status","universe_genes","selected_genes","term_genes","overlap_genes","p_value","adjusted_p","log_p_value","log_adjusted_p","hit_genes","adjustment","testing_denominator","association_adjustment","association_testing_denominator","enrichment_source"),rows);
        details.put("masks",maskSummary);details.put("testing_denominator",results.size());details.put("testing_family","Every size-eligible term across every estimable registered primary mask, including empty selections and zero-hit terms");details.put("required_manuscript_caveat","Exploratory rare-set mapped-gene over-representation assumes exchangeable gene selection within the tested mapped background. Gene length, variant opportunity, MAC, callability, coverage, ancestry and annotation can affect detection and are not corrected by this ordinary hypergeometric test. Overlapping masks/terms are dependent; BY is the default separate enrichment correction. The source selection family is retained exactly; enrichment does not recalculate its association FDR. Enrichment does not establish pathway activation, independent replication or causality. "+text(view.details().get("required_manuscript_caveat")));
        details.put("method_report",StudyMethods.record("Rare-set per-mask mapped-gene over-representation",Map.of("collection",collection,"policy",p,"association_source_family",family,"mask_by_term_tests",results.size()),List.of("https://doi.org/10.1214/aos/1013699998"),"Registered exploratory annotation context; no favorable mask or term selection",List.of("gene-set enrichment")));
        write(c.outputDirectory().resolve("rare-enrichment.yaml"),details);return new Result(State.COMPLETE,"Completed rare-set per-mask enrichment with joint mask-by-term correction",StudyRareSupport.outputs(c),details);
    }
}
