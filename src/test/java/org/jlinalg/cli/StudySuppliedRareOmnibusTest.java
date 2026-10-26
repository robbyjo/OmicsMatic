/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudySuppliedResultPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedRareOmnibusTest {
    @TempDir Path root;
    private Map<String,Object> definition(Map<String,Object> family){var d=new LinkedHashMap<String,Object>();d.put("id","external");d.put("kind","rare_set_omnibus");d.put("files",Map.of("results","results.tsv"));d.put("scope",Map.of("phenotype","synthetic"));d.put("method",Map.of("name","Synthetic registered cross-mask method","software","fixture","unit","gene"));d.put("family",family);return d;}
    private Context context(String id,Map<String,Object> d)throws Exception{var analysis=Map.<String,Object>of("rare_omnibus",Map.of("selection","primary"),"supplied_results",List.of(d));StudySuppliedResultPolicy.resolve(analysis);return new Context(root,Files.createDirectory(root.resolve(id)),Map.of("id",id,"type","rare_set_omnibus","hypothesis","H","supplied_result_id","external","datasets",List.of()),Map.of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",analysis))),Map.of(),Map.of());}
    private static Map<String,Object> full(int n){return Map.of("id","units","coverage","complete","tested_count",n,"adjustment","BY");}
    @Test void minimalCompletedOmnibusIsReusedWithoutAComponentRefitAndIgnoresUnmappedProse()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\tprivate_note\nENSG1\t.001\tPRIVATE_SENTINEL\nENSG2\t.5\tPRIVATE_SENTINEL\n");String hash=FollowupSupport.hash(root.resolve("results.tsv"));var c=context("reused",definition(full(2)));var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());assertEquals(false,r.details().get("analysis_executed_by_jlinalg"));assertEquals(true,r.details().get("primary_selection_eligible"));var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(.003,Double.parseDouble(t.rows().get(0)[t.column("adjusted_p")]),1e-16);assertFalse(Files.readString(c.outputDirectory().resolve("rare-omnibus-results.tsv")).contains("PRIVATE_SENTINEL"));assertFalse(Files.exists(c.outputDirectory().resolve("omnibus-components.tsv")));assertEquals(hash,FollowupSupport.hash(root.resolve("results.tsv")));
    }
    @Test void eachExportedScopeKeepsItsOwnDeclaredFamily()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"scope\tfeature_id\tp_value\nprimary\tENSG1\t.001\nprimary\tENSG2\t.5\ndiscovery\tENSG1\t.01\ndiscovery\tENSG2\t.5\ncohort:b\tENSG1\t.02\ncohort:b\tENSG2\t.5\n");var d=definition(full(2));d.put("method",Map.of("name","Synthetic","software","fixture","unit","gene","scope_families",Map.of("discovery",full(2),"cohort:b",full(2))));var c=context("scoped",d);var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));for(String[] row:t.rows())if(row[t.column("feature_id")].equals("ENSG1"))assertEquals(3*Double.parseDouble(row[t.column("p_value")]),Double.parseDouble(row[t.column("adjusted_p")]),1e-15);assertEquals(3,StudyPublicEvidence.list(r.details().get("families")).size());
    }
    @Test void anUndeclaredValidationFamilyHoldsThatScopeWhileRetainingTheCompletePrimary()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"scope\tfeature_id\tp_value\nprimary\tENSG1\t.001\ncohort:b\tENSG1\t.01\n");var c=context("partial",definition(full(1)));var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(true,r.details().get("rare_primary_complete"));assertEquals(true,r.details().get("primary_selection_eligible"));var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals("NA",t.rows().get(1)[t.column("adjusted_p")]);
    }
    @Test void selectedOnlyRowsPreserveTheSourceAdjustedValuesAndCannotCreateANewGeneFdr()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\tadjusted_p\nENSG1\t.00001\t.7\n");var d=definition(Map.of("id","external-full","coverage","selected_only","tested_count",25000,"adjustment","BY","adjusted_p_from_full_family",true));var c=context("selected",d);var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(".7",t.rows().get(0)[t.column("adjusted_p")]);assertEquals("selected_only",StudyPublicEvidence.map(r.details().get("resolved_family")).get("coverage"));
    }
    @Test void finiteLogTailsSurviveImportAndCorrection()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\tlog_p_value\nENSG1\t0\t-1000\nENSG2\t.5\tNA\n");var c=context("log",definition(full(2)));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(-1000+Math.log(3),Double.parseDouble(t.rows().get(0)[t.column("log_adjusted_p")]),1e-12);
    }
    @Test void anUnregisteredUnitOrConflictingCountsCannotBePromoted()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\texpected_components\tcomponent_count\tineligible_components\tunresolved_components\nENSG1\t.01\t4\t2\t0\t0\n");var c=context("counts",definition(full(1)));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));var d=definition(full(1));d.put("method",Map.of("name","Synthetic","software","fixture"));var missing=context("unit",d);assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(missing));
    }
    @Test void retainedMethodAndMembershipContextRejectsFreeProseOrNonIdentifierObjects()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\teffective_method\nENSG1\t.01\tprivate clinical prose\n");var c=context("prose",definition(full(1)));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\tsource_set_ids\nENSG1\t.01\t{\"note\":\"PRIVATE_SENTINEL\"}\n");var invalid=context("objects",definition(full(1)));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(invalid));
    }
    @Test void aLargerSourceFamilyCannotMakeASelectedExportACompleteEnrichmentBackground()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\tadjusted_p\nENSG1\t.00001\t.01\n");var d=definition(Map.of("id","source","coverage","complete","tested_count",18000,"adjustment","BY","adjusted_p_from_full_family",true));var c=context("partial-export",d);var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state());assertEquals(true,r.details().get("primary_selection_eligible"));var family=StudyPublicEvidence.map(r.details().get("resolved_family"));assertEquals("incomplete_export",family.get("coverage"));assertEquals(false,family.get("complete"));var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(".01",t.rows().get(0)[t.column("adjusted_p")]);
    }
    @Test void internallyCoherentButUnresolvedComponentCountsCannotClaimACompletedOmnibus()throws Exception{
        Files.writeString(root.resolve("results.tsv"),"feature_id\tp_value\texpected_components\tcomponent_count\tineligible_components\tunresolved_components\nENSG1\t.01\t4\t3\t0\t1\n");var c=context("unresolved-grid",definition(full(1)));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
}
