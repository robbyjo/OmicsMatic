/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registered downstream gene-set families, distinct from association correction. */
public final class StudyRareFollowupPolicy {
    private StudyRareFollowupPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var p=new LinkedHashMap<String,Object>(configured);
        if(!Set.of("enabled","enrichment_adjustment","minimum_term_genes","maximum_term_genes","maximum_gene_sets","maximum_testing_rows").containsAll(p.keySet()))
            throw new IllegalArgumentException("Unknown rare_variants.followup field");
        p.putIfAbsent("enabled",true);p.putIfAbsent("enrichment_adjustment","BY");
        p.putIfAbsent("minimum_term_genes",5);p.putIfAbsent("maximum_term_genes",5000);
        p.putIfAbsent("maximum_gene_sets",20000);p.putIfAbsent("maximum_testing_rows",2000000);
        if(!(p.get("enabled") instanceof Boolean)||!Set.of("BH","BY","NONE").contains(p.get("enrichment_adjustment")))
            throw new IllegalArgumentException("Rare follow-up requires enabled boolean and BH/BY/NONE enrichment adjustment");
        for(String key:Set.of("minimum_term_genes","maximum_term_genes","maximum_gene_sets","maximum_testing_rows"))
            if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||n.longValue()<1||n.longValue()>2000000)
                throw new IllegalArgumentException("rare_variants.followup."+key+" must be an integer in 1..2000000");
        if(((Number)p.get("maximum_term_genes")).intValue()<((Number)p.get("minimum_term_genes")).intValue())
            throw new IllegalArgumentException("Rare follow-up maximum_term_genes is below minimum_term_genes");
        return p;
    }
}
