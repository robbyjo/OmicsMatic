/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyCacheScopeTest {
    @Test void unrelatedHypothesesDownstreamSettingsAndResolutionHistoryDoNotChangePreparation() {
        var original = study();
        var changed = study();
        hypothesis(changed).put("analysis", Map.of("association", Map.of("model", "ols"),
            "bioinformatics", Map.of("genetic_pairs", List.of(Map.of("id", "new-pair")))));
        changed.put("hypotheses", List.of(hypothesis(changed), Map.of("id", "H2", "outcome", "new")));
        changed.put("_resolution", Map.of("defaults_version", 35, "amendments", List.of("new")));
        changed.put("project", Map.of("id", "fixture", "manuscript_policy", Map.of("max_main_words", 10000)));
        changed.put("assistance", Map.of("enabled", true));
        assertEquals(fingerprint(original, "association"), fingerprint(changed, "association"));
    }

    @Test void downstreamFilePathIsDeferredButScientificInputAndModelChangesInvalidate() {
        var original = study();
        var changed = study();
        dataset(changed).put("files", Map.of("matrix", "matrix.tsv", "phenotype", "phenotype.tsv", "exposure", "arrived.tsv"));
        assertEquals(fingerprint(original, "association"), fingerprint(changed, "association"));
        dataset(changed).put("files", Map.of("matrix", "new-matrix.tsv", "phenotype", "phenotype.tsv"));
        assertNotEquals(fingerprint(original, "association"), fingerprint(changed, "association"));
        changed = study();
        hypothesis(changed).put("covariates", List.of("age"));
        assertNotEquals(fingerprint(original, "association"), fingerprint(changed, "association"));
        changed = study();
        hypothesis(changed).put("analysis", Map.of("association", Map.of("model", "logistic")));
        assertNotEquals(fingerprint(original, "association"), fingerprint(changed, "association"));
    }

    @Test void qcPolicyAndSubgroupsAffectQcAndResourcesAlwaysRemainConservative() {
        var original = study();
        for (String field : List.of("genotype_sample_qc", "subgroups", "subgroup_interactions")) {
            var changed = study();
            var a = new LinkedHashMap<>(StudyIO.optionalMap(hypothesis(changed).get("analysis"),"analysis"));
            a.put(field, Map.of("changed", true)); hypothesis(changed).put("analysis", a);
            assertNotEquals(fingerprint(original,"participant_qc"), fingerprint(changed,"participant_qc"), field);
        }
        var changed = study(); changed.put("resources", Map.of("network", "offline"));
        assertNotEquals(fingerprint(original,"association"), fingerprint(changed,"association"));
    }

    @Test void unknownConfiguredImportedAndReportingOperationsKeepFullStudyContext() {
        var s = study();
        for (String type : List.of("new_adapter", "mendelian_randomization", "manuscript_draft", "gene_ontology"))
            assertEquals("full_study", StudyCacheScope.resolve(s, operation(type)).mode());
        var configured = operation("association"); configured.put("configuration", Map.of("command", "custom"));
        assertEquals("full_study", StudyCacheScope.resolve(s, configured).mode());
        var imported = operation("association"); imported.put("supplied_result_id", "source");
        assertEquals("full_study", StudyCacheScope.resolve(s, imported).mode());
        hypothesis(s).put("analysis", Map.of());
        assertEquals("full_study", StudyCacheScope.resolve(s, operation("association")).mode());
    }

    @Test void assaySourceCacheIgnoresOutcomesAndPhenotypePathsButBindsMatrixAndExactPolicy() {
        var original=study();var changed=study();hypothesis(changed).put("outcome","different");hypothesis(changed).put("covariates",List.of("age"));dataset(changed).put("files",Map.of("matrix","matrix.tsv","phenotype","unavailable-new-phenotype.tsv"));
        var op=operation("assay_qc");op.put("dataset_id","data");op.put("assay_qc_policy",StudyAssayQcPolicy.resolve(Map.of()));
        assertEquals(StudyIO.hash(StudyCacheScope.resolve(original,op).context()),StudyIO.hash(StudyCacheScope.resolve(changed,op).context()));
        dataset(changed).put("files",Map.of("matrix","other.tsv"));assertNotEquals(StudyIO.hash(StudyCacheScope.resolve(original,op).context()),StudyIO.hash(StudyCacheScope.resolve(changed,op).context()));
        var second=new LinkedHashMap<>(op);second.put("assay_qc_policy",StudyAssayQcPolicy.resolve(Map.of("mode","filter")));assertNotEquals(StudyIO.hash(StudyCacheScope.resolve(original,op).context()),StudyIO.hash(StudyCacheScope.resolve(original,second).context()));
    }
    private static String fingerprint(Map<String,Object> s, String type) { return StudyIO.hash(StudyCacheScope.resolve(s, operation(type)).context()); }
    @Test void missingAndBlankModelsBindPublicReviewLabelsAtBothNativeEntryPoints() {
        for(String type:List.of("participant_qc","association"))for(String model:List.of("missing","")){
            var original=study();var changed=study();var options=model.equals("missing")?Map.<String,Object>of():Map.<String,Object>of("model",model);
            hypothesis(original).put("analysis",Map.of("association",options,"bioinformatics",Map.of("phenotype_terms",List.of("first public trait"))));
            hypothesis(changed).put("analysis",Map.of("association",options,"bioinformatics",Map.of("phenotype_terms",List.of("second public trait"))));
            assertEquals("full_study",StudyCacheScope.resolve(original,operation(type)).mode());assertNotEquals(fingerprint(original,type),fingerprint(changed,type));
        }
    }
    private static LinkedHashMap<String,Object> operation(String type) { return new LinkedHashMap<>(Map.of("id","op-1","type",type,"hypothesis","H1","datasets",List.of("data"),"depends_on",List.of())); }
    private static Map<String,Object> study() {
        var h = new LinkedHashMap<String,Object>(Map.of("id","H1","outcome","y","inputs",Map.of("discovery",List.of("data")),"analysis",Map.of("association",Map.of("model","ols"))));
        var d = new LinkedHashMap<String,Object>(Map.of("id","data","files",Map.of("matrix","matrix.tsv","phenotype","phenotype.tsv","exposure","missing.tsv")));
        return new LinkedHashMap<>(Map.of("project",Map.of("id","fixture"),"hypotheses",new ArrayList<>(List.of(h)),"datasets",new ArrayList<>(List.of(d)),"_resolution",Map.of("defaults_version",35)));
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> hypothesis(Map<String,Object> s) { return (Map<String,Object>)((List<?>)s.get("hypotheses")).get(0); }
    @SuppressWarnings("unchecked") private static Map<String,Object> dataset(Map<String,Object> s) { return (Map<String,Object>)((List<?>)s.get("datasets")).get(0); }
}
