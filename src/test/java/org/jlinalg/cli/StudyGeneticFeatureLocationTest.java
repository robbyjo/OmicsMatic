/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyFeatureLocationPolicy;
import org.junit.jupiter.api.Test;

class StudyGeneticFeatureLocationTest {
    private Map<String,Object> valid() {return Map.of("feature_id","probe","genome_build","GRCh38","chromosome","chr1","start",100,"end",200,"coordinate_system","1-based-closed","source_id","synthetic-v1","citation","Synthetic source","rationale","Exact simulated interval");}
    @Test void policyRequiresEverySourcedFieldAndAnExplicitCoordinateConvention() {
        var source=valid();for(String key:source.keySet()){var p=new LinkedHashMap<>(source);p.remove(key);assertThrows(IllegalArgumentException.class,()->StudyFeatureLocationPolicy.resolve(p),key);}
        var normalized=StudyFeatureLocationPolicy.resolve(source);assertEquals("1",normalized.get("chromosome"));assertEquals(100L,normalized.get("start"));assertEquals("chr1",source.get("chromosome"));
    }
    @Test void policyRejectsMalformedOrUnknownFieldsRatherThanGuessingCoordinates() {
        for(var bad:Map.<String,Object>of("start",0,"end",99,"chromosome","chr0","genome_build","hg38","coordinate_system","0-based-half-open","citation","bad\ntext","unknown","value").entrySet()){var p=new LinkedHashMap<>(valid());p.put(bad.getKey(),bad.getValue());assertThrows(IllegalArgumentException.class,()->StudyFeatureLocationPolicy.resolve(p),bad.getKey());}
        var p=new LinkedHashMap<>(valid());p.put("start",100.5);assertThrows(IllegalArgumentException.class,()->StudyFeatureLocationPolicy.resolve(p));
    }
    @Test void aLocationDeclarationCannotEnableUnannotatedColocalizationOrInferAGene() {
        var finding=new StudyBioinformatics.Feature("probe","","","","","","","missing_annotation",1e-8,1e-7);var pair=Map.<String,Object>of("feature_location",valid());assertThrows(IllegalArgumentException.class,()->StudyGeneticFeatureLocation.resolve(finding,pair,"GRCh38",false));var result=StudyGeneticFeatureLocation.resolve(finding,pair,"GRCh38",true);assertEquals(false,result.get("gene_annotation_inferred"));assertEquals("missing_annotation",result.get("original_annotation_status"));assertFalse(result.containsKey("gene_id"));
    }
}
